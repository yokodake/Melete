package com.yokodake.melete.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Version of the named-field prescription payload. Bump it whenever the meaning of a field
 * changes, never reuse a name for a different quantity, and keep old versions readable.
 */
const val PRESCRIPTION_PAYLOAD_VERSION: Int = 2

/** How the app asks for a performed set. */
enum class ExerciseMode {
    /** Countable repetitions, e.g. a squat. */
    REPETITIONS,

    /** Timed sets of a discrete exercise, e.g. a hang or a stretch. */
    DURATION,

    /** Duration-only activity with no set structure, e.g. a mobility flow or a climbing session. */
    ACTIVITY,
}

/** What a numeric measurement means. These are different quantities and must never be merged. */
enum class MeasurementMeaning {
    /** Everything on the bar / the whole implement. */
    TOTAL_LOAD,

    /** Load added to bodyweight, e.g. a weight belt. */
    ADDED_LOAD,

    /** Load taken away, e.g. a band or a pulley counterweight. */
    ASSISTANCE,
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
    val rir: Int? = null,
) {
    init {
        require(sets >= 0) { "sets must not be negative" }
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
