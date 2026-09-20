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

    /** Forgets the cue bookkeeping for finished or cancelled runs. */
    fun clearCues(runId: String) {
        val editor = preferences.edit()
        TimerCue.entries.forEach { editor.remove("$KEY_CUE_PREFIX$runId:${it.name}") }
        editor.apply()
    }

    var warningLeadSeconds: Int
        get() = preferences.getInt(KEY_WARNING_LEAD, DEFAULT_WARNING_LEAD_SECONDS)
        set(value) = preferences.edit().putInt(KEY_WARNING_LEAD, value).apply()

    var lastRestSeconds: Int
        get() = preferences.getInt(KEY_LAST_REST, DEFAULT_REST_SECONDS)
        set(value) = preferences.edit().putInt(KEY_LAST_REST, value).apply()

    var lastWorkSeconds: Int
        get() = preferences.getInt(KEY_LAST_WORK, DEFAULT_WORK_SECONDS)
        set(value) = preferences.edit().putInt(KEY_LAST_WORK, value).apply()

    private companion object {
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_CUE_PREFIX = "cue:"
        const val KEY_WARNING_LEAD = "warning-lead-seconds"
        const val KEY_LAST_REST = "last-rest-seconds"
        const val KEY_LAST_WORK = "last-work-seconds"
        const val DEFAULT_WARNING_LEAD_SECONDS = 10
        const val DEFAULT_REST_SECONDS = 180
        const val DEFAULT_WORK_SECONDS = 10
    }
}

enum class TimerCue {
    /** The advance warning, some seconds before the end. */
    WARNING,

    /** The countdown reaching zero. */
    FINISH,
}
