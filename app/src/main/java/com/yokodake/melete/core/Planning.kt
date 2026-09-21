package com.yokodake.melete.core

import java.time.LocalDate

/**
 * The pure parts of organising a plan.
 *
 * Moving, copying and re-dating are mostly database work, but the two decisions underneath them —
 * where an item lands when you nudge it, and what to say when the plan and the record disagree —
 * are arithmetic on values. They live here so they can be tested without a device, which is the
 * only kind of test this phase can actually run.
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

    /**
     * How a planned date and a performed date differ, or null when there is nothing to say.
     *
     * The two are allowed to disagree on purpose: work planned for Monday and done on Tuesday is
     * an ordinary thing that happened, not an inconsistency to be reconciled. The app's job is to
     * show it plainly rather than to pick one and quietly overwrite the other.
     */
    fun dateMismatch(planned: LocalDate?, performed: LocalDate?): DateMismatch? {
        if (performed == null) return null
        if (planned == null) return DateMismatch(planned = null, performed = performed)
        if (planned == performed) return null
        return DateMismatch(planned = planned, performed = performed)
    }

    /** A planned placement and a performed date that are not the same day. */
    data class DateMismatch(val planned: LocalDate?, val performed: LocalDate) {
        /** e.g. `Planned Mon · Logged Tue`, or `Logged Tue` for work that was never planned. */
        fun label(dayName: (LocalDate) -> String): String = when (planned) {
            null -> "Logged ${dayName(performed)}"
            else -> "Planned ${dayName(planned)} · Logged ${dayName(performed)}"
        }
    }
}
