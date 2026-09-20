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
        /** How long before the deadline the advance warning sounds, or null for no warning. */
        val warningLeadMs: Long?,
        /** True once the advance warning has sounded for this run, so it is never repeated. */
        val warningFired: Boolean,
    ) : TimerState {

        fun remainingMs(nowElapsedMs: Long): Long = (deadlineElapsedMs - nowElapsedMs).coerceAtLeast(0)

        /**
         * When the advance warning is due, or null when this run has none. A warning at or beyond
         * the whole countdown is not a warning, so it is dropped rather than fired at the start.
         */
        val warningAtElapsedMs: Long?
            get() = warningLeadMs
                ?.takeIf { it > 0 && it < totalMs }
                ?.let { deadlineElapsedMs - it }

        fun isDue(nowElapsedMs: Long): Boolean = nowElapsedMs >= deadlineElapsedMs

        fun isWarningDue(nowElapsedMs: Long): Boolean =
            !warningFired && warningAtElapsedMs?.let { nowElapsedMs >= it } == true
    }

    data class Paused(
        val runId: String,
        val phase: TimerPhase,
        val totalMs: Long,
        val remainingMs: Long,
        val warningLeadMs: Long?,
        val warningFired: Boolean,
    ) : TimerState

    /** The countdown reached zero. Reaching zero records nothing: it is a cue, not a performed set. */
    data class Finished(
        val runId: String,
        val phase: TimerPhase,
        val totalMs: Long,
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
    ) : TimerState

    val activeRunId: String?
        get() = when (this) {
            is Running -> runId
            is Paused -> runId
            is Finished -> runId
            is Interrupted -> runId
            Idle -> null
        }
}

/** Pause and resume, kept pure so the arithmetic can be tested without a device. */
object TimerTransitions {

    fun start(
        runId: String,
        phase: TimerPhase,
        durationMs: Long,
        warningLeadMs: Long?,
        nowElapsedMs: Long,
    ): TimerState.Running = TimerState.Running(
        runId = runId,
        phase = phase,
        totalMs = durationMs,
        deadlineElapsedMs = nowElapsedMs + durationMs,
        warningLeadMs = warningLeadMs,
        warningFired = false,
    )

    fun pause(state: TimerState.Running, nowElapsedMs: Long): TimerState.Paused = TimerState.Paused(
        runId = state.runId,
        phase = state.phase,
        totalMs = state.totalMs,
        remainingMs = state.remainingMs(nowElapsedMs),
        warningLeadMs = state.warningLeadMs,
        // Carried across the pause: a warning already given must not be given again on resume.
        warningFired = state.warningFired,
    )

    fun resume(state: TimerState.Paused, nowElapsedMs: Long): TimerState.Running = TimerState.Running(
        runId = state.runId,
        phase = state.phase,
        totalMs = state.totalMs,
        deadlineElapsedMs = nowElapsedMs + state.remainingMs,
        warningLeadMs = state.warningLeadMs,
        warningFired = state.warningFired,
    )

    fun finish(state: TimerState): TimerState = when (state) {
        is TimerState.Running -> TimerState.Finished(state.runId, state.phase, state.totalMs)
        is TimerState.Paused -> TimerState.Finished(state.runId, state.phase, state.totalMs)
        else -> state
    }
}
