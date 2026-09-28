package com.yokodake.melete.ui

import com.yokodake.melete.data.CompletedExercise
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.ui.dashboard.DashboardMetric
import com.yokodake.melete.ui.dashboard.DashboardRange
import com.yokodake.melete.ui.dashboard.DashboardStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardStatsTest {

    /** A Monday. */
    private val today = LocalDate.of(2026, 9, 28)

    private fun done(
        id: String,
        date: LocalDate,
        seconds: Int? = null,
        manual: Boolean = true,
        category: ExerciseCategory? = ExerciseCategory.STRENGTH_CONDITIONING,
        name: String = id,
    ) = CompletedExercise(id, name, category, date, seconds, manual)

    @Test
    fun `ranges end today and include the current partial week or month`() {
        assertEquals(LocalDate.of(2026, 9, 7) to today, DashboardStats.span(DashboardRange.FOUR_WEEKS, today, null))
        assertEquals(LocalDate.of(2026, 7, 13), DashboardStats.span(DashboardRange.TWELVE_WEEKS, today, null).first)
        assertEquals(LocalDate.of(2026, 4, 1), DashboardStats.span(DashboardRange.SIX_MONTHS, today, null).first)
        assertEquals(LocalDate.of(2025, 10, 1), DashboardStats.span(DashboardRange.TWELVE_MONTHS, today, null).first)
        val first = LocalDate.of(2023, 11, 15)
        assertEquals(first, DashboardStats.span(DashboardRange.ALL_TIME, today, first).first)
        assertEquals(today, DashboardStats.span(DashboardRange.ALL_TIME, today, null).first)
    }

    @Test
    fun `paging back walks whole ranges with no gap or overlap`() {
        val first = LocalDate.of(2023, 1, 2)
        // 4 weeks: 7–28 Sep now; one back is 10 Aug – 6 Sep.
        assertEquals(
            LocalDate.of(2026, 8, 10) to LocalDate.of(2026, 9, 6),
            DashboardStats.span(DashboardRange.FOUR_WEEKS, today, first, back = 1),
        )
        // 12 months: Oct 2025 – today now; one back is the whole of Oct 2024 – Sep 2025.
        assertEquals(
            LocalDate.of(2024, 10, 1) to LocalDate.of(2025, 9, 30),
            DashboardStats.span(DashboardRange.TWELVE_MONTHS, today, first, back = 1),
        )
        // 6 months back is still weekly, 12 months back still monthly.
        val (sixFrom, sixTo) = DashboardStats.span(DashboardRange.SIX_MONTHS, today, first, back = 1)
        assertFalse(DashboardStats.monthly(sixFrom, sixTo))
        assertTrue(DashboardStats.monthly(LocalDate.of(2024, 10, 1), LocalDate.of(2025, 9, 30)))
        // All time does not page.
        assertEquals(first to today, DashboardStats.span(DashboardRange.ALL_TIME, today, first, back = 3))
    }

    @Test
    fun `earlier stops once the range before would hold nothing`() {
        val first = LocalDate.of(2025, 11, 20)
        // 12 months back from now covers Oct 2024 – Sep 2025: nothing before Nov 2025 exists.
        assertFalse(DashboardStats.hasEarlier(DashboardRange.TWELVE_MONTHS, today, first, back = 0))
        // The 4-week range holding the first record is still reachable.
        assertTrue(DashboardStats.hasEarlier(DashboardRange.FOUR_WEEKS, today, first, back = 0))
        assertFalse(DashboardStats.hasEarlier(DashboardRange.ALL_TIME, today, first, back = 0))
        assertFalse(DashboardStats.hasEarlier(DashboardRange.FOUR_WEEKS, today, null, back = 0))
    }

    @Test
    fun `weekly bars through six months, monthly beyond, all time by its real span`() {
        fun monthly(range: DashboardRange, first: LocalDate? = null): Boolean {
            val (from, to) = DashboardStats.span(range, today, first)
            return DashboardStats.build(emptyList(), from, to, DashboardMetric.HOURS).bars.first().monthly
        }
        assertFalse(monthly(DashboardRange.FOUR_WEEKS))
        assertFalse(monthly(DashboardRange.SIX_MONTHS))
        assertTrue(monthly(DashboardRange.TWELVE_MONTHS))
        assertFalse(monthly(DashboardRange.ALL_TIME, LocalDate.of(2026, 6, 1)))
        assertTrue(monthly(DashboardRange.ALL_TIME, LocalDate.of(2025, 1, 1)))

        val (from, to) = DashboardStats.span(DashboardRange.FOUR_WEEKS, today, null)
        val bars = DashboardStats.build(emptyList(), from, to, DashboardMetric.HOURS).bars
        // Empty weeks are bars too, and the current week is the last one.
        assertEquals(4, bars.size)
        assertEquals(today, bars.last().start)
    }

    @Test
    fun `every completed exercise counts once, and a missing duration adds no time`() {
        val (from, to) = DashboardStats.span(DashboardRange.FOUR_WEEKS, today, null)
        val stats = DashboardStats.build(
            listOf(
                done("squat", today, 3600),
                // Circuit stations are exercises in their own right, each with its share.
                done("pull-up", today.minusDays(2), 300, manual = false),
                done("plank", today.minusDays(2), 300, manual = false),
                // Completed with no duration recorded: counted, but no invented time.
                done("row", today.minusDays(8), null),
                // Outside the range.
                done("old", today.minusWeeks(10), 7200),
            ),
            from, to, DashboardMetric.HOURS,
        )
        assertEquals(4, stats.totalCount)
        assertEquals(4200, stats.totalSeconds)
        assertEquals("1.2 h", DashboardStats.hours(stats.totalSeconds))
        // Weeks of 7, 14, 21 and 28 September.
        assertEquals(1, stats.bars[3].totalCount)
        assertEquals(2, stats.bars[2].totalCount)
        assertEquals(600, stats.bars[2].totalSeconds)
        assertEquals(1, stats.bars[1].totalCount)
        assertEquals(0, stats.bars[1].totalSeconds)
    }

    @Test
    fun `shares and exercise order follow the chosen metric`() {
        val (from, to) = DashboardStats.span(DashboardRange.FOUR_WEEKS, today, null)
        val data = listOf(
            done("board", today, 5400, category = ExerciseCategory.BOARD_CLIMBING),
            done("hang", today, 600, category = ExerciseCategory.FINGER_TRAINING),
            done("hang", today.minusDays(1), 600, category = ExerciseCategory.FINGER_TRAINING),
            done("hang", today.minusDays(2), 600, manual = false, category = ExerciseCategory.FINGER_TRAINING),
            done("stretch", today, null, category = null),
        )
        val byHours = DashboardStats.build(data, from, to, DashboardMetric.HOURS)
        assertEquals(ExerciseCategory.BOARD_CLIMBING, byHours.categories.first().category)
        assertEquals(0.75, byHours.categories.first().share, 1e-9)
        assertEquals(listOf("board", "hang", "stretch"), byHours.exercises.map { it.exerciseId })

        val byCount = DashboardStats.build(data, from, to, DashboardMetric.EXERCISES)
        assertEquals(ExerciseCategory.FINGER_TRAINING, byCount.categories.first().category)
        assertEquals(0.6, byCount.categories.first().share, 1e-9)
        assertEquals(listOf("hang", "board", "stretch"), byCount.exercises.map { it.exerciseId })
        // No category is a row of its own, not dropped.
        assertTrue(byCount.categories.any { it.category == null && it.count == 1 && it.seconds == 0 })
    }

    @Test
    fun `an exercise row says when some of its time was worked out`() {
        val (from, to) = DashboardStats.span(DashboardRange.FOUR_WEEKS, today, null)
        val stats = DashboardStats.build(
            listOf(
                done("hang", today, 600, manual = false),
                done("squat", today, 1800, manual = true),
                done("row", today, null, manual = false),
            ),
            from, to, DashboardMetric.HOURS,
        )
        val rows = stats.exercises.associateBy { it.exerciseId }
        assertTrue(rows.getValue("hang").includesInferred)
        assertFalse(rows.getValue("squat").includesInferred)
        // No time at all is not an estimate either.
        assertFalse(rows.getValue("row").includesInferred)
    }

    @Test
    fun `small segments get a minimum height from the largest, and the bar keeps its total`() {
        val heights = DashboardStats.segmentHeights(listOf(90f, 0f, 1f, 9f), barHeight = 100f, minimum = 6f)
        assertEquals(listOf(85f, 0f, 6f, 9f), heights)
        assertEquals(100f, heights.sum(), 1e-4f)
        // A bar too short to lend anything stays proportional.
        val tiny = DashboardStats.segmentHeights(listOf(5f, 5f), barHeight = 8f, minimum = 6f)
        assertEquals(listOf(4f, 4f), tiny)
        // Nothing to show is nothing.
        assertEquals(listOf(0f, 0f), DashboardStats.segmentHeights(listOf(0f, 0f), 100f, 6f))
    }

    @Test
    fun `hours read short`() {
        assertEquals("0 h", DashboardStats.hours(0))
        assertEquals("45 min", DashboardStats.hours(45 * 60))
        assertEquals("24 h", DashboardStats.hours(24 * 3600))
        assertEquals("24.5 h", DashboardStats.hours(24 * 3600 + 1800))
    }
}
