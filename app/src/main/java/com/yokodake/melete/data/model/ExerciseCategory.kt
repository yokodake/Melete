package com.yokodake.melete.data.model

/**
 * What kind of training an exercise is. Deliberately a small closed set rather than free-form
 * tags: the point is a glanceable colour in a list, and a handful of meanings the user actually
 * plans around. A category is optional — an exercise without one is not a problem to be fixed.
 *
 * The order here is the order they are offered in, which runs from the most specific kind of
 * climbing to the least specific kind of anything.
 *
 * The constant names are stored on disk, so renaming one needs a migration — `MIGRATION_5_6`
 * rewrote the three this set grew out of.
 */
enum class ExerciseCategory(val label: String, val shortLabel: String = label) {
    /** Climbing where the session is the plan: bouldering, a sport day, an open session. */
    OPEN_CLIMBING("Open climbing", "OPEN"),

    /** All board session, whether projecting freely or volume, no endurance specific work tho */
    BOARD_CLIMBING("Board climbing", "BRD"),

    /** Climbing to a prescription: Mileage, movement, intervals, a circuit on the wall. */
    STRUCTURED_CLIMBING("Structured climbing", "Training"),

    /** Fingers specifically — hangs, repeaters, no-hangs. Kept apart from general strength
     * because it is the tissue that decides how often you can train at all. */
    FINGER_TRAINING("Finger training", "FGR"),

    /** Strength and conditioning: lifts, pulls, core, everything off the wall. */
    STRENGTH_CONDITIONING("Strength & Conditioning", "S&C"),

    /** Flexibility and mobility. */
    FLEXIBILITY("Flexibility", "FLEX"),

    /** Anything else that is still training: a run, a class, a swim. */
    OTHER_ACTIVITY("Other activity", "OTHER"),
}
