package com.yokodake.melete.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The upgrade path must never be a reset: a phone that logged training on schema 1 has to come out
 * of the phase 2 upgrade with the same rows.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeleteDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsExistingRowsAndAddsTheNewTables() {
        val exerciseId = "exercise-1"
        val prescriptionId = "prescription-1"
        val occurrenceId = "occurrence-1"

        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO prescriptions (id, payloadVersion, payloadJson, createdAtEpochMs, isSampleData) " +
                    "VALUES ('$prescriptionId', 1, '{\"sets\":4,\"targetReps\":8}', 1000, 0)"
            )
            db.execSQL(
                "INSERT INTO exercises (id, name, mode, measurementUnit, unilateral, " +
                    "defaultPrescriptionId, createdAtEpochMs, isSampleData) " +
                    "VALUES ('$exerciseId', 'Dumbbell row', 'REPETITIONS', 'kg', 1, " +
                    "'$prescriptionId', 1000, 0)"
            )
            db.execSQL(
                "INSERT INTO exercise_occurrences (id, weekStartEpochDay, trainingDateEpochDay, " +
                    "exerciseId, exerciseNameSnapshot, modeSnapshot, unilateralSnapshot, " +
                    "measurementUnitSnapshot, prescriptionId, orderIndex, state, comment, " +
                    "createdAtEpochMs, isSampleData) " +
                    "VALUES ('$occurrenceId', 20718, 20719, '$exerciseId', 'Dumbbell row', " +
                    "'REPETITIONS', 1, 'kg', '$prescriptionId', 0, 'PLANNED', 'keep me', 1000, 0)"
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            MeleteDatabase.MIGRATION_1_2,
        ).use { db ->
            db.query("SELECT name, notes, measurementMeaning FROM exercises WHERE id = '$exerciseId'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Dumbbell row", cursor.getString(0))
                    assertTrue(cursor.isNull(1))
                    assertTrue(cursor.isNull(2))
                }
            db.query("SELECT comment, measurementMeaningSnapshot FROM exercise_occurrences WHERE id = '$occurrenceId'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("keep me", cursor.getString(0))
                    assertTrue(cursor.isNull(1))
                }
            db.query("SELECT COUNT(*) FROM actual_sets").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM training_sessions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT payloadJson FROM prescriptions WHERE id = '$prescriptionId'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("{\"sets\":4,\"targetReps\":8}", cursor.getString(0))
                }
        }
    }

    @Test
    fun theProductionBuilderCarriesEveryMigration() {
        assertEquals(1, MeleteDatabase.MIGRATIONS.size)
        assertNull(
            MeleteDatabase.MIGRATIONS.firstOrNull { it.startVersion >= it.endVersion }
        )
    }

    private companion object {
        const val TEST_DB = "melete-migration-test.db"
    }
}
