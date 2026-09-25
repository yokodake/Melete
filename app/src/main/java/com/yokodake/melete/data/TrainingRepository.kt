package com.yokodake.melete.data

import androidx.room.withTransaction
import com.yokodake.melete.core.OneOffActivity
import com.yokodake.melete.core.Planning
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.dao.ExerciseWithDefaultPrescription
import com.yokodake.melete.data.dao.OccurrenceWithPrescription
import com.yokodake.melete.data.dao.ModuleWithEntries
import com.yokodake.melete.data.dao.SetPayloadRow
import com.yokodake.melete.data.dao.VariationWithPrescription
import com.yokodake.melete.data.entity.ActualSetEntity
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.ExerciseVariationEntity
import com.yokodake.melete.data.entity.ModuleEntity
import com.yokodake.melete.data.entity.ModuleEntryEntity
import com.yokodake.melete.data.entity.ModuleInstanceEntity
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.entity.PrescriptionEntity
import com.yokodake.melete.data.entity.TrainingSessionEntity
import com.yokodake.melete.data.dao.RoutineWithEntries
import com.yokodake.melete.data.entity.CircuitInstanceEntity
import com.yokodake.melete.data.entity.RoutineEntity
import com.yokodake.melete.data.entity.RoutineEntryEntity
import com.yokodake.melete.data.model.ACTUAL_SET_PAYLOAD_VERSION
import com.yokodake.melete.data.model.ActualSetJson
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.CIRCUIT_SNAPSHOT_VERSION
import com.yokodake.melete.data.model.CircuitEntrySnapshot
import com.yokodake.melete.data.model.CircuitSnapshotJson
import com.yokodake.melete.data.model.CircuitStructureSnapshot
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PRESCRIPTION_PAYLOAD_VERSION
import com.yokodake.melete.data.model.PrescriptionJson
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.data.model.VariationTag
import com.yokodake.melete.data.timer.DurationEstimate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * A planned exercise as the rest of the app sees it: the snapshot that was placed in the week,
 * not a live view of the library entry it came from.
 */
data class PlannedOccurrence(
    val id: String,
    val exerciseId: String,
    val name: String,
    val mode: ExerciseMode,
    val unilateral: Boolean,
    val measurementUnit: String?,
    val measurementMeaning: MeasurementMeaning?,
    /** The category as snapshotted when this copy was placed in the week. */
    val category: ExerciseCategory?,
    val trainingDate: LocalDate?,
    val weekStart: LocalDate,
    val prescriptionId: String?,
    val prescription: PrescriptionPayload?,
    /** True when a prescription row exists but its payload could not be read. */
    val prescriptionUnreadable: Boolean,
    /**
     * The date the work was actually filed under, when any has been logged. Deliberately separate
     * from [trainingDate], which is where the *plan* put it: the two are allowed to disagree.
     */
    /** The heaviest set logged against this placement, when anything was. */
    val maxLoad: Double? = null,
    /**
     * How many sets are recorded against it.
     *
     * Carried so that reopening a log can show *which* sets happened rather than assuming all of
     * them did: two rounds of a three-round circuit is a real session, and the review has to come
     * back saying so.
     */
    val loggedSets: Int = 0,
    val state: OccurrenceState,
    val comment: String?,
    val orderIndex: Int,
    /** How long it took, once logged. Absent means unknown, never zero. */
    val loggedDurationSeconds: Int? = null,
    /** Whether that number was typed or worked out. */
    val loggedDurationManual: Boolean = false,
    /** How hard it was, for work that records no sets at all. */
    val loggedEffort: EffortLevel? = null,
    /** An activity typed in by name rather than picked from the library. */
    val isOneOff: Boolean = false,
    /** The scheduled circuit this is a station of, when it is one. */
    val circuitInstanceId: String? = null,
    val circuitPosition: Int? = null,
    /** The library variation this copy was cut from, and its tag as it stood then. */
    val variationId: String? = null,
    val variationTag: String? = null,
    /** The scheduled module this belongs to, when it was placed as part of one. */
    val moduleInstanceId: String? = null,
    val modulePosition: Int? = null,
) {
    /**
     * How long the plan says this should take, worked out from its shape. Null when there is
     * nothing to go on — a missing estimate is an answer, not a zero.
     */
    val estimatedDurationSeconds: Int?
        get() = prescription?.plannedDurationSeconds
            ?: DurationEstimate.forPrescription(mode, unilateral, prescription)

    /**
     * The duration to show, and whether it is a real record.
     *
     * A logged number wins; otherwise the estimate stands in, greyed, as a suggestion. The two are
     * never confused, because [loggedDurationSeconds] being absent is what says so.
     */
    val displayDurationSeconds: Int? get() = loggedDurationSeconds ?: estimatedDurationSeconds

    /**
     * Whether this occurrence is evidence of training, not just a plan.
     *
     * Logged sets are the usual answer, but a duration-only activity records none and is still a
     * workout that happened, so completion counts too. Everything that protects history —
     * deletion, unscheduling, retiring a definition — asks this rather than counting sets.
     */
    val hasRecord: Boolean get() = state == OccurrenceState.COMPLETED
}

/** A library entry and its current default prescription. */
data class LibraryExercise(
    val id: String,
    val name: String,
    val mode: ExerciseMode,
    val unilateral: Boolean,
    val measurementUnit: String?,
    val measurementMeaning: MeasurementMeaning?,
    val notes: String?,
    val description: String?,
    val category: ExerciseCategory?,
    val defaultPrescription: PrescriptionPayload?,
    /** Retired from the library, but still the anchor for everything that refers to it. */
    val deletedAtEpochMs: Long? = null,
    /** Its named alternative plans, in order. Empty for most exercises. */
    val variations: List<ExerciseVariation> = emptyList(),
)

/**
 * A named alternative plan for one library exercise: its own prescription and its own notes, under
 * a short tag. The same movement planned another way, never a second exercise.
 */
data class ExerciseVariation(
    val id: String,
    val exerciseId: String,
    val tag: String,
    val notes: String?,
    val prescription: PrescriptionPayload?,
    val orderIndex: Int,
)

/** What saving a variation came to. The tag rules are the only way it can be refused. */
enum class VariationSave { SAVED, TAG_INVALID, TAG_TAKEN }

/**
 * What removing an exercise from the library would actually do.
 *
 * The three cases are genuinely different promises, so the screen must say which one it is making
 * rather than offer a single "remove" that means whichever of them happens to apply.
 */
data class ExerciseRemoval(
    val exercise: LibraryExercise,
    /** Planned copies pointing at it, trained or not. */
    val plannedCopies: Int,
    /** Sets ever logged against it, under any copy. */
    val loggedSets: Int,
    /**
     * Copies of it that were marked done. A duration-only activity writes no sets, so this is the
     * only evidence a year of climbing sessions leaves behind.
     */
    val completedCopies: Int = 0,
) {
    enum class Kind {
        /** Nothing refers to it: created by mistake, never used. */
        UNUSED,

        /** Planned, never performed. The plans are garbage too, and go with it. */
        PLANNED_NEVER_LOGGED,

        /** Trained at least once, so the row stays as the anchor for that history. */
        LOGGED,
    }

    val kind: Kind
        get() = when {
            loggedSets > 0 || completedCopies > 0 -> Kind.LOGGED
            plannedCopies > 0 -> Kind.PLANNED_NEVER_LOGGED
            else -> Kind.UNUSED
        }

    /** What actually happened, reported back after the fact. */
    enum class Outcome { DELETED, DELETED_WITH_PLANS, RETIRED }
}

/** One set about to be written: what it says, and which side it was done on. */
data class SetWrite(val payload: ActualSetPayload, val side: BodySide?)

/** Everything the exercise editor writes. Identity and default prescription travel together. */
data class ExerciseDraft(
    val name: String,
    val mode: ExerciseMode,
    val unilateral: Boolean,
    val measurementUnit: String?,
    val measurementMeaning: MeasurementMeaning?,
    val notes: String?,
    val description: String?,
    val category: ExerciseCategory?,
    val defaultPrescription: PrescriptionPayload,
)


/** One planned exercise inside a routine, as the editor holds it. */
data class RoutineEntryDraft(
    val exerciseId: String,
    val prescription: PrescriptionPayload?,
)

/** Everything the routine editor writes. */
data class RoutineDraft(
    val name: String,
    val rounds: Int,
    val transitionSeconds: Int,
    val roundRestSeconds: Int,
    val entries: List<RoutineEntryDraft>,
    val category: ExerciseCategory? = null,
)

/** One station of a routine, joined to what the library currently says about the exercise. */
data class RoutineEntryView(
    val id: String,
    val exerciseId: String,
    val name: String,
    val mode: ExerciseMode,
    val unilateral: Boolean,
    val measurementUnit: String?,
    val measurementMeaning: MeasurementMeaning?,
    val category: ExerciseCategory?,
    /** This station's own copy. Editing it never reaches the library default. */
    val prescription: PrescriptionPayload?,
    val orderIndex: Int,
    /** The library entry it came from is gone, so only the snapshotted name is left. */
    val definitionMissing: Boolean,
)

/** A saved routine: a named circuit or superset, ready to be copied into a week. */
data class Routine(
    val id: String,
    val name: String,
    val rounds: Int,
    val transitionSeconds: Int,
    val roundRestSeconds: Int,
    val structureVersion: Int,
    val entries: List<RoutineEntryView>,
    val category: ExerciseCategory? = null,
)

/** A routine copied into a week, together with the occurrences that are its stations. */
data class ScheduledCircuit(
    val id: String,
    val routineId: String,
    val name: String,
    val rounds: Int,
    val transitionSeconds: Int,
    val roundRestSeconds: Int,
    val weekStart: LocalDate,
    val trainingDate: LocalDate?,
    val orderIndex: Int,
    val stations: List<PlannedOccurrence>,
    val category: ExerciseCategory? = null,
) {
    /** A circuit is done when every station of it is. It counts nothing of its own. */
    val completed: Boolean get() = stations.isNotEmpty() && stations.all { it.hasRecord }

    val anyRecorded: Boolean get() = stations.any { it.hasRecord }
}

