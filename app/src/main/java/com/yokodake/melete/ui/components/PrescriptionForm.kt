package com.yokodake.melete.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.PrescriptionPayload

/**
 * Raw text state for prescription input. Values are kept as typed so half-finished input survives
 * recomposition and navigation, and so an empty field stays *absent* instead of becoming zero.
 *
 * There is deliberately no load here. What a plan fixes is the shape of the work — how many sets,
 * how long or how many reps, how much rest, how hard it should feel — while the weight on the bar
 * is what the day decides and what the logger records. A prescribed load would be a number the
 * user has to argue with before every set.
 */
data class PrescriptionFormState(
    val sets: String = "3",
    val targetReps: String = "",
    val targetDurationSeconds: String = "",
    val restSeconds: String = "",
    val effort: EffortLevel? = null,
    val rir: String = "",
) {
    /**
     * Written with `measurement = null`. An older prescription that still carries a load keeps it
     * on disk — rows are never mutated — but re-saving one drops it, which is the intended
     * migration away from prescribed weight.
     */
    fun toPayload(): PrescriptionPayload = PrescriptionPayload(
        sets = sets.toIntOrNull()?.coerceAtLeast(0) ?: 1,
        targetReps = targetReps.toIntOrNull(),
        targetDurationSeconds = targetDurationSeconds.toIntOrNull(),
        restSeconds = restSeconds.toIntOrNull(),
        measurement = null,
        effort = effort,
        rir = rir.toIntOrNull(),
    )

    companion object {
        fun from(payload: PrescriptionPayload?): PrescriptionFormState = PrescriptionFormState(
            sets = payload?.sets?.toString() ?: "3",
            targetReps = payload?.targetReps?.toString().orEmpty(),
            targetDurationSeconds = payload?.targetDurationSeconds?.toString().orEmpty(),
            restSeconds = payload?.restSeconds?.toString().orEmpty(),
            effort = payload?.effort,
            rir = payload?.rir?.toString().orEmpty(),
        )
    }
}

fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/**
 * The prescription half of a form. Shared by the exercise editor (a library default) and by
 * editing the copy that sits in a week — the same fields, two different owners.
 */
@Composable
fun PrescriptionFields(
    state: PrescriptionFormState,
    onStateChange: (PrescriptionFormState) -> Unit,
    mode: ExerciseMode,
    unilateral: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode != ExerciseMode.ACTIVITY) {
                NumberField(
                    label = if (unilateral) "Sets per side" else "Sets",
                    value = state.sets,
                    onValueChange = { onStateChange(state.copy(sets = it)) },
                    modifier = Modifier.weight(1f),
                )
            }
            when (mode) {
                ExerciseMode.REPETITIONS -> NumberField(
                    label = "Target reps",
                    value = state.targetReps,
                    onValueChange = { onStateChange(state.copy(targetReps = it)) },
                    modifier = Modifier.weight(1f),
                )

                ExerciseMode.DURATION, ExerciseMode.ACTIVITY -> NumberField(
                    label = "Target seconds",
                    value = state.targetDurationSeconds,
                    onValueChange = { onStateChange(state.copy(targetDurationSeconds = it)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        NumberField(
            label = "Rest seconds",
            value = state.restSeconds,
            onValueChange = { onStateChange(state.copy(restSeconds = it)) },
            modifier = Modifier.fillMaxWidth(0.5f),
        )
        Text("Target RPE", style = MaterialTheme.typography.bodyMedium)
        EffortSelector(
            selected = state.effort,
            onSelect = { onStateChange(state.copy(effort = it)) },
        )
        NumberField(
            label = "Target RIR",
            value = state.rir,
            onValueChange = { onStateChange(state.copy(rir = it)) },
            modifier = Modifier.fillMaxWidth(0.5f),
        )
        Text(
            text = "Leave a field empty to record it as not set. Empty is not zero. " +
                "Load belongs to the set you actually did, not to the plan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The five-point effort scale. Tapping the selected level clears it again, because an effort
 * rating is always optional and must stay genuinely unset rather than defaulting to a middle
 * value nobody chose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffortSelector(
    selected: EffortLevel?,
    onSelect: (EffortLevel?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EffortLevel.entries.forEach { level ->
            FilterChip(
                selected = selected == level,
                onClick = { onSelect(if (selected == level) null else level) },
                label = { Text(level.label) },
            )
        }
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            val filtered = typed.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }
                .replace(',', '.')
            onValueChange(filtered)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
