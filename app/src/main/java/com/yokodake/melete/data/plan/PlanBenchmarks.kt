package com.yokodake.melete.data.plan

import com.yokodake.melete.data.BenchmarkDraft
import com.yokodake.melete.data.entity.BenchmarkMeasure
import com.yokodake.melete.data.model.MeasurementMeaning
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * A benchmark in a plan file: its definition, and results to record against it.
 *
 * Matched to the phone's benchmarks by name. Results are records rather than plans, so the
 * import's scope never leaves them out, and a result the phone already holds (same date and same
 * value) is not added twice — importing the same file again changes nothing.
 */
@Serializable
data class PlanBenchmark(
    val name: String? = null,
    /** load, duration, distance, other or text. */
    val measure: String? = null,
    val unit: String? = null,
    /** For a load: TOTAL_LOAD, or ADDED_LOAD (negative for assistance). */
    val meaning: String? = null,
    val unilateral: Boolean = false,
    /** higher or lower. */
    val better: String? = null,
    val protocol: String? = null,
    val goal: String? = null,
    val results: List<PlanBenchmarkResult> = emptyList(),
)

@Serializable
data class PlanBenchmarkResult(
    val date: String? = null,
    /** The result; for unilateral work use [left] and [right] instead. */
    val value: Double? = null,
    val left: Double? = null,
    val right: Double? = null,
    /** A result in words, for a text benchmark. */
    val text: String? = null,
    /** A bodyweight percentage reported with a load, stored as written. */
    val bodyweightPercent: Double? = null,
    val note: String? = null,
)

/** A benchmark the phone already has, and the results it holds, by [resultKey]. */
data class IndexedBenchmark(val id: String, val resultKeys: Set<String>)

data class ResolvedBenchmarkResult(
    val date: LocalDate,
    val value: Double?,
    val valueRight: Double?,
    val text: String?,
    val bodyweightPercent: Double?,
    val note: String?,
)

data class ResolvedBenchmark(
    val key: String,
    val existingId: String?,
    val draft: BenchmarkDraft,
    /** Only the results the phone does not already hold. */
    val results: List<ResolvedBenchmarkResult>,
)

/** Two results are the same record when they share a date and what they say. */
internal fun resultKey(date: LocalDate, value: Double?, valueRight: Double?, text: String?): String =
    "$date|$value|$valueRight|${text?.trim()}"

/** The benchmarks section of a file, judged the same way as the rest of it. */
internal object PlanBenchmarkCheck {

    fun resolve(
        file: PlanFile,
        existing: Map<String, IndexedBenchmark>,
        problems: MutableList<String>,
        warnings: MutableList<String>,
    ): List<ResolvedBenchmark> {
        file.benchmarks.mapNotNull { it.name }.groupBy(::nameKey).values.firstOrNull { it.size > 1 }?.let {
            problems += "Two benchmarks are called \"${it.first().trim()}\"."
        }
        return file.benchmarks.mapIndexedNotNull { index, source ->
            val name = source.name?.trim()?.takeIf { it.isNotEmpty() }
            if (name == null) {
                problems += "Benchmark ${index + 1} has no name."
                return@mapIndexedNotNull null
            }
            val measure = source.measure?.let { raw ->
                BenchmarkMeasure.entries.firstOrNull { it.name == raw.trim().uppercase() }
            }
            if (measure == null) {
                problems += if (source.measure == null) {
                    "Benchmark $name has no \"measure\" (load, duration, distance, other or text)."
                } else {
                    "Benchmark $name: unknown measure \"${source.measure}\"."
                }
                return@mapIndexedNotNull null
            }
            val isText = measure == BenchmarkMeasure.TEXT
            val meaning = if (measure == BenchmarkMeasure.LOAD) {
                source.meaning?.let { raw ->
                    MeasurementMeaning.entries.firstOrNull { it.name == raw.trim().uppercase() }
                        ?: run { problems += "Benchmark $name: unknown meaning \"$raw\"."; null }
                } ?: run {
                    if (source.meaning == null) {
                        problems += "Benchmark $name: a load needs a \"meaning\" (TOTAL_LOAD or ADDED_LOAD)."
                    }
                    null
                }
            } else {
                if (source.meaning != null) warnings += "Benchmark $name: \"meaning\" is for loads; left out."
                null
            }
            val higher = when (source.better?.trim()?.lowercase()) {
                null, "higher" -> true
                "lower" -> false
                else -> {
                    problems += "Benchmark $name: \"better\" is \"higher\" or \"lower\"."
                    true
                }
            }
            if (isText && source.unilateral) warnings += "Benchmark $name: a text result has no sides; \"unilateral\" left out."
            val unilateral = source.unilateral && !isText
            val draft = BenchmarkDraft(
                name = name,
                measure = measure,
                unit = if (isText) "" else (source.unit?.trim() ?: measure.defaultUnit),
                loadMeaning = meaning,
                unilateral = unilateral,
                higherIsBetter = higher,
                protocol = source.protocol,
                goal = source.goal,
            )
            val known = existing[nameKey(name)]
            var duplicates = 0
            val results = source.results.mapIndexedNotNull { i, result ->
                val what = "Benchmark $name, result ${i + 1}"
                val date = result.date?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
                if (date == null) {
                    problems += if (result.date == null) "$what has no date." else "$what: unreadable date \"${result.date}\"."
                    return@mapIndexedNotNull null
                }
                val words = result.text?.trim()?.takeIf { it.isNotEmpty() }
                val value: Double?
                val right: Double?
                when {
                    isText -> {
                        if (words == null) problems += "$what has no \"text\"."
                        if (result.value != null || result.left != null || result.right != null) {
                            warnings += "$what: a text benchmark takes \"text\"; the number is left out."
                        }
                        value = null
                        right = null
                    }
                    unilateral -> {
                        if (result.value != null) problems += "$what: a left/right benchmark takes \"left\" and \"right\", not \"value\"."
                        if (result.left == null && result.right == null) problems += "$what has no \"left\" or \"right\"."
                        value = result.left
                        right = result.right
                    }
                    else -> {
                        if (result.left != null || result.right != null) problems += "$what: \"left\"/\"right\" need \"unilateral\": true."
                        if (result.value == null) problems += "$what has no \"value\"."
                        value = result.value
                        right = null
                    }
                }
                if (!isText && words != null) warnings += "$what: \"text\" is for text benchmarks; left out."
                if (measure != BenchmarkMeasure.LOAD && result.bodyweightPercent != null) {
                    warnings += "$what: \"bodyweightPercent\" goes with a load; left out."
                }
                if (meaning != MeasurementMeaning.ADDED_LOAD && listOfNotNull(value, right).any { it < 0 }) {
                    problems += "$what: only an added load can be negative."
                }
                val text = words.takeIf { isText }
                if (known != null && resultKey(date, value, right, text) in known.resultKeys) {
                    duplicates++
                    return@mapIndexedNotNull null
                }
                ResolvedBenchmarkResult(
                    date = date,
                    value = value,
                    valueRight = right,
                    text = text,
                    bodyweightPercent = result.bodyweightPercent.takeIf { measure == BenchmarkMeasure.LOAD },
                    note = result.note?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            if (duplicates > 0) {
                warnings += "Benchmark $name: $duplicates ${if (duplicates == 1) "result is" else "results are"} already recorded; not added again."
            }
            ResolvedBenchmark(nameKey(name), known?.id, draft, results)
        }
    }
}
