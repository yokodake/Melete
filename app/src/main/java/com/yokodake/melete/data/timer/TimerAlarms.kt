package com.yokodake.melete.data.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yokodake.melete.MeleteApplication

/** Schedules the backstop cues, behind a seam so tests do not have to race real alarms. */
interface AlarmScheduler {
    fun schedule(state: TimerState.Running)
    fun cancelAll()
}

/**
 * The backstop that makes a cue survive the process being killed.
 *
 * Two **one-shot** exact alarms per run, one for the advance warning and one for the end. This is
 * deliberately not a repeating alarm ticking every interval: the countdown itself is driven in
 * process, while the device is awake, and these alarms exist only so that a frozen or killed app
 * still sounds at the two moments that matter. Alarms are on the elapsed-realtime clock, the same
 * clock the deadline is expressed in, so the user changing the time of day cannot move them.
 */
class TimerAlarms(context: Context) : AlarmScheduler {

    private val appContext = context.applicationContext

    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Only the two cues worth waking a dead process for: thirty seconds out, and the end. The
     * finer cues are company while you train, not something to resurrect the app over, and eight
     * exact alarms per rest interval would be a poor trade for a phone's battery.
     */
    override fun schedule(state: TimerState.Running) {
        cancelAll()
        state.plan
            .filter { it.cue == TimerCue.THIRTY_SECONDS || it.cue == TimerCue.FINISH }
            .filter { it.cue !in state.delivered }
            .forEach { schedule(it.cue, state.runId, state.instantOf(it)) }
    }

    private fun schedule(cue: TimerCue, runId: String, triggerElapsedMs: Long) {
        val pendingIntent = pendingIntent(cue, runId, mutable = false)
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerElapsedMs,
                pendingIntent,
            )
        }.onFailure {
            // Exact alarms can be refused; the in-process countdown still cues while alive.
            Log.w(TAG, "Could not schedule the $cue alarm", it)
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsedMs, pendingIntent)
        }
    }

    /** Cancelling a countdown must leave nothing pending, or a cue arrives after it was called off. */
    override fun cancelAll() {
        BACKSTOPPED_CUES.forEach { cue ->
            alarmManager.cancel(pendingIntent(cue, runId = null, mutable = false))
        }
    }

    private fun pendingIntent(cue: TimerCue, runId: String?, mutable: Boolean): PendingIntent {
        val intent = Intent(appContext, TimerAlarmReceiver::class.java).apply {
            action = "${TimerAlarmReceiver.ACTION_CUE}.${cue.name}"
            runId?.let { putExtra(TimerAlarmReceiver.EXTRA_RUN_ID, it) }
            putExtra(TimerAlarmReceiver.EXTRA_CUE, cue.name)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(appContext, cue.ordinal, intent, flags)
    }

    private companion object {
        const val TAG = "TimerAlarms"
        val BACKSTOPPED_CUES = listOf(TimerCue.THIRTY_SECONDS, TimerCue.FINISH)
    }
}

/**
 * Delivers a cue even when the app is not running. The receiver hands straight over to the
 * controller, which decides whether this cue has already been delivered in process.
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cue = intent.getStringExtra(EXTRA_CUE)
            ?.let { name -> TimerCue.entries.firstOrNull { it.name == name } }
            ?: return
        val runId = intent.getStringExtra(EXTRA_RUN_ID) ?: return
        val application = context.applicationContext as? MeleteApplication ?: return
        val pendingResult = goAsync()
        runCatching {
            application.container.timerController.onAlarm(runId, cue)
        }.onFailure { Log.w(TAG, "Cue $cue could not be delivered", it) }
        pendingResult.finish()
    }

    companion object {
        const val ACTION_CUE = "com.yokodake.melete.TIMER_CUE"
        const val EXTRA_RUN_ID = "run-id"
        const val EXTRA_CUE = "cue"
        private const val TAG = "TimerAlarmReceiver"
    }
}
