package com.yokodake.melete.core

import kotlin.math.sign

/**
 * The pure parts of organising a plan.
 *
 * Moving and copying are mostly database work, but the decision underneath them — where an item
 * lands when you nudge it — is arithmetic on values. It lives here so it can be tested without a
 * device.
 */
object Planning {

    /** Where a nudged item lands: which slot, and at which position in it. */
    data class Landing(val slot: Int, val position: Int) {
        fun crosses(from: Int): Boolean = slot != from
    }

    /**
     * Moves one item a single place up ([delta] < 0) or down, across slot boundaries.
     *
     * [slots] are the week's slots in the order they are shown — the unscheduled area, then Monday
     * to Sunday — each holding its top-level items in order. Within a slot the item swaps with its
     * neighbour. Past the top of a slot it lands at the **bottom** of the one above, and past the
     * bottom at the **top** of the one below, which is exactly where it appears to go on screen.
     *
     * Returns null at the very top or bottom of the week, and for an item not in [slot]: nudging
     * the first thing up does nothing rather than wrapping round.
     */
    fun <T> nudge(slots: List<List<T>>, slot: Int, moving: T, delta: Int): Landing? {
        val items = slots.getOrNull(slot) ?: return null
        val from = items.indexOf(moving)
        if (from < 0 || delta == 0) return null
        val to = from + delta.sign
        if (to in items.indices) return Landing(slot, to)
        val next = slot + delta.sign
        val neighbour = slots.getOrNull(next) ?: return null
        return Landing(next, if (delta < 0) neighbour.size else 0)
    }
}
