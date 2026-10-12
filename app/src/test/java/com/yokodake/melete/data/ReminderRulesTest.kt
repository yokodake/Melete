package com.yokodake.melete.data

import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.entity.TrackerType
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.reminders.ReminderRules
import com.yokodake.melete.data.reminders.TrackerReminder
import com.yokodake.melete.data.reminders.TrackerTrigger
import com.yokodake.melete.data.reminders.WeekPlan
import com.yokodake.melete.data.reminders.WorkoutKind
import com.yokodake.melete.data.reminders.WorkoutMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderRulesTest {

    /** A Tuesday. */
    private val day = LocalDate.of(2026, 10, 6)
    private val monday = LocalDate.of(2026, 10, 5)

    private fun occurrence(
        id: String,
        state: OccurrenceState = OccurrenceState.PLANNED,
        date: LocalDate? = day,
        order: Int = 0,
        category: ExerciseCategory? = null,
        circuit: String? = null,
        module: String? = null,
        optional: Boolean = false,
        exerciseId: String = id,
    ) = PlannedOccurrence(
        id = id, exerciseId = exerciseId, name = id, mode = ExerciseMode.DURATION, unilateral = false,
        measurementUnit = null, measurementMeaning = null, category = category, trainingDate = date,
        weekStart = monday, prescription = null, prescriptionUnreadable = false, state = state, comment = null,
        orderIndex = order, circuitInstanceId = circuit, moduleInstanceId = module, optional = optional,
    )

    private fun circuit(id: String, name: String, order: Int, routineId: String = "r-$id", module: String? = null) =
        WeekCircuit(id, routineId, name, 2, 0, 0, monday, day, order, moduleInstanceId = module)

    private fun module(id: String, name: String, order: Int, moduleId: String = "m-$id") =
        WeekModule(id, moduleId, name, monday, day, order)

    @Test
    fun `next time is later today, else the next allowed day`() {
        val morning = day.atTime(8, 0)
        assertEquals(day.atTime(20, 30), ReminderRules.nextTime(morning, 20 * 60 + 30))
        assertEquals(day.plusDays(1).atTime(7, 0), ReminderRules.nextTime(morning, 7 * 60))
        // At the minute itself, the next one is tomorrow: an alarm that just went off is not set again.
        assertEquals(day.plusDays(1).atTime(8, 0), ReminderRules.nextTime(morning, 8 * 60))
        // Sunday only, from a Tuesday.
        assertEquals(LocalDate.of(2026, 10, 11).atTime(19, 0), ReminderRules.nextTime(morning, 19 * 60, setOf(7)))
        // Tuesday only, already past: a week later.
        assertEquals(day.plusWeeks(1).atTime(7, 0), ReminderRules.nextTime(morning, 7 * 60, setOf(2)))
        assertNull(ReminderRules.nextTime(morning, 60, emptySet()))
    }

    @Test
    fun `workouts to log name each module, circuit and exercise once, in the day's order`() {
        val plan = WeekPlan(
            occurrences = listOf(
                occurrence("hang", order = 3),
                occurrence("station 1", circuit = "c1"),
                occurrence("station 2", circuit = "c1"),
                occurrence("curl", module = "m1"),
                occurrence("circuit in module", circuit = "c2"),
                occurrence("done", state = OccurrenceState.COMPLETED, order = 0),
                occurrence("skipped", state = OccurrenceState.SKIPPED),
                occurrence("optional", optional = true),
                occurrence("tomorrow", date = day.plusDays(1)),
                occurrence("undated", date = null),
            ),
            circuits = listOf(circuit("c1", "Cooldown", 4), circuit("c2", "Inner", 0, module = "m1")),
            modules = listOf(module("m1", "Wrist block", 1)),
        )
        assertEquals(listOf("Wrist block", "hang", "Cooldown"), ReminderRules.workoutsToLog(day, plan))
    }

    @Test
    fun `a short list names two and counts the rest`() {
        assertEquals("Board session", ReminderRules.listShort(listOf("Board session")))
        assertEquals("A, B", ReminderRules.listShort(listOf("A", "B")))
        assertEquals("Board session, Finger training + 2 more", ReminderRules.listShort(listOf("Board session", "Finger training", "c", "d")))
    }

    @Test
    fun `a review early in the week is about the last one`() {
        assertEquals(LocalDate.of(2026, 9, 28), ReminderRules.reviewedWeek(monday))
        assertEquals(LocalDate.of(2026, 9, 28), ReminderRules.reviewedWeek(day))
        assertEquals(monday, ReminderRules.reviewedWeek(LocalDate.of(2026, 10, 7)))
        assertEquals(monday, ReminderRules.reviewedWeek(LocalDate.of(2026, 10, 8)))
        assertEquals(monday, ReminderRules.reviewedWeek(LocalDate.of(2026, 10, 11)))
    }

    @Test
    fun `the week summary counts as Home does`() {
        val plan = WeekPlan(
            listOf(
                occurrence("a", OccurrenceState.COMPLETED),
                occurrence("b"),
                occurrence("c", OccurrenceState.SKIPPED),
                occurrence("d", optional = true),
                occurrence("e", OccurrenceState.COMPLETED, optional = true),
            ),
            emptyList(),
            emptyList(),
        )
        assertEquals("2 of 3 done", ReminderRules.weekSummary(plan))
        // A circuit is one, done when every station is.
        val withCircuit = plan.copy(
            occurrences = plan.occurrences + occurrence("s1", OccurrenceState.COMPLETED, circuit = "c1") + occurrence("s2", circuit = "c1"),
        )
        assertEquals("2 of 4 done", ReminderRules.weekSummary(withCircuit))
        assertEquals("Nothing was planned.", ReminderRules.weekSummary(WeekPlan(emptyList(), emptyList(), emptyList())))
    }

    @Test
    fun `matching work is a chosen category, exercise, circuit or module, logged or planned, not skipped`() {
        val board = TrackerReminder("t", trigger = TrackerTrigger.AFTER_WORKOUT, categories = setOf(ExerciseCategory.BOARD_CLIMBING.name))
        fun plan(vararg occurrences: PlannedOccurrence) = WeekPlan(
            occurrences.toList(),
            listOf(circuit("c1", "Cooldown", 0, routineId = "cooldown")),
            listOf(module("m1", "FA", 0, moduleId = "fa")),
        )
        assertTrue(ReminderRules.hadMatchingWork(board, day, plan(occurrence("limit", category = ExerciseCategory.BOARD_CLIMBING))))
        assertTrue(
            ReminderRules.hadMatchingWork(
                board, day, plan(occurrence("limit", OccurrenceState.COMPLETED, category = ExerciseCategory.BOARD_CLIMBING)),
            )
        )
        assertFalse(
            ReminderRules.hadMatchingWork(
                board, day, plan(occurrence("limit", OccurrenceState.SKIPPED, category = ExerciseCategory.BOARD_CLIMBING)),
            )
        )
        assertFalse(
            ReminderRules.hadMatchingWork(
                board, day, plan(occurrence("limit", date = day.minusDays(1), category = ExerciseCategory.BOARD_CLIMBING)),
            )
        )
        assertFalse(ReminderRules.hadMatchingWork(board, day, plan(occurrence("curl", category = ExerciseCategory.STRENGTH_CONDITIONING))))

        val picked = TrackerReminder(
            "t", trigger = TrackerTrigger.AFTER_WORKOUT,
            workouts = listOf(
                WorkoutMatch(WorkoutKind.EXERCISE, "x-hang", "Hang"),
                WorkoutMatch(WorkoutKind.CIRCUIT, "cooldown", "Cooldown"),
                WorkoutMatch(WorkoutKind.MODULE, "fa", "FA"),
            ),
        )
        assertTrue(ReminderRules.hadMatchingWork(picked, day, plan(occurrence("h", exerciseId = "x-hang"))))
        assertTrue(ReminderRules.hadMatchingWork(picked, day, plan(occurrence("s", circuit = "c1"))))
        assertTrue(ReminderRules.hadMatchingWork(picked, day, plan(occurrence("m", module = "m1"))))
        assertFalse(ReminderRules.hadMatchingWork(picked, day, plan(occurrence("other"))))
        // Optional work counts once done, not before.
        assertFalse(ReminderRules.hadMatchingWork(picked, day, plan(occurrence("h", exerciseId = "x-hang", optional = true))))
        assertTrue(
            ReminderRules.hadMatchingWork(
                picked, day, plan(occurrence("h", OccurrenceState.COMPLETED, exerciseId = "x-hang", optional = true)),
            )
        )
        assertFalse(ReminderRules.hadMatchingWork(picked, day, plan(occurrence("s", OccurrenceState.SKIPPED, circuit = "c1"))))
    }

    @Test
    fun `trackers already filled in are left out`() {
        val energy = Tracker("energy", "Energy", TrackerType.SCALE, 0, 5)
        val fingers = Tracker("fingers", "Finger discomfort", TrackerType.SCALE, 0, 5)
        val weight = Tracker("weight", "Bodyweight", TrackerType.NUMBER, unit = "kg")
        val reminder = TrackerReminder("t", trackerIds = listOf("fingers", "energy"))
        val trackers = listOf(energy, fingers, weight)
        assertEquals(listOf(energy, fingers), ReminderRules.trackersToFill(reminder, trackers, null))
        val day = DiaryDay(day, null, mapOf("energy" to TrackedValue(energy, TrackerReading(number = 3.0))))
        assertEquals(listOf(fingers), ReminderRules.trackersToFill(reminder, trackers, day))
        val both = day.copy(values = day.values + ("fingers" to TrackedValue(fingers, TrackerReading(number = 0.0))))
        assertTrue(ReminderRules.trackersToFill(reminder, trackers, both).isEmpty())
    }
}
