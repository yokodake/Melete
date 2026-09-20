package com.yokodake.melete.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The training week runs Monday..Sunday (ISO-8601) and is identified by the [LocalDate] of its
 * Monday. Everything the user sees or schedules is a local training date, never an instant, so
 * travelling or logging just after midnight cannot regroup history.
 */
object WeekMath {

    const val DAYS_IN_WEEK = 7

    fun weekStartOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    fun daysOf(weekStart: LocalDate): List<LocalDate> =
        (0 until DAYS_IN_WEEK).map { weekStart.plusDays(it.toLong()) }

    fun contains(weekStart: LocalDate, date: LocalDate): Boolean =
        !date.isBefore(weekStart) && date.isBefore(weekStart.plusWeeks(1))

    /** e.g. `22–28 Sep 2026`, `29 Sep – 5 Oct 2026`, `28 Dec 2026 – 3 Jan 2027`. */
    fun weekLabel(weekStart: LocalDate, locale: Locale = Locale.getDefault()): String {
        val end = weekStart.plusDays((DAYS_IN_WEEK - 1).toLong())
        val day = DateTimeFormatter.ofPattern("d", locale)
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
        val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        return when {
            weekStart.year != end.year -> "${full.format(weekStart)} – ${full.format(end)}"
            weekStart.month != end.month -> "${dayMonth.format(weekStart)} – ${full.format(end)}"
            else -> "${day.format(weekStart)}–${full.format(end)}"
        }
    }

    /** e.g. `Mon 22 Sep`. */
    fun dayLabel(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEE d MMM", locale).format(date)
}
