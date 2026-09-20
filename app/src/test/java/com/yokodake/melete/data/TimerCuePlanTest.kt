package com.yokodake.melete.data

import com.yokodake.melete.data.timer.CuePlanner
import com.yokodake.melete.data.timer.CueSettings
import com.yokodake.melete.data.timer.TimerCue
import com.yokodake.melete.data.timer.TimerPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a countdown of a given length and phase will actually sound. */
class TimerCuePlanTest {

    private val all = CueSettings()

    private fun plan(phase: TimerPhase, seconds: Long, settings: CueSettings = all) =
        CuePlanner.plan(phase, seconds * 1000, settings)

    @Test
    fun `a short rest only counts the last three seconds down and ends`() {
        val cues = plan(TimerPhase.REST, 20).map { it.cue }
        assertEquals(
            listOf(TimerCue.COUNT_3, TimerCue.COUNT_2, TimerCue.COUNT_1, TimerCue.FINISH),
            cues,
        )
    }

    @Test
    fun `a three minute rest warns at thirty seconds and counts the last three down`() {
        val cues = plan(TimerPhase.REST, 180)
        assertEquals(
            listOf(
                TimerCue.THIRTY_SECONDS,
                TimerCue.COUNT_3,
                TimerCue.COUNT_2,
                TimerCue.COUNT_1,
                TimerCue.FINISH,
            ),
            cues.map { it.cue },
        )
        assertEquals(30_000, cues.first { it.cue == TimerCue.THIRTY_SECONDS }.remainingMs)
        assertEquals(0, cues.last().remainingMs)
    }

    @Test
    fun `a long work interval is marked at a quarter, a half and three quarters`() {
        val cues = plan(TimerPhase.WORK, 200)
        assertEquals(
            listOf(
                TimerCue.QUARTER,
                TimerCue.HALF,
                TimerCue.THREE_QUARTERS,
                TimerCue.THIRTY_SECONDS,
                TimerCue.COUNT_3,
                TimerCue.COUNT_2,
                TimerCue.COUNT_1,
                TimerCue.FINISH,
            ),
            cues.map { it.cue },
        )
        // A quarter done is three quarters left.
        assertEquals(150_000, cues.first { it.cue == TimerCue.QUARTER }.remainingMs)
        assertEquals(100_000, cues.first { it.cue == TimerCue.HALF }.remainingMs)
        assertEquals(50_000, cues.first { it.cue == TimerCue.THREE_QUARTERS }.remainingMs)
    }

    @Test
    fun `three quarters done and thirty seconds left are one moment on a two minute set`() {
        val cues = plan(TimerPhase.WORK, 120)
        assertEquals(
            listOf(
                TimerCue.QUARTER,
                TimerCue.HALF,
                TimerCue.THIRTY_SECONDS,
                TimerCue.COUNT_3,
                TimerCue.COUNT_2,
                TimerCue.COUNT_1,
                TimerCue.FINISH,
            ),
            cues.map { it.cue },
        )
    }

    @Test
    fun `quarter cues are for the work itself, not the rest between sets`() {
        val rest = plan(TimerPhase.REST, 300).map { it.cue }
        assertFalse(rest.contains(TimerCue.QUARTER))
        assertFalse(rest.contains(TimerCue.HALF))
        assertTrue(rest.contains(TimerCue.THIRTY_SECONDS))
    }

    @Test
    fun `a set shorter than a minute gets no quarter cues`() {
        val cues = plan(TimerPhase.WORK, 45).map { it.cue }
        assertFalse(cues.contains(TimerCue.QUARTER))
        assertTrue(cues.contains(TimerCue.THIRTY_SECONDS))
    }

    @Test
    fun `a minute of work is exactly where quarter cues begin`() {
        val cues = plan(TimerPhase.WORK, 60).map { it.cue }
        assertTrue(cues.contains(TimerCue.QUARTER))
        assertTrue(cues.contains(TimerCue.THREE_QUARTERS))
    }

    @Test
    fun `two cues never land on the same moment`() {
        // A minute of work puts the halfway mark and the thirty second warning together.
        val cues = plan(TimerPhase.WORK, 60)
        val moments = cues.map { it.remainingMs }
        assertEquals(moments.size, moments.distinct().size)
        // The more specific cue is the one that survives.
        assertTrue(cues.any { it.cue == TimerCue.THIRTY_SECONDS && it.remainingMs == 30_000L })
        assertFalse(cues.any { it.cue == TimerCue.HALF })
    }

    @Test
    fun `a countdown shorter than the last three seconds still ends`() {
        assertEquals(listOf(TimerCue.FINISH), plan(TimerPhase.REST, 1).map { it.cue })
        assertEquals(
            listOf(TimerCue.COUNT_2, TimerCue.COUNT_1, TimerCue.FINISH),
            plan(TimerPhase.REST, 3).map { it.cue },
        )
    }

    @Test
    fun `a thirty second rest does not warn that thirty seconds are left`() {
        val cues = plan(TimerPhase.REST, 30).map { it.cue }
        assertFalse(cues.contains(TimerCue.THIRTY_SECONDS))
    }

    @Test
    fun `each family can be switched off on its own`() {
        val quiet = CueSettings(
            thirtySecondWarning = false,
            finalCountdown = false,
            quarterCues = false,
        )
        assertEquals(listOf(TimerCue.FINISH), plan(TimerPhase.WORK, 300, quiet).map { it.cue })

        val onlyQuarters = plan(
            TimerPhase.WORK,
            300,
            CueSettings(thirtySecondWarning = false, finalCountdown = false),
        ).map { it.cue }
        assertEquals(
            listOf(TimerCue.QUARTER, TimerCue.HALF, TimerCue.THREE_QUARTERS, TimerCue.FINISH),
            onlyQuarters,
        )
    }

    @Test
    fun `the plan is always in the order the countdown meets it`() {
        val cues = plan(TimerPhase.WORK, 600)
        val remaining = cues.map { it.remainingMs }
        assertEquals(remaining.sortedDescending(), remaining)
    }

    @Test
    fun `the end is always cued`() {
        listOf(1L, 3L, 30L, 60L, 3_600L).forEach { seconds ->
            listOf(TimerPhase.WORK, TimerPhase.REST).forEach { phase ->
                assertEquals(
                    "$phase for ${seconds}s must end with FINISH",
                    TimerCue.FINISH,
                    plan(phase, seconds).last().cue,
                )
            }
        }
    }
}
