package com.yokodake.melete.ui.week

import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionPayload

/**
 * One-line rendering of a *planned* prescription. This is never a record of performed work; the
 * logger shows actuals separately and clearly distinguished.
 */
object PrescriptionSummary {

    fun format(occurrence: PlannedOccurrence): String = format(
        prescription = occurrence.prescription,
        mode = occurrence.mode,
        unilateral = occurrence.unilateral,
        unreadable = occurrence.prescriptionUnreadable,
    )

    fun formatDefault(exercise: LibraryExercise): String = format(
        prescription = exercise.defaultPrescription,
        mode = exercise.mode,
        unilateral = exercise.unilateral,
        unreadable = false,
    )

    /** A plan that belongs to no occurrence yet: a variation, or a module entry. */
    fun formatPlan(
        prescription: PrescriptionPayload?,
        mode: ExerciseMode,
        unilateral: Boolean,
    ): String = format(prescription, mode, unilateral, unreadable = false)

    /**
     * One station of a circuit: what a single set of it asks for, without the set count and rest
     * the circuit replaces with its rounds and its own rests. "8 reps · 20 kg", "30 s", "6 × 7/3s".
     */
    fun formatStation(
        prescription: PrescriptionPayload?,
        mode: ExerciseMode,
        unilateral: Boolean,
    ): String {
        if (prescription == null) return "No plan set"
        val repeater = prescription.repeater
        val target = when {
            repeater != null -> "${repeater.repsPerSet} × ${repeater.workSecondsPerRep}" +
                (if (repeater.restSecondsBetweenReps > 0) "/${repeater.restSecondsBetweenReps}" else "") + "s"
            prescription.targetReps != null ->
                "${prescription.targetReps} ${if (prescription.targetReps == 1) "rep" else "reps"}"
            prescription.targetDurationSeconds != null -> duration(prescription.targetDurationSeconds)
            else -> null
        }
        val parts = mutableListOf<String>()
        target?.let { parts += if (unilateral && mode != ExerciseMode.ACTIVITY) "$it per side" else it }
        prescription.measurement?.let { parts += measurement(it) }
        prescription.effort?.let { parts += it.label.lowercase() }
        return parts.joinToString(" · ").ifEmpty { "1 set" }
    }

    private fun format(
        prescription: PrescriptionPayload?,
        mode: ExerciseMode,
        unilateral: Boolean,
        unreadable: Boolean,
    ): String {
        if (unreadable) return "Couldn’t load this plan"
        if (prescription == null) return "No plan set"
        val parts = mutableListOf<String>()
        parts += volume(prescription, mode, unilateral)
        prescription.measurement?.let { parts += measurement(it) }
        if (mode.hasSetStructure)
            prescription.restSeconds?.let { parts += "rest ${duration(it)}" }
        prescription.effort?.let { parts += it.label.lowercase() }
        return parts.joinToString(" · ")
    }

    private fun volume(
        prescription: PrescriptionPayload,
        mode: ExerciseMode,
        unilateral: Boolean,
    ): String {
        val repeater = prescription.repeater
        val target = when {
            // A repeater is the shape of one set, so it is what a set is described as.
            repeater != null ->
                "${repeater.repsPerSet} × ${repeater.workSecondsPerRep}" +
                    if (repeater.restSecondsBetweenReps > 0) {
                        "/${repeater.restSecondsBetweenReps}"
                    } else {
                        ""
                    } + "s"

            prescription.targetReps != null -> "${prescription.targetReps}"
            prescription.targetDurationSeconds != null -> duration(prescription.targetDurationSeconds)
            else -> null
        }
        // A duration-only activity has no set structure to show.
        if (mode == ExerciseMode.ACTIVITY) return target ?: "Duration not set"
        if (mode == ExerciseMode.REPEATERS && repeater == null) return "Repeaters not set up"
        val base = if (target == null) {
            "${prescription.sets} ${if (prescription.sets == 1) "set" else "sets"}"
        } else {
            "${prescription.sets} × $target"
        }
        return if (unilateral) "$base per side" else base
    }

    private fun measurement(measurement: Measurement): String = load(measurement)

    /** A load as it reads: "60 kg" in total, "+10 kg" added, "−15 kg" taken off by assistance. */
    fun load(measurement: Measurement): String = when (measurement.meaning) {
        MeasurementMeaning.TOTAL_LOAD -> "${number(measurement.value)} ${measurement.unit}"
        MeasurementMeaning.ADDED_LOAD -> {
            val sign = if (measurement.value < 0) "−" else "+"
            "$sign${number(kotlin.math.abs(measurement.value))} ${measurement.unit}"
        }
    }

    fun duration(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds % 60 == 0 && seconds < 3600 -> "${seconds / 60} min"
        else -> "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
