package com.yokodake.melete.data

import com.yokodake.melete.data.model.VariationTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VariationTagTest {

    @Test
    fun `typing is folded to capitals rather than refused`() {
        assertEquals("PWR", VariationTag.normalise("pwr"))
        assertEquals("A", VariationTag.normalise("a"))
    }

    @Test
    fun `only letters and digits survive, four at most`() {
        assertEquals("V2", VariationTag.normalise("v-2"))
        assertEquals("MAXS", VariationTag.normalise("max strength"))
        assertEquals("5RM", VariationTag.normalise("5 rm!"))
    }

    @Test
    fun `accented and non-latin letters are dropped rather than mangled`() {
        // É is not silently turned into E: it is not a letter the chip allows, so it goes.
        assertEquals("ND", VariationTag.normalise("éND"))
    }

    @Test
    fun `a tag is one to four capitals or digits`() {
        listOf("A", "B", "PWR", "STR", "END", "SPD", "MAX", "V2", "1234").forEach {
            assertTrue(it, VariationTag.isValid(it))
        }
        listOf("", "pwr", "TOOLONG", "A B", "É").forEach {
            assertFalse(it, VariationTag.isValid(it))
        }
    }
}
