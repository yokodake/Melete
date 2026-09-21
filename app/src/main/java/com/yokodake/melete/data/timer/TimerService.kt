package com.yokodake.melete.data.timer

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.yokodake.melete.MeleteApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the countdown alive and visible.
 *
 * Type `specialUse`: a training countdown is none of the defined categories, and the alternatives
 * are worse. `shortService` is capped at three minutes — shorter than an ordinary hangboard rest —
 * and Android 17 excludes it from background audio outright, which is the one thing this service
 * exists to protect.
 *
 * A foreground service keeps the process alive; it does **not** promise the CPU stays awake. The
 * partial wake lock does that, held only while a countdown is actually running and bounded by its
 * remaining time, so a bug cannot leave it held.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var observer: Job? = null

    private val controller: TimerController
        get() = (application as MeleteApplication).container.timerController

    private val notifications: TimerNotifications
        get() = (application as MeleteApplication).container.timerNotifications

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // First thing, unconditionally: the platform was promised a foreground service, and the
        // promise has to be kept even when the countdown has already ended in the meantime.
        if (!promoteToForeground(controller.state.value)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observer == null) {
            observer = scope.launch {
                controller.state.collectLatest { state -> onState(state) }
            }
        }
        // Not sticky: a restarted service with no user in front of it would be a background start
        // with none of the capabilities that make a cue audible.
        return START_NOT_STICKY
    }

    private fun onState(state: TimerState) {
        when (state) {
            is TimerState.Running -> {
                val remaining = state.remainingMs(SystemClock.elapsedRealtime())
                acquireWakeLock(remaining + WAKE_LOCK_SLACK_MS)
                promoteToForeground(state)
            }

            is TimerState.Paused -> {
                releaseWakeLock()
                promoteToForeground(state)
            }

            // Reps in progress: nothing is counting, so nothing needs the CPU held awake, but the
            // service stays up because the program is not over and the phone may be in a pocket.
            is TimerState.AwaitingSet -> {
                releaseWakeLock()
                promoteToForeground(state)
            }

            TimerState.Idle, is TimerState.Finished, is TimerState.Interrupted -> {
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    /** Returns false when the platform refused the promotion, which is a reason to stand down. */
    private fun promoteToForeground(state: TimerState): Boolean {
        notifications.ensureChannels()
        return runCatching {
            startForeground(
                TimerNotifications.NOTIFICATION_ONGOING,
                notifications.ongoing(state, SystemClock.elapsedRealtime()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }.onFailure { Log.w(TAG, "The timer service could not go foreground", it) }.isSuccess
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "melete:timer")
            .apply {
                setReferenceCounted(false)
                runCatching { acquire(timeoutMs) }
                    .onFailure { Log.w(TAG, "Could not hold the CPU awake", it) }
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.let { runCatching { it.release() } }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        observer = null
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "TimerService"
        const val WAKE_LOCK_SLACK_MS = 10_000L
    }
}

/** Handles the notification buttons. A notification tap is a user action, so this may start the service. */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val controller = (context.applicationContext as? MeleteApplication)
            ?.container
            ?.timerController
            ?: return
        when (intent.action) {
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_CANCEL -> controller.cancel()
            ACTION_SET_DONE -> controller.completeSet()
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.yokodake.melete.TIMER_PAUSE"
        const val ACTION_RESUME = "com.yokodake.melete.TIMER_RESUME"
        const val ACTION_CANCEL = "com.yokodake.melete.TIMER_CANCEL"
        const val ACTION_SET_DONE = "com.yokodake.melete.TIMER_SET_DONE"
    }
}
