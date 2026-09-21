package com.yokodake.melete.ui

import com.yokodake.melete.ui.logger.SetRow
import com.yokodake.melete.ui.logger.SetTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a logged workout is complete enough to be written, and what it says when it is.
 *
 * The table is a draft: ticking, unticking and typing cost nothing until the workout is marked
 * done. These rules are the whole of the commit decision, so they are the part worth testing.
 */
class SetTableTest {

    private fun table(
        vararg rows: SetRow,
        maxLoad: String = "",
        maxLoadRight: String = "",
    ) = SetTable(rows = rows.toList(), maxLoad = maxLoad, maxLoadRight = maxLoadRight)

    private fun row(number: Int, load: String = "", loadRight: String = "", done: Boolean = true) =
        SetRow(number = number, reps = 5, load = load, loadRight = loadRight, done = done)

    @Test
    fun `every set carrying its own load is enough`() {
        val t = table(row(1, "60"), row(2, "60"), row(3, "65"))
        assertTrue(t.loggable(measured = true))
        assertNull(t.blocker(measured = true))
    }

    @Test
    fun `a max load stands for every set that has none`() {
        val t = table(row(1), row(2), row(3), maxLoad = "60")
        assertTrue(t.loggable(measured = true))
        assertEquals(listOf("60", "60", "60"), t.resolved().map { it.load })
    }

    @Test
    fun `a set that says something of its own keeps it`() {
        // The max load is a fallback, not an override: 65 was typed deliberately.
        val t = table(row(1), row(2, "65"), row(3), maxLoad = "60")
        assertEquals(listOf("60", "65", "60"), t.resolved().map { it.load })
    }

    @Test
    fun `ticked sets with no load anywhere cannot be logged`() {
        val t = table(row(1), row(2))
        assertFalse(t.loggable(measured = true))
        assertEquals("Fill in the max load, or every set's load", t.blocker(measured = true))
    }

    @Test
    fun `one set missing its load blocks the whole table`() {
        val t = table(row(1, "60"), row(2), row(3, "60"))
        assertFalse(t.loggable(measured = true))
    }

    /** An unticked set did not happen, so it neither counts against the table nor gets written. */
    @Test
    fun `an unticked set is not required to say what it weighed`() {
        val t = table(row(1, "60"), row(2, done = false), row(3, "60"))
        assertTrue(t.loggable(measured = true))
        assertEquals(listOf(1, 3), t.resolved().map { it.number })
    }

    @Test
    fun `nothing ticked means there is nothing to log`() {
        val t = table(row(1, "60", done = false), row(2, "60", done = false))
        assertFalse(t.loggable(measured = true))
        assertEquals("Tick at least one set", t.blocker(measured = true))
    }

    /** Bodyweight work measures nothing, so a tick is the entire claim. */
    @Test
    fun `an unmeasured exercise needs only a ticked set`() {
        val t = table(row(1), row(2))
        assertTrue(t.loggable(measured = false))
        assertNull(t.blocker(measured = false))
    }

    @Test
    fun `an unmeasured exercise with nothing ticked still has nothing to log`() {
        val t = table(row(1, done = false))
        assertFalse(t.loggable(measured = false))
        assertEquals("Tick at least one set", t.blocker(measured = false))
    }

    /**
     * A unilateral row carries two loads. The right side falls back to its own max first, and only
     * then to the single max load, so "60 both sides" and "60 left, 50 right" both work.
     */
    @Test
    fun `a unilateral row falls back per side`() {
        val both = table(row(1), maxLoad = "60").resolved().single()
        assertEquals("60", both.load)
        assertEquals("60", both.loadRight)

        val split = table(row(1), maxLoad = "60", maxLoadRight = "50").resolved().single()
        assertEquals("60", split.load)
        assertEquals("50", split.loadRight)
    }
}
