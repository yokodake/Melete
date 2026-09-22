package com.yokodake.melete.data.timer

import com.yokodake.melete.data.entity.BodySide

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

    /** The rest after a set. */
    REST,

    /** Changing sides inside one unilateral set. A rest, with a different thing to say. */
    SWITCH,

    /** Between two pulses of a repeater. Short by nature. */
    REP_REST,

    /** Between two exercises of a circuit. */
    TRANSITION,

    /** After the last exercise of a circuit round. Replaces the transition there. */
    ROUND_REST;

    /**
     * Whether this is time off. Every kind of rest behaves the same to the cue planner and to the
     * preparation rules; they differ only in what the screen calls them.
     */
    val isRest: Boolean
        get() = this == REST || this == SWITCH || this == REP_REST ||
            this == TRANSITION || this == ROUND_REST
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
 *
 * Where the run is, is one number: [stepIndex] into [TimerProgram.steps]. A preparation points at
 * the step it is preparing for, which is what lets the screen say *get ready, set two* and lets
 * skipping forward mean the same thing from anywhere.
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
        /** Which interval of [program] this is, or the one a preparation is leading into. */
        val stepIndex: Int = 0,
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
        val stepIndex: Int = 0,
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
        val stepIndex: Int,
    ) : TimerState

    /** The program ran out. Reaching the end records nothing: it is a cue, not performed work. */
    data class Finished(
        val runId: String,
        val phase: TimerPhase,
        val totalMs: Long,
        val program: TimerProgram = TimerProgram(),
        /** How many sets, or circuit rounds, were actually counted through. */
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

    /** Where in the program this state sits, when it sits anywhere. */
    val activeStepIndex: Int?
        get() = when (this) {
            is Running -> stepIndex
            is Paused -> stepIndex
            is AwaitingSet -> stepIndex
            else -> null
        }

    /**
     * The interval on screen. For a preparation this is the work it is leading into, which is the
     * thing the athlete needs named.
     */
    val currentStep: TimerStep?
        get() = activeStepIndex?.let { activeProgram?.steps?.getOrNull(it) }

    /**
     * What this countdown is for, when it was started from an exercise. Named apart from the
     * program's own `label` so a caller does not have to unwrap the state first.
     */
    val activeLabel: String? get() = activeProgram?.label

    /** The exercise on screen: the circuit station's name, or the program's own. */
    val currentExerciseLabel: String?
        get() {
            val program = activeProgram ?: return null
            val step = currentStep ?: return program.label
            return program.entries.getOrNull(step.entryIndex)?.label ?: program.label
        }

    /** Which set is on, counted from one for display. Null when nothing is running. */
    val currentSet: Int?
        get() {
            val program = activeProgram ?: return null
            val step = currentStep ?: return null
            return (if (program.isCircuit) step.roundIndex else step.setIndex) + 1
        }

    /** Which side is on, or null for bilateral work. */
    val currentSide: BodySide? get() = currentStep?.side

    /** Which repeater pulse is on, counted from one, or null outside a repeater. */
    val currentRep: Int? get() = currentStep?.repIndex?.plus(1)

    /** Which circuit station is on, counted from one, or null outside a circuit. */
    val currentStation: Int?
        get() = if (activeProgram?.isCircuit == true) currentStep?.entryIndex?.plus(1) else null
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
        val program = when {
            phase.isRest -> TimerProgram.rest(seconds, label)
            else -> TimerProgram.work(seconds, label)
        }
        return TimerState.Running(
            runId = runId,
            phase = phase,
            totalMs = durationMs,
            deadlineElapsedMs = nowElapsedMs + durationMs,
            plan = CuePlanner.plan(phase, durationMs, settings),
            program = program,
            stepIndex = 0,
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
            step.untimed -> TimerState.AwaitingSet(runId, program, stepIndex)

            step.phase == TimerPhase.WORK && prepare -> countdown(
                runId, TimerPhase.PREPARE, TimerProgram.PREPARE_SECONDS,
                program, stepIndex, settings, nowElapsedMs,
            )

            else -> countdown(
                runId, step.phase, step.seconds, program, stepIndex, settings, nowElapsedMs,
            )
        }
    }

    private fun countdown(
        runId: String,
        phase: TimerPhase,
        seconds: Int,
        program: TimerProgram,
        stepIndex: Int,
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
            stepIndex = stepIndex,
        )
    }

    /**
     * What happens once the interval on screen runs out: the work a preparation was for, the next
     * interval of the sequence, or the end of the program.
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

        // A preparation countdown ends by handing over to the interval it was preparing for.
        if (interval.phase == TimerPhase.PREPARE) {
            return enterStep(
                nextRunId, program, interval.stepIndex, settings, nowElapsedMs, prepare = false,
            )
        }

        return continueFrom(interval, settings, nowElapsedMs, nextRunId)
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
    ): TimerState = continueFrom(
        Interval(state.program, state.stepIndex, TimerPhase.WORK, 0, state.runId),
        settings,
        nowElapsedMs,
        nextRunId,
    )

    /** Moves on to the interval after this one, or ends the program when there is none. */
    private fun continueFrom(
        interval: Interval,
        settings: CueSettings,
        nowElapsedMs: Long,
        nextRunId: String,
    ): TimerState {
        val program = interval.program
        val next = interval.stepIndex + 1
        if (next >= program.steps.size) {
            return TimerState.Finished(
                runId = interval.runId,
                phase = interval.phase,
                totalMs = interval.totalMs,
                program = program,
                setsCompleted = program.setsCompletedAt(interval.stepIndex),
            )
        }
        return enterStep(
            nextRunId, program, next, settings, nowElapsedMs,
            prepare = ProgramSequencer.preparesInto(program.steps, next),
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
        return goToStep(interval, interval.stepIndex + 1, settings, nowElapsedMs, nextRunId)
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
        val elapsed = elapsedMs(state, nowElapsedMs)
        val target = if (elapsed != null && elapsed > RESTART_WINDOW_MS) {
            interval.stepIndex
        } else {
            interval.stepIndex - 1
        }
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
            Interval(state.program, state.stepIndex, state.phase, state.totalMs, state.runId)

        is TimerState.Paused ->
            Interval(state.program, state.stepIndex, state.phase, state.totalMs, state.runId)

        is TimerState.AwaitingSet ->
            Interval(state.program, state.stepIndex, TimerPhase.WORK, 0, state.runId)

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
        stepIndex = state.stepIndex,
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
        val nearlyOver = state.phase.isRest &&
            state.remainingMs < TimerProgram.PREPARE_SECONDS * 1000L
        if (nearlyOver) {
            val nextStep = program.steps.getOrNull(state.stepIndex + 1)
            // Only when timed work actually follows. A bare rest that is nearly over is just
            // nearly over; there is nothing to be ready for.
            if (nextStep != null && nextStep.phase == TimerPhase.WORK && !nextStep.untimed) {
                return countdown(
                    nextRunId, TimerPhase.PREPARE, TimerProgram.PREPARE_SECONDS,
                    program, state.stepIndex + 1, settings, nowElapsedMs,
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
            stepIndex = state.stepIndex,
        )
    }

    /** Ends the whole program here, whatever set it was on. */
    fun finish(state: TimerState): TimerState = when (state) {
        is TimerState.Running -> TimerState.Finished(
            state.runId,
            state.phase,
            state.totalMs,
            state.program,
            state.program.setsCompletedAt(state.stepIndex),
        )

        is TimerState.Paused -> TimerState.Finished(
            state.runId,
            state.phase,
            state.totalMs,
            state.program,
            state.program.setsCompletedAt(state.stepIndex),
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
        val stepIndex: Int,
        val phase: TimerPhase,
        val totalMs: Long,
        val runId: String,
    )
}
