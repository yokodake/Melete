package com.yokodake.melete.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning

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
    /** What the measurement means. Absent exactly when [measurementUnit] is absent. */
    val measurementMeaning: MeasurementMeaning?,
    val unilateral: Boolean,
    /** Free variation notes: grip, board, tempo, shoe. Never required. */
    val notes: String?,
    /** The exercise's default prescription, stored separately from its identity. */
    val defaultPrescriptionId: String?,
    val createdAtEpochMs: Long,
    val isSampleData: Boolean = false,
    /**
     * What the movement is and how to do it. Reference material, not part of the training record:
     * it is read live rather than snapshotted, so correcting an explanation corrects it
     * everywhere instead of leaving old copies saying something the user no longer believes.
     */
    val description: String? = null,
    /** Optional training-purpose grouping. Absent is a valid answer. */
    val category: ExerciseCategory? = null,
)

/**
 * A prescription value record. Both an exercise's default and a scheduled copy point at one of
 * these; scheduling copies the payload into a new row, so editing a template cannot reach back
 * into work that is already planned or logged.
 *
 * Rows are never mutated in place. Changing a prescription writes a new row and repoints its
 * owner, which keeps an actual set's reference pointing at what was really planned at the time.
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

/** Which side a set was performed on. Absent for bilateral work. */
enum class BodySide {
    LEFT,
    RIGHT,
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
    val measurementMeaningSnapshot: MeasurementMeaning?,
    val prescriptionId: String?,
    val orderIndex: Int,
    val state: OccurrenceState,
    /** Comments belong to the occurrence, not to a set and not to the library exercise. */
    val comment: String?,
    val createdAtEpochMs: Long,
    val isSampleData: Boolean = false,
    /**
     * The category as it stood when this copy was placed in the week. Snapshotted like the other
     * identity fields, so re-categorising a library entry cannot silently recolour history.
     */
    val categorySnapshot: ExerciseCategory? = null,
)

/**
 * A day's training container. Created or reused behind the scenes when the first set of the day is
 * confirmed; the user never starts or ends one. Several modules trained together share one
 * session, so a session is not a count of modules. [ordinal] leaves room for the deliberate split
 * and merge controls of a later phase.
 */
@Entity(
    tableName = "training_sessions",
    indices = [Index(value = ["trainingDateEpochDay", "ordinal"], unique = true)],
)
data class TrainingSessionEntity(
    @PrimaryKey val id: String,
    val trainingDateEpochDay: Long,
    val ordinal: Int,
    val createdAtEpochMs: Long,
)

/**
 * A set that was actually performed.
 *
 * Identity is [id] alone: it never depends on the set number shown on screen, so reordering,
 * inserting or deleting a set cannot rewrite which record a correction lands on. [prescriptionId]
 * is an optional reference — an unplanned set is perfectly valid — and the payload is independent
 * of whatever was planned, so four planned sets and six performed sets need no special case.
 */
@Entity(
    tableName = "actual_sets",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseOccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TrainingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PrescriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("occurrenceId"),
        Index("sessionId"),
        Index("prescriptionId"),
        Index("exerciseId"),
        Index("trainingDateEpochDay"),
    ],
)
data class ActualSetEntity(
    @PrimaryKey val id: String,
    val occurrenceId: String,
    val sessionId: String,
    /** Stable lineage, so history survives a renamed library entry. */
    val exerciseId: String,
    /** The local training date this set is filed under, persisted independently of [recordedAtEpochMs]. */
    val trainingDateEpochDay: Long,
    /** What this set was performed against, when it was planned at all. */
    val prescriptionId: String?,
    val orderIndex: Int,
    val side: BodySide?,
    val payloadVersion: Int,
    val payloadJson: String,
    val recordedAtEpochMs: Long,
)
