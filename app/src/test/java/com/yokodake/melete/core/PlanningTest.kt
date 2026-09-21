package com.yokodake.melete.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The parts of organising a plan that are arithmetic on values rather than database work.
 *
 * Moving, copying and re-dating are mostly SQL, and this phase had no device to run instrumented
 * tests on, so the decisions underneath them were pulled out here where they can be checked.
 */
class PlanningTest {

    private val monday = LocalDate.of(2026, 9, 21)
    private fun dayName(date: LocalDate): String =
        date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

    // ------------------------------------------------------------- reorder

    @Test
    fun `moving an item down swaps it with the one below`() {
        assertEquals(
            listOf("a", "c", "b", "d"),
            Planning.reorder(listOf("a", "b", "c", "d"), "b", 1),
        )
    }

    @Test
    fun `moving an item up swaps it with the one above`() {
        assertEquals(
            listOf("a", "c", "b", "d"),
            Planning.reorder(listOf("a", "b", "c", "d"), "c", -1),
        )
    }

    @Test
    fun `nudging the top item up does nothing rather than wrapping to the bottom`() {
        val items = listOf("a", "b", "c")
        assertEquals(items, Planning.reorder(items, "a", -1))
        assertEquals(items, Planning.reorder(items, "c", 1))
    }

    @Test
    fun `a bigger nudge is clamped to the ends`() {
        assertEquals(
            listOf("b", "c", "d", "a"),
            Planning.reorder(listOf("a", "b", "c", "d"), "a", 99),
        )
    }

    @Test
    fun `an item that is not in the list leaves it alone`() {
        val items = listOf("a", "b")
        assertEquals(items, Planning.reorder(items, "z", 1))
        assertEquals(items, Planning.reorder(items, "a", 0))
    }

    // ------------------------------------------------- planned vs performed

    @Test
    fun `work logged on the day it was planned has nothing to report`() {
        assertNull(Planning.dateMismatch(planned = monday, performed = monday))
    }

    @Test
    fun `a plan with nothing logged against it has nothing to report`() {
        assertNull(Planning.dateMismatch(planned = monday, performed = null))
    }

    @Test
    fun `work done on another day is reported rather than reconciled`() {
        // Planned Monday, trained Tuesday. Both are true; neither overwrites the other.
        val mismatch = Planning.dateMismatch(planned = monday, performed = monday.plusDays(1))
        assertEquals(monday, mismatch?.planned)
        assertEquals(monday.plusDays(1), mismatch?.performed)
        assertEquals("Planned Mon · Logged Tue", mismatch?.label(::dayName))
    }

    @Test
    fun `work logged against an unscheduled item reports only where it landed`() {
        val mismatch = Planning.dateMismatch(planned = null, performed = monday.plusDays(2))
        assertEquals("Logged Wed", mismatch?.label(::dayName))
    }
}
