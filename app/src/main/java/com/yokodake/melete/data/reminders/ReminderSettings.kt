package com.yokodake.melete.data.reminders

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalTime

/** A time of day, kept as minutes since midnight so it serialises as one number. */
fun minuteOf(time: LocalTime): Int = time.hour * 60 + time.minute

fun timeOf(minuteOfDay: Int): LocalTime = LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60)

/** Every reminder, all off until turned on. */
@Serializable
data class ReminderConfig(
    val daily: DailyReminder = DailyReminder(),
    val weekly: WeeklyReminder = WeeklyReminder(),
    val trackers: List<TrackerReminder> = emptyList(),
)

/** "Anything to log?" at [minuteOfDay], when today still has work that is neither logged nor skipped. */
@Serializable
data class DailyReminder(
    val enabled: Boolean = false,
    val minuteOfDay: Int = 20 * 60 + 30,
)

/** A nudge to look back at the week, on [day] at [minuteOfDay]. */
@Serializable
data class WeeklyReminder(
    val enabled: Boolean = false,
    /** ISO day number: 1 is Monday, 7 Sunday. */
    val day: Int = DayOfWeek.SUNDAY.value,
    val minuteOfDay: Int = 19 * 60,
)

/** When a tracker reminder comes. */
@Serializable
enum class TrackerTrigger {
    /** On the chosen weekdays. */
    SCHEDULED,

    /** The day after matching work, logged or planned, that was not skipped. */
    AFTER_WORKOUT,
}

/** Which kind of library item a [WorkoutMatch] names. */
@Serializable
enum class WorkoutKind { EXERCISE, CIRCUIT, MODULE }

/** A library exercise, circuit or module, by id, with its name as it was when picked. */
@Serializable
data class WorkoutMatch(val kind: WorkoutKind, val id: String, val name: String)

/**
 * A reminder to fill in some trackers. It names the trackers and says [phrase]; it comes on the
 * chosen [days] or the day after matching work, and never for trackers already filled in.
 */
@Serializable
data class TrackerReminder(
    val id: String,
    val enabled: Boolean = true,
    val phrase: String = "",
    val trackerIds: List<String> = emptyList(),
    val minuteOfDay: Int = 9 * 60,
    val trigger: TrackerTrigger = TrackerTrigger.SCHEDULED,
    /** ISO day numbers, for [TrackerTrigger.SCHEDULED]. */
    val days: Set<Int> = emptySet(),
    /** Category names, for [TrackerTrigger.AFTER_WORKOUT]. */
    val categories: Set<String> = emptySet(),
    /** Exercises, circuits and modules, for [TrackerTrigger.AFTER_WORKOUT]. */
    val workouts: List<WorkoutMatch> = emptyList(),
)

/**
 * Where the reminders are kept: SharedPreferences, like the other settings, so they are not part
 * of a backup and a restore never changes them.
 */
class ReminderSettings(context: Context) {

    private val preferences = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<ReminderConfig> = _config.asStateFlow()

    fun update(change: (ReminderConfig) -> ReminderConfig) {
        val next = change(_config.value)
        preferences.edit().putString(CONFIG, json.encodeToString(ReminderConfig.serializer(), next)).apply()
        _config.value = next
    }

    /** The alarm keys set last time, so the ones no longer wanted can be cancelled. */
    var scheduledKeys: Set<String>
        get() = preferences.getStringSet(SCHEDULED, emptySet()).orEmpty()
        set(value) = preferences.edit().putStringSet(SCHEDULED, value).apply()

    private fun read(): ReminderConfig = preferences.getString(CONFIG, null)
        ?.let { runCatching { json.decodeFromString(ReminderConfig.serializer(), it) }.getOrNull() }
        ?: ReminderConfig()

    private companion object {
        const val CONFIG = "config"
        const val SCHEDULED = "scheduledKeys"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
