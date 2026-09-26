package com.yokodake.melete.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.yokodake.melete.data.entity.DiaryEntryEntity
import com.yokodake.melete.data.entity.DiaryMetricValueEntity
import com.yokodake.melete.data.entity.MetricDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    /** The metrics that can still be recorded, in the order they are shown. */
    @Query("SELECT * FROM metric_definitions WHERE deletedAtEpochMs IS NULL ORDER BY orderIndex")
    fun observeMetrics(): Flow<List<MetricDefinitionEntity>>

    /** Adds a default metric unless one with that id already exists; a user's edits are kept. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMetricIfAbsent(metric: MetricDefinitionEntity)

    @Query(
        """
        SELECT * FROM diary_entries
        WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY dateEpochDay
        """
    )
    fun observeEntries(fromEpochDay: Long, toEpochDay: Long): Flow<List<DiaryEntryEntity>>

    @Query(
        """
        SELECT * FROM diary_metric_values
        WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        """
    )
    fun observeValues(fromEpochDay: Long, toEpochDay: Long): Flow<List<DiaryMetricValueEntity>>

    @Upsert
    suspend fun upsertEntry(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE dateEpochDay = :dateEpochDay")
    suspend fun deleteEntry(dateEpochDay: Long)

    @Query("DELETE FROM diary_metric_values WHERE dateEpochDay = :dateEpochDay")
    suspend fun deleteValues(dateEpochDay: Long)

    @Insert
    suspend fun insertValues(values: List<DiaryMetricValueEntity>)
}
