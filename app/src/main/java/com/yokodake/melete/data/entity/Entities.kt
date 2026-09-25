package com.yokodake.melete.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yokodake.melete.data.model.EffortLevel
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
    /**
     * What the movement is and how to do it. Reference material, not part of the training record:
     * it is read live rather than snapshotted, so correcting an explanation corrects it
     * everywhere instead of leaving old copies saying something the user no longer believes.
     */
    val description: String? = null,
    /** Optional training-purpose grouping. Absent is a valid answer. */
    val category: ExerciseCategory? = null,
    /**
     * When the user retired this exercise from the library, or null while it is still in use.
     *
     * A tombstone rather than a delete, because the row is the anchor for everything that refers
     * to it: scheduled copies keep working, logged sets keep grouping under the same stable id,
     * and "previous results" still finds them. Removing the definition removes it from the
     * *library*, which is a statement about what you plan to do next, not about what you did.
     */
    val deletedAtEpochMs: Long? = null,
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
    /**
     * The category as it stood when this copy was placed in the week. Snapshotted like the other
     * identity fields, so re-categorising a library entry cannot silently recolour history.
     */
    val categorySnapshot: ExerciseCategory? = null,
    /**
     * How long this exercise took, once it has been logged. Seconds, rests included.
     *
     * On the occurrence rather than on a set, because a duration-only activity has no sets and a
     * circuit's share of the clock belongs to the exercise rather than to any one of its rounds.
     * Absent means the time is unknown, which is a different thing from zero and is never
     * backfilled with a guess.
     */
    val loggedDurationSeconds: Int? = null,
    /**
     * Whether [loggedDurationSeconds] is a number the user typed, or one the app worked out.
     *
     * Kept so that changing a default or a formula later can leave the record alone: an inferred
     * value that has been saved is still what this workout says it took, and recomputing history
     * behind the user's back would make the diary untrustworthy.
     */
    val loggedDurationManual: Boolean = false,
    /**
     * How hard it was, for work that records no sets.
     *
     * An ordinary exercise rates its effort on its sets, which is where it belongs. A duration-only
     * activity has none, so the rating has nowhere else to live; it is read only when there are no
     * sets, so the two can never disagree about the same workout.
     */
    val loggedEffort: EffortLevel? = null,
    /**
     * True for an activity typed in by name rather than picked from the library.
     *
     * A one-off is a real occurrence with a real name and a stable [exerciseId] derived from that
     * name, so two runs called the same thing already group together and could be promoted to a
     * library entry later. What it does not have is a row in `exercises`, which is exactly the
     * point: going for a run once should leave no clutter behind.
     */
    val isOneOff: Boolean = false,
    /** The scheduled circuit this occurrence is a station of, when it is one. */
    val circuitInstanceId: String? = null,
    /** Position within that circuit, so the order survives independently of the day's ordering. */
    val circuitPosition: Int? = null,
    /**
     * The library variation this copy was cut from, when it was one. Lineage only, like
     * [exerciseId]: the variation may be renamed or deleted and this copy stays what it was.
     */
    val variationId: String? = null,
    /** The variation's tag as it stood when scheduled, so the chip survives the variation going. */
    val variationTagSnapshot: String? = null,
    /** The scheduled module this belongs to, when it was placed as part of one. */
    val moduleInstanceId: String? = null,
    /** Position within that module, among its exercises and circuits alike. */
    val modulePosition: Int? = null,
)

/**
 * A saved, named routine: an ordered set of exercises executed as a superset or circuit.
 *
 * A template, like an exercise's default prescription: scheduling copies it by value, so editing
 * the routine cannot reach work already placed in a week. A routine is an *execution* pattern —
 * how the work is performed — which is a different thing from the organisational grouping a
 * module will be.
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Times round. Every entry shares it: a circuit has one volume number, not one per station. */
    val rounds: Int,
    /** Rest between two exercises. Replaces each station's own set rest during execution. */
    val transitionSeconds: Int,
    /** Rest after the last exercise of a round. Replaces the transition rest there. */
    val roundRestSeconds: Int,
    /** Bumped on every structural edit, so a scheduled copy can say which version it was cut from. */
    val structureVersion: Int,
    val createdAtEpochMs: Long,
    /** Retired from the library of routines, while staying the anchor for scheduled copies. */
    val deletedAtEpochMs: Long? = null,
)

/**
 * One station of a routine, with its own prescription copy.
 *
 * The prescription is a copy rather than a pointer at the library default, so editing a circuit's
 * hang does not change the standalone hang, another circuit's hang, or anything already scheduled.
 */
@Entity(
    tableName = "routine_entries",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PrescriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("routineId"), Index("prescriptionId"), Index("exerciseId")],
)
data class RoutineEntryEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val orderIndex: Int,
    /** Lineage into the library, deliberately without a foreign key. */
    val exerciseId: String,
    /** So a retired library entry still leaves the routine readable. */
    val exerciseNameSnapshot: String,
    val prescriptionId: String?,
)

