package com.yokodake.melete.data

import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.entity.PrescriptionEntity
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
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
            meaning: MeasurementMeaning?,
            unilateral: Boolean,
            category: ExerciseCategory?,
            description: String?,
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
                measurementMeaning = unit?.let { meaning ?: MeasurementMeaning.TOTAL_LOAD },
                unilateral = unilateral,
                notes = null,
                defaultPrescriptionId = defaultPrescription.id,
                createdAtEpochMs = now,
                isSampleData = true,
                description = description,
                category = category,
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
                measurementMeaningSnapshot = unit?.let { meaning ?: MeasurementMeaning.TOTAL_LOAD },
                prescriptionId = scheduledCopy.id,
                orderIndex = orderIndex,
                state = OccurrenceState.PLANNED,
                comment = null,
                createdAtEpochMs = now,
                isSampleData = true,
                categorySnapshot = category,
            )
        }

        add(
            name = "Back squat",
            mode = ExerciseMode.REPETITIONS,
            unit = "kg",
            meaning = MeasurementMeaning.TOTAL_LOAD,
            unilateral = false,
            category = ExerciseCategory.CONDITIONING,
            description = "Bar on the upper back, brace, sit between the hips and stand up. " +
                "Depth below parallel without the pelvis tucking under.",
            payload = PrescriptionPayload(
                sets = 4,
                targetReps = 5,
                restSeconds = 180,
                rir = 2,
            ),
            dayOffset = 1,
            orderIndex = 0,
        )
        add(
            name = "Max hangs 20 mm",
            mode = ExerciseMode.DURATION,
            unit = "kg",
            meaning = MeasurementMeaning.ADDED_LOAD,
            unilateral = false,
            category = ExerciseCategory.CONDITIONING,
            description = "Half crimp on the 20 mm edge, shoulders engaged, elbows soft. " +
                "Stop the set the moment the grip position changes.",
            payload = PrescriptionPayload(
                sets = 5,
                targetDurationSeconds = 10,
                restSeconds = 180,
            ),
            dayOffset = 1,
            orderIndex = 1,
        )
        add(
            name = "Dumbbell row",
            mode = ExerciseMode.REPETITIONS,
            unit = "kg",
            meaning = MeasurementMeaning.TOTAL_LOAD,
            unilateral = true,
            category = ExerciseCategory.CONDITIONING,
            description = "One hand and one knee on the bench, back flat, pull the dumbbell to " +
                "the hip rather than to the shoulder.",
            payload = PrescriptionPayload(
                sets = 4,
                targetReps = 8,
                restSeconds = 60,
                effort = EffortLevel.HARD,
            ),
            dayOffset = 4,
            orderIndex = 0,
        )
        add(
            name = "Couch stretch",
            mode = ExerciseMode.DURATION,
            unit = null,
            meaning = null,
            unilateral = true,
            category = ExerciseCategory.FLEXIBILITY,
            description = "Rear shin against the wall, front foot forward, squeeze the glute of " +
                "the rear leg and bring the pelvis under before leaning back.",
            payload = PrescriptionPayload(sets = 2, targetDurationSeconds = 90),
            dayOffset = 3,
            orderIndex = 0,
        )
        add(
            name = "Mobility flow",
            mode = ExerciseMode.ACTIVITY,
            unit = null,
            meaning = null,
            unilateral = false,
            category = ExerciseCategory.OPEN,
            description = "Whatever the body asks for. No prescribed shape; move for the time.",
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
