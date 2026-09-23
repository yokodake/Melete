package com.yokodake.melete.data.timer

import com.yokodake.melete.data.entity.BodySide
import kotlinx.serialization.Serializable

/** How the working part of a set is counted. */
enum class WorkKind {
    /** A countdown: a hang, a plank, a carry. */
    TIMED,

    /**
     * Untimed. The set is repetitions, so only the athlete knows when it is over; the timer waits
     * for them to say so and then starts the rest. Counting reps for them would be a guess.
     */
    REPS,

    /** No work phase at all — the program is a bare rest, started between sets from the logger. */
    NONE,
}

/**
 * A repeater: one *set* is a series of short timed efforts with a short rest between them.
 *
 * Three sets of six seven-second efforts with three seconds between them is one prescription, not
 * eighteen sets. The pulses are the shape of the set, so they live here rather than being
 * flattened into the set count — flattening them would make the set rest fall in the wrong places
 * and would make the logger ask for eighteen loads.
 */
@Serializable
data class RepeaterSpec(
    val repsPerSet: Int,
    val workSecondsPerRep: Int,
    /** Between pulses only. After the last pulse the set rest takes over. */
    val restSecondsBetweenReps: Int = 0,
) {
    init {
        require(repsPerSet >= 1) { "a repeater needs at least one rep" }
        require(workSecondsPerRep >= 1) { "a repeater rep needs a length" }
        require(restSecondsBetweenReps >= 0) { "rep rest must not be negative" }
    }

    /** Length of one side's pulse sequence, rests between pulses included. */
    val sequenceSeconds: Int
        get() = repsPerSet * workSecondsPerRep + restSecondsBetweenReps * (repsPerSet - 1)
}

/**
 * One exercise as the timer executes it.
 *
 * A standalone program has exactly one of these; a circuit has one per station. Everything that
 * varies per exercise lives here — unilateral execution, the repeater shape, the set rest — so a
 * circuit can mix a timed hang, a set of reps and a repeater without any of them being a special
 * case in the sequencer.
 */
@Serializable
data class TimerEntry(
    val label: String? = null,
    val work: WorkKind = WorkKind.REPS,
    /** Number of target reps of one work set. Meaningful only when [work] is [WorkKind.REPS]. */
    val workReps: Int? = null,
    /** Length of one work interval. Meaningful only when [work] is [WorkKind.TIMED]. */
    val workSeconds: Int = 0,
    /** Sets of this exercise. Ignored inside a circuit, where one round executes one set. */
    val sets: Int = 1,
    /** Rest between sets. Ignored inside a circuit, which rests between exercises and rounds. */
    val restSeconds: Int = 0,
    /** Both sides are performed one after the other, left first, inside one prescribed set. */
    val unilateral: Boolean = false,
    /** How long you get to change sides. Zero runs them back to back. */
    val sideSwitchSeconds: Int = TimerProgram.DEFAULT_SIDE_SWITCH_SECONDS,
    /** When set, the timed set is a series of pulses rather than one interval. */
    val repeater: RepeaterSpec? = null,
    /**
     * The planned copy this entry came from, so the timer can *offer* the logger when it ends.
     * Navigation only: the timer has no write path into the training record.
     */
    val occurrenceId: String? = null,
) {
    init {
        require(sets >= 1) { "a program needs at least one set" }
        require(work != WorkKind.TIMED || repeater != null || workSeconds >= 1) {
            "a timed set needs a length"
        }
        require(work != WorkKind.NONE || restSeconds >= 1) {
            "a program with no work has to be counting something"
        }
        require(restSeconds >= 0) { "rest must not be negative" }
        require(sideSwitchSeconds >= 0) { "the side switch must not be negative" }
        require(repeater == null || work == WorkKind.TIMED) {
            "a repeater is a shape of timed work"
        }
    }
}

/**
 * One interval of a program, fully described.
 *
 * Every question the screen, the notification and the sequencer ask — which exercise, which set,
 * which round, which side, which pulse — is answered by the step itself rather than recomputed
 * from a set of rules about what follows what. Sequencing, skipping forward and going back are
 * then all the same operation on an index into [TimerProgram.steps].
 *
 * A preparation countdown is deliberately **not** a step: it is a lead-in to one, and skipping
 * through the program should not stop twice per set.
 */
