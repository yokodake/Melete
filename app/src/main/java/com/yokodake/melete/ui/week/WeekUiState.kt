package com.yokodake.melete.ui.week

import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.PlannedOccurrence
import java.time.LocalDate

data class DaySection(
    val date: LocalDate,
    val isToday: Boolean,
    val items: List<PlannedOccurrence>,
)

data class WeekUiState(
    val weekStart: LocalDate,
    val today: LocalDate,
    val weekLabel: String,
    val unscheduled: List<PlannedOccurrence>,
    val days: List<DaySection>,
    val sampleDataPresent: Boolean,
) {
    val isCurrentWeek: Boolean get() = WeekMath.contains(weekStart, today)

    companion object {
        /**
         * Builds the week view. The seven day sections always exist, so an empty week still shows
         * its shape and the list does not jump once data arrives.
         */
        fun build(
            weekStart: LocalDate,
            today: LocalDate,
            occurrences: List<PlannedOccurrence>,
            sampleDataPresent: Boolean,
        ): WeekUiState {
            val byDate = occurrences.filter { it.trainingDate != null }.groupBy { it.trainingDate }
            return WeekUiState(
                weekStart = weekStart,
                today = today,
                weekLabel = WeekMath.weekLabel(weekStart),
                unscheduled = occurrences.filter { it.trainingDate == null }.sortedBy { it.orderIndex },
                days = WeekMath.daysOf(weekStart).map { date ->
                    DaySection(
                        date = date,
                        isToday = date == today,
                        items = byDate[date].orEmpty().sortedBy { it.orderIndex },
                    )
                },
                sampleDataPresent = sampleDataPresent,
            )
        }
    }
}
