package com.yokodake.melete.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokodake.melete.core.WeekMath
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Says which day unscheduled work is about to be filed under, with a way to change it.
 *
 * Shared by the exercise logger and the circuit review, so logging a circuit never silently picks
 * today where logging an exercise would have asked.
 */
@Composable
fun TrainingDateCard(targetDate: LocalDate, today: LocalDate, onChangeDate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("No date yet", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "Logging for ${WeekMath.dayLabel(targetDate)}" +
                        if (targetDate == today) " (today)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onChangeDate) { Text("Change") }
        }
    }
}

/** Picks the day a log is filed under. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingDatePickerDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            ) { Text("Use this date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}
