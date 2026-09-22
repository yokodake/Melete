package com.yokodake.melete.core

/**
 * Identity for an activity that was typed in rather than picked from the library.
 *
 * A one-off leaves no row in `exercises` — going for a run once should not add an entry you then
 * have to tidy away. But it still needs a stable exercise id, because that id is what groups
 * history: two sessions both called "Outdoor bouldering" have to be the same thing when the
 * dashboard counts them, and they have to stay the same thing after the app is reinstalled from a
 * backup. Deriving the id from the name gives exactly that, with no table to keep in step.
 *
 * The name is normalised only for *matching* — case and surrounding space are not a different
 * activity — while the name the user typed is stored as they typed it. The prefix keeps a derived
 * id from ever colliding with a real library UUID, and is what a later "promote this to a library
 * entry" step would look for.
 */
object OneOffActivity {

    const val ID_PREFIX = "one-off:"

    /** The stable id for a one-off called [name]. */
    fun exerciseIdFor(name: String): String = ID_PREFIX + normalise(name)

    /** True for an id this object produced. */
    fun isOneOffId(exerciseId: String): Boolean = exerciseId.startsWith(ID_PREFIX)

    /**
     * The matching form of a name: trimmed, lower-cased, and with runs of whitespace collapsed.
     *
     * Deliberately conservative. "Outdoor bouldering" and "outdoor  bouldering" are the same
     * activity; "Outdoor bouldering (Fontainebleau)" is a different one, and guessing otherwise
     * would silently merge two things the user chose to distinguish.
     */
    fun normalise(name: String): String =
        name.trim().lowercase().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
}
