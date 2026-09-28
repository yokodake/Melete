package com.yokodake.melete.data

import androidx.room.withTransaction
import com.yokodake.melete.data.entity.BenchmarkEntity
import com.yokodake.melete.data.entity.BenchmarkMeasure
import com.yokodake.melete.data.entity.BenchmarkResultEntity
import com.yokodake.melete.data.model.MeasurementMeaning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

/** A benchmark's definition, as the screens use it. */
data class Benchmark(
    val id: String,
    val name: String,
    val measure: BenchmarkMeasure,
    val unit: String,
    val loadMeaning: MeasurementMeaning?,
    val unilateral: Boolean,
    val higherIsBetter: Boolean,
    val protocol: String?,
    val goal: String?,
    val orderIndex: Int,
    val hidden: Boolean,
) {
    /** A result in words: no numbers, sides or best. */
    val isText: Boolean get() = measure == BenchmarkMeasure.TEXT
}

/** Everything the benchmark editor writes. */
data class BenchmarkDraft(
    val name: String,
    val measure: BenchmarkMeasure,
    val unit: String,
    val loadMeaning: MeasurementMeaning?,
    val unilateral: Boolean,
    val higherIsBetter: Boolean,
    val protocol: String?,
    val goal: String? = null,
)

/** One recorded result, in the unit, meaning and sides it was recorded under. */
data class BenchmarkResult(
    val id: String,
    val benchmarkId: String,
    val date: LocalDate,
    /** The value, or the left side's for unilateral work. */
    val value: Double?,
    val valueRight: Double?,
    val unit: String,
    val loadMeaning: MeasurementMeaning?,
    val unilateral: Boolean,
    val note: String?,
    val recordedAtEpochMs: Long,
    /** A result in words, for a text benchmark. */
    val textValue: String? = null,
    /** A bodyweight percentage recorded with the load, as reported. */
    val bodyweightPercent: Double? = null,
) {
    /** The result as it reads: the words, or the value(s) with the unit. */
    val text: String
        get() = textValue ?: BenchmarkFormat.values(value, valueRight, unilateral, unit, loadMeaning)

    /** "128% BW", when one was recorded. */
    val bodyweightText: String?
        get() = bodyweightPercent?.let { "${BenchmarkFormat.number(it, null)}% BW" }
}

/** A best value and the date it was first reached. */
data class BestValue(val value: Double, val date: LocalDate)

/**
 * Where a benchmark stands: its latest result and its best, which are different things — the most
 * recent test can be below an older maximum. For unilateral work each side has its own best, with
 * its own date.
 */
data class BenchmarkStanding(
    val benchmark: Benchmark,
    /** Most recent first, by the date the test was done, then by when it was typed in. */
    val results: List<BenchmarkResult>,
    val best: BestValue?,
    val bestRight: BestValue?,
) {
    val latest: BenchmarkResult? get() = results.firstOrNull()

    val bestText: String?
        get() = if (best == null && bestRight == null) {
            null
        } else {
            BenchmarkFormat.values(
                best?.value, bestRight?.value, benchmark.unilateral, benchmark.unit, benchmark.loadMeaning,
            )
        }

    companion object {
        /**
         * Orders the results and finds the bests. Only results recorded in the benchmark's current
         * unit and load meaning compete for best, so a change of unit can never make an old number
         * look like a record; ties keep the date the value was first reached.
         */
        fun of(benchmark: Benchmark, results: List<BenchmarkResult>): BenchmarkStanding {
            val ordered = results.sortedWith(
                compareByDescending<BenchmarkResult> { it.date }.thenByDescending { it.recordedAtEpochMs }
            )
            val comparable = results.filter {
                it.unit == benchmark.unit && it.loadMeaning == benchmark.loadMeaning
            }
            fun best(of: (BenchmarkResult) -> Double?): BestValue? {
                val chronological = comparable
                    .mapNotNull { r -> of(r)?.let { BestValue(it, r.date) to r.recordedAtEpochMs } }
                    .sortedWith(compareBy({ it.first.date }, { it.second }))
                    .map { it.first }
                return chronological.fold(null as BestValue?) { held, next ->
                    when {
                        held == null -> next
                        benchmark.higherIsBetter && next.value > held.value -> next
                        !benchmark.higherIsBetter && next.value < held.value -> next
                        else -> held
                    }
                }
            }
            return BenchmarkStanding(
                benchmark = benchmark,
                results = ordered,
                best = best { it.value },
                bestRight = if (benchmark.unilateral) best { it.valueRight } else null,
            )
        }
    }
}

