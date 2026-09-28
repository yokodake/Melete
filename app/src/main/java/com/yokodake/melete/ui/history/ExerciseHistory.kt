package com.yokodake.melete.ui.history

import com.yokodake.melete.data.OccurrenceDetail
import com.yokodake.melete.data.PerformedSet
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.detail.LastLoggedPicker
import com.yokodake.melete.ui.week.PrescriptionSummary
import java.time.LocalDate

/**
 * What an exercise's graph plots, chosen from what it actually records.
 *
 * Durations are never graphed: how long a set or a session took is not a measure of progress. A
 * hold that is one is recorded as a load in seconds (total load, unit "s"), the way a distance is
 * recorded in centimetres, and is graphed as a load.
 */
enum class HistoryMeasure(val label: String) {
    /** The heaviest set of each workout, in the exercise's current unit and meaning. */
    LOAD("Heaviest set"),

    /** The most reps in one set, for reps work that records no load. */
    REPS("Most reps in a set"),
}

/** One workout's value on the graph. */
data class HistoryPoint(val date: LocalDate, val value: Double, val occurrenceId: String)

/** One line of the graph: a side of unilateral work, or the whole of it ([side] null). */
data class HistorySeries(val side: BodySide?, val points: List<HistoryPoint>)

/** A value and the date it was reached. */
data class HistoryMark(val value: Double, val date: LocalDate)

/** One dated result under the graph. */
data class HistoryRecord(
    val occurrence: PlannedOccurrence,
    val date: LocalDate,
    val sets: List<PerformedSet>,
    /** What was recorded, in one line; null when it was completed with nothing written down. */
    val summary: String?,
)

/** A choice of plan to narrow the history to: the default, or one variation. */
data class PlanOption(val key: String, val label: String)

/**
 * An exercise across weeks: its dated results, the graph of its main number, and where it stands.
 *
 * Built from records only — a completed occurrence, or one with sets — under the date the work was
 * filed. Plans that never happened are not history. Every value shown is one that was recorded:
 * nothing is filled in, a zero load is a real point, and assistance is simply below zero.
 */
