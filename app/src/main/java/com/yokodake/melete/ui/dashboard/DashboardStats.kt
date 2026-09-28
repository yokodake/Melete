package com.yokodake.melete.ui.dashboard

import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.CompletedExercise
import com.yokodake.melete.data.model.ExerciseCategory
import java.time.LocalDate

/** The ranges the dashboard offers, each ending today and including the current partial period. */
enum class DashboardRange(val label: String) {
    FOUR_WEEKS("4 weeks"),
    TWELVE_WEEKS("12 weeks"),
    SIX_MONTHS("6 months"),
    TWELVE_MONTHS("12 months"),
    ALL_TIME("All time"),
}

/** What the bars, the shares and the exercise rows measure. */
enum class DashboardMetric(val label: String) {
    HOURS("Hours"),
    EXERCISES("Exercises"),
}

/** One bar: a week or a month, and what each category contributed to it. */
data class DashboardBar(
    val start: LocalDate,
    val monthly: Boolean,
    /** Seconds per category; null is "no category". Only categories present appear. */
    val seconds: Map<ExerciseCategory?, Int>,
    val counts: Map<ExerciseCategory?, Int>,
) {
    val totalSeconds: Int get() = seconds.values.sum()
    val totalCount: Int get() = counts.values.sum()
}

data class CategoryTotal(
    val category: ExerciseCategory?,
    val seconds: Int,
    val count: Int,
    /** Its share of the range, by the selected metric, 0..1. */
    val share: Double,
)

data class ExerciseTotal(
    val exerciseId: String,
    val name: String,
    val category: ExerciseCategory?,
    val seconds: Int,
    val count: Int,
    /** Some of its time was worked out from the plan rather than typed. */
    val includesInferred: Boolean,
)

