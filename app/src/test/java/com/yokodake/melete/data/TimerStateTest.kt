package com.yokodake.melete.data

import com.yokodake.melete.data.timer.CuePlanner
import com.yokodake.melete.data.timer.CueSettings
import com.yokodake.melete.data.timer.TimerCue
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.data.timer.TimerRestore
import com.yokodake.melete.data.timer.TimerSnapshot
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.data.timer.TimerTransitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The timer arithmetic, tested without a device. A countdown is a deadline on the monotonic clock,
 * so every one of these can be expressed as "given this instant, what does the state say".
 */
class TimerStateTest {

    private val runId = "run-1"
    private val settings = CueSettings()

    private fun running(
        totalMs: Long = 60_000,
        startedAt: Long = 1_000,
        phase: TimerPhase = TimerPhase.REST,
        cues: CueSettings = settings,
    ) = TimerTransitions.start(runId, phase, totalMs, cues, startedAt)

    @Test
    fun `remaining time is derived from the deadline and never goes negative`() {
        val state = running(totalMs = 60_000, startedAt = 1_000)
        assertEquals(60_000, state.remainingMs(1_000))
        assertEquals(59_000, state.remainingMs(2_000))
        assertEquals(0, state.remainingMs(61_000))
        assertEquals(0, state.remainingMs(999_999))
        assertTrue(state.isDue(61_000))
        assertFalse(state.isDue(60_999))
    }

    @Test
    fun `pausing and resuming preserves the remaining time and does not shift the deadline`() {
        val state = running(totalMs = 60_000, startedAt = 1_000)
        val paused = TimerTransitions.pause(state, nowElapsedMs = 21_000)
        assertEquals(40_000, paused.remainingMs)

        // Ten minutes of standing around before resuming.
        val resumed = TimerTransitions.resume(paused, nowElapsedMs = 621_000)
        assertEquals(40_000, resumed.remainingMs(621_000))
        assertEquals(661_000, resumed.deadlineElapsedMs)
    }

    @Test
    fun `cues fall due at their planned moment and in order`() {
        val state = running(totalMs = 120_000, startedAt = 0, phase = TimerPhase.WORK)
        // A quarter done is thirty seconds in.
        assertNull(state.dueCue(29_999))
        assertEquals(TimerCue.QUARTER, state.dueCue(30_000)?.cue)

        val afterQuarter = state.copy(delivered = setOf(TimerCue.QUARTER))
        assertEquals(TimerCue.HALF, afterQuarter.dueCue(60_000)?.cue)
        assertNull(afterQuarter.dueCue(59_999))
    }

    @Test
    fun `several cues that fell due unseen are given oldest first`() {
        val state = running(totalMs = 120_000, startedAt = 0, phase = TimerPhase.WORK)
        // Nothing was delivered for the first ninety seconds.
        assertEquals(TimerCue.QUARTER, state.dueCue(95_000)?.cue)
        val next = state.copy(delivered = setOf(TimerCue.QUARTER))
        assertEquals(TimerCue.HALF, next.dueCue(95_000)?.cue)
    }

    @Test
    fun `a cue already given is not repeated after pause and resume`() {
        val state = running(totalMs = 180_000, startedAt = 0)
        val afterWarning = state.copy(delivered = setOf(TimerCue.THIRTY_SECONDS))
        assertNull(afterWarning.dueCue(150_000))

        val paused = TimerTransitions.pause(afterWarning, nowElapsedMs = 155_000)
        assertTrue(TimerCue.THIRTY_SECONDS in paused.delivered)
        val resumed = TimerTransitions.resume(paused, nowElapsedMs = 500_000)
        assertTrue(TimerCue.THIRTY_SECONDS in resumed.delivered)
        assertNull(resumed.dueCue(500_001))
    }

