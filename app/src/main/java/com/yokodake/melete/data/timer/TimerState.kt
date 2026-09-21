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
 * One **interval** — one work phase or one rest — is one [runId]. A [TimerProgram] of several sets
 * is therefore a succession of run ids rather than one long one, which is what keeps the
 * at-most-once cue bookkeeping honest: the second set's end is a different run from the first
 * set's, so it is allowed to sound.
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
        val program: TimerProgram = TimerProgram(),
        /** Which set of [program] this interval belongs to, counted from zero. */
        val setIndex: Int = 0,
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
        val program: TimerProgram = TimerProgram(),
        val setIndex: Int = 0,
    ) : TimerState

    /**
     * A set of repetitions is under way and nothing is counting.
     *
     * Reps cannot be timed without inventing a number, so the timer waits here and the athlete
     * says when the set is done; the rest then starts on its own. This is a deliberate stop in an
     * otherwise automatic sequence, not a countdown with an unknown length.
     */
    data class AwaitingSet(
        val runId: String,
        val program: TimerProgram,
        val setIndex: Int,
    ) : TimerState

    /** The program ran out. Reaching the end records nothing: it is a cue, not performed work. */
    data class Finished(
        val runId: String,
        val phase: TimerPhase,
        val totalMs: Long,
        val program: TimerProgram = TimerProgram(),
        /** How many sets were actually counted through. */
        val setsCompleted: Int = 1,
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
        val program: TimerProgram = TimerProgram(),
    ) : TimerState

    val activeRunId: String?
        get() = when (this) {
            is Running -> runId
            is Paused -> runId
            is AwaitingSet -> runId
            is Finished -> runId
            is Interrupted -> runId
            Idle -> null
        }

    /** The program being counted, when one is. */
    val activeProgram: TimerProgram?
        get() = when (this) {
            is Running -> program
            is Paused -> program
            is AwaitingSet -> program
            is Finished -> program
            is Interrupted -> program
            Idle -> null
        }

    /**
     * What this countdown is for, when it was started from an exercise. Named apart from the
     * program's own `label` so a caller does not have to unwrap the state first.
     */
    val activeLabel: String? get() = activeProgram?.label

    /** Which set is on, counted from one for display. Null when nothing is running. */
    val currentSet: Int?
        get() = when (this) {
            is Running -> setIndex + 1
            is Paused -> setIndex + 1
            is AwaitingSet -> setIndex + 1
            else -> null
        }
}

/** The arithmetic and the sequencing, kept pure so both can be tested without a device. */
object TimerTransitions {

    /**
     * Begins [program]. The first thing that happens depends on what the first set is made of: a
     * countdown for timed work, a wait for reps, or the rest itself when there is no work phase.
     */
    fun startProgram(
        runId: String,
        program: TimerProgram,
        settings: CueSettings,
        nowElapsedMs: Long,
    ): TimerState = enterSet(runId, program, setIndex = 0, settings = settings, nowElapsedMs = nowElapsedMs)

    /** The single-interval start, kept for a bare rest or one timed set. */
    fun start(
        runId: String,
        phase: TimerPhase,
        durationMs: Long,
        settings: CueSettings,
        nowElapsedMs: Long,
        label: String? = null,
    ): TimerState.Running {
        val seconds = (durationMs / 1000).toInt().coerceAtLeast(1)
        val program = when (phase) {
            TimerPhase.WORK -> TimerProgram.work(seconds, label)
            TimerPhase.REST -> TimerProgram.rest(seconds, label)
        }
        return TimerState.Running(
            runId = runId,
            phase = phase,
            totalMs = durationMs,
            deadlineElapsedMs = nowElapsedMs + durationMs,
            plan = CuePlanner.plan(phase, durationMs, settings),
            program = program,
            setIndex = 0,
        )
    }

    /** Opens set [setIndex] of [program]: its work phase, or its rest when there is no work. */
    private fun enterSet(
        runId: String,
        program: TimerProgram,
        setIndex: Int,
        settings: CueSettings,
        nowElapsedMs: Long,
    ): TimerState = when (program.work) {
        WorkKind.REPS -> TimerState.AwaitingSet(runId, program, setIndex)

        WorkKind.NONE -> countdown(
            runId, TimerPhase.REST, program.restSeconds, program, setIndex, settings, nowElapsedMs
        )

        WorkKind.TIMED -> countdown(
            runId, TimerPhase.WORK, program.workSeconds, program, setIndex, settings, nowElapsedMs
        )
    }

