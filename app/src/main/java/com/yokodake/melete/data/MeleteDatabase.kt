package com.yokodake.melete.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.yokodake.melete.data.dao.LibraryDao
import com.yokodake.melete.data.dao.LoggingDao
import com.yokodake.melete.data.dao.TrainingDao
import com.yokodake.melete.data.entity.ActualSetEntity
import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.PrescriptionEntity
import com.yokodake.melete.data.entity.TrainingSessionEntity

/**
 * The training record. There is deliberately no destructive-migration fallback: this database is
 * the only copy of the user's training history, so a missing migration must fail loudly in
 * development rather than silently wipe real data on a phone.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        PrescriptionEntity::class,
        ExerciseOccurrenceEntity::class,
        TrainingSessionEntity::class,
        ActualSetEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(MeleteConverters::class)
abstract class MeleteDatabase : RoomDatabase() {

    abstract fun trainingDao(): TrainingDao

    abstract fun libraryDao(): LibraryDao

    abstract fun loggingDao(): LoggingDao

    companion object {
        const val DATABASE_NAME = "melete.db"

        /**
         * Phase 2 adds sessions and actual sets, plus the load-meaning and notes fields the
         * exercise editor writes. Existing rows are preserved: new columns are nullable and the
         * new tables start empty.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `measurementMeaning` TEXT")
                connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `notes` TEXT")
                connection.execSQL(
                    "ALTER TABLE `exercise_occurrences` ADD COLUMN `measurementMeaningSnapshot` TEXT"
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `training_sessions` (
                        `id` TEXT NOT NULL,
                        `trainingDateEpochDay` INTEGER NOT NULL,
                        `ordinal` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_training_sessions_trainingDateEpochDay_ordinal` " +
                        "ON `training_sessions` (`trainingDateEpochDay`, `ordinal`)"
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `actual_sets` (
                        `id` TEXT NOT NULL,
                        `occurrenceId` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `exerciseId` TEXT NOT NULL,
                        `trainingDateEpochDay` INTEGER NOT NULL,
                        `prescriptionId` TEXT,
                        `orderIndex` INTEGER NOT NULL,
                        `side` TEXT,
                        `payloadVersion` INTEGER NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `recordedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`occurrenceId`) REFERENCES `exercise_occurrences`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT ,
                        FOREIGN KEY(`sessionId`) REFERENCES `training_sessions`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT ,
                        FOREIGN KEY(`prescriptionId`) REFERENCES `prescriptions`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                listOf(
                    "occurrenceId",
                    "sessionId",
                    "prescriptionId",
                    "exerciseId",
                    "trainingDateEpochDay",
                ).forEach { column ->
                    connection.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_actual_sets_$column` " +
                            "ON `actual_sets` (`$column`)"
                    )
                }
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): MeleteDatabase =
            Room.databaseBuilder(context, MeleteDatabase::class.java, DATABASE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
