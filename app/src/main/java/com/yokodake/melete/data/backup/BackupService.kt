package com.yokodake.melete.data.backup

import androidx.room.withTransaction
import com.yokodake.melete.data.MeleteDatabase
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A file that could not be read as a backup at all, with the reason in words. */
class BackupUnreadable(message: String) : Exception(message)

/** How much a backup holds, for saying what a restore is about to put back. */
data class BackupSummary(
    val exportedAt: String,
    val exercises: Int,
    val circuits: Int,
    val modules: Int,
    val planned: Int,
    val loggedSets: Int,
    val diaryDays: Int,
)

/**
 * Export and restore of the whole training record.
 *
 * Its own class, apart from the training repository, reading the tables directly: an export is a
 * copy of the database, not of what any screen shows. A restore is not a merge — it replaces the
 * record, in one transaction, and only after the file has been validated whole.
 */
class BackupService(private val database: MeleteDatabase) {

    private val dao = database.backupDao()

    /** Everything, read in one transaction so the file is a consistent moment of the record. */
    suspend fun export(now: Instant = Instant.now()): MeleteBackup = database.withTransaction {
        val routineEntries = dao.routineEntries().groupBy { it.routineId }
        val moduleEntries = dao.moduleEntries().groupBy { it.moduleId }
        val diaryValues = dao.diaryValues().groupBy { it.dateEpochDay }
        MeleteBackup(
            exportedAt = now.toString(),
            schemaVersion = database.openHelper.readableDatabase.version,
            exercises = dao.exercises().sortedBy { it.createdAtEpochMs }.map { it.toRecord() },
            variations = dao.variations().sortedBy { it.createdAtEpochMs }.map { it.toRecord() },
            routines = dao.routines().sortedBy { it.createdAtEpochMs }
                .map { it.toRecord(routineEntries[it.id].orEmpty()) },
            modules = dao.modules().sortedBy { it.createdAtEpochMs }
                .map { it.toRecord(moduleEntries[it.id].orEmpty()) },
            circuitInstances = dao.circuitInstances()
                .sortedWith(compareBy({ it.weekStartEpochDay }, { it.createdAtEpochMs }))
                .map { it.toRecord() },
            moduleInstances = dao.moduleInstances()
                .sortedWith(compareBy({ it.weekStartEpochDay }, { it.createdAtEpochMs }))
                .map { it.toRecord() },
            occurrences = dao.occurrences()
                .sortedWith(compareBy({ it.weekStartEpochDay }, { it.createdAtEpochMs }))
                .map { it.toRecord() },
            sessions = dao.sessions().sortedWith(compareBy({ it.trainingDateEpochDay }, { it.ordinal }))
                .map { it.toRecord() },
            sets = dao.sets().sortedWith(compareBy({ it.trainingDateEpochDay }, { it.recordedAtEpochMs }))
                .map { it.toRecord() },
            trackers = dao.trackers().sortedBy { it.orderIndex }.map { it.toRecord() },
            diary = dao.diaryEntries().sortedBy { it.dateEpochDay }
                .map { it.toRecord(diaryValues[it.dateEpochDay].orEmpty()) },
        )
    }

    fun encode(backup: MeleteBackup): String = BackupJson.encodeToString(MeleteBackup.serializer(), backup)

    /** Reads a file's text as a backup, or says in words why it cannot be one. */
    fun decode(text: String): MeleteBackup =
        runCatching { BackupJson.decodeFromString(MeleteBackup.serializer(), text) }
            .getOrElse { throw BackupUnreadable("This file is not a readable Melete backup.") }

    fun summarise(backup: MeleteBackup) = BackupSummary(
        exportedAt = backup.exportedAt,
        exercises = backup.exercises.count { it.deletedAtEpochMs == null },
        circuits = backup.routines.count { it.deletedAtEpochMs == null },
        modules = backup.modules.count { it.deletedAtEpochMs == null },
        planned = backup.occurrences.size,
        loggedSets = backup.sets.size,
        diaryDays = backup.diary.size,
    )

    /**
     * Writes the current record to [directory] before anything replaces it, and returns the file.
     * A restore never runs without one: the only undo it has is this copy.
     */
    suspend fun writeSafetyCopy(directory: File, now: Instant = Instant.now()): File {
        directory.mkdirs()
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(now)
        val file = File(directory, "melete-before-restore-$stamp.json")
        file.writeText(encode(export(now)))
        return file
    }

    /**
     * Replaces the whole record with [backup], atomically.
     *
     * Refuses — throwing, with nothing changed — unless the file validates cleanly. Inside the
     * transaction the old record goes and the file's comes in; if any insert fails, the
     * transaction rolls back and the old record is exactly as it was.
     */
    suspend fun restore(backup: MeleteBackup) {
        val problems = BackupValidator.problems(backup)
        if (problems.isNotEmpty()) throw BackupUnreadable(problems.first())
        database.withTransaction {
            dao.clearDiaryValues()
            dao.clearDiaryEntries()
            dao.clearTrackers()
            dao.clearSets()
            dao.clearSessions()
            dao.clearOccurrences()
            dao.clearCircuitInstances()
            dao.clearModuleInstances()
            dao.clearRoutineEntries()
            dao.clearRoutines()
            dao.clearModuleEntries()
            dao.clearModules()
            dao.clearVariations()
            dao.clearExercises()

            // Parents before children.
            dao.insertExercises(backup.exercises.map { it.toEntity() })
            dao.insertVariations(backup.variations.map { it.toEntity() })
            dao.insertRoutines(backup.routines.map { it.toEntity() })
            dao.insertRoutineEntries(backup.routines.flatMap { it.entryEntities() })
            dao.insertModules(backup.modules.map { it.toEntity() })
            dao.insertModuleEntries(backup.modules.flatMap { it.entryEntities() })
            dao.insertModuleInstances(backup.moduleInstances.map { it.toEntity() })
            dao.insertCircuitInstances(backup.circuitInstances.map { it.toEntity() })
            dao.insertOccurrences(backup.occurrences.map { it.toEntity() })
            dao.insertSessions(backup.sessions.map { it.toEntity() })
            dao.insertSets(backup.sets.map { it.toEntity() })
            dao.insertTrackers(backup.trackers.map { it.toEntity() })
            dao.insertDiaryEntries(backup.diary.map { it.toEntity() })
            dao.insertDiaryValues(backup.diary.flatMap { it.valueEntities() })
        }
    }
}
