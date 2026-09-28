package com.yokodake.melete.ui

import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.WeekCircuit
import com.yokodake.melete.data.WeekModule
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.ui.week.PrescriptionSummary
import com.yokodake.melete.ui.week.WeekItem
import com.yokodake.melete.ui.week.WeekUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekUiStateTest {

    private val monday = LocalDate.of(2026, 9, 21)

    private fun occurrence(
        name: String,
        date: LocalDate?,
        order: Int = 0,
        mode: ExerciseMode = ExerciseMode.REPETITIONS,
        unilateral: Boolean = false,
        prescription: PrescriptionPayload? = PrescriptionPayload(sets = 3, targetReps = 5),
        circuitInstanceId: String? = null,
        circuitPosition: Int? = null,
        moduleInstanceId: String? = null,
        modulePosition: Int? = null,
    ) = PlannedOccurrence(
        id = "$name-$date-$order",
        exerciseId = name,
        name = name,
        mode = mode,
        unilateral = unilateral,
        measurementUnit = "kg",
        measurementMeaning = MeasurementMeaning.TOTAL_LOAD,
        category = null,
        trainingDate = date,
        weekStart = monday,
        prescription = prescription,
        prescriptionUnreadable = false,
        state = OccurrenceState.PLANNED,
        comment = null,
        orderIndex = order,
        circuitInstanceId = circuitInstanceId,
        circuitPosition = circuitPosition,
        moduleInstanceId = moduleInstanceId,
        modulePosition = modulePosition,
    )

    private fun module(id: String, date: LocalDate?, order: Int = 0) = WeekModule(
        id = id,
        moduleId = "template-$id",
        name = id,
        weekStart = monday,
        trainingDate = date,
        orderIndex = order,
    )

    private fun circuit(
        id: String,
        date: LocalDate?,
        order: Int = 0,
        moduleInstanceId: String? = null,
        modulePosition: Int? = null,
    ) = WeekCircuit(
        id = id,
        routineId = "routine-$id",
        name = id,
        rounds = 3,
        transitionSeconds = 30,
        roundRestSeconds = 120,
        weekStart = monday,
        trainingDate = date,
        orderIndex = order,
        moduleInstanceId = moduleInstanceId,
        modulePosition = modulePosition,
    )

    /** The names of a slot's cards: a circuit or module reads as its own name, not its contents. */
    private fun List<WeekItem>.names(): List<String> = map { item ->
        when (item) {
            is WeekItem.Single -> item.occurrence.name
            is WeekItem.Circuit -> item.circuit.name
            is WeekItem.Module -> item.module.name
        }
    }

    // ------------------------------------------------------------- modules

    @Test
    fun `a module is one group holding its exercises and circuits in module order`() {
        val state = WeekUiState.build(
            weekStart = monday,
            today = monday,
            occurrences = listOf(
                occurrence("Warm-up", monday, order = 0),
                occurrence("Mobility", monday, order = 3, moduleInstanceId = "Fingers", modulePosition = 1),
                occurrence("Max hangs", monday, order = 2, moduleInstanceId = "Fingers", modulePosition = 0),
                // A station of a circuit that is itself a member: listed once, inside the circuit.
                occurrence(
                    "Row", monday, order = 4,
                    circuitInstanceId = "Pull", circuitPosition = 0,
                    moduleInstanceId = "Fingers", modulePosition = 2,
                ),
            ),
            circuits = listOf(
                circuit("Pull", monday, order = 4, moduleInstanceId = "Fingers", modulePosition = 2),
            ),
            modules = listOf(module("Fingers", monday, order = 1)),
        )
        assertEquals(listOf("Warm-up", "Fingers"), state.days[0].items.names())
        val group = state.days[0].items[1] as WeekItem.Module
        assertEquals(listOf("Max hangs", "Mobility", "Pull"), group.members.names())
        // Three exercises in it, the circuit's station included; the group itself is none of them.
        assertEquals(listOf("Max hangs", "Mobility", "Row"), group.exercises.map { it.name })
    }

    @Test
    fun `a member filed on another day shows there, not inside the group`() {
        val state = WeekUiState.build(
            weekStart = monday,
            today = monday,
            occurrences = listOf(
                occurrence("Max hangs", monday, moduleInstanceId = "Fingers", modulePosition = 0),
                // Logged on Tuesday: still a member on paper, but it happened on Tuesday.
                occurrence("Mobility", monday.plusDays(1), moduleInstanceId = "Fingers", modulePosition = 1),
            ),
            modules = listOf(module("Fingers", monday)),
        )
        val group = state.days[0].items.single() as WeekItem.Module
        assertEquals(listOf("Max hangs"), group.members.names())
        assertEquals(listOf("Mobility"), state.days[1].items.names())
    }

    @Test
    fun `a module is done only once every exercise in it is, and counts nothing itself`() {
        fun build(vararg states: OccurrenceState) = WeekUiState.build(
            weekStart = monday,
            today = monday,
            occurrences = states.mapIndexed { index, state ->
                occurrence(
                    name = "Exercise $index",
                    date = monday,
                    order = index,
                    moduleInstanceId = "Fingers",
                    modulePosition = index,
                ).copy(state = state)
            },
            modules = listOf(module("Fingers", monday)),
        ).days[0].items.filterIsInstance<WeekItem.Module>().single()

        assertTrue(!build(OccurrenceState.COMPLETED, OccurrenceState.PLANNED).completed)
        assertEquals(1, build(OccurrenceState.COMPLETED, OccurrenceState.PLANNED).recordedExercises)
        assertTrue(build(OccurrenceState.COMPLETED, OccurrenceState.COMPLETED).completed)
        // An empty group is never "done": there is nothing in it to have done.
        assertTrue(!WeekUiState.build(monday, monday, emptyList(), modules = listOf(module("E", monday)))
            .days[0].items.filterIsInstance<WeekItem.Module>().single().completed)
    }

    @Test
    fun `a member whose module is missing still shows as its own card`() {
        val state = WeekUiState.build(
            weekStart = monday,
            today = monday,
            occurrences = listOf(occurrence("Orphan", monday, moduleInstanceId = "gone")),
        )
        assertEquals(listOf("Orphan"), state.days[0].items.names())
    }

    @Test
    fun `undated items land in the unscheduled section and dated items on their day`() {
        val state = WeekUiState.build(
            weekStart = monday,
            today = monday.plusDays(2),
            occurrences = listOf(
                occurrence("Mobility", null),
                occurrence("Squat", monday.plusDays(1)),
            ),
        )
        assertEquals(listOf("Mobility"), state.unscheduled.names())
        assertEquals(7, state.days.size)
        assertEquals(listOf("Squat"), state.days[1].items.names())
        assertTrue(state.days[0].items.isEmpty())
    }

    @Test
    fun `exactly one day is marked today and only inside the shown week`() {
        val current = WeekUiState.build(monday, monday.plusDays(3), emptyList())
        assertEquals(1, current.days.count { it.isToday })
        assertTrue(current.isCurrentWeek)

        val other =
            WeekUiState.build(monday.plusWeeks(1), monday.plusDays(3), emptyList())
        assertEquals(0, other.days.count { it.isToday })
        assertTrue(!other.isCurrentWeek)
    }

    @Test
    fun `items keep their explicit order within a day`() {
        val state = WeekUiState.build(
            weekStart = monday,
            today = monday,
            occurrences = listOf(
                occurrence("Second", monday, order = 1),
                occurrence("First", monday, order = 0),
            ),
        )
        assertEquals(listOf("First", "Second"), state.days[0].items.names())
    }

    @Test
    fun `a circuit is one card, and its stations are folded into it`() {
        val state = WeekUiState.build(
            weekStart = monday,
            today = monday,
            occurrences = listOf(
                occurrence("Warm-up", monday, order = 0),
                occurrence("Hang", monday, order = 2, circuitInstanceId = "Pull", circuitPosition = 0),
                occurrence("Row", monday, order = 3, circuitInstanceId = "Pull", circuitPosition = 1),
            ),
            circuits = listOf(circuit("Pull", monday, order = 1)),
        )
        // Two cards, not three exercises: the stations live inside the circuit's card.
        assertEquals(listOf("Warm-up", "Pull"), state.days[0].items.names())
        val group = state.days[0].items[1] as WeekItem.Circuit
        assertEquals(listOf("Hang", "Row"), group.stations.map { it.name })
        assertTrue(!group.completed)
        assertEquals(0, group.recordedStations)
    }

    @Test
    fun `a circuit is done only once every station of it is`() {
        fun build(vararg states: OccurrenceState) = WeekUiState.build(
            weekStart = monday,
            today = monday,
            occurrences = states.mapIndexed { index, state ->
                occurrence(
                    name = "Station $index",
                    date = monday,
                    order = index,
                    circuitInstanceId = "Pull",
                    circuitPosition = index,
                ).copy(state = state)
            },
            circuits = listOf(circuit("Pull", monday)),
        ).days[0].items.filterIsInstance<WeekItem.Circuit>().single()

        assertTrue(!build(OccurrenceState.COMPLETED, OccurrenceState.PLANNED).completed)
        assertEquals(1, build(OccurrenceState.COMPLETED, OccurrenceState.PLANNED).recordedStations)
        assertTrue(build(OccurrenceState.COMPLETED, OccurrenceState.COMPLETED).completed)
    }

    @Test
    fun `a station whose circuit is missing still shows as its own card`() {
        val state = WeekUiState.build(
            weekStart = monday,
            today = monday,
            occurrences = listOf(
                occurrence("Orphan", monday, circuitInstanceId = "gone", circuitPosition = 0),
            ),
            circuits = emptyList(),
        )
        // Losing sight of planned work would be worse than an odd-looking list.
        assertEquals(listOf("Orphan"), state.days[0].items.names())
    }

    @Test
    fun `unilateral work is labelled per side`() {
        val summary = PrescriptionSummary.format(
            occurrence(
                name = "Dumbbell row",
                date = monday,
                unilateral = true,
                prescription = PrescriptionPayload(
                    sets = 4,
                    targetReps = 8,
                    restSeconds = 30,
                    measurement = Measurement(22.5, "kg", MeasurementMeaning.TOTAL_LOAD),
                ),
            )
        )
        assertEquals("4 × 8 per side · 22.5 kg · rest 30 s", summary)
    }

    @Test
    fun `an absent effort target is not rendered as zero`() {
        val summary = PrescriptionSummary.format(
            occurrence(
                name = "Couch stretch",
                date = monday,
                mode = ExerciseMode.DURATION,
                prescription = PrescriptionPayload(sets = 2, targetDurationSeconds = 90),
            )
        )
        assertEquals("2 × 1:30", summary)
    }

    @Test
    fun `a duration only activity shows no set count`() {
        val summary = PrescriptionSummary.format(
            occurrence(
                name = "Mobility flow",
                date = null,
                mode = ExerciseMode.ACTIVITY,
                prescription = PrescriptionPayload(sets = 1, targetDurationSeconds = 600),
            )
        )
        assertEquals("10 m", summary)
    }

    @Test
    fun `added load is never shown as plain load`() {
        val summary = PrescriptionSummary.format(
            occurrence(
                name = "Max hangs",
                date = monday,
                mode = ExerciseMode.DURATION,
                prescription = PrescriptionPayload(
                    sets = 5,
                    targetDurationSeconds = 10,
                    measurement = Measurement(12.5, "kg", MeasurementMeaning.ADDED_LOAD),
                ),
            )
        )
        assertEquals("5 × 10 s · +12.5 kg", summary)
    }
}
