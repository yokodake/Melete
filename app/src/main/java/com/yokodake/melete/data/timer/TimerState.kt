package com.yokodake.melete.data.timer

/** What a countdown is counting. */
enum class TimerPhase {
    /**
     * The few seconds before a set: time to get to the bar and take the weight.
     *
     * A phase rather than a flag, because it is a real countdown — its 3-2-1 falls out of the
     * ordinary cue planner with no special case — and because everything that asks "what is on
     * screen" then gets a real answer instead of a work interval that is lying about having
     * started.
     */
    PREPARE,

    /** The work interval itself. */
    WORK,

    /** The rest after it. */
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
 * One **interval** — one work phase, one rest, one preparation — is one [runId], so a
 * [TimerProgram] of several sets is a succession of them. That also means the `delivered` set
 * empties by itself at every interval boundary, which is the whole of the at-most-once cue
 * bookkeeping: the second set's end is a different state from the first set's, so it is free to
 * sound.
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
     * How long after an interval starts that "previous" still means the interval before it.
     *
     * Short on purpose: past this, pressing back means "let me do this one again", which is the
     * far commoner intent once a countdown has been running for a while.
     */
    const val RESTART_WINDOW_MS: Long = 1_000

    /**
     * Begins [program] at its first interval, with a preparation countdown if that interval is
     * timed work — a set that starts the instant you press a button starts without you.
     */
    fun startProgram(
        runId: String,
        program: TimerProgram,
        settings: CueSettings,
        nowElapsedMs: Long,
    ): TimerState = enterStep(runId, program, stepIndex = 0, settings, nowElapsedMs, prepare = true)

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
            TimerPhase.REST -> TimerProgram.rest(seconds, label)
            else -> TimerProgram.work(seconds, label)
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

