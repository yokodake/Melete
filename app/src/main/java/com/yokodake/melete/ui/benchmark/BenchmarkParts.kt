package com.yokodake.melete.ui.benchmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.Benchmark
import com.yokodake.melete.data.BenchmarkFormat
import com.yokodake.melete.data.BenchmarkResult
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.components.CompactTextField
import com.yokodake.melete.ui.components.NumberField
import com.yokodake.melete.ui.components.SignKey
import com.yokodake.melete.ui.components.TrainingDatePickerDialog
import com.yokodake.melete.ui.components.flipSign
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "24 Sep", with the year once it is not [today]'s. */
fun benchmarkDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val pattern = if (date.year == today.year) "d MMM" else "d MMM yyyy"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date)
}

/** What the result dialog hands back: a date, the value or both sides, and a note. */
data class ResultInput(
    val date: LocalDate,
    val value: Double?,
    val valueRight: Double?,
    val note: String?,
)

/**
 * Recording a result, or correcting one: the date (today unless changed), the value — or left and
 * right — and an optional note. The best valid attempt of a test, not every attempt.
 *
 * Unilateral and sign follow the result's own terms when correcting one, and the benchmark's
 * definition when recording a new one. Zero is a result; a blank is not.
 */
@Composable
fun ResultDialog(
    benchmark: Benchmark,
    existing: BenchmarkResult?,
    onSave: (ResultInput) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val unilateral = existing?.unilateral ?: benchmark.unilateral
    val meaning = if (existing != null) existing.loadMeaning else benchmark.loadMeaning
    val unit = existing?.unit ?: benchmark.unit
    val signed = meaning == MeasurementMeaning.ADDED_LOAD
    fun text(value: Double?) = value?.let { BenchmarkFormat.number(it, null) }
        ?.replace("−", "-").orEmpty()

    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var left by remember { mutableStateOf(text(existing?.value)) }
    var right by remember { mutableStateOf(text(existing?.valueRight)) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val leftValue = left.toDoubleOrNull()
    val rightValue = right.toDoubleOrNull()
    val canSave = leftValue != null || (unilateral && rightValue != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(benchmark.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                benchmark.protocol?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = benchmarkDate(date) + if (date == LocalDate.now()) " (today)" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pickingDate = true }) { Text("Change") }
                }
                val suffix = unit.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                if (unilateral) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ValueField("Left$suffix", left, signed, Modifier.weight(1f)) { left = it }
                        ValueField("Right$suffix", right, signed, Modifier.weight(1f)) { right = it }
                    }
                } else {
                    ValueField(
                        label = "Result$suffix",
                        value = left,
                        signed = signed,
                        modifier = Modifier.fillMaxWidth(),
                    ) { left = it }
                }
                CompactTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Note (optional)",
                    singleLine = false,
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmingDelete = true }) {
                        Text("Delete this result", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        ResultInput(
                            date = date,
                            value = leftValue,
                            valueRight = rightValue.takeIf { unilateral },
                            note = note,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingDate) {
        TrainingDatePickerDialog(
            initial = date,
            onPick = { date = it },
            onDismiss = { pickingDate = false },
        )
    }

    if (confirmingDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this result?") },
            text = { Text("${benchmarkDate(existing?.date ?: date)} · ${existing?.text.orEmpty()}") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ValueField(
    label: String,
    value: String,
    signed: Boolean,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        NumberField(
            label = label,
            value = value,
            onValueChange = onChange,
            decimal = true,
            signed = signed,
            modifier = Modifier.weight(1f),
        )
        // Added load goes below zero for assistance, and decimal keyboards often hide the minus.
        if (signed) SignKey(label.lowercase()) { onChange(flipSign(value)) }
    }
}