/** A scheduled circuit as the planner sees it: the card, without its stations. */
data class WeekCircuit(
    val id: String,
    val routineId: String,
    val name: String,
    val rounds: Int,
    val transitionSeconds: Int,
    val roundRestSeconds: Int,
    val weekStart: LocalDate,
    val trainingDate: LocalDate?,
    val orderIndex: Int,
    /** The scheduled module this circuit belongs to, when it was placed as part of one. */
    val moduleInstanceId: String? = null,
    val modulePosition: Int? = null,
    val category: ExerciseCategory? = null,
)

/** What removing a routine would cost. */
data class RoutineRemoval(val routine: Routine, val scheduledCopies: Int, val recordedCopies: Int)

/** One entry of a module as the editor holds it: an exercise with its plan, or a circuit. */
data class ModuleEntryDraft(
    val exerciseId: String? = null,
    val variationId: String? = null,
    val prescription: PrescriptionPayload? = null,
    val routineId: String? = null,
)

/** Everything the module editor writes. */
data class ModuleDraft(
    val name: String,
    val description: String?,
    val entries: List<ModuleEntryDraft>,
)

/** One entry of a saved module, joined to what the library currently says about it. */
data class ModuleEntryView(
    val id: String,
    val exerciseId: String?,
    val variationId: String?,
    val variationTag: String?,
    val name: String,
    val mode: ExerciseMode,
    val unilateral: Boolean,
    val category: ExerciseCategory?,
    /** This entry's own copy of the plan. Editing it never reaches the library. */
    val prescription: PrescriptionPayload?,
    val routineId: String?,
    /** The saved circuit, when this entry is one and it still exists. */
    val routine: Routine?,
    /** The exercise or circuit it came from is gone, so only the snapshotted name is left. */
    val definitionMissing: Boolean,
) {
    val isCircuit: Boolean get() = routineId != null
}

/** A saved module: a named group of planned work, ready to be copied into a week. */
data class TrainingModule(
    val id: String,
    val name: String,
    val description: String?,
    val entries: List<ModuleEntryView>,
) {
    /** Entries whose exercise or circuit is gone or retired, and which scheduling will leave out. */
    val unavailableEntries: List<ModuleEntryView> get() = entries.filter { it.definitionMissing }
}

/** A module copied into a week, as the planner sees it: the group, without its members. */
data class WeekModule(
    val id: String,
    val moduleId: String,
    val name: String,
    val weekStart: LocalDate,
    val trainingDate: LocalDate?,
    val orderIndex: Int,
)

/** What removing a module template would cost. Scheduled copies are never touched either way. */
data class ModuleRemoval(val module: TrainingModule, val scheduledCopies: Int)

/**
 * One occurrence's whole log, about to be written.
 *
 * Everything a workout has to say travels together — its sets, its note, its duration and whether
 * it happened — because logging is one act. A list of these is what makes a circuit's review a
 * single atomic save rather than one transaction per exercise, half of which could land.
 */
data class OccurrenceLogWrite(
    val occurrenceId: String,
    val completed: Boolean,
    val sets: List<SetWrite> = emptyList(),
    val comment: String? = null,
    /** Read only where there are no sets to carry it. */
    val effort: EffortLevel? = null,
    val durationSeconds: Int? = null,
    val durationManual: Boolean = false,
)

/** One performed set. [id] is the identity; the set number on screen is only a position. */
data class PerformedSet(
    val id: String,
    val occurrenceId: String,
    val exerciseId: String,
    val trainingDate: LocalDate,
    val orderIndex: Int,
    val side: BodySide?,
    val payload: ActualSetPayload,
    /** Null when the set was performed without a plan. */
    val prescriptionId: String?,
    val recordedAtEpochMs: Long,
)

/** Sets performed for one exercise on one earlier training date. */
data class PreviousResult(
    val trainingDate: LocalDate,
    val sets: List<PerformedSet>,
)

data class OccurrenceDetail(
    val occurrence: PlannedOccurrence,
    val sets: List<PerformedSet>,
)

class TrainingRepository(private val database: MeleteDatabase) {

    private val dao = database.trainingDao()
    private val library = database.libraryDao()
    private val logging = database.loggingDao()
    private val routines = database.routineDao()
    private val variations = database.variationDao()
    private val modules = database.moduleDao()

    // ---------------------------------------------------------------- week

    /**
      * The planner's view of a week.
      *
      * A placement carries one date, and its logged sets are filed under that same date. The two
      * cannot drift apart, because moving the placement moves the sets with it — see
      * [moveOccurrence]. What a card says is therefore what happened, once anything has happened.
      */
    fun observeWeek(weekStart: LocalDate): Flow<List<PlannedOccurrence>> = combine(
        dao.observeWeek(weekStart.toEpochDay()),
        logging.observeSetPayloadsInWeek(weekStart.toEpochDay()),
    ) { rows, payloads ->
        // The heaviest set of each workout, so a card that says it is done can say what it took.
        val heaviest = payloads.heaviestByOccurrence()
        val counts = payloads.groupingBy { it.occurrenceId }.eachCount()

        rows.map {
            it.toPlanned(
                maxLoad = heaviest[it.occurrence.id],
                loggedSets = counts[it.occurrence.id] ?: 0,
            )
        }
    }

    /** Every placement of one exercise, including those whose definition has been retired. */
    fun observeOccurrencesOf(exerciseId: String): Flow<List<PlannedOccurrence>> =
        dao.observeOccurrencesOf(exerciseId).map { rows -> rows.map { it.toPlanned() } }

    fun observeOccurrence(occurrenceId: String): Flow<OccurrenceDetail?> =
        combine(
            dao.observeOccurrence(occurrenceId),
            logging.observeSetsForOccurrence(occurrenceId),
        ) { row, sets ->
            row?.let { OccurrenceDetail(it.toPlanned(), sets.map(ActualSetEntity::toPerformed)) }
        }

    /**
     * Previous results for the same stable exercise, excluding this occurrence, most recent
     * training date first. These are actuals and are shown separately from the prescription.
     */
    fun observePreviousResults(
        exerciseId: String,
        excludeOccurrenceId: String,
        maxSessions: Int = 3,
    ): Flow<List<PreviousResult>> =
        logging.observeRecentSets(exerciseId, excludeOccurrenceId).map { rows ->
            rows.map(ActualSetEntity::toPerformed)
                .groupBy { it.trainingDate }
                .entries
                .sortedByDescending { it.key }
                .take(maxSessions)
                .map { (date, sets) -> PreviousResult(date, sets.sortedBy { it.orderIndex }) }
        }

    // ------------------------------------------------------------- library

    fun observeLibrary(): Flow<List<LibraryExercise>> =
        combine(library.observeExercises(), variations.observeAllVariations()) { rows, all ->
            val byExercise = all.groupBy { it.variation.exerciseId }
            rows.map { it.toLibraryExercise(byExercise[it.exercise.id].orEmpty()) }
        }

    suspend fun getLibraryExercise(id: String): LibraryExercise? =
        library.getExerciseWithDefault(id)
            ?.toLibraryExercise(variations.variationsWithPrescriptionOf(id))

    /**
     * The library entry a planned copy came from, followed live. The explanation of a movement is
     * reference material rather than part of the record, so it is read from the library instead
     * of from the snapshot: an exercise that has since been deleted simply has none.
     */
    fun observeLibraryExercise(id: String): Flow<LibraryExercise?> =
        combine(
            library.observeExerciseWithDefault(id),
            variations.observeVariationsOf(id),
        ) { row, rows -> row?.toLibraryExercise(rows) }

