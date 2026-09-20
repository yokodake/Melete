package com.yokodake.melete.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Version of the named-field actual-set payload. */
const val ACTUAL_SET_PAYLOAD_VERSION: Int = 1

/**
 * What was actually performed in one set. Deliberately independent of [PrescriptionPayload]: a set
 * may record more reps than planned, a different load, or nothing that was planned at all.
 *
 * Absent values stay absent. No RPE means the user did not rate the set, not that the set was easy.
 */
@Serializable
data class ActualSetPayload(
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val measurement: Measurement? = null,
    val rpe: Double? = null,
    val rir: Int? = null,
) {
    val isEmpty: Boolean
        get() = reps == null && durationSeconds == null && measurement == null &&
            rpe == null && rir == null
}

object ActualSetJson {
    private val format = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    fun encode(payload: ActualSetPayload): String = format.encodeToString(payload)

    fun decode(json: String): ActualSetPayload = format.decodeFromString(json)
}
