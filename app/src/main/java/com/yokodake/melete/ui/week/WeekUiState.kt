package com.yokodake.melete.ui.week

import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.WeekCircuit
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
}

data class DaySection(
    val date: LocalDate,
    val isToday: Boolean,
    val items: List<WeekItem>,
)

data class WeekUiState(
    val weekStart: LocalDate,
    val today: LocalDate,
    val weekLabel: String,
    val unscheduled: List<WeekItem>,
    val days: List<DaySection>,
    val sampleDataPresent: Boolean,
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
            sampleDataPresent: Boolean = false,
        ): WeekUiState {
            val stationsByCircuit = occurrences
                .filter { it.circuitInstanceId != null }
                .groupBy { it.circuitInstanceId }
            val known = circuits.map { it.id }.toSet()

            fun itemsFor(date: LocalDate?): List<WeekItem> {
                val singles = occurrences
                    .filter { it.trainingDate == date }
                    .filter { it.circuitInstanceId == null || it.circuitInstanceId !in known }
                    .map(WeekItem::Single)
                val groups = circuits
                    .filter { it.trainingDate == date }
                    .map { circuit ->
                        WeekItem.Circuit(
                            circuit = circuit,
                            stations = stationsByCircuit[circuit.id]
                                .orEmpty()
                                .sortedBy { it.circuitPosition ?: it.orderIndex },
                        )
                    }
                return (singles + groups).sortedBy { it.orderIndex }
            }

            return WeekUiState(
                weekStart = weekStart,
                today = today,
                weekLabel = WeekMath.weekLabel(weekStart),
                unscheduled = itemsFor(null),
                days = WeekMath.daysOf(weekStart).map { date ->
                    DaySection(
                        date = date,
                        isToday = date == today,
                        items = itemsFor(date),
                    )
                },
                sampleDataPresent = sampleDataPresent,
            )
        }
    }
}
