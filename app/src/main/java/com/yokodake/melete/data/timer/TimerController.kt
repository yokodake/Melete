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

    /**
     * Which countdown loop is the live one.
     *
     * A program advances from *inside* the loop — the interval ends, and the next one is armed
     * from the same coroutine that was waiting for it — so cancelling the old loop there cannot
     * take effect until it next suspends. Without a generation to check against, the outgoing
     * loop would keep driving the incoming run alongside its replacement: harmless, because every
     * cue is deduplicated, but two coroutines waking the CPU for the same deadline is exactly the
     * waste this timer is careful about elsewhere.
     */
    private var loopGeneration = 0

    init {
        notifications.ensureChannels()
        val restored = TimerRestore.restore(store.readSnapshot(), store.bootCount, now())
        _state.value = restored
        when (restored) {
            // The app came back while a countdown, or a program waiting on a set, is still live.
            is TimerState.Running, is TimerState.AwaitingSet -> engage(restored)

            is TimerState.Interrupted -> {
                alarms.cancelAll()
                persist()
            }

            else -> Unit
        }
    }

    val cueSettings: CueSettings get() = store.cueSettings

    /**
     * Changes which cues are wanted, including in the middle of a countdown.
     *
     * A cue setting is a preference about the next few seconds, not a property of the run, so
     * waiting for the timer to end before it takes effect would be useless exactly when it
     * matters — mid-rest, realising the ticking is wrong for where you are. The run is replanned
     * in place: cues whose moment has already passed are written off rather than fired, and both
     * delivery paths are re-armed against the new plan so neither can sound a cue the user has
     * just switched off.
     */
    fun setCueSettings(settings: CueSettings) {
        store.cueSettings = settings
        when (val current = _state.value) {
            is TimerState.Running -> {
                countdownJob?.cancel()
                val replanned = TimerTransitions.replan(current, settings, now())
                _state.value = replanned
                persist()
                alarms.schedule(replanned)
                startCountdownLoop()
            }

            is TimerState.Paused -> {
                _state.value = TimerTransitions.replan(current, settings)
                persist()
            }

            else -> Unit
        }
    }

    /** How many sets the last program had, so the create screen opens on a familiar number. */
    val lastSets: Int get() = store.lastSets

    fun lastDurationSeconds(phase: TimerPhase): Int = when (phase) {
        TimerPhase.WORK -> store.lastWorkSeconds
        TimerPhase.REST -> store.lastRestSeconds
    }

    /**
     * Starts a countdown. Called from a tap while the app is on screen, which is what gives the
     * foreground service the while-in-use capability it needs to make a sound later on.
     */
    fun start(
        phase: TimerPhase,
        durationSeconds: Int,
        settings: CueSettings = store.cueSettings,
        label: String? = null,
    ) {
        val duration = durationSeconds.coerceAtLeast(1)
        val program = when (phase) {
            TimerPhase.WORK -> TimerProgram.work(duration, label)
            TimerPhase.REST -> TimerProgram.rest(duration, label)
        }
        start(program, settings)
    }

    /**
     * Starts a whole program: so many sets, this long each, that much rest between them.
     *
     * Only the first interval is set up here. What follows is decided when this one runs out, in
     * [deliver], because that is the moment the clock actually reaches — deciding it up front
     * would mean holding a schedule of deadlines that a pause or a cancel would invalidate.
     */
    fun start(program: TimerProgram, settings: CueSettings = store.cueSettings) {
        if (program.work == WorkKind.TIMED) store.lastWorkSeconds = program.workSeconds
        if (program.restSeconds > 0) store.lastRestSeconds = program.restSeconds
        store.lastSets = program.sets
        clearRun(cancelAlarms = true)
        notifications.cancelFinished()
        val started = TimerTransitions.startProgram(
            runId = UUID.randomUUID().toString(),
            program = program,
            settings = settings,
            nowElapsedMs = now(),
        )
        _state.value = started
        persist()
        engage(started)
    }

    /**
     * The athlete says a set of reps is done, which starts its rest. This is the only manual step
     * in a program; everything after it happens on the clock.
     */
    fun completeSet() {
        val awaiting = _state.value as? TimerState.AwaitingSet ?: return
        val next = TimerTransitions.completeSet(
            state = awaiting,
            settings = store.cueSettings,
            nowElapsedMs = now(),
            nextRunId = UUID.randomUUID().toString(),
        )
        store.clearCues(awaiting.runId)
        _state.value = next
        persist()
        if (next is TimerState.Finished) {
            notifications.postFinished(next)
        }
        engage(next)
    }

    /** Arms whatever the new state needs: the countdown loop, the alarms, the service. */
    private fun engage(state: TimerState) {
        countdownJob?.cancel()
        countdownJob = null
        when (state) {
            is TimerState.Running -> {
                alarms.schedule(state)
                startCountdownLoop()
                startService()
            }

            // Nothing is counting, but the service stays up so the program is not forgotten while
            // the phone is in a pocket between sets.
            is TimerState.AwaitingSet -> startService()

            else -> Unit
        }
    }

    /** True while there is a program the user would lose by starting another one. */
    val hasActiveProgram: Boolean
        get() = _state.value.let {
            it is TimerState.Running || it is TimerState.Paused || it is TimerState.AwaitingSet
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
        if (hasActiveProgram) return
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
        val generation = ++loopGeneration
        countdownJob = scope.launch {
            while (loopGeneration == generation) {
                val running = _state.value as? TimerState.Running ?: return@launch
                val nowMs = now()
                val due = running.dueCue(nowMs)
                if (due != null) {
                    deliver(running.runId, due.cue)
                    continue
                }
                val nextAt = running.nextCueAt(nowMs) ?: return@launch
                delay((nextAt - nowMs).coerceAtLeast(MIN_SLEEP_MS))
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
            if (current is TimerState.Running && cue in current.delivered) return
            if (current is TimerState.Finished && cue == TimerCue.FINISH) return

            // Whether *this* path owes the sound. The state still has to move either way: a cue
            // delivered by the other path is a cue that happened, and leaving the countdown
            // running because someone else rang the bell would strand it forever.
            val owed = store.markCueDelivered(runId, cue)
            if (!owed) Log.d(TAG, "$cue for $runId was already delivered")

            if (cue == TimerCue.FINISH) {
                alarms.cancelAll()
                // The end of an interval is not necessarily the end of the program: the next set,
                // or the rest between, starts from here under a new run id.
                val next = TimerTransitions.advance(
                    state = current,
                    settings = store.cueSettings,
                    nowElapsedMs = now(),
                    nextRunId = UUID.randomUUID().toString(),
                )
                // The interval that just ended will never be asked about again.
                if (next.activeRunId != runId) store.clearCues(runId)
                _state.value = next
                persist()
                if (owed) {
                    cues.play(cue)
                    // Reaching zero is a cue, never a performed set: the logger is offered, and
                    // only the user can confirm that the work happened.
                    if (next is TimerState.Finished) notifications.postFinished(next)
                }
                engage(next)
            } else {
                if (current is TimerState.Running) {
                    _state.value = current.copy(delivered = current.delivered + cue)
                    persist()
                }
                if (owed) cues.play(cue)
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
