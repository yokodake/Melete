package com.yokodake.melete.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.yokodake.melete.data.entity.ActualSetEntity
import com.yokodake.melete.data.entity.CircuitInstanceEntity
import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.PrescriptionEntity
import com.yokodake.melete.data.entity.RoutineEntity
import com.yokodake.melete.data.entity.RoutineEntryEntity
import com.yokodake.melete.data.entity.TrainingSessionEntity
import kotlinx.coroutines.flow.Flow

/** An occurrence together with the prescription copy that was planned for it. */
data class OccurrenceWithPrescription(
    @Embedded val occurrence: ExerciseOccurrenceEntity,
    @Relation(parentColumn = "prescriptionId", entityColumn = "id")
    val prescription: PrescriptionEntity?,
)

/** One logged set, reduced to what the planner needs to summarise it. */
data class SetPayloadRow(
    val occurrenceId: String,
    val payloadJson: String,
)

/** A library exercise together with its current default prescription. */
data class ExerciseWithDefaultPrescription(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "defaultPrescriptionId", entityColumn = "id")
    val defaultPrescription: PrescriptionEntity?,
)

@Dao
interface TrainingDao {

    @Transaction
    @Query(
        """
        SELECT * FROM exercise_occurrences
        WHERE weekStartEpochDay = :weekStartEpochDay
        ORDER BY trainingDateEpochDay IS NOT NULL, trainingDateEpochDay, orderIndex, createdAtEpochMs
        """
    )
    fun observeWeek(weekStartEpochDay: Long): Flow<List<OccurrenceWithPrescription>>

    @Transaction
    @Query("SELECT * FROM exercise_occurrences WHERE id = :id")
    fun observeOccurrence(id: String): Flow<OccurrenceWithPrescription?>

    @Query("SELECT * FROM exercise_occurrences WHERE id = :id")
    suspend fun getOccurrence(id: String): ExerciseOccurrenceEntity?

    /** Next free position inside one day, or inside the week's unscheduled area. */
    @Query(
        """
        SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM exercise_occurrences
        WHERE weekStartEpochDay = :weekStartEpochDay
          AND ((:trainingDateEpochDay IS NULL AND trainingDateEpochDay IS NULL)
               OR trainingDateEpochDay = :trainingDateEpochDay)
        """
    )
    suspend fun nextOrderIndex(weekStartEpochDay: Long, trainingDateEpochDay: Long?): Int

    /**
     * An index that sorts before everything currently in the slot.
     *
     * Negative values are fine: the list is ordered by this column, never counted by it, so there
     * is no need to renumber the rest of the slot to make room at the front.
     */
    @Query(
        """
        SELECT COALESCE(MIN(orderIndex), 0) - 1 FROM exercise_occurrences
        WHERE weekStartEpochDay = :weekStartEpochDay
          AND ((:trainingDateEpochDay IS NULL AND trainingDateEpochDay IS NULL)
               OR trainingDateEpochDay = :trainingDateEpochDay)
        """
    )
    suspend fun firstOrderIndex(weekStartEpochDay: Long, trainingDateEpochDay: Long?): Int

    /** Every scheduled circuit of one week, in the order its cards are shown. */
    @Query(
        """
        SELECT * FROM circuit_instances
        WHERE weekStartEpochDay = :weekStartEpochDay
        ORDER BY trainingDateEpochDay IS NOT NULL, trainingDateEpochDay, orderIndex
        """
    )
    fun observeCircuitsInWeek(weekStartEpochDay: Long): Flow<List<CircuitInstanceEntity>>

    @Query("SELECT * FROM circuit_instances WHERE id = :id")
    suspend fun getCircuit(id: String): CircuitInstanceEntity?

    @Query("SELECT * FROM circuit_instances WHERE id = :id")
    fun observeCircuit(id: String): Flow<CircuitInstanceEntity?>

    @Insert
    suspend fun insertCircuit(circuit: CircuitInstanceEntity)

