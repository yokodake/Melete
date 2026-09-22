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
import java.util.UUID

/**
 * The one owner of timer state.
 *
 * There is a single program at a time, on purpose: starting another from a workout asks before
 * calling the first one off.
 *
 * **One path delivers cues**: the countdown loop, kept alive by the foreground service. The timer
 * has to survive the phone being locked and other apps being used, which the service provides; it
 * is explicitly *not* required to survive the app being killed. That one line of scope is what
 * lets everything here be ordinary in-memory state — a cue that has sounded is remembered in
 * [TimerState.Running.delivered], which empties by itself when the next interval begins, so
 * nothing has to be written to disk before a sound or reconciled between two deliverers after it.
 *
 * What *is* persisted is the deadline, which is enough. A killed process makes no sound, but
 * reopening the app recomputes from the monotonic clock and either shows the true remaining time
 * or says the countdown ended while the app was not running, rather than pretending it alerted.
 */
class TimerController(
    context: Context,
    private val store: TimerStore,
    private val cues: CuePlayer,
    private val notifications: TimerNotifications,
    private val scope: CoroutineScope,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow<TimerState>(TimerState.Idle)
    val state: StateFlow<TimerState> = _state.asStateFlow()

    /**
     * The single driver of the current program.
     *
     * It runs across intervals rather than being restarted at each one: when a countdown ends it
     * publishes the next state and keeps looping, so it never has to cancel and relaunch itself
     * from inside itself. Anything that changes the interval from *outside* — starting, skipping,
     * resuming — does restart it, because the loop is otherwise asleep until a deadline that no
     * longer applies.
     */
    private var countdownJob: Job? = null

    init {
        notifications.ensureChannels()
        val restored = TimerRestore.restore(store.readSnapshot(), store.bootCount, now())
        _state.value = restored
        when (restored) {
            // The app came back while a countdown, or a program waiting on a set, is still live.
            is TimerState.Running, is TimerState.AwaitingSet -> engage(restored)

            is TimerState.Interrupted -> persist()

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
     * in place: cues whose moment has already passed are written off rather than fired.
     */
    fun setCueSettings(settings: CueSettings) {
        store.cueSettings = settings
        when (val current = _state.value) {
            is TimerState.Running -> {
                val replanned = TimerTransitions.replan(current, settings, now())
                _state.value = replanned
                persist()
                engage(replanned)
            }

            is TimerState.Paused -> {
                _state.value = TimerTransitions.replan(current, settings)
                persist()
            }

            else -> Unit
        }
    }

    /** The shape of the last hand-built program, so the create screen opens on familiar numbers. */
    val lastSets: Int get() = store.lastSets
    val lastUnilateral: Boolean get() = store.lastUnilateral
    val lastSideSwitchSeconds: Int get() = store.lastSideSwitchSeconds
    val lastRepeaterReps: Int get() = store.lastRepeaterReps
    val lastRepeaterWorkSeconds: Int get() = store.lastRepeaterWorkSeconds
    val lastRepeaterRestSeconds: Int get() = store.lastRepeaterRestSeconds

    fun lastDurationSeconds(phase: TimerPhase): Int =
        // A preparation has a fixed length, so there is nothing remembered about it; asking
        // yields the work length, which is what the caller is really after. The same goes for
        // the short rests a side switch or a repeater pulse uses: those come from a prescription.
        if (phase.isRest) store.lastRestSeconds else store.lastWorkSeconds

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
        val program = if (phase.isRest) {
            TimerProgram.rest(duration, label)
        } else {
            TimerProgram.work(duration, label)
        }
        start(program, settings)
    }

    /**
     * Starts a whole program: so many sets, this long each, that much rest between them.
     *
     * Only the first interval is set up here. What follows is decided when this one runs out, in
     * the countdown loop, because that is the moment the clock actually reaches — deciding it up
     * front would mean holding a schedule of deadlines that a pause or a skip would invalidate.
     */
    fun start(program: TimerProgram, settings: CueSettings = store.cueSettings) {
        // Only a plain single-exercise program is worth remembering numbers from: a circuit's
        // shape belongs to the routine, and reopening the create screen on one of its stations
        // would be a worse guess than leaving the last standalone timer in place.
        if (!program.isCircuit) {
            val entry = program.entry
            if (entry.work == WorkKind.TIMED && entry.workSeconds > 0) {
                store.lastWorkSeconds = entry.workSeconds
            }
            if (program.restSeconds > 0) store.lastRestSeconds = program.restSeconds
            store.lastSets = program.sets
            store.lastUnilateral = entry.unilateral
            if (entry.unilateral) store.lastSideSwitchSeconds = entry.sideSwitchSeconds
            entry.repeater?.let {
                store.lastRepeaterReps = it.repsPerSet
                store.lastRepeaterWorkSeconds = it.workSecondsPerRep
                store.lastRepeaterRestSeconds = it.restSecondsBetweenReps
            }
        }
        // The store is the one source of cue settings, because every interval after the first is
        // planned when it begins and reads them from there. Planning the first from an argument
        // and the rest from the store meant a program started with explicit settings quietly
        // reverted after one set.
        store.cueSettings = settings
        countdownJob?.cancel()
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
        moveTo(
            TimerTransitions.completeSet(
                state = awaiting,
                settings = store.cueSettings,
                nowElapsedMs = now(),
                nextRunId = UUID.randomUUID().toString(),
            )
        )
    }

    /** True while there is a program the user would lose by starting another one. */
    val hasActiveProgram: Boolean
        get() = _state.value.let {
            it is TimerState.Running || it is TimerState.Paused || it is TimerState.AwaitingSet
        }

    fun pause() {
        val running = _state.value as? TimerState.Running ?: return
        countdownJob?.cancel()
        _state.value = TimerTransitions.pause(running, now())
        persist()
        startService()
    }

    fun resume() {
        val paused = _state.value as? TimerState.Paused ?: return
        moveTo(
            TimerTransitions.resume(
                state = paused,
                nowElapsedMs = now(),
                settings = store.cueSettings,
                nextRunId = UUID.randomUUID().toString(),
            )
        )
    }

    /** Skips to the next interval of the program. */
    fun next() {
        if (!hasActiveProgram) return
        moveTo(
            TimerTransitions.next(
                state = _state.value,
                settings = store.cueSettings,
                nowElapsedMs = now(),
                nextRunId = UUID.randomUUID().toString(),
            )
        )
    }

    /**
     * Restarts the interval on screen, or steps back to the one before it when pressed straight
     * after this one began.
     */
    fun previous() {
        if (!hasActiveProgram) return
        moveTo(
            TimerTransitions.previous(
                state = _state.value,
                settings = store.cueSettings,
                nowElapsedMs = now(),
                nextRunId = UUID.randomUUID().toString(),
            )
        )
    }

    /** Applies a state the user asked for, from outside the countdown loop. */
    private fun moveTo(next: TimerState) {
        if (next === _state.value) return
        countdownJob?.cancel()
        _state.value = next
        persist()
        if (next is TimerState.Finished) notifications.postFinished(next)
        engage(next)
    }

    fun cancel() {
        countdownJob?.cancel()
        _state.value = TimerState.Idle
        persist()
        notifications.cancelFinished()
    }

    /** Acknowledges a finished or interrupted run without recording anything. */
    fun dismiss() {
        if (hasActiveProgram) return
        countdownJob?.cancel()
        _state.value = TimerState.Idle
        persist()
        notifications.cancelFinished()
    }

    /**
     * Arms what the state needs. The service is only ever asked to start: it watches the state
     * itself and stands down once there is nothing left to count.
     */
    private fun engage(state: TimerState) {
        when (state) {
            is TimerState.Running -> {
                startCountdownLoop()
                startService()
            }

            // Nothing is counting, but the service stays up so the program is not forgotten while
            // the phone is in a pocket between sets.
            is TimerState.AwaitingSet -> startService()

            else -> Unit
        }
    }

    /**
     * Sleeps until the next cue is due, sounds it, and carries on — across the whole program.
     *
     * The loop owns the sequencing: when an interval ends it works out what comes next, publishes
     * it and keeps going, which is why there is one of these for a whole program rather than one
     * per interval.
     */
    private fun startCountdownLoop() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (true) {
                val running = _state.value as? TimerState.Running ?: return@launch
                val nowMs = now()
                val due = running.dueCue(nowMs)
                if (due == null) {
                    val nextAt = running.nextCueAt(nowMs) ?: return@launch
                    delay((nextAt - nowMs).coerceAtLeast(MIN_SLEEP_MS))
                    continue
                }
                if (!deliver(running, due.cue, nowMs)) return@launch
            }
        }
    }

    /**
     * Sounds one cue and moves the state on. Returns false when there is nothing left to count.
     *
     * A cue sounds at most once because [TimerState.Running.delivered] says so, and that set
     * belongs to the interval: the next interval is a new state with an empty one, so the same
     * beep is free to sound again for the next set.
     */
    private fun deliver(running: TimerState.Running, cue: TimerCue, nowMs: Long): Boolean {
        // The interval may have been skipped, paused or cancelled while this loop was asleep.
        if (_state.value !== running) return _state.value is TimerState.Running
        if (cue in running.delivered) return true

        if (cue != TimerCue.FINISH) {
            _state.value = running.copy(delivered = running.delivered + cue)
            persist()
            cues.play(cue)
            return true
        }

        // The end of an interval is not necessarily the end of the program: the next set, or the
        // rest between, starts from here.
        val next = TimerTransitions.advance(
            state = running,
            settings = store.cueSettings,
            nowElapsedMs = nowMs,
            nextRunId = UUID.randomUUID().toString(),
        )
        _state.value = next
        persist()
        cues.play(cue)
        if (next is TimerState.Finished) {
            // Reaching the end is a cue, never a performed set: the logger is offered, and only
            // the user can confirm that the work happened.
            notifications.postFinished(next)
        }
        if (next is TimerState.AwaitingSet) startService()
        return next is TimerState.Running
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
            // countdown itself is unaffected: its deadline is already set.
            Log.w(TAG, "Could not bring up the timer service", it)
        }
    }

    private companion object {
        const val TAG = "TimerController"
        const val MIN_SLEEP_MS = 50L
    }
}
