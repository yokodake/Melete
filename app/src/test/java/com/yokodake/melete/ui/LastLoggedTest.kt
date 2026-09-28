package com.yokodake.melete.ui

import com.yokodake.melete.data.PerformedSet
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.backup.recoveryCopyLabel
import com.yokodake.melete.ui.detail.LastLoggedPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class LastLoggedTest {

    private val today = LocalDate.of(2026, 9, 28)

    private fun occurrence(
        id: String,
        date: LocalDate?,
        state: OccurrenceState = OccurrenceState.COMPLETED,
        variationId: String? = null,
        variationTag: String? = null,
        unilateral: Boolean = false,
        mode: ExerciseMode = ExerciseMode.REPETITIONS,
        order: Int = 0,
        duration: Int? = null,
        effort: EffortLevel? = null,
    ) = PlannedOccurrence(
        id = id,
        exerciseId = "ex",
        name = "Pull-up",
        mode = mode,
        unilateral = unilateral,
        measurementUnit = "kg",
        measurementMeaning = MeasurementMeaning.TOTAL_LOAD,
        category = null,
        trainingDate = date,
        weekStart = today,
        prescription = null,
        prescriptionUnreadable = false,
        state = state,
        comment = null,
        orderIndex = order,
        variationId = variationId,
        variationTag = variationTag,
        loggedDurationSeconds = duration,
        loggedEffort = effort,
    )

    private fun set(
        occurrenceId: String,
        date: LocalDate,
        index: Int,
        reps: Int? = 8,
        load: Double? = 20.0,
        side: BodySide? = null,
        effort: EffortLevel? = null,
        meaning: MeasurementMeaning = MeasurementMeaning.TOTAL_LOAD,
        seconds: Int? = null,
    ) = PerformedSet(
        id = "$occurrenceId-$index-$side",
        occurrenceId = occurrenceId,
        exerciseId = "ex",
        trainingDate = date,
        orderIndex = index,
        side = side,
        payload = ActualSetPayload(
            reps = reps,
            durationSeconds = seconds,
            measurement = load?.let { Measurement(it, "kg", meaning) },
            effort = effort,
        ),
        // Recorded in the reverse order of training, so ordering by it would be wrong.
        recordedAtEpochMs = 1_000_000L - date.toEpochDay(),
    )

    private val sep24 = LocalDate.of(2026, 9, 24)
    private val sep20 = LocalDate.of(2026, 9, 20)

    // ------------------------------------------------------------ choosing

    @Test
    fun `the most recent training date wins, and the viewed log is never its own previous`() {
        val current = occurrence("now", today)
        val history = listOf(current, occurrence("old", sep20), occurrence("recent", sep24))
        val sets = listOf(
            set("now", today, 0, load = 99.0),
            set("old", sep20, 0, load = 15.0),
            set("recent", sep24, 0, load = 20.0),
        )
        val last = LastLoggedPicker.pick(current, history, sets, today)!!
        assertEquals(sep24, last.date)
        assertEquals("1 × 8 · 20 kg", last.summary)
        assertFalse(last.otherPlan)
    }

    @Test
    fun `plans alone are not results, and later training is not earlier`() {
        val current = occurrence("now", sep24, state = OccurrenceState.PLANNED)
        val history = listOf(
            current,
            occurrence("planned", sep20, state = OccurrenceState.PLANNED),
            occurrence("later", today),
        )
        val sets = listOf(set("later", today, 0))
        assertNull(LastLoggedPicker.pick(current, history, sets, today))
    }

    @Test
    fun `the same variation is preferred, and falling back to another plan says so`() {
        val current = occurrence("now", today, state = OccurrenceState.PLANNED, variationId = "pwr", variationTag = "PWR")
        val history = listOf(
            current,
            occurrence("pwr", sep20, variationId = "pwr", variationTag = "PWR"),
            occurrence("default", sep24),
        )
        val sets = listOf(set("pwr", sep20, 0, load = 30.0), set("default", sep24, 0))
        val same = LastLoggedPicker.pick(current, history, sets, today)!!
        assertEquals(sep20, same.date)
        assertFalse(same.otherPlan)

        val other = occurrence("now", today, state = OccurrenceState.PLANNED, variationId = "end", variationTag = "END")
        val fallback = LastLoggedPicker.pick(other, history, sets, today)!!
        assertEquals(sep24, fallback.date)
        assertTrue(fallback.otherPlan)
        assertNull("the default plan has no tag", fallback.variationTag)
    }

    // ------------------------------------------------------------ summarising

    @Test
    fun `uniform sets read as count by reps, with the effort when it is one`() {
        val occ = occurrence("o", sep24)
        val sets = (0..2).map { set("o", sep24, it, effort = EffortLevel.entries.last()) }
        assertEquals(
            "3 × 8 · 20 kg · ${EffortLevel.entries.last().label}",
            LastLoggedPicker.summarise(occ, sets),
        )
    }

    @Test
    fun `different loads are listed set by set, with the unit once`() {
        val occ = occurrence("o", sep24)
        val sets = listOf(
            set("o", sep24, 0), set("o", sep24, 1), set("o", sep24, 2, reps = 6, load = 22.0),
        )
        assertEquals("8 × 20 · 8 × 20 · 6 × 22 kg", LastLoggedPicker.summarise(occ, sets))
    }

    @Test
    fun `left and right read as one row each, and differing sides are spelled out`() {
        val occ = occurrence("o", sep24, unilateral = true)
        val even = (0..1).flatMap {
            listOf(set("o", sep24, it, side = BodySide.LEFT), set("o", sep24, it, side = BodySide.RIGHT))
        }
        assertEquals("2 × 8 · 20 kg", LastLoggedPicker.summarise(occ, even))
        val uneven = (0..1).flatMap {
            listOf(
                set("o", sep24, it, side = BodySide.LEFT, load = 20.0),
                set("o", sep24, it, side = BodySide.RIGHT, load = 22.5),
            )
        }
        assertEquals("2 × 8 · L 20 / R 22.5 kg", LastLoggedPicker.summarise(occ, uneven))
    }

    @Test
    fun `zero and negative added loads are kept as they were recorded`() {
        val occ = occurrence("o", sep24)
        val bodyweight = (0..1).map { set("o", sep24, it, load = 0.0, meaning = MeasurementMeaning.ADDED_LOAD) }
        assertEquals("2 × 8 · +0 kg", LastLoggedPicker.summarise(occ, bodyweight))
        val assisted = listOf(
            set("o", sep24, 0, load = -10.0, meaning = MeasurementMeaning.ADDED_LOAD),
            set("o", sep24, 1, load = 0.0, meaning = MeasurementMeaning.ADDED_LOAD),
        )
        assertEquals("8 × −10 · 8 × +0 kg", LastLoggedPicker.summarise(occ, assisted))
    }

    @Test
    fun `timed work invents no reps, and an activity says how long`() {
        val occ = occurrence("o", sep24, mode = ExerciseMode.DURATION)
        val hangs = (0..4).map { set("o", sep24, it, reps = null, load = 10.0) }
        assertEquals("5 sets · 10 kg", LastLoggedPicker.summarise(occ, hangs))

        val activity = occurrence("a", sep24, mode = ExerciseMode.ACTIVITY, duration = 5400)
        assertEquals(
            com.yokodake.melete.ui.week.PrescriptionSummary.duration(5400),
            LastLoggedPicker.summarise(activity, emptyList()),
        )
        assertNull(LastLoggedPicker.summarise(occurrence("b", sep24, mode = ExerciseMode.ACTIVITY), emptyList()))
    }

    // ------------------------------------------------------------ recovery copies

    @Test
    fun `recovery copies read as dates, with a year only when it is needed`() {
        Locale.setDefault(Locale.US)
        assertEquals("27 Sep · 19:33", recoveryCopyLabel("melete-before-restore-20260927-193305.json", today))
        assertEquals(
            "3 Jan 2025 · 08:05",
            recoveryCopyLabel("melete-before-restore-20250103-080500.json", today),
        )
        assertEquals("something-else", recoveryCopyLabel("something-else.json", today))
    }
}