@Serializable
data class TimerStep(
    val phase: TimerPhase,
    /** Length in seconds. Zero exactly when [untimed]. */
    val seconds: Int,
    /** A set of repetitions: nothing counts, and the athlete says when it is done. */
    val untimed: Boolean = false,
    /** Which exercise of a circuit. Always 0 in a standalone program. */
    val entryIndex: Int = 0,
    /** Which circuit round. Always 0 outside a circuit. */
    val roundIndex: Int = 0,
    /** Which set of the entry. Always 0 inside a circuit, where one round is one set. */
    val setIndex: Int = 0,
    /** Which repeater pulse, or null when this step is not part of a repeater sequence. */
    val repIndex: Int? = null,
    /**
     * Which side, or null for bilateral work. A [TimerPhase.SWITCH] carries the side being
     * switched *to*, which is what the screen needs to say.
     */
    val side: BodySide? = null,
) {
    /** Whether two steps belong to the same side of the same set of the same exercise. */
    fun sameBlockAs(other: TimerStep): Boolean =
        entryIndex == other.entryIndex &&
            roundIndex == other.roundIndex &&
            setIndex == other.setIndex &&
            side == other.side
}

/**
 * What the timer is counting: a sequence of intervals, not a single countdown.
 *
 * A single countdown was the wrong shape for training. Real work is *four sets of eight with a
 * minute between them*, or *three sets of six seven-second pulses*, or *four exercises round and
 * round three times*. The program holds the whole shape and flattens it into [steps] once, so the
 * sequencing logic never has to know which of those it is looking at.
 *
 * Rest follows a set only when another set is still to come: a trailing rest after the last one is
 * time the app invented. A program whose work is [WorkKind.NONE] is the exception — there the rest
 * *is* the content.
 */
@Serializable
data class TimerProgram(
    val entries: List<TimerEntry>,
    /** Circuit rounds. One for a standalone program. */
    val rounds: Int = 1,
    /**
     * True when the entries are a circuit: one set of each, in order, [rounds] times over.
     * A circuit deliberately does not multiply each entry's own set count by the round count.
     */
    val isCircuit: Boolean = false,
    /** Rest between two exercises of a circuit. */
    val transitionSeconds: Int = 0,
    /** Rest after the last exercise of a round. Replaces the transition rest there. */
    val roundRestSeconds: Int = 0,
    /** The exercise, or the routine, this was started from. */
    val label: String? = null,
    /**
     * The planned copy this was started from, so the timer can *offer* the logger when it ends.
     *
     * Navigation only. The timer still records nothing and has no write path into the training
     * record — reaching zero is a cue, not evidence that the work happened. Carrying an id to
     * open a screen with is a different thing from being able to change what that screen shows.
     */
    val occurrenceId: String? = null,
    /**
     * The scheduled circuit this was started from, so the timer can *offer* its review when it
     * ends. Navigation only, like [occurrenceId]: reaching the end is a cue, never a record.
     */
    val circuitInstanceId: String? = null,
) {
    init {
        require(entries.isNotEmpty()) { "a program needs something to count" }
        require(rounds >= 1) { "a circuit needs at least one round" }
        require(transitionSeconds >= 0) { "rest must not be negative" }
        require(roundRestSeconds >= 0) { "rest must not be negative" }
    }

    /**
     * The intervals this program is made of, in order, as a flat list.
     *
     * Built once and cached: it is pure arithmetic over the entries, and every transition asks for
     * it. Not serialized — it is derived, and a snapshot that carried it could disagree with the
     * entries it was built from.
     */
    val steps: List<TimerStep> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ProgramSequencer.build(this)
    }

    /** The single entry of a standalone program, or the first station of a circuit. */
    val entry: TimerEntry get() = entries.first()

    // The standalone shorthands. A circuit answers about its rounds, which is the thing that
    // plays the same part there; anything that needs the per-station numbers reads [entries].

    val sets: Int get() = if (isCircuit) rounds else entry.sets
    val work: WorkKind get() = entry.work
    val workSeconds: Int get() = entry.workSeconds
    val workReps: Int? get() = entry.workReps
    val restSeconds: Int get() = if (isCircuit) roundRestSeconds else entry.restSeconds
    val isRepsMode: Boolean get() = !isCircuit && work == WorkKind.REPS
    val hasUnilateral: Boolean get() = entries.any { it.unilateral }
    val hasRepeater: Boolean get() = entries.any { it.repeater != null }

    /** True when [setIndex] is the last set, and so is not followed by a rest. */
    fun isLastSet(setIndex: Int): Boolean = setIndex >= sets - 1

    /** Whether a rest follows the work of [setIndex]. */
    fun restFollows(setIndex: Int): Boolean =
        work != WorkKind.NONE && restSeconds > 0 && !isLastSet(setIndex)

    /** The interval after the one at [stepIndex], or null when the program ends there. */
    fun stepAfter(stepIndex: Int): TimerStep? = steps.getOrNull(stepIndex + 1)

    /**
     * How many sets — or circuit rounds — a run that stopped on [stepIndex] got through.
     * Past the end of the program, all of them.
     */
    fun setsCompletedAt(stepIndex: Int): Int {
        val step = steps.getOrNull(stepIndex) ?: return sets
        return (if (isCircuit) step.roundIndex else step.setIndex) + 1
    }

