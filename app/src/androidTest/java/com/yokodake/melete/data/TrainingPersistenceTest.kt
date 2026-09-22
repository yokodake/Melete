package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.core.WeekMath
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Persistence is checked against a real on-disk database that is closed and reopened, because the
 * acceptance question is "is it still there after the app is killed", not "does the DAO return
 * what was just inserted".
 *
 * Written through the repository rather than seeded, now that there is no sample data to seed —
 * which also makes it the path the app itself takes, and that is the one worth proving.
 */
@RunWith(AndroidJUnit4::class)
class TrainingPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "melete-persistence-test.db"
    private val weekStart = WeekMath.weekStartOf(LocalDate.of(2026, 9, 23))
    private val wednesday = weekStart.plusDays(2)

    private lateinit var database: MeleteDatabase

    private fun open(): MeleteDatabase =
        Room.databaseBuilder(context, MeleteDatabase::class.java, databaseName).build()

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = open()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    /** Reopens the file and hands back a repository onto it, as a cold start would. */
    private fun reopen(): TrainingRepository {
        database.close()
        database = open()
        return TrainingRepository(database)
    }

    /** Two exercises in the library, one placed on a day and one waiting without a date. */
    private suspend fun seedWeek(repository: TrainingRepository): String {
        val hang = repository.createExercise(
            ExerciseDraft(
                name = "Max hangs 20 mm",
                mode = ExerciseMode.DURATION,
                unilateral = false,
                measurementUnit = "kg",
                measurementMeaning = MeasurementMeaning.ADDED_LOAD,
                notes = null,
                description = "Half crimp.",
                category = ExerciseCategory.FINGER_TRAINING,
                defaultPrescription = PrescriptionPayload(
                    sets = 5,
                    targetDurationSeconds = 10,
                    restSeconds = 180,
                    effort = EffortLevel.HARD,
                ),
            )
        )
        val stretch = repository.createExercise(
            ExerciseDraft(
                name = "Couch stretch",
                mode = ExerciseMode.DURATION,
                unilateral = true,
                measurementUnit = null,
                measurementMeaning = null,
                notes = null,
                description = null,
                category = ExerciseCategory.FLEXIBILITY,
                defaultPrescription = PrescriptionPayload(sets = 2, targetDurationSeconds = 90),
            )
        )
        repository.scheduleExercise(stretch, weekStart, trainingDate = null)
        return repository.scheduleExercise(hang, weekStart, wednesday)
    }

    @Test
    fun aPlannedWeekSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        seedWeek(TrainingRepository(database))

        val week = reopen().observeWeek(weekStart).first()
        assertEquals(2, week.size)
        assertEquals(1, week.count { it.trainingDate == null })
        assertEquals(1, week.count { it.trainingDate == wednesday })
    }

    @Test
    fun theSnapshotAndItsPrescriptionCopySurviveTheReopen() = runBlocking {
        seedWeek(TrainingRepository(database))

        val hang = reopen().observeWeek(weekStart).first().single { it.trainingDate == wednesday }
        assertEquals("Max hangs 20 mm", hang.name)
        assertEquals(ExerciseMode.DURATION, hang.mode)
        assertEquals("kg", hang.measurementUnit)
        // The meaning travels with the snapshot: added load must never read back as total load.
        assertEquals(MeasurementMeaning.ADDED_LOAD, hang.measurementMeaning)
        assertEquals(ExerciseCategory.FINGER_TRAINING, hang.category)
        assertNotNull(hang.prescription)
        assertEquals(5, hang.prescription?.sets)
        assertEquals(10, hang.prescription?.targetDurationSeconds)
        assertEquals(EffortLevel.HARD, hang.prescription?.effort)
        // Absent stays absent rather than coming back as zero.
        assertNull(hang.prescription?.targetReps)
    }

    @Test
    fun aLoggedWorkoutSurvivesTheReopen() = runBlocking {
        val repository = TrainingRepository(database)
        val occurrenceId = seedWeek(repository)
        repository.saveLogs(
            trainingDate = wednesday,
            writes = listOf(
                OccurrenceLogWrite(
                    occurrenceId = occurrenceId,
                    completed = true,
                    sets = List(3) {
                        SetWrite(
                            ActualSetPayload(
                                durationSeconds = 10,
                                measurement = Measurement(20.0, "kg", MeasurementMeaning.ADDED_LOAD),
                                effort = EffortLevel.HARD,
                            ),
                            null,
                        )
                    },
                    comment = "felt strong",
                    durationSeconds = 950,
                )
            ),
        )

        val detail = reopen().observeOccurrence(occurrenceId).first()!!
        assertEquals(3, detail.sets.size)
        assertEquals(OccurrenceState.COMPLETED, detail.occurrence.state)
        assertEquals("felt strong", detail.occurrence.comment)
        assertEquals(950, detail.occurrence.loggedDurationSeconds)
        assertEquals(20.0, detail.sets.first().payload.measurement?.value)
        // And every set came back filed under the day it was performed on.
        assertTrue(detail.sets.all { it.trainingDate == wednesday })
    }

    @Test
    fun anEmptyWeekIsEmptyRatherThanMissing() = runBlocking {
        seedWeek(TrainingRepository(database))

        assertTrue(reopen().observeWeek(weekStart.plusWeeks(4)).first().isEmpty())
    }
}
