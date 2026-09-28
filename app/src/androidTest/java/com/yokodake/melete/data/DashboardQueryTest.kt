package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** What the dashboard reads: completed exercises in a date range, and nothing else. */
@RunWith(AndroidJUnit4::class)
class DashboardQueryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MeleteDatabase
    private lateinit var repository: TrainingRepository

    private val monday = LocalDate.of(2026, 9, 21)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MeleteDatabase::class.java).build()
        repository = TrainingRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun anUpcomingPlanIsFutureDatedOrUndatedWorkNotYetDone() = runBlocking {
        val today = monday.plusDays(2)
        val exerciseId = repository.createExercise(
            ExerciseDraft(
                name = "Hang", mode = ExerciseMode.DURATION, unilateral = false,
                measurementUnit = null, measurementMeaning = null, notes = null, description = null,
                category = null, defaultPrescription = PrescriptionPayload(sets = 1, targetDurationSeconds = 10),
            )
        )
        assertEquals(false, repository.observeHasUpcomingPlan(today).first())

        // Past or finished work is not a plan.
        repository.scheduleExercise(exerciseId, monday, monday)
        val skipped = repository.scheduleExercise(exerciseId, monday, today.plusDays(1))
        repository.setOccurrenceState(skipped, OccurrenceState.SKIPPED)
        val undatedDone = repository.scheduleExercise(exerciseId, monday, null)
        repository.saveLogs(today, listOf(OccurrenceLogWrite(undatedDone, completed = true)))
        repository.scheduleExercise(exerciseId, monday.minusWeeks(1), null) // last week's Anytime
        assertEquals(false, repository.observeHasUpcomingPlan(today).first())

        // An unfinished Anytime item this week is a plan, even with today empty.
        val anytime = repository.scheduleExercise(exerciseId, monday, null)
        assertEquals(true, repository.observeHasUpcomingPlan(today).first())
        repository.deleteOccurrenceIfEmpty(anytime)

        // So is anything dated later.
        repository.scheduleExercise(exerciseId, monday.plusWeeks(3), monday.plusWeeks(3))
        assertEquals(true, repository.observeHasUpcomingPlan(today).first())
    }

    @Test
    fun onlyCompletedExercisesInRangeAreRead() = runBlocking {
        val exerciseId = repository.createExercise(
            ExerciseDraft(
                name = "Squat", mode = ExerciseMode.REPETITIONS, unilateral = false,
                measurementUnit = null, measurementMeaning = null, notes = null, description = null,
                category = null, defaultPrescription = PrescriptionPayload(sets = 3, targetReps = 5),
            )
        )
        suspend fun placed(date: LocalDate) = repository.scheduleExercise(exerciseId, monday, date)
        suspend fun complete(id: String, date: LocalDate, seconds: Int?) = repository.saveLogs(
            date, listOf(OccurrenceLogWrite(id, completed = true, durationSeconds = seconds, durationManual = true)),
        )

        val done = placed(monday).also { complete(it, monday, 1800) }
        placed(monday.plusDays(1)) // planned only
        placed(monday.plusDays(2)).also { repository.setOccurrenceState(it, OccurrenceState.SKIPPED) }
        placed(monday.plusDays(3)).also { complete(it, monday.plusDays(3), null) }
        placed(monday.plusDays(10)).also { complete(it, monday.plusDays(10), 600) } // out of range

        val read = repository.observeCompleted(monday, monday.plusDays(6)).first()
        assertEquals(2, read.size)
        assertEquals(setOf(1800, null), read.map { it.durationSeconds }.toSet())
        assertEquals(monday, repository.observeFirstCompletedDate().first())
        assertEquals(done, repository.observeOccurrence(done).first()!!.occurrence.id)
    }

    @Test
    fun recordsCarryTheirSetsAndAnExercisesHistoryHasEveryOccurrence() = runBlocking {
        val exerciseId = repository.createExercise(
            ExerciseDraft(
                name = "Weighted pull-up", mode = ExerciseMode.REPETITIONS, unilateral = false,
                measurementUnit = "kg", measurementMeaning = MeasurementMeaning.ADDED_LOAD, notes = null,
                description = null, category = null, defaultPrescription = PrescriptionPayload(sets = 2, targetReps = 5),
            )
        )
        val first = repository.scheduleExercise(exerciseId, monday, monday)
        fun set(load: Double) = SetWrite(ActualSetPayload(reps = 5, measurement = Measurement(load, "kg", MeasurementMeaning.ADDED_LOAD)), null)
        repository.saveLogs(monday, listOf(OccurrenceLogWrite(first, completed = true, sets = listOf(set(0.0), set(-5.0)))))
        val later = monday.plusDays(10)
        val second = repository.scheduleExercise(exerciseId, monday.plusWeeks(1), later)
        repository.logSet(second, ActualSetPayload(reps = 5, measurement = Measurement(2.5, "kg", MeasurementMeaning.ADDED_LOAD)), null, later)
        repository.scheduleExercise(exerciseId, monday.plusWeeks(2), monday.plusWeeks(2)) // planned only

        val inWeek = repository.observeCompletedRecords(monday, monday.plusDays(6)).first()
        assertEquals(listOf(first), inWeek.map { it.occurrence.id })
        assertEquals(listOf(0.0, -5.0), inWeek.single().sets.map { it.payload.measurement?.value })

        val all = repository.observeExerciseRecords(exerciseId).first()
        assertEquals(3, all.size)
        assertEquals(1, all.single { it.occurrence.id == second }.sets.size)
    }
}
