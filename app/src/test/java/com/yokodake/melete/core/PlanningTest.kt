package com.yokodake.melete.core

import com.yokodake.melete.core.Planning.Landing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanningTest {

    /** Unscheduled, then three days: enough to cross boundaries both ways. */
    private val week = listOf(
        listOf("u1"),
        listOf("a", "b", "c"),
        emptyList(),
        listOf("d"),
    )

    @Test
    fun `within a slot an item swaps with its neighbour`() {
        assertEquals(Landing(1, 2), Planning.nudge(week, 1, "b", 1))
        assertEquals(Landing(1, 0), Planning.nudge(week, 1, "b", -1))
    }

    @Test
    fun `past the top of a day it lands at the bottom of the one above`() {
        assertEquals(Landing(0, 1), Planning.nudge(week, 1, "a", -1))
        assertEquals(Landing(2, 0), Planning.nudge(week, 3, "d", -1))
    }

    @Test
    fun `past the bottom of a day it lands at the top of the one below, empty days included`() {
        assertEquals(Landing(2, 0), Planning.nudge(week, 1, "c", 1))
        assertEquals(Landing(1, 0), Planning.nudge(week, 0, "u1", 1))
    }

    @Test
    fun `the ends of the week stop rather than wrap`() {
        assertNull(Planning.nudge(week, 0, "u1", -1))
        assertNull(Planning.nudge(week, 3, "d", 1))
    }

    @Test
    fun `an item not in the slot, or no movement, lands nowhere`() {
        assertNull(Planning.nudge(week, 1, "d", 1))
        assertNull(Planning.nudge(week, 1, "a", 0))
    }

    @Test
    fun `a bigger delta still moves one place, so a double tap is two nudges`() {
        assertEquals(Landing(1, 2), Planning.nudge(week, 1, "b", 5))
    }

    @Test
    fun `crossing is reported so the caller knows to change the date`() {
        val landing = Planning.nudge(week, 1, "a", -1)!!
        assertEquals(true, landing.crosses(from = 1))
        assertEquals(false, Planning.nudge(week, 1, "b", 1)!!.crosses(from = 1))
    }
}
