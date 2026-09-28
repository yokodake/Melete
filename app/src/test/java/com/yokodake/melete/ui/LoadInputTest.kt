package com.yokodake.melete.ui

import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.components.flipSign
import com.yokodake.melete.ui.components.loadInput
import com.yokodake.melete.ui.week.PrescriptionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadInputTest {

    @Test
    fun `a minus anywhere flips an added load, and a second one flips it back`() {
        assertEquals("-15", loadInput("-15", signed = true))
        assertEquals("-15", loadInput("15-", signed = true))
        assertEquals("15", loadInput("-15-", signed = true))
        assertEquals("-12.5", loadInput("-12,5", signed = true))
        assertEquals("-", loadInput("-", signed = true))
    }

    @Test
    fun `a total load never goes negative`() {
        assertEquals("15", loadInput("-15", signed = false))
        assertEquals("12.5", loadInput("12,5kg", signed = false))
    }

    @Test
    fun `the sign key flips whatever is there, empty included`() {
        assertEquals("-15", flipSign("15"))
        assertEquals("15", flipSign("-15"))
        assertEquals("-", flipSign(""))
        // A lone minus parses as nothing, so it records no load until a number follows it.
        assertEquals(null, "-".toDoubleOrNull())
    }

    @Test
    fun `loads read with their sign`() {
        assertEquals("60 kg", PrescriptionSummary.load(Measurement(60.0, "kg", MeasurementMeaning.TOTAL_LOAD)))
        assertEquals("+10 kg", PrescriptionSummary.load(Measurement(10.0, "kg", MeasurementMeaning.ADDED_LOAD)))
        assertEquals("−15 kg", PrescriptionSummary.load(Measurement(-15.0, "kg", MeasurementMeaning.ADDED_LOAD)))
        assertEquals("+2.5 kg", PrescriptionSummary.load(Measurement(2.5, "kg", MeasurementMeaning.ADDED_LOAD)))
    }
}