    @Update
    suspend fun updateCircuit(circuit: CircuitInstanceEntity)

    @Query("DELETE FROM circuit_instances WHERE id = :id")
    suspend fun deleteCircuit(id: String)

    /** The stations of one scheduled circuit, in execution order. */
    @Transaction
    @Query(
        """
        SELECT * FROM exercise_occurrences
        WHERE circuitInstanceId = :circuitInstanceId
        ORDER BY circuitPosition, orderIndex
        """
    )
    fun observeCircuitStations(circuitInstanceId: String): Flow<List<OccurrenceWithPrescription>>

    @Query(
        """
        SELECT * FROM exercise_occurrences
        WHERE circuitInstanceId = :circuitInstanceId
        ORDER BY circuitPosition, orderIndex
        """
    )
    suspend fun circuitStations(circuitInstanceId: String): List<ExerciseOccurrenceEntity>

    @Insert
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Insert
    suspend fun insertPrescriptions(prescriptions: List<PrescriptionEntity>)

    @Insert
    suspend fun insertOccurrences(occurrences: List<ExerciseOccurrenceEntity>)

    @Update
    suspend fun updateOccurrence(occurrence: ExerciseOccurrenceEntity)

    @Query("DELETE FROM exercise_occurrences WHERE id = :id")
    suspend fun deleteOccurrence(id: String)

    /** Every occurrence of one exercise, newest planning first. Includes retired definitions. */
    @Transaction
    @Query(
        """
        SELECT * FROM exercise_occurrences
        WHERE exerciseId = :exerciseId
        ORDER BY trainingDateEpochDay DESC, createdAtEpochMs DESC
        """
    )
    fun observeOccurrencesOf(exerciseId: String): Flow<List<OccurrenceWithPrescription>>

    /** Occurrences sharing one slot, in the order they are shown. */
    @Query(
        """
        SELECT * FROM exercise_occurrences
        WHERE weekStartEpochDay = :weekStartEpochDay
          AND ((:trainingDateEpochDay IS NULL AND trainingDateEpochDay IS NULL)
               OR trainingDateEpochDay = :trainingDateEpochDay)
        ORDER BY orderIndex, createdAtEpochMs
        """
    )
    suspend fun occurrencesInSlot(
        weekStartEpochDay: Long,
        trainingDateEpochDay: Long?,
    ): List<ExerciseOccurrenceEntity>

}

@Dao
interface LibraryDao {