/**
     * Whether every interval has a length, so a total can be stated without inventing one.
     *
     * Named, rather than left as "is [totalSeconds] null", because callers ask this question for
     * its own sake — whether to offer a total at all — and a null standing in for a boolean is a
     * thing the next reader has to decode.
     */
    val isFullyTimed: Boolean get() = steps.none { it.untimed }

    /**
     * How long the whole thing takes, or null when a set is untimed and the answer would be a
     * fabrication. Rests are counted only where one actually falls; the preparation countdowns are
     * not, because this is the length of what is *counted* — see [estimatedSeconds] for the
     * planning number.
     */
    val totalSeconds: Int?
        get() = if (isFullyTimed) steps.sumOf { it.seconds } else null

    /**
     * Just the work, with every rest excluded: the number a "time under tension" total is made of.
     * A set that is untimed by nature contributes nothing rather than a guess.
     */
    val workOnlySeconds: Int
        get() = steps.filter { it.phase == TimerPhase.WORK }.sumOf { it.seconds }

    /**
     * How long this will take in practice, for planning: every interval, the preparation
     * countdowns the sequence actually generates, and a documented assumption for the sets that
     * have no honest length.
     *
     * Separate from [totalSeconds] on purpose. That one refuses to answer rather than invent;
     * this one is allowed to estimate, and says so by taking its assumptions as arguments.
     */
    fun estimatedSeconds(
        secondsPerRep: Int = ASSUMED_SECONDS_PER_REP,
        secondsPerUntimedSet: Int = ASSUMED_SECONDS_PER_SET,
    ): Int = estimatedSecondsByEntry(secondsPerRep, secondsPerUntimedSet).sum()

    /**
     * The same estimate, divided between the entries: one number per exercise, in entry order.
     *
     * Every generated segment is allocated exactly once. Work, the rest between repeater pulses
     * and the side switch belong to the exercise being performed; the transition and the round
     * rest belong to the exercise they follow, because that is the station you are walking away
     * from. Summing this list gives [estimatedSeconds] back, which is what stops a circuit's time
     * being counted twice — once as a whole and again as its parts.
     */
    fun estimatedSecondsByEntry(
        secondsPerRep: Int = ASSUMED_SECONDS_PER_REP,
        secondsPerUntimedSet: Int = ASSUMED_SECONDS_PER_SET,
    ): List<Int> {
        val shares = IntArray(entries.size)
        steps.indices.forEach { index ->
            val step = steps[index]
            val lead = if (ProgramSequencer.preparesInto(steps, index)) PREPARE_SECONDS else 0
            val body = if (step.untimed) {
                entries.getOrNull(step.entryIndex)?.workReps?.let { it * secondsPerRep }
                    ?: secondsPerUntimedSet
            } else {
                step.seconds
            }
            val owner = step.entryIndex.coerceIn(0, shares.lastIndex)
            shares[owner] += lead + body
        }
        return shares.toList()
    }

    companion object {
        /**
         * How long you get to chalk up, find the edge and take the weight before a set starts.
         *
         * It exists because a set that begins the instant you press a button begins without you.
         * It is not needed when a rest runs into the set on its own: the tail of the rest already
         * *is* the preparation, which is why the screen turns amber for those last seconds.
         */
        const val PREPARE_SECONDS = 5

        /** How long changing sides takes, unless the prescription says otherwise. */
        const val DEFAULT_SIDE_SWITCH_SECONDS = 15

        /**
         * The documented assumptions behind an estimated duration.
         *
         * Three seconds a rep is a slow-ish ordinary tempo; thirty seconds stands for a set whose
         * rep count is not even stated. Both are deliberately modest — an estimate that runs long
         * makes a week look fuller than it was, which is the error that matters here.
         */
        const val ASSUMED_SECONDS_PER_REP = 3
        const val ASSUMED_SECONDS_PER_SET = 30

        /**
         * A standalone program: one exercise, so many sets, this long each, that much rest.
         *
         * Spelled as an `invoke` so the ordinary single-exercise case still reads
         * `TimerProgram(sets = 3, work = TIMED, workSeconds = 7)`, while the primary constructor
         * takes the list of entries a circuit is made of. The two never collide, because this one
         * has no `entries` parameter.
         */
        operator fun invoke(
            sets: Int = 1,
            work: WorkKind = WorkKind.REPS,
            workReps: Int? = null,
            workSeconds: Int = 0,
            restSeconds: Int = 0,
            unilateral: Boolean = false,
            sideSwitchSeconds: Int = DEFAULT_SIDE_SWITCH_SECONDS,
            repeater: RepeaterSpec? = null,
            label: String? = null,
            occurrenceId: String? = null,
        ): TimerProgram = TimerProgram(
            entries = listOf(
                TimerEntry(
                    label = label,
                    work = work,
                    workReps = workReps,
                    workSeconds = workSeconds,
                    sets = sets,
                    restSeconds = restSeconds,
                    unilateral = unilateral,
                    sideSwitchSeconds = sideSwitchSeconds,
                    repeater = repeater,
                    occurrenceId = occurrenceId,
                )
            ),
            label = label,
            occurrenceId = occurrenceId,
        )

        /** A circuit: one set of each entry, in order, [rounds] times over. */
        fun circuit(
            entries: List<TimerEntry>,
            rounds: Int,
            transitionSeconds: Int = 0,
            roundRestSeconds: Int = 0,
            label: String? = null,
            occurrenceId: String? = null,
            circuitInstanceId: String? = null,
        ): TimerProgram = TimerProgram(
            entries = entries,
            rounds = rounds,
            isCircuit = true,
            transitionSeconds = transitionSeconds,
            roundRestSeconds = roundRestSeconds,
            label = label,
            occurrenceId = occurrenceId,
            circuitInstanceId = circuitInstanceId,
        )

        /** One set, nothing claimed about it. The fallback for a state that predates programs. */
        val SINGLE = TimerProgram()

        /** A bare rest, the shape the logger starts between two sets. */
        fun rest(seconds: Int, label: String? = null, occurrenceId: String? = null) =
            TimerProgram(
                sets = 1,
                work = WorkKind.NONE,
                restSeconds = seconds,
                label = label,
                occurrenceId = occurrenceId,
            )

        /** A single timed interval with nothing after it. */
        fun work(seconds: Int, label: String? = null) =
            TimerProgram(sets = 1, work = WorkKind.TIMED, workSeconds = seconds, label = label)
    }
}

