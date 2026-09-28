package com.yokodake.melete

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.MeleteDatabase
import com.yokodake.melete.data.OccurrenceLogWrite
import com.yokodake.melete.data.SetWrite
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.backup.BackupService
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.plan.ImportMode
import com.yokodake.melete.data.plan.ImportScope
import com.yokodake.melete.data.plan.PlanImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Not a test: imports the test library (`assets/test-library.json`, an ordinary plan file) into
 * the real app, adding to what is there, then logs a few of its first week's days — a plan file
 * holds no logs, and without some there is nothing to see an import keep or delete.
 *
 * It writes to the app's own database, so it is skipped unless asked for by name:
 *
 *     adb shell am instrument -w -e seed library \
 *         -e class com.yokodake.melete.LibrarySeed \
 *         com.yokodake.melete.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Pass `-e planning false` for the exercises alone, without circuits, modules, weeks or logs.
 * Re-running updates what the file names and adds what is missing; the weeks and logs are only
 * written when the first week is still empty, so they are never doubled. The same file can be
 * imported by hand from Backup & restore → Import a plan (where *Add* does plan the weeks again).
 */
@RunWith(AndroidJUnit4::class)
class LibrarySeed {

    private val firstWeek = LocalDate.of(2026, 9, 21)

    @Test
    fun seedLibrary() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("seed") == "library")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val text = instrumentation.context.assets.open("test-library.json").use { it.readBytes().decodeToString() }
        val database = MeleteDatabase.build(instrumentation.targetContext)
        try {
            val repository = TrainingRepository(database)
            val importer = PlanImporter(database, repository, BackupService(database))
            val planning = arguments.getString("planning") != "false"
            val weeksAlreadyThere = repository.observeWeek(firstWeek).first().isNotEmpty()
            val file = importer.decode(text).let {
                when {
                    !planning -> it.copy(circuits = emptyList(), modules = emptyList(), weeks = emptyList())
                    weeksAlreadyThere -> it.copy(weeks = emptyList())
                    else -> it
                }
            }
            importer.import(file, ImportMode.ADD, ImportScope.INCLUDE_PAST, File(instrumentation.targetContext.filesDir, "backups"))
            if (planning && !weeksAlreadyThere) logFirstWeek(repository)
        } finally {
            database.close()
        }
    }

    /** Monday and Friday trained, Tuesday's outing timed, Thursday's run skipped; the rest planned. */
    private suspend fun logFirstWeek(repository: TrainingRepository) {
        val week = repository.observeWeek(firstWeek).first()
            .filter { it.circuitInstanceId == null && it.moduleInstanceId == null }
        fun on(day: Long, name: String) = week.single { it.trainingDate == firstWeek.plusDays(day) && it.name == name }
        fun kg(value: Double, meaning: MeasurementMeaning) = Measurement(value, "kg", meaning)

        val monday = firstWeek
        repository.saveLogs(
            monday,
            listOf(
                OccurrenceLogWrite(
                    on(0, "Max hangs 20 mm").id, completed = true,
                    sets = List(5) { SetWrite(ActualSetPayload(durationSeconds = 10, measurement = kg(12.5, MeasurementMeaning.ADDED_LOAD)), null) },
                ),
                OccurrenceLogWrite(
                    on(0, "Back squat").id, completed = true,
                    sets = listOf(100.0, 100.0, 105.0, 105.0).map {
                        SetWrite(ActualSetPayload(reps = 5, measurement = kg(it, MeasurementMeaning.TOTAL_LOAD)), null)
                    },
                    comment = "Last set slow out of the hole.",
                ),
            ),
        )
        repository.saveLogs(
            firstWeek.plusDays(1),
            listOf(
                OccurrenceLogWrite(
                    on(1, "Outdoor bouldering").id, completed = true,
                    durationSeconds = 165 * 60, durationManual = true,
                ),
            ),
        )
        repository.setOccurrenceState(on(3, "Run").id, OccurrenceState.SKIPPED)
        repository.saveLogs(
            firstWeek.plusDays(4),
            listOf(
                OccurrenceLogWrite(
                    on(4, "Pull-up").id, completed = true,
                    sets = listOf(8, 8, 6).map { SetWrite(ActualSetPayload(reps = it), null) },
                ),
            ),
        )
    }
}
