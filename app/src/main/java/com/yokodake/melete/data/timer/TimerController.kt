package com.yokodake.melete.data.timer

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * The one owner of timer state.
 *
 * There is a single active countdown, on purpose: a rest timer that can be paused, resumed and
 * cancelled is what the training actually needs, and a general interval-sequence engine would be a
 * far larger thing to get right.
 *
 * Two paths can deliver a cue — the in-process countdown while the app lives, and an exact alarm
 * if it does not — so every cue passes through [deliver], which consults durable bookkeeping and
 * lets exactly one of them through per run.
 */
class TimerController(
    context: Context,
    private val store: TimerStore,
    private val cues: CuePlayer,
    private val alarms: AlarmScheduler,
    private val notifications: TimerNotifications,
    private val scope: CoroutineScope,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow<TimerState>(TimerState.Idle)
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val cueMutex = Mutex()
    private var countdownJob: Job? = null

    init {
        notifications.ensureChannels()
        val restored = TimerRestore.restore(store.readSnapshot(), store.bootCount, now())
        _state.value = restored
        when (restored) {
            is TimerState.Running -> {
                // The app came back while a countdown is still live: re-arm both paths.
                alarms.schedule(restored)
                startCountdownLoop()
                startService()
            }

            is TimerState.Interrupted -> {
                alarms.cancelAll()
                persist()
            }

            else -> Unit
        }
    }

    val warningLeadSeconds: Int get() = store.warningLeadSeconds

    fun setWarningLeadSeconds(seconds: Int) {
        store.warningLeadSeconds = seconds
    }

    fun lastDurationSeconds(phase: TimerPhase): Int = when (phase) {
        TimerPhase.WORK -> store.lastWorkSeconds
        TimerPhase.REST -> store.lastRestSeconds
    }

    /**
     * Starts a countdown. Called from a tap while the app is on screen, which is what gives the
     * foreground service the while-in-use capability it needs to make a sound later on.
     */
    fun start(phase: TimerPhase, durationSeconds: Int, warningLeadSeconds: Int = store.warningLeadSeconds) {
        val duration = durationSeconds.coerceAtLeast(1)
        when (phase) {
            TimerPhase.WORK -> store.lastWorkSeconds = duration
            TimerPhase.REST -> store.lastRestSeconds = duration
        }
        clearRun(cancelAlarms = true)
        notifications.cancelFinished()
        val running = TimerTransitions.start(
            runId = UUID.randomUUID().toString(),
            phase = phase,
            durationMs = duration * 1000L,
            warningLeadMs = warningLeadSeconds.takeIf { it > 0 }?.times(1000L),
            nowElapsedMs = now(),
        )
        _state.value = running
        persist()
        alarms.schedule(running)
        startCountdownLoop()
        startService()
    }

    fun pause() {
        val running = _state.value as? TimerState.Running ?: return
        countdownJob?.cancel()
        // Nothing may remain pending, or the cue arrives while the countdown is stopped.
        alarms.cancelAll()
        _state.value = TimerTransitions.pause(running, now())
        persist()
        startService()
    }

    fun resume() {
        val paused = _state.value as? TimerState.Paused ?: return
        val running = TimerTransitions.resume(paused, now())
        _state.value = running
        persist()
        alarms.schedule(running)
        startCountdownLoop()
        startService()
    }

    fun cancel() {
        clearRun(cancelAlarms = true)
        _state.value = TimerState.Idle
        persist()
        notifications.cancelFinished()
    }

    /** Acknowledges a finished or interrupted run without recording anything. */
    fun dismiss() {
        val current = _state.value
        if (current is TimerState.Running || current is TimerState.Paused) return
        clearRun(cancelAlarms = true)
        _state.value = TimerState.Idle
        persist()
        notifications.cancelFinished()
    }

    /** Called by the alarm receiver, including on a process that has just been created for it. */
    fun onAlarm(runId: String, cue: TimerCue) {
        scope.launch { deliver(runId, cue) }
    }

    private fun startCountdownLoop() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (true) {
                val running = _state.value as? TimerState.Running ?: return@launch
                val nowMs = now()
                if (running.isWarningDue(nowMs)) {
                    deliver(running.runId, TimerCue.WARNING)
                    continue
                }
                if (running.isDue(nowMs)) {
                    deliver(running.runId, TimerCue.FINISH)
                    return@launch
                }
                val nextCueAt = running.warningAtElapsedMs
                    ?.takeIf { !running.warningFired && it > nowMs }
                    ?: running.deadlineElapsedMs
                delay((nextCueAt - nowMs).coerceAtLeast(MIN_SLEEP_MS))
            }
        }
    }

    /**
     * Delivers a cue at most once per run, whichever path gets here first. The bookkeeping is
     * written durably before the sound, so a process killed mid-cue cannot double it on restart.
     */
    private suspend fun deliver(runId: String, cue: TimerCue) {
        cueMutex.withLock {
            val current = _state.value
            if (current.activeRunId != runId) {
                Log.d(TAG, "Ignoring $cue for stale run $runId")
                return
            }
            if (current is TimerState.Running && cue == TimerCue.WARNING && current.warningFired) return
            if (current is TimerState.Finished && cue == TimerCue.FINISH) return

            // Whether *this* path owes the sound. The state still has to move either way: a cue
            // delivered by the other path is a cue that happened, and leaving the countdown
            // running because someone else rang the bell would strand it forever.
            val owed = store.markCueDelivered(runId, cue)
            if (!owed) Log.d(TAG, "$cue for $runId was already delivered")

            when (cue) {
                TimerCue.WARNING -> {
                    if (current is TimerState.Running) {
                        _state.value = current.copy(warningFired = true)
                        persist()
                    }
                    if (owed) cues.play(TimerCue.WARNING)
                }

                TimerCue.FINISH -> {
                    alarms.cancelAll()
                    val phase = (current as? TimerState.Running)?.phase
                        ?: (current as? TimerState.Paused)?.phase
                    _state.value = TimerTransitions.finish(current)
                    persist()
                    if (owed) {
                        cues.play(TimerCue.FINISH)
                        // Reaching zero is a cue, never a performed set: the logger is offered,
                        // and only the user can confirm that the work happened.
                        phase?.let(notifications::postFinished)
                    }
                }
            }
        }
    }

    private fun clearRun(cancelAlarms: Boolean) {
        countdownJob?.cancel()
        countdownJob = null
        if (cancelAlarms) alarms.cancelAll()
        _state.value.activeRunId?.let(store::clearCues)
    }

    private fun persist() {
        store.writeSnapshot(TimerRestore.snapshot(_state.value, store.bootCount))
    }

    /**
     * The service is only ever asked to start, never told to stop from here. Calling
     * `stopService` on a start that has not yet reached `startForeground` breaks the promise the
     * platform was given and it kills the process for it — which is easy to hit with a one-second
     * countdown, or a start the user immediately cancels. The service watches the state instead
     * and stands itself down once there is nothing left to count.
     */
    private fun startService() {
        runCatching {
            appContext.startForegroundService(Intent(appContext, TimerService::class.java))
        }.onFailure {
            // Starting a foreground service from the background is refused by design. The
            // countdown itself is unaffected: its deadline and its alarms are already set.
            Log.w(TAG, "Could not bring up the timer service", it)
        }
    }

    private companion object {
        const val TAG = "TimerController"
        const val MIN_SLEEP_MS = 50L
    }
}