/**
 * A routine copied into a week: the container its exercise occurrences hang from.
 *
 * Deliberately *not* an occurrence. A circuit contributes nothing to the completed-workout count —
 * its stations are the work, and counting the container as well would count the same training
 * twice. What it holds is the execution shape and a snapshot of the structure it was cut from, so
 * a log can still be read correctly after the routine has been edited or deleted.
 */
@Entity(
    tableName = "circuit_instances",
    indices = [Index("weekStartEpochDay"), Index("trainingDateEpochDay"), Index("routineId")],
)
data class CircuitInstanceEntity(
    @PrimaryKey val id: String,
    /** Stable routine identity, for later "how has this circuit gone" questions. */
    val routineId: String,
    val routineNameSnapshot: String,
    /** Which version of the routine this copy was cut from. */
    val structureVersion: Int,
    /** The routine's shape as it stood when scheduled, so the log stays interpretable. */
    val structureSnapshotJson: String,
    val weekStartEpochDay: Long,
    val trainingDateEpochDay: Long?,
    val orderIndex: Int,
    val rounds: Int,
    val transitionSeconds: Int,
    val roundRestSeconds: Int,
    val createdAtEpochMs: Long,
    /** The scheduled module this circuit belongs to, when it was placed as part of one. */
    val moduleInstanceId: String? = null,
    /** Position within that module, among its exercises and circuits alike. */
    val modulePosition: Int? = null,
)

/**
 * A named alternative plan for one library exercise: `PWR`, `END`, `A`.
 *
 * Not a second exercise. It is the same movement planned another way, so every copy of it keeps
 * the exercise's identity and history groups across variations. What a variation adds is its own
 * prescription and its own notes — the part of "how" that changes between phases.
 */
@Entity(
    tableName = "exercise_variations",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PrescriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        // One exercise cannot have two variations answering to the same chip.
        Index(value = ["exerciseId", "tag"], unique = true),
        Index("prescriptionId"),
    ],
)
data class ExerciseVariationEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    /** Capitals and digits, four at most. See [com.yokodake.melete.data.model.VariationTag]. */
    val tag: String,
    /** How this variation is done, when that differs: loads, tempo, a sequence within the set. */
    val notes: String?,
    val prescriptionId: String?,
    val orderIndex: Int,
    val createdAtEpochMs: Long,
)

/**
 * A reusable named group of planned work: "Fingers + mobility", "Taper day".
 *
 * An *organisational* group, where a routine is an *execution* pattern. A module is not trained
 * and not timed; its contents are. It never counts as a workout or adds time of its own.
 */
@Entity(tableName = "modules")
data class ModuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** What it is for, in the user's own words. Optional. */
    val description: String? = null,
    val createdAtEpochMs: Long,
    /** Retired from the list of modules, while staying the anchor for scheduled copies. */
    val deletedAtEpochMs: Long? = null,
)

/**
 * One entry of a module template: an exercise with its own prescription copy, or a saved circuit.
 *
 * Exactly one of [exerciseId] and [routineId] is set. A circuit is referenced rather than copied,
 * because it is copied into the week when the module is — by the same code that schedules a
 * circuit on its own. Groups do not nest: a module entry is never another module.
 */
@Entity(
    tableName = "module_entries",
    foreignKeys = [
        ForeignKey(
            entity = ModuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["moduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PrescriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("moduleId"), Index("prescriptionId"), Index("exerciseId"), Index("routineId")],
)
data class ModuleEntryEntity(
    @PrimaryKey val id: String,
    val moduleId: String,
    val orderIndex: Int,
    /** Lineage into the library, deliberately without a foreign key. */
    val exerciseId: String? = null,
    val exerciseNameSnapshot: String? = null,
    /** The variation this entry was cut from, when it was one. */
    val variationId: String? = null,
    val variationTagSnapshot: String? = null,
    /** This entry's own copy of the plan. Editing it reaches neither the library nor the week. */
    val prescriptionId: String? = null,
    val routineId: String? = null,
    val routineNameSnapshot: String? = null,
)

/**
 * A module copied into a week: the named group its occurrences and circuits hang from.
 *
 * Like a circuit container, deliberately not an occurrence, and it counts nothing. Ungrouping
 * deletes this row and leaves every member exactly where it was.
 */
@Entity(
    tableName = "module_instances",
    indices = [Index("weekStartEpochDay"), Index("trainingDateEpochDay"), Index("moduleId")],
)
data class ModuleInstanceEntity(
    @PrimaryKey val id: String,
    /** Stable template identity, kept for later analysis even after the template is edited. */
    val moduleId: String,
    val moduleNameSnapshot: String,
    val weekStartEpochDay: Long,
    val trainingDateEpochDay: Long?,
    val orderIndex: Int,
    val createdAtEpochMs: Long,
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