/**
 * Flattens a program into the intervals it will actually run.
 *
 * All the structural rules live here and nowhere else: no trailing rest, both sides inside one
 * set, pulses inside one side, the round rest standing in for the transition after the last
 * station. Everything downstream walks a list.
 */
object ProgramSequencer {

    fun build(program: TimerProgram): List<TimerStep> =
        if (program.isCircuit) circuit(program) else standalone(program)

    private fun standalone(program: TimerProgram): List<TimerStep> = buildList {
        val entry = program.entry
        for (setIndex in 0 until entry.sets) {
            if (entry.work == WorkKind.NONE) {
                add(TimerStep(TimerPhase.REST, entry.restSeconds, setIndex = setIndex))
                continue
            }
            addAll(setBlock(entry, entryIndex = 0, roundIndex = 0, setIndex = setIndex))
            if (entry.restSeconds > 0 && setIndex < entry.sets - 1) {
                add(TimerStep(TimerPhase.REST, entry.restSeconds, setIndex = setIndex))
            }
        }
    }

    /**
     * A round executes one set of each entry in order. The entry's own set count is deliberately
     * *not* multiplied by the round count: a circuit says how many times round, and that is the
     * only volume number it has.
     */
    private fun circuit(program: TimerProgram): List<TimerStep> = buildList {
        for (round in 0 until program.rounds) {
            program.entries.forEachIndexed { entryIndex, entry ->
                addAll(setBlock(entry, entryIndex, roundIndex = round, setIndex = 0))
                val lastEntry = entryIndex == program.entries.lastIndex
                val lastRound = round == program.rounds - 1
                when {
                    !lastEntry && program.transitionSeconds > 0 -> add(
                        TimerStep(
                            phase = TimerPhase.TRANSITION,
                            seconds = program.transitionSeconds,
                            entryIndex = entryIndex,
                            roundIndex = round,
                        )
                    )

                    // The round rest replaces the transition after the final station, and there is
                    // none at all after the final round.
                    lastEntry && !lastRound && program.roundRestSeconds > 0 -> add(
                        TimerStep(
                            phase = TimerPhase.ROUND_REST,
                            seconds = program.roundRestSeconds,
                            entryIndex = entryIndex,
                            roundIndex = round,
                        )
                    )
                }
            }
        }
    }