/** A result on a calendar day, with the name of what it measured. */
data class BenchmarkDayResult(
    val benchmarkId: String,
    val name: String,
    val date: LocalDate,
    val text: String,
)

/** How benchmark numbers read: "+30 kg", "−5 kg", "L 25 · R 23 kg", "7.5 s". */
object BenchmarkFormat {

    fun number(value: Double, meaning: MeasurementMeaning?): String {
        val magnitude = abs(value)
        val digits = if (magnitude == magnitude.toLong().toDouble()) {
            magnitude.toLong().toString()
        } else {
            magnitude.toString()
        }
        return when {
            meaning == MeasurementMeaning.ADDED_LOAD && value < 0 -> "−$digits"
            meaning == MeasurementMeaning.ADDED_LOAD -> "+$digits"
            value < 0 -> "−$digits"
            else -> digits
        }
    }

    fun values(
        value: Double?,
        valueRight: Double?,
        unilateral: Boolean,
        unit: String,
        meaning: MeasurementMeaning?,
    ): String {
        val suffix = unit.trim().takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
        fun side(v: Double?) = v?.let { number(it, meaning) } ?: "—"
        return if (unilateral || valueRight != null) {
            "L ${side(value)} · R ${side(valueRight)}$suffix"
        } else {
            "${side(value)}$suffix"
        }
    }
}

/**
 * Benchmarks: reference tests and the results recorded against them.
 *
 * A record apart from training. Recording a result writes only here — no occurrence, no session,
 * nothing a count or a training total could pick up — and the calendar shows it on its date as a
 * read-only line, as it does daily notes.
 */
class BenchmarkRepository(private val database: MeleteDatabase) {

    private val dao = database.benchmarkDao()

    fun observeStandings(): Flow<List<BenchmarkStanding>> =
        combine(dao.observeBenchmarks(), dao.observeAllResults()) { benchmarks, results ->
            val byBenchmark = results.groupBy { it.benchmarkId }
            benchmarks.map { row ->
                BenchmarkStanding.of(row.toModel(), byBenchmark[row.id].orEmpty().map { it.toModel() })
            }
        }

    fun observeStanding(id: String): Flow<BenchmarkStanding?> =
        combine(dao.observeBenchmark(id), dao.observeResultsOf(id)) { row, results ->
            row?.let { BenchmarkStanding.of(it.toModel(), results.map { r -> r.toModel() }) }
        }

    /** Results filed between two dates, inclusive, named, in the order the benchmarks are listed. */
    fun observeDayResults(from: LocalDate, to: LocalDate): Flow<List<BenchmarkDayResult>> =
        combine(
            dao.observeBenchmarks(),
            dao.observeResultsBetween(from.toEpochDay(), to.toEpochDay()),
        ) { benchmarks, results ->
            val byId = benchmarks.associateBy { it.id }
            results
                .sortedWith(compareBy({ byId[it.benchmarkId]?.orderIndex ?: Int.MAX_VALUE }, { it.recordedAtEpochMs }))
                .map { row ->
                    val result = row.toModel()
                    BenchmarkDayResult(
                        benchmarkId = row.benchmarkId,
                        name = byId[row.benchmarkId]?.name.orEmpty(),
                        date = result.date,
                        text = result.text,
                    )
                }
        }

    suspend fun getBenchmark(id: String): Benchmark? = dao.getBenchmark(id)?.toModel()

    suspend fun create(draft: BenchmarkDraft): String = database.withTransaction {
        val row = BenchmarkEntity(
            id = UUID.randomUUID().toString(),
            name = draft.name.trim(),
            measure = draft.measure,
            unit = draft.unit.trim(),
            loadMeaning = draft.loadMeaning.takeIf { draft.measure == BenchmarkMeasure.LOAD },
            unilateral = draft.unilateral,
            higherIsBetter = draft.higherIsBetter,
            protocol = draft.protocol?.trim()?.takeIf { it.isNotEmpty() },
            goal = draft.goal?.trim()?.takeIf { it.isNotEmpty() },
            orderIndex = dao.nextOrderIndex(),
            createdAtEpochMs = System.currentTimeMillis(),
        )
        dao.insertBenchmark(row)
        row.id
    }

    /** Changes the definition from now on. Results already recorded keep their own unit and sides. */
    suspend fun update(id: String, draft: BenchmarkDraft) {
        database.withTransaction {
            val row = dao.getBenchmark(id) ?: return@withTransaction
            dao.updateBenchmark(
                row.copy(
                    name = draft.name.trim(),
                    measure = draft.measure,
                    unit = draft.unit.trim(),
                    loadMeaning = draft.loadMeaning.takeIf { draft.measure == BenchmarkMeasure.LOAD },
                    unilateral = draft.unilateral,
                    higherIsBetter = draft.higherIsBetter,
                    protocol = draft.protocol?.trim()?.takeIf { it.isNotEmpty() },
                    goal = draft.goal?.trim()?.takeIf { it.isNotEmpty() },
                )
            )
        }
    }

