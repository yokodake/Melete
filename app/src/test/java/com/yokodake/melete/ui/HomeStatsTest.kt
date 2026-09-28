package com.yokodake.melete.ui

import com.yokodake.melete.data.Benchmark
import com.yokodake.melete.data.BenchmarkResult
import com.yokodake.melete.data.BenchmarkStanding
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.WeekCircuit
import com.yokodake.melete.data.entity.BenchmarkMeasure
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.ui.home.BenchmarkReminder
import com.yokodake.melete.ui.home.TodaySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeStatsTest {

    private val today = LocalDate.of(2026, 9, 28)

    private fun occurrence(
        id: String,
        date: LocalDate? = today,
        state: OccurrenceState = OccurrenceState.PLANNED,
        plan: PrescriptionPayload? = PrescriptionPayload(sets = 1, targetDurationSeconds = 600),
        mode: ExerciseMode = ExerciseMode.DURATION,
        circuit: String? = null,
        position: Int? = null,
    ) = PlannedOccurrence(
        id = id, exerciseId = id, name = id, mode = mode, unilateral = false, measurementUnit = null,
        measurementMeaning = null, category = null, trainingDate = date, weekStart = today,
        prescription = plan, prescriptionUnreadable = false, state = state, comment = null, orderIndex = 0,
        circuitInstanceId = circuit, circuitPosition = position,
    )

    @Test
    fun `counts today's exercises, skipped ones out of the target, undated ones not at all`() {
        val summary = TodaySummary.build(
            today,
            listOf(
                occurrence("a", state = OccurrenceState.COMPLETED),
                occurrence("b", state = OccurrenceState.COMPLETED),
                occurrence("c"),
                occurrence("d", state = OccurrenceState.SKIPPED),
                occurrence("anytime", date = null),
                occurrence("tomorrow", date = today.plusDays(1)),
            ),
            emptyList(),
        )
        assertEquals(2, summary.completed)
        assertEquals(3, summary.target)
        assertEquals(1, summary.skipped)
        assertFalse(summary.allDone)
    }

    @Test
    fun `remaining time is shown only when everything left has an estimate`() {
        val known = TodaySummary.build(today, listOf(occurrence("a"), occurrence("b")), emptyList())
        assertTrue(known.remainingSeconds!! > 1200)

        // An activity with no length says nothing about how long it takes: no partial total.
        val unknown = TodaySummary.build(
            today,
            listOf(occurrence("a"), occurrence("run", plan = PrescriptionPayload(sets = 1), mode = ExerciseMode.ACTIVITY)),
            emptyList(),
        )
        assertNull(unknown.remainingSeconds)

        val done = TodaySummary.build(today, listOf(occurrence("a", state = OccurrenceState.COMPLETED)), emptyList())
        assertTrue(done.allDone)
        assertNull(done.remainingSeconds)
    }

    @Test
    fun `nothing planned is its own state, and a circuit counts its exercises, not itself`() {
        assertTrue(TodaySummary.build(today, emptyList(), emptyList()).nothingPlanned)

        val circuit = WeekCircuit(
            id = "c1", routineId = "r", name = "Pull + core", rounds = 3, transitionSeconds = 30,
            roundRestSeconds = 120, weekStart = today, trainingDate = today, orderIndex = 0,
        )
        val stations = listOf(
            occurrence("pull", circuit = "c1", position = 0, plan = PrescriptionPayload(sets = 1, targetReps = 5), mode = ExerciseMode.REPETITIONS),
            occurrence("plank", circuit = "c1", position = 1),
        )
        val summary = TodaySummary.build(today, stations, listOf(circuit))
        assertEquals(2, summary.target)
        // The stations share the circuit's one clock, rests included, rather than each estimating
        // its own standalone plan.
        val whole = com.yokodake.melete.data.timer.PrescriptionProgram.circuit(
            label = "Pull + core", rounds = 3, transitionSeconds = 30, roundRestSeconds = 120,
            stations = stations.map { com.yokodake.melete.data.timer.StationPlan(it.name, it.mode, false, it.prescription, it.id) },
        ).estimatedSeconds()
        assertEquals(whole, summary.remainingSeconds)
    }

    // ------------------------------------------------------------ reminders

    private fun standing(name: String, last: LocalDate?, hidden: Boolean = false) = BenchmarkStanding.of(
        Benchmark(name, name, BenchmarkMeasure.LOAD, "kg", null, false, true, null, null, 0, hidden),
        listOfNotNull(
            last?.let {
                BenchmarkResult("$name-r", name, it, 20.0, null, "kg", null, false, null, 0)
            }
        ),
    )

    @Test
    fun `the oldest visible benchmark at least six months untested is the one reminded`() {
        val standings = listOf(
            standing("Recent", today.minusMonths(2)),
            standing("Max hang", today.minusMonths(8)),
            standing("Older but hidden", today.minusYears(2), hidden = true),
            standing("Box split", today.minusMonths(7)),
            standing("Never tested", null),
        )
        val reminder = BenchmarkReminder.pick(standings, today, dismissedUntil = null)!!
        assertEquals("Max hang", reminder.name)
        assertEquals("8 months ago", reminder.ageText(today))
        assertEquals("2 years ago", BenchmarkReminder("x", "x", today.minusMonths(25)).ageText(today))
    }

    @Test
    fun `the reminder interval is a setting, and off means none`() {
        val old = listOf(standing("Max hang", today.minusMonths(4)))
        assertNull(BenchmarkReminder.pick(old, today, null, months = 6))
        assertEquals("Max hang", BenchmarkReminder.pick(old, today, null, months = 3)?.name)
        assertNull(BenchmarkReminder.pick(old, today, null, months = 0))
    }

    @Test
    fun `no reminder when none qualifies or while dismissed`() {
        assertNull(BenchmarkReminder.pick(listOf(standing("Recent", today.minusMonths(5))), today, null))
        val old = listOf(standing("Max hang", today.minusMonths(8)))
        assertNull(BenchmarkReminder.pick(old, today, dismissedUntil = today.plusDays(10)))
        assertEquals("Max hang", BenchmarkReminder.pick(old, today, dismissedUntil = today.minusDays(1))?.name)
    }
}
