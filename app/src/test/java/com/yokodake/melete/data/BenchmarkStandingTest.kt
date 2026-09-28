package com.yokodake.melete.data

import com.yokodake.melete.data.backup.BackupValidator
import com.yokodake.melete.data.backup.BenchmarkRecord
import com.yokodake.melete.data.backup.BenchmarkResultRecord
import com.yokodake.melete.data.backup.MeleteBackup
import com.yokodake.melete.data.entity.BenchmarkMeasure
import com.yokodake.melete.data.model.MeasurementMeaning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BenchmarkStandingTest {

    private val sep20 = LocalDate.of(2026, 9, 20)
    private val sep22 = LocalDate.of(2026, 9, 22)
    private val sep24 = LocalDate.of(2026, 9, 24)

    private fun benchmark(
        unilateral: Boolean = false,
        higherIsBetter: Boolean = true,
        unit: String = "kg",
        meaning: MeasurementMeaning? = MeasurementMeaning.ADDED_LOAD,
    ) = Benchmark(
        id = "b", name = "Weighted pull-up", measure = BenchmarkMeasure.LOAD, unit = unit,
        loadMeaning = meaning, unilateral = unilateral, higherIsBetter = higherIsBetter,
        protocol = null, orderIndex = 0, hidden = false,
    )

    private fun result(
        date: LocalDate,
        value: Double?,
        right: Double? = null,
        typedAt: Long = 0,
        unit: String = "kg",
        meaning: MeasurementMeaning? = MeasurementMeaning.ADDED_LOAD,
        unilateral: Boolean = right != null,
    ) = BenchmarkResult(
        id = "$date-$value-$right-$typedAt", benchmarkId = "b", date = date, value = value,
        valueRight = right, unit = unit, loadMeaning = meaning, unilateral = unilateral,
        note = null, recordedAtEpochMs = typedAt,
    )

    @Test
    fun `latest is the most recent test and best keeps an older maximum with its date`() {
        val standing = BenchmarkStanding.of(
            benchmark(),
            // Typed in out of order: the test date decides, not when it was entered.
            listOf(result(sep24, 30.0, typedAt = 1), result(sep20, 35.0, typedAt = 2)),
        )
        assertEquals(sep24, standing.latest!!.date)
        assertEquals("+30 kg", standing.latest!!.text)
        assertEquals(BestValue(35.0, sep20), standing.best)
        assertEquals("+35 kg", standing.bestText)
    }

    @Test
    fun `a tie keeps the date the value was first reached, and lower can be better`() {
        val tied = BenchmarkStanding.of(benchmark(), listOf(result(sep20, 30.0), result(sep24, 30.0)))
        assertEquals(sep20, tied.best!!.date)

        val timed = BenchmarkStanding.of(
            benchmark(higherIsBetter = false, unit = "s", meaning = null),
            listOf(result(sep20, 12.4, unit = "s", meaning = null), result(sep24, 11.9, unit = "s", meaning = null)),
        )
        assertEquals(BestValue(11.9, sep24), timed.best)
    }

    @Test
    fun `left and right keep their own bests and dates`() {
        val standing = BenchmarkStanding.of(
            benchmark(unilateral = true),
            listOf(result(sep20, 27.0, 23.0), result(sep24, 25.0, 25.0)),
        )
        assertEquals(BestValue(27.0, sep20), standing.best)
        assertEquals(BestValue(25.0, sep24), standing.bestRight)
        assertEquals("L +27 · R +25 kg", standing.bestText)
        assertEquals("L +25 · R +25 kg", standing.latest!!.text)
    }

    @Test
    fun `zero and assistance are results, and assistance is below bodyweight`() {
        val standing = BenchmarkStanding.of(
            benchmark(),
            listOf(result(sep20, -10.0), result(sep22, 0.0)),
        )
        assertEquals(BestValue(0.0, sep22), standing.best)
        assertEquals("+0 kg", standing.latest!!.text)
        assertEquals("−10 kg", result(sep20, -10.0).text)
    }

    @Test
    fun `results in another unit never compete for best`() {
        val standing = BenchmarkStanding.of(
            benchmark(),
            listOf(result(sep20, 80.0, unit = "lb"), result(sep24, 30.0)),
        )
        assertEquals(BestValue(30.0, sep24), standing.best)
        assertEquals(2, standing.results.size)
    }

    @Test
    fun `no results means no latest and no best`() {
        val standing = BenchmarkStanding.of(benchmark(), emptyList())
        assertNull(standing.latest)
        assertNull(standing.bestText)
    }

    @Test
    fun `total loads and other numbers read unsigned, decimals kept`() {
        assertEquals("60 kg", BenchmarkFormat.values(60.0, null, false, "kg", MeasurementMeaning.TOTAL_LOAD))
        assertEquals("7.5 s", BenchmarkFormat.values(7.5, null, false, "s", null))
        assertEquals("L 25 · R — kg", BenchmarkFormat.values(25.0, null, true, "kg", null))
        assertEquals("12", BenchmarkFormat.values(12.0, null, false, "", null))
    }

    // ------------------------------------------------------------------ backups

    @Test
    fun `a backup with benchmarks survives the file and a version 1 file has none`() {
        val backup = MeleteBackup(
            exportedAt = "2026-09-28T10:00:00Z",
            schemaVersion = 11,
            benchmarks = listOf(
                BenchmarkRecord(
                    id = "b", name = "One-arm lift", measure = BenchmarkMeasure.LOAD, unit = "kg",
                    loadMeaning = MeasurementMeaning.TOTAL_LOAD, unilateral = true, higherIsBetter = true,
                    protocol = "20 mm · 7 s", orderIndex = 0, createdAtEpochMs = 1,
                    results = listOf(
                        BenchmarkResultRecord(
                            id = "r", date = "2026-09-24", value = 25.0, valueRight = 23.0, unit = "kg",
                            loadMeaning = MeasurementMeaning.TOTAL_LOAD, unilateral = true,
                            recordedAtEpochMs = 2,
                        )
                    ),
                )
            ),
        )
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val text = json.encodeToString(MeleteBackup.serializer(), backup)
        assertEquals(backup, json.decodeFromString(MeleteBackup.serializer(), text))
        assertTrue(BackupValidator.problems(backup).isEmpty())

        val v1 = json.decodeFromString(
            MeleteBackup.serializer(),
            """{ "format": "melete-backup", "formatVersion": 1, "exportedAt": "x", "schemaVersion": 10 }""",
        )
        assertTrue(v1.benchmarks.isEmpty())
        assertTrue(BackupValidator.problems(v1).isEmpty())
    }

    @Test
    fun `a result with no value, or a bad date, is refused`() {
        val record = BenchmarkRecord(
            id = "b", name = "Hang", measure = BenchmarkMeasure.DURATION, unit = "s",
            unilateral = false, higherIsBetter = true, orderIndex = 0, createdAtEpochMs = 1,
            results = listOf(
                BenchmarkResultRecord(id = "r", date = "2026-13-40", unit = "s", unilateral = false, recordedAtEpochMs = 1),
            ),
        )
        val problems = BackupValidator.problems(MeleteBackup(exportedAt = "x", schemaVersion = 11, benchmarks = listOf(record)))
        assertTrue(problems.any { "unreadable date" in it })
        assertTrue(problems.any { "holds no value" in it })
    }
}
