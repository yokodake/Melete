package com.yokodake.melete.ui

import com.yokodake.melete.core.OneOffActivity
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.data.model.RepeaterPrescription
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.ui.components.PrescriptionFormState
import com.yokodake.melete.ui.logger.LoggerUiState
import com.yokodake.melete.ui.logger.SetTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What the logger will actually save as a duration, and where that number came from.
 *
 * Provenance is the part worth pinning down: an estimate the user never touched must not come back
 * later looking like something they said, and a number they typed must survive every later change
 * to the defaults and the formulas.
 */
class DurationLoggingTest {

    private val monday = LocalDate.of(2026, 9, 21)

    private fun occurrence(
        mode: ExerciseMode = ExerciseMode.DURATION,
        unilateral: Boolean = false,
        prescription: PrescriptionPayload? = PrescriptionPayload(
            sets = 3,
            targetDurationSeconds = 10,
            restSeconds = 60,
        ),
        loggedDurationSeconds: Int? = null,
        loggedDurationManual: Boolean = false,
    ) = PlannedOccurrence(
        id = "occurrence",
        exerciseId = "exercise",
        name = "Max hangs",
        mode = mode,
        unilateral = unilateral,
        measurementUnit = "kg",
        measurementMeaning = MeasurementMeaning.TOTAL_LOAD,
        category = null,
        trainingDate = monday,
        weekStart = monday,
        prescriptionId = null,
        prescription = prescription,
        prescriptionUnreadable = false,
        state = OccurrenceState.PLANNED,
        comment = null,
        orderIndex = 0,
        isSampleData = false,
        loggedDurationSeconds = loggedDurationSeconds,
        loggedDurationManual = loggedDurationManual,
    )

    @Test
    fun `an untouched field saves the estimate, and says it was inferred`() {
        val state = LoggerUiState(occurrence = occurrence(), table = SetTable())
        // 3 x 10s + 2 x 60s, plus the preparation the sequence generates.
        assertEquals(155 to false, state.resolvedDuration)
        assertEquals("3", state.inferredDurationMinutes)
    }

    @Test
    fun `typing a number makes it the user's own`() {
        val state = LoggerUiState(
            occurrence = occurrence(),
            table = SetTable(durationMinutes = "25"),
        )
        assertEquals(1_500 to true, state.resolvedDuration)
    }

    @Test
    fun `clearing the field hands the question back to the estimate`() {
        val typed = LoggerUiState(occurrence = occurrence(), table = SetTable(durationMinutes = "25"))
        val cleared = typed.copy(table = typed.table.copy(durationMinutes = ""))
        assertEquals(155 to false, cleared.resolvedDuration)
    }

    @Test
    fun `an explicit planned duration is inherited as inferred until the log overrides it`() {
        val planned = occurrence(
            prescription = PrescriptionPayload(
                sets = 3,
                targetDurationSeconds = 10,
                restSeconds = 60,
                plannedDurationSeconds = 1_800,
            )
        )
        // The plan says half an hour, so that is what the log will say — but the user has not
        // said it about *this* session, so it is still inferred.
        assertEquals(
            1_800 to false,
            LoggerUiState(occurrence = planned, table = SetTable()).resolvedDuration,
        )
        assertEquals(
            600 to true,
            LoggerUiState(
                occurrence = planned,
                table = SetTable(durationMinutes = "10"),
            ).resolvedDuration,
        )
    }

    @Test
    fun `no estimate saves nothing rather than a fabricated zero`() {
        val unknowable = occurrence(prescription = PrescriptionPayload(sets = 3))
        val state = LoggerUiState(occurrence = unknowable, table = SetTable())
        assertEquals(null to false, state.resolvedDuration)
        assertNull(state.inferredDurationMinutes)
    }

    @Test
    fun `an activity is recognised and needs no set table`() {
        val activity = LoggerUiState(
            occurrence = occurrence(
                mode = ExerciseMode.ACTIVITY,
                prescription = PrescriptionPayload(sets = 1, targetDurationSeconds = 5_400),
            ),
            table = SetTable(),
        )
        assertTrue(activity.isActivity)
        assertEquals(5_400 to false, activity.resolvedDuration)
        assertFalse(LoggerUiState(occurrence = occurrence(), table = SetTable()).isActivity)
    }

    @Test
    fun `a unilateral plan is estimated for both sides`() {
        val both = LoggerUiState(occurrence = occurrence(unilateral = true), table = SetTable())
        val one = LoggerUiState(occurrence = occurrence(), table = SetTable())
        assertTrue(both.resolvedDuration.first!! > one.resolvedDuration.first!!)
    }

    // --------------------------------------------------- the prescription form

    @Test
    fun `the form round trips the fields phase 5A and the timer added`() {
        val payload = PrescriptionPayload(
            sets = 3,
            restSeconds = 180,
            plannedDurationSeconds = 1_500,
            sideSwitchSeconds = 10,
            repeater = RepeaterPrescription(
                repsPerSet = 6,
                workSecondsPerRep = 7,
                restSecondsBetweenReps = 3,
            ),
        )
        val form = PrescriptionFormState.from(payload)
        assertEquals("25", form.plannedDurationMinutes)
        assertEquals("10", form.sideSwitchSeconds)
        assertTrue(form.repeaterEnabled)
        assertEquals(payload, form.toPayload())
    }

    @Test
    fun `an empty duration field stays absent rather than becoming zero`() {
        val form = PrescriptionFormState(sets = "3", targetDurationSeconds = "10")
        assertNull(form.toPayload().plannedDurationSeconds)
        assertNull(form.toPayload().sideSwitchSeconds)
        assertNull(form.toPayload().repeater)
    }

    @Test
    fun `a zero side switch is a real answer and is not the default`() {
        val form = PrescriptionFormState(sets = "3", sideSwitchSeconds = "0")
        assertEquals(0, form.toPayload().sideSwitchSeconds)
        assertEquals(
            TimerProgram.DEFAULT_SIDE_SWITCH_SECONDS,
            PrescriptionFormState(sets = "3").toPayload().sideSwitchSeconds
                ?: TimerProgram.DEFAULT_SIDE_SWITCH_SECONDS,
        )
    }

    @Test
    fun `a half-typed repeater describes no repeater at all`() {
        val form = PrescriptionFormState(repeaterEnabled = true, repeaterReps = "6")
        assertNull(form.toRepeater())
        assertNull(form.copy(repeaterWorkSeconds = "0").toRepeater())
    }

    // ------------------------------------------------------- one-off identity

    @Test
    fun `one-off identity ignores case and spacing but not a real difference`() {
        assertEquals(
            OneOffActivity.exerciseIdFor("Outdoor bouldering"),
            OneOffActivity.exerciseIdFor("  outdoor   BOULDERING "),
        )
        assertTrue(
            OneOffActivity.exerciseIdFor("Outdoor bouldering") !=
                OneOffActivity.exerciseIdFor("Outdoor bouldering (Font)")
        )
        assertTrue(OneOffActivity.isOneOffId(OneOffActivity.exerciseIdFor("Trail run")))
        assertFalse(OneOffActivity.isOneOffId("6f1b0c3e-0000-4000-8000-000000000000"))
    }
}
