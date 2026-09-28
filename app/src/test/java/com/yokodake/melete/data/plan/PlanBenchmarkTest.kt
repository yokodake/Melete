package com.yokodake.melete.data.plan

import com.yokodake.melete.data.entity.BenchmarkMeasure
import com.yokodake.melete.data.model.MeasurementMeaning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanBenchmarkTest {

    private fun resolve(json: String, library: LibraryIndex = LibraryIndex.EMPTY, cutoff: LocalDate? = null) =
        PlanCheck.resolve(
            PlanJson.decodeFromString(PlanFile.serializer(), """{ "format": "melete-plan", "version": 1, $json }"""),
            library,
            cutoff = cutoff,
        )

    private val hang = """
        "benchmarks": [{
          "name": "Max hang", "measure": "load", "unit": "kg", "meaning": "ADDED_LOAD",
          "protocol": "20 mm · 7 s", "goal": "150% BW",
          "results": [
            { "date": "2024-11-15", "value": -5, "bodyweightPercent": 92 },
            { "date": "2025-11-15", "value": 0 },
            { "date": "2026-02-15", "value": 20, "bodyweightPercent": 127.5, "note": "fresh" }
          ]
        }]
    """

    @Test
    fun `a benchmark and its results resolve, past dates and all, zero and assistance kept`() {
        val resolution = resolve(hang, cutoff = LocalDate.of(2026, 9, 28))
        assertEquals(emptyList<String>(), resolution.problems)
        assertEquals(listOf("Max hang"), resolution.preview.benchmarksAdded)
        assertEquals(3, resolution.preview.benchmarkResults)
        val benchmark = resolution.plan!!.benchmarks.single()
        assertEquals(BenchmarkMeasure.LOAD, benchmark.draft.measure)
        assertEquals(MeasurementMeaning.ADDED_LOAD, benchmark.draft.loadMeaning)
        assertEquals("150% BW", benchmark.draft.goal)
        assertEquals(listOf(-5.0, 0.0, 20.0), benchmark.results.map { it.value })
        assertEquals(127.5, benchmark.results.last().bodyweightPercent!!, 0.0)
    }

    @Test
    fun `results the phone already holds are not added again`() {
        val library = LibraryIndex(
            benchmarks = mapOf(
                "max hang" to IndexedBenchmark(
                    "b-1",
                    setOf(resultKey(LocalDate.of(2025, 11, 15), 0.0, null, null)),
                )
            ),
        )
        val resolution = resolve(hang, library)
        assertEquals(listOf("Max hang"), resolution.preview.benchmarksUpdated)
        assertEquals(2, resolution.preview.benchmarkResults)
        assertTrue(resolution.warnings.any { "already recorded" in it })
    }

    @Test
    fun `a text benchmark takes words and has no sides`() {
        val resolution = resolve(
            """
            "benchmarks": [{ "name": "Forward bend", "measure": "text", "unilateral": true, "goal": "face to knees",
              "results": [{ "date": "2026-02-15", "text": "touching heels" }, { "date": "2026-03-01", "value": 3 }] }]
            """
        )
        assertTrue(resolution.problems.any { "has no \"text\"" in it })
        assertTrue(resolution.warnings.any { "no sides" in it })
        val ok = resolve(
            """"benchmarks": [{ "name": "Forward bend", "measure": "text", "results": [{ "date": "2026-02-15", "text": "touching heels" }] }]"""
        )
        val result = ok.plan!!.benchmarks.single().results.single()
        assertEquals("touching heels", result.text)
        assertNull(result.value)
    }

    @Test
    fun `mistakes are named`() {
        val resolution = resolve(
            """
            "benchmarks": [
              { "name": "Lift", "measure": "load", "unit": "kg", "unilateral": true,
                "results": [{ "date": "2026-02-15", "value": 20 }] },
              { "name": "Split", "measure": "distance", "unit": "cm", "better": "wider",
                "results": [{ "date": "15/02/2026", "value": -3 }] },
              { "measure": "frogs" }
            ]
            """
        )
        assertTrue(resolution.problems.any { "a load needs a \"meaning\"" in it })
        assertTrue(resolution.problems.any { "takes \"left\" and \"right\"" in it })
        assertTrue(resolution.problems.any { "\"better\" is" in it })
        assertTrue(resolution.problems.any { "unreadable date" in it })
        assertTrue(resolution.problems.any { "has no name" in it })
        assertNull(resolution.plan)
    }

    @Test
    fun `left and right results resolve for a unilateral benchmark`() {
        val resolution = resolve(
            """"benchmarks": [{ "name": "One-arm lift", "measure": "load", "unit": "kg", "meaning": "TOTAL_LOAD",
              "unilateral": true, "results": [{ "date": "2026-02-15", "left": 25, "right": 23 }] }]"""
        )
        assertEquals(emptyList<String>(), resolution.problems)
        val result = resolution.plan!!.benchmarks.single().results.single()
        assertEquals(25.0, result.value!!, 0.0)
        assertEquals(23.0, result.valueRight!!, 0.0)
    }
}
