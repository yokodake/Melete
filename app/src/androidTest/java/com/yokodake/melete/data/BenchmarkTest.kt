package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.backup.BackupService
import com.yokodake.melete.data.entity.BenchmarkMeasure
import com.yokodake.melete.data.model.MeasurementMeaning
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Benchmarks are a record apart from training: recording writes a result and nothing else, the
 * definition can change without rewriting old results, and results are never lost to hiding or to
 * a restore.
 */
@RunWith(AndroidJUnit4::class)
class BenchmarkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MeleteDatabase
    private lateinit var benchmarks: BenchmarkRepository

    private val monday = LocalDate.of(2026, 9, 28)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MeleteDatabase::class.java).build()
        benchmarks = BenchmarkRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private fun draft(unilateral: Boolean = false, unit: String = "kg") = BenchmarkDraft(
        name = "One-arm lift",
        measure = BenchmarkMeasure.LOAD,
        unit = unit,
        loadMeaning = MeasurementMeaning.TOTAL_LOAD,
        unilateral = unilateral,
        higherIsBetter = true,
        protocol = "20 mm · 7 s",
    )

    @Test
    fun recordingAResultCreatesNoTraining() = runBlocking {
        val id = benchmarks.create(draft())
        benchmarks.record(id, monday, 30.0, null, "felt strong")

        val training = TrainingRepository(database)
        assertTrue(training.observeWeek(monday).first().isEmpty())
        assertEquals(0, database.backupDao().sessions().size)
        assertEquals(0, database.backupDao().sets().size)

        val day = benchmarks.observeDayResults(monday, monday.plusDays(6)).first().single()
        assertEquals("One-arm lift", day.name)
        assertEquals("30 kg", day.text)
    }

    @Test
    fun aResultKeepsTheTermsItWasRecordedIn() = runBlocking {
        val id = benchmarks.create(draft(unilateral = true))
        benchmarks.record(id, monday, 25.0, 23.0, null)
        benchmarks.update(id, draft(unilateral = false, unit = "lb"))

        val standing = benchmarks.observeStanding(id).first()!!
        assertEquals("L 25 · R 23 kg", standing.latest!!.text)
        // Recorded in kg, so it no longer competes for a best measured in lb.
        assertNull(standing.best)
    }

    @Test
    fun hidingKeepsResultsAndOnlyAnUnusedBenchmarkCanBeDeleted() = runBlocking {
        val used = benchmarks.create(draft())
        val resultId = benchmarks.record(used, monday, 30.0, null, null)!!
        benchmarks.setHidden(used, true)
        assertTrue(benchmarks.observeStanding(used).first()!!.benchmark.hidden)
        assertEquals(1, benchmarks.observeStanding(used).first()!!.results.size)
        assertFalse(benchmarks.deleteIfUnused(used))

        benchmarks.setHidden(used, false)
        assertFalse(benchmarks.observeStanding(used).first()!!.benchmark.hidden)

        benchmarks.deleteResult(resultId)
        assertTrue(benchmarks.deleteIfUnused(used))
        assertNull(benchmarks.observeStanding(used).first())
    }

    @Test
    fun aResultNeedsAValueAndCanBeCorrected() = runBlocking {
        val id = benchmarks.create(draft())
        assertNull(benchmarks.record(id, monday, null, null, null))
        val resultId = benchmarks.record(id, monday, 30.0, null, null)!!
        assertTrue(benchmarks.updateResult(resultId, monday.minusDays(1), 32.5, null, "typo"))
        val result = benchmarks.observeStanding(id).first()!!.latest!!
        assertEquals(monday.minusDays(1), result.date)
        assertEquals(32.5, result.value!!, 0.0)
        assertEquals("typo", result.note)
    }

    @Test
    fun benchmarksSurviveExportAndRestore() = runBlocking {
        val id = benchmarks.create(draft(unilateral = true))
        benchmarks.record(id, monday, 25.0, 23.0, "note")
        benchmarks.setHidden(id, true)

        val service = BackupService(database)
        val exported = service.export()
        assertEquals(2, exported.formatVersion)

        service.restore(exported)
        val again = service.export()
        assertEquals(exported.copy(exportedAt = ""), again.copy(exportedAt = ""))
        val standing = benchmarks.observeStanding(id).first()!!
        assertTrue(standing.benchmark.hidden)
        assertEquals("L 25 · R 23 kg", standing.latest!!.text)
    }
}
