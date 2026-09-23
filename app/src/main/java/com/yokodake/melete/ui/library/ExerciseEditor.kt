package com.yokodake.melete.ui.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.yokodake.melete.ui.components.ChoiceField
import com.yokodake.melete.ui.components.CompactTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.PrescriptionFields
import com.yokodake.melete.ui.theme.MeleteTheme
import com.yokodake.melete.ui.components.PrescriptionFormState

@Composable
fun ExerciseEditorRoute(
    /** Called after saving, with the new exercise's id, or null when an existing one was edited. */
    onDone: (createdId: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: ExerciseEditorViewModel = viewModel(factory = ExerciseEditorViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseEditorScreen(
        state = state,
        onNameChange = viewModel::setName,
        onModeChange = viewModel::setMode,
        onUnilateralChange = viewModel::setUnilateral,
        onUnitChange = viewModel::setUnit,
        onMeaningChange = viewModel::setMeaning,
        onNotesChange = viewModel::setNotes,
        onDescriptionChange = viewModel::setDescription,
        onCategoryChange = viewModel::setCategory,
        onPrescriptionChange = viewModel::setPrescription,
        onSave = { viewModel.save(onDone) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorScreen(
    state: ExerciseEditorUiState,
    onNameChange: (String) -> Unit,
    onModeChange: (ExerciseMode) -> Unit,
    onUnilateralChange: (Boolean) -> Unit,
    onUnitChange: (String) -> Unit,
    onMeaningChange: (MeasurementMeaning) -> Unit,
    onNotesChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (ExerciseCategory?) -> Unit,
    onPrescriptionChange: (PrescriptionFormState) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text(if (state.isNew) "New exercise" else "Edit exercise") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompactTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = "Name",
                minHeight = 48,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Category", style = MaterialTheme.typography.titleSmall)
            // Wrapped. Six categories do not fit across a phone, and a plain Row squeezes the
            // ones that overflow until their labels break a letter per line.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ExerciseCategory.entries.forEach { category ->
                    CategoryChip(category, state.category, onCategoryChange)
                }
            }

            CompactTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = "What it is, and how to do it",
                placeholder = "The explanation you want to read before a set.",
                singleLine = false,
                minLines = 3,
                minHeight = 48,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            ChoiceField(
                value = state.mode,
                options = ExerciseMode.entries,
                optionLabel = ExerciseMode::label,
                onSelect = onModeChange,
                label = "How is a set measured?",
                modifier = Modifier.fillMaxWidth(),
            )
            // Sides and a load belong to sets. An activity has neither, so it is not asked — and
            // the view model saves them cleared whatever these fields held before the switch.
            if (state.mode.hasSetStructure) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Unilateral", style = MaterialTheme.typography.bodyLarge)
                    }
                    Switch(checked = state.unilateral, onCheckedChange = onUnilateralChange)
                }

                HorizontalDivider()
                Text("Measurement", style = MaterialTheme.typography.titleSmall)
                CompactTextField(
                    value = state.unit,
                    onValueChange = onUnitChange,
                    label = "Unit (empty for none)",
                    placeholder = "kg",
                    supportingText = "",
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.unit.isNotBlank()) {
                    ChoiceField(
                        value = state.meaning,
                        options = MeasurementMeaning.entries,
                        optionLabel = MeasurementMeaning::label,
                        onSelect = onMeaningChange,
                        label = "What the number means",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HorizontalDivider()
            Text("Default prescription", style = MaterialTheme.typography.titleSmall)
            PrescriptionFields(
                state = state.prescription,
                onStateChange = onPrescriptionChange,
                mode = state.mode,
                unilateral = state.unilateral,
            )

            CompactTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = "Variation notes (optional)",
                placeholder = "Grip, board, tempo, shoes…",
                singleLine = false,
                minLines = 2,
                minHeight = 48,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text(if (state.isNew) "Create exercise" else "Save changes")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChip(
    category: ExerciseCategory,
    selected: ExerciseCategory?,
    onSelect: (ExerciseCategory?) -> Unit,
) {
    FilterChip(
        selected = category == selected,
        onClick = { onSelect(if (category == selected) null else category) },
        leadingIcon = { CategoryDot(category) },
        label = { Text(category.shortLabel) },
    )
}



// ---------------------------------------------------------------- previews

/**
 * The editor as it opens, and the editor with everything filled in.
 *
 * Worth previewing because this screen is nearly all controls: a dropdown that has grown an option,
 * or a field that has gained a label, shows up here without a build and an install.
 */
@Composable
private fun EditorPreview(state: ExerciseEditorUiState) {
    MeleteTheme {
        ExerciseEditorScreen(
            state = state,
            onNameChange = {},
            onModeChange = {},
            onUnilateralChange = {},
            onUnitChange = {},
            onMeaningChange = {},
            onNotesChange = {},
            onDescriptionChange = {},
            onCategoryChange = {},
            onPrescriptionChange = {},
            onSave = {},
            onBack = {},
        )
    }
}

@Preview(name = "Editor · new", showBackground = true, widthDp = 400, heightDp = 1100)
@Composable
private fun NewExercisePreview() {
    EditorPreview(ExerciseEditorUiState())
}

@Preview(name = "Editor · repeaters", showBackground = true, widthDp = 400, heightDp = 1100)
@Composable
private fun RepeaterExercisePreview() {
    EditorPreview(
        ExerciseEditorUiState(
            exerciseId = "existing",
            name = "Max hangs 20 mm",
            mode = ExerciseMode.REPEATERS,
            unit = "kg",
            meaning = MeasurementMeaning.ADDED_LOAD,
            category = ExerciseCategory.FINGER_TRAINING,
            description = "Half crimp, both hands, feet on the floor.",
            prescription = PrescriptionFormState(
                sets = "3",
                restSeconds = "180",
                repeaterReps = "6",
                repeaterWorkSeconds = "7",
                repeaterRestSeconds = "3",
            ),
        )
    )
}

@Preview(name = "Editor · unilateral, no unit", showBackground = true, widthDp = 400, heightDp = 1100)
@Composable
private fun UnilateralExercisePreview() {
    EditorPreview(
        ExerciseEditorUiState(
            exerciseId = "existing",
            name = "Copenhagen plank",
            mode = ExerciseMode.DURATION,
            unilateral = true,
            // No unit, so the "what the number means" dropdown is not offered at all.
            unit = "",
            category = ExerciseCategory.STRENGTH_CONDITIONING,
            prescription = PrescriptionFormState(sets = "3", targetDurationSeconds = "30"),
        )
    )
}

/**
 * Every category, at the size it is actually seen.
 *
 * A palette is only as good as its worst pair, and the dot is four millimetres across — so the
 * question is not whether the colours are nice but whether any two of them are the same colour at
 * a glance. Both yellows are here so the choice can be made by looking rather than by imagining.
 */
@Preview(name = "Category colours · light", showBackground = true, widthDp = 400)
@Composable
private fun CategoryColoursPreview() {
    MeleteTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                ExerciseCategory.entries.forEach { category ->
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryDot(category)
                        Text(category.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Category colours · dark",
    showBackground = true,
    widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CategoryColoursDarkPreview() {
    CategoryColoursPreview()
}
