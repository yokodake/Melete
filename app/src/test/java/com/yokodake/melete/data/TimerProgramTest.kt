package com.yokodake.melete.data

import com.yokodake.melete.data.entity.BodySide
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

    /**
     * One interval, as a readable line.
     *
     * Everything that distinguishes an interval appears only when it distinguishes something: a
     * plain bilateral program still reads `work set1 10s`, so the expectations that predate sides,
     * pulses and circuits say exactly what they always said.
     */
    private fun describe(state: TimerState): String {
        val program = state.activeProgram
        val step = state.currentStep
        val where = buildString {
            append(if (program?.isCircuit == true) "round" else "set")
            append(state.currentSet ?: 0)
            if (program?.isCircuit == true) append(" e${(step?.entryIndex ?: 0) + 1}")
            when (step?.side) {
                BodySide.LEFT -> append(" L")
                BodySide.RIGHT -> append(" R")
                null -> Unit
            }
            state.currentRep?.let { append(" rep$it") }
        }
        return when (state) {
            is TimerState.Running ->
                "${state.phase.name.lowercase()} $where ${state.totalMs / 1000}s"

            is TimerState.AwaitingSet -> "reps $where"
            is TimerState.Finished -> "finished ${state.setsCompleted}"
            else -> state.toString()
        }
    }

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
                    steps += describe(current)
                    now = current.deadlineElapsedMs
                    state = TimerTransitions.advance(current, settings, now, id())
                }

                is TimerState.AwaitingSet -> {
                    steps += describe(current)
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
                // Only the first set is led into by a preparation. Every later one comes out of a
                // three-minute rest, whose own last seconds are the getting-ready.
                "prepare set1 5s",
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
    fun `with no rest between them, every set still gets its five seconds`() {
        // Nothing precedes set two but the end of set one, so there is no rest to be ready during.
        val program = TimerProgram(sets = 3, work = WorkKind.TIMED, workSeconds = 20, restSeconds = 0)
        assertEquals(
            listOf(
                "prepare set1 5s", "work set1 20s",
                "prepare set2 5s", "work set2 20s",
                "prepare set3 5s", "work set3 20s",
                "finished 3",
            ),
            walk(program),
        )
    }

    @Test
    fun `a rest too short to be a run-up is followed by a preparation anyway`() {
        val program = TimerProgram(sets = 2, work = WorkKind.TIMED, workSeconds = 20, restSeconds = 3)
        assertEquals(
            listOf(
                "prepare set1 5s", "work set1 20s",
                "rest set1 3s",
                "prepare set2 5s", "work set2 20s",
                "finished 2",
            ),
            walk(program),
        )
    }

    @Test
    fun `a bare rest and a set of reps are never preceded by a preparation`() {
        // Nothing is about to start on its own, so there is nothing to be ready for.
        assertEquals(listOf("rest set1 180s", "finished 1"), walk(TimerProgram.rest(180)))
        assertEquals(
            listOf("reps set1", "rest set1 60s", "reps set2", "finished 2"),
            walk(TimerProgram(sets = 2, work = WorkKind.REPS, restSeconds = 60)),
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
    fun `a single timed set is prepared for and then has nothing after it`() {
        assertEquals(
            listOf("prepare set1 5s", "work set1 10s", "finished 1"),
            walk(TimerProgram.work(10)),
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
        // A preparation, three work intervals and two rests: six runs, six distinct ids. Cue
        // bookkeeping is keyed by run id, so sharing one would silence every set after the first.
        assertEquals(6, ids.size)
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
        // Through the preparation, through set 1, and into the rest after it.
        repeat(2) {
            now = (state as TimerState.Running).deadlineElapsedMs
            state = TimerTransitions.advance(state, settings, now, id())
        }

        val snapshot = TimerRestore.snapshot(state, bootCount = 7)
        val restored = TimerRestore.restore(snapshot, currentBootCount = 7, nowElapsedMs = now + 1_000)

        assertTrue(restored is TimerState.Running)
        val running = restored as TimerState.Running
        assertEquals(TimerPhase.REST, running.phase)
        assertEquals(1, running.currentSet)
        assertEquals(5, running.program.sets)
        // And it keeps going from there rather than stopping at one set.
        val next = TimerTransitions.advance(running, settings, running.deadlineElapsedMs, id())
        assertEquals(2, (next as TimerState.Running).currentSet)
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
        assertEquals(2, (restored as TimerState.AwaitingSet).currentSet)
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
        // Preparation, set 1, its rest, then into set 2 -- and stop there.
        repeat(3) {
            val running = state as TimerState.Running
            state = TimerTransitions.advance(running, settings, running.deadlineElapsedMs, id())
        }
        val stopped = TimerTransitions.finish(state)
        assertEquals(2, (stopped as TimerState.Finished).setsCompleted)
    }

    // ------------------------------------------------- skipping and resuming

    private val fiveSets = TimerProgram(
        sets = 5,
        work = WorkKind.TIMED,
        workSeconds = 10,
        restSeconds = 180,
    )



    private fun forward(state: TimerState, now: Long = 50_000) =
        TimerTransitions.next(state, settings, now, id())

    private fun back(state: TimerState, now: Long = 50_000) =
        TimerTransitions.previous(state, settings, now, id())

    /** The instant a running interval started, so a test can say how far into it we are. */
    private fun startedAt(state: TimerState): Long = when (state) {
        is TimerState.Running -> state.deadlineElapsedMs - state.totalMs
        else -> error("not a countdown")
    }

    @Test
    fun `skipping forward out of a rest lands on the next set with its five seconds`() {
        // Mid-rest after set one, and impatient.
        var state: TimerState = TimerTransitions.startProgram(id(), fiveSets, settings, 1_000)
        state = TimerTransitions.advance(state, settings, 6_000, id())   // prepare -> work
        state = TimerTransitions.advance(state, settings, 16_000, id())  // work -> rest
        assertEquals("rest set1 180s", describe(state))

        // Skipping is a button press, so the rest is no longer the run-up: the preparation is.
        val skipped = forward(state)
        assertEquals("prepare set2 5s", describe(skipped))
        assertEquals("work set2 10s", describe(TimerTransitions.advance(skipped, settings, 55_000, id())))
    }

    @Test
    fun `back, once a countdown has been running, restarts it`() {
        // Ninety seconds into a three-minute rest, and the wrong button gets pressed. Losing the
        // ninety seconds is worse than losing nothing, so this one starts the rest again.
        var state: TimerState = TimerTransitions.startProgram(id(), fiveSets, settings, 1_000)
        state = TimerTransitions.advance(state, settings, 6_000, id())
        state = TimerTransitions.advance(state, settings, 16_000, id())
        assertEquals("rest set1 180s", describe(state))

        val restarted = back(state, now = startedAt(state) + 90_000)
        assertEquals("rest set1 180s", describe(restarted))
        assertEquals(180_000, (restarted as TimerState.Running).remainingMs(startedAt(state) + 90_000))
    }

    @Test
    fun `back, straight after an interval starts, goes to the one before it`() {
        var state: TimerState = TimerTransitions.startProgram(id(), fiveSets, settings, 1_000)
        state = TimerTransitions.advance(state, settings, 6_000, id())
        state = TimerTransitions.advance(state, settings, 16_000, id())
        assertEquals("rest set1 180s", describe(state))

        // Half a second in: this was a correction, not a restart.
        assertEquals("prepare set1 5s", describe(back(state, now = startedAt(state) + 500)))
    }

    @Test
    fun `back from a set that just started lands on the rest before it`() {
        var state: TimerState = TimerTransitions.startProgram(id(), fiveSets, settings, 1_000)
        repeat(3) { state = TimerTransitions.advance(state, settings, 10_000L * (it + 1), id()) }
        assertEquals("work set2 10s", describe(state))

        assertEquals("rest set1 180s", describe(back(state, now = startedAt(state) + 200)))
    }

    @Test
    fun `back from the very first interval restarts it rather than doing nothing`() {
        val state: TimerState = TimerTransitions.startProgram(id(), fiveSets, settings, 1_000)
        assertEquals("prepare set1 5s", describe(state))
        // Both sides of the window clamp to the same place, because there is nothing before it.
        assertEquals("prepare set1 5s", describe(back(state, now = 1_200)))
        assertEquals("prepare set1 5s", describe(back(state, now = 4_000)))
    }

    @Test
    fun `skipping past the last interval ends the program`() {
        val last = TimerTransitions.startProgram(id(), TimerProgram.work(10), settings, 1_000)
        assertEquals("finished 1", describe(forward(last)))
    }

    @Test
    fun `skipping in a reps program moves between the sets and their rests`() {
        val reps = TimerProgram(sets = 3, work = WorkKind.REPS, restSeconds = 60)
        val state: TimerState = TimerTransitions.startProgram(id(), reps, settings, 1_000)
        assertEquals("reps set1", describe(state))
        val rest = forward(state)
        assertEquals("rest set1 60s", describe(rest))
        // A set of reps never gets a preparation: it does not start without you.
        assertEquals("reps set2", describe(forward(rest)))
    }

    @Test
    fun `back from a set of reps goes to the interval before it, with no window`() {
        // Nothing is counting, so there is no elapsed time to measure and restarting would do
        // nothing you could see.
        val reps = TimerProgram(sets = 3, work = WorkKind.REPS, restSeconds = 60)
        var state: TimerState = TimerTransitions.startProgram(id(), reps, settings, 1_000)
        state = TimerTransitions.completeSet(state as TimerState.AwaitingSet, settings, 6_000, id())
        state = TimerTransitions.advance(state, settings, 66_000, id())
        assertEquals("reps set2", describe(state))

        assertEquals("rest set1 60s", describe(back(state, now = 999_999)))
    }

    @Test
    fun `resuming inside a set just carries on`() {
        var state: TimerState = TimerTransitions.startProgram(id(), fiveSets, settings, 1_000)
        state = TimerTransitions.advance(state, settings, 6_000, id())
        assertEquals("work set1 10s", describe(state))

        val paused = TimerTransitions.pause(state as TimerState.Running, 9_000)
        assertEquals(7_000, paused.remainingMs)
        val resumed = TimerTransitions.resume(paused, 100_000, settings, id())
        // Same interval, same id, same seven seconds. Pausing changed nothing about the set.
        assertEquals("work set1 10s", describe(resumed))
        assertEquals(paused.runId, resumed.runId)
        assertEquals(7_000, resumed.remainingMs(100_000))
    }

    @Test
    fun `resuming a rest with plenty left just carries on`() {
        var state: TimerState = TimerTransitions.startProgram(id(), fiveSets, settings, 1_000)
        state = TimerTransitions.advance(state, settings, 6_000, id())
        state = TimerTransitions.advance(state, settings, 16_000, id())

        val paused = TimerTransitions.pause(state as TimerState.Running, 100_000)
        val resumed = TimerTransitions.resume(paused, 500_000, settings, id())
        assertEquals("rest set1 180s", describe(resumed))
        assertEquals(paused.remainingMs, resumed.remainingMs(500_000))
    }

    @Test
    fun `resuming a rest with only a moment left becomes a preparation for the next set`() {
        var state: TimerState = TimerTransitions.startProgram(id(), fiveSets, settings, 1_000)
        state = TimerTransitions.advance(state, settings, 6_000, id())
        state = TimerTransitions.advance(state, settings, 16_000, id())

        // Paused with two seconds of rest to go: unpausing straight into the set would give no
        // time to get back to the bar.
        val paused = TimerTransitions.pause(state as TimerState.Running, 194_000)
        assertEquals(2_000, paused.remainingMs)

        val resumed = TimerTransitions.resume(paused, 900_000, settings, id())
        assertEquals("prepare set2 5s", describe(resumed))
        assertEquals(5_000, resumed.remainingMs(900_000))
        // A fresh interval, because the rest it replaced had already sounded some of its cues.
        assertTrue(resumed.runId != paused.runId)
    }

    @Test
    fun `a bare rest that is nearly over is just nearly over`() {
        // Nothing follows it, so there is nothing to get ready for.
        val state = TimerTransitions.startProgram(id(), TimerProgram.rest(180), settings, 1_000)
        val paused = TimerTransitions.pause(state as TimerState.Running, 179_000)
        assertEquals(2_000, paused.remainingMs)
        val resumed = TimerTransitions.resume(paused, 900_000, settings, id())
        assertEquals("rest set1 180s", describe(resumed))
        assertEquals(2_000, resumed.remainingMs(900_000))
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
