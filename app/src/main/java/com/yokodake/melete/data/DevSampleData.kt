package com.yokodake.melete.data

import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.entity.PrescriptionEntity
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PRESCRIPTION_PAYLOAD_VERSION
import com.yokodake.melete.data.model.PrescriptionJson
import com.yokodake.melete.data.model.PrescriptionPayload
import java.time.LocalDate
import java.util.UUID

/**
 * Development sample data. Every row carries `isSampleData = true` and a visible name prefix so
 * it can never be confused with, or accidentally left inside, the real training record.
 */
object DevSampleData {

    const val NAME_PREFIX = "Sample · "

    data class Bundle(
        val exercises: List<ExerciseEntity>,
        val prescriptions: List<PrescriptionEntity>,
        val occurrences: List<ExerciseOccurrenceEntity>,
    )

    fun build(weekStart: LocalDate, now: Long = System.currentTimeMillis()): Bundle {
        val exercises = mutableListOf<ExerciseEntity>()
        val prescriptions = mutableListOf<PrescriptionEntity>()
        val occurrences = mutableListOf<ExerciseOccurrenceEntity>()

        fun add(
            name: String,
            mode: ExerciseMode,
            unit: String?,
            unilateral: Boolean,
            payload: PrescriptionPayload,
            dayOffset: Int?,
            orderIndex: Int,
        ) {
            // The library default and the scheduled copy are two separate prescription rows, so a
            // later edit of the default cannot reach the copy that is already in the week.
            val defaultPrescription = prescriptionRow(payload, now)
            val scheduledCopy = prescriptionRow(payload, now)
            val exercise = ExerciseEntity(
                id = UUID.randomUUID().toString(),
                name = NAME_PREFIX + name,
                mode = mode,
                measurementUnit = unit,
                measurementMeaning = payload.measurement?.meaning,
                unilateral = unilateral,
                notes = null,
                defaultPrescriptionId = defaultPrescription.id,
                createdAtEpochMs = now,
                isSampleData = true,
            )
            prescriptions += defaultPrescription
            prescriptions += scheduledCopy
            exercises += exercise
            occurrences += ExerciseOccurrenceEntity(
                id = UUID.randomUUID().toString(),
                weekStartEpochDay = weekStart.toEpochDay(),
                trainingDateEpochDay = dayOffset?.let { weekStart.plusDays(it.toLong()).toEpochDay() },
                exerciseId = exercise.id,
                exerciseNameSnapshot = exercise.name,
                modeSnapshot = mode,
                unilateralSnapshot = unilateral,
                measurementUnitSnapshot = unit,
                measurementMeaningSnapshot = payload.measurement?.meaning,
                prescriptionId = scheduledCopy.id,
                orderIndex = orderIndex,
                state = OccurrenceState.PLANNED,
                comment = null,
                createdAtEpochMs = now,
                isSampleData = true,
            )
        }

        add(
            name = "Back squat",
            mode = ExerciseMode.REPETITIONS,
            unit = "kg",
            unilateral = false,
            payload = PrescriptionPayload(
                sets = 4,
                targetReps = 5,
                restSeconds = 180,
                measurement = Measurement(80.0, "kg", MeasurementMeaning.TOTAL_LOAD),
                rir = 2,
            ),
            dayOffset = 1,
            orderIndex = 0,
        )
        add(
            name = "Max hangs 20 mm",
            mode = ExerciseMode.DURATION,
            unit = "kg",
            unilateral = false,
            payload = PrescriptionPayload(
                sets = 5,
                targetDurationSeconds = 10,
                restSeconds = 180,
                measurement = Measurement(12.5, "kg", MeasurementMeaning.ADDED_LOAD),
            ),
            dayOffset = 1,
            orderIndex = 1,
        )
        add(
            name = "Dumbbell row",
            mode = ExerciseMode.REPETITIONS,
            unit = "kg",
            unilateral = true,
            payload = PrescriptionPayload(
                sets = 4,
                targetReps = 8,
                restSeconds = 60,
                measurement = Measurement(22.5, "kg", MeasurementMeaning.TOTAL_LOAD),
                effort = EffortLevel.HARD,
            ),
            dayOffset = 4,
            orderIndex = 0,
        )
        add(
            name = "Couch stretch",
            mode = ExerciseMode.DURATION,
            unit = null,
            unilateral = true,
            payload = PrescriptionPayload(sets = 2, targetDurationSeconds = 90),
            dayOffset = 3,
            orderIndex = 0,
        )
        add(
            name = "Mobility flow",
            mode = ExerciseMode.ACTIVITY,
            unit = null,
            unilateral = false,
            payload = PrescriptionPayload(sets = 1, targetDurationSeconds = 600),
            dayOffset = null,
            orderIndex = 0,
        )

        return Bundle(exercises, prescriptions, occurrences)
    }

    private fun prescriptionRow(payload: PrescriptionPayload, now: Long) = PrescriptionEntity(
        id = UUID.randomUUID().toString(),
        payloadVersion = PRESCRIPTION_PAYLOAD_VERSION,
        payloadJson = PrescriptionJson.encode(payload),
        createdAtEpochMs = now,
        isSampleData = true,
    )
}
