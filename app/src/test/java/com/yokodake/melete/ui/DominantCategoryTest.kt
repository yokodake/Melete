package com.yokodake.melete.ui

import com.yokodake.melete.data.model.ExerciseCategory.FINGER_TRAINING
import com.yokodake.melete.data.model.ExerciseCategory.FLEXIBILITY
import com.yokodake.melete.data.model.ExerciseCategory.STRENGTH_CONDITIONING
import com.yokodake.melete.ui.components.dominantCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DominantCategoryTest {

    @Test
    fun `the most common category wins, uncategorised entries aside`() {
        assertEquals(
            FLEXIBILITY,
            dominantCategory(listOf(STRENGTH_CONDITIONING, FLEXIBILITY, null, FLEXIBILITY, null)),
        )
    }

    @Test
    fun `a tie goes to the first, so no majority means the first exercise's category`() {
        assertEquals(FINGER_TRAINING, dominantCategory(listOf(null, FINGER_TRAINING, STRENGTH_CONDITIONING)))
        assertEquals(STRENGTH_CONDITIONING, dominantCategory(listOf(STRENGTH_CONDITIONING, FLEXIBILITY, FLEXIBILITY, STRENGTH_CONDITIONING)))
    }

    @Test
    fun `nothing categorised means no category`() {
        assertNull(dominantCategory(listOf(null, null)))
        assertNull(dominantCategory(emptyList()))
    }
}
