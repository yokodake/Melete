package com.yokodake.melete.data

import com.yokodake.melete.data.backup.BackupJson
import com.yokodake.melete.data.backup.BackupValidator
import com.yokodake.melete.data.backup.MeleteBackup
import com.yokodake.melete.data.model.ActualSetJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Backups written today must restore in every later version of the app. `backup-v1.json` is a
 * real export, frozen when the format was declared stable (2026-09-28), holding every kind of row.
 * It is never regenerated: if this test fails, the change broke old backups — make the new code
 * read them, don't edit the fixture. A new format version gets a new fixture beside this one.
 */
class BackupFixtureTest {

    private val fixture = File("src/androidTest/assets/backup-v1.json")

    private fun read(): MeleteBackup = BackupJson.decodeFromString(MeleteBackup.serializer(), fixture.readText())

    @Test
    fun `the version 1 fixture still reads and validates`() {
        val backup = read()
        assertEquals(1, backup.formatVersion)
        assertEquals(emptyList<String>(), BackupValidator.problems(backup))
    }

    @Test
    fun `the fixture covers every kind of row`() {
        val backup = read()
        val lists = mapOf(
            "exercises" to backup.exercises, "variations" to backup.variations, "routines" to backup.routines,
            "modules" to backup.modules, "circuitInstances" to backup.circuitInstances,
            "moduleInstances" to backup.moduleInstances, "occurrences" to backup.occurrences,
            "sessions" to backup.sessions, "sets" to backup.sets, "trackers" to backup.trackers,
            "diary" to backup.diary,
        )
        lists.forEach { (name, rows) -> assertTrue("no $name in the fixture", rows.isNotEmpty()) }
        assertTrue(backup.exercises.any { it.deletedAtEpochMs != null })
        assertTrue(backup.diary.any { day -> day.values.isNotEmpty() })
    }

    @Test
    fun `its logged sets still read, a negative added load included`() {
        val loads = read().sets.map {
            ActualSetJson.decode(BackupJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), it.payload))
        }.mapNotNull { it.measurement?.value }
        assertTrue(loads.contains(-15.0))
        assertTrue(loads.any { it > 0 })
    }
}