    private fun countdown(
        runId: String,
        phase: TimerPhase,
        seconds: Int,
        program: TimerProgram,
        setIndex: Int,
        settings: CueSettings,
        nowElapsedMs: Long,
    ): TimerState.Running {
        val durationMs = seconds.coerceAtLeast(1) * 1000L
        return TimerState.Running(
            runId = runId,
            phase = phase,
            totalMs = durationMs,
            deadlineElapsedMs = nowElapsedMs + durationMs,
            plan = CuePlanner.plan(phase, durationMs, settings),
            program = program,
            setIndex = setIndex,
        )
    }

    /**
     * What happens once the interval on screen runs out: the rest that follows this set, the work
     * of the next one, or the end of the program.
     *
     * [nextRunId] becomes the identity of whatever comes next. Each interval is its own run, so
     * the cue bookkeeping that stops one beep being sounded twice does not also stop the next
     * set's beep from sounding at all.
     */
    fun advance(
        state: TimerState,
        settings: CueSettings,
        nowElapsedMs: Long,
        nextRunId: String,
    ): TimerState {
        val (program, setIndex, phase, totalMs, runId) = when (state) {
            is TimerState.Running ->
                Interval(state.program, state.setIndex, state.phase, state.totalMs, state.runId)

            is TimerState.Paused ->
                Interval(state.program, state.setIndex, state.phase, state.totalMs, state.runId)

            else -> return state
        }
        val finished = TimerState.Finished(
            runId = runId,
            phase = phase,
            totalMs = totalMs,
            program = program,
            setsCompleted = program.sets,
        )
        return when (phase) {
            // Work just ended. A rest follows only when another set is still to come; with no
            // rest configured the next set starts immediately, which is still a next set.
            TimerPhase.WORK -> when {
                program.restFollows(setIndex) -> countdown(
                    nextRunId, TimerPhase.REST, program.restSeconds,
                    program, setIndex, settings, nowElapsedMs,
                )

                setIndex + 1 < program.sets ->
                    enterSet(nextRunId, program, setIndex + 1, settings, nowElapsedMs)

                else -> finished.copy(setsCompleted = setIndex + 1)
            }

            // Rest just ended. On to the next set, if there is one.
            TimerPhase.REST ->
                if (setIndex + 1 < program.sets) {
                    enterSet(nextRunId, program, setIndex + 1, settings, nowElapsedMs)
                } else {
                    finished.copy(setsCompleted = program.sets)
                }
        }
    }

    /**
     * The athlete says the reps are done. The rest starts by itself from here, which is the whole
     * point: the one thing they have to do between sets is say that a set happened.
     */
    fun completeSet(
        state: TimerState.AwaitingSet,
        settings: CueSettings,
        nowElapsedMs: Long,
        nextRunId: String,
    ): TimerState {
        val program = state.program
        return if (program.restFollows(state.setIndex)) {
            countdown(
                nextRunId, TimerPhase.REST, program.restSeconds,
                program, state.setIndex, settings, nowElapsedMs,
            )
        } else if (state.setIndex + 1 < program.sets) {
            // No rest configured: straight into the next set.
            enterSet(nextRunId, program, state.setIndex + 1, settings, nowElapsedMs)
        } else {
            TimerState.Finished(
                runId = state.runId,
                phase = TimerPhase.WORK,
                totalMs = 0,
                program = program,
                setsCompleted = program.sets,
            )
        }
    }

    fun pause(state: TimerState.Running, nowElapsedMs: Long): TimerState.Paused = TimerState.Paused(
        runId = state.runId,
        phase = state.phase,
        totalMs = state.totalMs,
        remainingMs = state.remainingMs(nowElapsedMs),
        plan = state.plan,
        // Carried across the pause: cues already given must not be given again on resume.
        delivered = state.delivered,
        program = state.program,
        setIndex = state.setIndex,
    )

    fun resume(state: TimerState.Paused, nowElapsedMs: Long): TimerState.Running = TimerState.Running(
        runId = state.runId,
        phase = state.phase,
        totalMs = state.totalMs,
        deadlineElapsedMs = nowElapsedMs + state.remainingMs,
        plan = state.plan,
        delivered = state.delivered,
        program = state.program,
        setIndex = state.setIndex,
    )

    /** Ends the whole program here, whatever set it was on. */
    fun finish(state: TimerState): TimerState = when (state) {
        is TimerState.Running -> TimerState.Finished(
            state.runId, state.phase, state.totalMs, state.program, state.setIndex + 1,
        )

        is TimerState.Paused -> TimerState.Finished(
            state.runId, state.phase, state.totalMs, state.program, state.setIndex + 1,
        )

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

    private data class Interval(
        val program: TimerProgram,
        val setIndex: Int,
        val phase: TimerPhase,
        val totalMs: Long,
        val runId: String,
    )
}
