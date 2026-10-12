package com.yokodake.melete.data.reminders

import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.Counting
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.Tracker
import com.yokodake.melete.data.DiaryDay
import com.yokodake.melete.data.WeekCircuit
import com.yokodake.melete.data.WeekModule
import com.yokodake.melete.data.entity.OccurrenceState
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.LocalDateTime

/** One week's plan as the reminders read it. */
data class WeekPlan(
    val occurrences: List<PlannedOccurrence>,
    val circuits: List<WeekCircuit>,
    val modules: List<WeekModule>,
)

/** The decisions behind the reminders, apart from Android so they can be tested. */
object ReminderRules {

    /**
     * The first moment after [now] at [minuteOfDay] on one of [days] (ISO numbers; null is every
     * day). Null when [days] is empty: a reminder with no day never comes.
     */
    fun nextTime(now: LocalDateTime, minuteOfDay: Int, days: Set<Int>? = null): LocalDateTime? {
        if (days != null && days.isEmpty()) return null
        val time = timeOf(minuteOfDay)
        for (offset in 0L..7L) {
            val candidate = now.toLocalDate().plusDays(offset).atTime(time)
            if (candidate.isAfter(now) && (days == null || candidate.dayOfWeek.value in days)) return candidate
        }
        return null
    }

    /**
     * The names of [date]'s workouts that still have work to log: a module, a circuit or an
     * exercise, each once, in the day's order. Work that is logged, skipped or optional is not
     * waiting on anything.
     */
    fun workoutsToLog(date: LocalDate, plan: WeekPlan): List<String> {
        val circuits = plan.circuits.associateBy { it.id }
        val modules = plan.modules.associateBy { it.id }
        val waiting = plan.occurrences.filter {
            it.trainingDate == date && it.state == OccurrenceState.PLANNED && !it.optional
        }
        // Each piece of work stands for the outermost thing it was placed as.
        val units = linkedMapOf<String, Pair<Int, String>>()
        waiting.forEach { occurrence ->
            val circuit = occurrence.circuitInstanceId?.let(circuits::get)
            val module = (occurrence.moduleInstanceId ?: circuit?.moduleInstanceId)?.let(modules::get)
            when {
                module != null -> units.putIfAbsent("m:${module.id}", module.orderIndex to module.name)
                circuit != null -> units.putIfAbsent("c:${circuit.id}", circuit.orderIndex to circuit.name)
                else -> units.putIfAbsent("o:${occurrence.id}", occurrence.orderIndex to occurrence.name)
            }
        }
        return units.values.sortedBy { it.first }.map { it.second }
    }

    /** "Board session, Finger training + 2 more". */
    fun listShort(names: List<String>, shown: Int = 2): String {
        val head = names.take(shown).joinToString(", ")
        val more = names.size - shown
        return if (more > 0) "$head + $more more" else head
    }

    /**
     * The week a review on [date] is about: the one just finished when the review comes in the
     * first two days of a week, otherwise the week it falls in (midweek, the week under way). Counted from the week's start,
     * so it follows the start day wherever that is.
     */
    fun reviewedWeek(date: LocalDate): LocalDate {
        val start = WeekMath.weekStartOf(date)
        return if (ChronoUnit.DAYS.between(start, date) < 2) start.minusWeeks(1) else start
    }

    /**
     * A week in one line: how much of what counts is done. Counted as Home counts a day — skipped
     * work leaves the target, optional work joins it only once done.
     */
    fun weekSummary(plan: WeekPlan): String {
        val units = Counting.units(plan.occurrences)
        if (units.isEmpty()) return "Nothing was planned."
        return "${units.count(Counting::done)} of ${units.size} done"
    }

    /**
     * Whether [date] had work [reminder] follows: an exercise of a chosen category, or a chosen
     * exercise, circuit or module, logged or planned. Skipped work does not count, nor optional
     * work until it is done — as everywhere else.
     */
    fun hadMatchingWork(reminder: TrackerReminder, date: LocalDate, plan: WeekPlan): Boolean {
        val ids = reminder.workouts.groupBy({ it.kind }, { it.id }).mapValues { it.value.toSet() }
        val exercises = ids[WorkoutKind.EXERCISE].orEmpty()
        val routines = ids[WorkoutKind.CIRCUIT].orEmpty()
        val moduleIds = ids[WorkoutKind.MODULE].orEmpty()
        val circuits = plan.circuits.associateBy { it.id }
        val modules = plan.modules.associateBy { it.id }
        return plan.occurrences.any { occurrence ->
            if (occurrence.trainingDate != date || occurrence.state == OccurrenceState.SKIPPED) return@any false
            if (occurrence.optional && occurrence.state != OccurrenceState.COMPLETED) return@any false
            val circuit = occurrence.circuitInstanceId?.let(circuits::get)
            val module = (occurrence.moduleInstanceId ?: circuit?.moduleInstanceId)?.let(modules::get)
            occurrence.exerciseId in exercises ||
                occurrence.category?.name in reminder.categories ||
                circuit?.category?.name in reminder.categories ||
                circuit?.routineId in routines ||
                module?.moduleId in moduleIds
        }
    }

    /** The reminder's trackers, in the diary's order, that [day] has no value for yet. */
    fun trackersToFill(reminder: TrackerReminder, trackers: List<Tracker>, day: DiaryDay?): List<Tracker> =
        trackers.filter { tracker ->
            tracker.id in reminder.trackerIds && day?.values?.get(tracker.id)?.reading?.isEmpty != false
        }

    /** What a reminder says when it has no phrase of its own. */
    const val DEFAULT_TRACKER_TITLE = "Daily notes"
}
