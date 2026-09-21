package com.yokodake.melete.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.ui.theme.categoryColor

/**
 * The category as a small coloured circle before a name. Discreet by design: it is there to be
 * recognised out of the corner of the eye while scrolling, not read.
 *
 * An exercise without a category draws nothing rather than a grey placeholder, so an uncategorised
 * list simply looks like a list.
 */
@Composable
fun CategoryDot(category: ExerciseCategory?, modifier: Modifier = Modifier, size: Int = 10) {
    if (category == null) return
    Box(
        modifier = modifier
            .size(size.dp)
            .background(categoryColor(category), CircleShape)
            .semantics { contentDescription = category.label },
    )
}
