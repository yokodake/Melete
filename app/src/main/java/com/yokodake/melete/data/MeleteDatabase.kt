package com.yokodake.melete.data

import android.content.Context
import com.yokodake.melete.BuildConfig
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yokodake.melete.data.dao.BackupDao
import com.yokodake.melete.data.dao.DiaryDao
import com.yokodake.melete.data.dao.LibraryDao
import com.yokodake.melete.data.dao.LoggingDao
import com.yokodake.melete.data.dao.ModuleDao
import com.yokodake.melete.data.dao.RoutineDao
import com.yokodake.melete.data.dao.TrainingDao
import com.yokodake.melete.data.dao.VariationDao
import com.yokodake.melete.data.entity.ActualSetEntity
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
 * **One schema, no migration chain, until phase 6.** The version still has to move when the schema
 * does — Room refuses a changed schema under an unchanged number — but only the current schema is
 * exported and nothing migrates between them.
 * The app has never been installed by anyone
 * but its author and the database is disposable until the first real training block, so the five
 * migrations that carried it from v1 to v6 were ceremony: two hundred lines and six tests proving
 * that upgrades work for a record nobody would mind losing. They are gone, along with the schemas
 * they stepped through.
 *
 * What replaces them is a debug-only destructive fallback. A schema change now means the next
 * debug launch starts from an empty database, which takes seconds to refill. **The release build
 * has no fallback** and still fails loudly, because that is the build trained against, and a
 * silent wipe of a real diary is the one outcome worth crashing to avoid.
 *
 * Phase 6 is where this reverses: at that point the record becomes the stable baseline, the
 * fallback comes out, and every change needs a data-preserving migration again.
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
    ],
    version = 10,
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

    companion object {
        const val DATABASE_NAME = "melete.db"

        fun build(context: Context): MeleteDatabase =
            Room.databaseBuilder(context, MeleteDatabase::class.java, DATABASE_NAME)
                .apply {
                    // Debug only, and deliberately not a convenience: it is what lets the schema
                    // change freely before phase 6. The release build keeps no fallback at all.
                    if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true)
                }
                .build()
    }
}
