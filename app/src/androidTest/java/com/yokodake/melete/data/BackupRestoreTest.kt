package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.backup.BackupService
import com.yokodake.melete.data.backup.BackupUnreadable
import com.yokodake.melete.data.backup.MeleteBackup
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Export → a different database → restore, against real databases.
 *
 * The proof is that exporting the restored database gives back the same file, section by section:
 * every id, every field and every relationship survived, not merely that the file parsed.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var source: MeleteDatabase
    private lateinit var target: MeleteDatabase

    private val monday = LocalDate.of(2026, 9, 21)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)
    private val at = Instant.parse("2026-09-26T10:00:00Z")

    @Before
    fun setUp() {
        source = Room.inMemoryDatabaseBuilder(context, MeleteDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, MeleteDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private suspend fun exercise(
        repository: TrainingRepository,
        name: String,
        mode: ExerciseMode = ExerciseMode.REPETITIONS,
        unilateral: Boolean = false,
    ) = repository.createExercise(
        ExerciseDraft(
            name = name,
            mode = mode,
            unilateral = unilateral,
            measurementUnit = "kg",
            measurementMeaning = MeasurementMeaning.ADDED_LOAD,
            notes = "notes for $name",
            description = null,
            category = ExerciseCategory.STRENGTH_CONDITIONING,
            defaultPrescription = PrescriptionPayload(sets = 3, targetReps = 5, effort = EffortLevel.HARD),
        )
    )

    private fun set(load: Double, side: BodySide?) = SetWrite(
        ActualSetPayload(reps = 5, measurement = Measurement(load, "kg", MeasurementMeaning.ADDED_LOAD)),
        side,
    )

    /**
     * Everything the spec names: activities, circuits, modules, skipped work, manual, inferred
     * and missing durations, unilateral loads, a tombstoned definition, variations and the diary.
     */
    private suspend fun populate(database: MeleteDatabase) {
        val repository = TrainingRepository(database)
        val diary = DiaryRepository(database)

        val squat = exercise(repository, "Squat")
        val row = exercise(repository, "Row", unilateral = true)
        val pull = exercise(repository, "Pull-up")
        repository.createVariation(squat, "PWR", "Fast", PrescriptionPayload(sets = 5, targetReps = 3))

        // Unilateral loads, a manual duration.
        val rowOcc = repository.scheduleExercise(row, monday, monday)
        repository.saveLogs(
            monday,
            listOf(
                OccurrenceLogWrite(
                    occurrenceId = rowOcc,
                    completed = true,
                    sets = listOf(set(20.0, BodySide.LEFT), set(22.5, BodySide.RIGHT)),
                    comment = "left weaker",
                    durationSeconds = 600,
                    durationManual = true,
                )
            ),
        )
        // A variation copy with an inferred duration.
        val squatOcc = repository.scheduleExercise(
            squat, monday, tuesday,
            repository.getLibraryExercise(squat)!!.variations.single().id,
        )
        repository.saveLogs(
            tuesday,
            listOf(
                OccurrenceLogWrite(
                    occurrenceId = squatOcc, completed = true, sets = listOf(set(60.0, null)),
                    durationSeconds = 900, durationManual = false,
                )
            ),
        )
        // Skipped, with no duration at all.
        val skipped = repository.scheduleExercise(pull, monday, wednesday)
        repository.setOccurrenceState(skipped, OccurrenceState.SKIPPED)
        // A one-off activity, marked done with only a duration and an effort.
        val run = repository.createOneOffActivity("Trail run", monday, wednesday, 45 * 60)
        repository.saveLogs(
            wednesday,
            listOf(
                OccurrenceLogWrite(
                    occurrenceId = run, completed = true, effort = EffortLevel.MODERATE,
                    durationSeconds = 2_700, durationManual = true,
                )
            ),
        )
        // A circuit and a module holding it, scheduled unscheduled.
        val circuit = repository.createRoutine(
            RoutineDraft(
                name = "Pull circuit", rounds = 3, transitionSeconds = 30, roundRestSeconds = 120,
                entries = listOf(
                    RoutineEntryDraft(pull, PrescriptionPayload(sets = 1, targetReps = 5)),
                    RoutineEntryDraft(row, PrescriptionPayload(sets = 1, targetReps = 8)),
                ),
                category = ExerciseCategory.FINGER_TRAINING,
            )
        )
        val module = repository.createModule(
            ModuleDraft(
                "Pulling", "Base",
                listOf(
                    ModuleEntryDraft(exerciseId = squat, prescription = PrescriptionPayload(sets = 4, targetReps = 4)),
                    ModuleEntryDraft(routineId = circuit),
                ),
            )
        )
        repository.scheduleModule(module, monday, null)
        // A definition retired after being trained: a tombstone history hangs from.
        val retired = exercise(repository, "Old lift")
        val oldOcc = repository.scheduleExercise(retired, monday, monday)
        repository.saveLogs(monday, listOf(OccurrenceLogWrite(oldOcc, true, listOf(set(10.0, null)))))
        repository.removeExercise(retired)
        // The diary.
        diary.observeMetrics().first()
        diary.save(monday, "Slept badly", mapOf("energy" to 2, "finger_discomfort" to 1))
        diary.save(tuesday, null, mapOf("energy" to 4))
    }

    /** The file minus when it was written, which is the one thing allowed to differ. */
    private fun MeleteBackup.comparable() = copy(exportedAt = "")

    @Test
    fun aRestoredRecordExportsToTheSameFile() = runBlocking {
        populate(source)
        val original = BackupService(source).export(at)

        // The target starts with something else in it, which the restore must replace entirely.
        val targetRepository = TrainingRepository(target)
        exercise(targetRepository, "Something else")
        val service = BackupService(target)
        service.restore(service.decode(service.encode(original)))

        val restored = service.export(at)
        assertEquals(original.exercises, restored.exercises)
        assertEquals(original.variations, restored.variations)
        assertEquals(original.routines, restored.routines)
        assertEquals(original.modules, restored.modules)
        assertEquals(original.circuitInstances, restored.circuitInstances)
        assertEquals(original.moduleInstances, restored.moduleInstances)
        assertEquals(original.occurrences, restored.occurrences)
        assertEquals(original.sessions, restored.sessions)
        assertEquals(original.sets, restored.sets)
        assertEquals(original.metrics, restored.metrics)
        assertEquals(original.diary, restored.diary)
        assertEquals(original.comparable(), restored.comparable())
    }

    @Test
    fun theRestoredRecordReadsBackAsTheSameWeek() = runBlocking {
        populate(source)
        val service = BackupService(target)
        service.restore(BackupService(source).export(at))

        val repository = TrainingRepository(target)
        val week = repository.observeWeek(monday).first()
        // The row, the squat, the skipped pull-up, the run, the module's squat, the circuit's two
        // stations and the old lift.
        assertEquals(8, week.size)
        val rowOcc = week.single { it.name == "Row" && it.circuitInstanceId == null }
        val sets = repository.observeOccurrence(rowOcc.id).first()!!.sets
        assertEquals(listOf(BodySide.LEFT, BodySide.RIGHT), sets.map { it.side })
        assertEquals(listOf(20.0, 22.5), sets.map { it.payload.measurement?.value })
        assertEquals(600 to true, rowOcc.loggedDurationSeconds to rowOcc.loggedDurationManual)
        val squat = week.single { it.trainingDate == tuesday }
        assertEquals("PWR", squat.variationTag)
        assertEquals(900 to false, squat.loggedDurationSeconds to squat.loggedDurationManual)
        val pull = week.single { it.state == OccurrenceState.SKIPPED }
        assertEquals(null, pull.loggedDurationSeconds)
        val run = week.single { it.isOneOff }
        assertEquals(EffortLevel.MODERATE, run.loggedEffort)
        // The module still holds its members, the circuit its stations.
        val moduleInstance = repository.observeWeekModules(monday).first().single()
        assertEquals(3, week.count { it.moduleInstanceId == moduleInstance.id })
        assertEquals(
            ExerciseCategory.FINGER_TRAINING,
            repository.observeWeekCircuits(monday).first().single().category,
        )
        // The retired definition is back as a tombstone: not in the library, still the anchor.
        assertTrue(repository.observeLibrary().first().none { it.name == "Old lift" })
        assertEquals(1, week.count { it.name == "Old lift" && it.hasRecord })
        // The diary.
        val days = DiaryRepository(target).observeDays(monday, tuesday).first()
        assertEquals("Slept badly", days.getValue(monday).text)
        assertEquals(mapOf("energy" to 4), days.getValue(tuesday).values)
    }

    @Test
    fun anUnreadableFileIsRefusedAndChangesNothing() = runBlocking {
        populate(target)
        val service = BackupService(target)
        val before = service.export(at)

        try {
            service.decode("{ this is not a backup")
            fail("an unreadable file must be refused")
        } catch (expected: BackupUnreadable) {
            // Refused in words, before anything was touched.
        }
        assertEquals(before, service.export(at))
    }

    @Test
    fun anInvalidFileIsRefusedBeforeTheRestoreBegins() = runBlocking {
        populate(target)
        val service = BackupService(target)
        val before = service.export(at)
        val broken = before.copy(sessions = emptyList())

        try {
            service.restore(broken)
            fail("a file whose sets have no sessions must be refused")
        } catch (expected: BackupUnreadable) {
            assertTrue(expected.message!!.contains("session"))
        }
        assertEquals(before, service.export(at))
    }

    @Test
    fun theSafetyCopyIsTheWholeRecordAndRestoresToIt() = runBlocking<Unit> {
        populate(target)
        val service = BackupService(target)
        val before = service.export(at)
        val directory = context.cacheDir.resolve("backup-test-${System.nanoTime()}")

        val copy = service.writeSafetyCopy(directory, at)
        // Replace the record with an empty one, then undo from the copy.
        service.restore(MeleteBackup(exportedAt = at.toString(), schemaVersion = 7))
        assertTrue(TrainingRepository(target).observeWeek(monday).first().isEmpty())
        service.restore(service.decode(copy.readText()))

        assertEquals(before.comparable(), service.export(at).comparable())
        directory.deleteRecursively()
    }
}
