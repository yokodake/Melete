package com.yokodake.melete.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yokodake.melete.data.model.ExerciseMode

/**
 * A user-authored exercise definition. Exercises are data, never Kotlin classes: the app must be
 * able to express a new movement without a code change.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mode: ExerciseMode,
    /** `null` means the exercise has no numeric measurement (a stretch, a bodyweight movement). */
    val measurementUnit: String?,
    val unilateral: Boolean,
    /** The exercise's default prescription, stored separately from its identity. */
    val defaultPrescriptionId: String?,
    val createdAtEpochMs: Long,
    val isSampleData: Boolean = false,
)

/**
 * A prescription value record. Both an exercise's default and a scheduled copy point at one of
 * these; scheduling copies the payload into a new row, so editing a template cannot reach back
 * into work that is already planned or logged.
 */
@Entity(tableName = "prescriptions")
data class PrescriptionEntity(
    @PrimaryKey val id: String,
    val payloadVersion: Int,
    val payloadJson: String,
    val createdAtEpochMs: Long,
    val isSampleData: Boolean = false,
)

/** Explicit state of a planned occurrence. Zero actuals on its own never means "skipped". */
enum class OccurrenceState {
    PLANNED,
    COMPLETED,
    SKIPPED,
}

/**
 * One exercise placed in one week: either on a training date, or in the week's unscheduled area
 * when [trainingDateEpochDay] is null.
 *
 * [exerciseId] is lineage only and deliberately has no foreign key: the snapshot must survive the
 * template being renamed or deleted. Human-readable snapshots travel with the row so an export
 * stays understandable without the library.
 */
@Entity(
    tableName = "exercise_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = PrescriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
    indices = [
        Index("weekStartEpochDay"),
        Index("trainingDateEpochDay"),
        Index("exerciseId"),
        Index("prescriptionId"),
    ],
)
data class ExerciseOccurrenceEntity(
    @PrimaryKey val id: String,
    /** Monday of the week this occurrence belongs to, as a local date. */
    val weekStartEpochDay: Long,
    /** Local training date, or `null` for an unscheduled item inside the week. */
    val trainingDateEpochDay: Long?,
    val exerciseId: String,
    val exerciseNameSnapshot: String,
    val modeSnapshot: ExerciseMode,
    val unilateralSnapshot: Boolean,
    val measurementUnitSnapshot: String?,
    val prescriptionId: String?,
    val orderIndex: Int,
    val state: OccurrenceState,
    /** Comments belong to the occurrence, not to a set and not to the library exercise. */
    val comment: String?,
    val createdAtEpochMs: Long,
    val isSampleData: Boolean = false,
)
