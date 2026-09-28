package com.yokodake.melete.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades keep the record. Every exported schema from the baseline on is built as a real
 * database and migrated to the current one, and the app itself opens a baseline database with
 * its rows intact — through the same builder the app uses, which has no destructive fallback.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val name = "melete-migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MeleteDatabase::class.java)

    @After
    fun tearDown() {
        instrumentation.targetContext.deleteDatabase(name)
    }

    @Test
    fun everyExportedSchemaMigratesToTheCurrentOne() {
        for (version in MeleteMigrations.BASELINE..MeleteMigrations.CURRENT) {
            helper.createDatabase(name, version).close()
            // Validates the migrated schema against the current one, table by table.
            helper.runMigrationsAndValidate(name, MeleteMigrations.CURRENT, true, *MeleteMigrations.ALL).close()
            instrumentation.targetContext.deleteDatabase(name)
        }
    }

    @Test
    fun theAppOpensABaselineDatabaseWithItsRowsIntact() = runBlocking<Unit> {
        helper.createDatabase(name, MeleteMigrations.BASELINE).apply {
            execSQL(
                "INSERT INTO exercises (id, name, mode, measurementUnit, measurementMeaning, unilateral, " +
                    "createdAtEpochMs) VALUES ('ex-1', 'Pull-up', 'REPETITIONS', 'kg', 'ADDED_LOAD', 0, 1)"
            )
            close()
        }
        val database = MeleteDatabase.build(instrumentation.targetContext, name)
        try {
            val library = TrainingRepository(database).observeLibrary().first()
            assertEquals(listOf("Pull-up"), library.map { it.name })
        } finally {
            database.close()
        }
    }
}
