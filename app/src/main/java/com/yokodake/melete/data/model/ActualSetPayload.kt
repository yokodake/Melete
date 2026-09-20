package com.yokodake.melete.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Version of the named-field actual-set payload. */
const val ACTUAL_SET_PAYLOAD_VERSION: Int = 2

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
    /**
     * How hard the set was, on the five-point verbal scale. Replaced numeric `rpe` in v2.
     *
     * There is deliberately no reps-in-reserve here. Reserve is a planning target; asking for it
     * again after every set is exactly the confirmation friction this app exists to avoid.
     */
    val effort: EffortLevel? = null,
) {
    val isEmpty: Boolean
        get() = reps == null && durationSeconds == null && measurement == null && effort == null
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
