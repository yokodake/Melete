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
 * The upgrade path must never be a reset: a phone that logged training on an earlier schema has to
 * come out of the upgrade with the same rows.
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
    fun migrate2To3KeepsExistingRowsAndLeavesTheNewColumnsEmpty() {
        val exerciseId = "exercise-2"
        val prescriptionId = "prescription-2"
        val occurrenceId = "occurrence-2"

        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO prescriptions (id, payloadVersion, payloadJson, createdAtEpochMs, isSampleData) " +
                    "VALUES ('$prescriptionId', 2, '{\"sets\":3,\"targetReps\":10}', 1000, 0)"
            )
            db.execSQL(
                "INSERT INTO exercises (id, name, mode, measurementUnit, measurementMeaning, " +
                    "unilateral, notes, defaultPrescriptionId, createdAtEpochMs, isSampleData) " +
                    "VALUES ('$exerciseId', 'Couch stretch', 'DURATION', NULL, NULL, 1, " +
                    "'knee to the wall', '$prescriptionId', 1000, 0)"
            )
            db.execSQL(
                "INSERT INTO exercise_occurrences (id, weekStartEpochDay, trainingDateEpochDay, " +
                    "exerciseId, exerciseNameSnapshot, modeSnapshot, unilateralSnapshot, " +
                    "measurementUnitSnapshot, measurementMeaningSnapshot, prescriptionId, " +
                    "orderIndex, state, comment, createdAtEpochMs, isSampleData) " +
                    "VALUES ('$occurrenceId', 20718, 20719, '$exerciseId', 'Couch stretch', " +
                    "'DURATION', 1, NULL, NULL, '$prescriptionId', 0, 'PLANNED', 'keep me', 1000, 0)"
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            MeleteDatabase.MIGRATION_2_3,
        ).use { db ->
            db.query(
                "SELECT name, notes, description, category FROM exercises WHERE id = '$exerciseId'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Couch stretch", cursor.getString(0))
                // The upgrade is not allowed to lose what was there...
                assertEquals("knee to the wall", cursor.getString(1))
                // ...and an exercise that had no category yesterday genuinely has none, rather
                // than being backfilled with a guess.
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
            }
            db.query(
                "SELECT comment, categorySnapshot FROM exercise_occurrences WHERE id = '$occurrenceId'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("keep me", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
            db.query("SELECT payloadJson FROM prescriptions WHERE id = '$prescriptionId'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("{\"sets\":3,\"targetReps\":10}", cursor.getString(0))
                }
        }
    }

    @Test
    fun migrate3To4RetiresNothingAndKeepsEverything() {
        val exerciseId = "exercise-3"
        val prescriptionId = "prescription-3"

        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO prescriptions (id, payloadVersion, payloadJson, createdAtEpochMs, isSampleData) " +
                    "VALUES (\'$prescriptionId\', 2, \'{\"sets\":5}\', 1000, 0)"
            )
            db.execSQL(
                "INSERT INTO exercises (id, name, mode, measurementUnit, measurementMeaning, " +
                    "unilateral, notes, defaultPrescriptionId, createdAtEpochMs, isSampleData, " +
                    "description, category) " +
                    "VALUES (\'$exerciseId\', \'Max hangs\', \'DURATION\', \'kg\', \'ADDED_LOAD\', 0, " +
                    "NULL, \'$prescriptionId\', 1000, 0, \'half crimp\', \'CONDITIONING\')"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, MeleteDatabase.MIGRATION_3_4).use { db ->
            db.query(
                "SELECT name, description, category, deletedAtEpochMs FROM exercises WHERE id = \'$exerciseId\'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Max hangs", cursor.getString(0))
                assertEquals("half crimp", cursor.getString(1))
                assertEquals("CONDITIONING", cursor.getString(2))
                // Every existing exercise is still in the library: the upgrade retires nothing.
                assertTrue(cursor.isNull(3))
            }
        }
    }

    @Test
    fun migrate4To5KeepsTheRecordAndAddsTimeActivitiesAndCircuits() {
        val exerciseId = "exercise-5"
        val prescriptionId = "prescription-5"
        val occurrenceId = "occurrence-5"
        val sessionId = "session-5"

        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO prescriptions (id, payloadVersion, payloadJson, createdAtEpochMs, isSampleData) " +
                    "VALUES (\'$prescriptionId\', 2, \'{\"sets\":5}\', 1000, 0)"
            )
            db.execSQL(
                "INSERT INTO exercises (id, name, mode, measurementUnit, measurementMeaning, " +
                    "unilateral, notes, defaultPrescriptionId, createdAtEpochMs, isSampleData, " +
                    "description, category, deletedAtEpochMs) " +
                    "VALUES (\'$exerciseId\', \'Max hangs\', \'DURATION\', \'kg\', \'ADDED_LOAD\', 0, " +
                    "NULL, \'$prescriptionId\', 1000, 0, NULL, \'CONDITIONING\', NULL)"
            )
            db.execSQL(
                "INSERT INTO exercise_occurrences (id, weekStartEpochDay, trainingDateEpochDay, " +
                    "exerciseId, exerciseNameSnapshot, modeSnapshot, unilateralSnapshot, " +
                    "measurementUnitSnapshot, measurementMeaningSnapshot, prescriptionId, " +
                    "orderIndex, state, comment, createdAtEpochMs, isSampleData, categorySnapshot) " +
                    "VALUES (\'$occurrenceId\', 20718, 20719, \'$exerciseId\', \'Max hangs\', " +
                    "\'DURATION\', 0, \'kg\', \'ADDED_LOAD\', \'$prescriptionId\', 0, " +
                    "\'COMPLETED\', \'felt strong\', 1000, 0, \'CONDITIONING\')"
            )
            db.execSQL(
                "INSERT INTO training_sessions (id, trainingDateEpochDay, ordinal, createdAtEpochMs) " +
                    "VALUES (\'$sessionId\', 20719, 0, 1000)"
            )
            db.execSQL(
                "INSERT INTO actual_sets (id, occurrenceId, sessionId, exerciseId, " +
                    "trainingDateEpochDay, prescriptionId, orderIndex, side, payloadVersion, " +
                    "payloadJson, recordedAtEpochMs) " +
                    "VALUES (\'set-5\', \'$occurrenceId\', \'$sessionId\', \'$exerciseId\', " +
                    "20719, \'$prescriptionId\', 0, NULL, 2, \'{\"reps\":null}\', 2000)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, MeleteDatabase.MIGRATION_4_5).use { db ->
            db.query(
                "SELECT comment, state, loggedDurationSeconds, loggedDurationManual, " +
                    "loggedEffort, isOneOff, circuitInstanceId, circuitPosition " +
                    "FROM exercise_occurrences WHERE id = \'$occurrenceId\'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                // Everything that was there is still there, word for word.
                assertEquals("felt strong", cursor.getString(0))
                assertEquals("COMPLETED", cursor.getString(1))
                // And nothing is backfilled with a guess: unknown time stays unknown.
                assertTrue(cursor.isNull(2))
                assertEquals(0, cursor.getInt(3))
                assertTrue(cursor.isNull(4))
                assertEquals(0, cursor.getInt(5))
                assertTrue(cursor.isNull(6))
                assertTrue(cursor.isNull(7))
            }
            db.query("SELECT COUNT(*) FROM actual_sets").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            // The new tables exist and start empty.
            listOf("routines", "routine_entries", "circuit_instances").forEach { table ->
                db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
        }
    }

    @Test
    fun migrate5To6RenamesTheCategoriesRatherThanLosingThem() {
        val exerciseId = "exercise-6"
        val prescriptionId = "prescription-6"
        val occurrenceId = "occurrence-6"

        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO prescriptions (id, payloadVersion, payloadJson, createdAtEpochMs, isSampleData) " +
                    "VALUES ('$prescriptionId', 3, '{\"sets\":3}', 1000, 0)"
            )
            listOf(
                "ex-open" to "OPEN",
                "ex-cond" to "CONDITIONING",
                "ex-flex" to "FLEXIBILITY",
                "ex-none" to null,
            ).forEach { (id, category) ->
                val value = category?.let { "'$it'" } ?: "NULL"
                db.execSQL(
                    "INSERT INTO exercises (id, name, mode, measurementUnit, measurementMeaning, " +
                        "unilateral, notes, defaultPrescriptionId, createdAtEpochMs, isSampleData, " +
                        "description, category, deletedAtEpochMs) " +
                        "VALUES ('$id', '$id', 'DURATION', NULL, NULL, 0, NULL, " +
                        "'$prescriptionId', 1000, 0, NULL, $value, NULL)"
                )
            }
            db.execSQL(
                "INSERT INTO exercise_occurrences (id, weekStartEpochDay, trainingDateEpochDay, " +
                    "exerciseId, exerciseNameSnapshot, modeSnapshot, unilateralSnapshot, " +
                    "measurementUnitSnapshot, measurementMeaningSnapshot, prescriptionId, " +
                    "orderIndex, state, comment, createdAtEpochMs, isSampleData, categorySnapshot, " +
                    "loggedDurationSeconds, loggedDurationManual, loggedEffort, isOneOff, " +
                    "circuitInstanceId, circuitPosition) " +
                    "VALUES ('$occurrenceId', 20718, 20719, '$exerciseId', 'Bouldering', " +
                    "'ACTIVITY', 0, NULL, NULL, '$prescriptionId', 0, 'COMPLETED', NULL, 1000, 0, " +
                    "'OPEN', NULL, 0, NULL, 0, NULL, NULL)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, MeleteDatabase.MIGRATION_5_6).use { db ->
            db.query("SELECT id, category FROM exercises ORDER BY id").use { cursor ->
                val found = buildMap {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(0), if (cursor.isNull(1)) null else cursor.getString(1))
                    }
                }
                // Renamed where the meaning carries over, untouched where it does not, and an
                // exercise that never had a category still has none.
                assertEquals("OPEN_CLIMBING", found["ex-open"])
                assertEquals("STRENGTH_CONDITIONING", found["ex-cond"])
                assertEquals("FLEXIBILITY", found["ex-flex"])
                assertNull(found["ex-none"])
            }
            db.query(
                "SELECT categorySnapshot FROM exercise_occurrences WHERE id = '$occurrenceId'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                // The snapshot is rewritten too, or history would lose its colour.
                assertEquals("OPEN_CLIMBING", cursor.getString(0))
            }
        }
    }

    @Test
    fun theProductionBuilderCarriesEveryMigration() {
        assertEquals(5, MeleteDatabase.MIGRATIONS.size)
        assertNull(
            MeleteDatabase.MIGRATIONS.firstOrNull { it.startVersion >= it.endVersion }
        )
    }

    private companion object {
        const val TEST_DB = "melete-migration-test.db"
    }
}
