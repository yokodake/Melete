package com.yokodake.melete.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The part of organising a plan that is arithmetic on values rather than database work.
 *
 * Moving and copying are mostly SQL, so the decision underneath them was pulled out here where it
 * can be checked without a device.
 */
class PlanningTest {

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

}
