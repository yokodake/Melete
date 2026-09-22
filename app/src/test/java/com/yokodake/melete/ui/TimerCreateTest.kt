package com.yokodake.melete.ui

import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.timer.RepeaterSpec
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.data.timer.WorkKind
import com.yokodake.melete.ui.timer.TimerCreateMode
import com.yokodake.melete.ui.timer.TimerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The timer you build by hand, on the timer tab.
 *
 * Everything the create screen can express has to reach the same engine a planned workout does —
 * a repeater started from here is the same sequence as a repeater started from an exercise, or the
 * screen is quietly a second, worse timer.
 */
class TimerCreateTest {

    private val base = TimerUiState(setsText = "3")

    @Test
    fun `timed sets build the program they always did`() {
        val program = base.copy(
            mode = TimerCreateMode.TIMED,
            work = com.yokodake.melete.ui.timer.DurationDraft.of(10),
            rest = com.yokodake.melete.ui.timer.DurationDraft.of(60),
        ).draftProgram!!
        assertEquals(3, program.sets)
        assertEquals(WorkKind.TIMED, program.work)
        assertEquals(10, program.workSeconds)
        assertEquals(60, program.restSeconds)
        assertNull(program.entry.repeater)
    }

    @Test
    fun `reps need no length and still build`() {
        val program = base.copy(mode = TimerCreateMode.REPS).draftProgram!!
        assertEquals(WorkKind.REPS, program.work)
        assertTrue(program.steps.any { it.untimed })
    }

    @Test
    fun `a timed set with no length is not a program yet`() {
        val state = base.copy(
            mode = TimerCreateMode.TIMED,
            work = com.yokodake.melete.ui.timer.DurationDraft.of(0),
        )
        assertNull(state.draftProgram)
        assertTrue(!state.canStart)
    }

    @Test
    fun `repeaters built by hand are the same sequence as a planned one`() {
        val program = base.copy(
            mode = TimerCreateMode.REPEATERS,
            rest = com.yokodake.melete.ui.timer.DurationDraft.of(180),
            repeaterRepsText = "6",
            repeaterWorkText = "7",
            repeaterRestText = "3",
        ).draftProgram!!

        assertEquals(RepeaterSpec(6, 7, 3), program.entry.repeater)
        // 3 x (6 x 7s + 5 x 3s) + 2 x 180s -- the same arithmetic as a prescribed repeater.
        assertEquals(531, program.totalSeconds)
        assertEquals(126, program.workOnlySeconds)
        // And no preparation is inserted between pulses.
        assertEquals(536, program.estimatedSeconds())
    }

    @Test
    fun `a half-typed repeater is not a program yet`() {
        // The fields open on the classic six-sevens-and-threes, so "half typed" means one of
        // them has actually been cleared rather than merely left alone.
        val state = base.copy(
            mode = TimerCreateMode.REPEATERS,
            repeaterRepsText = "6",
            repeaterWorkText = "",
        )
        assertNull(state.repeaterSpec)
        assertNull(state.draftProgram)
        assertNull(base.copy(mode = TimerCreateMode.REPEATERS, repeaterRepsText = "").draftProgram)
    }

    @Test
    fun `the both-sides switch reaches the sequencer`() {
        val program = base.copy(
            mode = TimerCreateMode.TIMED,
            work = com.yokodake.melete.ui.timer.DurationDraft.of(10),
            rest = com.yokodake.melete.ui.timer.DurationDraft.of(60),
            setsText = "1",
            unilateral = true,
            sideSwitchText = "12",
        ).draftProgram!!

        assertTrue(program.hasUnilateral)
        assertEquals(12, program.entry.sideSwitchSeconds)
        assertEquals(
            listOf(BodySide.LEFT, BodySide.RIGHT, BodySide.RIGHT),
            program.steps.map { it.side },
        )
        assertEquals(TimerPhase.SWITCH, program.steps[1].phase)
    }

    @Test
    fun `both sides and repeaters compose`() {
        val program = base.copy(
            mode = TimerCreateMode.REPEATERS,
            setsText = "1",
            unilateral = true,
            repeaterRepsText = "2",
            repeaterWorkText = "7",
            repeaterRestText = "3",
        ).draftProgram!!
        // Every pulse on the left, the switch, then every pulse on the right.
        assertEquals(
            listOf("WORK L", "REP_REST L", "WORK L", "SWITCH R", "WORK R", "REP_REST R", "WORK R"),
            program.steps.map { "${it.phase} ${it.side.toString().first()}" },
        )
    }

    @Test
    fun `an empty side switch falls back to the default rather than to zero`() {
        val program = base.copy(
            mode = TimerCreateMode.REPS,
            unilateral = true,
            sideSwitchText = "",
        ).draftProgram!!
        assertEquals(TimerProgram.DEFAULT_SIDE_SWITCH_SECONDS, program.entry.sideSwitchSeconds)
        // Zero is still a real answer when it is actually typed.
        val none = base.copy(
            mode = TimerCreateMode.REPS,
            unilateral = true,
            sideSwitchText = "0",
        ).draftProgram!!
        assertEquals(0, none.entry.sideSwitchSeconds)
        assertTrue(none.steps.none { it.phase == TimerPhase.SWITCH })
    }

    @Test
    fun `no sets is not a program`() {
        assertNull(base.copy(setsText = "0", mode = TimerCreateMode.REPS).draftProgram)
        assertNull(base.copy(setsText = "", mode = TimerCreateMode.REPS).draftProgram)
    }
}
