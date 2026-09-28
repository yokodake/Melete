package com.yokodake.melete.ui

import com.yokodake.melete.data.OccurrenceDetail
import com.yokodake.melete.data.PerformedSet
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.ui.history.ExerciseHistory
import com.yokodake.melete.ui.history.HistoryMark
import com.yokodake.melete.ui.history.HistoryMeasure
import com.yokodake.melete.ui.history.Records
import com.yokodake.melete.ui.week.PrescriptionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExerciseHistoryTest {

    private val sep = { day: Int -> LocalDate.of(2026, 9, day) }

    private fun occurrence(
        id: String,
        date: LocalDate?,
        state: OccurrenceState = OccurrenceState.COMPLETED,
        variationId: String? = null,
        variationTag: String? = null,
        unilateral: Boolean = false,
        mode: ExerciseMode = ExerciseMode.REPETITIONS,
        duration: Int? = null,
        category: ExerciseCategory? = ExerciseCategory.STRENGTH_CONDITIONING,
        order: Int = 0,
    ) = PlannedOccurrence(
        id = id,
        exerciseId = "ex",
        name = "Weighted pull-up",
        mode = mode,
        unilateral = unilateral,
        measurementUnit = "kg",
        measurementMeaning = MeasurementMeaning.ADDED_LOAD,
        category = category,
        trainingDate = date,
        weekStart = sep(21),
        prescription = null,
        prescriptionUnreadable = false,
        state = state,
        comment = null,
        orderIndex = order,
        variationId = variationId,
        variationTag = variationTag,
        loggedDurationSeconds = duration,
    )

    private fun set(
        occurrenceId: String,
        date: LocalDate,
        index: Int,
        reps: Int? = 5,
        load: Double? = 10.0,
        side: BodySide? = null,
        unit: String = "kg",
        meaning: MeasurementMeaning = MeasurementMeaning.ADDED_LOAD,
    ) = PerformedSet(
        id = "$occurrenceId-$index-$side",
        occurrenceId = occurrenceId,
        exerciseId = "ex",
        trainingDate = date,
        orderIndex = index,
        side = side,
        payload = ActualSetPayload(reps = reps, measurement = load?.let { Measurement(it, unit, meaning) }),
        recordedAtEpochMs = 0L,
    )

    private fun build(
        details: List<OccurrenceDetail>,
        mode: ExerciseMode = ExerciseMode.REPETITIONS,
        plan: String? = null,
        target: Int? = null,
        from: LocalDate? = null,
        to: LocalDate? = null,
    ) = ExerciseHistory.build(details, mode, "kg", MeasurementMeaning.ADDED_LOAD, plan, target, from, to)

    @Test
    fun `only records count, filed under the date their sets were logged`() {
        val history = build(
            listOf(
                OccurrenceDetail(occurrence("a", sep(21)), listOf(set("a", sep(21), 0, load = 10.0))),
                // Planned and never done: not history.
                OccurrenceDetail(occurrence("b", sep(28), state = OccurrenceState.PLANNED), emptyList()),
                // Skipped but with a set: a record, like the last-logged line counts it.
                OccurrenceDetail(occurrence("c", sep(24), state = OccurrenceState.SKIPPED), listOf(set("c", sep(24), 0, load = 12.5))),
                // Completed with nothing written down: a record without a point.
                OccurrenceDetail(occurrence("d", sep(26)), emptyList()),
            )
        )
        assertEquals(listOf("d", "c", "a"), history.records.map { it.occurrence.id })
        assertEquals(HistoryMeasure.LOAD, history.measure)
        assertEquals(listOf(10.0, 12.5), history.series.single().points.map { it.value })
        assertNull(history.records.first().summary)
    }

    @Test
    fun `the heaviest set of each workout is its point, zero and assistance included`() {
        val history = build(
            listOf(
                OccurrenceDetail(occurrence("a", sep(14)), listOf(set("a", sep(14), 0, load = -15.0), set("a", sep(14), 1, load = -10.0))),
                OccurrenceDetail(occurrence("b", sep(21)), listOf(set("b", sep(21), 0, load = 0.0))),
                OccurrenceDetail(occurrence("c", sep(28)), listOf(set("c", sep(28), 0, load = -5.0))),
            )
        )
        assertEquals(listOf(-10.0, 0.0, -5.0), history.series.single().points.map { it.value })
        assertEquals(HistoryMark(-5.0, sep(28)), history.latest[null])
        assertEquals(HistoryMark(0.0, sep(21)), history.best[null])
        assertEquals("−5 kg", history.format(-5.0))
        assertEquals("+0 kg", history.format(0.0))
    }

    @Test
    fun `the best keeps the first date it was reached`() {
        val history = build(
            listOf(
                OccurrenceDetail(occurrence("a", sep(14)), listOf(set("a", sep(14), 0, load = 20.0))),
                OccurrenceDetail(occurrence("b", sep(21)), listOf(set("b", sep(21), 0, load = 20.0))),
            )
        )
        assertEquals(HistoryMark(20.0, sep(14)), history.best[null])
        assertEquals(HistoryMark(20.0, sep(21)), history.latest[null])
    }

    @Test
    fun `unilateral work draws a line per side`() {
        val history = build(
            listOf(
                OccurrenceDetail(
                    occurrence("a", sep(21), unilateral = true),
                    listOf(set("a", sep(21), 0, load = 25.0, side = BodySide.LEFT), set("a", sep(21), 0, load = 23.0, side = BodySide.RIGHT)),
                ),
                OccurrenceDetail(
                    occurrence("b", sep(28), unilateral = true),
                    listOf(set("b", sep(28), 0, load = 26.0, side = BodySide.LEFT), set("b", sep(28), 0, load = 24.0, side = BodySide.RIGHT)),
                ),
            )
        )
        assertEquals(listOf(BodySide.LEFT, BodySide.RIGHT), history.series.map { it.side })
        assertEquals(listOf(23.0, 24.0), history.series[1].points.map { it.value })
        assertEquals(HistoryMark(26.0, sep(28)), history.best[BodySide.LEFT])
    }

    @Test
    fun `loads in another unit or meaning stay off the graph`() {
        val history = build(
            listOf(
                OccurrenceDetail(occurrence("a", sep(14)), listOf(set("a", sep(14), 0, load = 50.0, unit = "lb"))),
                OccurrenceDetail(occurrence("b", sep(21)), listOf(set("b", sep(21), 0, load = 60.0, meaning = MeasurementMeaning.TOTAL_LOAD))),
                OccurrenceDetail(occurrence("c", sep(28)), listOf(set("c", sep(28), 0, load = 10.0))),
            )
        )
        assertEquals(listOf(10.0), history.series.single().points.map { it.value })
        // Still results, listed with what they recorded.
        assertEquals(3, history.records.size)
    }

    @Test
    fun `without loads, reps are plotted, and an activity plots its duration`() {
        val reps = build(listOf(OccurrenceDetail(occurrence("a", sep(21)), listOf(set("a", sep(21), 0, reps = 12, load = null)))))
        assertEquals(HistoryMeasure.REPS, reps.measure)
        assertEquals("12 reps", reps.format(12.0))

        val activity = build(
            listOf(
                OccurrenceDetail(occurrence("a", sep(21), mode = ExerciseMode.ACTIVITY, duration = 5400), emptyList()),
                OccurrenceDetail(occurrence("b", sep(22), mode = ExerciseMode.ACTIVITY), emptyList()),
            ),
            mode = ExerciseMode.ACTIVITY,
        )
        assertEquals(HistoryMeasure.DURATION, activity.measure)
        assertEquals(listOf(5400.0), activity.series.single().points.map { it.value })
        assertEquals(2, activity.records.size)
    }

    @Test
    fun `plans and rep counts narrow the history, and a stale choice is dropped`() {
        val details = listOf(
            OccurrenceDetail(occurrence("a", sep(14)), listOf(set("a", sep(14), 0, reps = 5, load = 20.0), set("a", sep(14), 1, reps = 3, load = 25.0))),
            OccurrenceDetail(occurrence("b", sep(21), variationId = "v-str", variationTag = "STR"), listOf(set("b", sep(21), 0, reps = 3, load = 27.5))),
        )
        val all = build(details)
        assertEquals(listOf("default", "v-str"), all.planOptions.map { it.key })
        assertEquals(listOf("Default", "STR"), all.planOptions.map { it.label })
        assertEquals(listOf(3, 5), all.targetOptions)

        val str = build(details, plan = "v-str")
        assertEquals(listOf("b"), str.records.map { it.occurrence.id })

        val fives = build(details, target = 5)
        assertEquals(listOf(20.0), fives.series.single().points.map { it.value })
        assertEquals(listOf("a"), fives.records.map { it.occurrence.id })

        val stale = build(details, plan = "v-gone", target = 8)
        assertNull(stale.plan)
        assertNull(stale.target)
        assertEquals(2, stale.records.size)
    }

    @Test
    fun `one plan offers no plan filter`() {
        val history = build(listOf(OccurrenceDetail(occurrence("a", sep(14)), listOf(set("a", sep(14), 0)))))
        assertTrue(history.planOptions.isEmpty())
        assertTrue(history.targetOptions.isEmpty())
    }

    @Test
    fun `a range keeps only the records inside it`() {
        val details = listOf(
            OccurrenceDetail(occurrence("a", sep(7)), listOf(set("a", sep(7), 0))),
            OccurrenceDetail(occurrence("b", sep(21)), listOf(set("b", sep(21), 0))),
        )
        assertEquals(listOf("b"), build(details, from = sep(14), to = sep(28)).records.map { it.occurrence.id })
        val empty = build(details, from = sep(8), to = sep(13))
        assertTrue(empty.records.isEmpty())
        assertFalse(empty.hasGraph)
    }

    @Test
    fun `records by day, newest first, narrowed to a category or to none`() {
        val details = listOf(
            OccurrenceDetail(occurrence("a", sep(21), order = 1), emptyList()),
            OccurrenceDetail(occurrence("b", sep(21), order = 0, category = null), emptyList()),
            OccurrenceDetail(occurrence("c", sep(23), category = ExerciseCategory.FLEXIBILITY), emptyList()),
        )
        val all = Records.days(details, category = null, uncategorised = false)
        assertEquals(listOf(sep(23), sep(21)), all.map { it.date })
        assertEquals(listOf("b", "a"), all[1].lines.map { it.occurrenceId })
        assertEquals(listOf("c"), Records.days(details, ExerciseCategory.FLEXIBILITY, false).flatMap { it.lines }.map { it.occurrenceId })
        assertEquals(listOf("b"), Records.days(details, null, uncategorised = true).flatMap { it.lines }.map { it.occurrenceId })
    }

    @Test
    fun `attempts read as set rest over rep rest, and minutes as m`() {
        val plan = PrescriptionPayload(sets = 3, targetReps = 3, restSeconds = 300, restSecondsBetweenReps = 180,
            effort = com.yokodake.melete.data.model.EffortLevel.HARD)
        assertEquals("3 × 3 · rest 5m/3m · hard", PrescriptionSummary.formatPlan(plan, ExerciseMode.REPETITIONS, false))
        assertEquals("3 × 3 · rest –/1m", PrescriptionSummary.formatPlan(
            PrescriptionPayload(sets = 3, targetReps = 3, restSecondsBetweenReps = 60), ExerciseMode.REPETITIONS, false))
        assertEquals("3 × 3 · rest 2 m", PrescriptionSummary.formatPlan(
            PrescriptionPayload(sets = 3, targetReps = 3, restSeconds = 120), ExerciseMode.REPETITIONS, false))
        assertEquals("3 reps · 1:30 between reps", PrescriptionSummary.formatStation(
            PrescriptionPayload(sets = 1, targetReps = 3, restSecondsBetweenReps = 90), ExerciseMode.REPETITIONS, false))
    }
}
