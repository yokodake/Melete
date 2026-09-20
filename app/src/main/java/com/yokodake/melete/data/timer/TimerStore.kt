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

    /** Cues already delivered, so the in-process path and the alarm path cannot both sound. */
    fun markCueDelivered(runId: String, cue: TimerCue): Boolean {
        val key = "$KEY_CUE_PREFIX$runId:${cue.name}"
        synchronized(this) {
            if (preferences.getBoolean(key, false)) return false
            preferences.edit().putBoolean(key, true).commit()
            return true
        }
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

    /** Forgets the cue bookkeeping for finished or cancelled runs. */
    fun clearCues(runId: String) {
        val editor = preferences.edit()
        TimerCue.entries.forEach { editor.remove("$KEY_CUE_PREFIX$runId:${it.name}") }
        editor.apply()
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

    var lastWorkSeconds: Int
        get() = preferences.getInt(KEY_LAST_WORK, DEFAULT_WORK_SECONDS)
        set(value) = preferences.edit().putInt(KEY_LAST_WORK, value).apply()

    private companion object {
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_CUE_PREFIX = "cue:"
        const val KEY_THIRTY_SECONDS = "cue-thirty-seconds"
        const val KEY_FINAL_COUNTDOWN = "cue-final-countdown"
        const val KEY_QUARTER_CUES = "cue-quarters"
        const val KEY_LAST_REST = "last-rest-seconds"
        const val KEY_LAST_WORK = "last-work-seconds"
        const val DEFAULT_REST_SECONDS = 180
        const val DEFAULT_WORK_SECONDS = 10
    }
}
