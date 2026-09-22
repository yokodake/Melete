package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.core.OneOffActivity
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.CircuitSnapshotJson
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Phase 5A and Timer C against a real database: duration-only activities, logged duration and its
 * provenance, and circuits scheduled by value.
 *
 * These are the claims that cannot be checked by looking at a screen — that a one-off leaves no
 * library clutter, that a completed activity is protected like any other record, and that editing
 * a routine cannot reach a copy already placed in a week.
 */
@RunWith(AndroidJUnit4::class)
class ActivityAndCircuitTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MeleteDatabase
    private lateinit var repository: TrainingRepository

    private val monday = LocalDate.of(2026, 9, 21)
    private val tuesday = monday.plusDays(1)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MeleteDatabase::class.java).build()
        repository = TrainingRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private fun draft(
        name: String,
        mode: ExerciseMode = ExerciseMode.DURATION,
        unilateral: Boolean = false,
        unit: String? = "kg",
        prescription: PrescriptionPayload = PrescriptionPayload(
            sets = 3,
            targetDurationSeconds = 10,
            restSeconds = 60,
        ),
    ) = ExerciseDraft(
        name = name,
        mode = mode,
        unilateral = unilateral,
        measurementUnit = unit,
        measurementMeaning = unit?.let { MeasurementMeaning.TOTAL_LOAD },
        notes = null,
        description = null,
        category = ExerciseCategory.CONDITIONING,
        defaultPrescription = prescription,
    )

    private suspend fun week() = repository.observeWeek(monday).first()

    // -------------------------------------------------------- activities

    @Test
    fun aOneOffRunLeavesNoLibraryEntryBehind() = runBlocking {
        repository.createOneOffActivity("Trail run", monday, tuesday, 45 * 60)

        assertTrue(repository.observeLibrary().first().isEmpty())
        val occurrence = week().single()
        assertEquals("Trail run", occurrence.name)
        assertTrue(occurrence.isOneOff)
        assertEquals(ExerciseMode.ACTIVITY, occurrence.mode)
        assertEquals(tuesday, occurrence.trainingDate)
    }

    @Test
    fun twoOneOffsWithTheSameNameShareOneStableIdentity() = runBlocking {
        repository.createOneOffActivity("Outdoor bouldering", monday, monday, null)
        repository.createOneOffActivity("  outdoor   bouldering ", monday, tuesday, null)
        repository.createOneOffActivity("Trail run", monday, tuesday, null)

        val ids = week().map { it.exerciseId }
        assertEquals(2, ids.distinct().size)
        assertEquals(
            OneOffActivity.exerciseIdFor("Outdoor bouldering"),
            week().first { it.name.trim().startsWith("Outdoor") }.exerciseId,
        )
    }

    @Test
    fun anActivityIsCompletedWithNoDurationAndNoFabricatedSets() = runBlocking {
        val id = repository.createOneOffActivity("Silks class", monday, tuesday, null)
        repository.saveLogs(
            trainingDate = tuesday,
            writes = listOf(OccurrenceLogWrite(occurrenceId = id, completed = true)),
        )

        val occurrence = repository.observeOccurrence(id).first()!!
        assertEquals(OccurrenceState.COMPLETED, occurrence.occurrence.state)
        // Counted once, with no set records invented to make it countable.
        assertTrue(occurrence.sets.isEmpty())
        assertNull(occurrence.occurrence.loggedDurationSeconds)
        assertTrue(occurrence.occurrence.hasRecord)
    }

    @Test
    fun aCompletedActivityIsProtectedLikeAnyOtherRecord() = runBlocking {
        val id = repository.createOneOffActivity("Open climbing", monday, tuesday, null)
        repository.saveLogs(tuesday, listOf(OccurrenceLogWrite(id, completed = true)))

        // It has no sets, so a sets-only guard would have let it be tidied away.
        assertFalse(repository.deleteOccurrenceIfEmpty(id))
        assertFalse(repository.moveOccurrence(id, monday, trainingDate = null))
        assertNotNull(repository.observeOccurrence(id).first())
    }

    @Test
    fun aReusableLibraryActivityCanBeScheduledAgainAndAgain() = runBlocking {
        val exerciseId = repository.createExercise(
            draft(
                name = "Flexibility class",
                mode = ExerciseMode.ACTIVITY,
                unit = null,
                prescription = PrescriptionPayload(sets = 1, targetDurationSeconds = 3_600),
            )
        )
        repository.scheduleExercise(exerciseId, monday, monday)
        repository.scheduleExercise(exerciseId, monday, tuesday)

        val placed = week().filter { it.exerciseId == exerciseId }
        assertEquals(2, placed.size)
        assertEquals(listOf(monday, tuesday), placed.mapNotNull { it.trainingDate }.sorted())
        // One definition, two occurrences: a class is a library entry, not a storage engine.
        assertEquals(1, repository.observeLibrary().first().size)
    }

    @Test
    fun aCompletedActivityRetiresItsDefinitionRatherThanDeletingIt() = runBlocking {
        val exerciseId = repository.createExercise(
            draft("Open climbing", ExerciseMode.ACTIVITY, unit = null)
        )
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)
        repository.saveLogs(tuesday, listOf(OccurrenceLogWrite(occurrenceId, completed = true)))

        val impact = repository.removalImpactOf(exerciseId)!!
        assertEquals(1, impact.completedCopies)
        assertEquals(ExerciseRemoval.Kind.LOGGED, impact.kind)
        assertEquals(ExerciseRemoval.Outcome.RETIRED, repository.removeExercise(exerciseId))
        assertNotNull(repository.observeOccurrence(occurrenceId).first())
    }

    // ---------------------------------------------------------- duration

    @Test
    fun aTypedDurationIsManualAndAnInheritedEstimateIsNot() = runBlocking {
        val exerciseId = repository.createExercise(draft("Max hangs"))
        val typed = repository.scheduleExercise(exerciseId, monday, tuesday)
        val inferred = repository.scheduleExercise(exerciseId, monday, tuesday)

        repository.saveLogs(
            trainingDate = tuesday,
            writes = listOf(
                OccurrenceLogWrite(typed, completed = true, durationSeconds = 900, durationManual = true),
                OccurrenceLogWrite(inferred, completed = true, durationSeconds = 155, durationManual = false),
            ),
        )

        val saved = week().associateBy { it.id }
        assertEquals(900, saved[typed]!!.loggedDurationSeconds)
        assertTrue(saved[typed]!!.loggedDurationManual)
        assertEquals(155, saved[inferred]!!.loggedDurationSeconds)
        assertFalse(saved[inferred]!!.loggedDurationManual)
    }

    @Test
    fun historicalDurationStaysFixedWhenThePrescriptionIsEdited() = runBlocking {
        val exerciseId = repository.createExercise(draft("Max hangs"))
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)
        repository.saveLogs(
            tuesday,
            listOf(OccurrenceLogWrite(occurrenceId, completed = true, durationSeconds = 155)),
        )

        // Doubling the plan afterwards must not rewrite what the session says it took.
        repository.updateOccurrencePrescription(
            occurrenceId,
            PrescriptionPayload(sets = 6, targetDurationSeconds = 20, restSeconds = 180),
        )
        assertEquals(155, week().single { it.id == occurrenceId }.loggedDurationSeconds)
    }

    @Test
    fun clearingADurationRestoresTheEstimateRatherThanRecordingZero() = runBlocking {
        val exerciseId = repository.createExercise(draft("Max hangs"))
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)
        repository.setLoggedDuration(occurrenceId, 900, manual = true)
        assertEquals(900, week().single().loggedDurationSeconds)

        repository.setLoggedDuration(occurrenceId, null, manual = true)
        val cleared = week().single()
        assertNull(cleared.loggedDurationSeconds)
        assertFalse(cleared.loggedDurationManual)
        // The estimate takes the question back rather than the record saying zero.
        assertEquals(155, cleared.estimatedDurationSeconds)
    }

    @Test
    fun savingTheSameWorkoutTwiceCorrectsItRatherThanDuplicating() = runBlocking {
        val exerciseId = repository.createExercise(draft("Max hangs"))
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)
        fun write(load: Double) = OccurrenceLogWrite(
            occurrenceId = occurrenceId,
            completed = true,
            sets = List(3) {
                SetWrite(
                    ActualSetPayload(
                        measurement = Measurement(load, "kg", MeasurementMeaning.TOTAL_LOAD),
                    ),
                    null,
                )
            },
            durationSeconds = 155,
        )

        repository.saveLogs(tuesday, listOf(write(20.0)))
        repository.saveLogs(tuesday, listOf(write(25.0)))

        val detail = repository.observeOccurrence(occurrenceId).first()!!
        assertEquals(3, detail.sets.size)
        assertTrue(detail.sets.all { it.payload.measurement?.value == 25.0 })
    }

    // ---------------------------------------------------------- circuits

    private suspend fun pullCircuit(): Pair<String, String> {
        val hang = repository.createExercise(draft("Hang"))
        val row = repository.createExercise(
            draft(
                name = "Row",
                mode = ExerciseMode.REPETITIONS,
                prescription = PrescriptionPayload(sets = 4, targetReps = 8, restSeconds = 90),
            )
        )
        val routineId = repository.createRoutine(
            RoutineDraft(
                name = "Pull circuit",
                rounds = 3,
                transitionSeconds = 30,
                roundRestSeconds = 120,
                entries = listOf(
                    RoutineEntryDraft(hang, PrescriptionPayload(sets = 1, targetDurationSeconds = 20)),
                    RoutineEntryDraft(row, PrescriptionPayload(sets = 1, targetReps = 8)),
                ),
            )
        )
        return routineId to repository.scheduleRoutine(routineId, monday, tuesday)!!
    }

    @Test
    fun aScheduledCircuitIsRealOccurrencesPlusAContainerThatCountsNothing() = runBlocking {
        val (_, circuitId) = pullCircuit()

        val circuit = repository.observeScheduledCircuit(circuitId).first()!!
        assertEquals("Pull circuit", circuit.name)
        assertEquals(listOf("Hang", "Row"), circuit.stations.map { it.name })
        assertEquals(listOf(0, 1), circuit.stations.map { it.circuitPosition })
        // Two occurrences, not three: the container is not one of them.
        assertEquals(2, week().size)
        assertTrue(week().all { it.circuitInstanceId == circuitId })
    }

    @Test
    fun aScheduledCircuitKeepsASnapshotOfTheStructureItWasCutFrom() = runBlocking {
        val (routineId, circuitId) = pullCircuit()
        val row = database.trainingDao().getCircuit(circuitId)!!
        val snapshot = CircuitSnapshotJson.decode(row.structureSnapshotJson)!!

        assertEquals(routineId, snapshot.routineId)
        assertEquals(1, snapshot.structureVersion)
        assertEquals(listOf("Hang", "Row"), snapshot.entries.map { it.exerciseName })
        assertEquals(3, snapshot.rounds)
    }

    @Test
    fun editingARoutineNeverReachesACopyAlreadyInAWeek() = runBlocking {
        val (routineId, circuitId) = pullCircuit()
        val before = repository.observeScheduledCircuit(circuitId).first()!!
        val hangId = before.stations.first().exerciseId

        repository.updateRoutine(
            routineId,
            RoutineDraft(
                name = "Pull circuit, harder",
                rounds = 5,
                transitionSeconds = 15,
                roundRestSeconds = 60,
                entries = listOf(
                    RoutineEntryDraft(hangId, PrescriptionPayload(sets = 1, targetDurationSeconds = 30)),
                ),
            )
        )

        val after = repository.observeScheduledCircuit(circuitId).first()!!
        assertEquals("Pull circuit", after.name)
        assertEquals(3, after.rounds)
        assertEquals(2, after.stations.size)
        assertEquals(20, after.stations.first().prescription?.targetDurationSeconds)
        // And the routine itself did change, so the isolation is real rather than a no-op.
        val routine = repository.observeRoutine(routineId).first()!!
        assertEquals(5, routine.rounds)
        assertEquals(2, routine.structureVersion)
        assertEquals(30, routine.entries.single().prescription?.targetDurationSeconds)
    }

    @Test
    fun twoCopiesOfOneRoutineDoNotShareTheirPrescriptions() = runBlocking {
        val (routineId, first) = pullCircuit()
        val second = repository.scheduleRoutine(routineId, monday, monday)!!

        val a = repository.observeScheduledCircuit(first).first()!!.stations.first()
        val b = repository.observeScheduledCircuit(second).first()!!.stations.first()
        assertNotEquals(a.prescriptionId, b.prescriptionId)

        repository.updateOccurrencePrescription(
            a.id,
            PrescriptionPayload(sets = 1, targetDurationSeconds = 7),
        )
        val untouched = repository.observeScheduledCircuit(second).first()!!.stations.first()
        assertEquals(20, untouched.prescription?.targetDurationSeconds)
    }

    @Test
    fun oneSaveWritesEveryStationAndEachCountsOnce() = runBlocking {
        val (_, circuitId) = pullCircuit()
        val circuit = repository.observeScheduledCircuit(circuitId).first()!!

        repository.saveLogs(
            trainingDate = tuesday,
            writes = circuit.stations.map { station ->
                OccurrenceLogWrite(
                    occurrenceId = station.id,
                    completed = true,
                    // Three rounds of this exercise are three sets, not three occurrences.
                    sets = List(3) { SetWrite(ActualSetPayload(reps = 8), null) },
                    durationSeconds = 240,
                )
            },
        )

        val saved = repository.observeScheduledCircuit(circuitId).first()!!
        assertTrue(saved.completed)
        assertEquals(2, saved.stations.count { it.hasRecord })
        assertEquals(listOf(240, 240), saved.stations.map { it.loggedDurationSeconds })
        // Six sets across two exercises, and still exactly two completed occurrences.
        assertEquals(2, week().count { it.state == OccurrenceState.COMPLETED })
    }

    @Test
    fun untickingAStationClearsItsSetsAndTakesBackItsCompletion() = runBlocking {
        val (_, circuitId) = pullCircuit()
        val stations = repository.observeScheduledCircuit(circuitId).first()!!.stations
        repository.saveLogs(
            tuesday,
            stations.map {
                OccurrenceLogWrite(it.id, completed = true, sets = listOf(SetWrite(ActualSetPayload(reps = 8), null)))
            },
        )
        repository.saveLogs(
            tuesday,
            listOf(
                OccurrenceLogWrite(stations[0].id, completed = true, sets = listOf(SetWrite(ActualSetPayload(reps = 8), null))),
                OccurrenceLogWrite(stations[1].id, completed = false),
            ),
        )

        val after = repository.observeScheduledCircuit(circuitId).first()!!
        assertTrue(after.stations[0].hasRecord)
        assertFalse(after.stations[1].hasRecord)
        assertEquals(OccurrenceState.PLANNED, after.stations[1].state)
        assertTrue(repository.observeOccurrence(after.stations[1].id).first()!!.sets.isEmpty())
    }

    @Test
    fun movingACircuitTakesItsStationsAndTheirLogsWithIt() = runBlocking {
        val (_, circuitId) = pullCircuit()
        val stations = repository.observeScheduledCircuit(circuitId).first()!!.stations
        repository.saveLogs(
            tuesday,
            listOf(
                OccurrenceLogWrite(
                    stations[0].id,
                    completed = true,
                    sets = listOf(SetWrite(ActualSetPayload(reps = 8), null)),
                )
            ),
        )

        val wednesday = monday.plusDays(2)
        assertTrue(repository.moveCircuit(circuitId, monday, wednesday))
        val moved = repository.observeScheduledCircuit(circuitId).first()!!
        assertEquals(wednesday, moved.trainingDate)
        assertTrue(moved.stations.all { it.trainingDate == wednesday })
        assertEquals(
            wednesday,
            repository.observeOccurrence(stations[0].id).first()!!.sets.single().trainingDate,
        )
        // Trained work belongs to a day, so it cannot go back to "anytime this week".
        assertFalse(repository.moveCircuit(circuitId, monday, null))
    }

    @Test
    fun removingARoutineLeavesEveryScheduledCopyAlone() = runBlocking {
        val (routineId, circuitId) = pullCircuit()
        val impact = repository.routineRemovalImpact(routineId)!!
        assertEquals(1, impact.scheduledCopies)

        assertTrue(repository.removeRoutine(routineId))
        assertTrue(repository.observeRoutines().first().isEmpty())
        val circuit = repository.observeScheduledCircuit(circuitId).first()!!
        assertEquals(2, circuit.stations.size)
        assertEquals("Pull circuit", circuit.name)
    }

    @Test
    fun aCircuitWithARecordCannotBeTidiedAwayByAccident() = runBlocking {
        val (_, circuitId) = pullCircuit()
        val stations = repository.observeScheduledCircuit(circuitId).first()!!.stations
        repository.saveLogs(tuesday, listOf(OccurrenceLogWrite(stations[0].id, completed = true)))

        assertEquals(1, repository.circuitRecordedStations(circuitId))
        assertFalse(repository.deleteCircuitIfEmpty(circuitId))
        assertNotNull(repository.observeScheduledCircuit(circuitId).first())

        // The deliberate stronger answer does go through, and takes the log with it.
        repository.deleteCircuitAndLogs(circuitId)
        assertNull(repository.observeScheduledCircuit(circuitId).first())
        assertTrue(week().isEmpty())
    }
}
