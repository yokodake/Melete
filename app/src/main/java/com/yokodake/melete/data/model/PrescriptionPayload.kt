package com.yokodake.melete.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Version of the named-field prescription payload. Bump it whenever the meaning of a field
 * changes, never reuse a name for a different quantity, and keep old versions readable.
 */
const val PRESCRIPTION_PAYLOAD_VERSION: Int = 3

/** How the app asks for a performed set. */
enum class ExerciseMode(val label: String) {
    /** Countable repetitions, e.g. a squat. */
    REPETITIONS("Reps"),

    /** Timed sets of a discrete exercise, e.g. a hang or a stretch. */
    DURATION("Timed sets"),

    /**
     * Timed sets made of pulses: so many short efforts inside one set, e.g. hangboard repeaters.
     *
     * A kind of exercise rather than a switch on a timed one, because it is a different movement
     * to plan, to run and to read back. Six sevens on and threes off is not "a 57-second hang",
     * and a hang is not a repeater with one rep — asking which of the two you are creating is a
     * clearer question than asking for a duration and then taking it away again.
     */
    REPEATERS("Repeaters"),

    /** Duration-only activity with no set structure, e.g. a mobility flow or a climbing session. */
    ACTIVITY("Activity");

    val hasSetStructure get() = this != ExerciseMode.ACTIVITY
}

/** What a numeric measurement means. These are different quantities and must never be merged. */
enum class MeasurementMeaning(val label: String) {
    /** Everything on the bar / the whole implement. Never negative. */
    TOTAL_LOAD("Total load"),

    /**
     * Load relative to bodyweight: positive is added (a weight belt), negative is taken away (a
     * band, a pulley counterweight). One signed scale rather than "added" and "assistance" apart,
     * so moving from assisted to weighted reads as one line of progress, and the heaviest set is
     * always simply the largest number.
     */
    ADDED_LOAD("Added load"),
}

@Serializable
data class Measurement(
    val value: Double,
    val unit: String,
    val meaning: MeasurementMeaning,
)

/**
 * A planned prescription. Absent values are stored as absent (`null`), never as `0`: no target
 * RPE is not an RPE of zero, and no rest is not a rest of zero seconds.
 */
@Serializable
data class PrescriptionPayload(
    val sets: Int,
    val targetReps: Int? = null,
    val targetDurationSeconds: Int? = null,
    val restSeconds: Int? = null,
    val measurement: Measurement? = null,
    /** Target effort on the five-point verbal scale. Replaced the numeric `rpe` field in v2. */
    val effort: EffortLevel? = null,
    /**
     * v3. How long the whole exercise is expected to take, rests included.
     *
     * Absent asks for an estimate rather than meaning zero: the app can usually work the number
     * out from the shape of the work, and typing one here is how you overrule it.
     */
    val plannedDurationSeconds: Int? = null,
    /**
     * v3. How long changing sides takes, for a unilateral exercise. Absent means the default;
     * zero is a real answer and means the sides run back to back.
     */
    val sideSwitchSeconds: Int? = null,
    /**
     * v3. The pulse shape of one set. Present exactly when the exercise's mode is
     * [ExerciseMode.REPEATERS]; the mode is what decides, so a payload cannot claim to be both a
     * plain timed set and a series of efforts.
     */
    val repeater: RepeaterPrescription? = null,
) {
    init {
        require(sets >= 0) { "sets must not be negative" }
    }
}

/**
 * A repeater, as it is planned: so many timed efforts inside one set, this long each, with this
 * much between them.
 *
 * Stored with the prescription rather than being flattened into sets and rest, because the shape
 * is what a later reading of the record needs: "three sets of six sevens" and "eighteen sets of
 * seven" are the same seconds and different training, and only one of them is what happened.
 */
@Serializable
data class RepeaterPrescription(
    val repsPerSet: Int,
    val workSecondsPerRep: Int,
    val restSecondsBetweenReps: Int = 0,
) {
    /** The timer shape this plans. Null when the numbers do not yet describe a real repeater. */
    fun toSpec(): com.yokodake.melete.data.timer.RepeaterSpec? =
        if (repsPerSet >= 1 && workSecondsPerRep >= 1) {
            com.yokodake.melete.data.timer.RepeaterSpec(
                repsPerSet = repsPerSet,
                workSecondsPerRep = workSecondsPerRep,
                restSecondsBetweenReps = restSecondsBetweenReps.coerceAtLeast(0),
            )
        } else {
            null
        }
}

object PrescriptionJson {
    private val format = Json {
        encodeDefaults = true
        explicitNulls = true
        // Tolerate payloads written by a newer app version rather than losing the whole record.
        ignoreUnknownKeys = true
    }

    fun encode(payload: PrescriptionPayload): String = format.encodeToString(payload)

    fun decode(json: String): PrescriptionPayload = format.decodeFromString(json)
}
