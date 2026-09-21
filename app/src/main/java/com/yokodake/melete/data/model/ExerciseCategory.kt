package com.yokodake.melete.data.model

/**
 * What kind of training an exercise is. Deliberately a small closed set rather than free-form
 * tags: the point is a glanceable colour in a list, and a handful of meanings the user actually
 * plans around. A category is optional — an exercise without one is not a problem to be fixed.
 *
 * The constant names are stored on disk, so renaming one needs a migration.
 */
enum class ExerciseCategory(val label: String) {
    /** Open training: the session itself is the plan. Bouldering, a climbing session, lifting. */
    OPEN("Open"),

    /** Conditioning and strength work with a prescribed shape. */
    CONDITIONING("Conditioning"),

    /** Flexibility and mobility. */
    FLEXIBILITY("Flexibility"),
}
