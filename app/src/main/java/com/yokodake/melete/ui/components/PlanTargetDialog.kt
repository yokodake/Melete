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

/** Where a plan is being put: a week, and either a day in it or its unscheduled area. */
data class PlanTarget(val weekStart: LocalDate, val trainingDate: LocalDate?)

/**
 * Chooses a week and a day.
 *
 * A dialog rather than a screen because it is always an aside from something else — scheduling
 * from the library, moving from the planner — and because the answer is two taps from anywhere in
 * the year: step the week, pick the row.
 *
 * The unscheduled row comes first and is the default, which is the honest shape of most planning:
 * you know it is happening this week before you know which day.
 */
@Composable
fun PlanTargetDialog(
    title: String,
    initial: PlanTarget,
    confirmLabel: String,
    today: LocalDate,
    onConfirm: (PlanTarget) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Whether "anytime this week" is one of the answers.
     *
     * It is not, for work that has already been trained: that happened on a particular day, and
     * offering to file it under no day at all would be offering to make the record vaguer than
     * the truth.
     */
    allowUnscheduled: Boolean = true,
) {
    var weekStart by remember { mutableStateOf(WeekMath.weekStartOf(initial.weekStart)) }
    var trainingDate by remember { mutableStateOf(initial.trainingDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            weekStart = weekStart.minusWeeks(1)
                            // The chosen day belongs to the week it was chosen in.
                            trainingDate = trainingDate?.minusWeeks(1)
                        },
                        modifier = Modifier.semantics { contentDescription = "Previous week" },
                    ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    Text(
                        text = WeekMath.weekLabel(weekStart),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            weekStart = weekStart.plusWeeks(1)
                            trainingDate = trainingDate?.plusWeeks(1)
                        },
                        modifier = Modifier.semantics { contentDescription = "Next week" },
                    ) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                }
                HorizontalDivider()
                Column(modifier = Modifier.heightIn(max = 320.dp)) {
                    if (allowUnscheduled) {
                        TargetRow(
                            label = "Unscheduled",
                            detail = "Anytime this week",
                            selected = trainingDate == null,
                            onSelect = { trainingDate = null },
                        )
                    }
                    WeekMath.daysOf(weekStart).forEach { date ->
                        TargetRow(
                            label = WeekMath.dayLabel(date),
                            detail = if (date == today) "Today" else null,
                            selected = trainingDate == date,
                            onSelect = { trainingDate = date },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(PlanTarget(weekStart, trainingDate)) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

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
