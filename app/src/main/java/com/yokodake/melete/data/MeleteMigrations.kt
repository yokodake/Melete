package com.yokodake.melete.data

import androidx.room.migration.Migration

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
    const val CURRENT = 10

    val ALL: Array<Migration> = arrayOf()
}
