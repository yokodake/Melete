package com.yokodake.melete.data

import com.yokodake.melete.data.timer.TimerPhase
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

    private fun running(
        totalMs: Long = 60_000,
        startedAt: Long = 1_000,
        warningLeadMs: Long? = 10_000,
    ) = TimerTransitions.start(runId, TimerPhase.REST, totalMs, warningLeadMs, startedAt)

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
    fun `a warning already given is not repeated after pause and resume`() {
        val state = running(totalMs = 60_000, startedAt = 1_000, warningLeadMs = 10_000)
        assertTrue(state.isWarningDue(51_000))

        val afterWarning = state.copy(warningFired = true)
        assertFalse(afterWarning.isWarningDue(51_000))

        val paused = TimerTransitions.pause(afterWarning, nowElapsedMs = 55_000)
        assertTrue(paused.warningFired)
        val resumed = TimerTransitions.resume(paused, nowElapsedMs = 100_000)
        assertTrue(resumed.warningFired)
        assertFalse(resumed.isWarningDue(106_000))
    }

    @Test
    fun `the warning is due only once the lead time is reached`() {
        val state = running(totalMs = 60_000, startedAt = 0, warningLeadMs = 10_000)
        assertEquals(50_000L, state.warningAtElapsedMs)
        assertFalse(state.isWarningDue(49_999))
        assertTrue(state.isWarningDue(50_000))
    }

    @Test
    fun `a warning at least as long as the countdown is dropped rather than fired at the start`() {
        val sameLength = running(totalMs = 10_000, startedAt = 0, warningLeadMs = 10_000)
        assertNull(sameLength.warningAtElapsedMs)
        assertFalse(sameLength.isWarningDue(0))

        val longer = running(totalMs = 10_000, startedAt = 0, warningLeadMs = 30_000)
        assertNull(longer.warningAtElapsedMs)
        assertFalse(longer.isWarningDue(0))
        // The countdown itself is unaffected.
        assertTrue(longer.isDue(10_000))
    }

    @Test
    fun `no warning is configured when the lead is off`() {
        val state = running(warningLeadMs = null)
        assertNull(state.warningAtElapsedMs)
        assertFalse(state.isWarningDue(59_000))
    }

    @Test
    fun `a running countdown that outlived a reboot is reported interrupted`() {
        val snapshot = TimerRestore.snapshot(running(), bootCount = 7) as TimerSnapshot
        val restored = TimerRestore.restore(snapshot, currentBootCount = 8, nowElapsedMs = 5_000)
        assertEquals(TimerState.Interrupted(runId, TimerPhase.REST, 60_000), restored)
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
        assertEquals(TimerState.Finished(runId, TimerPhase.REST, 60_000), restored)
    }

    @Test
    fun `a countdown still within its deadline comes back running with the same deadline`() {
        val state = running()
        val snapshot = TimerRestore.snapshot(state, bootCount = 7) as TimerSnapshot
        val restored = TimerRestore.restore(snapshot, currentBootCount = 7, nowElapsedMs = 30_000)
        assertEquals(state, restored)
        assertEquals(31_000, (restored as TimerState.Running).remainingMs(30_000))
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
    fun `finishing keeps the run identity and records nothing else`() {
        val finished = TimerTransitions.finish(running())
        assertEquals(TimerState.Finished(runId, TimerPhase.REST, 60_000), finished)
        // Finishing is idempotent, so a late alarm cannot turn it into anything else.
        assertEquals(finished, TimerTransitions.finish(finished))
    }
}
