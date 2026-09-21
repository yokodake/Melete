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

    @Transaction
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
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
}
