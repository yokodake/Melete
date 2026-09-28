package com.yokodake.melete.data

import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionJson
import com.yokodake.melete.data.model.PrescriptionPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrescriptionPayloadTest {

    @Test
    fun `round trip preserves every named field`() {
        val payload = PrescriptionPayload(
            sets = 4,
            targetReps = 8,
            restSeconds = 90,
            measurement = Measurement(22.5, "kg", MeasurementMeaning.TOTAL_LOAD),
            effort = EffortLevel.HARD,
        )
        assertEquals(payload, PrescriptionJson.decode(PrescriptionJson.encode(payload)))
    }

    @Test
    fun `absent effort and rest stay absent rather than becoming zero`() {
        val payload = PrescriptionPayload(sets = 2, targetDurationSeconds = 90)
        val decoded = PrescriptionJson.decode(PrescriptionJson.encode(payload))
        assertNull(decoded.effort)
        assertNull(decoded.restSeconds)
        assertNull(decoded.measurement)
        assertNull(decoded.targetReps)
    }

    @Test
    fun `added load is signed, and assistance is simply below zero`() {
        val assisted = PrescriptionPayload(
            sets = 5,
            targetDurationSeconds = 10,
            measurement = Measurement(-12.5, "kg", MeasurementMeaning.ADDED_LOAD),
        )
        val decoded = PrescriptionJson.decode(PrescriptionJson.encode(assisted))
        assertEquals(Measurement(-12.5, "kg", MeasurementMeaning.ADDED_LOAD), decoded.measurement)
    }

    @Test
    fun `effort is stored as its integer level`() {
        val json = PrescriptionJson.encode(
            PrescriptionPayload(sets = 3, targetReps = 5, effort = EffortLevel.VERY_HARD)
        )
        assertTrue(json.contains("\"effort\":5"))
        assertEquals(EffortLevel.VERY_HARD, PrescriptionJson.decode(json).effort)
    }

    @Test
    fun `every effort level round trips through its stored number`() {
        EffortLevel.entries.forEach { level ->
            val payload = PrescriptionPayload(sets = 1, effort = level)
            assertEquals(level, PrescriptionJson.decode(PrescriptionJson.encode(payload)).effort)
            assertEquals(level, EffortLevel.fromLevel(level.level))
        }
    }

    @Test
    fun `unknown fields from a newer payload version do not lose the record`() {
        val json = """{"sets":3,"targetReps":5,"targetDurationSeconds":null,"restSeconds":120,
            "measurement":null,"effort":null,"rir":null,"tempo":"3010"}"""
        val decoded = PrescriptionJson.decode(json)
        assertEquals(3, decoded.sets)
        assertEquals(5, decoded.targetReps)
        assertEquals(120, decoded.restSeconds)
    }
}