    suspend fun createExercise(draft: ExerciseDraft): String = database.withTransaction {
        val now = System.currentTimeMillis()
        val prescription = newPrescriptionRow(draft.defaultPrescription, now)
        val exercise = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = draft.name.trim(),
            mode = draft.mode,
            measurementUnit = draft.measurementUnit,
            measurementMeaning = draft.measurementMeaning,
            unilateral = draft.unilateral,
            notes = draft.notes?.takeIf { it.isNotBlank() },
            description = draft.description?.takeIf { it.isNotBlank() },
            category = draft.category,
            defaultPrescriptionId = prescription.id,
            createdAtEpochMs = now,
        )
        library.insertPrescription(prescription)
        library.insertExercise(exercise)
        exercise.id
    }

    /**
     * Updates the library entry. The default prescription is written as a *new* row rather than
     * mutated, so copies already placed in a week keep pointing at what they were given.
     */
    suspend fun updateExercise(id: String, draft: ExerciseDraft) {
        database.withTransaction {
            val existing = library.getExerciseWithDefault(id)?.exercise ?: return@withTransaction
            val now = System.currentTimeMillis()
            val prescription = newPrescriptionRow(draft.defaultPrescription, now)
            library.insertPrescription(prescription)
            library.updateExercise(
                existing.copy(
                    name = draft.name.trim(),
                    mode = draft.mode,
                    measurementUnit = draft.measurementUnit,
                    measurementMeaning = draft.measurementMeaning,
                    unilateral = draft.unilateral,
                    notes = draft.notes?.takeIf { it.isNotBlank() },
                    description = draft.description?.takeIf { it.isNotBlank() },
                    category = draft.category,
                    defaultPrescriptionId = prescription.id,
                )
            )
        }
    }

    /**
     * Replaces the library default with a new prescription row and repoints the exercise at it.
     * Copies already placed in a week keep pointing at what they were given, because nothing is
     * mutated in place.
     */
    suspend fun updateDefaultPrescription(exerciseId: String, payload: PrescriptionPayload) {
        database.withTransaction {
            val existing = library.getExerciseWithDefault(exerciseId)?.exercise
                ?: return@withTransaction
            val row = newPrescriptionRow(payload, System.currentTimeMillis())
            library.insertPrescription(row)
            library.updateExercise(existing.copy(defaultPrescriptionId = row.id))
        }
    }

    /**
     * What removing this exercise would cost, counted before anything is asked.
     *
     * Returns null for an id the library does not know, which is the only case where there is
     * nothing to offer.
     */
    suspend fun removalImpactOf(exerciseId: String): ExerciseRemoval? =
        database.withTransaction {
            val exercise = library.getExerciseWithDefault(exerciseId)?.toLibraryExercise()
                ?: return@withTransaction null
            ExerciseRemoval(
                exercise = exercise,
                plannedCopies = library.countOccurrencesOf(exerciseId),
                loggedSets = library.countSetsOf(exerciseId),
                completedCopies = library.countCompletedOccurrencesOf(exerciseId),
            )
        }

    /**
     * Removes an exercise, as completely as its history allows.
     *
     * A row that nothing refers to is deleted outright, along with the planned copies that were
     * never trained: a mistake should leave nothing behind. Once a single set has been logged
     * against it the row becomes a tombstone instead, because it anchors that set's lineage and
     * the grouping that makes "previous results" work. The rule is the user's: keep the fact that
     * it was done, never the fact that it was merely planned.
     *
     * Re-counted inside the transaction rather than trusting the [ExerciseRemoval] the dialog was
     * drawn from, so a set logged between asking and confirming still protects itself.
     */
    suspend fun removeExercise(exerciseId: String): ExerciseRemoval.Outcome =
        database.withTransaction {
            // Either kind of evidence outranks a plan: a logged set, or a completed copy that
            // never had sets to log because it was an activity.
            if (library.countSetsOf(exerciseId) > 0 ||
                library.countCompletedOccurrencesOf(exerciseId) > 0
            ) {
                library.markExerciseDeleted(exerciseId, System.currentTimeMillis())
                return@withTransaction ExerciseRemoval.Outcome.RETIRED
            }
            val plannedCopies = library.countOccurrencesOf(exerciseId)
            val prescriptions = buildList {
                addAll(library.occurrencePrescriptionIdsOf(exerciseId))
                // The variations go with the row, by cascade; the plans they held are freed here.
                addAll(variations.variationsOf(exerciseId).mapNotNull { it.prescriptionId })
                library.getExerciseWithDefault(exerciseId)?.exercise?.defaultPrescriptionId
                    ?.let { add(it) }
            }
            // Occurrences first: their prescription copies are held by a RESTRICT foreign key.
            library.deleteOccurrencesOf(exerciseId)
            library.deleteExercise(exerciseId)
            prescriptions.distinct().forEach { library.deletePrescriptionIfUnused(it) }
            if (plannedCopies > 0) {
                ExerciseRemoval.Outcome.DELETED_WITH_PLANS
            } else {
                ExerciseRemoval.Outcome.DELETED
            }
        }

    suspend fun restoreExercise(exerciseId: String) = library.restoreExercise(exerciseId)

    // ---------------------------------------------------------- variations

    /**
     * Adds a named alternative plan to a library exercise.
     *
     * Refused, without writing anything, for a tag that breaks the rules or that another variation
     * of the same exercise already uses: two plans under one chip could not be told apart.
     */
    suspend fun createVariation(
        exerciseId: String,
        tag: String,
        notes: String?,
        prescription: PrescriptionPayload,
    ): VariationSave = database.withTransaction {
        if (!VariationTag.isValid(tag)) return@withTransaction VariationSave.TAG_INVALID
        if (variations.countTag(exerciseId, tag, excludeId = "") > 0) {
            return@withTransaction VariationSave.TAG_TAKEN
        }
        val now = System.currentTimeMillis()
        val row = newPrescriptionRow(prescription, now)
        library.insertPrescription(row)
        variations.insertVariation(
            ExerciseVariationEntity(
                id = UUID.randomUUID().toString(),
                exerciseId = exerciseId,
                tag = tag,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                prescriptionId = row.id,
                orderIndex = variations.nextOrderIndex(exerciseId),
                createdAtEpochMs = now,
            )
        )
        VariationSave.SAVED
    }

    /**
     * Changes a variation. Its plan is written as a new row, like an exercise default, so copies
     * already in a week keep what they were given; the tag they show is their own snapshot.
     */
    suspend fun updateVariation(
        variationId: String,
        tag: String,
        notes: String?,
        prescription: PrescriptionPayload,
    ): VariationSave = database.withTransaction {
        val existing = variations.getVariation(variationId)?.variation
            ?: return@withTransaction VariationSave.SAVED
        if (!VariationTag.isValid(tag)) return@withTransaction VariationSave.TAG_INVALID
        if (variations.countTag(existing.exerciseId, tag, excludeId = variationId) > 0) {
            return@withTransaction VariationSave.TAG_TAKEN
        }
        val row = newPrescriptionRow(prescription, System.currentTimeMillis())
        library.insertPrescription(row)
        variations.updateVariation(
            existing.copy(
                tag = tag,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                prescriptionId = row.id,
            )
        )
        existing.prescriptionId?.let { library.deletePrescriptionIfUnused(it) }
        VariationSave.SAVED
    }

    /**
     * Deletes a variation. Nothing already planned or logged changes: every copy holds its own plan
     * and its own tag, and only remembers which variation it came from.
     */
    suspend fun deleteVariation(variationId: String) {
        database.withTransaction {
            val existing = variations.getVariation(variationId)?.variation
                ?: return@withTransaction
            variations.deleteVariation(variationId)
            existing.prescriptionId?.let { library.deletePrescriptionIfUnused(it) }
        }
    }

    // ---------------------------------------------------------- scheduling

    /**
     * Copies a library exercise into the week: onto [trainingDate], or into the week's unscheduled
     * area when it is null. The prescription is copied by value and the exercise's identity fields
     * are snapshotted, so later template edits cannot reach this copy.
     *
     * With a [variationId] the copy is cut from that variation's plan instead of the default, and
     * carries its tag.
     */
    suspend fun scheduleExercise(
        exerciseId: String,
        weekStart: LocalDate,
        trainingDate: LocalDate?,
        variationId: String? = null,
    ): String = database.withTransaction {
        val source = library.getExerciseWithDefault(exerciseId)
            ?: error("Unknown exercise $exerciseId")
        val variation = variationId
            ?.let { variations.getVariation(it) }
            ?.takeIf { it.variation.exerciseId == exerciseId }
        placeExercise(
            exercise = source.exercise,
            // A variation with no plan is copied as having none, never as the default.
            plan = if (variation != null) variation.prescription else source.defaultPrescription,
            variationId = variation?.variation?.id,
            variationTag = variation?.variation?.tag,
            weekStart = weekStart,
            trainingDate = trainingDate,
            orderIndex = dao.nextOrderIndex(weekStart.toEpochDay(), trainingDate?.toEpochDay()),
        )
    }

    /**
     * Writes one occurrence cut from [exercise] and a copy of [plan]. The single place a library
     * exercise becomes planned work, whether it arrives alone or as part of a module.
     */
    private suspend fun placeExercise(
        exercise: ExerciseEntity,
        plan: PrescriptionEntity?,
        variationId: String?,
        variationTag: String?,
        weekStart: LocalDate,
        trainingDate: LocalDate?,
        orderIndex: Int,
        moduleInstanceId: String? = null,
        modulePosition: Int? = null,
    ): String {
        val now = System.currentTimeMillis()
        val copy = plan?.let {
            PrescriptionEntity(
                id = UUID.randomUUID().toString(),
                payloadVersion = it.payloadVersion,
                payloadJson = it.payloadJson,
                createdAtEpochMs = now,
            )
        }
        copy?.let { library.insertPrescription(it) }
        val occurrence = ExerciseOccurrenceEntity(
            id = UUID.randomUUID().toString(),
            weekStartEpochDay = weekStart.toEpochDay(),
            trainingDateEpochDay = trainingDate?.toEpochDay(),
            exerciseId = exercise.id,
            exerciseNameSnapshot = exercise.name,
            modeSnapshot = exercise.mode,
            unilateralSnapshot = exercise.unilateral,
            measurementUnitSnapshot = exercise.measurementUnit,
            measurementMeaningSnapshot = exercise.measurementMeaning,
            categorySnapshot = exercise.category,
            prescriptionId = copy?.id,
            orderIndex = orderIndex,
            state = OccurrenceState.PLANNED,
            comment = null,
            createdAtEpochMs = now,
            variationId = variationId,
            variationTagSnapshot = variationTag,
            moduleInstanceId = moduleInstanceId,
            modulePosition = modulePosition,
        )
        dao.insertOccurrences(listOf(occurrence))
        return occurrence.id
    }

    /** Edits this week's copy only. Writes a new prescription row; nothing else is touched. */
    suspend fun updateOccurrencePrescription(occurrenceId: String, payload: PrescriptionPayload) {
        database.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
            val row = newPrescriptionRow(payload, System.currentTimeMillis())
            library.insertPrescription(row)
            dao.updateOccurrence(occurrence.copy(prescriptionId = row.id))
        }
    }

    /**
     * Files an occurrence under a training date, or back into the week's unscheduled area. Already
     * recorded sets keep their own training date: correcting those is a separate, explicit action.
     */
    suspend fun assignOccurrenceDate(occurrenceId: String, trainingDate: LocalDate?) {
        database.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
            val weekStart = trainingDate?.let { WeekMath.weekStartOf(it) }
                ?: LocalDate.ofEpochDay(occurrence.weekStartEpochDay)
            dao.updateOccurrence(
                occurrence.copy(
                    trainingDateEpochDay = trainingDate?.toEpochDay(),
                    weekStartEpochDay = weekStart.toEpochDay(),
                    orderIndex = dao.nextOrderIndex(
                        weekStart.toEpochDay(),
                        trainingDate?.toEpochDay(),
                    ),
                )
            )
        }
    }

    /**
     * Moves a placement to any week, on a day or into that week's unscheduled area.
     *
     * Only the *plan* moves. Anything already logged against it keeps the performed date it was
     * logged on, because moving a plan is a statement about the future and re-dating evidence is
     * a different decision the user has to make on purpose.
     */
    /**
     * Moves a placement, and whatever was logged against it, to another day.
     *
     * Once a set exists, the placement is no longer a plan — it is the record of a thing that was
     * done, and its date is the day it was done on. So the sets are re-dated with it and re-homed
     * into that day's session, rather than being left behind on the day it was once planned for.
     *
     * Returns false without changing anything when asked to unschedule a placement that has been
     * trained: work that happened happened on a day, and "anytime this week" cannot describe it.
     * The dialog does not offer the option, so this is the guard rather than the message.
     */
    suspend fun moveOccurrence(
        occurrenceId: String,
        weekStart: LocalDate,
        trainingDate: LocalDate?,
    ): Boolean = database.withTransaction {
        val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction false
        val loggedSets = logging.countSetsForOccurrence(occurrenceId)
        // Work that happened happened on a day, whether it left sets behind or only a duration.
        val trained = loggedSets > 0 || occurrence.state == OccurrenceState.COMPLETED
        if (trainingDate == null && trained) return@withTransaction false
        dao.updateOccurrence(
            occurrence.copy(
                weekStartEpochDay = weekStart.toEpochDay(),
                trainingDateEpochDay = trainingDate?.toEpochDay(),
                orderIndex = dao.nextOrderIndex(
                    weekStart.toEpochDay(),
                    trainingDate?.toEpochDay(),
                ),
                // Moved on its own, it has left its group: a module is the things planned together.
                moduleInstanceId = null,
                modulePosition = null,
            )
        )
        if (trainingDate != null && loggedSets > 0) {
            val session = ensureSession(trainingDate)
            logging.repointSets(occurrenceId, trainingDate.toEpochDay(), session.id)
        }
        true
    }

    /**
     * Places a second copy of an existing placement elsewhere.
     *
     * The prescription is copied by value into its own row, exactly as scheduling from the library
     * does, so editing one copy never reaches the other. Nothing logged is copied: the new copy is
     * a plan, and has not happened yet.
     */
    /**
     * Duplicates a placement into its own week's unscheduled area, at the top.
     *
     * Duplicating is for "I want this again", and the answer to *when* is almost always "not yet".
     * Unscheduled is where that belongs, and the top is where a thing you just asked for should
     * appear — a copy that lands at the bottom of a long list looks like nothing happened.
     */
    suspend fun duplicateOccurrence(occurrenceId: String): String? = database.withTransaction {
        val source = dao.getOccurrence(occurrenceId) ?: return@withTransaction null
        copyOccurrence(
            occurrenceId = occurrenceId,
            weekStart = LocalDate.ofEpochDay(source.weekStartEpochDay),
            trainingDate = null,
            atTop = true,
        )
    }

    suspend fun copyOccurrence(
        occurrenceId: String,
        weekStart: LocalDate,
        trainingDate: LocalDate?,
        atTop: Boolean = false,
    ): String? = database.withTransaction {
        val source = dao.getOccurrence(occurrenceId) ?: return@withTransaction null
        val now = System.currentTimeMillis()
        val prescriptionCopy = source.prescriptionId
            ?.let { library.getPrescription(it) }
            ?.let {
                PrescriptionEntity(
                    id = UUID.randomUUID().toString(),
                    payloadVersion = it.payloadVersion,
                    payloadJson = it.payloadJson,
                    createdAtEpochMs = now,
                )
            }
        prescriptionCopy?.let { library.insertPrescription(it) }
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            weekStartEpochDay = weekStart.toEpochDay(),
            trainingDateEpochDay = trainingDate?.toEpochDay(),
            prescriptionId = prescriptionCopy?.id,
            // A copy is a new plan standing on its own: it joins neither the source's circuit nor
            // its module, which would otherwise gain a member nobody put there.
            circuitInstanceId = null,
            circuitPosition = null,
            moduleInstanceId = null,
            modulePosition = null,
            orderIndex = if (atTop) {
                dao.firstOrderIndex(weekStart.toEpochDay(), trainingDate?.toEpochDay())
            } else {
                dao.nextOrderIndex(weekStart.toEpochDay(), trainingDate?.toEpochDay())
            },
            state = OccurrenceState.PLANNED,
            comment = null,
            createdAtEpochMs = now,
        )
        dao.insertOccurrences(listOf(copy))
        copy.id
    }

    /** Nudges a placement up or down within its own day, or within the unscheduled area. */
    suspend fun reorderOccurrence(occurrenceId: String, delta: Int) {
        database.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
            val slot = dao.occurrencesInSlot(
                occurrence.weekStartEpochDay,
                occurrence.trainingDateEpochDay,
            )
            val moving = slot.firstOrNull { it.id == occurrenceId } ?: return@withTransaction
            val reordered = Planning.reorder(slot, moving, delta)
            if (reordered == slot) return@withTransaction
            reordered.forEachIndexed { index, row ->
                if (row.orderIndex != index) dao.updateOccurrence(row.copy(orderIndex = index))
            }
        }
    }

    /**
     * Re-dates the work logged against one occurrence, as a whole.
     *
     * Every set moves together and is re-homed into the session for the new date, because a set is
     * evidence of a day's training and a day's training is what a session groups. The *plan* is
     * untouched: an exercise can stay planned on Monday while its work is recorded on Tuesday, and
     * the planner and the history are both then telling the truth.
     */
    /**
     * Deletes a placement *and* the work logged against it.
     *
     * Separate from [deleteOccurrenceIfEmpty] on purpose: that one refuses when evidence exists,
     * and this one is the deliberate stronger answer. Nothing calls it without saying out loud
     * what is about to be destroyed.
     */
    suspend fun deleteOccurrenceAndLog(occurrenceId: String) {
        database.withTransaction {
            logging.deleteSetsForOccurrence(occurrenceId)
            dao.deleteOccurrence(occurrenceId)
        }
    }

    /** How many sets are recorded against a placement, for deciding what a deletion would cost. */
    suspend fun loggedSetCount(occurrenceId: String): Int =
        logging.countSetsForOccurrence(occurrenceId)

    suspend fun setOccurrenceState(occurrenceId: String, state: OccurrenceState) {
        database.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
            dao.updateOccurrence(occurrence.copy(state = state))
        }
    }

    suspend fun setOccurrenceComment(occurrenceId: String, comment: String?) {
        database.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
            dao.updateOccurrence(occurrence.copy(comment = comment?.takeIf { it.isNotBlank() }))
        }
    }

    /**
     * Only possible while nothing has been recorded.
     *
     * Logged sets are the usual guard and the database enforces them with RESTRICT, but an
     * activity marked done has no sets and is still a record of training — so completion is
     * refused here too, rather than letting a year of climbing sessions be tidied away.
     */
    suspend fun deleteOccurrenceIfEmpty(occurrenceId: String): Boolean =
        database.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction false
            if (logging.countSetsForOccurrence(occurrenceId) > 0 ||
                occurrence.state == OccurrenceState.COMPLETED
            ) {
                false
            } else {
                dao.deleteOccurrence(occurrenceId)
                true
            }
        }

    // ----------------------------------------------------------- logging

    /**
     * Records one performed set and returns its stable id.
     *
     * The day's session container is created or reused here, behind the scenes: the user never
     * starts or ends a session, and several exercises trained on the same date share one.
     * An unscheduled occurrence is filed under [trainingDate] (today unless the caller overrides
     * it for backfilling), which is also what the set itself records.
     */
    suspend fun logSet(
        occurrenceId: String,
        payload: ActualSetPayload,
        side: BodySide?,
        trainingDate: LocalDate,
    ): String = database.withTransaction {
        val occurrence = dao.getOccurrence(occurrenceId) ?: error("Unknown occurrence")
        if (occurrence.trainingDateEpochDay == null) {
            assignOccurrenceDate(occurrenceId, trainingDate)
        }
        val session = ensureSession(trainingDate)
        val set = ActualSetEntity(
            id = UUID.randomUUID().toString(),
            occurrenceId = occurrenceId,
            sessionId = session.id,
            exerciseId = occurrence.exerciseId,
            trainingDateEpochDay = trainingDate.toEpochDay(),
            prescriptionId = occurrence.prescriptionId,
            orderIndex = logging.nextSetOrderIndex(occurrenceId),
            side = side,
            payloadVersion = ACTUAL_SET_PAYLOAD_VERSION,
            payloadJson = ActualSetJson.encode(payload),
            recordedAtEpochMs = System.currentTimeMillis(),
        )
        logging.insertSet(set)
        set.id
    }

    /** Corrects an already recorded set. History is editable; snapshots are not. */
    suspend fun updateSet(setId: String, payload: ActualSetPayload, side: BodySide?) {
        database.withTransaction {
            val existing = logging.getSet(setId) ?: return@withTransaction
            logging.updateSet(
                existing.copy(
                    side = side,
                    payloadVersion = ACTUAL_SET_PAYLOAD_VERSION,
                    payloadJson = ActualSetJson.encode(payload),
                )
            )
        }
    }

    suspend fun deleteSet(setId: String) = logging.deleteSet(setId)

    private suspend fun ensureSession(trainingDate: LocalDate): TrainingSessionEntity {
        logging.sessionForDate(trainingDate.toEpochDay())?.let { return it }
        val session = TrainingSessionEntity(
            id = UUID.randomUUID().toString(),
            trainingDateEpochDay = trainingDate.toEpochDay(),
            ordinal = 0,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        logging.insertSession(session)
        return session
    }

    // ------------------------------------------------ activities and time

    /**
     * Plans, or records, an activity that is only a duration: an outdoor session, a class, a run.
     *
     * It creates an *occurrence* and nothing else. No library row is written, because going for a
     * run once should not leave an entry behind that you then have to tidy up; the exercise id is
     * derived from the name instead, so two runs called the same thing already group together in
     * history and could be promoted to a real definition later without rewriting anything.
     */
    suspend fun createOneOffActivity(
        name: String,
        weekStart: LocalDate,
        trainingDate: LocalDate?,
        plannedDurationSeconds: Int? = null,
    ): String = database.withTransaction {
        val trimmed = name.trim().ifBlank { "Activity" }
        val now = System.currentTimeMillis()
        val prescription = newPrescriptionRow(
            PrescriptionPayload(sets = 1, targetDurationSeconds = plannedDurationSeconds),
            now,
        )
        library.insertPrescription(prescription)
        val occurrence = ExerciseOccurrenceEntity(
            id = UUID.randomUUID().toString(),
            weekStartEpochDay = weekStart.toEpochDay(),
            trainingDateEpochDay = trainingDate?.toEpochDay(),
            exerciseId = OneOffActivity.exerciseIdFor(trimmed),
            exerciseNameSnapshot = trimmed,
            modeSnapshot = ExerciseMode.ACTIVITY,
            unilateralSnapshot = false,
            measurementUnitSnapshot = null,
            measurementMeaningSnapshot = null,
            categorySnapshot = ExerciseCategory.OTHER_ACTIVITY,
            prescriptionId = prescription.id,
            orderIndex = dao.nextOrderIndex(weekStart.toEpochDay(), trainingDate?.toEpochDay()),
            state = OccurrenceState.PLANNED,
            comment = null,
            createdAtEpochMs = now,
            isOneOff = true,
        )
        dao.insertOccurrences(listOf(occurrence))
        occurrence.id
    }

    /** Corrects the name of a one-off, keeping its derived identity in step with it. */
    suspend fun renameOneOffActivity(occurrenceId: String, name: String) {
        database.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
            if (!occurrence.isOneOff) return@withTransaction
            val trimmed = name.trim().ifBlank { return@withTransaction }
            dao.updateOccurrence(
                occurrence.copy(
                    exerciseNameSnapshot = trimmed,
                    exerciseId = OneOffActivity.exerciseIdFor(trimmed),
                )
            )
        }
    }

    /**
     * Records how long something took.
     *
     * [manual] is the provenance, not a formatting hint: an inferred value that has been saved is
     * still what this workout says it took, so changing a default or a formula later must leave it
     * alone. Passing null clears the record and hands the question back to the estimate.
     */
    suspend fun setLoggedDuration(occurrenceId: String, seconds: Int?, manual: Boolean) {
        database.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
            dao.updateOccurrence(
                occurrence.copy(
                    loggedDurationSeconds = seconds?.coerceAtLeast(0),
                    loggedDurationManual = seconds != null && manual,
                )
            )
        }
    }

    /**
     * Writes whole workouts, atomically.
     *
     * One occurrence or twenty, it is a single transaction: a circuit review that half-landed
     * would be a record of training that did not happen that way. Replacing rather than appending
     * is what makes saving a second time a correction instead of a duplication.
     *
     * A station that is not ticked has its sets cleared and drops back to planned, so unticking
     * says "this did not happen" as plainly as ticking says it did. A skip the user set on purpose
     * is left alone.
     */
    suspend fun saveLogs(trainingDate: LocalDate, writes: List<OccurrenceLogWrite>) {
        if (writes.isEmpty()) return
        database.withTransaction {
            val session = ensureSession(trainingDate)
            val now = System.currentTimeMillis()
            writes.forEach { write ->
                val occurrence = dao.getOccurrence(write.occurrenceId) ?: return@forEach
                logging.deleteSetsForOccurrence(write.occurrenceId)
                write.sets.forEachIndexed { index, set ->
                    logging.insertSet(
                        ActualSetEntity(
                            id = UUID.randomUUID().toString(),
                            occurrenceId = write.occurrenceId,
                            sessionId = session.id,
                            exerciseId = occurrence.exerciseId,
                            trainingDateEpochDay = trainingDate.toEpochDay(),
                            prescriptionId = occurrence.prescriptionId,
                            orderIndex = index,
                            side = set.side,
                            payloadVersion = ACTUAL_SET_PAYLOAD_VERSION,
                            payloadJson = ActualSetJson.encode(set.payload),
                            recordedAtEpochMs = now,
                        )
                    )
                }
                val state = when {
                    write.completed -> OccurrenceState.COMPLETED
                    occurrence.state == OccurrenceState.COMPLETED -> OccurrenceState.PLANNED
                    else -> occurrence.state
                }
                // Work that happened belongs to the day it happened on, and a placement always
                // sits in the week of its own date -- so the two move together or not at all.
                val moved = occurrence.trainingDateEpochDay != trainingDate.toEpochDay()
                val weekStart = WeekMath.weekStartOf(trainingDate)
                dao.updateOccurrence(
                    occurrence.copy(
                        trainingDateEpochDay = trainingDate.toEpochDay(),
                        weekStartEpochDay = weekStart.toEpochDay(),
                        orderIndex = if (moved) {
                            dao.nextOrderIndex(weekStart.toEpochDay(), trainingDate.toEpochDay())
                        } else {
                            occurrence.orderIndex
                        },
                        state = state,
                        comment = write.comment?.takeIf { it.isNotBlank() },
                        loggedEffort = write.effort,
                        loggedDurationSeconds = write.durationSeconds?.coerceAtLeast(0),
                        loggedDurationManual = write.durationSeconds != null && write.durationManual,
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------ routines

    /**
     * Saved routines, joined live to the library so a renamed exercise reads correctly in the
     * editor. Each station's prescription is the routine's own copy and is never re-read from the
     * library default.
     */
    fun observeRoutines(): Flow<List<Routine>> =
        combine(routines.observeRoutines(), library.observeExercises()) { rows, exercises ->
            val byId = exercises.associateBy { it.exercise.id }
            rows.map { it.toRoutine(byId) }
        }

    fun observeRoutine(id: String): Flow<Routine?> =
        combine(routines.observeRoutine(id), library.observeExercises()) { row, exercises ->
            row?.toRoutine(exercises.associateBy { it.exercise.id })
        }

    suspend fun getRoutine(id: String): Routine? = database.withTransaction {
        val row = routines.getRoutine(id) ?: return@withTransaction null
        val exercises = row.entries
            .mapNotNull { library.getExerciseWithDefault(it.entry.exerciseId) }
            .associateBy { it.exercise.id }
        row.toRoutine(exercises)
    }

    suspend fun createRoutine(draft: RoutineDraft): String = database.withTransaction {
        val now = System.currentTimeMillis()
        val routine = RoutineEntity(
            id = UUID.randomUUID().toString(),
            name = draft.name.trim().ifBlank { "Circuit" },
            rounds = draft.rounds.coerceAtLeast(1),
            transitionSeconds = draft.transitionSeconds.coerceAtLeast(0),
            roundRestSeconds = draft.roundRestSeconds.coerceAtLeast(0),
            structureVersion = 1,
            createdAtEpochMs = now,
            category = draft.category,
        )
        routines.insertRoutine(routine)
        writeRoutineEntries(routine.id, draft.entries, now)
        routine.id
    }

    /**
     * Replaces a routine's stations. Every entry gets a fresh prescription row, so a scheduled
     * copy keeps pointing at what it was given and the library default is never touched.
     */
    suspend fun updateRoutine(id: String, draft: RoutineDraft) {
        database.withTransaction {
            val existing = routines.getRoutine(id)?.routine ?: return@withTransaction
            val now = System.currentTimeMillis()
            val superseded = routines.entryPrescriptionIdsOf(id)
            routines.deleteEntriesOf(id)
            routines.updateRoutine(
                existing.copy(
                    name = draft.name.trim().ifBlank { existing.name },
                    rounds = draft.rounds.coerceAtLeast(1),
                    transitionSeconds = draft.transitionSeconds.coerceAtLeast(0),
                    roundRestSeconds = draft.roundRestSeconds.coerceAtLeast(0),
                    structureVersion = existing.structureVersion + 1,
                    category = draft.category,
                )
            )
            writeRoutineEntries(id, draft.entries, now)
            // Only rows nothing points at any more actually go; a scheduled copy holds its own.
            superseded.forEach { library.deletePrescriptionIfUnused(it) }
        }
    }

    /** A second copy of a routine, to vary without disturbing the original. */
    suspend fun duplicateRoutine(id: String): String? = database.withTransaction {
        val source = routines.getRoutine(id) ?: return@withTransaction null
        createRoutine(
            RoutineDraft(
                name = source.routine.name + " copy",
                rounds = source.routine.rounds,
                transitionSeconds = source.routine.transitionSeconds,
                roundRestSeconds = source.routine.roundRestSeconds,
                entries = source.orderedEntries.map { row ->
                    RoutineEntryDraft(
                        exerciseId = row.entry.exerciseId,
                        prescription = row.prescription?.payload(),
                    )
                },
                category = source.routine.category,
            )
        )
    }

    suspend fun routineRemovalImpact(id: String): RoutineRemoval? = database.withTransaction {
        val routine = getRoutine(id) ?: return@withTransaction null
        val instances = routines.instanceIdsOf(id)
        val recorded = instances.count { instanceId -> circuitHasRecord(instanceId) }
        RoutineRemoval(routine, scheduledCopies = instances.size, recordedCopies = recorded)
    }

    /**
     * Removes a routine. Scheduled copies are untouched either way: they are real occurrences and
     * real logs, and a template going away is a statement about what you plan next.
     *
     * A routine nothing was ever cut from is deleted outright; one that has been scheduled becomes
     * a tombstone, because its id is what a circuit log points at.
     */
    suspend fun removeRoutine(id: String): Boolean = database.withTransaction {
        val existing = routines.getRoutine(id) ?: return@withTransaction false
        if (routines.countInstancesOf(id) > 0) {
            routines.markRoutineDeleted(id, System.currentTimeMillis())
            return@withTransaction true
        }
        val prescriptions = routines.entryPrescriptionIdsOf(id)
        routines.deleteEntriesOf(id)
        routines.deleteRoutine(existing.routine.id)
        prescriptions.forEach { library.deletePrescriptionIfUnused(it) }
        true
    }

    private suspend fun writeRoutineEntries(
        routineId: String,
        entries: List<RoutineEntryDraft>,
        now: Long,
    ) {
        val rows = entries.mapIndexed { index, draft ->
            val prescription = draft.prescription?.let { newPrescriptionRow(it, now) }
            prescription?.let { library.insertPrescription(it) }
            RoutineEntryEntity(
                id = UUID.randomUUID().toString(),
                routineId = routineId,
                orderIndex = index,
                exerciseId = draft.exerciseId,
                exerciseNameSnapshot =
                    library.getExerciseWithDefault(draft.exerciseId)?.exercise?.name.orEmpty(),
                prescriptionId = prescription?.id,
            )
        }
        routines.insertEntries(rows)
    }

    private fun RoutineWithEntries.toRoutine(
        exercises: Map<String, ExerciseWithDefaultPrescription>,
    ) = Routine(
        id = routine.id,
        name = routine.name,
        rounds = routine.rounds,
        transitionSeconds = routine.transitionSeconds,
        roundRestSeconds = routine.roundRestSeconds,
        structureVersion = routine.structureVersion,
        category = routine.category,
        entries = orderedEntries.map { row ->
            val definition = exercises[row.entry.exerciseId]?.exercise
            RoutineEntryView(
                id = row.entry.id,
                exerciseId = row.entry.exerciseId,
                name = definition?.name ?: row.entry.exerciseNameSnapshot,
                mode = definition?.mode ?: ExerciseMode.REPETITIONS,
                unilateral = definition?.unilateral == true,
                measurementUnit = definition?.measurementUnit,
                measurementMeaning = definition?.measurementMeaning,
                category = definition?.category,
                prescription = row.prescription?.payload(),
                orderIndex = row.entry.orderIndex,
                definitionMissing = definition == null,
            )
        },
    )

    // ------------------------------------------------------------ circuits

    /** Every scheduled circuit of one week, without its stations. */
    fun observeWeekCircuits(weekStart: LocalDate): Flow<List<WeekCircuit>> =
        dao.observeCircuitsInWeek(weekStart.toEpochDay()).map { rows ->
            rows.map { it.toWeekCircuit() }
        }

    private fun CircuitInstanceEntity.toWeekCircuit() = WeekCircuit(
        id = id,
        routineId = routineId,
        name = routineNameSnapshot,
        rounds = rounds,
        transitionSeconds = transitionSeconds,
        roundRestSeconds = roundRestSeconds,
        weekStart = LocalDate.ofEpochDay(weekStartEpochDay),
        trainingDate = trainingDateEpochDay?.let(LocalDate::ofEpochDay),
        orderIndex = orderIndex,
        moduleInstanceId = moduleInstanceId,
        modulePosition = modulePosition,
        category = categorySnapshot,
    )

    /** One scheduled circuit and the occurrences that are its stations. */
    fun observeScheduledCircuit(circuitInstanceId: String): Flow<ScheduledCircuit?> = combine(
        dao.observeCircuit(circuitInstanceId),
        dao.observeCircuitStations(circuitInstanceId),
        logging.observeSetPayloadsForCircuit(circuitInstanceId),
    ) { circuit, stations, payloads ->
        circuit?.let { row ->
            val heaviest = payloads.heaviestByOccurrence()
            val counts = payloads.groupingBy { it.occurrenceId }.eachCount()
            row.toScheduledCircuit(
                stations.map {
                    it.toPlanned(
                        maxLoad = heaviest[it.occurrence.id],
                        loggedSets = counts[it.occurrence.id] ?: 0,
                    )
                }
            )
        }
    }

    /**
     * Copies a routine into a week: the circuit container, plus one real exercise occurrence per
     * station with its own prescription copy.
     *
     * The stations are ordinary occurrences on purpose. That is what makes a circuit loggable
     * without ever running the timer, and what keeps the completed-workout count honest — the
     * container is not one of them and contributes nothing.
     */
    suspend fun scheduleRoutine(
        routineId: String,
        weekStart: LocalDate,
        trainingDate: LocalDate?,
        moduleInstanceId: String? = null,
        modulePosition: Int? = null,
        orderIndex: Int? = null,
    ): String? = database.withTransaction {
        val source = routines.getRoutine(routineId) ?: return@withTransaction null
        val now = System.currentTimeMillis()
        val circuitId = UUID.randomUUID().toString()
        val base = orderIndex
            ?: dao.nextOrderIndex(weekStart.toEpochDay(), trainingDate?.toEpochDay())
        val snapshots = mutableListOf<CircuitEntrySnapshot>()
        val occurrences = source.orderedEntries.mapIndexed { position, row ->
            val definition = library.getExerciseWithDefault(row.entry.exerciseId)?.exercise
            val payload = row.prescription?.payload()
            val copy = row.prescription?.let {
                PrescriptionEntity(
                    id = UUID.randomUUID().toString(),
                    payloadVersion = it.payloadVersion,
                    payloadJson = it.payloadJson,
                    createdAtEpochMs = now,
                )
            }
            copy?.let { library.insertPrescription(it) }
            snapshots += CircuitEntrySnapshot(
                position = position,
                exerciseId = row.entry.exerciseId,
                exerciseName = definition?.name ?: row.entry.exerciseNameSnapshot,
                mode = definition?.mode ?: ExerciseMode.REPETITIONS,
                unilateral = definition?.unilateral == true,
                prescription = payload,
            )
            ExerciseOccurrenceEntity(
                id = UUID.randomUUID().toString(),
                weekStartEpochDay = weekStart.toEpochDay(),
                trainingDateEpochDay = trainingDate?.toEpochDay(),
                exerciseId = row.entry.exerciseId,
                exerciseNameSnapshot = definition?.name ?: row.entry.exerciseNameSnapshot,
                modeSnapshot = definition?.mode ?: ExerciseMode.REPETITIONS,
                unilateralSnapshot = definition?.unilateral == true,
                measurementUnitSnapshot = definition?.measurementUnit,
                measurementMeaningSnapshot = definition?.measurementMeaning,
                categorySnapshot = definition?.category,
                prescriptionId = copy?.id,
                orderIndex = base + position,
                state = OccurrenceState.PLANNED,
                comment = null,
                createdAtEpochMs = now,
                circuitInstanceId = circuitId,
                circuitPosition = position,
                // Stations carry the module too, so what a module holds can be asked of the
                // occurrences alone when checking for records or moving logged work.
                moduleInstanceId = moduleInstanceId,
                modulePosition = modulePosition,
            )
        }
        dao.insertCircuit(
            CircuitInstanceEntity(
                id = circuitId,
                routineId = routineId,
                routineNameSnapshot = source.routine.name,
                structureVersion = source.routine.structureVersion,
                structureSnapshotJson = CircuitSnapshotJson.encode(
                    CircuitStructureSnapshot(
                        routineId = routineId,
                        routineName = source.routine.name,
                        structureVersion = source.routine.structureVersion,
                        rounds = source.routine.rounds,
                        transitionSeconds = source.routine.transitionSeconds,
                        roundRestSeconds = source.routine.roundRestSeconds,
                        entries = snapshots,
                    )
                ),
                weekStartEpochDay = weekStart.toEpochDay(),
                trainingDateEpochDay = trainingDate?.toEpochDay(),
                orderIndex = base,
                rounds = source.routine.rounds,
                transitionSeconds = source.routine.transitionSeconds,
                roundRestSeconds = source.routine.roundRestSeconds,
                createdAtEpochMs = now,
                moduleInstanceId = moduleInstanceId,
                modulePosition = modulePosition,
                categorySnapshot = source.routine.category,
            )
        )
        dao.insertOccurrences(occurrences)
        circuitId
    }

    /**
     * Moves a scheduled circuit, and every station of it, together. Moved on its own it leaves any
     * module it was part of, exactly as a single exercise does.
     */
    suspend fun moveCircuit(
        circuitInstanceId: String,
        weekStart: LocalDate,
        trainingDate: LocalDate?,
    ): Boolean = database.withTransaction {
        val circuit = dao.getCircuit(circuitInstanceId) ?: return@withTransaction false
        if (trainingDate == null && circuitHasRecord(circuitInstanceId)) {
            return@withTransaction false
        }
        relocateCircuit(
            circuit = circuit,
            weekStart = weekStart,
            trainingDate = trainingDate,
            base = dao.nextOrderIndex(weekStart.toEpochDay(), trainingDate?.toEpochDay()),
            keepModule = false,
        )
        true
    }

    /** Puts a circuit and its stations in a slot, taking any logged sets along to the new date. */
    private suspend fun relocateCircuit(
        circuit: CircuitInstanceEntity,
        weekStart: LocalDate,
        trainingDate: LocalDate?,
        base: Int,
        keepModule: Boolean,
    ) {
        val stations = dao.circuitStations(circuit.id)
        dao.updateCircuit(
            circuit.copy(
                weekStartEpochDay = weekStart.toEpochDay(),
                trainingDateEpochDay = trainingDate?.toEpochDay(),
                orderIndex = base,
                moduleInstanceId = circuit.moduleInstanceId.takeIf { keepModule },
                modulePosition = circuit.modulePosition.takeIf { keepModule },
            )
        )
        stations.forEachIndexed { index, station ->
            dao.updateOccurrence(
                station.copy(
                    weekStartEpochDay = weekStart.toEpochDay(),
                    trainingDateEpochDay = trainingDate?.toEpochDay(),
                    orderIndex = base + index,
                    moduleInstanceId = station.moduleInstanceId.takeIf { keepModule },
                    modulePosition = station.modulePosition.takeIf { keepModule },
                )
            )
        }
        if (trainingDate != null) {
            val session = ensureSession(trainingDate)
            stations.forEach { logging.repointSets(it.id, trainingDate.toEpochDay(), session.id) }
        }
    }

    /** Takes a scheduled circuit back out of the week, refusing once anything was recorded. */
    suspend fun deleteCircuitIfEmpty(circuitInstanceId: String): Boolean =
        database.withTransaction {
            if (circuitHasRecord(circuitInstanceId)) return@withTransaction false
            dao.circuitStations(circuitInstanceId).forEach { dao.deleteOccurrence(it.id) }
            dao.deleteCircuit(circuitInstanceId)
            true
        }

    /** Deletes a scheduled circuit together with everything logged against its stations. */
    suspend fun deleteCircuitAndLogs(circuitInstanceId: String) {
        database.withTransaction {
            dao.circuitStations(circuitInstanceId).forEach { station ->
                logging.deleteSetsForOccurrence(station.id)
                dao.deleteOccurrence(station.id)
            }
            dao.deleteCircuit(circuitInstanceId)
        }
    }

    /** How many stations of a circuit carry evidence, for saying what a deletion would cost. */
    suspend fun circuitRecordedStations(circuitInstanceId: String): Int =
        dao.circuitStations(circuitInstanceId).count {
            it.state == OccurrenceState.COMPLETED || logging.countSetsForOccurrence(it.id) > 0
        }

    private suspend fun circuitHasRecord(circuitInstanceId: String): Boolean =
        dao.circuitStations(circuitInstanceId).any {
            it.state == OccurrenceState.COMPLETED || logging.countSetsForOccurrence(it.id) > 0
        }

    private fun CircuitInstanceEntity.toScheduledCircuit(stations: List<PlannedOccurrence>) =
        ScheduledCircuit(
            id = id,
            routineId = routineId,
            name = routineNameSnapshot,
            rounds = rounds,
            transitionSeconds = transitionSeconds,
            roundRestSeconds = roundRestSeconds,
            weekStart = LocalDate.ofEpochDay(weekStartEpochDay),
            trainingDate = trainingDateEpochDay?.let(LocalDate::ofEpochDay),
            orderIndex = orderIndex,
            stations = stations,
            category = categorySnapshot,
        )

    // ------------------------------------------------------------- modules

    fun observeModules(): Flow<List<TrainingModule>> =
        combine(modules.observeModules(), library.observeExercises(), observeRoutines()) {
            rows, exercises, saved ->
            val exercisesById = exercises.associateBy { it.exercise.id }
            val routinesById = saved.associateBy { it.id }
            rows.map { it.toModule(exercisesById, routinesById) }
        }

    fun observeModule(id: String): Flow<TrainingModule?> =
        combine(modules.observeModule(id), library.observeExercises(), observeRoutines()) {
            row, exercises, saved ->
            row?.toModule(exercises.associateBy { it.exercise.id }, saved.associateBy { it.id })
        }

    suspend fun getModule(id: String): TrainingModule? = database.withTransaction {
        val row = modules.getModule(id) ?: return@withTransaction null
        // Retired exercises and circuits are left out, so they read as unavailable here exactly
        // as they do in the live list, and exactly as scheduling will treat them.
        val exercises = row.entries
            .mapNotNull { entry -> entry.entry.exerciseId?.let { library.getExerciseWithDefault(it) } }
            .filter { it.exercise.deletedAtEpochMs == null }
            .associateBy { it.exercise.id }
        val saved = row.entries
            .mapNotNull { entry -> entry.entry.routineId }
            .filter { routines.getRoutine(it)?.routine?.deletedAtEpochMs == null }
            .mapNotNull { getRoutine(it) }
            .associateBy { it.id }
        row.toModule(exercises, saved)
    }

    suspend fun createModule(draft: ModuleDraft): String = database.withTransaction {
        val now = System.currentTimeMillis()
        val module = ModuleEntity(
            id = UUID.randomUUID().toString(),
            name = draft.name.trim().ifBlank { "Module" },
            description = draft.description.cleaned(),
            createdAtEpochMs = now,
        )
        modules.insertModule(module)
        writeModuleEntries(module.id, draft.entries, now)
        module.id
    }

    /**
     * Replaces a module's entries. Each gets a fresh prescription row, so scheduled copies keep
     * what they were given: template edits never reach the week.
     */
    suspend fun updateModule(id: String, draft: ModuleDraft) {
        database.withTransaction {
            val existing = modules.getModule(id)?.module ?: return@withTransaction
            val superseded = modules.entryPrescriptionIdsOf(id)
            modules.deleteEntriesOf(id)
            modules.updateModule(
                existing.copy(
                    name = draft.name.trim().ifBlank { existing.name },
                    description = draft.description.cleaned(),
                )
            )
            writeModuleEntries(id, draft.entries, System.currentTimeMillis())
            superseded.forEach { library.deletePrescriptionIfUnused(it) }
        }
    }

    suspend fun duplicateModule(id: String): String? = database.withTransaction {
        val source = modules.getModule(id) ?: return@withTransaction null
        createModule(
            ModuleDraft(
                name = source.module.name + " copy",
                description = source.module.description,
                entries = source.orderedEntries.map { row ->
                    ModuleEntryDraft(
                        exerciseId = row.entry.exerciseId,
                        variationId = row.entry.variationId,
                        prescription = row.prescription?.payload(),
                        routineId = row.entry.routineId,
                    )
                },
            )
        )
    }

    suspend fun moduleRemovalImpact(id: String): ModuleRemoval? = database.withTransaction {
        val module = getModule(id) ?: return@withTransaction null
        ModuleRemoval(module, scheduledCopies = modules.countInstancesOf(id))
    }

    /**
     * Removes a module template. Scheduled copies are untouched either way. One nothing was ever
     * cut from goes outright; one that was scheduled stays as a tombstone for their lineage.
     */
    suspend fun removeModule(id: String): Boolean = database.withTransaction {
        val existing = modules.getModule(id) ?: return@withTransaction false
        if (modules.countInstancesOf(id) > 0) {
            modules.markModuleDeleted(id, System.currentTimeMillis())
            return@withTransaction true
        }
        val prescriptions = modules.entryPrescriptionIdsOf(id)
        modules.deleteEntriesOf(id)
        modules.deleteModule(existing.module.id)
        prescriptions.forEach { library.deletePrescriptionIfUnused(it) }
        true
    }

    private suspend fun writeModuleEntries(
        moduleId: String,
        entries: List<ModuleEntryDraft>,
        now: Long,
    ) {
        val rows = entries.mapIndexedNotNull { index, draft ->
            when {
                draft.routineId != null -> ModuleEntryEntity(
                    id = UUID.randomUUID().toString(),
                    moduleId = moduleId,
                    orderIndex = index,
                    routineId = draft.routineId,
                    routineNameSnapshot = routines.getRoutine(draft.routineId)?.routine?.name,
                )

                draft.exerciseId != null -> {
                    val prescription = draft.prescription?.let { newPrescriptionRow(it, now) }
                    prescription?.let { library.insertPrescription(it) }
                    val variation = draft.variationId
                        ?.let { variations.getVariation(it) }
                        ?.variation
                        ?.takeIf { it.exerciseId == draft.exerciseId }
                    ModuleEntryEntity(
                        id = UUID.randomUUID().toString(),
                        moduleId = moduleId,
                        orderIndex = index,
                        exerciseId = draft.exerciseId,
                        exerciseNameSnapshot =
                            library.getExerciseWithDefault(draft.exerciseId)?.exercise?.name,
                        variationId = variation?.id,
                        variationTagSnapshot = variation?.tag,
                        prescriptionId = prescription?.id,
                    )
                }

                else -> null
            }
        }
        modules.insertEntries(rows)
    }

    private fun ModuleWithEntries.toModule(
        exercises: Map<String, ExerciseWithDefaultPrescription>,
        saved: Map<String, Routine>,
    ) = TrainingModule(
        id = module.id,
        name = module.name,
        description = module.description,
        entries = orderedEntries.map { row ->
            val entry = row.entry
            val definition = entry.exerciseId?.let { exercises[it]?.exercise }
            val routine = entry.routineId?.let { saved[it] }
            ModuleEntryView(
                id = entry.id,
                exerciseId = entry.exerciseId,
                variationId = entry.variationId,
                variationTag = entry.variationTagSnapshot,
                name = definition?.name ?: routine?.name ?: entry.exerciseNameSnapshot
                    ?: entry.routineNameSnapshot.orEmpty(),
                mode = definition?.mode ?: ExerciseMode.REPETITIONS,
                unilateral = definition?.unilateral == true,
                category = definition?.category ?: routine?.category,
                prescription = row.prescription?.payload(),
                routineId = entry.routineId,
                routine = routine,
                definitionMissing = if (entry.routineId != null) routine == null else definition == null,
            )
        },
    )

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    // ------------------------------------------------- modules in the week

    fun observeWeekModules(weekStart: LocalDate): Flow<List<WeekModule>> =
        modules.observeModulesInWeek(weekStart.toEpochDay()).map { rows ->
            rows.map {
                WeekModule(
                    id = it.id,
                    moduleId = it.moduleId,
                    name = it.moduleNameSnapshot,
                    weekStart = LocalDate.ofEpochDay(it.weekStartEpochDay),
                    trainingDate = it.trainingDateEpochDay?.let(LocalDate::ofEpochDay),
                    orderIndex = it.orderIndex,
                )
            }
        }

    /**
     * Copies a module into a week: the named group, and each entry as the real planned work it
     * stands for — an exercise occurrence with its own prescription copy, or a whole circuit cut by
     * the same code that schedules a circuit alone. The group counts nothing and adds no time.
     */
    suspend fun scheduleModule(
        moduleId: String,
        weekStart: LocalDate,
        trainingDate: LocalDate?,
    ): String? = database.withTransaction {
        val source = modules.getModule(moduleId) ?: return@withTransaction null
        val instanceId = UUID.randomUUID().toString()
        val base = dao.nextOrderIndex(weekStart.toEpochDay(), trainingDate?.toEpochDay())
        modules.insertInstance(
            ModuleInstanceEntity(
                id = instanceId,
                moduleId = moduleId,
                moduleNameSnapshot = source.module.name,
                weekStartEpochDay = weekStart.toEpochDay(),
                trainingDateEpochDay = trainingDate?.toEpochDay(),
                orderIndex = base,
                createdAtEpochMs = System.currentTimeMillis(),
            )
        )
        source.orderedEntries.forEachIndexed { position, row ->
            val entry = row.entry
            when {
                entry.routineId != null -> {
                    // Unavailable entries are left out, exactly as the editor and the picker
                    // warn: a circuit removed from the list is not one to plan again.
                    if (routines.getRoutine(entry.routineId)?.routine?.deletedAtEpochMs != null) {
                        return@forEachIndexed
                    }
                    scheduleRoutine(
                        routineId = entry.routineId,
                        weekStart = weekStart,
                        trainingDate = trainingDate,
                        moduleInstanceId = instanceId,
                        modulePosition = position,
                        orderIndex = base + position,
                    )
                }

                entry.exerciseId != null -> {
                    // Deleted outright leaves nothing to cut a copy from; retired from the library
                    // is a statement that it is not to be planned again. Either way it is skipped.
                    val exercise = library.getExerciseWithDefault(entry.exerciseId)?.exercise
                        ?.takeIf { it.deletedAtEpochMs == null }
                        ?: return@forEachIndexed
                    placeExercise(
                        exercise = exercise,
                        plan = row.prescription,
                        variationId = entry.variationId,
                        variationTag = entry.variationTagSnapshot,
                        weekStart = weekStart,
                        trainingDate = trainingDate,
                        orderIndex = base + position,
                        moduleInstanceId = instanceId,
                        modulePosition = position,
                    )
                }
            }
        }
        instanceId
    }

    /**
     * Moves a scheduled module and the members still with it, together.
     *
     * A member that has already been filed somewhere else — logged on another day, say — stays
     * where it is: moving the group is a statement about the plan, and re-dating that work would
     * rewrite a record nobody asked to change. Refuses to unschedule a group holding trained work.
     */
    suspend fun moveModule(
        moduleInstanceId: String,
        weekStart: LocalDate,
        trainingDate: LocalDate?,
    ): Boolean = database.withTransaction {
        val instance = modules.getInstance(moduleInstanceId) ?: return@withTransaction false
        if (trainingDate == null && moduleHasRecord(moduleInstanceId)) {
            return@withTransaction false
        }
        val base = dao.nextOrderIndex(weekStart.toEpochDay(), trainingDate?.toEpochDay())
        fun withGroup(week: Long, date: Long?) =
            week == instance.weekStartEpochDay && date == instance.trainingDateEpochDay
        modules.updateInstance(
            instance.copy(
                weekStartEpochDay = weekStart.toEpochDay(),
                trainingDateEpochDay = trainingDate?.toEpochDay(),
                orderIndex = base,
            )
        )
        modules.circuitsIn(moduleInstanceId)
            .filter { withGroup(it.weekStartEpochDay, it.trainingDateEpochDay) }
            .forEach { circuit ->
                relocateCircuit(
                    circuit = circuit,
                    weekStart = weekStart,
                    trainingDate = trainingDate,
                    base = base + (circuit.modulePosition ?: 0),
                    keepModule = true,
                )
            }
        val session = trainingDate?.let { ensureSession(it) }
        modules.occurrencesIn(moduleInstanceId)
            .filter { it.circuitInstanceId == null }
            .filter { withGroup(it.weekStartEpochDay, it.trainingDateEpochDay) }
            .forEach { member ->
                dao.updateOccurrence(
                    member.copy(
                        weekStartEpochDay = weekStart.toEpochDay(),
                        trainingDateEpochDay = trainingDate?.toEpochDay(),
                        orderIndex = base + (member.modulePosition ?: 0),
                    )
                )
                if (session != null && trainingDate != null &&
                    logging.countSetsForOccurrence(member.id) > 0
                ) {
                    logging.repointSets(member.id, trainingDate.toEpochDay(), session.id)
                }
            }
        true
    }

    /** Dissolves the group. Every member stays exactly where it is, as ordinary planned work. */
    suspend fun ungroupModule(moduleInstanceId: String) {
        database.withTransaction {
            modules.releaseOccurrences(moduleInstanceId)
            modules.releaseCircuits(moduleInstanceId)
            modules.deleteInstance(moduleInstanceId)
        }
    }

    /** Takes one exercise out of its module, leaving it where it is. */
    suspend fun takeOccurrenceOutOfModule(occurrenceId: String) {
        database.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
            dao.updateOccurrence(occurrence.copy(moduleInstanceId = null, modulePosition = null))
        }
    }

    /** Takes one circuit out of its module, stations and all, leaving it where it is. */
    suspend fun takeCircuitOutOfModule(circuitInstanceId: String) {
        database.withTransaction {
            val circuit = dao.getCircuit(circuitInstanceId) ?: return@withTransaction
            dao.updateCircuit(circuit.copy(moduleInstanceId = null, modulePosition = null))
            dao.circuitStations(circuitInstanceId).forEach {
                dao.updateOccurrence(it.copy(moduleInstanceId = null, modulePosition = null))
            }
        }
    }

    /** How many of a module's exercises carry a record, for saying what a deletion would cost. */
    suspend fun moduleRecordedExercises(moduleInstanceId: String): Int =
        modules.occurrencesIn(moduleInstanceId).count {
            it.state == OccurrenceState.COMPLETED || logging.countSetsForOccurrence(it.id) > 0
        }

    /** Takes a scheduled module and everything in it back out of the week, refusing once trained. */
    suspend fun removeModuleIfEmpty(moduleInstanceId: String): Boolean =
        database.withTransaction {
            if (moduleHasRecord(moduleInstanceId)) return@withTransaction false
            deleteModuleMembers(moduleInstanceId, withLogs = false)
            true
        }

    /** The deliberate stronger answer: the module, its members and what was logged against them. */
    suspend fun deleteModuleAndLogs(moduleInstanceId: String) {
        database.withTransaction { deleteModuleMembers(moduleInstanceId, withLogs = true) }
    }

    private suspend fun deleteModuleMembers(moduleInstanceId: String, withLogs: Boolean) {
        modules.occurrencesIn(moduleInstanceId).forEach { member ->
            if (withLogs) logging.deleteSetsForOccurrence(member.id)
            dao.deleteOccurrence(member.id)
        }
        modules.circuitsIn(moduleInstanceId).forEach { dao.deleteCircuit(it.id) }
        modules.deleteInstance(moduleInstanceId)
    }

    private suspend fun moduleHasRecord(moduleInstanceId: String): Boolean =
        moduleRecordedExercises(moduleInstanceId) > 0

    private fun PrescriptionEntity.payload(): PrescriptionPayload? =
        runCatching { PrescriptionJson.decode(payloadJson) }.getOrNull()

    private fun newPrescriptionRow(payload: PrescriptionPayload, now: Long) = PrescriptionEntity(
        id = UUID.randomUUID().toString(),
        payloadVersion = PRESCRIPTION_PAYLOAD_VERSION,
        payloadJson = PrescriptionJson.encode(payload),
        createdAtEpochMs = now,
    )
}

private fun OccurrenceWithPrescription.toPlanned(
    maxLoad: Double? = null,
    loggedSets: Int = 0,
): PlannedOccurrence {
    val payload: PrescriptionPayload? = prescription?.let {
        runCatching { PrescriptionJson.decode(it.payloadJson) }.getOrNull()
    }
    return PlannedOccurrence(
        id = occurrence.id,
        exerciseId = occurrence.exerciseId,
        name = occurrence.exerciseNameSnapshot,
        mode = occurrence.modeSnapshot,
        unilateral = occurrence.unilateralSnapshot,
        measurementUnit = occurrence.measurementUnitSnapshot,
        measurementMeaning = occurrence.measurementMeaningSnapshot,
        category = occurrence.categorySnapshot,
        trainingDate = occurrence.trainingDateEpochDay?.let(LocalDate::ofEpochDay),
        weekStart = LocalDate.ofEpochDay(occurrence.weekStartEpochDay),
        prescriptionId = occurrence.prescriptionId,
        prescription = payload,
        prescriptionUnreadable = prescription != null && payload == null,
        maxLoad = maxLoad,
        loggedSets = loggedSets,
        state = occurrence.state,
        comment = occurrence.comment,
        orderIndex = occurrence.orderIndex,
        loggedDurationSeconds = occurrence.loggedDurationSeconds,
        loggedDurationManual = occurrence.loggedDurationManual,
        loggedEffort = occurrence.loggedEffort,
        isOneOff = occurrence.isOneOff,
        circuitInstanceId = occurrence.circuitInstanceId,
        circuitPosition = occurrence.circuitPosition,
        variationId = occurrence.variationId,
        variationTag = occurrence.variationTagSnapshot,
        moduleInstanceId = occurrence.moduleInstanceId,
        modulePosition = occurrence.modulePosition,
    )
}

private fun VariationWithPrescription.toVariation() = ExerciseVariation(
    id = variation.id,
    exerciseId = variation.exerciseId,
    tag = variation.tag,
    notes = variation.notes,
    prescription = prescription?.let {
        runCatching { PrescriptionJson.decode(it.payloadJson) }.getOrNull()
    },
    orderIndex = variation.orderIndex,
)

private fun ExerciseWithDefaultPrescription.toLibraryExercise(
    variations: List<VariationWithPrescription> = emptyList(),
) = LibraryExercise(
    id = exercise.id,
    name = exercise.name,
    mode = exercise.mode,
    unilateral = exercise.unilateral,
    measurementUnit = exercise.measurementUnit,
    measurementMeaning = exercise.measurementMeaning,
    notes = exercise.notes,
    description = exercise.description,
    category = exercise.category,
    deletedAtEpochMs = exercise.deletedAtEpochMs,
    defaultPrescription = defaultPrescription?.let {
        runCatching { PrescriptionJson.decode(it.payloadJson) }.getOrNull()
    },
    variations = variations.map { it.toVariation() }.sortedWith(compareBy({ it.orderIndex }, { it.tag })),
)

/**
 * The heaviest set of each occurrence.
 *
 * The load lives inside the JSON payload rather than in a column, so this cannot be a `MAX()` and
 * is worked out after decoding. Cheap enough: a week holds a few dozen sets, and it only feeds a
 * label.
 */
private fun List<SetPayloadRow>.heaviestByOccurrence(): Map<String, Double> = this
    .mapNotNull { row ->
        runCatching { ActualSetJson.decode(row.payloadJson) }
            .getOrNull()?.measurement?.value?.let { row.occurrenceId to it }
    }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, values) -> values.max() }

private fun ActualSetEntity.toPerformed() = PerformedSet(
    id = id,
    occurrenceId = occurrenceId,
    exerciseId = exerciseId,
    trainingDate = LocalDate.ofEpochDay(trainingDateEpochDay),
    orderIndex = orderIndex,
    side = side,
    payload = runCatching { ActualSetJson.decode(payloadJson) }.getOrElse { ActualSetPayload() },
    prescriptionId = prescriptionId,
    recordedAtEpochMs = recordedAtEpochMs,
)
