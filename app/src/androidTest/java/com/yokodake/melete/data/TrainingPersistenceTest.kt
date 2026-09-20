package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.core.WeekMath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Persistence is checked against a real on-disk database that is closed and reopened, because the
 * acceptance question is "is it still there after the app is killed", not "does the DAO return
 * what was just inserted".
 */
@RunWith(AndroidJUnit4::class)
class TrainingPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "melete-persistence-test.db"
    private val weekStart = WeekMath.weekStartOf(LocalDate.of(2026, 9, 23))

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

    @Test
    fun sampleWeekSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        TrainingRepository(database).seedSampleWeek(weekStart)

        database.close()
        database = open()
        val repository = TrainingRepository(database)

        val week = repository.observeWeek(weekStart).first()
        assertEquals(5, week.size)
        assertEquals(1, week.count { it.trainingDate == null })
        assertEquals(4, week.count { it.trainingDate != null })
        assertTrue(week.all { it.isSampleData })
        assertTrue(week.all { date -> date.trainingDate?.let { WeekMath.contains(weekStart, it) } != false })
    }

    @Test
    fun prescriptionSnapshotsAreReadableAfterReopening() = runBlocking {
        TrainingRepository(database).seedSampleWeek(weekStart)
        database.close()
        database = open()

        val week = TrainingRepository(database).observeWeek(weekStart).first()
        val row = week.first { it.name.endsWith("Dumbbell row") }
        assertNotNull(row.prescription)
        assertEquals(4, row.prescription?.sets)
        assertEquals(8, row.prescription?.targetReps)
        assertEquals(22.5, row.prescription?.measurement?.value ?: 0.0, 0.001)
        assertTrue(row.unilateral)
        assertTrue(week.none { it.prescriptionUnreadable })
    }

    @Test
    fun neighbouringWeeksAreNotShownInTheSeededWeek() = runBlocking {
        val repository = TrainingRepository(database)
        repository.seedSampleWeek(weekStart)

        assertTrue(repository.observeWeek(weekStart.minusWeeks(1)).first().isEmpty())
        assertTrue(repository.observeWeek(weekStart.plusWeeks(1)).first().isEmpty())
    }

    @Test
    fun clearingSampleDataRemovesEveryMarkedRow() = runBlocking {
        val repository = TrainingRepository(database)
        repository.seedSampleWeek(weekStart)
        assertTrue(repository.sampleDataPresent.first())

        repository.clearSampleData()

        assertTrue(repository.observeWeek(weekStart).first().isEmpty())
        assertTrue(!repository.sampleDataPresent.first())
    }
}
