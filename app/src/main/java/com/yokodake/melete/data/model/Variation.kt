package com.yokodake.melete.data.model

/**
 * The short name a variation goes by: `A`, `B`, `PWR`, `STR`, `END`, `MAX`.
 *
 * Capitals and digits only, at most four, because it is read as a chip beside an exercise's name
 * and has to fit there on a phone without truncating. Case is folded rather than refused, so
 * typing "pwr" gives `PWR` instead of an error.
 */
object VariationTag {
    const val MAX_LENGTH = 4

    /** What the field keeps of what was typed: upper-cased, letters and digits, four at most. */
    fun normalise(typed: String): String =
        typed.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(MAX_LENGTH)

    fun isValid(tag: String): Boolean =
        tag.length in 1..MAX_LENGTH && tag.all { it in 'A'..'Z' || it in '0'..'9' }
}
