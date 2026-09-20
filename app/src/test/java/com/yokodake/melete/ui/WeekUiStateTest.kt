package com.yokodake.melete.ui

import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.ui.week.PrescriptionSummary
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
    ) = PlannedOccurrence(
        id = "$name-$date-$order",
        exerciseId = name,
        name = name,
        mode = mode,
        unilateral = unilateral,
        measurementUnit = "kg",
        measurementMeaning = MeasurementMeaning.TOTAL_LOAD,
        trainingDate = date,
        weekStart = monday,
        prescriptionId = null,
        prescription = prescription,
        prescriptionUnreadable = false,
        state = OccurrenceState.PLANNED,
        comment = null,
        orderIndex = order,
        isSampleData = false,
    )

    @Test
    fun `undated items land in the unscheduled section and dated items on their day`() {
        val state = WeekUiState.build(
            weekStart = monday,
            today = monday.plusDays(2),
            occurrences = listOf(
                occurrence("Mobility", null),
                occurrence("Squat", monday.plusDays(1)),
            ),
            sampleDataPresent = false,
        )
        assertEquals(listOf("Mobility"), state.unscheduled.map { it.name })
        assertEquals(7, state.days.size)
        assertEquals(listOf("Squat"), state.days[1].items.map { it.name })
        assertTrue(state.days[0].items.isEmpty())
    }

    @Test
    fun `exactly one day is marked today and only inside the shown week`() {
        val current = WeekUiState.build(monday, monday.plusDays(3), emptyList(), false)
        assertEquals(1, current.days.count { it.isToday })
        assertTrue(current.isCurrentWeek)

        val other = WeekUiState.build(monday.plusWeeks(1), monday.plusDays(3), emptyList(), false)
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
            sampleDataPresent = false,
        )
        assertEquals(listOf("First", "Second"), state.days[0].items.map { it.name })
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
        assertEquals("10 min", summary)
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
