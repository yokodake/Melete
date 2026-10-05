package com.yokodake.melete.ui

import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.ui.week.DayMarker
import com.yokodake.melete.ui.week.MonthView
import com.yokodake.melete.ui.week.WeekItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthViewTest {

    private val day = LocalDate.of(2026, 10, 5)

    private fun single(
        id: String,
        category: ExerciseCategory?,
        state: OccurrenceState = OccurrenceState.PLANNED,
        sets: Int = 0,
    ) = WeekItem.Single(
        PlannedOccurrence(
            id = id, exerciseId = id, name = id, mode = ExerciseMode.REPETITIONS, unilateral = false,
            measurementUnit = null, measurementMeaning = null, category = category, trainingDate = day,
            weekStart = day, prescription = null, prescriptionUnreadable = false, state = state,
            comment = null, orderIndex = 0, loggedSets = sets,
        )
    )

    @Test
    fun `a month covers every week it touches, Monday first`() {
        // October 2026 starts on a Thursday and ends on a Saturday.
        val weeks = MonthView.weekStarts(YearMonth.of(2026, 10))
        assertEquals(LocalDate.of(2026, 9, 28), weeks.first())
        assertEquals(LocalDate.of(2026, 10, 26), weeks.last())
        assertEquals(5, weeks.size)
        // February 2027 starts on a Monday and ends on a Sunday: exactly four weeks.
        assertEquals(4, MonthView.weekStarts(YearMonth.of(2027, 2)).size)
    }

    @Test
    fun `logged only keeps what was done or has sets, plans go`() {
        val items = listOf(
            single("planned", ExerciseCategory.FLEXIBILITY),
            single("done", ExerciseCategory.BOARD_CLIMBING, OccurrenceState.COMPLETED),
            single("started", ExerciseCategory.FINGER_TRAINING, sets = 2),
        )
        assertEquals(3, MonthView.visible(items, loggedOnly = false).size)
        assertEquals(
            listOf("done", "started"),
            MonthView.visible(items, loggedOnly = true).map { (it as WeekItem.Single).occurrence.id },
        )
    }

    @Test
    fun `markers are one per category, filled when any of it was done, skipped work marks nothing`() {
        val markers = MonthView.markers(
            listOf(
                single("a", ExerciseCategory.STRENGTH_CONDITIONING),
                single("b", ExerciseCategory.STRENGTH_CONDITIONING, OccurrenceState.COMPLETED),
                single("c", ExerciseCategory.FLEXIBILITY),
                single("d", ExerciseCategory.BOARD_CLIMBING, OccurrenceState.SKIPPED),
                single("e", null),
            )
        )
        assertEquals(
            listOf(
                DayMarker(ExerciseCategory.STRENGTH_CONDITIONING, done = true),
                DayMarker(ExerciseCategory.FLEXIBILITY, done = false),
                DayMarker(null, done = false),
            ),
            markers,
        )
    }
}
