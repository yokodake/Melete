package com.yokodake.melete.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yokodake.melete.data.entity.BenchmarkEntity
import com.yokodake.melete.data.entity.BenchmarkResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BenchmarkDao {

    /** Every benchmark, hidden ones included, in the order they are listed. */
    @Query("SELECT * FROM benchmarks ORDER BY orderIndex, createdAtEpochMs")
    fun observeBenchmarks(): Flow<List<BenchmarkEntity>>

    @Query("SELECT * FROM benchmarks WHERE id = :id")
    fun observeBenchmark(id: String): Flow<BenchmarkEntity?>

    @Query("SELECT * FROM benchmarks WHERE id = :id")
    suspend fun getBenchmark(id: String): BenchmarkEntity?

    @Query("SELECT COALESCE(MAX(orderIndex) + 1, 0) FROM benchmarks")
    suspend fun nextOrderIndex(): Int

    @Insert
    suspend fun insertBenchmark(row: BenchmarkEntity)

    @Update
    suspend fun updateBenchmark(row: BenchmarkEntity)

    @Query("DELETE FROM benchmarks WHERE id = :id")
    suspend fun deleteBenchmark(id: String)

    @Query("SELECT * FROM benchmark_results")
    fun observeAllResults(): Flow<List<BenchmarkResultEntity>>

    @Query("SELECT * FROM benchmark_results WHERE benchmarkId = :benchmarkId")
    fun observeResultsOf(benchmarkId: String): Flow<List<BenchmarkResultEntity>>

    /** Results filed between two dates, inclusive: what the calendar shows on its days. */
    @Query("SELECT * FROM benchmark_results WHERE dateEpochDay BETWEEN :from AND :to")
    fun observeResultsBetween(from: Long, to: Long): Flow<List<BenchmarkResultEntity>>

    @Query("SELECT COUNT(*) FROM benchmark_results WHERE benchmarkId = :benchmarkId")
    suspend fun countResults(benchmarkId: String): Int

    @Query("SELECT * FROM benchmark_results WHERE id = :id")
    suspend fun getResult(id: String): BenchmarkResultEntity?

    @Insert
    suspend fun insertResult(row: BenchmarkResultEntity)

    @Update
    suspend fun updateResult(row: BenchmarkResultEntity)

    @Query("DELETE FROM benchmark_results WHERE id = :id")
    suspend fun deleteResult(id: String)
}
