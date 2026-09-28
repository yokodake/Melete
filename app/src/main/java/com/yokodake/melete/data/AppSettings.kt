package com.yokodake.melete.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** When the screen is held on. */
enum class KeepAwake(val label: String) {
    /** Only while a countdown is running: the long-standing behaviour. */
    WHILE_TIMING("While timing"),

    /** Whenever the app is in front: reading instructions, logging between sets. */
    ALWAYS("Always"),
}

/**
 * The app's preferences: how it behaves, never what was trained. Kept in SharedPreferences, apart
 * from the database, so they are not in backups and a restore never changes them.
 *
 * Bodyweight tracking is deliberately not here: it is a diary tracker, part of the record, and
 * [DiaryRepository] owns it.
 */
class AppSettings(context: Context) {

    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _keepAwake = MutableStateFlow(
        KeepAwake.entries.firstOrNull { it.name == preferences.getString(KEEP_AWAKE, null) } ?: KeepAwake.WHILE_TIMING
    )
    val keepAwake: StateFlow<KeepAwake> = _keepAwake.asStateFlow()

    private val _benchmarkReminderMonths = MutableStateFlow(preferences.getInt(REMINDER_MONTHS, 6))

    /** How old a benchmark's latest result is before Home reminds; 0 is off. */
    val benchmarkReminderMonths: StateFlow<Int> = _benchmarkReminderMonths.asStateFlow()

    fun setKeepAwake(value: KeepAwake) {
        preferences.edit().putString(KEEP_AWAKE, value.name).apply()
        _keepAwake.value = value
    }

    fun setBenchmarkReminderMonths(months: Int) {
        preferences.edit().putInt(REMINDER_MONTHS, months).apply()
        _benchmarkReminderMonths.value = months
    }

    companion object {
        private const val KEEP_AWAKE = "keepAwake"
        private const val REMINDER_MONTHS = "benchmarkReminderMonths"

        /** What the reminder can be set to: off, or months. */
        val REMINDER_CHOICES = listOf(0, 3, 6, 12)
    }
}
