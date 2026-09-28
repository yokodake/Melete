package com.yokodake.melete.ui.home

import com.yokodake.melete.data.BenchmarkStanding
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.WeekCircuit
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.timer.PrescriptionProgram
import com.yokodake.melete.data.timer.StationPlan
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Today at a glance: how many of today's exercises are done, how many are left to do, and — only
 * when every one of them has an estimate — how long they will take.
 *
 * Exercises are the unit, exactly as the dashboard counts: each exercise occurrence dated today,
 * circuit stations and module members included; the circuit and module containers are not
 * occurrences and add nothing. Skipped work leaves the target. Undated work in the week's
 * unscheduled area is not today's until it is given the date; logging it files it under today.
 */
data class TodaySummary(
    val completed: Int,
    /** Exercises today that are not skipped: what "all done" is measured against. */
    val target: Int,
    val skipped: Int,
    /** Null when anything left has no estimate, and when nothing is left. */
    val remainingSeconds: Int?,
) {
    val nothingPlanned: Boolean get() = target == 0
    val allDone: Boolean get() = target > 0 && completed == target

    companion object {
        fun build(today: LocalDate, occurrences: List<PlannedOccurrence>, circuits: List<WeekCircuit>): TodaySummary {
            val todays = occurrences.filter { it.trainingDate == today }
            val skipped = todays.count { it.state == OccurrenceState.SKIPPED }
            val counted = todays.filter { it.state != OccurrenceState.SKIPPED }
            val completed = counted.count { it.state == OccurrenceState.COMPLETED }
            val remaining = counted.filter { it.state == OccurrenceState.PLANNED }

            // A circuit station's time is its share of the circuit's one clock, not what its own
            // plan would take alone; every other exercise estimates itself.
            val stationShares = mutableMapOf<String, Int>()
            val circuitsById = circuits.associateBy { it.id }
            counted.filter { it.circuitInstanceId != null }.groupBy { it.circuitInstanceId }.forEach { (id, stations) ->
                val circuit = circuitsById[id] ?: return@forEach
                val ordered = stations.sortedBy { it.circuitPosition ?: it.orderIndex }
                val shares = PrescriptionProgram.circuit(
                    label = circuit.name,
                    rounds = circuit.rounds,
                    transitionSeconds = circuit.transitionSeconds,
                    roundRestSeconds = circuit.roundRestSeconds,
                    stations = ordered.map { StationPlan(it.name, it.mode, it.unilateral, it.prescription, it.id) },
                ).estimatedSecondsByEntry()
                ordered.forEachIndexed { index, station -> shares.getOrNull(index)?.let { stationShares[station.id] = it } }
            }
            val estimates = remaining.map { stationShares[it.id] ?: it.estimatedDurationSeconds }
            val remainingSeconds = if (remaining.isEmpty() || estimates.any { it == null }) {
                null
            } else {
                estimates.sumOf { it ?: 0 }
            }
            return TodaySummary(completed, counted.size, skipped, remainingSeconds)
        }
    }
}

/** A benchmark worth testing again: the one whose latest result is the oldest, six months on. */
data class BenchmarkReminder(val benchmarkId: String, val name: String, val lastTested: LocalDate) {

    /** "8 months ago", or in years past two. */
    fun ageText(today: LocalDate): String {
        val months = ChronoUnit.MONTHS.between(lastTested, today)
        return if (months >= 24) "${months / 12} years ago" else "$months months ago"
    }

    companion object {
        /**
         * Among visible benchmarks with a result, the one tested longest ago, if that was at least
         * six months before [today]; null otherwise, or while dismissed until after [today].
         */
        fun pick(standings: List<BenchmarkStanding>, today: LocalDate, dismissedUntil: LocalDate?): BenchmarkReminder? {
            if (dismissedUntil != null && today <= dismissedUntil) return null
            return standings
                .filter { !it.benchmark.hidden }
                .mapNotNull { standing -> standing.latest?.let { standing to it.date } }
                .filter { (_, date) -> date <= today.minusMonths(6) }
                .minByOrNull { (_, date) -> date }
                ?.let { (standing, date) -> BenchmarkReminder(standing.benchmark.id, standing.benchmark.name, date) }
        }
    }
}
