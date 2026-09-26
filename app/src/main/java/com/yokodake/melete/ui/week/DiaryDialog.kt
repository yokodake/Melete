package com.yokodake.melete.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.DiaryDay
import com.yokodake.melete.data.MetricDefinition
import com.yokodake.melete.ui.components.ChoiceField
import com.yokodake.melete.ui.components.CompactTextField
import java.time.LocalDate

/**
 * One day's diary: a few lines, and each metric as a word on its scale — or *Not set*, which is a
 * real answer. Nothing here is ever required, and nothing here is a workout.
 */
@Composable
fun DiaryDialog(
    date: LocalDate,
    metrics: List<MetricDefinition>,
    day: DiaryDay?,
    onSave: (text: String?, values: Map<String, Int?>) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(date) { mutableStateOf(day?.text.orEmpty()) }
    var values by remember(date) { mutableStateOf<Map<String, Int?>>(day?.values.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(WeekMath.dayLabel(date)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "Notes",
                    placeholder = "Slept badly, skin still sore from Tuesday.",
                    singleLine = false,
                    minLines = 3,
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
                metrics.forEach { metric ->
                    ChoiceField(
                        value = values[metric.id],
                        // "Not set" first: leaving a metric unrated is as findable as rating it.
                        options = listOf<Int?>(null) + (1..metric.scale.size).toList(),
                        optionLabel = { value -> value?.let(metric::labelFor) ?: "Not set" },
                        onSelect = { value -> values = values + (metric.id to value) },
                        label = metric.label,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, values) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A day's diary in one line under its heading: the text, then each rated metric as a word. Shown
 * only when there is an entry; tapping it opens the diary.
 */
@Composable
fun DiaryLine(day: DiaryDay, metrics: List<MetricDefinition>, onClick: () -> Unit) {
    val ratings = metrics.mapNotNull { metric ->
        day.values[metric.id]?.let { value -> metric.labelFor(value)?.let { "${metric.label}: $it" } }
    }
    Text(
        text = listOfNotNull(day.text?.takeIf { it.isNotBlank() }, ratings.joinToString(" · ").ifEmpty { null })
            .joinToString(" — "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}
