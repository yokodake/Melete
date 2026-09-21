package com.yokodake.melete.data.timer

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
 * What the timer is counting: a sequence of sets, not a single interval.
 *
 * A single countdown was the wrong shape for training. Real work is *four sets of eight with a
 * minute between them*, and a timer that can only express one of those three numbers makes the
 * athlete drive it by hand between every set. The program holds all three, and the prescription
 * already knows them, so starting a timer from a workout needs no further input.
 *
 * Rest follows a set only when another set is still to come: a trailing rest after the last one is
 * time the app invented. A program whose work is [WorkKind.NONE] is the exception — there the rest
 * *is* the content.
 */
@Serializable
data class TimerProgram(
    val sets: Int = 1,
    /**
     * Defaults to [WorkKind.REPS], which is the only kind that asserts nothing about lengths.
     * A no-argument [TimerProgram] therefore means "one set of unstated shape" and is valid, which
     * matters because it is what a state carries when it comes from a snapshot written before
     * programs existed.
     */
    val work: WorkKind = WorkKind.REPS,
    /** Length of one work interval. Meaningful only when [work] is [WorkKind.TIMED]. */
    val workSeconds: Int = 0,
    /** Rest between sets. Zero runs them back to back. */
    val restSeconds: Int = 0,
    /** The exercise this was started from, when it was started from one. */
    val label: String? = null,
) {
    init {
        require(sets >= 1) { "a program needs at least one set" }
        require(work != WorkKind.TIMED || workSeconds >= 1) {
            "a timed set needs a length"
        }
        require(work != WorkKind.NONE || restSeconds >= 1) {
            "a program with no work has to be counting something"
        }
        require(restSeconds >= 0) { "rest must not be negative" }
    }

    val isRepsMode: Boolean get() = work == WorkKind.REPS

    /** True when [setIndex] is the last set, and so is not followed by a rest. */
    fun isLastSet(setIndex: Int): Boolean = setIndex >= sets - 1

    /** Whether a rest follows the work of [setIndex]. */
    fun restFollows(setIndex: Int): Boolean =
        work != WorkKind.NONE && restSeconds > 0 && !isLastSet(setIndex)

    /**
     * How long the whole thing takes, or null when a set is untimed and the answer would be a
     * fabrication. Rests are counted only where one actually falls.
     */
    val totalSeconds: Int?
        get() = when (work) {
            WorkKind.REPS -> null
            WorkKind.NONE -> restSeconds * sets
            WorkKind.TIMED -> workSeconds * sets + restSeconds * (sets - 1)
        }

    companion object {
        /** One set, nothing claimed about it. The fallback for a state that predates programs. */
        val SINGLE = TimerProgram()

        /** A bare rest, the shape the logger starts between two sets. */
        fun rest(seconds: Int, label: String? = null) =
            TimerProgram(sets = 1, work = WorkKind.NONE, restSeconds = seconds, label = label)

        /** A single timed interval with nothing after it. */
        fun work(seconds: Int, label: String? = null) =
            TimerProgram(sets = 1, work = WorkKind.TIMED, workSeconds = seconds, label = label)
    }
}