    @Test
    fun `the next cue instant is the earliest one still owed`() {
        val state = running(totalMs = 180_000, startedAt = 0)
        // Thirty seconds left, i.e. 150 seconds in.
        assertEquals(150_000L, state.nextCueAt(0))
        val afterWarning = state.copy(delivered = setOf(TimerCue.THIRTY_SECONDS))
        assertEquals(177_000L, afterWarning.nextCueAt(0))
    }

    @Test
    fun `once every cue is given there is no next instant`() {
        val state = running(totalMs = 60_000, startedAt = 0)
        val all = state.copy(delivered = state.plan.map { it.cue }.toSet())
        assertNull(all.nextCueAt(0))
    }

    @Test
    fun `the plan is fixed when the run starts so settings cannot change it mid countdown`() {
        val state = running(totalMs = 180_000, startedAt = 0)
        assertEquals(
            CuePlanner.plan(TimerPhase.REST, 180_000, settings),
            state.plan,
        )
        val paused = TimerTransitions.pause(state, 10_000)
        assertEquals(state.plan, paused.plan)
        assertEquals(state.plan, TimerTransitions.resume(paused, 99_000).plan)
    }

    @Test
    fun `a running countdown that outlived a reboot is reported interrupted`() {
        val snapshot = TimerRestore.snapshot(running(), bootCount = 7) as TimerSnapshot
        val restored = TimerRestore.restore(snapshot, currentBootCount = 8, nowElapsedMs = 5_000)
        // The program travels with the state, so a restored run still knows what it was.
        assertEquals(
            TimerState.Interrupted(runId, TimerPhase.REST, 60_000, TimerProgram.rest(60)),
            restored,
        )
    }

    @Test
    fun `a paused countdown survives a reboot because remaining time is a duration`() {
        val paused = TimerTransitions.pause(running(), nowElapsedMs = 21_000)
        val snapshot = TimerRestore.snapshot(paused, bootCount = 7) as TimerSnapshot
        val restored = TimerRestore.restore(snapshot, currentBootCount = 8, nowElapsedMs = 100)
        assertEquals(paused, restored)
    }

    @Test
    fun `a countdown that ran out while the process was gone comes back finished`() {
        val snapshot = TimerRestore.snapshot(running(), bootCount = 7) as TimerSnapshot
        val restored = TimerRestore.restore(snapshot, currentBootCount = 7, nowElapsedMs = 120_000)
        assertEquals(
            TimerState.Finished(runId, TimerPhase.REST, 60_000, TimerProgram.rest(60), 1),
            restored,
        )
    }

    @Test
    fun `a countdown still within its deadline comes back running with its plan intact`() {
        val state = running()
        val snapshot = TimerRestore.snapshot(state, bootCount = 7) as TimerSnapshot
        val restored = TimerRestore.restore(snapshot, currentBootCount = 7, nowElapsedMs = 30_000)
        assertEquals(state, restored)
        assertEquals(31_000, (restored as TimerState.Running).remainingMs(30_000))
        assertEquals(state.plan, restored.plan)
    }

    @Test
    fun `delivered cues survive the process going away`() {
        val state = running(totalMs = 180_000, startedAt = 0)
            .copy(delivered = setOf(TimerCue.THIRTY_SECONDS))
        val snapshot = TimerRestore.snapshot(state, bootCount = 3) as TimerSnapshot
        val restored = TimerRestore.restore(snapshot, currentBootCount = 3, nowElapsedMs = 160_000)
        assertTrue(TimerCue.THIRTY_SECONDS in (restored as TimerState.Running).delivered)
        assertNull(restored.dueCue(160_000))
    }

    @Test
    fun `nothing is persisted for an idle or interrupted timer`() {
        assertNull(TimerRestore.snapshot(TimerState.Idle, bootCount = 1))
        assertNull(
            TimerRestore.snapshot(TimerState.Interrupted(runId, TimerPhase.WORK, 1_000), bootCount = 1)
        )
        assertEquals(
            TimerState.Idle,
            TimerRestore.restore(null, currentBootCount = 1, nowElapsedMs = 0),
        )
    }

