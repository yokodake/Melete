package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.backup.BackupService
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.plan.ImportMode
import com.yokodake.melete.data.plan.ImportScope
import com.yokodake.melete.data.plan.PlanImporter
import com.yokodake.melete.data.plan.PlanUnreadable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * A plan file written into the database: everything it names lands where it says, adding keeps
 * what is there and updates by name, replacing clears only plans that have not happened, the past
 * is left alone unless included, and a file with a mistake changes nothing at all.
 */
@RunWith(AndroidJUnit4::class)
class PlanImportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MeleteDatabase
    private lateinit var repository: TrainingRepository
    private lateinit var importer: PlanImporter
    private lateinit var safety: File

    private val monday = LocalDate.of(2026, 10, 5)

    private val plan = """
        {
          "format": "melete-plan", "version": 1,
          "exercises": [
            { "name": "Max hangs", "mode": "DURATION", "category": "finger_training",
              "unit": "kg", "meaning": "ADDED_LOAD",
              "plan": { "sets": 5, "seconds": 10, "restSeconds": 180 },
              "variations": [ { "tag": "END", "notes": "Speed first.", "plan": { "sets": 6, "seconds": 10, "restSeconds": 90 } } ] },
            { "name": "Pull-up", "mode": "reps", "plan": { "sets": 4, "reps": 6 } },
            { "name": "Bouldering", "mode": "activity", "plan": { "minutes": 120 } }
          ],
          "circuits": [
            { "name": "Pull circuit", "rounds": 3, "transitionSeconds": 30, "roundRestSeconds": 120,
              "stations": [ { "exercise": "Pull-up", "plan": { "reps": 5 } }, { "exercise": "Max hangs" } ] }
          ],
          "modules": [
            { "name": "Fingers day", "description": "Base block.",
              "entries": [ { "exercise": "Max hangs", "variation": "END" }, { "circuit": "Pull circuit" } ] }
          ],
          "weeks": [
            { "weekStart": "2026-10-05",
              "unscheduled": [ { "exercise": "Pull-up" } ],
              "monday": [ { "exercise": "Max hangs", "variation": "END", "plan": { "sets": 3, "seconds": 7 } }, { "circuit": "Pull circuit" } ],
              "wednesday": [ { "module": "Fingers day" } ],
              "saturday": [ { "exercise": "Bouldering" }, { "activity": "Outdoor day", "minutes": 300 } ] }
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MeleteDatabase::class.java).build()
        repository = TrainingRepository(database)
        importer = PlanImporter(database, repository, BackupService(database))
        safety = File(context.cacheDir, "plan-import-test").apply { deleteRecursively() }
    }

    @After
    fun tearDown() {
        database.close()
        safety.deleteRecursively()
    }

    /** "Today" is fixed, before the file's weeks, unless a test says otherwise. */
    private suspend fun import(
        text: String,
        mode: ImportMode = ImportMode.ADD,
        scope: ImportScope = ImportScope.FROM_TODAY,
        today: LocalDate = monday.minusDays(7),
    ) = importer.import(importer.decode(text), mode, scope, safety, today)

    @Test
    fun everythingTheFileNamesLandsWhereItSays() = runBlocking {
        import(plan)

        val library = repository.observeLibrary().first().associateBy { it.name }
        assertEquals(setOf("Max hangs", "Pull-up", "Bouldering"), library.keys)
        val hangs = library.getValue("Max hangs")
        assertEquals("kg", hangs.measurementUnit)
        assertEquals("Speed first.", hangs.variations.single().notes)
        assertEquals(120 * 60, library.getValue("Bouldering").defaultPrescription?.targetDurationSeconds)

        val circuit = repository.observeRoutines().first().single()
        assertEquals(listOf(5, 10), circuit.entries.map { it.prescription?.targetReps ?: it.prescription?.targetDurationSeconds })
        val module = repository.observeModules().first().single()
        assertEquals(listOf("END", null), module.entries.map { it.variationTag })

        val week = repository.observeWeek(monday).first()
        val standalone = week.filter { it.circuitInstanceId == null && it.moduleInstanceId == null }
        assertEquals(
            listOf(null to "Pull-up", monday to "Max hangs", monday.plusDays(5) to "Bouldering", monday.plusDays(5) to "Outdoor day"),
            standalone.sortedWith(compareBy({ it.trainingDate }, { it.orderIndex })).map { it.trainingDate to it.name },
        )
        val endHang = standalone.single { it.name == "Max hangs" }
        assertEquals("END", endHang.variationTag)
        // The placement's own plan, over the variation's.
        assertEquals(3 to 7, endHang.prescription?.sets to endHang.prescription?.targetDurationSeconds)
        assertEquals(300 * 60, standalone.single { it.name == "Outdoor day" }.prescription?.targetDurationSeconds)
        // Monday's circuit stands alone; Wednesday's came with the module.
        val circuits = repository.observeWeekCircuits(monday).first()
        assertEquals(listOf(monday), circuits.filter { it.moduleInstanceId == null }.map { it.trainingDate })
        assertEquals(listOf(monday.plusDays(2)), circuits.filter { it.moduleInstanceId != null }.map { it.trainingDate })
        assertEquals(listOf(monday.plusDays(2)), repository.observeWeekModules(monday).first().map { it.trainingDate })
    }

    @Test
    fun addingUpdatesByNameAndKeepsWhatIsAlreadyPlanned() = runBlocking {
        import(plan)
        val before = repository.observeWeek(monday).first().single { it.name == "Pull-up" && it.trainingDate == null }

        import(
            """ { "format": "melete-plan", "version": 1,
                  "exercises": [ { "name": "pull-up", "mode": "REPETITIONS", "description": "Chin over bar.", "plan": { "sets": 5, "reps": 3 } } ],
                  "weeks": [ { "weekStart": "2026-10-05", "unscheduled": [ { "exercise": "Pull-up" } ] } ] } """
        )

        val library = repository.observeLibrary().first()
        assertEquals(3, library.size)
        val pull = library.single { it.name == "pull-up" }
        assertEquals("Chin over bar.", pull.description)
        assertEquals(5, pull.defaultPrescription?.sets)
        // The variation the second file did not list is still there.
        assertEquals(listOf("END"), library.single { it.name == "Max hangs" }.variations.map { it.tag })
        val undated = repository.observeWeek(monday).first().filter { it.trainingDate == null && it.circuitInstanceId == null }
        assertEquals(2, undated.size)
        // Already planned: its own copy, unchanged. Newly planned: the new default.
        assertEquals(4, undated.single { it.id == before.id }.prescription?.sets)
        assertEquals(5, undated.single { it.id != before.id }.prescription?.sets)
    }

    @Test
    fun replacingClearsOnlyPlansThatHaveNotHappened() = runBlocking {
        import(plan, scope = ImportScope.INCLUDE_PAST)
        val week = repository.observeWeek(monday).first()
        // Logged: the undated pull-up. Skipped: Saturday's bouldering. The rest is only planned.
        val logged = week.single { it.name == "Pull-up" && it.trainingDate == null && it.circuitInstanceId == null }
        repository.saveLogs(
            monday,
            listOf(OccurrenceLogWrite(logged.id, true, listOf(SetWrite(ActualSetPayload(reps = 6), null)))),
        )
        val skipped = week.single { it.name == "Bouldering" }
        repository.setOccurrenceState(skipped.id, OccurrenceState.SKIPPED)

        val result = import(
            """ { "format": "melete-plan", "version": 1,
                  "exercises": [ { "name": "Pull-up", "mode": "REPETITIONS", "plan": { "sets": 3, "reps": 10 } },
                                 { "name": "Run", "mode": "ACTIVITY" } ],
                  "weeks": [ { "weekStart": "2026-10-05", "friday": [ { "exercise": "Run" } ] } ] } """,
            ImportMode.REPLACE,
            ImportScope.INCLUDE_PAST,
        )

        // The library is the file's; Pull-up kept its id, so its history still groups with it.
        val library = repository.observeLibrary().first()
        assertEquals(setOf("Pull-up", "Run"), library.map { it.name }.toSet())
        assertEquals(logged.exerciseId, library.single { it.name == "Pull-up" }.id)
        assertTrue(repository.observeRoutines().first().isEmpty())
        assertTrue(repository.observeModules().first().isEmpty())

        // What happened stays, sets included; every plan that had not happened went.
        val after = repository.observeWeek(monday).first()
        assertEquals(setOf(logged.id, skipped.id), after.filter { it.name != "Run" }.map { it.id }.toSet())
        assertEquals(1, repository.loggedSetCount(logged.id))
        assertEquals(listOf(monday.plusDays(4)), after.filter { it.name == "Run" }.map { it.trainingDate })
        assertTrue(repository.observeWeekCircuits(monday).first().isEmpty())
        assertTrue(repository.observeWeekModules(monday).first().isEmpty())

        val impact = result.replace!!
        assertEquals(2, impact.kept)
        assertEquals(setOf("Max hangs", "Bouldering"), impact.removedExercises.toSet())
        assertEquals(1, safety.listFiles().orEmpty().size)
    }

    @Test
    fun fromTodayLeavesThePastAlone() = runBlocking {
        val wednesday = monday.plusDays(2)
        // Planned with the past included, then replaced from Wednesday on.
        import(plan, scope = ImportScope.INCLUDE_PAST)
        val check = import(
            """ { "format": "melete-plan", "version": 1,
                  "exercises": [ { "name": "Pull-up", "mode": "REPS" }, { "name": "Max hangs", "mode": "DURATION" },
                                 { "name": "Bouldering", "mode": "ACTIVITY" } ],
                  "circuits": [ { "name": "Pull circuit", "stations": [ { "exercise": "Pull-up" } ] } ],
                  "modules": [ { "name": "Fingers day", "entries": [ { "exercise": "Max hangs" } ] } ],
                  "weeks": [ { "weekStart": "2026-10-05",
                      "monday": [ { "activity": "Too late" } ],
                      "thursday": [ { "exercise": "Pull-up" } ] } ] } """,
            ImportMode.REPLACE,
            ImportScope.FROM_TODAY,
            today = wednesday,
        )

        val week = repository.observeWeek(monday).first()
        // Monday's cards were before today: kept as planned, and the file's Monday item skipped.
        assertTrue(week.any { it.trainingDate == monday && it.name == "Max hangs" })
        assertTrue(week.none { it.name == "Too late" })
        assertEquals(listOf(monday), repository.observeWeekCircuits(monday).first().filter { it.moduleInstanceId == null }.map { it.trainingDate })
        // Wednesday on was cleared (the module, Saturday) and rebuilt from the file (Thursday).
        assertTrue(repository.observeWeekModules(monday).first().isEmpty())
        assertTrue(week.none { it.trainingDate == monday.plusDays(5) })
        assertTrue(week.any { it.trainingDate == monday.plusDays(3) && it.name == "Pull-up" })
        // The week's undated area still has days ahead of it, so it counted as today on.
        assertTrue(week.none { it.trainingDate == null })
        assertEquals(1, check.resolution.preview.skippedPast)
    }

    @Test
    fun aFileWithAMistakeChangesNothing() = runBlocking {
        import(plan)
        try {
            import(
                """ { "format": "melete-plan", "version": 1,
                      "exercises": [ { "name": "New one", "mode": "REPS" } ],
                      "weeks": [ { "weekStart": "2026-10-12", "monday": [ { "exercise": "Nope" } ] } ] } """,
                ImportMode.REPLACE,
            )
            fail("a file naming an exercise that exists nowhere must be refused")
        } catch (expected: PlanUnreadable) {
            assertTrue(expected.message!!.contains("Nope"))
        }
        assertEquals(3, repository.observeLibrary().first().size)
        assertTrue(safety.listFiles().isNullOrEmpty())
    }

    @Test
    fun aBackupIsPointedToRestore() {
        try {
            importer.decode("""{ "format": "melete-backup", "formatVersion": 1 }""")
            fail("a backup is not a plan")
        } catch (expected: PlanUnreadable) {
            assertTrue(expected.message!!.contains("Restore"))
        }
    }
}
