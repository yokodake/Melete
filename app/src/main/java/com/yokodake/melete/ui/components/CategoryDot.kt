package com.yokodake.melete.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

/**
 * A group's category as a chip: its dot and short label ("● FLEX") on a quiet background. For a
 * module, whose category is not its own but read from what it holds ([dominantCategory]).
 * Nothing when there is no category.
 */
@Composable
fun CategoryChip(category: ExerciseCategory?, modifier: Modifier = Modifier) {
    if (category == null) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = category.label },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(8.dp).background(categoryColor(category), CircleShape))
            Text(category.shortLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * The category a group of entries reads as: the most common among those that have one, ties going
 * to whichever comes first — so a module with no clear majority takes its first exercise's.
 * Null when none has a category.
 */
fun dominantCategory(categories: List<ExerciseCategory?>): ExerciseCategory? {
    val present = categories.filterNotNull()
    if (present.isEmpty()) return null
    val counts = present.groupingBy { it }.eachCount()
    val most = counts.values.max()
    return present.first { counts.getValue(it) == most }
}
