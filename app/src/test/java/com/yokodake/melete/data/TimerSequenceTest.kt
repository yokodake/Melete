package com.yokodake.melete.data

import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.data.model.RepeaterPrescription
import com.yokodake.melete.data.timer.CueSettings
import com.yokodake.melete.data.timer.DurationEstimate
import com.yokodake.melete.data.timer.PrescriptionProgram
import com.yokodake.melete.data.timer.RepeaterSpec
import com.yokodake.melete.data.timer.StationPlan
import com.yokodake.melete.data.timer.TimerEntry
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.data.timer.TimerTransitions
import com.yokodake.melete.data.timer.WorkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unilateral execution, repeaters and circuits, walked exactly as the controller walks them.
 *
 * The whole point of flattening a program into a list of steps is that every one of these shapes
 * is the same succession of intervals, so they can all be asserted as plain strings with no device
 * and no clock.
 */
class TimerSequenceTest {

    private val settings = CueSettings()
    private var nextId = 0

    private fun id() = "run-${nextId++}"

    /** One interval, as a readable line: phase, where you are, and how long it counts. */
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

    private fun walk(program: TimerProgram, limit: Int = 200): List<String> {
        val steps = mutableListOf<String>()
        var now = 1_000L
        var state = TimerTransitions.startProgram(id(), program, settings, now)
        repeat(limit) {
            steps += describe(state)
            when (val current = state) {
                is TimerState.Running -> {
                    now = current.deadlineElapsedMs
                    state = TimerTransitions.advance(current, settings, now, id())
                }

                is TimerState.AwaitingSet -> {
                    now += 5_000
                    state = TimerTransitions.completeSet(current, settings, now, id())
                }

                else -> return steps
            }
        }
        return steps
    }

    // -------------------------------------------------------- unilateral

    @Test
    fun `a unilateral timed set runs left, switch, right, and only then rests`() {
        val program = TimerProgram(
            sets = 2,
            work = WorkKind.TIMED,
            workSeconds = 10,
            restSeconds = 60,
            unilateral = true,
        )
        assertEquals(
            listOf(
                "prepare set1 L 5s",
                "work set1 L 10s",
                // Switching is a rest, and a long enough one serves as the next set's lead-in.
                "switch set1 R 15s",
                "work set1 R 10s",
                // The set rest falls after both sides, never between them.
                "rest set1 60s",
                "work set2 L 10s",
                "switch set2 R 15s",
                "work set2 R 10s",
                "finished 2",
            ),
            walk(program),
        )
    }

    @Test
    fun `a unilateral set of reps waits for each side in turn`() {
        val program = TimerProgram(
            sets = 2,
            work = WorkKind.REPS,
            workReps = 8,
            restSeconds = 60,
            unilateral = true,
        )
        assertEquals(
            listOf(
                "reps set1 L",
                "switch set1 R 15s",
                "reps set1 R",
                "rest set1 60s",
                "reps set2 L",
                "switch set2 R 15s",
                "reps set2 R",
                "finished 2",
            ),
            walk(program),
        )
    }

    @Test
    fun `a zero-second side switch still gives you the ordinary lead-in`() {
        val program = TimerProgram(
            sets = 1,
            work = WorkKind.TIMED,
            workSeconds = 10,
            unilateral = true,
            sideSwitchSeconds = 0,
        )
        assertEquals(
            listOf(
                "prepare set1 L 5s",
                "work set1 L 10s",
                // No switch interval at all, so the existing short-transition rule applies and the
                // right side is led into rather than starting the instant the left one ends.
                "prepare set1 R 5s",
                "work set1 R 10s",
                "finished 1",
            ),
            walk(program),
        )
    }

    @Test
    fun `bilateral work is untouched by any of it`() {
        val program = TimerProgram(sets = 2, work = WorkKind.TIMED, workSeconds = 10, restSeconds = 60)
        assertEquals(
            listOf(
                "prepare set1 5s", "work set1 10s", "rest set1 60s", "work set2 10s", "finished 2",
            ),
            walk(program),
        )
    }

