package com.yokodake.melete.data.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.yokodake.melete.MainActivity
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.R

/**
 * The timer notifications.
 *
 * The ongoing one carries the remaining time as a countdown chronometer, so the system ticks the
 * display itself instead of the app waking once a second to rewrite it. Neither channel makes a
 * sound: the cues are played deliberately with alarm attributes, and a channel sound would double
 * every beep.
 */
class TimerNotifications(context: Context) {

    private val appContext = context.applicationContext

    private val manager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                "Running timer",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows the countdown while it runs"
                setShowBadge(false)
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                "Timer finished",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Tells you the countdown has ended"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun ongoing(state: TimerState, nowElapsedMs: Long): Notification {
        val builder = base(CHANNEL_ONGOING)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
        when (state) {
            is TimerState.Running -> {
                val remaining = state.remainingMs(nowElapsedMs)
                builder
                    .setContentTitle(state.heading())
                    .setContentText(state.currentExerciseLabel ?: "Counting down")
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(System.currentTimeMillis() + remaining)
                    .setShowWhen(true)
                    .addAction(
                        0,
                        "Pause",
                        actionIntent(TimerActionReceiver.ACTION_PAUSE),
                    )
            }

            is TimerState.Paused -> builder
                .setContentTitle(state.heading())
                .setContentText("Paused with ${formatRemaining(state.remainingMs)} left")
                .addAction(0, "Resume", actionIntent(TimerActionReceiver.ACTION_RESUME))

            // Reps: nothing is counting, and the one thing the user has to do is say the set
            // happened. That action belongs on the notification, not only on the screen.
            is TimerState.AwaitingSet -> builder
                .setContentTitle(state.heading())
                .setContentText(state.currentExerciseLabel ?: "Tap when the set is done")
                .addAction(0, "Set done", actionIntent(TimerActionReceiver.ACTION_SET_DONE))

            else -> builder.setContentTitle(state.phaseTitleOrDefault())
        }
        builder.addAction(0, "Stop timer", actionIntent(TimerActionReceiver.ACTION_CANCEL))
        return builder.build()
    }

    fun postFinished(state: TimerState.Finished) {
        val notification = base(CHANNEL_ALERT)
            .setContentTitle("Timer finished")
            .setContentText(
                "Open Melete to review and save your log."
                    .takeIf { state.program.circuitInstanceId != null || state.program.occurrenceId != null }
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(NOTIFICATION_FINISHED, notification)
    }

    fun cancelFinished() = manager.cancel(NOTIFICATION_FINISHED)

    private fun base(channel: String) = NotificationCompat.Builder(appContext, channel)
        .setSmallIcon(R.drawable.ic_nav_timer)
        .setContentIntent(
            PendingIntent.getActivity(
                appContext,
                0,
                Intent(appContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        action.hashCode(),
        Intent(appContext, TimerActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ONGOING = "timer-ongoing"
        const val CHANNEL_ALERT = "timer-alert"
        const val NOTIFICATION_ONGOING = 1001
        const val NOTIFICATION_FINISHED = 1002
    }
}

internal fun TimerPhase.title(): String = when (this) {
    TimerPhase.PREPARE -> "Get ready"
    TimerPhase.WORK -> "Work"
    TimerPhase.REST -> "Rest"
    TimerPhase.SWITCH -> "Switch sides"
    TimerPhase.REP_REST -> "Between reps"
    TimerPhase.TRANSITION -> "Next exercise"
    TimerPhase.ROUND_REST -> "Between rounds"
}

private fun TimerState.phaseTitleOrDefault(): String = when (this) {
    is TimerState.Running -> phase.title()
    is TimerState.Paused -> phase.title()
    is TimerState.AwaitingSet -> "Reps"
    is TimerState.Finished -> phase.title()
    is TimerState.Interrupted -> phase.title()
    TimerState.Idle -> "Timer"
}

/**
 * Where the run is, in words: the set or round, the side, the repeater pulse.
 *
 * One function for the screen and for the notification, because a countdown that says something
 * different in two places is a countdown you cannot trust while your hands are on a hold. Each
 * part appears only when it distinguishes anything — a bilateral single-set timer says nothing.
 */
internal fun TimerState.positionDetail(): String? {
    val program = activeProgram ?: return null
    val parts = buildList {
        val set = currentSet
        if (set != null && program.sets > 1) {
            add(if (program.isCircuit) "round $set of ${program.sets}" else "set $set of ${program.sets}")
        }
        val station = currentStation
        if (station != null) add("exercise $station of ${program.entries.size}")
        when (currentSide) {
            BodySide.LEFT -> add("left")
            BodySide.RIGHT -> add("right")
            null -> Unit
        }
        val rep = currentRep
        val reps = currentStep?.entryIndex?.let { program.entries.getOrNull(it) }?.repsPerSetOnTimer
        if (rep != null && reps != null) add("rep $rep of $reps")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * What the notification calls this moment. A one-set program says only what it is doing; anything
 * with more shape than that leads with where you are in it, because that is the thing you cannot
 * see from the remaining time alone.
 */
internal fun TimerState.heading(): String {
    val what = when (this) {
        is TimerState.AwaitingSet -> "Reps"
        else -> phaseTitleOrDefault()
    }
    val where = positionDetail() ?: return what
    return "$what · $where"
}

internal fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs + 999) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    } else {
        "$seconds s"
    }
}
