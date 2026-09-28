package com.yokodake.melete.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema step since the baseline, in order.
 *
 * Schema **10** is the baseline (2026-09-28): the first version holding a record worth keeping,
 * and the oldest one any installed app can have. From here, a change to an entity means:
 *
 * 1. Bump `version` in [MeleteDatabase] and build once, so Room exports the new `N.json` beside
 *    the old ones in `app/schemas`. **Never delete an exported schema** — the migration tests
 *    open databases at those versions.
 * 2. Add a `Migration(N - 1, N)` here that carries the data across, in SQL.
 * 3. Run `MigrationTest`: it builds a database at every exported version from the baseline and
 *    checks it reaches the current one intact.
 *
 * There is no destructive fallback in either build. A missing migration fails on open instead of
 * emptying the database — in Melete Debug first, since it takes the same upgrade path.
 */
object MeleteMigrations {

    const val BASELINE = 10

    /** The schema the app is built for; [MeleteDatabase] declares it from here. */
    const val CURRENT = 11

    /**
     * 10 → 11, phase 7A: benchmarks and their results, two new tables. Nothing existing changes,
     * so every row of the record is carried over untouched.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `benchmarks` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`measure` TEXT NOT NULL, `unit` TEXT NOT NULL, `loadMeaning` TEXT, " +
                    "`unilateral` INTEGER NOT NULL, `higherIsBetter` INTEGER NOT NULL, `protocol` TEXT, " +
                    "`goal` TEXT, `orderIndex` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, " +
                    "`hiddenAtEpochMs` INTEGER, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `benchmark_results` (`id` TEXT NOT NULL, " +
                    "`benchmarkId` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `value` REAL, " +
                    "`valueRight` REAL, `textValue` TEXT, `bodyweightPercent` REAL, " +
                    "`unitSnapshot` TEXT NOT NULL, `loadMeaningSnapshot` TEXT, " +
                    "`unilateralSnapshot` INTEGER NOT NULL, `note` TEXT, " +
                    "`recordedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`benchmarkId`) REFERENCES `benchmarks`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_benchmark_results_benchmarkId` " +
                    "ON `benchmark_results` (`benchmarkId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_benchmark_results_dateEpochDay` " +
                    "ON `benchmark_results` (`dateEpochDay`)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_10_11)
}
