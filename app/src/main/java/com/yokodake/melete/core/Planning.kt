package com.yokodake.melete.core

import java.time.LocalDate

/**
 * The pure parts of organising a plan.
 *
 * Moving and copying are mostly database work, but the decision underneath them — where an item
 * lands when you nudge it — is arithmetic on values. It lives here so it can be tested without a
 * device.
 */
object Planning {

    /**
     * Moves one item [delta] places within its list, clamped at both ends.
     *
     * Clamped rather than wrapping: nudging the top item up should do nothing, not send it to the
     * bottom. Returns the list unchanged when the item is not in it.
     */
    fun <T> reorder(items: List<T>, moving: T, delta: Int): List<T> {
        val from = items.indexOf(moving)
        if (from < 0 || delta == 0) return items
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (to == from) return items
        return items.toMutableList().apply {
            removeAt(from)
            add(to, moving)
        }
    }
}
