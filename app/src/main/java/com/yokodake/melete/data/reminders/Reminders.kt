package com.yokodake.melete.data.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yokodake.melete.MainActivity
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.R
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.DiaryRepository
import com.yokodake.melete.data.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where a tapped reminder opens. */
enum class OpenTarget { DAY, WEEK, NOTES }

/**
 * The reminders: one alarm for each, set for its next time. When it goes off the app looks at the
 * day — what is left to log, which trackers are empty — notifies only if there is something to say,
 * and sets the next one. Alarms do not survive a reboot or an update, so those set them again.
 */
class Reminders(
    context: Context,
    private val settings: ReminderSettings,
    private val training: TrainingRepository,
    private val diary: DiaryRepository,
) {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)
    private val notifications = appContext.getSystemService(NotificationManager::class.java)

    /** Sets every reminder's next alarm and cancels those no longer wanted. */
    fun reschedule(now: LocalDateTime = LocalDateTime.now()) {
        val config = settings.config.value
        val next = buildMap {
            if (config.daily.enabled) put(DAILY, ReminderRules.nextTime(now, config.daily.minuteOfDay))
            if (config.weekly.enabled) {
                put(WEEKLY, ReminderRules.nextTime(now, config.weekly.minuteOfDay, setOf(config.weekly.day)))
            }
            config.trackers.filter { it.enabled && it.trackerIds.isNotEmpty() }.forEach { reminder ->
                val days = if (reminder.trigger == TrackerTrigger.SCHEDULED) reminder.days else null
                put(trackerKey(reminder.id), ReminderRules.nextTime(now, reminder.minuteOfDay, days))
            }
        }
        (settings.scheduledKeys - next.keys).forEach { key -> alarms.cancel(alarmIntent(key)) }
        next.forEach { (key, time) ->
            if (time == null) alarms.cancel(alarmIntent(key)) else setAlarm(key, time)
        }
        settings.scheduledKeys = next.filterValues { it != null }.keys
    }

    /** A reminder's alarm went off: notify if there is anything to say, then set the next one. */
    suspend fun fire(key: String, now: LocalDateTime = LocalDateTime.now()) {
        val config = settings.config.value
        val today = now.toLocalDate()
        when {
            key == DAILY && config.daily.enabled -> {
                val names = ReminderRules.workoutsToLog(today, weekPlan(today))
                if (names.isNotEmpty()) {
                    post(DAILY_ID, R.drawable.ic_reminder, "Anything to log?", ReminderRules.listShort(names), OpenTarget.DAY, today)
                }
            }

            key == WEEKLY && config.weekly.enabled -> {
                val week = ReminderRules.reviewedWeek(today)
                val label = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(week)
                post(
                    WEEKLY_ID, R.drawable.ic_reminder, "Weekly review",
                    "Week of $label: ${ReminderRules.weekSummary(weekPlan(week))}", OpenTarget.WEEK, week,
                )
            }

            key.startsWith(TRACKER_PREFIX) -> {
                val reminder = config.trackers.firstOrNull { trackerKey(it.id) == key && it.enabled }
                if (reminder != null && isDue(reminder, today)) {
                    val day = diary.observeDays(today, today).first()[today]
                    val left = ReminderRules.trackersToFill(reminder, diary.observeTrackers().first(), day)
                    if (left.isNotEmpty()) {
                        post(
                            trackerNotificationId(reminder.id), R.drawable.ic_home_note,
                            reminder.phrase.trim().ifEmpty { ReminderRules.DEFAULT_TRACKER_TITLE },
                            left.joinToString(", ") { it.label }, OpenTarget.NOTES, today,
                        )
                    }
                }
            }
        }
        reschedule(now)
    }

    private suspend fun isDue(reminder: TrackerReminder, today: LocalDate): Boolean = when (reminder.trigger) {
        TrackerTrigger.SCHEDULED -> today.dayOfWeek.value in reminder.days
        TrackerTrigger.AFTER_WORKOUT -> {
            val yesterday = today.minusDays(1)
            ReminderRules.hadMatchingWork(reminder, yesterday, weekPlan(yesterday))
        }
    }

    private suspend fun weekPlan(date: LocalDate): WeekPlan {
        val start = WeekMath.weekStartOf(date)
        return WeekPlan(
            occurrences = training.observeWeek(start).first(),
            circuits = training.observeWeekCircuits(start).first(),
            modules = training.observeWeekModules(start).first(),
        )
    }

    private fun setAlarm(key: String, time: LocalDateTime) {
        val at = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = alarmIntent(key)
        // On time when the system allows it; otherwise within ten minutes.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, intent)
        }
    }

    private fun alarmIntent(key: String): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(appContext, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.fromParts(SCHEME, key, null)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(
        id: Int,
        @DrawableRes icon: Int,
        title: String,
        body: String,
        target: OpenTarget,
        date: LocalDate,
    ) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel()
        val open = Intent(appContext, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN)
            .setData(Uri.fromParts(SCHEME, "$id", null))
            .putExtra(MainActivity.EXTRA_TARGET, target.name)
            .putExtra(MainActivity.EXTRA_DATE, date.toEpochDay())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val notification = NotificationCompat.Builder(appContext, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    appContext, id, open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()
        notifications.notify(id, notification)
    }

    private fun ensureChannel() {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Logging, weekly review and tracker reminders"
            }
        )
    }

    companion object {
        const val ACTION_FIRE = "com.yokodake.melete.REMINDER"
        private const val SCHEME = "melete-reminder"
        private const val CHANNEL = "reminders"
        const val DAILY = "daily"
        const val WEEKLY = "weekly"
        private const val TRACKER_PREFIX = "tracker:"
        private const val DAILY_ID = 2001
        private const val WEEKLY_ID = 2002

        fun trackerKey(id: String) = TRACKER_PREFIX + id

        private fun trackerNotificationId(id: String) = 2100 + Math.floorMod(id.hashCode(), 800)

        /** The key an alarm intent carries. */
        fun keyOf(intent: Intent): String? = intent.data?.schemeSpecificPart
    }
}

/** A reminder's alarm. Not exported: only the app's own alarms reach it. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = Reminders.keyOf(intent) ?: return
        val reminders = (context.applicationContext as MeleteApplication).container.reminders
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reminders.fire(key)
            } finally {
                pending.finish()
            }
        }
    }
}

/** A reboot, an update or a clock change: alarms are gone or wrong, so they are set again. */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTORE_ACTIONS) return
        (context.applicationContext as MeleteApplication).container.reminders.reschedule()
    }

    private companion object {
        val RESTORE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
