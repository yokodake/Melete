package com.yokodake.melete.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yokodake.melete.data.dao.BackupDao
import com.yokodake.melete.data.dao.BenchmarkDao
import com.yokodake.melete.data.dao.DiaryDao
import com.yokodake.melete.data.dao.LibraryDao
import com.yokodake.melete.data.dao.LoggingDao
import com.yokodake.melete.data.dao.ModuleDao
import com.yokodake.melete.data.dao.RoutineDao
import com.yokodake.melete.data.dao.TrainingDao
import com.yokodake.melete.data.dao.VariationDao
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
 * The training record.
 *
 * **Schema 10 is the baseline, and upgrades preserve data.** Every schema change from here comes
 * with a migration in [MeleteMigrations] and a test of it; see there for the steps. Neither build
 * has a destructive fallback: until phase 5B the debug build wiped itself on a schema change, which
 * was fine while every record was disposable, but it also meant a forgotten migration only showed
 * up in release — the build holding the real record. Now Melete Debug fails on open exactly as
 * release would, so a missing migration is found where losing data does not matter.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        ExerciseOccurrenceEntity::class,
        TrainingSessionEntity::class,
        ActualSetEntity::class,
        RoutineEntity::class,
        RoutineEntryEntity::class,
        CircuitInstanceEntity::class,
        ExerciseVariationEntity::class,
        ModuleEntity::class,
        ModuleEntryEntity::class,
        ModuleInstanceEntity::class,
        TrackerEntity::class,
        DiaryEntryEntity::class,
        DiaryValueEntity::class,
        BenchmarkEntity::class,
        BenchmarkResultEntity::class,
    ],
    version = MeleteMigrations.CURRENT,
    exportSchema = true,
)
@TypeConverters(MeleteConverters::class)
abstract class MeleteDatabase : RoomDatabase() {

    abstract fun trainingDao(): TrainingDao

    abstract fun libraryDao(): LibraryDao

    abstract fun loggingDao(): LoggingDao

    abstract fun routineDao(): RoutineDao

    abstract fun variationDao(): VariationDao

    abstract fun moduleDao(): ModuleDao

    abstract fun diaryDao(): DiaryDao

    abstract fun backupDao(): BackupDao

    abstract fun benchmarkDao(): BenchmarkDao

    companion object {
        const val DATABASE_NAME = "melete.db"

        fun build(context: Context, name: String = DATABASE_NAME): MeleteDatabase =
            Room.databaseBuilder(context, MeleteDatabase::class.java, name)
                .addMigrations(*MeleteMigrations.ALL)
                .build()
    }
}