data class ExerciseHistory(
    val measure: HistoryMeasure?,
    /** The unit and meaning loads are compared in; only loads recorded in them are plotted. */
    val unit: String?,
    val meaning: MeasurementMeaning?,
    val series: List<HistorySeries>,
    /** The most recent value of each series, keyed by side (null for bilateral work). */
    val latest: Map<BodySide?, HistoryMark>,
    /** The highest value of each series, and the first date it was reached. */
    val best: Map<BodySide?, HistoryMark>,
    /** Newest first. */
    val records: List<HistoryRecord>,
    /** Offered when more than one plan appears in the records. */
    val planOptions: List<PlanOption>,
    /** The rep counts (or set lengths) seen, offered when there is more than one. */
    val targetOptions: List<Int>,
    /** The plan and target actually applied: a choice that no longer matches anything is dropped. */
    val plan: String?,
    val target: Int?,
) {
    val hasGraph: Boolean get() = measure != null && series.any { it.points.isNotEmpty() }

    /** A value of this history's measure, as it reads: "+20 kg", "8 reps", "45 s", "1:30". */
    fun format(value: Double): String = when (measure) {
        HistoryMeasure.LOAD -> PrescriptionSummary.load(
            Measurement(value, unit.orEmpty(), meaning ?: MeasurementMeaning.TOTAL_LOAD)
        ).trim()
        HistoryMeasure.REPS -> value.toInt().let { "$it ${if (it == 1) "rep" else "reps"}" }
        null -> value.toString()
    }

    companion object {
        /** The plan key for copies cut from no variation. */
        const val DEFAULT_PLAN = "default"

        /**
         * @param unit the exercise's current load unit, when it has one; loads recorded in another
         *   unit or meaning are left off the graph rather than mixed in.
         * @param plan null for every plan, [DEFAULT_PLAN], or a variation id.
         * @param target a rep count (reps) or set length in seconds (timed) that sets must match.
         * @param from, to limit the records to a range, inclusive; null for all time.
         */
        fun build(
            details: List<OccurrenceDetail>,
            mode: ExerciseMode,
            unit: String?,
            meaning: MeasurementMeaning?,
            plan: String? = null,
            target: Int? = null,
            from: LocalDate? = null,
            to: LocalDate? = null,
        ): ExerciseHistory {
            val all = details.mapNotNull { detail ->
                val occurrence = detail.occurrence
                val recorded = occurrence.state == OccurrenceState.COMPLETED || detail.sets.isNotEmpty()
                if (!recorded) return@mapNotNull null
                val date = detail.sets.firstOrNull()?.trainingDate ?: occurrence.trainingDate
                    ?: return@mapNotNull null
                if (from != null && date < from) return@mapNotNull null
                if (to != null && date > to) return@mapNotNull null
                HistoryRecord(
                    occurrence = occurrence,
                    date = date,
                    sets = detail.sets.sortedWith(compareBy({ it.orderIndex }, { it.side?.ordinal ?: 0 })),
                    summary = LastLoggedPicker.summarise(occurrence, detail.sets),
                )
            }

            val planOptions = buildList {
                if (all.any { it.occurrence.variationId == null }) add(PlanOption(DEFAULT_PLAN, "Default"))
                all.filter { it.occurrence.variationId != null }
                    .sortedByDescending { it.date }
                    .distinctBy { it.occurrence.variationId }
                    .sortedBy { it.occurrence.variationTag ?: "" }
                    .forEach { add(PlanOption(it.occurrence.variationId!!, it.occurrence.variationTag ?: "?")) }
            }.takeIf { it.size > 1 }.orEmpty()
            val appliedPlan = plan?.takeIf { key -> planOptions.any { it.key == key } }
            val inPlan = all.filter { record ->
                when (appliedPlan) {
                    null -> true
                    DEFAULT_PLAN -> record.occurrence.variationId == null
                    else -> record.occurrence.variationId == appliedPlan
                }
            }

            fun targetOf(set: PerformedSet): Int? = when (mode) {
                ExerciseMode.REPETITIONS -> set.payload.reps
                ExerciseMode.DURATION -> set.payload.durationSeconds
                else -> null
            }
            val targetOptions = inPlan.flatMap { it.sets }.mapNotNull(::targetOf).distinct().sorted()
                .takeIf { it.size > 1 }.orEmpty()
            val appliedTarget = target?.takeIf { it in targetOptions }
            fun matches(set: PerformedSet) = appliedTarget == null || targetOf(set) == appliedTarget
            val records = inPlan
                .filter { record -> appliedTarget == null || record.sets.any(::matches) }
                .sortedWith(compareByDescending<HistoryRecord> { it.date }.thenByDescending { it.occurrence.orderIndex })

            // The unit loads are compared in: the exercise's own, else the latest one recorded.
            val loads = records.flatMap { it.sets }.filter(::matches).mapNotNull { it.payload.measurement }
            val loadUnit = unit ?: loads.lastOrNull()?.unit
            val loadMeaning = meaning ?: loads.lastOrNull()?.meaning
            fun comparable(m: Measurement?) = m != null && m.unit == loadUnit && m.meaning == loadMeaning
            val measure = when {
                loads.any(::comparable) -> HistoryMeasure.LOAD
                mode == ExerciseMode.REPETITIONS -> HistoryMeasure.REPS
                else -> null
            }

            fun valueOf(set: PerformedSet): Double? = when (measure) {
                HistoryMeasure.LOAD -> set.payload.measurement?.takeIf(::comparable)?.value
                HistoryMeasure.REPS -> set.payload.reps?.toDouble()
                null -> null
            }
            val chronological = records.reversed()
            val points: List<Pair<BodySide?, HistoryPoint>> = chronological.flatMap { record ->
                val sided = record.occurrence.unilateral
                record.sets.filter(::matches)
                    .groupBy { if (sided) it.side else null }
                    .mapNotNull { (side, sets) ->
                        sets.mapNotNull(::valueOf).maxOrNull()?.let { side to HistoryPoint(record.date, it, record.occurrence.id) }
                    }
            }
            val order = listOf(BodySide.LEFT, BodySide.RIGHT, null)
            val series = points.groupBy({ it.first }, { it.second })
                .map { (side, list) -> HistorySeries(side, list) }
                .sortedBy { order.indexOf(it.side) }

            return ExerciseHistory(
                measure = measure.takeIf { points.isNotEmpty() },
                unit = loadUnit.takeIf { measure == HistoryMeasure.LOAD },
                meaning = loadMeaning.takeIf { measure == HistoryMeasure.LOAD },
                series = series,
                latest = series.associate { s -> s.side to s.points.last().let { HistoryMark(it.value, it.date) } },
                best = series.associate { s -> s.side to best(s.points) },
                records = records,
                planOptions = planOptions,
                targetOptions = targetOptions,
                plan = appliedPlan,
                target = appliedTarget,
            )
        }

        /** The highest value, keeping the date it was first reached. [points] are chronological. */
        private fun best(points: List<HistoryPoint>): HistoryMark =
            points.fold(null as HistoryPoint?) { held, next ->
                if (held == null || next.value > held.value) next else held
            }!!.let { HistoryMark(it.value, it.date) }
    }
}