    /** The active library: what you can still plan. Retired entries are excluded. */
    @Transaction
    @Query(
        """
        SELECT * FROM exercises
        WHERE deletedAtEpochMs IS NULL
        ORDER BY name COLLATE NOCASE
        """
    )
    fun observeExercises(): Flow<List<ExerciseWithDefaultPrescription>>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseWithDefault(id: String): ExerciseWithDefaultPrescription?

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeExerciseWithDefault(id: String): Flow<ExerciseWithDefaultPrescription?>

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity)

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Insert
    suspend fun insertPrescription(prescription: PrescriptionEntity)

    @Query("SELECT * FROM prescriptions WHERE id = :id")
    suspend fun getPrescription(id: String): PrescriptionEntity?

    @Query("UPDATE exercises SET deletedAtEpochMs = :atEpochMs WHERE id = :id")
    suspend fun markExerciseDeleted(id: String, atEpochMs: Long)

    @Query("UPDATE exercises SET deletedAtEpochMs = NULL WHERE id = :id")
    suspend fun restoreExercise(id: String)

    // ------------------------------------------------- what refers to an exercise

    /**
     * How many planned copies point at this exercise, retired ones included.
     *
     * [ExerciseOccurrenceEntity.exerciseId] is a lineage reference rather than a foreign key, so
     * nothing in the schema stops the row going; this is the check that decides whether it should.
     */
    @Query("SELECT COUNT(*) FROM exercise_occurrences WHERE exerciseId = :exerciseId")
    suspend fun countOccurrencesOf(exerciseId: String): Int

    /** How many sets were ever logged against it, under any planned copy. */
    @Query("SELECT COUNT(*) FROM actual_sets WHERE exerciseId = :exerciseId")
    suspend fun countSetsOf(exerciseId: String): Int

    /**
     * How many copies of it were marked done.
     *
     * A duration-only activity writes no sets at all, so counting sets would call a year of
     * climbing sessions "never used" and delete the definition out from under them. Completion is
     * the other kind of evidence, and it protects the row exactly as a logged set does.
     */
    @Query(
        """
        SELECT COUNT(*) FROM exercise_occurrences
        WHERE exerciseId = :exerciseId AND state = 'COMPLETED'
        """
    )
    suspend fun countCompletedOccurrencesOf(exerciseId: String): Int

    @Query(
        """
        SELECT prescriptionId FROM exercise_occurrences
        WHERE exerciseId = :exerciseId AND prescriptionId IS NOT NULL
        """
    )
    suspend fun occurrencePrescriptionIdsOf(exerciseId: String): List<String>

    @Query("DELETE FROM exercise_occurrences WHERE exerciseId = :exerciseId")
    suspend fun deleteOccurrencesOf(exerciseId: String)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteExercise(id: String)

    /**
     * Drops a prescription row only once nothing points at it.
     *
     * Prescriptions are copied by value and shared by nobody, but the guard makes the delete
     * order-independent and impossible to get wrong: if anything still refers to it, it stays.
     */
    @Query(
        """
        DELETE FROM prescriptions
        WHERE id = :id
          AND NOT EXISTS (SELECT 1 FROM exercises WHERE defaultPrescriptionId = :id)
          AND NOT EXISTS (SELECT 1 FROM exercise_occurrences WHERE prescriptionId = :id)
          AND NOT EXISTS (SELECT 1 FROM actual_sets WHERE prescriptionId = :id)
        """
    )
    suspend fun deletePrescriptionIfUnused(id: String)
}

@Dao
interface LoggingDao {

    @Query("SELECT * FROM training_sessions WHERE trainingDateEpochDay = :trainingDateEpochDay ORDER BY ordinal LIMIT 1")
    suspend fun sessionForDate(trainingDateEpochDay: Long): TrainingSessionEntity?

    @Insert
    suspend fun insertSession(session: TrainingSessionEntity)

    @Query("SELECT * FROM actual_sets WHERE occurrenceId = :occurrenceId ORDER BY orderIndex, recordedAtEpochMs")
    fun observeSetsForOccurrence(occurrenceId: String): Flow<List<ActualSetEntity>>

    @Query("SELECT COUNT(*) FROM actual_sets WHERE occurrenceId = :occurrenceId")
    suspend fun countSetsForOccurrence(occurrenceId: String): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM actual_sets WHERE occurrenceId = :occurrenceId")
    suspend fun nextSetOrderIndex(occurrenceId: String): Int

    /**
     * Sets performed for the same stable exercise in other occurrences, most recent training date
     * first. Bounded so that a long history cannot make the logger slow.
     */
    @Query(
        """
        SELECT * FROM actual_sets
        WHERE exerciseId = :exerciseId AND occurrenceId != :excludeOccurrenceId
        ORDER BY trainingDateEpochDay DESC, orderIndex ASC, recordedAtEpochMs ASC
        LIMIT 60
        """
    )
    fun observeRecentSets(exerciseId: String, excludeOccurrenceId: String): Flow<List<ActualSetEntity>>

    @Insert
    suspend fun insertSet(set: ActualSetEntity)

    @Update
    suspend fun updateSet(set: ActualSetEntity)

    @Query("SELECT * FROM actual_sets WHERE id = :id")
    suspend fun getSet(id: String): ActualSetEntity?

