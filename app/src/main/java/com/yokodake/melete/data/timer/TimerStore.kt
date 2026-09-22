package com.yokodake.melete.data.timer

import android.content.Context
import android.provider.Settings
import kotlinx.serialization.json.Json

/**
 * Durable timer state and settings.
 *
 * SharedPreferences rather than Room: this is one small record that must be readable synchronously
 * from a broadcast receiver on a cold process, and it is not part of the training diary.
 */
class TimerStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences("timer", Context.MODE_PRIVATE)

    private val contentResolver = context.applicationContext.contentResolver

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Number of times the device has booted. The value is only ever compared with a previously
     * stored one, so a device that does not expose it degrades to "never detect a reboot" rather
     * than to a wrong answer; in that case a stale running countdown is still caught by its
     * deadline having passed.
     */
    val bootCount: Int
        get() = runCatching {
            Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrDefault(0)

    fun readSnapshot(): TimerSnapshot? {
        val raw = preferences.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { json.decodeFromString<TimerSnapshot>(raw) }.getOrNull()
    }

    fun writeSnapshot(snapshot: TimerSnapshot?) {
        preferences.edit().apply {
            if (snapshot == null) {
                remove(KEY_SNAPSHOT)
            } else {
                putString(KEY_SNAPSHOT, json.encodeToString(snapshot))
            }
        }.apply()
    }

    /**
     * Test and development hook: the timer settings belong to the user, so anything that changes
     * them for its own purposes has to be able to put them back.
     */
    fun snapshotSettings(): Triple<CueSettings, Int, Int> =
        Triple(cueSettings, lastRestSeconds, lastWorkSeconds)

    fun restoreSettings(saved: Triple<CueSettings, Int, Int>) {
        cueSettings = saved.first
        lastRestSeconds = saved.second
        lastWorkSeconds = saved.third
    }

    var cueSettings: CueSettings
        get() = CueSettings(
            thirtySecondWarning = preferences.getBoolean(KEY_THIRTY_SECONDS, true),
            finalCountdown = preferences.getBoolean(KEY_FINAL_COUNTDOWN, true),
            quarterCues = preferences.getBoolean(KEY_QUARTER_CUES, true),
        )
        set(value) {
            preferences.edit()
                .putBoolean(KEY_THIRTY_SECONDS, value.thirtySecondWarning)
                .putBoolean(KEY_FINAL_COUNTDOWN, value.finalCountdown)
                .putBoolean(KEY_QUARTER_CUES, value.quarterCues)
                .apply()
        }

    var lastRestSeconds: Int
        get() = preferences.getInt(KEY_LAST_REST, DEFAULT_REST_SECONDS)
        set(value) = preferences.edit().putInt(KEY_LAST_REST, value).apply()

    /**
     * Coerced on the way out, not just on the way in. An earlier build stored a repeater program's
     * empty [TimerEntry.workSeconds] here, and a zero read back as a work length the create screen
     * could not start from — so a value that cannot be a length is treated as no value at all.
     */
    var lastWorkSeconds: Int
        get() = preferences.getInt(KEY_LAST_WORK, DEFAULT_WORK_SECONDS)
            .takeIf { it > 0 } ?: DEFAULT_WORK_SECONDS
        set(value) = preferences.edit().putInt(KEY_LAST_WORK, value).apply()

    /** How many sets the last program had, so the create screen opens on a familiar number. */
    var lastSets: Int
        get() = preferences.getInt(KEY_LAST_SETS, DEFAULT_SETS)
        set(value) = preferences.edit().putInt(KEY_LAST_SETS, value).apply()

    /**
     * The rest of the last hand-built program's shape.
     *
     * Remembered for the same reason the set count is: the create screen is used mid-session with
     * chalk on your hands, and retyping six-sevens-and-threes every time you want another set of
     * repeaters is exactly the friction this app exists to remove.
     */
    var lastUnilateral: Boolean
        get() = preferences.getBoolean(KEY_LAST_UNILATERAL, false)
        set(value) = preferences.edit().putBoolean(KEY_LAST_UNILATERAL, value).apply()

    var lastSideSwitchSeconds: Int
        get() = preferences.getInt(KEY_LAST_SIDE_SWITCH, TimerProgram.DEFAULT_SIDE_SWITCH_SECONDS)
        set(value) = preferences.edit().putInt(KEY_LAST_SIDE_SWITCH, value).apply()

    var lastRepeaterReps: Int
        get() = preferences.getInt(KEY_LAST_REPEATER_REPS, DEFAULT_REPEATER_REPS)
        set(value) = preferences.edit().putInt(KEY_LAST_REPEATER_REPS, value).apply()

    var lastRepeaterWorkSeconds: Int
        get() = preferences.getInt(KEY_LAST_REPEATER_WORK, DEFAULT_REPEATER_WORK)
        set(value) = preferences.edit().putInt(KEY_LAST_REPEATER_WORK, value).apply()

    var lastRepeaterRestSeconds: Int
        get() = preferences.getInt(KEY_LAST_REPEATER_REST, DEFAULT_REPEATER_REST)
        set(value) = preferences.edit().putInt(KEY_LAST_REPEATER_REST, value).apply()

    private companion object {
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_THIRTY_SECONDS = "cue-thirty-seconds"
        const val KEY_FINAL_COUNTDOWN = "cue-final-countdown"
        const val KEY_QUARTER_CUES = "cue-quarters"
        const val KEY_LAST_REST = "last-rest-seconds"
        const val KEY_LAST_WORK = "last-work-seconds"
        const val KEY_LAST_SETS = "last-sets"
        const val KEY_LAST_UNILATERAL = "last-unilateral"
        const val KEY_LAST_SIDE_SWITCH = "last-side-switch-seconds"
        const val KEY_LAST_REPEATER_REPS = "last-repeater-reps"
        const val KEY_LAST_REPEATER_WORK = "last-repeater-work-seconds"
        const val KEY_LAST_REPEATER_REST = "last-repeater-rest-seconds"
        const val DEFAULT_REST_SECONDS = 180
        const val DEFAULT_WORK_SECONDS = 10
        const val DEFAULT_SETS = 3
        // The classic hangboard repeater, which is what anyone reaching for this is most likely
        // to want: six seven-second efforts with three seconds between them.
        const val DEFAULT_REPEATER_REPS = 6
        const val DEFAULT_REPEATER_WORK = 7
        const val DEFAULT_REPEATER_REST = 3
    }
}
