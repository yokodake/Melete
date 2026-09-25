package com.yokodake.melete.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.model.ExerciseCategory

/**
 * Picks a category, or none: tapping the selected chip clears it, because having no category is a
 * valid answer. Shared by the exercise and circuit editors, so both ask it the same way.
 */
@Composable
fun CategoryPicker(
    selected: ExerciseCategory?,
    onSelect: (ExerciseCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Category", style = MaterialTheme.typography.titleSmall)
        // Wrapped. Seven categories do not fit across a phone, and a plain Row squeezes the ones
        // that overflow until their labels break a letter per line.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ExerciseCategory.entries.forEach { category ->
                FilterChip(
                    selected = category == selected,
                    onClick = { onSelect(if (category == selected) null else category) },
                    leadingIcon = { CategoryDot(category) },
                    label = { Text(category.shortLabel) },
                )
            }
        }
    }
}