    /** Hides or shows again. A hidden benchmark keeps every result. */
    suspend fun setHidden(id: String, hidden: Boolean) {
        database.withTransaction {
            val row = dao.getBenchmark(id) ?: return@withTransaction
            dao.updateBenchmark(row.copy(hiddenAtEpochMs = if (hidden) System.currentTimeMillis() else null))
        }
    }

    /** Deletes a benchmark only while it has no results; one with results can only be hidden. */
    suspend fun deleteIfUnused(id: String): Boolean = database.withTransaction {
        if (dao.countResults(id) > 0) {
            false
        } else {
            dao.deleteBenchmark(id)
            true
        }
    }

    /**
     * Records a result under the benchmark's current definition. A result with no value at all is
     * not one, and is refused.
     */
    suspend fun record(
        benchmarkId: String,
        date: LocalDate,
        value: Double?,
        valueRight: Double?,
        note: String?,
        textValue: String? = null,
        bodyweightPercent: Double? = null,
    ): String? = database.withTransaction {
        val benchmark = dao.getBenchmark(benchmarkId) ?: return@withTransaction null
        val words = textValue?.trim()?.takeIf { it.isNotEmpty() }
        val isText = benchmark.measure == BenchmarkMeasure.TEXT
        if (isText && words == null) return@withTransaction null
        if (!isText && value == null && valueRight == null) return@withTransaction null
        val row = BenchmarkResultEntity(
            id = UUID.randomUUID().toString(),
            benchmarkId = benchmarkId,
            dateEpochDay = date.toEpochDay(),
            value = value.takeIf { !isText },
            valueRight = valueRight.takeIf { benchmark.unilateral && !isText },
            textValue = words.takeIf { isText },
            bodyweightPercent = bodyweightPercent.takeIf { benchmark.measure == BenchmarkMeasure.LOAD },
            unitSnapshot = benchmark.unit,
            loadMeaningSnapshot = benchmark.loadMeaning,
            unilateralSnapshot = benchmark.unilateral,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            recordedAtEpochMs = System.currentTimeMillis(),
        )
        dao.insertResult(row)
        row.id
    }

    /** Corrects a result. It keeps the unit and sides it was recorded under. */
    suspend fun updateResult(
        resultId: String,
        date: LocalDate,
        value: Double?,
        valueRight: Double?,
        note: String?,
        textValue: String? = null,
        bodyweightPercent: Double? = null,
    ): Boolean = database.withTransaction {
        val row = dao.getResult(resultId) ?: return@withTransaction false
        val words = textValue?.trim()?.takeIf { it.isNotEmpty() }
        val isText = row.textValue != null
        if (isText && words == null) return@withTransaction false
        if (!isText && value == null && valueRight == null) return@withTransaction false
        dao.updateResult(
            row.copy(
                dateEpochDay = date.toEpochDay(),
                value = value.takeIf { !isText },
                valueRight = valueRight.takeIf { row.unilateralSnapshot && !isText },
                textValue = words.takeIf { isText },
                bodyweightPercent = bodyweightPercent.takeIf { !isText },
                note = note?.trim()?.takeIf { it.isNotEmpty() },
            )
        )
        true
    }

    suspend fun deleteResult(resultId: String) {
        dao.deleteResult(resultId)
    }
}

private fun BenchmarkEntity.toModel() = Benchmark(
    id = id,
    name = name,
    measure = measure,
    unit = unit,
    loadMeaning = loadMeaning,
    unilateral = unilateral,
    higherIsBetter = higherIsBetter,
    protocol = protocol,
    goal = goal,
    orderIndex = orderIndex,
    hidden = hiddenAtEpochMs != null,
)

private fun BenchmarkResultEntity.toModel() = BenchmarkResult(
    id = id,
    benchmarkId = benchmarkId,
    date = LocalDate.ofEpochDay(dateEpochDay),
    value = value,
    valueRight = valueRight,
    unit = unitSnapshot,
    loadMeaning = loadMeaningSnapshot,
    unilateral = unilateralSnapshot,
    note = note,
    recordedAtEpochMs = recordedAtEpochMs,
    textValue = textValue,
    bodyweightPercent = bodyweightPercent,
)