    @Query("DELETE FROM actual_sets WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Query("SELECT * FROM actual_sets WHERE occurrenceId = :occurrenceId ORDER BY orderIndex")
    suspend fun setsForOccurrence(occurrenceId: String): List<ActualSetEntity>

    @Query("DELETE FROM actual_sets WHERE occurrenceId = :occurrenceId")
    suspend fun deleteSetsForOccurrence(occurrenceId: String)

    /**
     * Every logged set in one week, as its occurrence and its raw payload.
     *
     * The load lives inside the JSON payload rather than in a column, so the heaviest set cannot
     * be found with MAX() and is worked out after decoding. Cheap enough: a week holds a few dozen
     * sets, and this only feeds a label.
     */
    @Query(
        """
        SELECT a.occurrenceId AS occurrenceId, a.payloadJson AS payloadJson
        FROM actual_sets a
        JOIN exercise_occurrences o ON a.occurrenceId = o.id
        WHERE o.weekStartEpochDay = :weekStartEpochDay
        """
    )
    fun observeSetPayloadsInWeek(weekStartEpochDay: Long): Flow<List<SetPayloadRow>>

    /**
     * Re-dates every set of one occurrence together, and re-homes them into that day's session.
     *
     * Sets move with the placement they belong to, so the two can never disagree about when the
     * work happened.
     */
    @Query("UPDATE actual_sets SET trainingDateEpochDay = :date, sessionId = :sessionId WHERE occurrenceId = :occurrenceId")
    suspend fun repointSets(occurrenceId: String, date: Long, sessionId: String)

    /** Every logged set of one scheduled circuit, as its station and its raw payload. */
    @Query(
        """
        SELECT a.occurrenceId AS occurrenceId, a.payloadJson AS payloadJson
        FROM actual_sets a
        JOIN exercise_occurrences o ON a.occurrenceId = o.id
        WHERE o.circuitInstanceId = :circuitInstanceId
        """
    )
    fun observeSetPayloadsForCircuit(circuitInstanceId: String): Flow<List<SetPayloadRow>>
}

/** One station of a routine, with the prescription copy it owns. */
data class RoutineEntryWithPrescription(
    @Embedded val entry: RoutineEntryEntity,
    @Relation(parentColumn = "prescriptionId", entityColumn = "id")
    val prescription: PrescriptionEntity?,
)

/** A routine with its stations, in order. */
data class RoutineWithEntries(
    @Embedded val routine: RoutineEntity,
    @Relation(entity = RoutineEntryEntity::class, parentColumn = "id", entityColumn = "routineId")
    val entries: List<RoutineEntryWithPrescription>,
) {
    val orderedEntries: List<RoutineEntryWithPrescription>
        get() = entries.sortedBy { it.entry.orderIndex }
}

@Dao
interface RoutineDao {

    /** The routines you can still schedule. Retired ones stay as anchors and are excluded. */
    @Transaction
    @Query("SELECT * FROM routines WHERE deletedAtEpochMs IS NULL ORDER BY name COLLATE NOCASE")
    fun observeRoutines(): Flow<List<RoutineWithEntries>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    fun observeRoutine(id: String): Flow<RoutineWithEntries?>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutine(id: String): RoutineWithEntries?

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity)

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Insert
    suspend fun insertEntries(entries: List<RoutineEntryEntity>)

    @Query("SELECT prescriptionId FROM routine_entries WHERE routineId = :routineId AND prescriptionId IS NOT NULL")
    suspend fun entryPrescriptionIdsOf(routineId: String): List<String>

    @Query("DELETE FROM routine_entries WHERE routineId = :routineId")
    suspend fun deleteEntriesOf(routineId: String)

    @Query("UPDATE routines SET deletedAtEpochMs = :atEpochMs WHERE id = :id")
    suspend fun markRoutineDeleted(id: String, atEpochMs: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    /** How many scheduled copies point at this routine, trained or not. */
    @Query("SELECT COUNT(*) FROM circuit_instances WHERE routineId = :routineId")
    suspend fun countInstancesOf(routineId: String): Int

    @Query("SELECT id FROM circuit_instances WHERE routineId = :routineId")
    suspend fun instanceIdsOf(routineId: String): List<String>
}
