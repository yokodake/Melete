package com.yokodake.melete.ui

import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.model.ActualSetJson
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.logger.SetDraft
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetDraftTest {

    @Test
    fun `empty fields become absent values rather than zeroes`() {
        val payload = SetDraft(reps = "8").toPayload("kg", MeasurementMeaning.TOTAL_LOAD)
        assertEquals(8, payload.reps)
        assertNull(payload.measurement)
        assertNull(payload.rpe)
        assertNull(payload.rir)
        assertNull(payload.durationSeconds)
    }

    @Test
    fun `an exercise without a unit records no measurement even if a number was typed`() {
        val payload = SetDraft(durationSeconds = "90", measurement = "12").toPayload(null, null)
        assertEquals(90, payload.durationSeconds)
        assertNull(payload.measurement)
    }

    @Test
    fun `the meaning of a load travels with the recorded set`() {
        val payload = SetDraft(durationSeconds = "10", measurement = "12.5")
            .toPayload("kg", MeasurementMeaning.ADDED_LOAD)
        assertEquals(
            Measurement(12.5, "kg", MeasurementMeaning.ADDED_LOAD),
            payload.measurement,
        )
    }

    @Test
    fun `a draft with nothing entered cannot be confirmed`() {
        assertTrue(SetDraft().toPayload("kg", MeasurementMeaning.TOTAL_LOAD).isEmpty)
        assertFalse(SetDraft(reps = "1").toPayload("kg", MeasurementMeaning.TOTAL_LOAD).isEmpty)
    }

    @Test
    fun `a draft survives being stored and restored`() {
        val draft = SetDraft(
            reps = "8",
            measurement = "22.5",
            rpe = "8",
            side = BodySide.RIGHT,
            editingSetId = "set-1",
            showEffortFields = true,
        )
        val restored = Json.decodeFromString<SetDraft>(Json.encodeToString(draft))
        assertEquals(draft, restored)
    }

    @Test
    fun `an actual set payload round trips`() {
        val payload = ActualSetPayload(
            reps = 6,
            measurement = Measurement(80.0, "kg", MeasurementMeaning.TOTAL_LOAD),
            rir = 1,
        )
        assertEquals(payload, ActualSetJson.decode(ActualSetJson.encode(payload)))
    }
}
