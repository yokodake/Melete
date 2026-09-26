package com.yokodake.melete.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.yokodake.melete.data.entity.DiaryEntryEntity
import com.yokodake.melete.data.entity.DiaryValueEntity
import com.yokodake.melete.data.entity.TrackerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    /** The trackers still in use, in the order they are shown. */
    @Query("SELECT * FROM trackers WHERE deletedAtEpochMs IS NULL ORDER BY orderIndex")
    fun observeTrackers(): Flow<List<TrackerEntity>>

    /** Every tracker ever defined, retired ones included; zero only on a brand-new database. */
    @Query("SELECT COUNT(*) FROM trackers")
    suspend fun countAllTrackers(): Int

    @Query("SELECT * FROM trackers WHERE id = :id")
    suspend fun getTracker(id: String): TrackerEntity?

    @Query("SELECT * FROM trackers WHERE deletedAtEpochMs IS NULL ORDER BY orderIndex")
    suspend fun activeTrackers(): List<TrackerEntity>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM trackers")
    suspend fun nextTrackerOrder(): Int

    @Insert
    suspend fun insertTracker(tracker: TrackerEntity)

    @Update
    suspend fun updateTracker(tracker: TrackerEntity)


    @Query(
        """
        SELECT * FROM diary_entries
        WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY dateEpochDay
        """
    )
    fun observeEntries(fromEpochDay: Long, toEpochDay: Long): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_values WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeValues(fromEpochDay: Long, toEpochDay: Long): Flow<List<DiaryValueEntity>>

    @Upsert
    suspend fun upsertEntry(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE dateEpochDay = :dateEpochDay")
    suspend fun deleteEntry(dateEpochDay: Long)

    @Query("DELETE FROM diary_values WHERE dateEpochDay = :dateEpochDay")
    suspend fun deleteValues(dateEpochDay: Long)

    @Insert
    suspend fun insertValues(values: List<DiaryValueEntity>)
}