data class DashboardStats(
    val from: LocalDate,
    val to: LocalDate,
    /** Recorded time only: a completed exercise with no duration adds nothing. */
    val totalSeconds: Int,
    val totalCount: Int,
    val bars: List<DashboardBar>,
    val categories: List<CategoryTotal>,
    val exercises: List<ExerciseTotal>,
) {
    companion object {

        /**
         * The first and last day a range covers, [back] whole ranges before the current one. The
         * current range (0) ends today; an earlier one ends the day before the next one starts,
         * so paging back walks whole weeks or months without gaps or overlaps. All time starts at
         * the first record and has nothing before it.
         */
        fun span(
            range: DashboardRange,
            today: LocalDate,
            firstRecord: LocalDate?,
            back: Int = 0,
        ): Pair<LocalDate, LocalDate> {
            val thisWeek = WeekMath.weekStartOf(today)
            val thisMonth = today.withDayOfMonth(1)
            fun start(k: Int): LocalDate = when (range) {
                DashboardRange.FOUR_WEEKS -> thisWeek.minusWeeks(3L + 4L * k)
                DashboardRange.TWELVE_WEEKS -> thisWeek.minusWeeks(11L + 12L * k)
                DashboardRange.SIX_MONTHS -> thisMonth.minusMonths(5L + 6L * k)
                DashboardRange.TWELVE_MONTHS -> thisMonth.minusMonths(11L + 12L * k)
                DashboardRange.ALL_TIME -> firstRecord?.takeIf { it <= today } ?: today
            }
            if (range == DashboardRange.ALL_TIME || back <= 0) return start(0) to today
            return start(back) to start(back - 1).minusDays(1)
        }

        /** Whether the range before the one [back] ranges back still holds anything recorded. */
        fun hasEarlier(range: DashboardRange, today: LocalDate, firstRecord: LocalDate?, back: Int): Boolean =
            range != DashboardRange.ALL_TIME && firstRecord != null &&
                span(range, today, firstRecord, back + 1).second >= firstRecord

        /** Weekly bars through six months inclusive; monthly beyond. [to] is the range's last day. */
        fun monthly(from: LocalDate, to: LocalDate): Boolean =
            from < to.withDayOfMonth(1).minusMonths(5)

        fun build(
            completed: List<CompletedExercise>,
            from: LocalDate,
            to: LocalDate,
            metric: DashboardMetric,
        ): DashboardStats {
            val inRange = completed.filter { it.date in from..to }
            val monthly = monthly(from, to)

            // Every period from the first to today, empty ones included: a week off is part of
            // the picture, not a gap to close up.
            val starts = generateSequence(if (monthly) from.withDayOfMonth(1) else WeekMath.weekStartOf(from)) {
                if (monthly) it.plusMonths(1) else it.plusWeeks(1)
            }.takeWhile { it <= to }.toList()
            fun periodOf(date: LocalDate) = if (monthly) date.withDayOfMonth(1) else WeekMath.weekStartOf(date)
            val byPeriod = inRange.groupBy { periodOf(it.date) }
            val bars = starts.map { start ->
                val items = byPeriod[start].orEmpty()
                DashboardBar(
                    start = start,
                    monthly = monthly,
                    seconds = items.groupBy { it.category }
                        .mapValues { (_, list) -> list.sumOf { it.durationSeconds ?: 0 } }
                        .filterValues { it > 0 },
                    counts = items.groupingBy { it.category }.eachCount(),
                )
            }

            val totalSeconds = inRange.sumOf { it.durationSeconds ?: 0 }
            val totalCount = inRange.size
            fun share(seconds: Int, count: Int): Double = when (metric) {
                DashboardMetric.HOURS -> if (totalSeconds == 0) 0.0 else seconds.toDouble() / totalSeconds
                DashboardMetric.EXERCISES -> if (totalCount == 0) 0.0 else count.toDouble() / totalCount
            }
            fun key(seconds: Int, count: Int): Int = if (metric == DashboardMetric.HOURS) seconds else count

            val categories = inRange.groupBy { it.category }.map { (category, list) ->
                val seconds = list.sumOf { it.durationSeconds ?: 0 }
                CategoryTotal(category, seconds, list.size, share(seconds, list.size))
            }.sortedWith(
                compareByDescending<CategoryTotal> { key(it.seconds, it.count) }
                    .thenBy { it.category?.ordinal ?: Int.MAX_VALUE }
            )

            val exercises = inRange.groupBy { it.exerciseId }.map { (id, list) ->
                // The name as most recently trained, which is what the week showed last.
                val latest = list.maxBy { it.date }
                ExerciseTotal(
                    exerciseId = id,
                    name = latest.name,
                    category = latest.category,
                    seconds = list.sumOf { it.durationSeconds ?: 0 },
                    count = list.size,
                    includesInferred = list.any { it.durationSeconds != null && !it.durationManual },
                )
            }.sortedWith(
                compareByDescending<ExerciseTotal> { key(it.seconds, it.count) }
                    .thenBy { it.name.lowercase() }
            )

            return DashboardStats(from, to, totalSeconds, totalCount, bars, categories, exercises)
        }

        /**
         * Heights for one bar's segments: proportional to [values], summing to [barHeight], with
         * every non-zero segment at least [minimum] tall. What the small ones gain is taken from
         * the largest segment, so the bar's total stays true; when that would leave the largest
         * below the minimum itself, the bar stays plainly proportional.
         */
        fun segmentHeights(values: List<Float>, barHeight: Float, minimum: Float): List<Float> {
            val sum = values.sum()
            if (sum <= 0f) return values.map { 0f }
            val raw = values.map { barHeight * it / sum }
            val largest = raw.indices.maxBy { raw[it] }
            val lifted = raw.mapIndexed { i, h -> if (i != largest && h > 0f && h < minimum) minimum else h }
            val deficit = lifted.sum() - raw.sum()
            if (deficit <= 0f) return raw
            if (raw[largest] - deficit < minimum) return raw
            return lifted.mapIndexed { i, h -> if (i == largest) h - deficit else h }
        }

        /** "24 h", "24.5 h", "45 m", "0 h". */
        fun hours(seconds: Int): String = when {
            seconds <= 0 -> "0 h"
            seconds < 3600 -> "${(seconds + 30) / 60} m"
            else -> {
                val tenths = (seconds + 180) / 360
                if (tenths % 10 == 0) "${tenths / 10} h" else "${tenths / 10}.${tenths % 10} h"
            }
        }
    }
}
