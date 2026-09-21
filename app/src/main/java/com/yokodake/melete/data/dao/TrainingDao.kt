package com.yokodake.melete.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.yokodake.melete.data.entity.ActualSetEntity
import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.PrescriptionEntity
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

    @Query("SELECT COUNT(*) FROM exercise_occurrences WHERE isSampleData = 1")
    fun observeSampleOccurrenceCount(): Flow<Int>

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

    @Query("DELETE FROM actual_sets WHERE occurrenceId IN (SELECT id FROM exercise_occurrences WHERE isSampleData = 1)")
    suspend fun deleteSampleActualSets()

    @Query("DELETE FROM exercise_occurrences WHERE isSampleData = 1")
    suspend fun deleteSampleOccurrences()

    @Query("DELETE FROM exercises WHERE isSampleData = 1")
    suspend fun deleteSampleExercises()

    @Query("DELETE FROM prescriptions WHERE isSampleData = 1")
    suspend fun deleteSamplePrescriptions()
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
}
