package com.yokodake.melete.data.plan

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A plan file: a library and weeks written by hand, everything referred to by name.
 *
 * The other half of "one import, two jobs". A backup (`melete-backup`) is the whole record with
 * its ids, for putting a phone back exactly; a plan (`melete-plan`) is what a person writes — no
 * ids, no timestamps, no snapshots — for filling a library and laying out a block of weeks. The
 * spec with examples is `docs/plan-format.md`.
 *
 * Every field is optional to the parser so that a missing one is reported by [PlanCheck] in words
 * ("an exercise has no name"), not by the JSON library in offsets.
 */
const val PLAN_FORMAT = "melete-plan"
const val PLAN_FORMAT_VERSION = 1

@Serializable
data class PlanFile(
    val format: String? = null,
    val version: Int? = null,
    val exercises: List<PlanExercise> = emptyList(),
    val circuits: List<PlanCircuit> = emptyList(),
    val modules: List<PlanModule> = emptyList(),
    val weeks: List<PlanWeek> = emptyList(),
)

@Serializable
data class PlanExercise(
    val name: String? = null,
    val mode: String? = null,
    val category: String? = null,
    val unilateral: Boolean = false,
    val unit: String? = null,
    val meaning: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val plan: PlanSpec? = null,
    /** Absent leaves the library's variations alone; listed ones are added or updated by tag. */
    val variations: List<PlanVariation>? = null,
)

@Serializable
data class PlanVariation(
    val tag: String? = null,
    val notes: String? = null,
    val plan: PlanSpec? = null,
)

/**
 * A plan, in the units it is typed in. Which fields mean anything depends on the exercise's mode,
 * exactly as in the editor; the others are dropped with a warning.
 */
@Serializable
data class PlanSpec(
    val sets: Int? = null,
    val reps: Int? = null,
    /** The length of one timed set. */
    val seconds: Int? = null,
    val restSeconds: Int? = null,
    val sideSwitchSeconds: Int? = null,
    /** An activity's length. */
    val minutes: Int? = null,
    /** How long a set-based exercise should take, overruling the estimate. */
    val plannedMinutes: Int? = null,
    val effort: String? = null,
    val repeater: PlanRepeater? = null,
)

@Serializable
data class PlanRepeater(
    val reps: Int? = null,
    val workSeconds: Int? = null,
    val restSeconds: Int = 0,
)

@Serializable
data class PlanCircuit(
    val name: String? = null,
    val category: String? = null,
    val rounds: Int = 1,
    val transitionSeconds: Int = 0,
    val roundRestSeconds: Int = 0,
    val stations: List<PlanItem> = emptyList(),
)

@Serializable
data class PlanModule(
    val name: String? = null,
    val description: String? = null,
    val entries: List<PlanItem> = emptyList(),
)

/**
 * One thing placed somewhere: a circuit's station, a module's entry or an item in a week. Exactly
 * one of [exercise], [circuit], [module] and [activity]; which are allowed depends on where.
 */
@Serializable
data class PlanItem(
    val exercise: String? = null,
    val variation: String? = null,
    val plan: PlanSpec? = null,
    val circuit: String? = null,
    val module: String? = null,
    val activity: String? = null,
    val minutes: Int? = null,
)

@Serializable
data class PlanWeek(
    val weekStart: String? = null,
    val unscheduled: List<PlanItem> = emptyList(),
    val monday: List<PlanItem> = emptyList(),
    val tuesday: List<PlanItem> = emptyList(),
    val wednesday: List<PlanItem> = emptyList(),
    val thursday: List<PlanItem> = emptyList(),
    val friday: List<PlanItem> = emptyList(),
    val saturday: List<PlanItem> = emptyList(),
    val sunday: List<PlanItem> = emptyList(),
) {
    /** The days in order, Monday first, each with its offset from [weekStart]. */
    val days: List<Pair<Int, List<PlanItem>>>
        get() = listOf(monday, tuesday, wednesday, thursday, friday, saturday, sunday).withIndex()
            .map { it.index to it.value }
}

/**
 * Lenient where a hand-written file can be without doubt — comments, trailing commas — and strict
 * where leniency would hide a mistake: an unknown key (a misspelt "Monday") is an error, not
 * something quietly ignored.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val PlanJson = Json {
    ignoreUnknownKeys = false
    allowComments = true
    allowTrailingComma = true
}