    /**
     * Opens one interval of the program.
     *
     * [prepare] asks for the lead-in countdown, and is honoured only where it means something:
     * before timed work. A rest needs no preparing, and a set of reps does not start on its own,
     * so there is nothing to be late for.
     */
    private fun enterStep(
        runId: String,
        program: TimerProgram,
        stepIndex: Int,
        settings: CueSettings,
        nowElapsedMs: Long,
        prepare: Boolean,
    ): TimerState {
        val step = program.steps.getOrNull(stepIndex) ?: return TimerState.Finished(
            runId = runId,
            phase = TimerPhase.REST,
            totalMs = 0,
            program = program,
            setsCompleted = program.sets,
        )
        return when {
            step.phase == TimerPhase.REST -> countdown(
                runId, TimerPhase.REST, program.restSeconds,
                program, step.setIndex, settings, nowElapsedMs,
            )

            program.work == WorkKind.REPS ->
                TimerState.AwaitingSet(runId, program, step.setIndex)

            prepare -> countdown(
                runId, TimerPhase.PREPARE, TimerProgram.PREPARE_SECONDS,
                program, step.setIndex, settings, nowElapsedMs,
            )

            else -> countdown(
                runId, TimerPhase.WORK, program.workSeconds,
                program, step.setIndex, settings, nowElapsedMs,
            )
        }
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

    /** Which interval of the program a state is sitting on. */
    private fun stepIndexOf(state: TimerState): Int = when (state) {
        is TimerState.Running -> state.program.stepIndexOf(state.setIndex, state.phase)
        is TimerState.Paused -> state.program.stepIndexOf(state.setIndex, state.phase)
        is TimerState.AwaitingSet -> state.program.stepIndexOf(state.setIndex, TimerPhase.WORK)
        else -> -1
    }

    /**
     * Whether the interval at [stepIndex] needs a lead-in, given what ran before it.
     *
     * A rest that is long enough already serves as the preparation — its last seconds are the
     * ones the screen turns amber for — so following it with five more would be five seconds of
     * standing around. Anything else that lands on a set does need them.
     */
    private fun needsPreparation(program: TimerProgram, stepIndex: Int): Boolean {
        val previous = program.steps.getOrNull(stepIndex - 1) ?: return true
        return !(previous.phase == TimerPhase.REST &&
            program.restSeconds >= TimerProgram.PREPARE_SECONDS)
    }

    /**
     * What happens once the interval on screen runs out: the work a preparation was for, the rest
     * that follows this set, the next set, or the end of the program.
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
        val interval = intervalOf(state) ?: return state
        val program = interval.program

        // A preparation countdown ends by handing over to the set it was preparing for.
        if (interval.phase == TimerPhase.PREPARE) {
            return countdown(
                nextRunId, TimerPhase.WORK, program.workSeconds,
                program, interval.setIndex, settings, nowElapsedMs,
            )
        }

        val stepIndex = program.stepIndexOf(interval.setIndex, interval.phase)
        val nextStep = stepIndex + 1
        if (stepIndex < 0 || nextStep >= program.steps.size) {
            return TimerState.Finished(
                runId = interval.runId,
                phase = interval.phase,
                totalMs = interval.totalMs,
                program = program,
                setsCompleted = interval.setIndex + 1,
            )
        }
        return enterStep(
            nextRunId, program, nextStep, settings, nowElapsedMs,
            prepare = needsPreparation(program, nextStep),
        )
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
        val stepIndex = program.stepIndexOf(state.setIndex, TimerPhase.WORK)
        val nextStep = stepIndex + 1
        if (stepIndex < 0 || nextStep >= program.steps.size) {
            return TimerState.Finished(
                runId = state.runId,
                phase = TimerPhase.WORK,
                totalMs = 0,
                program = program,
                setsCompleted = program.sets,
            )
        }
        return enterStep(
            nextRunId, program, nextStep, settings, nowElapsedMs,
            prepare = needsPreparation(program, nextStep),
        )
    }

    /** Skips forward one interval. */
    fun next(
        state: TimerState,
        settings: CueSettings,
        nowElapsedMs: Long,
        nextRunId: String,
    ): TimerState {
        val interval = intervalOf(state) ?: return state
        val current = interval.program.stepIndexOf(interval.setIndex, interval.phase)
        if (current < 0) return state
        return goToStep(interval, current + 1, settings, nowElapsedMs, nextRunId)
    }

    /**
     * Goes back, the way a music player does: this interval from the top, unless you pressed it
     * straight after the interval started, in which case you meant the one before.
     *
     * Restarting is what a mis-tap nearly always wants — you are a minute into a rest and you hit
     * the wrong thing, and losing the minute is worse than losing nothing. Wanting the previous
     * interval is real too, so the short window at the start says so unambiguously without a
     * second control.
     *
     * A set of reps has no elapsed time to measure, so there is no window: nothing is counting,
     * restarting it would do nothing visible, and going back is the only thing it could mean.
     */
    fun previous(
        state: TimerState,
        settings: CueSettings,
        nowElapsedMs: Long,
        nextRunId: String,
    ): TimerState {
        val interval = intervalOf(state) ?: return state
        val current = interval.program.stepIndexOf(interval.setIndex, interval.phase)
        if (current < 0) return state
        val elapsed = elapsedMs(state, nowElapsedMs)
        val target = if (elapsed != null && elapsed > RESTART_WINDOW_MS) current else current - 1
        return goToStep(interval, target, settings, nowElapsedMs, nextRunId)
    }

    /**
     * Moves to an interval by index, clamping at the start and ending the program past the end.
     *
     * Landing on a set this way always gets the preparation countdown, whichever direction you
     * came from: you pressed a button, so the set is about to start on your say-so rather than
     * flowing out of a rest that gave you time to get ready.
     */
    private fun goToStep(
        interval: Interval,
        stepIndex: Int,
        settings: CueSettings,
        nowElapsedMs: Long,
        nextRunId: String,
    ): TimerState {
        val program = interval.program
        if (stepIndex >= program.steps.size) {
            return TimerState.Finished(
                runId = interval.runId,
                phase = interval.phase,
                totalMs = interval.totalMs,
                program = program,
                setsCompleted = program.sets,
            )
        }
        return enterStep(
            nextRunId, program, stepIndex.coerceAtLeast(0), settings, nowElapsedMs, prepare = true,
        )
    }

    /** How far into its interval a state is, or null when nothing is counting. */
    private fun elapsedMs(state: TimerState, nowElapsedMs: Long): Long? = when (state) {
        is TimerState.Running -> state.totalMs - state.remainingMs(nowElapsedMs)
        is TimerState.Paused -> state.totalMs - state.remainingMs
        else -> null
    }

    /** The interval a state is on, whatever kind of state it is. */
    private fun intervalOf(state: TimerState): Interval? = when (state) {
        is TimerState.Running ->
            Interval(state.program, state.setIndex, state.phase, state.totalMs, state.runId)

        is TimerState.Paused ->
            Interval(state.program, state.setIndex, state.phase, state.totalMs, state.runId)

        is TimerState.AwaitingSet ->
            Interval(state.program, state.setIndex, TimerPhase.WORK, 0, state.runId)

        else -> null
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

    /**
     * Picks a paused countdown back up.
     *
     * Resuming inside a set, or a preparation, just carries on — nothing special, because nothing
     * about the pause changed what you were doing. Resuming a rest with only a moment left is the
     * exception: unpausing straight into a set would give you no time to get back to the bar, so
     * a rest with less than the preparation left becomes a preparation instead. It is a fresh
     * interval with its own id and its own plan, rather than the old rest stretched, because the
     * old rest has already sounded some of its cues and stretching it would re-owe them.
     */
    fun resume(
        state: TimerState.Paused,
        nowElapsedMs: Long,
        settings: CueSettings = CueSettings(),
        nextRunId: String = state.runId,
    ): TimerState.Running {
        val program = state.program
        val nearlyOver = state.phase == TimerPhase.REST &&
            state.remainingMs < TimerProgram.PREPARE_SECONDS * 1000L
        if (nearlyOver) {
            val stepIndex = program.stepIndexOf(state.setIndex, TimerPhase.REST)
            val nextStep = program.steps.getOrNull(stepIndex + 1)
            // Only when a set actually follows. A bare rest that is nearly over is just nearly
            // over; there is nothing to be ready for.
            if (nextStep != null && nextStep.phase == TimerPhase.WORK &&
                program.work == WorkKind.TIMED
            ) {
                return countdown(
                    nextRunId, TimerPhase.PREPARE, TimerProgram.PREPARE_SECONDS,
                    program, nextStep.setIndex, settings, nowElapsedMs,
                )
            }
        }
        return TimerState.Running(
            runId = state.runId,
            phase = state.phase,
            totalMs = state.totalMs,
            deadlineElapsedMs = nowElapsedMs + state.remainingMs,
            plan = state.plan,
            delivered = state.delivered,
            program = program,
            setIndex = state.setIndex,
        )
    }

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
