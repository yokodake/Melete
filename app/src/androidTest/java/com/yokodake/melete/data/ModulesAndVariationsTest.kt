package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.model.ActualSetPayload
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Variations and modules against a real database.
 *
 * The claims worth proving are the ones a screen cannot show: that a copy is cut by value and
 * nothing done to a template reaches it, that a group moves and dissolves without losing a single
 * occurrence, and that a module never adds work of its own.
 */
@RunWith(AndroidJUnit4::class)
class ModulesAndVariationsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MeleteDatabase
    private lateinit var repository: TrainingRepository

    private val monday = LocalDate.of(2026, 9, 21)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MeleteDatabase::class.java).build()
        repository = TrainingRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun exercise(
        name: String,
        prescription: PrescriptionPayload = PrescriptionPayload(sets = 3, targetReps = 5),
    ) = repository.createExercise(
        ExerciseDraft(
            name = name,
            mode = ExerciseMode.REPETITIONS,
            unilateral = false,
            measurementUnit = "kg",
            measurementMeaning = MeasurementMeaning.TOTAL_LOAD,
            notes = null,
            description = null,
            category = ExerciseCategory.STRENGTH_CONDITIONING,
            defaultPrescription = prescription,
        )
    )

    private suspend fun variationId(exerciseId: String, tag: String): String =
        repository.getLibraryExercise(exerciseId)!!.variations.single { it.tag == tag }.id

    private suspend fun week() = repository.observeWeek(monday).first()

    /** Logs one set against a placement, on [date], the way the logger does. */
    private suspend fun logOneSet(occurrenceId: String, date: LocalDate) {
        repository.saveLogs(
            trainingDate = date,
            writes = listOf(
                OccurrenceLogWrite(
                    occurrenceId = occurrenceId,
                    completed = true,
                    sets = listOf(
                        SetWrite(
                            ActualSetPayload(
                                reps = 5,
                                measurement = Measurement(60.0, "kg", MeasurementMeaning.TOTAL_LOAD),
                            ),
                            null,
                        )
                    ),
                )
            ),
        )
    }

    // ------------------------------------------------------------ variations

    @Test
    fun aVariationIsCopiedWithItsOwnPlanAndTagUnderTheSameExercise() = runBlocking {
        val squat = exercise("Squat")
        assertEquals(
            VariationSave.SAVED,
            repository.createVariation(squat, "PWR", "Fast", PrescriptionPayload(sets = 5, targetReps = 3)),
        )

        repository.scheduleExercise(squat, monday, monday, variationId(squat, "PWR"))

        val copy = week().single()
        assertEquals(squat, copy.exerciseId)
        assertEquals("PWR", copy.variationTag)
        assertEquals(5, copy.prescription?.sets)
        assertEquals(3, copy.prescription?.targetReps)
    }

    @Test
    fun theDefaultIsStillWhatIsCopiedWhenNoVariationIsChosen() = runBlocking {
        val squat = exercise("Squat")
        repository.createVariation(squat, "PWR", null, PrescriptionPayload(sets = 5, targetReps = 3))

        repository.scheduleExercise(squat, monday, monday)

        val copy = week().single()
        assertNull(copy.variationTag)
        assertEquals(3, copy.prescription?.sets)
    }

    @Test
    fun aTagIsUniquePerExerciseAndMustFollowTheRules() = runBlocking {
        val squat = exercise("Squat")
        val deadlift = exercise("Deadlift")
        val plan = PrescriptionPayload(sets = 3, targetReps = 3)

        assertEquals(VariationSave.SAVED, repository.createVariation(squat, "A", null, plan))
        assertEquals(VariationSave.TAG_TAKEN, repository.createVariation(squat, "A", null, plan))
        // The same tag on another exercise is a different chip on a different card.
        assertEquals(VariationSave.SAVED, repository.createVariation(deadlift, "A", null, plan))
        assertEquals(VariationSave.TAG_INVALID, repository.createVariation(squat, "pwr", null, plan))
        assertEquals(VariationSave.TAG_INVALID, repository.createVariation(squat, "TOOLONG", null, plan))
        assertEquals(1, repository.getLibraryExercise(squat)!!.variations.size)
    }

    @Test
    fun editingOrDeletingAVariationNeverReachesAScheduledCopy() = runBlocking {
        val squat = exercise("Squat")
        repository.createVariation(squat, "PWR", null, PrescriptionPayload(sets = 5, targetReps = 3))
        val pwr = variationId(squat, "PWR")
        repository.scheduleExercise(squat, monday, monday, pwr)

        repository.updateVariation(pwr, "STR", "Heavier", PrescriptionPayload(sets = 6, targetReps = 2))
        var copy = week().single()
        assertEquals("PWR", copy.variationTag)
        assertEquals(5, copy.prescription?.sets)

        repository.deleteVariation(pwr)
        copy = week().single()
        assertEquals("PWR", copy.variationTag)
        assertEquals(5, copy.prescription?.sets)
        // A copy came from it, so it is retired rather than deleted: no longer offered, still there
        // for the copy to find its notes, and its tag is free for a new variation.
        val library = repository.getLibraryExercise(squat)!!
        assertTrue(library.activeVariations.isEmpty())
        assertEquals("Heavier", library.variations.single { it.id == pwr }.notes)
        assertEquals(
            VariationSave.SAVED,
            repository.createVariation(squat, "STR", null, PrescriptionPayload(sets = 3, targetReps = 3)),
        )
    }

    @Test
    fun aVariationNothingCameFromIsDeletedOutright() = runBlocking {
        val squat = exercise("Squat")
        repository.createVariation(squat, "PWR", null, PrescriptionPayload(sets = 5, targetReps = 3))
        repository.deleteVariation(variationId(squat, "PWR"))
        assertTrue(repository.getLibraryExercise(squat)!!.variations.isEmpty())
    }

    @Test
    fun removingAnUnusedExerciseTakesItsVariationsWithIt() = runBlocking {
        val squat = exercise("Squat")
        repository.createVariation(squat, "PWR", null, PrescriptionPayload(sets = 5, targetReps = 3))

        assertEquals(ExerciseRemoval.Outcome.DELETED, repository.removeExercise(squat))
        assertNull(repository.getLibraryExercise(squat))
    }

    // --------------------------------------------------------------- modules

    /** Fingers + a pull circuit: one standalone exercise (as a variation) and one circuit of two. */
    private suspend fun fingersModule(): String {
        val hang = exercise("Max hangs")
        repository.createVariation(hang, "MAX", "Heavy", PrescriptionPayload(sets = 6, targetReps = 1))
        val row = exercise("Row")
        val pull = exercise("Pull-up")
        val circuit = repository.createRoutine(
            RoutineDraft(
                name = "Pull circuit",
                rounds = 3,
                transitionSeconds = 30,
                roundRestSeconds = 120,
                entries = listOf(
                    RoutineEntryDraft(row, PrescriptionPayload(sets = 1, targetReps = 8)),
                    RoutineEntryDraft(pull, PrescriptionPayload(sets = 1, targetReps = 5)),
                ),
            )
        )
        return repository.createModule(
            ModuleDraft(
                name = "Fingers",
                description = "  Base block.  ",
                entries = listOf(
                    ModuleEntryDraft(
                        exerciseId = hang,
                        variationId = variationId(hang, "MAX"),
                        prescription = PrescriptionPayload(sets = 6, targetReps = 1),
                    ),
                    ModuleEntryDraft(routineId = circuit),
                ),
            )
        )
    }

    @Test
    fun aModuleIsSavedWithItsEntriesAndDescription() = runBlocking {
        val id = fingersModule()
        val module = repository.getModule(id)!!
        assertEquals("Fingers", module.name)
        // Trimmed on the way in; a blank one is stored as none, never as an empty string.
        assertEquals("Base block.", module.description)
        val blank = repository.createModule(ModuleDraft("Blank", "   ", emptyList()))
        assertNull(repository.getModule(blank)!!.description)
        assertEquals(listOf("Max hangs", "Pull circuit"), module.entries.map { it.name })
        assertEquals("MAX", module.entries[0].variationTag)
        assertTrue(module.entries[1].isCircuit)
    }

    @Test
    fun schedulingAModuleCopiesEachEntryAsRealWorkAndAddsNoneOfItsOwn() = runBlocking {
        val instance = repository.scheduleModule(fingersModule(), monday, monday)!!

        val occurrences = week()
        // One hang and the circuit's two stations: three exercises, and nothing for the group.
        assertEquals(3, occurrences.size)
        assertTrue(occurrences.all { it.moduleInstanceId == instance })
        assertEquals("MAX", occurrences.single { it.name == "Max hangs" }.variationTag)
        val circuit = repository.observeWeekCircuits(monday).first().single()
        assertEquals(instance, circuit.moduleInstanceId)
        assertEquals(1, circuit.modulePosition)
        assertEquals(1, repository.observeWeekModules(monday).first().size)
    }

    @Test
    fun editingTheTemplateNeverReachesAScheduledCopy() = runBlocking {
        val id = fingersModule()
        repository.scheduleModule(id, monday, monday)
        val template = repository.getModule(id)!!
        repository.updateModule(
            id,
            ModuleDraft(
                name = "Fingers v2",
                description = template.description,
                entries = listOf(
                    ModuleEntryDraft(
                        exerciseId = template.entries[0].exerciseId,
                        prescription = PrescriptionPayload(sets = 2, targetReps = 9),
                    ),
                ),
            )
        )

        assertEquals(6, week().single { it.name == "Max hangs" }.prescription?.sets)
        assertEquals("Fingers", repository.observeWeekModules(monday).first().single().name)
        assertEquals(3, week().size)
    }

    @Test
    fun movingAModuleMovesItsMembersAndTheirLogsTogether() = runBlocking {
        val instance = repository.scheduleModule(fingersModule(), monday, monday)!!
        val hang = week().single { it.name == "Max hangs" }
        logOneSet(hang.id, monday)

        // Trained work belongs to a day.
        assertFalse(repository.moveModule(instance, monday, null))
        assertTrue(repository.moveModule(instance, monday, wednesday))

        assertTrue(week().all { it.trainingDate == wednesday })
        assertEquals(wednesday, repository.observeWeekCircuits(monday).first().single().trainingDate)
        assertEquals(wednesday, repository.observeWeekModules(monday).first().single().trainingDate)
        val sets = repository.observeOccurrence(hang.id).first()!!.sets
        assertTrue(sets.all { it.trainingDate == wednesday })
    }

    @Test
    fun ungroupingKeepsEveryOccurrenceWhereItWas() = runBlocking {
        val instance = repository.scheduleModule(fingersModule(), monday, monday)!!

        repository.ungroupModule(instance)

        val occurrences = week()
        assertEquals(3, occurrences.size)
        assertTrue(occurrences.all { it.moduleInstanceId == null && it.trainingDate == monday })
        assertNull(repository.observeWeekCircuits(monday).first().single().moduleInstanceId)
        assertTrue(repository.observeWeekModules(monday).first().isEmpty())
    }

    @Test
    fun movingOneMemberOnItsOwnTakesItOutOfTheGroup() = runBlocking {
        repository.scheduleModule(fingersModule(), monday, monday)
        val hang = week().single { it.name == "Max hangs" }

        repository.moveOccurrence(hang.id, monday, tuesday)

        val moved = week().single { it.id == hang.id }
        assertNull(moved.moduleInstanceId)
        assertEquals(tuesday, moved.trainingDate)
        // The rest of the group is untouched.
        assertTrue(week().filter { it.id != hang.id }.all { it.moduleInstanceId != null })
    }

    @Test
    fun aTrainedModuleIsKeptAndItsTemplateCanGoWithoutTakingTheLog() = runBlocking {
        val id = fingersModule()
        val instance = repository.scheduleModule(id, monday, monday)!!
        val hang = week().single { it.name == "Max hangs" }
        logOneSet(hang.id, monday)

        assertEquals(1, repository.moduleRecordedExercises(instance))
        assertFalse(repository.removeModuleIfEmpty(instance))
        assertEquals(3, week().size)

        // The template goes from the list; what was scheduled and logged from it stays.
        assertTrue(repository.removeModule(id))
        assertTrue(repository.observeModules().first().isEmpty())
        assertEquals(1, repository.observeOccurrence(hang.id).first()!!.sets.size)
        assertNotNull(repository.observeWeekModules(monday).first().singleOrNull())
    }

    @Test
    fun anUntrainedModuleLeavesTheWeekCompletely() = runBlocking {
        val instance = repository.scheduleModule(fingersModule(), monday, monday)!!

        assertTrue(repository.removeModuleIfEmpty(instance))

        assertTrue(week().isEmpty())
        assertTrue(repository.observeWeekCircuits(monday).first().isEmpty())
        assertTrue(repository.observeWeekModules(monday).first().isEmpty())
    }

    @Test
    fun aRetiredExerciseIsFlaggedInTheModuleAndLeftOutWhenScheduled() = runBlocking {
        val id = fingersModule()
        val hang = repository.getModule(id)!!.entries.first { it.name == "Max hangs" }.exerciseId!!
        // Logged once, so removing it retires it rather than deleting it.
        repository.scheduleExercise(hang, monday.plusWeeks(1), monday.plusWeeks(1))
        val placed = repository.observeWeek(monday.plusWeeks(1)).first().single()
        logOneSet(placed.id, monday.plusWeeks(1))
        assertEquals(ExerciseRemoval.Outcome.RETIRED, repository.removeExercise(hang))

        assertEquals(listOf("Max hangs"), repository.getModule(id)!!.unavailableEntries.map { it.name })
        assertEquals(
            listOf("Max hangs"),
            repository.observeModules().first().single().unavailableEntries.map { it.name },
        )

        repository.scheduleModule(id, monday, monday)
        // The circuit's two stations, and no hang.
        assertEquals(listOf("Row", "Pull-up"), week().map { it.name })
    }

    @Test
    fun aCircuitKeepsItsCategoryAndItsCopyInTheWeekSnapshotsIt() = runBlocking {
        val row = exercise("Row")
        val id = repository.createRoutine(
            RoutineDraft(
                name = "Pull circuit",
                rounds = 3,
                transitionSeconds = 30,
                roundRestSeconds = 120,
                entries = listOf(RoutineEntryDraft(row, PrescriptionPayload(sets = 1, targetReps = 8))),
                category = ExerciseCategory.STRENGTH_CONDITIONING,
            )
        )
        assertEquals(ExerciseCategory.STRENGTH_CONDITIONING, repository.getRoutine(id)!!.category)
        val copy = repository.duplicateRoutine(id)!!
        assertEquals(ExerciseCategory.STRENGTH_CONDITIONING, repository.getRoutine(copy)!!.category)

        repository.scheduleRoutine(id, monday, monday)
        // Recategorising the template afterwards does not recolour the copy already placed.
        val template = repository.getRoutine(id)!!
        repository.updateRoutine(
            id,
            RoutineDraft(
                name = template.name,
                rounds = template.rounds,
                transitionSeconds = template.transitionSeconds,
                roundRestSeconds = template.roundRestSeconds,
                entries = template.entries.map { RoutineEntryDraft(it.exerciseId, it.prescription) },
                category = ExerciseCategory.FINGER_TRAINING,
            )
        )
        assertEquals(
            ExerciseCategory.STRENGTH_CONDITIONING,
            repository.observeWeekCircuits(monday).first().single().category,
        )
    }

    @Test
    fun somethingAddedAfterAnEmptyGroupStillComesAfterIt() = runBlocking {
        val empty = repository.createModule(ModuleDraft("Empty", null, emptyList()))
        repository.scheduleModule(empty, monday, monday)
        val squat = exercise("Squat")

        repository.scheduleExercise(squat, monday, monday)

        val group = repository.observeWeekModules(monday).first().single()
        assertTrue(week().single().orderIndex > group.orderIndex)
    }
}