    @Test
    fun `transport lands on a side and says which one`() {
        val program = TimerProgram(
            sets = 2,
            work = WorkKind.TIMED,
            workSeconds = 10,
            restSeconds = 60,
            unilateral = true,
        )
        var state: TimerState = TimerTransitions.startProgram(id(), program, settings, 1_000)
        // Preparation, left, switch -- then skip forward onto the right side.
        repeat(2) {
            val running = state as TimerState.Running
            state = TimerTransitions.advance(running, settings, running.deadlineElapsedMs, id())
        }
        state = TimerTransitions.next(state, settings, 50_000, id())
        // Reached by hand, so the set is about to start on your say-so and gets its five seconds.
        assertEquals("prepare set1 R 5s", describe(state))
        assertEquals(BodySide.RIGHT, state.currentSide)

        state = TimerTransitions.previous(state, settings, 50_100, id())
        assertEquals("switch set1 R 15s", describe(state))
    }

    // ---------------------------------------------------------- repeaters

    private val repeaters = TimerProgram(
        sets = 3,
        work = WorkKind.TIMED,
        restSeconds = 180,
        repeater = RepeaterSpec(repsPerSet = 6, workSecondsPerRep = 7, restSecondsBetweenReps = 3),
        label = "Repeaters 20 mm",
    )

    @Test
    fun `three sets of six sevens with three between them, and no rep rest after the last pulse`() {
        val walked = walk(repeaters)
        assertEquals("prepare set1 rep1 5s", walked.first())
        assertEquals(
            listOf(
                "work set1 rep1 7s", "rep_rest set1 rep1 3s",
                "work set1 rep2 7s", "rep_rest set1 rep2 3s",
                "work set1 rep3 7s", "rep_rest set1 rep3 3s",
                "work set1 rep4 7s", "rep_rest set1 rep4 3s",
                "work set1 rep5 7s", "rep_rest set1 rep5 3s",
                // After the sixth pulse the set rest takes over; there is no extra rep rest.
                "work set1 rep6 7s", "rest set1 180s",
            ),
            walked.subList(1, 13),
        )
        assertEquals("work set2 rep1 7s", walked[13])
        assertEquals("finished 3", walked.last())
        // No trailing rest after the last set, and no rep rest after the last pulse of it.
        assertEquals("work set3 rep6 7s", walked[walked.lastIndex - 1])
    }

    @Test
    fun `no five-second preparation is inserted between pulses`() {
        assertEquals(1, walk(repeaters).count { it.startsWith("prepare") })
    }

    @Test
    fun `a unilateral repeater does every pulse on one side before switching`() {
        val program = TimerProgram(
            sets = 1,
            work = WorkKind.TIMED,
            unilateral = true,
            repeater = RepeaterSpec(repsPerSet = 2, workSecondsPerRep = 7, restSecondsBetweenReps = 3),
        )
        assertEquals(
            listOf(
                "prepare set1 L rep1 5s",
                "work set1 L rep1 7s",
                "rep_rest set1 L rep1 3s",
                "work set1 L rep2 7s",
                "switch set1 R 15s",
                "work set1 R rep1 7s",
                "rep_rest set1 R rep1 3s",
                "work set1 R rep2 7s",
                "finished 1",
            ),
            walk(program),
        )
    }

    @Test
    fun `one rep, one set and zero rests degenerate to a single interval`() {
        val program = TimerProgram(
            sets = 1,
            work = WorkKind.TIMED,
            repeater = RepeaterSpec(repsPerSet = 1, workSecondsPerRep = 7),
        )
        assertEquals(listOf("prepare set1 rep1 5s", "work set1 rep1 7s", "finished 1"), walk(program))
    }

    @Test
    fun `a repeater totals its pulses and its rests, and counts only the pulses as work`() {
        // 3 x (6 x 7s + 5 x 3s) + 2 x 180s
        assertEquals(531, repeaters.totalSeconds)
        assertEquals(126, repeaters.workOnlySeconds)
        // Plus the one preparation the sequence actually generates.
        assertEquals(536, repeaters.estimatedSeconds())
    }

    // ----------------------------------------------------------- circuits

    private val hang = TimerEntry(
        label = "Hang",
        work = WorkKind.TIMED,
        workSeconds = 20,
        // A standalone set count that the circuit must ignore rather than multiply by.
        sets = 4,
        restSeconds = 90,
    )

