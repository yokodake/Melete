package com.yokodake.melete.data

import com.yokodake.melete.data.entity.OccurrenceState

/**
 * What the app counts: a circuit is one, its stations together; every other exercise is one each.
 * Home's "x / y completed", the dashboard and the weekly review all count this way.
 */
object Counting {

    /** The key of the thing [occurrence] counts towards: its circuit, or itself. */
    fun unitKey(circuitInstanceId: String?, occurrenceId: String): String =
        circuitInstanceId?.let { "c:$it" } ?: "o:$occurrenceId"

    /**
     * The units planned work makes, each with the occurrences that count in it. Skipped work is
     * out, optional work counts only once done; a circuit with nothing left counting is no unit.
     */
    fun units(occurrences: List<PlannedOccurrence>): List<List<PlannedOccurrence>> =
        occurrences
            .filter { it.state != OccurrenceState.SKIPPED && (!it.optional || it.state == OccurrenceState.COMPLETED) }
            .groupBy { unitKey(it.circuitInstanceId, it.id) }
            .values.toList()

    /** A unit is done when everything counting in it is. */
    fun done(unit: List<PlannedOccurrence>): Boolean = unit.all { it.state == OccurrenceState.COMPLETED }

    /** How many units were skipped outright: an exercise, or a circuit with every station skipped. */
    fun skipped(occurrences: List<PlannedOccurrence>): Int =
        occurrences.groupBy { unitKey(it.circuitInstanceId, it.id) }.values
            .count { unit -> unit.all { it.state == OccurrenceState.SKIPPED } }
}
