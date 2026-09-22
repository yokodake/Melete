package com.yokodake.melete.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.yokodake.melete.ui.components.CompactTextField
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.PrescriptionFields
import com.yokodake.melete.ui.components.PrescriptionFormState

@Composable
fun ExerciseEditorRoute(
    onDone: () -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Text("How is a set measured?", style = MaterialTheme.typography.titleSmall)
            // Wrapped, because four chips do not fit across a phone and a row that scrolls
            // sideways hides the one you have not thought of.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Repetitions", ExerciseMode.REPETITIONS, state.mode, onModeChange)
                ModeChip("Timed sets", ExerciseMode.DURATION, state.mode, onModeChange)
                ModeChip("Repeaters", ExerciseMode.REPEATERS, state.mode, onModeChange)
                ModeChip("Activity", ExerciseMode.ACTIVITY, state.mode, onModeChange)
            }
            Text(
                text = when (state.mode) {
                    ExerciseMode.REPETITIONS -> "Counted reps, e.g. a squat."
                    ExerciseMode.DURATION -> "Timed sets, e.g. a hang or a stretch."
                    ExerciseMode.REPEATERS ->
                        "Timed sets made of pulses, e.g. hangboard repeaters: so many short " +
                            "efforts inside one set, with a short rest between them."

                    ExerciseMode.ACTIVITY -> "Duration only, no set structure."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Left and right separately", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Sets are then prescribed and logged per side.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                supportingText = "A stretch or a bodyweight movement needs no measurement.",
                minHeight = 48,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.unit.isNotBlank()) {
                Text(
                    text = "What the number means",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeaningChip("Total load", MeasurementMeaning.TOTAL_LOAD, state, onMeaningChange)
                    MeaningChip("Added", MeasurementMeaning.ADDED_LOAD, state, onMeaningChange)
                    MeaningChip("Assistance", MeasurementMeaning.ASSISTANCE, state, onMeaningChange)
                }
                Text(
                    text = when (state.meaning) {
                        MeasurementMeaning.TOTAL_LOAD -> "Everything on the bar or the implement."
                        MeasurementMeaning.ADDED_LOAD -> "Load added to bodyweight, e.g. a belt."
                        MeasurementMeaning.ASSISTANCE -> "Load taken away, e.g. a band."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()
            Text("Default prescription", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Copied into the week when you schedule this exercise. Editing it later " +
                    "never changes copies that are already scheduled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        label = { Text(category.label) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeChip(
    label: String,
    mode: ExerciseMode,
    selected: ExerciseMode,
    onSelect: (ExerciseMode) -> Unit,
) {
    FilterChip(
        selected = mode == selected,
        onClick = { onSelect(mode) },
        label = { Text(label) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeaningChip(
    label: String,
    meaning: MeasurementMeaning,
    state: ExerciseEditorUiState,
    onSelect: (MeasurementMeaning) -> Unit,
) {
    FilterChip(
        selected = state.meaning == meaning,
        onClick = { onSelect(meaning) },
        label = { Text(label) },
    )
}