    private val row = TimerEntry(label = "Row", work = WorkKind.REPS, workReps = 8, sets = 3)

    private val circuit = TimerProgram.circuit(
        entries = listOf(hang, row),
        rounds = 2,
        transitionSeconds = 30,
        roundRestSeconds = 120,
        label = "Pull circuit",
    )

    @Test
    fun `a circuit runs one set of each exercise per round and ignores their own set counts`() {
        assertEquals(
            listOf(
                "prepare round1 e1 5s",
                "work round1 e1 20s",
                "transition round1 e1 30s",
                "reps round1 e2",
                // The round rest replaces the transition after the last station.
                "round_rest round1 e2 120s",
                "work round2 e1 20s",
                "transition round2 e1 30s",
                "reps round2 e2",
                // And there is none at all after the final round.
                "finished 2",
            ),
            walk(circuit),
        )
    }

    @Test
    fun `a circuit never uses a station's own set rest`() {
        assertTrue(circuit.steps.none { it.phase == TimerPhase.REST })
        assertEquals(2, circuit.steps.count { it.phase == TimerPhase.TRANSITION })
        assertEquals(1, circuit.steps.count { it.phase == TimerPhase.ROUND_REST })
    }

    @Test
    fun `a circuit station may be unilateral and may be a repeater`() {
        val program = TimerProgram.circuit(
            entries = listOf(
                TimerEntry(label = "Pistol", work = WorkKind.REPS, unilateral = true, sideSwitchSeconds = 10),
                TimerEntry(
                    label = "Repeaters",
                    work = WorkKind.TIMED,
                    repeater = RepeaterSpec(repsPerSet = 2, workSecondsPerRep = 7, restSecondsBetweenReps = 3),
                ),
            ),
            rounds = 1,
            transitionSeconds = 30,
        )
        assertEquals(
            listOf(
                "reps round1 e1 L",
                "switch round1 e1 R 10s",
                "reps round1 e1 R",
                "transition round1 e1 30s",
                "work round1 e2 rep1 7s",
                "rep_rest round1 e2 rep1 3s",
                "work round1 e2 rep2 7s",
                "finished 1",
            ),
            walk(program),
        )
    }

    @Test
    fun `a circuit is timed once from its own sequence, never by summing its stations`() {
        // Round: 20s hang + 30s transition + an 8-rep set + (120s round rest between rounds).
        // Preparation lands once, before the very first hang: the round rest is long enough to
        // serve as the second round's lead-in, exactly as a set rest does.
        val perRound = 20 + 30 + 8 * TimerProgram.ASSUMED_SECONDS_PER_REP
        assertEquals(2 * perRound + 120 + TimerProgram.PREPARE_SECONDS, circuit.estimatedSeconds())
        // A station's standalone plan would have said four sets of twenty with ninety between.
        assertTrue(circuit.estimatedSeconds() < 4 * 20 + 3 * 90)
        assertNull(circuit.totalSeconds)
        assertEquals(2 * 20, circuit.workOnlySeconds)
    }

    @Test
    fun `every second of a circuit is allocated to exactly one exercise`() {
        val shares = circuit.estimatedSecondsByEntry()
        assertEquals(2, shares.size)
        // The parts add up to the whole, which is what stops a later total counting a circuit and
        // its exercises both.
        assertEquals(circuit.estimatedSeconds(), shares.sum())
        // The transition after the hang belongs to the hang, and the round rest after the row
        // belongs to the row: each gap is charged to the station you are walking away from.
        assertEquals(2 * (20 + 30) + TimerProgram.PREPARE_SECONDS, shares[0])
        assertEquals(2 * 8 * TimerProgram.ASSUMED_SECONDS_PER_REP + 120, shares[1])
    }

    @Test
    fun `a standalone program allocates everything to its one exercise`() {
        assertEquals(listOf(repeaters.estimatedSeconds()), repeaters.estimatedSecondsByEntry())
    }

