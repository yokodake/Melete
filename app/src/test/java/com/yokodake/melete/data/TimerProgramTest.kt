package com.yokodake.melete.data

import com.yokodake.melete.data.timer.CueSettings
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.data.timer.TimerRestore
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.data.timer.TimerTransitions
import com.yokodake.melete.data.timer.WorkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sequencing of a multi-set timer, tested without a device.
 *
 * A program is walked here exactly as the controller walks it: start it, then hand each ended
 * interval back to [TimerTransitions.advance] and see what comes next. Everything that matters
 * about "can it do sets" is in that succession, so it is all expressible as plain values.
 */
class TimerProgramTest {

    private val settings = CueSettings()
    private var nextId = 0

    private fun id() = "run-${nextId++}"

    /** Plays a program through to the end, recording what each step was. */
    private fun walk(
        program: TimerProgram,
        repsTakeMs: Long = 5_000,
        limit: Int = 50,
    ): List<String> {
        val steps = mutableListOf<String>()
        var now = 1_000L
        var state = TimerTransitions.startProgram(id(), program, settings, now)
        repeat(limit) {
            when (val current = state) {
                is TimerState.Running -> {
                    steps += "${current.phase.name.lowercase()}" +
                        " set${current.setIndex + 1}" +
                        " ${current.totalMs / 1000}s"
                    now = current.deadlineElapsedMs
                    state = TimerTransitions.advance(current, settings, now, id())
                }

                is TimerState.AwaitingSet -> {
                    steps += "reps set${current.setIndex + 1}"
                    now += repsTakeMs
                    state = TimerTransitions.completeSet(current, settings, now, id())
                }

                is TimerState.Finished -> {
                    steps += "finished ${current.setsCompleted}"
                    return steps
                }

                else -> return steps
            }
        }
        return steps
    }

    @Test
    fun `five timed sets alternate work and rest, with no rest after the last one`() {
        val program = TimerProgram(
            sets = 5,
            work = WorkKind.TIMED,
            workSeconds = 10,
            restSeconds = 180,
        )
        assertEquals(
            listOf(
                "work set1 10s", "rest set1 180s",
                "work set2 10s", "rest set2 180s",
                "work set3 10s", "rest set3 180s",
                "work set4 10s", "rest set4 180s",
                "work set5 10s",
                // Five sets, four rests: a trailing rest would be time the app invented.
                "finished 5",
            ),
            walk(program),
        )
    }

    @Test
    fun `a reps program waits for the athlete between every set`() {
        val program = TimerProgram(sets = 3, work = WorkKind.REPS, restSeconds = 60)
        assertEquals(
            listOf(
                "reps set1", "rest set1 60s",
                "reps set2", "rest set2 60s",
                "reps set3",
                "finished 3",
            ),
            walk(program),
        )
    }

    @Test
    fun `a bare rest is one interval and then it is over`() {
        assertEquals(listOf("rest set1 180s", "finished 1"), walk(TimerProgram.rest(180)))
    }

    @Test
    fun `a single timed set has nothing after it`() {
        assertEquals(listOf("work set1 10s", "finished 1"), walk(TimerProgram.work(10)))
    }

    @Test
    fun `sets with no rest configured run straight into each other`() {
        val program = TimerProgram(sets = 3, work = WorkKind.TIMED, workSeconds = 20, restSeconds = 0)
        assertEquals(
            listOf("work set1 20s", "work set2 20s", "work set3 20s", "finished 3"),
            walk(program),
        )
    }

    @Test
    fun `every interval is its own run, so the same cue may sound once per set`() {
        val program = TimerProgram(sets = 3, work = WorkKind.TIMED, workSeconds = 10, restSeconds = 30)
        val ids = mutableSetOf<String>()
        var now = 1_000L
        var state: TimerState = TimerTransitions.startProgram(id(), program, settings, now)
        repeat(10) {
            val running = state as? TimerState.Running ?: return@repeat
            ids += running.runId
            now = running.deadlineElapsedMs
            state = TimerTransitions.advance(running, settings, now, id())
        }
        // Three work intervals and two rests: five runs, five distinct ids. Cue bookkeeping is
        // keyed by run id, so sharing one would silence every set after the first.
        assertEquals(5, ids.size)
    }

