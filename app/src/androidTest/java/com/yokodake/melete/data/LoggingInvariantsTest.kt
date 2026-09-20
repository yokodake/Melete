package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ActualSetPayload
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
 * The invariants phase 2 is really about: snapshots stay put when templates change, actuals are
 * independent of what was planned, set identity does not depend on a set number, and a day's
 * session container is created behind the scenes and reused.
 */
@RunWith(AndroidJUnit4::class)
class LoggingInvariantsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MeleteDatabase
    private lateinit var repository: TrainingRepository

    private val monday = LocalDate.of(2026, 9, 21)
    private val tuesday = monday.plusDays(1)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MeleteDatabase::class.java)
            .build()
        repository = TrainingRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private fun rowDraft(sets: Int = 4, load: Double = 22.5) = ExerciseDraft(
        name = "Dumbbell row",
        mode = ExerciseMode.REPETITIONS,
        unilateral = true,
        measurementUnit = "kg",
        measurementMeaning = MeasurementMeaning.TOTAL_LOAD,
        notes = null,
        defaultPrescription = PrescriptionPayload(
            sets = sets,
            targetReps = 8,
            restSeconds = 30,
            measurement = Measurement(load, "kg", MeasurementMeaning.TOTAL_LOAD),
            rpe = 8.0,
        ),
    )

    private suspend fun occurrenceOf(id: String) =
        repository.observeOccurrence(id).first()!!

    @Test
    fun anExerciseAndItsPrescriptionAreCreatedFromDataAlone() = runBlocking {
        val id = repository.createExercise(rowDraft())
        val exercise = repository.getLibraryExercise(id)

        assertNotNull(exercise)
        assertTrue(exercise!!.unilateral)
        assertEquals("kg", exercise.measurementUnit)
        assertEquals(4, exercise.defaultPrescription?.sets)
        assertEquals(8, exercise.defaultPrescription?.targetReps)
        assertEquals(30, exercise.defaultPrescription?.restSeconds)
        assertEquals(8.0, exercise.defaultPrescription?.rpe ?: 0.0, 0.001)
        // Nothing about "dumbbell row" exists in code: an unloaded timed stretch is the same path.
        val stretchId = repository.createExercise(
            ExerciseDraft(
                name = "Couch stretch",
                mode = ExerciseMode.DURATION,
                unilateral = true,
                measurementUnit = null,
                measurementMeaning = null,
                notes = "Knee against the wall",
                defaultPrescription = PrescriptionPayload(sets = 2, targetDurationSeconds = 90),
            )
        )
        val stretch = repository.getLibraryExercise(stretchId)!!
        assertNull(stretch.measurementUnit)
        assertNull(stretch.defaultPrescription?.measurement)
        assertEquals(90, stretch.defaultPrescription?.targetDurationSeconds)
    }

    @Test
    fun editingTheLibraryDefaultLeavesAnAlreadyScheduledCopyAndItsActualsAlone() = runBlocking {
        val exerciseId = repository.createExercise(rowDraft(sets = 4, load = 22.5))
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)
        repository.logSet(
            occurrenceId = occurrenceId,
            payload = ActualSetPayload(
                reps = 8,
                measurement = Measurement(22.5, "kg", MeasurementMeaning.TOTAL_LOAD),
            ),
            side = BodySide.LEFT,
            trainingDate = tuesday,
        )

        repository.updateExercise(
            exerciseId,
            rowDraft(sets = 6, load = 30.0).copy(name = "Dumbbell row (new grip)"),
        )

        val detail = occurrenceOf(occurrenceId)
        assertEquals("Dumbbell row", detail.occurrence.name)
        assertEquals(4, detail.occurrence.prescription?.sets)
        assertEquals(22.5, detail.occurrence.prescription?.measurement?.value ?: 0.0, 0.001)
        assertEquals(1, detail.sets.size)
        assertEquals(22.5, detail.sets.first().payload.measurement?.value ?: 0.0, 0.001)

        // The library itself did change.
        val library = repository.getLibraryExercise(exerciseId)!!
        assertEquals("Dumbbell row (new grip)", library.name)
        assertEquals(6, library.defaultPrescription?.sets)
    }

    @Test
    fun sixSetsCanBeLoggedAgainstAPlanOfFour() = runBlocking {
        val exerciseId = repository.createExercise(rowDraft(sets = 4))
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)

        repeat(6) { index ->
            repository.logSet(
                occurrenceId = occurrenceId,
                payload = ActualSetPayload(reps = 8 - index),
                side = if (index % 2 == 0) BodySide.LEFT else BodySide.RIGHT,
                trainingDate = tuesday,
            )
        }

        val detail = occurrenceOf(occurrenceId)
        assertEquals(4, detail.occurrence.prescription?.sets)
        assertEquals(6, detail.sets.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), detail.sets.map { it.orderIndex })
    }

    @Test
    fun anUnplannedSetIsValidAndStillBelongsToAnOccurrenceAndASession() = runBlocking {
        val exerciseId = repository.createExercise(
            rowDraft().copy(defaultPrescription = PrescriptionPayload(sets = 0))
        )
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)
        val setId = repository.logSet(
            occurrenceId = occurrenceId,
            payload = ActualSetPayload(reps = 12),
            side = null,
            trainingDate = tuesday,
        )

        val stored = database.loggingDao().getSet(setId)!!
        assertEquals(occurrenceId, stored.occurrenceId)
        assertTrue(stored.sessionId.isNotBlank())
        assertEquals(tuesday.toEpochDay(), stored.trainingDateEpochDay)
    }

    @Test
    fun eachSideIsRecordedExplicitlyAndCanCarryDifferentLoads() = runBlocking {
        val exerciseId = repository.createExercise(rowDraft())
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)

        repository.logSet(
            occurrenceId,
            ActualSetPayload(reps = 8, measurement = Measurement(22.5, "kg", MeasurementMeaning.TOTAL_LOAD)),
            BodySide.LEFT,
            tuesday,
        )
        repository.logSet(
            occurrenceId,
            ActualSetPayload(reps = 7, measurement = Measurement(20.0, "kg", MeasurementMeaning.TOTAL_LOAD)),
            BodySide.RIGHT,
            tuesday,
        )

        val sets = occurrenceOf(occurrenceId).sets
        assertEquals(2, sets.size)
        val left = sets.single { it.side == BodySide.LEFT }
        val right = sets.single { it.side == BodySide.RIGHT }
        assertEquals(22.5, left.payload.measurement?.value ?: 0.0, 0.001)
        assertEquals(20.0, right.payload.measurement?.value ?: 0.0, 0.001)
        assertEquals(8, left.payload.reps)
        assertEquals(7, right.payload.reps)
    }

    @Test
    fun deletingASetDoesNotMoveCorrectionsOntoAnotherRecord() = runBlocking {
        val exerciseId = repository.createExercise(rowDraft())
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)
        val first = repository.logSet(occurrenceId, ActualSetPayload(reps = 8), null, tuesday)
        val second = repository.logSet(occurrenceId, ActualSetPayload(reps = 7), null, tuesday)
        val third = repository.logSet(occurrenceId, ActualSetPayload(reps = 6), null, tuesday)

        repository.deleteSet(first)
        repository.updateSet(third, ActualSetPayload(reps = 5), null)

        val sets = occurrenceOf(occurrenceId).sets
        assertEquals(listOf(second, third), sets.map { it.id })
        assertEquals(7, sets.single { it.id == second }.payload.reps)
        assertEquals(5, sets.single { it.id == third }.payload.reps)
    }

    @Test
    fun oneDayIsOneSessionEvenAcrossSeveralExercises() = runBlocking {
        val first = repository.createExercise(rowDraft().copy(name = "Back squat"))
        val second = repository.createExercise(rowDraft().copy(name = "Max hangs"))
        val firstOccurrence = repository.scheduleExercise(first, monday, tuesday)
        val secondOccurrence = repository.scheduleExercise(second, monday, tuesday)
        val laterOccurrence = repository.scheduleExercise(first, monday, tuesday.plusDays(2))

        val a = repository.logSet(firstOccurrence, ActualSetPayload(reps = 5), null, tuesday)
        val b = repository.logSet(secondOccurrence, ActualSetPayload(reps = 5), null, tuesday)
        val c = repository.logSet(
            laterOccurrence,
            ActualSetPayload(reps = 5),
            null,
            tuesday.plusDays(2),
        )

        val dao = database.loggingDao()
        assertEquals(dao.getSet(a)!!.sessionId, dao.getSet(b)!!.sessionId)
        assertFalse(dao.getSet(a)!!.sessionId == dao.getSet(c)!!.sessionId)
    }

    @Test
    fun loggingAnUndatedItemFilesItUnderTheChosenDate() = runBlocking {
        val exerciseId = repository.createExercise(rowDraft())
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, trainingDate = null)
        assertNull(occurrenceOf(occurrenceId).occurrence.trainingDate)

        val backfillDate = monday.plusDays(3)
        repository.logSet(occurrenceId, ActualSetPayload(reps = 8), null, backfillDate)

        val detail = occurrenceOf(occurrenceId)
        assertEquals(backfillDate, detail.occurrence.trainingDate)
        assertEquals(backfillDate, detail.sets.single().trainingDate)
    }

    @Test
    fun previousResultsComeFromOtherOccurrencesOfTheSameExercise() = runBlocking {
        val exerciseId = repository.createExercise(rowDraft())
        val lastWeek = repository.scheduleExercise(
            exerciseId,
            monday.minusWeeks(1),
            tuesday.minusWeeks(1),
        )
        repository.logSet(
            lastWeek,
            ActualSetPayload(reps = 8, measurement = Measurement(20.0, "kg", MeasurementMeaning.TOTAL_LOAD)),
            BodySide.LEFT,
            tuesday.minusWeeks(1),
        )
        val thisWeek = repository.scheduleExercise(exerciseId, monday, tuesday)

        val previous = repository.observePreviousResults(exerciseId, thisWeek).first()
        assertEquals(1, previous.size)
        assertEquals(tuesday.minusWeeks(1), previous.single().trainingDate)
        assertEquals(20.0, previous.single().sets.single().payload.measurement?.value ?: 0.0, 0.001)

        // And the logger for last week's copy does not show itself as history.
        assertTrue(repository.observePreviousResults(exerciseId, lastWeek).first()
            .none { it.trainingDate == tuesday.minusWeeks(1) })
    }

    @Test
    fun anOccurrenceWithRecordedSetsIsNotSilentlyDeleted() = runBlocking {
        val exerciseId = repository.createExercise(rowDraft())
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)
        repository.logSet(occurrenceId, ActualSetPayload(reps = 8), null, tuesday)

        assertFalse(repository.deleteOccurrenceIfEmpty(occurrenceId))
        assertNotNull(repository.observeOccurrence(occurrenceId).first())

        val empty = repository.scheduleExercise(exerciseId, monday, tuesday)
        assertTrue(repository.deleteOccurrenceIfEmpty(empty))
    }

    @Test
    fun markingCompletionIsSeparateFromHavingRecordedSets() = runBlocking {
        val exerciseId = repository.createExercise(rowDraft())
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)

        repository.setOccurrenceState(occurrenceId, OccurrenceState.SKIPPED)
        assertEquals(OccurrenceState.SKIPPED, occurrenceOf(occurrenceId).occurrence.state)

        // Sets can still be recorded, and recording does not flip the state by itself.
        repository.logSet(occurrenceId, ActualSetPayload(reps = 8), null, tuesday)
        assertEquals(OccurrenceState.SKIPPED, occurrenceOf(occurrenceId).occurrence.state)

        repository.setOccurrenceState(occurrenceId, OccurrenceState.COMPLETED)
        assertEquals(OccurrenceState.COMPLETED, occurrenceOf(occurrenceId).occurrence.state)
    }

    @Test
    fun editingThisWeeksPlanDoesNotTouchTheLibraryOrRecordedSets() = runBlocking {
        val exerciseId = repository.createExercise(rowDraft(sets = 4, load = 22.5))
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)
        repository.logSet(occurrenceId, ActualSetPayload(reps = 8), null, tuesday)

        repository.updateOccurrencePrescription(
            occurrenceId,
            PrescriptionPayload(
                sets = 2,
                targetReps = 12,
                measurement = Measurement(15.0, "kg", MeasurementMeaning.TOTAL_LOAD),
            ),
        )

        val detail = occurrenceOf(occurrenceId)
        assertEquals(2, detail.occurrence.prescription?.sets)
        assertEquals(4, repository.getLibraryExercise(exerciseId)?.defaultPrescription?.sets)
        assertEquals(1, detail.sets.size)
        assertEquals(8, detail.sets.single().payload.reps)
    }

    @Test
    fun commentsBelongToTheOccurrenceNotToASet() = runBlocking {
        val exerciseId = repository.createExercise(rowDraft())
        val occurrenceId = repository.scheduleExercise(exerciseId, monday, tuesday)
        repository.logSet(occurrenceId, ActualSetPayload(reps = 8), null, tuesday)

        repository.setOccurrenceComment(occurrenceId, "Elbow felt better with a narrower grip")

        val detail = occurrenceOf(occurrenceId)
        assertEquals("Elbow felt better with a narrower grip", detail.occurrence.comment)
        repository.setOccurrenceComment(occurrenceId, "   ")
        assertNull(occurrenceOf(occurrenceId).occurrence.comment)
    }
}
