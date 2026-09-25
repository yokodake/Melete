package com.yokodake.melete.ui.module

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.CompactTextField
import com.yokodake.melete.ui.components.EditorTopBar
import com.yokodake.melete.ui.components.PrescriptionFields
import com.yokodake.melete.ui.components.PrescriptionFormState
import com.yokodake.melete.ui.components.VariationChip
import com.yokodake.melete.ui.components.VariationChoiceDialog
import com.yokodake.melete.ui.week.PrescriptionSummary

@Composable
fun ModuleEditorRoute(
    onNewExercise: () -> Unit,
    onNewCircuit: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ModuleEditorViewModel = viewModel(factory = ModuleEditorViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModuleEditorScreen(
        state = state,
        actions = ModuleEditorActions(
            onName = viewModel::setName,
            onDescription = viewModel::setDescription,
            onOpenPicker = viewModel::openPicker,
            onDismissPicker = viewModel::dismissPicker,
            onPickExercise = viewModel::pickExercise,
            onChoosePlan = viewModel::choosePlan,
            onPickCircuit = viewModel::pickCircuit,
            onNewExercise = { viewModel.createExercise(onNewExercise) },
            onNewCircuit = { viewModel.createCircuit(onNewCircuit) },
            onReplace = viewModel::openReplace,
            onToggle = viewModel::toggleEntry,
            onMove = viewModel::moveEntry,
            onRemove = viewModel::removeEntry,
            onForm = viewModel::updateEntryForm,
            onSave = { viewModel.save(onDone) },
            onBack = onBack,
        ),
    )
}

/** Everything the editor can be asked to do, gathered so the screen's signature stays readable. */
data class ModuleEditorActions(
    val onName: (String) -> Unit = {},
    val onDescription: (String) -> Unit = {},
    val onOpenPicker: (ModulePick) -> Unit = {},
    val onDismissPicker: () -> Unit = {},
    val onPickExercise: (com.yokodake.melete.data.LibraryExercise) -> Unit = {},
    val onChoosePlan: (String?) -> Unit = {},
    val onPickCircuit: (com.yokodake.melete.data.Routine) -> Unit = {},
    val onNewExercise: () -> Unit = {},
    val onNewCircuit: () -> Unit = {},
    val onReplace: (Int) -> Unit = {},
    val onToggle: (Int) -> Unit = {},
    val onMove: (Int, Int) -> Unit = { _, _ -> },
    val onRemove: (Int) -> Unit = {},
    val onForm: (Int, PrescriptionFormState) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Making or changing a module: its name, what it is for, and what is in it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleEditorScreen(state: ModuleEditorUiState, actions: ModuleEditorActions) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            EditorTopBar(
                title = if (state.existing) "Edit module" else "New module",
                onBack = actions.onBack,
                onSave = actions.onSave,
                canSave = state.canSave,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CompactTextField(
                    value = state.name,
                    onValueChange = actions.onName,
                    label = "Name",
                    placeholder = "Fingers + mobility",
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CompactTextField(
                    value = state.description,
                    onValueChange = actions.onDescription,
                    label = "Description (optional)",
                    placeholder = "Base block finger strength, before climbing.",
                    singleLine = false,
                    minLines = 2,
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            itemsIndexed(state.entries, key = { _, entry -> entry.key }) { index, entry ->
                EntryCard(
                    entry = entry,
                    first = index == 0,
                    last = index == state.entries.lastIndex,
                    onToggle = { actions.onToggle(index) },
                    onMove = { actions.onMove(index, it) },
                    onRemove = { actions.onRemove(index) },
                    onForm = { actions.onForm(index, it) },
                    onReplace = { actions.onReplace(index) },
                )
            }
            item {
                Row {
                    TextButton(onClick = { actions.onOpenPicker(ModulePick.EXERCISE) }) {
                        Text("+  Exercise")
                    }
                    TextButton(onClick = { actions.onOpenPicker(ModulePick.CIRCUIT) }) {
                        Text("+  Circuit")
                    }
                }
            }
        }
    }

    when (state.picking) {
        ModulePick.EXERCISE -> AlertDialog(
            onDismissRequest = actions.onDismissPicker,
            title = { Text("Add an exercise") },
            text = {
                if (state.library.isEmpty()) {
                    Text("The library is empty.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(items = state.library, key = { it.id }) { exercise ->
                            PickRow(
                                onClick = { actions.onPickExercise(exercise) },
                                content = {
                                    CategoryDot(exercise.category)
                                    Text(
                                        text = exercise.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    exercise.activeVariations.forEach { VariationChip(it.tag) }
                                },
                            )
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = actions.onDismissPicker) { Text("Close") } },
            confirmButton = {
                TextButton(onClick = actions.onNewExercise) { Text("+  New exercise") }
            },
        )

        ModulePick.CIRCUIT -> AlertDialog(
            onDismissRequest = actions.onDismissPicker,
            title = { Text("Add a circuit") },
            text = {
                if (state.circuits.isEmpty()) {
                    Text("No circuits yet.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(items = state.circuits, key = { it.id }) { circuit ->
                            PickRow(
                                onClick = { actions.onPickCircuit(circuit) },
                                content = {
                                    Column {
                                        Text(circuit.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = circuitLine(circuit),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            },
            // Circuits are often the thing not written yet, so one can be made from right here
            // and comes back as the next entry, with the module as it was left.
            dismissButton = { TextButton(onClick = actions.onDismissPicker) { Text("Close") } },
            confirmButton = {
                TextButton(onClick = actions.onNewCircuit) { Text("+  New circuit") }
            },
        )

        null -> Unit
    }

    state.choosingPlanFor?.let { exercise ->
        VariationChoiceDialog(
            exercise = exercise,
            onChoose = actions.onChoosePlan,
            onDismiss = actions.onDismissPicker,
        )
    }
}

@Composable
private fun PickRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/**
 * One entry, collapsed to its name and a line until opened. A circuit never opens: its plan is the
 * saved circuit's, edited there, and copied as it stands when the module is scheduled.
 */
@Composable
private fun EntryCard(
    entry: ModuleEntryUi,
    first: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onForm: (PrescriptionFormState) -> Unit,
    onReplace: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // One colour for both: a circuit entry is told apart by its line, not its shade.
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !entry.isCircuit, onClick = onToggle),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryDot(entry.category)
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        entry.variationTag?.let { VariationChip(it) }
                    }
                    if (entry.unavailable) {
                        // Scheduling leaves this out; say so here, where it can be repaired.
                        Text(
                            text = "Unavailable — replace or remove. Left out when added to a plan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            text = if (entry.isCircuit) {
                                entry.circuitSummary.orEmpty()
                            } else {
                                PrescriptionSummary.formatPlan(
                                    entry.form.toPayload(entry.mode),
                                    entry.mode,
                                    entry.unilateral,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (entry.unavailable) {
                    TextButton(onClick = onReplace) { Text("Replace") }
                }
                IconButton(
                    onClick = { onMove(-1) },
                    enabled = !first,
                    modifier = Modifier.semantics { contentDescription = "Move ${entry.name} up" },
                ) { Text("↑") }
                IconButton(
                    onClick = { onMove(1) },
                    enabled = !last,
                    modifier = Modifier.semantics { contentDescription = "Move ${entry.name} down" },
                ) { Text("↓") }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.semantics { contentDescription = "Remove ${entry.name} from module" },
                ) { Text("×", style = MaterialTheme.typography.titleLarge) }
            }
            if (entry.expanded && !entry.isCircuit && !entry.unavailable) {
                Text(
                    text = "Changes apply to this module only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrescriptionFields(
                    state = entry.form,
                    onStateChange = onForm,
                    mode = entry.mode,
                    unilateral = entry.unilateral,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (entry.isCircuit && !entry.unavailable) {
                Text(
                    text = "Uses the saved circuit when added to your plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
