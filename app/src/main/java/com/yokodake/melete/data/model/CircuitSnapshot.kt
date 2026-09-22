package com.yokodake.melete.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Version of the circuit structure snapshot. */
const val CIRCUIT_SNAPSHOT_VERSION: Int = 1

/**
 * What a routine looked like when a week was given a copy of it.
 *
 * The scheduled circuit's exercises are real occurrences and carry their own prescription copies,
 * so this is not where the plan lives. What it preserves is the *shape* — which stations, in which
 * order, with which numbers, at which version of the routine — so a log read months later can say
 * what circuit it was part of even after the routine has been edited, renamed or retired.
 */
@Serializable
data class CircuitStructureSnapshot(
    val version: Int = CIRCUIT_SNAPSHOT_VERSION,
    val routineId: String,
    val routineName: String,
    val structureVersion: Int,
    val rounds: Int,
    val transitionSeconds: Int,
    val roundRestSeconds: Int,
    val entries: List<CircuitEntrySnapshot>,
)

@Serializable
data class CircuitEntrySnapshot(
    val position: Int,
    val exerciseId: String,
    val exerciseName: String,
    val mode: ExerciseMode,
    val unilateral: Boolean,
    val prescription: PrescriptionPayload?,
)

object CircuitSnapshotJson {
    private val format = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    fun encode(snapshot: CircuitStructureSnapshot): String = format.encodeToString(snapshot)

    fun decode(json: String): CircuitStructureSnapshot? =
        runCatching { format.decodeFromString<CircuitStructureSnapshot>(json) }.getOrNull()
}
