package com.yokodake.melete.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.ExerciseVariation
import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.VariationTag
import com.yokodake.melete.ui.week.PrescriptionSummary
import java.time.LocalDate

/**
 * A variation's tag, shown beside an exercise's name wherever a copy of it appears.
 *
 * The tertiary container, as the old sample marker used: a fact about *which* plan this is,
 * distinct from the done and skipped chips, which are facts about what happened.
 */
@Composable
fun VariationChip(tag: String) {
    Chip(
        text = tag,
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

/**
 * Asks which of an exercise's plans to use: the default, or one of its variations.
 *
 * Only ever shown for an exercise that has variations. For every other exercise there is nothing
 * to choose, and asking anyway would put a pointless tap in front of the commonest action.
 */
@Composable
fun VariationChoiceDialog(
    exercise: LibraryExercise,
    onChoose: (variationId: String?) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Choose a plan for ${exercise.name}",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    PlanChoiceRow(
                        tag = null,
                        summary = PrescriptionSummary.formatDefault(exercise),
                        onClick = { onChoose(null) },
                    )
                }
                items(items = exercise.activeVariations, key = { it.id }) { variation ->
                    HorizontalDivider()
                    PlanChoiceRow(
                        tag = variation.tag,
                        summary = PrescriptionSummary.formatPlan(
                            variation.prescription,
                            exercise.mode,
                            exercise.unilateral,
                        ),
                        onClick = { onChoose(variation.id) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlanChoiceRow(tag: String?, summary: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tag != null) VariationChip(tag) else {
                Text("Default", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Adding a library exercise to a week: which plan, if there is a choice, then which week.
 *
 * One flow for every place that offers it, so the library list and the exercise itself cannot
 * drift into asking different questions in a different order.
 */
@Composable
fun AddToPlanFlow(
    exercise: LibraryExercise,
    today: LocalDate,
    onSchedule: (weekStart: LocalDate, variationId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Chosen straight away when there is nothing to choose between.
    var chosen by remember(exercise.id) {
        mutableStateOf(if (exercise.activeVariations.isEmpty()) PlanPick(null) else null)
    }
    when (val pick = chosen) {
        null -> VariationChoiceDialog(
            exercise = exercise,
            title = "Choose a plan for ${exercise.name}",
            onChoose = { chosen = PlanPick(it) },
            onDismiss = onDismiss,
        )

        else -> {
            val tag = exercise.variations.firstOrNull { it.id == pick.variationId }?.tag
            WeekTargetDialog(
                title = "Add ${exercise.name}${tag?.let { " · $it" }.orEmpty()} to",
                today = today,
                onConfirm = { week -> onSchedule(week, pick.variationId) },
                onDismiss = onDismiss,
            )
        }
    }
}

private data class PlanPick(val variationId: String?)

/** A variation being written: its tag, its notes and its plan, as typed. */
data class VariationEditorState(
    /** Null while creating one. */
    val variationId: String? = null,
    val tag: String = "",
    val notes: String = "",
    val form: PrescriptionFormState = PrescriptionFormState(),
    /** Why the last save was refused, shown under the tag. */
    val error: String? = null,
) {
    val canSave: Boolean get() = VariationTag.isValid(tag)

    companion object {
        fun of(variation: ExerciseVariation) = VariationEditorState(
            variationId = variation.id,
            tag = variation.tag,
            notes = variation.notes.orEmpty(),
            form = PrescriptionFormState.from(variation.prescription),
        )
    }
}

/**
 * Creating or changing a variation: a tag, a plan in the same fields as every other plan, and
 * notes long enough to say something like "first three reps at 40 % of 5RM, then RIR 1–2".
 */
@Composable
fun VariationEditorDialog(
    state: VariationEditorState,
    mode: ExerciseMode,
    unilateral: Boolean,
    onChange: (VariationEditorState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.variationId == null) "New variation" else "Edit variation") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactTextField(
                    value = state.tag,
                    // Folded as it is typed, so the field can never hold something unsaveable.
                    onValueChange = { onChange(state.copy(tag = VariationTag.normalise(it), error = null)) },
                    label = "Tag",
                    placeholder = "PWR",
                    supportingText = state.error ?: "1–4 letters or digits",
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
                PrescriptionFields(
                    state = state.form,
                    onStateChange = { onChange(state.copy(form = it)) },
                    mode = mode,
                    unilateral = unilateral,
                )
                CompactTextField(
                    value = state.notes,
                    onValueChange = { onChange(state.copy(notes = it)) },
                    label = "Instructions (optional)",
                    placeholder = "First 3 reps at 40% of 5RM, fast; then RIR 1–2 for the last 3.",
                    singleLine = false,
                    minLines = 3,
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.variationId != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete this variation", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = state.canSave) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
