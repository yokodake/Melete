package com.yokodake.melete.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokodake.melete.core.WeekMath
import java.time.LocalDate

/**
 * Chooses a week, and nothing finer.
 *
 * Adding something from the library is a decision about *what* you are going to train, not about
 * which morning. The day is a later question, answered in the planner once the week has taken
 * shape, so this asks only for the week and drops the exercise into its unscheduled area.
 *
 * The list has no end. It is lazy, so only the rows on screen exist, and a block planned for
 * next spring is a scroll away rather than beyond an arbitrary horizon.
 */
@Composable
fun WeekTargetDialog(
    title: String,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val thisWeek = WeekMath.weekStartOf(today)
    var selected by remember { mutableStateOf(thisWeek) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                // Row 0 is last week, so a session you forgot to write down can still be put where
                // it happened; everything after it runs forward without limit.
                items(count = Int.MAX_VALUE) { index ->
                    val week = thisWeek.plusWeeks(index - 1L)
                    TargetRow(
                        label = WeekMath.weekLabel(week),
                        detail = when (week) {
                            thisWeek -> "This week"
                            thisWeek.plusWeeks(1) -> "Next week"
                            thisWeek.minusWeeks(1) -> "Last week"
                            else -> null
                        },
                        selected = selected == week,
                        onSelect = { selected = week },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TargetRow(
    label: String,
    detail: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