    @Test
    fun `a circuit station with no target length waits for you rather than resting`() {
        // Standalone this plan is a bare rest, because resting is all there is to count. A
        // station you stand still for would be nonsense, and copying a zero set rest onto a
        // rest-only entry is not even a valid program, so it becomes an untimed set instead.
        val station = StationPlan(
            label = "Open climbing",
            mode = ExerciseMode.DURATION,
            unilateral = false,
            prescription = PrescriptionPayload(sets = 3, restSeconds = 60),
        )
        assertEquals(WorkKind.NONE, PrescriptionProgram.entryOf("x", ExerciseMode.DURATION, false, station.prescription).work)
        assertEquals(WorkKind.REPS, PrescriptionProgram.stationOf(station).work)

        val program = PrescriptionProgram.circuit(
            label = "Mixed",
            rounds = 2,
            transitionSeconds = 30,
            roundRestSeconds = 60,
            stations = listOf(station),
        )
        assertEquals(
            listOf("reps round1 e1", "round_rest round1 e1 60s", "reps round2 e1", "finished 2"),
            walk(program),
        )
    }

    // -------------------------------------------------- planned durations

    @Test
    fun `an estimate follows the sequence the plan actually generates`() {
        val plan = PrescriptionPayload(sets = 3, targetDurationSeconds = 10, restSeconds = 60)
        // 3 x 10s + 2 x 60s, plus the one preparation before the first set.
        assertEquals(
            155,
            DurationEstimate.forPrescription(ExerciseMode.DURATION, unilateral = false, plan),
        )
        // Both sides, with a side switch between them, make the same plan take much longer.
        assertEquals(
            3 * (10 + 15 + 10) + 2 * 60 + 5,
            DurationEstimate.forPrescription(ExerciseMode.DURATION, unilateral = true, plan),
        )
    }

    @Test
    fun `reps are estimated from a documented per-rep assumption`() {
        val plan = PrescriptionPayload(sets = 4, targetReps = 8, restSeconds = 90)
        assertEquals(
            4 * 8 * TimerProgram.ASSUMED_SECONDS_PER_REP + 3 * 90,
            DurationEstimate.forPrescription(ExerciseMode.REPETITIONS, unilateral = false, plan),
        )
        // With no rep count at all, a set stands for the flat per-set assumption instead.
        assertEquals(
            4 * TimerProgram.ASSUMED_SECONDS_PER_SET + 3 * 90,
            DurationEstimate.forPrescription(
                ExerciseMode.REPETITIONS,
                unilateral = false,
                PrescriptionPayload(sets = 4, restSeconds = 90),
            ),
        )
    }

    @Test
    fun `a repeater prescription is estimated as its pulses`() {
        val plan = PrescriptionPayload(
            sets = 3,
            restSeconds = 180,
            repeater = RepeaterPrescription(
                repsPerSet = 6,
                workSecondsPerRep = 7,
                restSecondsBetweenReps = 3,
            ),
        )
        assertEquals(
            536,
            DurationEstimate.forPrescription(ExerciseMode.REPEATERS, unilateral = false, plan),
        )
    }

    @Test
    fun `no useful estimate is a real answer rather than a fabricated zero`() {
        assertNull(
            DurationEstimate.forPrescription(ExerciseMode.DURATION, false, PrescriptionPayload(sets = 3)),
        )
        assertNull(
            DurationEstimate.forPrescription(ExerciseMode.ACTIVITY, false, PrescriptionPayload(sets = 1)),
        )
        assertNull(DurationEstimate.forPrescription(ExerciseMode.REPETITIONS, false, null))
        // An activity that says how long it should take is simply that long.
        assertEquals(
            3_600,
            DurationEstimate.forPrescription(
                ExerciseMode.ACTIVITY,
                false,
                PrescriptionPayload(sets = 1, targetDurationSeconds = 3_600),
            ),
        )
    }

    @Test
    fun `the program the timer runs is the program the estimate measured`() {
        val plan = PrescriptionPayload(
            sets = 2,
            targetDurationSeconds = 10,
            restSeconds = 60,
            sideSwitchSeconds = 5,
        )
        val program = PrescriptionProgram.of("Copenhagen", ExerciseMode.DURATION, true, plan)
        assertEquals(
            program.estimatedSeconds(),
            DurationEstimate.forPrescription(ExerciseMode.DURATION, true, plan),
        )
        assertEquals(5, program.entry.sideSwitchSeconds)
    }
}
