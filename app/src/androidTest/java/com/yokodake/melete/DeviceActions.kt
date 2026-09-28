package com.yokodake.melete

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.MeleteDatabase
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.backup.BackupService
import com.yokodake.melete.data.plan.ImportMode
import com.yokodake.melete.data.plan.ImportScope
import com.yokodake.melete.data.plan.PlanImporter
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Not a test: runs what the Backup & restore screen runs, against the real app, from adb — for
 * trying an import or putting back a recovery copy without tapping through the screens. Each
 * step says what it did on the instrumentation output. Skipped unless asked for:
 *
 *     adb shell am instrument -w -e action restore -e copy melete-before-restore-….json \
 *         -e class com.yokodake.melete.DeviceActions com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner
 *     adb shell am instrument -w -e action import -e mode add|replace -e scope today|past [-e dry true] \
 *         -e class com.yokodake.melete.DeviceActions com.yokodake.melete.debug.test/androidx.test.runner.AndroidJUnitRunner
 *
 * `restore` reads a copy from the app's recovery copies; `import` reads the test library plan.
 * Both save a recovery copy first, exactly as the screen does. `dry` only prints the preview.
 */
@RunWith(AndroidJUnit4::class)
class DeviceActions {

    @Test
    fun run() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        val action = arguments.getString("action")
        assumeTrue(action != null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "com.yokodake.melete.debug") {
            "Device actions may only modify Melete Debug."
        }
        val safety = File(context.filesDir, "backups")
        fun say(text: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$text\n") })

        val database = MeleteDatabase.build(context)
        try {
            val backups = BackupService(database)
            when (action) {
                "restore" -> {
                    val copy = File(safety, requireNotNull(arguments.getString("copy")) { "-e copy <file>" })
                    val backup = backups.decode(copy.readText())
                    backups.writeSafetyCopy(safety)
                    backups.restore(backup)
                    say("Restored ${copy.name}: ${backups.summarise(backup)}")
                }
                "import" -> {
                    val importer = PlanImporter(database, TrainingRepository(database), backups)
                    val text = instrumentation.context.assets.open("test-library.json").use { it.readBytes().decodeToString() }
                    val file = importer.decode(text)
                    val mode = if (arguments.getString("mode") == "replace") ImportMode.REPLACE else ImportMode.ADD
                    val scope = if (arguments.getString("scope") == "past") ImportScope.INCLUDE_PAST else ImportScope.FROM_TODAY
                    val check = if (arguments.getString("dry") == "true") {
                        importer.check(file, mode, scope)
                    } else {
                        importer.import(file, mode, scope, safety)
                    }
                    val resolution = check.resolution
                    say("$mode / $scope${if (arguments.getString("dry") == "true") " (preview only)" else ""}")
                    say("  problems: ${resolution.problems}")
                    say("  warnings: ${resolution.warnings.size}")
                    say("  preview: ${resolution.preview}")
                    check.replace?.let { say("  replace: $it") }
                }
                else -> error("unknown action $action")
            }
        } finally {
            database.close()
        }
    }
}