    @Test
    fun `the total is the sets plus the rests that actually fall between them`() {
        val program = TimerProgram(sets = 5, work = WorkKind.TIMED, workSeconds = 10, restSeconds = 180)
        assertEquals(5 * 10 + 4 * 180, program.totalSeconds)
        assertEquals(180, TimerProgram.rest(180).totalSeconds)
        // Reps have no honest total: the length of a set of eights is not knowable in advance.
        assertNull(TimerProgram(sets = 4, work = WorkKind.REPS, restSeconds = 60).totalSeconds)
    }

    @Test
    fun `a program survives the process dying mid-way and resumes on the same set`() {
        val program = TimerProgram(sets = 5, work = WorkKind.TIMED, workSeconds = 10, restSeconds = 180)
        var now = 1_000L
        var state: TimerState = TimerTransitions.startProgram(id(), program, settings, now)
        // Through set 1 and into the rest after it.
        now = (state as TimerState.Running).deadlineElapsedMs
        state = TimerTransitions.advance(state, settings, now, id())

        val snapshot = TimerRestore.snapshot(state, bootCount = 7)
        val restored = TimerRestore.restore(snapshot, currentBootCount = 7, nowElapsedMs = now + 1_000)

        assertTrue(restored is TimerState.Running)
        val running = restored as TimerState.Running
        assertEquals(TimerPhase.REST, running.phase)
        assertEquals(0, running.setIndex)
        assertEquals(5, running.program.sets)
        // And it keeps going from there rather than stopping at one set.
        val next = TimerTransitions.advance(running, settings, running.deadlineElapsedMs, id())
        assertEquals(1, (next as TimerState.Running).setIndex)
        assertEquals(TimerPhase.WORK, next.phase)
    }

    @Test
    fun `a set of reps interrupted by the process dying is still that set of reps`() {
        val program = TimerProgram(sets = 4, work = WorkKind.REPS, restSeconds = 60)
        var state: TimerState = TimerTransitions.startProgram(id(), program, settings, 1_000)
        state = TimerTransitions.completeSet(state as TimerState.AwaitingSet, settings, 6_000, id())
        state = TimerTransitions.advance(
            state, settings, (state as TimerState.Running).deadlineElapsedMs, id(),
        )
        assertTrue(state is TimerState.AwaitingSet)

        val snapshot = TimerRestore.snapshot(state, bootCount = 2)
        // Nothing is counting, so even a reboot leaves it exactly where it was.
        val restored = TimerRestore.restore(snapshot, currentBootCount = 3, nowElapsedMs = 0)
        assertTrue(restored is TimerState.AwaitingSet)
        assertEquals(1, (restored as TimerState.AwaitingSet).setIndex)
        assertEquals(4, restored.program.sets)
    }

    @Test
    fun `the label travels with the program to every interval of it`() {
        val program = TimerProgram(
            sets = 3,
            work = WorkKind.TIMED,
            workSeconds = 10,
            restSeconds = 60,
            label = "Max hangs 20 mm",
        )
        var state: TimerState = TimerTransitions.startProgram(id(), program, settings, 1_000)
        repeat(3) {
            assertEquals("Max hangs 20 mm", state.activeLabel)
            val running = state as? TimerState.Running ?: return@repeat
            state = TimerTransitions.advance(running, settings, running.deadlineElapsedMs, id())
        }
        assertEquals("Max hangs 20 mm", state.activeLabel)
    }

    @Test
    fun `a program cancelled part way through reports the sets it actually reached`() {
        val program = TimerProgram(sets = 5, work = WorkKind.TIMED, workSeconds = 10, restSeconds = 60)
        var state: TimerState = TimerTransitions.startProgram(id(), program, settings, 1_000)
        // Finish set 1, rest, start set 2, then stop there.
        repeat(2) {
            val running = state as TimerState.Running
            state = TimerTransitions.advance(running, settings, running.deadlineElapsedMs, id())
        }
        val stopped = TimerTransitions.finish(state)
        assertEquals(2, (stopped as TimerState.Finished).setsCompleted)
    }

    @Test
    fun `a program refuses to describe something that cannot be counted`() {
        // A timed set with no length, and a bare rest of nothing, are not programs.
        runCatching { TimerProgram(sets = 2, work = WorkKind.TIMED, workSeconds = 0) }
            .let { assertTrue(it.isFailure) }
        runCatching { TimerProgram(sets = 1, work = WorkKind.NONE, restSeconds = 0) }
            .let { assertTrue(it.isFailure) }
        runCatching { TimerProgram(sets = 0, work = WorkKind.REPS, restSeconds = 60) }
            .let { assertTrue(it.isFailure) }
    }
}
