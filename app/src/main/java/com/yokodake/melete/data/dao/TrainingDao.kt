package com.yokodake.melete.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.PrescriptionEntity
import kotlinx.coroutines.flow.Flow

/** An occurrence together with the prescription copy that was planned for it. */
data class OccurrenceWithPrescription(
    @Embedded val occurrence: ExerciseOccurrenceEntity,
    @Relation(parentColumn = "prescriptionId", entityColumn = "id")
    val prescription: PrescriptionEntity?,
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

    @Query("DELETE FROM exercise_occurrences WHERE isSampleData = 1")
    suspend fun deleteSampleOccurrences()

    @Query("DELETE FROM exercises WHERE isSampleData = 1")
    suspend fun deleteSampleExercises()

    @Query("DELETE FROM prescriptions WHERE isSampleData = 1")
    suspend fun deleteSamplePrescriptions()
}
