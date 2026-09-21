package com.yokodake.melete.data

import androidx.room.withTransaction
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.dao.ExerciseWithDefaultPrescription
import com.yokodake.melete.data.dao.OccurrenceWithPrescription
import com.yokodake.melete.data.entity.ActualSetEntity
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.entity.PrescriptionEntity
import com.yokodake.melete.data.entity.TrainingSessionEntity
import com.yokodake.melete.data.model.ACTUAL_SET_PAYLOAD_VERSION
import com.yokodake.melete.data.model.ActualSetJson
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PRESCRIPTION_PAYLOAD_VERSION
import com.yokodake.melete.data.model.PrescriptionJson
import com.yokodake.melete.data.model.PrescriptionPayload
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
    val state: OccurrenceState,
    val comment: String?,
    val orderIndex: Int,
    val isSampleData: Boolean,
)

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
    val isSampleData: Boolean,
)

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

    // ---------------------------------------------------------------- week

    fun observeWeek(weekStart: LocalDate): Flow<List<PlannedOccurrence>> =
        dao.observeWeek(weekStart.toEpochDay()).map { rows -> rows.map { it.toPlanned() } }

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
        library.observeExercises().map { rows -> rows.map { it.toLibraryExercise() } }

    suspend fun getLibraryExercise(id: String): LibraryExercise? =
        library.getExerciseWithDefault(id)?.toLibraryExercise()

    /**
     * The library entry a planned copy came from, followed live. The explanation of a movement is
     * reference material rather than part of the record, so it is read from the library instead
     * of from the snapshot: an exercise that has since been deleted simply has none.
     */
    fun observeLibraryExercise(id: String): Flow<LibraryExercise?> =
        library.observeExerciseWithDefault(id).map { it?.toLibraryExercise() }

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
            isSampleData = false,
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

    // ---------------------------------------------------------- scheduling

    /**
     * Copies a library exercise into the week: onto [trainingDate], or into the week's unscheduled
     * area when it is null. The prescription is copied by value and the exercise's identity fields
     * are snapshotted, so later template edits cannot reach this copy.
     */
    suspend fun scheduleExercise(
        exerciseId: String,
        weekStart: LocalDate,
        trainingDate: LocalDate?,
    ): String = database.withTransaction {
        val source = library.getExerciseWithDefault(exerciseId)
            ?: error("Unknown exercise $exerciseId")
        val now = System.currentTimeMillis()
        val copy = source.defaultPrescription?.let {
            PrescriptionEntity(
                id = UUID.randomUUID().toString(),
                payloadVersion = it.payloadVersion,
                payloadJson = it.payloadJson,
                createdAtEpochMs = now,
                isSampleData = false,
            )
        }
        copy?.let { library.insertPrescription(it) }
        val occurrence = ExerciseOccurrenceEntity(
            id = UUID.randomUUID().toString(),
            weekStartEpochDay = weekStart.toEpochDay(),
            trainingDateEpochDay = trainingDate?.toEpochDay(),
            exerciseId = source.exercise.id,
            exerciseNameSnapshot = source.exercise.name,
            modeSnapshot = source.exercise.mode,
            unilateralSnapshot = source.exercise.unilateral,
            measurementUnitSnapshot = source.exercise.measurementUnit,
            measurementMeaningSnapshot = source.exercise.measurementMeaning,
            categorySnapshot = source.exercise.category,
            prescriptionId = copy?.id,
            orderIndex = dao.nextOrderIndex(weekStart.toEpochDay(), trainingDate?.toEpochDay()),
            state = OccurrenceState.PLANNED,
            comment = null,
            createdAtEpochMs = now,
            isSampleData = false,
        )
        dao.insertOccurrences(listOf(occurrence))
        occurrence.id
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

    /** Only possible while nothing has been logged; the database enforces that with RESTRICT. */
    suspend fun deleteOccurrenceIfEmpty(occurrenceId: String): Boolean =
        database.withTransaction {
            if (logging.countSetsForOccurrence(occurrenceId) > 0) {
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

    // ------------------------------------------------------- sample data

    val sampleDataPresent: Flow<Boolean> =
        dao.observeSampleOccurrenceCount().map { it > 0 }

    /**
     * Inserts explicitly marked development data for [weekStart]. Only ever called from an
     * explicit debug action: sample rows are never inserted on launch and never mixed into real
     * records without the flag that makes them removable again.
     */
    suspend fun seedSampleWeek(weekStart: LocalDate) {
        val sample = DevSampleData.build(weekStart)
        database.withTransaction {
            dao.insertPrescriptions(sample.prescriptions)
            dao.insertExercises(sample.exercises)
            dao.insertOccurrences(sample.occurrences)
        }
    }

    suspend fun clearSampleData() {
        database.withTransaction {
            // Children first: actual sets and occurrences reference rows with ON DELETE RESTRICT.
            dao.deleteSampleActualSets()
            dao.deleteSampleOccurrences()
            dao.deleteSampleExercises()
            dao.deleteSamplePrescriptions()
        }
    }

    private fun newPrescriptionRow(payload: PrescriptionPayload, now: Long) = PrescriptionEntity(
        id = UUID.randomUUID().toString(),
        payloadVersion = PRESCRIPTION_PAYLOAD_VERSION,
        payloadJson = PrescriptionJson.encode(payload),
        createdAtEpochMs = now,
        isSampleData = false,
    )
}

private fun OccurrenceWithPrescription.toPlanned(): PlannedOccurrence {
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
        state = occurrence.state,
        comment = occurrence.comment,
        orderIndex = occurrence.orderIndex,
        isSampleData = occurrence.isSampleData,
    )
}

private fun ExerciseWithDefaultPrescription.toLibraryExercise() = LibraryExercise(
    id = exercise.id,
    name = exercise.name,
    mode = exercise.mode,
    unilateral = exercise.unilateral,
    measurementUnit = exercise.measurementUnit,
    measurementMeaning = exercise.measurementMeaning,
    notes = exercise.notes,
    description = exercise.description,
    category = exercise.category,
    defaultPrescription = defaultPrescription?.let {
        runCatching { PrescriptionJson.decode(it.payloadJson) }.getOrNull()
    },
    isSampleData = exercise.isSampleData,
)

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
