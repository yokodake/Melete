package com.yokodake.melete.data.timer

/** What a countdown is counting: the work interval itself, or the rest after it. */
enum class TimerPhase {
    WORK,
    REST,
}

/**
 * The timer state.
 *
 * A running countdown is a **deadline on the monotonic clock**, never a number being decremented.
 * Anything that decrements drifts, stops when the process is frozen, and cannot be recovered after
 * the UI is recreated; a deadline can always be re-derived from the clock. Elapsed-realtime is the
 * right clock because it keeps counting while the device sleeps and is unaffected by the user or
 * the network moving the wall clock.
 *
 * Which cues a run owes is fixed when it starts, and which it has already given travels with the
 * state, so pausing, resuming or rebuilding the UI can never sound the same cue twice.
 *
 * A run carries a [label] — the exercise it was started from, when it was started from one. It is
 * a snapshot of a name, never a link into the record: a countdown reaching zero must not be able
 * to touch what was logged, and a countdown outliving the screen that began it must still be able
 * to say what it is counting.
 */
sealed interface TimerState {

    /** Nothing is being timed. */
    data object Idle : TimerState

    data class Running(
        val runId: String,
        val phase: TimerPhase,
        val totalMs: Long,
        /** Elapsed-realtime instant the countdown reaches zero. */
        val deadlineElapsedMs: Long,
        val plan: List<PlannedCue>,
        val delivered: Set<TimerCue> = emptySet(),
        /** The exercise this countdown was started from, or null for a free-standing one. */
        val label: String? = null,
    ) : TimerState {

        fun remainingMs(nowElapsedMs: Long): Long =
            (deadlineElapsedMs - nowElapsedMs).coerceAtLeast(0)

        /** The elapsed-realtime instant a planned cue sounds. */
        fun instantOf(planned: PlannedCue): Long = deadlineElapsedMs - planned.remainingMs

        /** The earliest cue that is owed and already due, or null when none is. */
        fun dueCue(nowElapsedMs: Long): PlannedCue? = plan
            .filter { it.cue !in delivered && instantOf(it) <= nowElapsedMs }
            // The most time left is the earliest one, so cues are always given in order even if
            // several fell due while the app was not looking.
            .maxByOrNull { it.remainingMs }

        /** When the next owed cue falls due, or null when everything has been given. */
        fun nextCueAt(nowElapsedMs: Long): Long? = plan
            .filter { it.cue !in delivered }
            .map(::instantOf)
            .filter { it > nowElapsedMs }
            .minOrNull()

        fun isDue(nowElapsedMs: Long): Boolean = nowElapsedMs >= deadlineElapsedMs
    }

    data class Paused(
        val runId: String,
        val phase: TimerPhase,
        val totalMs: Long,
        val remainingMs: Long,
        val plan: List<PlannedCue>,
        val delivered: Set<TimerCue> = emptySet(),
        val label: String? = null,
    ) : TimerState

    /** The countdown reached zero. Reaching zero records nothing: it is a cue, not a performed set. */
    data class Finished(
        val runId: String,
        val phase: TimerPhase,
        val totalMs: Long,
        val label: String? = null,
    ) : TimerState

    /**
     * A countdown was running when the device restarted. The old deadline belonged to a previous
     * boot and means nothing now, so the run is reported as interrupted instead of being resumed
     * from a number that would be a fabrication.
     */
    data class Interrupted(
        val runId: String,
        val phase: TimerPhase,
        val totalMs: Long,
        val label: String? = null,
    ) : TimerState

    val activeRunId: String?
        get() = when (this) {
            is Running -> runId
            is Paused -> runId
            is Finished -> runId
            is Interrupted -> runId
            Idle -> null
        }

    /**
     * What this countdown is for, when it was started from an exercise. Named apart from the
     * subclasses' own `label` so that they declare it rather than override it.
     */
    val activeLabel: String?
        get() = when (this) {
            is Running -> label
            is Paused -> label
            is Finished -> label
            is Interrupted -> label
            Idle -> null
        }
}

/** Pause and resume, kept pure so the arithmetic can be tested without a device. */
object TimerTransitions {

    fun start(
        runId: String,
        phase: TimerPhase,
        durationMs: Long,
        settings: CueSettings,
        nowElapsedMs: Long,
        label: String? = null,
    ): TimerState.Running = TimerState.Running(
        runId = runId,
        phase = phase,
        totalMs = durationMs,
        deadlineElapsedMs = nowElapsedMs + durationMs,
        plan = CuePlanner.plan(phase, durationMs, settings),
        label = label,
    )

    fun pause(state: TimerState.Running, nowElapsedMs: Long): TimerState.Paused = TimerState.Paused(
        runId = state.runId,
        phase = state.phase,
        totalMs = state.totalMs,
        remainingMs = state.remainingMs(nowElapsedMs),
        plan = state.plan,
        // Carried across the pause: cues already given must not be given again on resume.
        delivered = state.delivered,
        label = state.label,
    )

    fun resume(state: TimerState.Paused, nowElapsedMs: Long): TimerState.Running = TimerState.Running(
        runId = state.runId,
        phase = state.phase,
        totalMs = state.totalMs,
        deadlineElapsedMs = nowElapsedMs + state.remainingMs,
        plan = state.plan,
        delivered = state.delivered,
        label = state.label,
    )

    fun finish(state: TimerState): TimerState = when (state) {
        is TimerState.Running ->
            TimerState.Finished(state.runId, state.phase, state.totalMs, state.label)

        is TimerState.Paused ->
            TimerState.Finished(state.runId, state.phase, state.totalMs, state.label)

        else -> state
    }

    /**
     * Applies changed cue settings to a countdown that is already under way.
     *
     * The plan is rebuilt for the same total, but every cue whose moment has already gone by is
     * marked delivered rather than fired: switching a family on halfway through a rest must not
     * make the phone suddenly sound three cues it owed in the past.
     */
    fun replan(
        state: TimerState.Running,
        settings: CueSettings,
        nowElapsedMs: Long,
    ): TimerState.Running {
        val plan = CuePlanner.plan(state.phase, state.totalMs, settings)
        val alreadyGone = plan
            .filter { state.deadlineElapsedMs - it.remainingMs <= nowElapsedMs }
            .map { it.cue }
        return state.copy(plan = plan, delivered = state.delivered + alreadyGone)
    }

    /** The same, for a countdown that is paused: "already gone" is measured against what is left. */
    fun replan(state: TimerState.Paused, settings: CueSettings): TimerState.Paused {
        val plan = CuePlanner.plan(state.phase, state.totalMs, settings)
        val alreadyGone = plan.filter { it.remainingMs >= state.remainingMs }.map { it.cue }
        return state.copy(plan = plan, delivered = state.delivered + alreadyGone)
    }
}
