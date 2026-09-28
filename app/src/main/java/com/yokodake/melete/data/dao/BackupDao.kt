package com.yokodake.melete.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yokodake.melete.data.entity.ActualSetEntity
import com.yokodake.melete.data.entity.BenchmarkEntity
import com.yokodake.melete.data.entity.BenchmarkResultEntity
import com.yokodake.melete.data.entity.CircuitInstanceEntity
import com.yokodake.melete.data.entity.DiaryEntryEntity
import com.yokodake.melete.data.entity.DiaryValueEntity
import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.ExerciseVariationEntity
import com.yokodake.melete.data.entity.ModuleEntity
import com.yokodake.melete.data.entity.ModuleEntryEntity
import com.yokodake.melete.data.entity.ModuleInstanceEntity
import com.yokodake.melete.data.entity.RoutineEntity
import com.yokodake.melete.data.entity.RoutineEntryEntity
import com.yokodake.melete.data.entity.TrackerEntity
import com.yokodake.melete.data.entity.TrainingSessionEntity

/**
 * Every table, whole: read out for an export, emptied and refilled for a restore.
 *
 * Nothing here filters out retired rows — a backup that dropped tombstones would drop the anchors
 * history hangs from.
 */
@Dao
interface BackupDao {

    @Query("SELECT * FROM exercises") suspend fun exercises(): List<ExerciseEntity>
    @Query("SELECT * FROM exercise_variations") suspend fun variations(): List<ExerciseVariationEntity>
    @Query("SELECT * FROM routines") suspend fun routines(): List<RoutineEntity>
    @Query("SELECT * FROM routine_entries") suspend fun routineEntries(): List<RoutineEntryEntity>
    @Query("SELECT * FROM modules") suspend fun modules(): List<ModuleEntity>
    @Query("SELECT * FROM module_entries") suspend fun moduleEntries(): List<ModuleEntryEntity>
    @Query("SELECT * FROM circuit_instances") suspend fun circuitInstances(): List<CircuitInstanceEntity>
    @Query("SELECT * FROM module_instances") suspend fun moduleInstances(): List<ModuleInstanceEntity>
    @Query("SELECT * FROM exercise_occurrences") suspend fun occurrences(): List<ExerciseOccurrenceEntity>
    @Query("SELECT * FROM training_sessions") suspend fun sessions(): List<TrainingSessionEntity>
    @Query("SELECT * FROM actual_sets") suspend fun sets(): List<ActualSetEntity>
    @Query("SELECT * FROM trackers") suspend fun trackers(): List<TrackerEntity>
    @Query("SELECT * FROM diary_entries") suspend fun diaryEntries(): List<DiaryEntryEntity>
    @Query("SELECT * FROM diary_values") suspend fun diaryValues(): List<DiaryValueEntity>
    @Query("SELECT * FROM benchmarks") suspend fun benchmarks(): List<BenchmarkEntity>
    @Query("SELECT * FROM benchmark_results") suspend fun benchmarkResults(): List<BenchmarkResultEntity>

    // Children before parents, so no foreign key ever sees a missing row.
    @Query("DELETE FROM benchmark_results") suspend fun clearBenchmarkResults()
    @Query("DELETE FROM benchmarks") suspend fun clearBenchmarks()
    @Query("DELETE FROM diary_values") suspend fun clearDiaryValues()
    @Query("DELETE FROM diary_entries") suspend fun clearDiaryEntries()
    @Query("DELETE FROM trackers") suspend fun clearTrackers()
    @Query("DELETE FROM actual_sets") suspend fun clearSets()
    @Query("DELETE FROM training_sessions") suspend fun clearSessions()
    @Query("DELETE FROM exercise_occurrences") suspend fun clearOccurrences()
    @Query("DELETE FROM circuit_instances") suspend fun clearCircuitInstances()
    @Query("DELETE FROM module_instances") suspend fun clearModuleInstances()
    @Query("DELETE FROM routine_entries") suspend fun clearRoutineEntries()
    @Query("DELETE FROM routines") suspend fun clearRoutines()
    @Query("DELETE FROM module_entries") suspend fun clearModuleEntries()
    @Query("DELETE FROM modules") suspend fun clearModules()
    @Query("DELETE FROM exercise_variations") suspend fun clearVariations()
    @Query("DELETE FROM exercises") suspend fun clearExercises()

    @Insert suspend fun insertExercises(rows: List<ExerciseEntity>)
    @Insert suspend fun insertVariations(rows: List<ExerciseVariationEntity>)
    @Insert suspend fun insertRoutines(rows: List<RoutineEntity>)
    @Insert suspend fun insertRoutineEntries(rows: List<RoutineEntryEntity>)
    @Insert suspend fun insertModules(rows: List<ModuleEntity>)
    @Insert suspend fun insertModuleEntries(rows: List<ModuleEntryEntity>)
    @Insert suspend fun insertCircuitInstances(rows: List<CircuitInstanceEntity>)
    @Insert suspend fun insertModuleInstances(rows: List<ModuleInstanceEntity>)
    @Insert suspend fun insertOccurrences(rows: List<ExerciseOccurrenceEntity>)
    @Insert suspend fun insertSessions(rows: List<TrainingSessionEntity>)
    @Insert suspend fun insertSets(rows: List<ActualSetEntity>)
    @Insert suspend fun insertTrackers(rows: List<TrackerEntity>)
    @Insert suspend fun insertDiaryEntries(rows: List<DiaryEntryEntity>)
    @Insert suspend fun insertDiaryValues(rows: List<DiaryValueEntity>)
    @Insert suspend fun insertBenchmarks(rows: List<BenchmarkEntity>)
    @Insert suspend fun insertBenchmarkResults(rows: List<BenchmarkResultEntity>)
}