    @Test
    fun `switching a cue family on mid-run never sounds the moments already gone`() {
        // A three-minute rest started with no cues at all, two minutes in.
        val silent = running(
            totalMs = 180_000,
            startedAt = 1_000,
            cues = CueSettings(
                thirtySecondWarning = false,
                finalCountdown = false,
                quarterCues = false,
            ),
        )
        val now = 121_000L
        val replanned = TimerTransitions.replan(silent, CueSettings(), now)

        // The 30 s warning is still ahead, so it is owed and will sound.
        assertFalse(TimerCue.THIRTY_SECONDS in replanned.delivered)
        assertNull(replanned.dueCue(now))
        assertEquals(TimerCue.THIRTY_SECONDS, replanned.dueCue(152_000)?.cue)
    }

    @Test
    fun `a cue whose moment has passed is written off rather than fired late`() {
        val silent = running(
            totalMs = 180_000,
            startedAt = 1_000,
            cues = CueSettings(
                thirtySecondWarning = false,
                finalCountdown = false,
                quarterCues = false,
            ),
        )
        // Twenty seconds left: the thirty-second warning is ten seconds in the past.
        val now = 161_000L
        val replanned = TimerTransitions.replan(silent, CueSettings(), now)

        assertTrue(TimerCue.THIRTY_SECONDS in replanned.delivered)
        assertNull(replanned.dueCue(now))
        // What is still ahead is untouched.
        assertFalse(TimerCue.COUNT_3 in replanned.delivered)
        assertEquals(TimerCue.COUNT_3, replanned.dueCue(178_000)?.cue)
    }

    @Test
    fun `switching cues off while paused drops them from what is still owed`() {
        val paused = TimerTransitions.pause(running(totalMs = 180_000, startedAt = 1_000), 61_000)
        val quiet = TimerTransitions.replan(
            paused,
            CueSettings(thirtySecondWarning = false, finalCountdown = true, quarterCues = false),
        )
        assertFalse(quiet.plan.any { it.cue == TimerCue.THIRTY_SECONDS })
        assertTrue(quiet.plan.any { it.cue == TimerCue.COUNT_3 })
        // Resuming carries the trimmed plan rather than rebuilding the old one.
        val resumed = TimerTransitions.resume(quiet, 200_000)
        assertFalse(resumed.plan.any { it.cue == TimerCue.THIRTY_SECONDS })
    }

    @Test
    fun `a countdown remembers the exercise it was started from across pause and finish`() {
        val started = TimerTransitions.start(
            runId = runId,
            phase = TimerPhase.REST,
            durationMs = 60_000,
            settings = settings,
            nowElapsedMs = 1_000,
            label = "Back squat",
        )
        assertEquals("Back squat", started.activeLabel)
        val paused = TimerTransitions.pause(started, 10_000)
        assertEquals("Back squat", paused.activeLabel)
        assertEquals("Back squat", TimerTransitions.resume(paused, 20_000).activeLabel)
        assertEquals("Back squat", TimerTransitions.finish(started).activeLabel)
        // It survives the process dying, because it travels in the snapshot.
        val snapshot = TimerRestore.snapshot(started, bootCount = 4)
        val restored = TimerRestore.restore(snapshot, currentBootCount = 4, nowElapsedMs = 30_000)
        assertEquals("Back squat", restored.activeLabel)
    }

    @Test
    fun `finishing keeps the run identity and records nothing else`() {
        val finished = TimerTransitions.finish(running())
        assertTrue(finished is TimerState.Finished)
        assertEquals(runId, (finished as TimerState.Finished).runId)
        assertEquals(TimerPhase.REST, finished.phase)
        assertEquals(60_000, finished.totalMs)
        // Finishing is idempotent, so a late cue cannot turn it into anything else.
        assertEquals(finished, TimerTransitions.finish(finished))
    }
}
