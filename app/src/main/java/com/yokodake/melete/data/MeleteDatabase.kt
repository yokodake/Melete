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
import com.yokodake.melete.data.dao.RoutineDao
import com.yokodake.melete.data.dao.TrainingDao
import com.yokodake.melete.data.entity.ActualSetEntity
import com.yokodake.melete.data.entity.CircuitInstanceEntity
import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.PrescriptionEntity
import com.yokodake.melete.data.entity.RoutineEntity
import com.yokodake.melete.data.entity.RoutineEntryEntity
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
        RoutineEntity::class,
        RoutineEntryEntity::class,
        CircuitInstanceEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(MeleteConverters::class)
abstract class MeleteDatabase : RoomDatabase() {

    abstract fun trainingDao(): TrainingDao

    abstract fun libraryDao(): LibraryDao

    abstract fun loggingDao(): LoggingDao

    abstract fun routineDao(): RoutineDao

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

        /**
         * Adds the explanation and the category. Three nullable columns and nothing else: an
         * exercise that had no category yesterday simply has none today, which is a valid state
         * rather than something to backfill with a guess.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `description` TEXT")
                connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `category` TEXT")
                connection.execSQL(
                    "ALTER TABLE `exercise_occurrences` ADD COLUMN `categorySnapshot` TEXT"
                )
            }
        }

        /**
         * Retiring an exercise from the library. One nullable column: everything that already
         * refers to an exercise keeps referring to it, so there is nothing to backfill and
         * nothing that can be lost.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `deletedAtEpochMs` INTEGER")
            }
        }

        /**
         * Phase 5A and the timer extensions: logged duration, one-off activities and circuits.
         *
         * All additive. Six nullable-or-defaulted columns on the occurrence and three new tables
         * that start empty, so every existing plan, log and session is left exactly as it was and
         * no reset is needed. An occurrence that had no recorded duration yesterday simply has
         * none today — a valid state rather than something to backfill with a guess.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                listOf(
                    "ALTER TABLE `exercise_occurrences` ADD COLUMN `loggedDurationSeconds` INTEGER",
                    "ALTER TABLE `exercise_occurrences` ADD COLUMN `loggedDurationManual` " +
                        "INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE `exercise_occurrences` ADD COLUMN `loggedEffort` TEXT",
                    "ALTER TABLE `exercise_occurrences` ADD COLUMN `isOneOff` " +
                        "INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE `exercise_occurrences` ADD COLUMN `circuitInstanceId` TEXT",
                    "ALTER TABLE `exercise_occurrences` ADD COLUMN `circuitPosition` INTEGER",
                ).forEach(connection::execSQL)

                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `routines` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `rounds` INTEGER NOT NULL,
                        `transitionSeconds` INTEGER NOT NULL,
                        `roundRestSeconds` INTEGER NOT NULL,
                        `structureVersion` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `routine_entries` (
                        `id` TEXT NOT NULL,
                        `routineId` TEXT NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `exerciseId` TEXT NOT NULL,
                        `exerciseNameSnapshot` TEXT NOT NULL,
                        `prescriptionId` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE ,
                        FOREIGN KEY(`prescriptionId`) REFERENCES `prescriptions`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `circuit_instances` (
                        `id` TEXT NOT NULL,
                        `routineId` TEXT NOT NULL,
                        `routineNameSnapshot` TEXT NOT NULL,
                        `structureVersion` INTEGER NOT NULL,
                        `structureSnapshotJson` TEXT NOT NULL,
                        `weekStartEpochDay` INTEGER NOT NULL,
                        `trainingDateEpochDay` INTEGER,
                        `orderIndex` INTEGER NOT NULL,
                        `rounds` INTEGER NOT NULL,
                        `transitionSeconds` INTEGER NOT NULL,
                        `roundRestSeconds` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                listOf(
                    "CREATE INDEX IF NOT EXISTS `index_routine_entries_routineId` " +
                        "ON `routine_entries` (`routineId`)",
                    "CREATE INDEX IF NOT EXISTS `index_routine_entries_prescriptionId` " +
                        "ON `routine_entries` (`prescriptionId`)",
                    "CREATE INDEX IF NOT EXISTS `index_routine_entries_exerciseId` " +
                        "ON `routine_entries` (`exerciseId`)",
                    "CREATE INDEX IF NOT EXISTS `index_circuit_instances_weekStartEpochDay` " +
                        "ON `circuit_instances` (`weekStartEpochDay`)",
                    "CREATE INDEX IF NOT EXISTS `index_circuit_instances_trainingDateEpochDay` " +
                        "ON `circuit_instances` (`trainingDateEpochDay`)",
                    "CREATE INDEX IF NOT EXISTS `index_circuit_instances_routineId` " +
                        "ON `circuit_instances` (`routineId`)",
                ).forEach(connection::execSQL)
            }
        }

        /**
         * The categories grew from three to six, and two of the three were renamed.
         *
         * Enum constants are stored by name, so a rename is a data change even though no column
         * moves: `MeleteConverters` reads an unknown name as "no category", which would have
         * quietly stripped the colour off every exercise and every snapshot that had one. This
         * rewrites them instead.
         *
         * `OPEN` becomes `OPEN_CLIMBING` and `CONDITIONING` becomes `STRENGTH_CONDITIONING`,
         * which is the closest each one maps to. That is a narrowing — an `OPEN` row that meant
         * an open *lifting* session now reads as climbing — so it is worth glancing over the
         * library afterwards. `FLEXIBILITY` is unchanged, and the three new categories start
         * empty because nothing could have been filed under them yet.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                listOf(
                    "exercises" to "category",
                    "exercise_occurrences" to "categorySnapshot",
                ).forEach { (table, column) ->
                    listOf(
                        "OPEN" to "OPEN_CLIMBING",
                        "CONDITIONING" to "STRENGTH_CONDITIONING",
                    ).forEach { (from, to) ->
                        connection.execSQL(
                            "UPDATE `$table` SET `$column` = '$to' WHERE `$column` = '$from'"
                        )
                    }
                }
            }
        }

        val MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun build(context: Context): MeleteDatabase =
            Room.databaseBuilder(context, MeleteDatabase::class.java, DATABASE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
