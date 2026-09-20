package com.yokodake.melete.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class WeekMathTest {

    @Test
    fun `week starts on monday`() {
        val sunday = LocalDate.of(2026, 9, 27)
        val monday = LocalDate.of(2026, 9, 21)
        assertEquals(monday, WeekMath.weekStartOf(sunday))
        assertEquals(monday, WeekMath.weekStartOf(monday))
        assertEquals(monday, WeekMath.weekStartOf(LocalDate.of(2026, 9, 24)))
    }

    @Test
    fun `week has seven consecutive days`() {
        val days = WeekMath.daysOf(LocalDate.of(2026, 9, 21))
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 9, 21), days.first())
        assertEquals(LocalDate.of(2026, 9, 27), days.last())
    }

    @Test
    fun `containment is half open`() {
        val monday = LocalDate.of(2026, 9, 21)
        assertTrue(WeekMath.contains(monday, monday))
        assertTrue(WeekMath.contains(monday, LocalDate.of(2026, 9, 27)))
        assertFalse(WeekMath.contains(monday, LocalDate.of(2026, 9, 20)))
        assertFalse(WeekMath.contains(monday, LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun `week spanning the new year keeps both years visible`() {
        // 28 Dec 2026 is a Monday; that week ends in 2027.
        val monday = WeekMath.weekStartOf(LocalDate.of(2026, 12, 31))
        assertEquals(LocalDate.of(2026, 12, 28), monday)
        assertEquals(
            "28 Dec 2026 – 3 Jan 2027",
            WeekMath.weekLabel(monday, Locale.US),
        )
    }

    @Test
    fun `labels stay compact inside one month`() {
        assertEquals(
            "21–27 Sep 2026",
            WeekMath.weekLabel(LocalDate.of(2026, 9, 21), Locale.US),
        )
        assertEquals(
            "28 Sep – 4 Oct 2026",
            WeekMath.weekLabel(LocalDate.of(2026, 9, 28), Locale.US),
        )
    }
}