    /** One prescribed set. Unilateral work performs both sides inside it, left first. */
    private fun setBlock(
        entry: TimerEntry,
        entryIndex: Int,
        roundIndex: Int,
        setIndex: Int,
    ): List<TimerStep> = buildList {
        if (!entry.unilateral) {
            addAll(workBlock(entry, entryIndex, roundIndex, setIndex, side = null))
            return@buildList
        }
        addAll(workBlock(entry, entryIndex, roundIndex, setIndex, BodySide.LEFT))
        if (entry.sideSwitchSeconds > 0) {
            add(
                TimerStep(
                    phase = TimerPhase.SWITCH,
                    seconds = entry.sideSwitchSeconds,
                    entryIndex = entryIndex,
                    roundIndex = roundIndex,
                    setIndex = setIndex,
                    side = BodySide.RIGHT,
                )
            )
        }
        addAll(workBlock(entry, entryIndex, roundIndex, setIndex, BodySide.RIGHT))
    }

    /** One side's work: a pulse sequence, one timed interval, or one untimed set of reps. */
    private fun workBlock(
        entry: TimerEntry,
        entryIndex: Int,
        roundIndex: Int,
        setIndex: Int,
        side: BodySide?,
    ): List<TimerStep> = buildList {
        val repeater = entry.repeater
        fun step(phase: TimerPhase, seconds: Int, untimed: Boolean = false, rep: Int? = null) =
            TimerStep(
                phase = phase,
                seconds = seconds,
                untimed = untimed,
                entryIndex = entryIndex,
                roundIndex = roundIndex,
                setIndex = setIndex,
                repIndex = rep,
                side = side,
            )
        when {
            repeater != null -> for (rep in 0 until repeater.repsPerSet) {
                add(step(TimerPhase.WORK, repeater.workSecondsPerRep, rep = rep))
                // Rep rest exists between pulses only; after the last one the set rest takes over.
                if (rep < repeater.repsPerSet - 1 && repeater.restSecondsBetweenReps > 0) {
                    add(step(TimerPhase.REP_REST, repeater.restSecondsBetweenReps, rep = rep))
                }
            }

            entry.work == WorkKind.TIMED -> add(step(TimerPhase.WORK, entry.workSeconds))

            entry.work == WorkKind.REPS -> add(step(TimerPhase.WORK, 0, untimed = true))

            else -> Unit
        }
    }

    /**
     * Whether the interval at [index] is led into by a preparation countdown when the program
     * reaches it on its own.
     *
     * Only timed work is ever prepared for: a rest needs no preparing, and a set of reps does not
     * start on its own, so there is nothing to be late for. A rest that is long enough already
     * *serves* as the preparation — its last seconds are the ones the screen turns amber for — so
     * following it with five more would be five seconds of standing around.
     *
     * The exception is a repeater. Five seconds between the pulses of a set of repeaters would not
     * be the protocol any more, so a pulse that follows another pulse of the same side is entered
     * directly. The boundaries around the sequence — its first pulse, the side switch, the set
     * rest — keep the ordinary rule, which is what makes a zero-second side switch still give you
     * a moment to get onto the other hand.
     */
    fun preparesInto(steps: List<TimerStep>, index: Int): Boolean {
        val step = steps.getOrNull(index) ?: return false
        if (step.phase != TimerPhase.WORK || step.untimed) return false
        val previous = steps.getOrNull(index - 1) ?: return true
        if (continuesPulses(steps, index)) return false
        return !(previous.phase.isRest && previous.seconds >= TimerProgram.PREPARE_SECONDS)
    }

    /**
     * True for a pulse that follows another pulse of the same side of the same set — the one place
     * a preparation is never inserted, whether the sequence got there or the user skipped there.
     */
    fun continuesPulses(steps: List<TimerStep>, index: Int): Boolean {
        val step = steps.getOrNull(index) ?: return false
        if (step.phase != TimerPhase.WORK || step.repIndex == null) return false
        val previous = steps.getOrNull(index - 1) ?: return false
        return previous.repIndex != null && previous.sameBlockAs(step)
    }
}
