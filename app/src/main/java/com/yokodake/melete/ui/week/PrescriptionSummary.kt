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

    private fun format(
        prescription: PrescriptionPayload?,
        mode: ExerciseMode,
        unilateral: Boolean,
        unreadable: Boolean,
    ): String {
        if (unreadable) return "Prescription could not be read"
        if (prescription == null) return "No prescription"
        val parts = mutableListOf<String>()
        parts += volume(prescription, mode, unilateral)
        prescription.measurement?.let { parts += measurement(it) }
        prescription.restSeconds?.let { parts += "rest ${duration(it)}" }
        prescription.effort?.let { parts += it.label.lowercase() }
        prescription.rir?.let { parts += "$it RIR" }
        return parts.joinToString(" · ")
    }

    private fun volume(
        prescription: PrescriptionPayload,
        mode: ExerciseMode,
        unilateral: Boolean,
    ): String {
        val target = when {
            prescription.targetReps != null -> "${prescription.targetReps}"
            prescription.targetDurationSeconds != null -> duration(prescription.targetDurationSeconds)
            else -> null
        }
        // A duration-only activity has no set structure to show.
        if (mode == ExerciseMode.ACTIVITY) return target ?: "Duration not set"
        val base = if (target == null) {
            "${prescription.sets} sets"
        } else {
            "${prescription.sets} × $target"
        }
        return if (unilateral) "$base per side" else base
    }

    private fun measurement(measurement: Measurement): String {
        val value = number(measurement.value)
        return when (measurement.meaning) {
            MeasurementMeaning.TOTAL_LOAD -> "$value ${measurement.unit}"
            MeasurementMeaning.ADDED_LOAD -> "+$value ${measurement.unit}"
            MeasurementMeaning.ASSISTANCE -> "−$value ${measurement.unit} assist"
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
