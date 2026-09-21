package com.yokodake.melete.data.timer

import kotlinx.serialization.Serializable

/**
 * What is written to disk so a countdown survives the process being killed.
 *
 * [bootCount] is the reboot detector. Elapsed-realtime restarts at zero on every boot, so a stored
 * deadline from a previous boot is not merely stale, it is meaningless — it would usually look
 * like a countdown that finished long ago, or one with hours left. Comparing the boot count tells
 * the two situations apart without guessing.
 *
 * The cue plan is stored with the run rather than recomputed, so changing the cue settings cannot
 * reach back into a countdown that is already under way. The program is stored too: a five-set
 * timer that loses its process must come back knowing it was on set three, or it silently becomes
 * a one-set timer.
 */
@Serializable
data class TimerSnapshot(
    val runId: String,
    val phase: TimerPhase,
    val totalMs: Long,
    val bootCount: Int,
    val status: Status,
    /** Deadline on the elapsed-realtime clock. Meaningful only while [status] is RUNNING. */
    val deadlineElapsedMs: Long = 0,
    /** Remaining time. Meaningful only while [status] is PAUSED. */
    val remainingMs: Long = 0,
    val plan: List<PlannedCue> = emptyList(),
    val delivered: Set<TimerCue> = emptySet(),
    /** Defaulted, so snapshots written before programs existed still decode. */
    val program: TimerProgram = TimerProgram(),
    val setIndex: Int = 0,
    val setsCompleted: Int = 1,
) {
    enum class Status { RUNNING, PAUSED, AWAITING_SET, FINISHED }
}

object TimerRestore {

    /** Writes a state out for persistence, or null when there is nothing worth keeping. */
    fun snapshot(state: TimerState, bootCount: Int): TimerSnapshot? = when (state) {
        is TimerState.Running -> TimerSnapshot(
            runId = state.runId,
            phase = state.phase,
            totalMs = state.totalMs,
            bootCount = bootCount,
            status = TimerSnapshot.Status.RUNNING,
            deadlineElapsedMs = state.deadlineElapsedMs,
            plan = state.plan,
            delivered = state.delivered,
            program = state.program,
            setIndex = state.setIndex,
        )

        is TimerState.Paused -> TimerSnapshot(
            runId = state.runId,
            phase = state.phase,
            totalMs = state.totalMs,
            bootCount = bootCount,
            status = TimerSnapshot.Status.PAUSED,
            remainingMs = state.remainingMs,
            plan = state.plan,
            delivered = state.delivered,
            program = state.program,
            setIndex = state.setIndex,
        )

        // Nothing is counting, so there is no deadline to go stale: a set of reps interrupted by
        // the process dying, or by a reboot, is still the same set of reps when you come back.
        is TimerState.AwaitingSet -> TimerSnapshot(
            runId = state.runId,
            phase = TimerPhase.WORK,
            totalMs = 0,
            bootCount = bootCount,
            status = TimerSnapshot.Status.AWAITING_SET,
            program = state.program,
            setIndex = state.setIndex,
        )

        is TimerState.Finished -> TimerSnapshot(
            runId = state.runId,
            phase = state.phase,
            totalMs = state.totalMs,
            bootCount = bootCount,
            status = TimerSnapshot.Status.FINISHED,
            program = state.program,
            setsCompleted = state.setsCompleted,
        )

        // An interrupted run has already been reported; there is nothing left to restore.
        is TimerState.Interrupted -> null
        TimerState.Idle -> null
    }

    /**
     * Rebuilds the state after the process, or the whole device, went away.
     *
     * A paused countdown survives a reboot untouched: its remaining time is a duration, not a
     * point on a clock that no longer exists. A running one does not, and says so.
     */
    fun restore(
        snapshot: TimerSnapshot?,
        currentBootCount: Int,
        nowElapsedMs: Long,
    ): TimerState {
        if (snapshot == null) return TimerState.Idle
        val rebooted = snapshot.bootCount != currentBootCount
        return when (snapshot.status) {
            TimerSnapshot.Status.PAUSED -> TimerState.Paused(
                runId = snapshot.runId,
                phase = snapshot.phase,
                totalMs = snapshot.totalMs,
                remainingMs = snapshot.remainingMs,
                plan = snapshot.plan,
                delivered = snapshot.delivered,
                program = snapshot.program,
                setIndex = snapshot.setIndex,
            )

            TimerSnapshot.Status.AWAITING_SET -> TimerState.AwaitingSet(
                runId = snapshot.runId,
                program = snapshot.program,
                setIndex = snapshot.setIndex,
            )

            TimerSnapshot.Status.FINISHED -> TimerState.Finished(
                runId = snapshot.runId,
                phase = snapshot.phase,
                totalMs = snapshot.totalMs,
                program = snapshot.program,
                setsCompleted = snapshot.setsCompleted,
            )

            TimerSnapshot.Status.RUNNING -> when {
                rebooted -> TimerState.Interrupted(
                    snapshot.runId,
                    snapshot.phase,
                    snapshot.totalMs,
                    snapshot.program,
                )

                // The process was gone when the countdown ran out. The arithmetic is recoverable;
                // whether the cue was actually heard is not, so the state says finished and the
                // screen says the app was not running rather than pretending it alerted.
                nowElapsedMs >= snapshot.deadlineElapsedMs -> TimerState.Finished(
                    snapshot.runId,
                    snapshot.phase,
                    snapshot.totalMs,
                    snapshot.program,
                    snapshot.setIndex + 1,
                )

                else -> TimerState.Running(
                    runId = snapshot.runId,
                    phase = snapshot.phase,
                    totalMs = snapshot.totalMs,
                    deadlineElapsedMs = snapshot.deadlineElapsedMs,
                    plan = snapshot.plan,
                    delivered = snapshot.delivered,
                    program = snapshot.program,
                    setIndex = snapshot.setIndex,
                )
            }
        }
    }
}
