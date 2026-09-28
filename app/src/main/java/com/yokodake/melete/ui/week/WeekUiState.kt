package com.yokodake.melete.ui.week

import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.BenchmarkDayResult
import com.yokodake.melete.data.DiaryDay
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.Tracker
import com.yokodake.melete.data.WeekCircuit
import com.yokodake.melete.data.WeekModule
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.ui.components.dominantCategory
import java.time.LocalDate

/**
 * One thing in a slot of the week: a standalone exercise, or a circuit and the stations inside it.
 *
 * A circuit is one card because it is one decision — you do the whole thing or you do not — and
 * because its stations scattered through a day would read as four unrelated exercises. What it is
 * *not* is an extra piece of work: the stations are the training, the card is only how they are
 * shown together, and nothing counts it.
 */
sealed interface WeekItem {
    val key: String
    val orderIndex: Int

    data class Single(val occurrence: PlannedOccurrence) : WeekItem {
        override val key: String get() = "occurrence-${occurrence.id}"
        override val orderIndex: Int get() = occurrence.orderIndex
    }

    data class Circuit(
        val circuit: WeekCircuit,
        val stations: List<PlannedOccurrence>,
    ) : WeekItem {
        override val key: String get() = "circuit-${circuit.id}"
        override val orderIndex: Int get() = circuit.orderIndex

        /** Done when every station is. The container itself records nothing. */
        val completed: Boolean get() = stations.isNotEmpty() && stations.all { it.hasRecord }

        val recordedStations: Int get() = stations.count { it.hasRecord }
    }

    /**
     * A scheduled module: a named group holding standalone exercises and whole circuits.
     *
     * Organisational only. It is shown as one group because it was planned as one, but its members
     * are the work — each keeps its own card, menu and log — and nothing counts the group itself.
     */
    data class Module(
        val module: WeekModule,
        val members: List<WeekItem>,
    ) : WeekItem {
        override val key: String get() = "module-${module.id}"
        override val orderIndex: Int get() = module.orderIndex

        /** The exercises inside it, circuit stations included, for "done" and deletion costs. */
        val exercises: List<PlannedOccurrence>
            get() = members.flatMap {
                when (it) {
                    is Single -> listOf(it.occurrence)
                    is Circuit -> it.stations
                    is Module -> emptyList()
                }
            }

        val recordedExercises: Int get() = exercises.count { it.hasRecord }

        /**
         * What the module reads as: the most common category among its members, a circuit
         * counting as its own category, ties to the first. Derived, never stored.
         */
        val category: ExerciseCategory?
            get() = dominantCategory(
                members.map {
                    when (it) {
                        is Single -> it.occurrence.category
                        is Circuit -> it.circuit.category
                        is Module -> null
                    }
                }
            )

        val completed: Boolean get() = exercises.isNotEmpty() && exercises.all { it.hasRecord }
    }
}

data class DaySection(
    val date: LocalDate,
    val isToday: Boolean,
    val items: List<WeekItem>,
    /** The day's diary, when it has one. Never counted, never part of the training. */
    val diary: DiaryDay? = null,
    /** Benchmark results recorded that day. A record apart from training: never counted. */
    val benchmarks: List<BenchmarkDayResult> = emptyList(),
)

data class WeekUiState(
    val weekStart: LocalDate,
    val today: LocalDate,
    val weekLabel: String,
    val unscheduled: List<WeekItem>,
    val days: List<DaySection>,
    /** What the diary tracks, in order, for its dialog and its one-line summaries. */
    val trackers: List<Tracker> = emptyList(),
) {
    val isCurrentWeek: Boolean get() = WeekMath.contains(weekStart, today)

    companion object {
        /**
         * Builds the week view. The seven day sections always exist, so an empty week still shows
         * its shape and the list does not jump once data arrives.
         *
         * Stations of a scheduled circuit are folded into their circuit's card rather than listed
         * on their own; a station whose circuit is missing falls back to being an ordinary card,
         * because losing sight of planned work would be worse than an odd-looking list.
         */
        fun build(
            weekStart: LocalDate,
            today: LocalDate,
            occurrences: List<PlannedOccurrence>,
            circuits: List<WeekCircuit> = emptyList(),
            modules: List<WeekModule> = emptyList(),
            diary: Map<LocalDate, DiaryDay> = emptyMap(),
            trackers: List<Tracker> = emptyList(),
            benchmarkResults: List<BenchmarkDayResult> = emptyList(),
        ): WeekUiState {
            val resultsByDate = benchmarkResults.groupBy { it.date }
            val stationsByCircuit = occurrences
                .filter { it.circuitInstanceId != null }
                .groupBy { it.circuitInstanceId }
            val known = circuits.map { it.id }.toSet()
            val modulesById = modules.associateBy { it.id }

            // A member folds into its module only while it shares the module's slot. Work logged
            // on another day, or moved there, is shown where it now is rather than hidden inside a
            // group that says it is somewhere else.
            fun groupOf(moduleInstanceId: String?, date: LocalDate?): WeekModule? =
                moduleInstanceId?.let(modulesById::get)?.takeIf { it.trainingDate == date }

            fun circuitItem(circuit: WeekCircuit) = WeekItem.Circuit(
                circuit = circuit,
                stations = stationsByCircuit[circuit.id]
                    .orEmpty()
                    .sortedBy { it.circuitPosition ?: it.orderIndex },
            )

            fun itemsFor(date: LocalDate?): List<WeekItem> {
                val singles = occurrences
                    .filter { it.trainingDate == date }
                    .filter { it.circuitInstanceId == null || it.circuitInstanceId !in known }
                val slotCircuits = circuits.filter { it.trainingDate == date }

                val loose = singles.filter { groupOf(it.moduleInstanceId, date) == null }
                    .map(WeekItem::Single) +
                    slotCircuits.filter { groupOf(it.moduleInstanceId, date) == null }
                        .map(::circuitItem)

                val grouped = modules
                    .filter { it.trainingDate == date }
                    .map { module ->
                        val members =
                            singles.filter { groupOf(it.moduleInstanceId, date) == module }
                                .map { (it.modulePosition ?: 0) to WeekItem.Single(it) } +
                                slotCircuits.filter { groupOf(it.moduleInstanceId, date) == module }
                                    .map { (it.modulePosition ?: 0) to circuitItem(it) }
                        WeekItem.Module(
                            module = module,
                            members = members.sortedBy { it.first }.map { it.second },
                        )
                    }
                return (loose + grouped).sortedBy { it.orderIndex }
            }

            return WeekUiState(
                weekStart = weekStart,
                today = today,
                weekLabel = WeekMath.weekLabel(weekStart),
                unscheduled = itemsFor(null),
                trackers = trackers,
                days = WeekMath.daysOf(weekStart).map { date ->
                    DaySection(
                        date = date,
                        isToday = date == today,
                        items = itemsFor(date),
                        diary = diary[date],
                        benchmarks = resultsByDate[date].orEmpty(),
                    )
                },
            )
        }
    }
}
