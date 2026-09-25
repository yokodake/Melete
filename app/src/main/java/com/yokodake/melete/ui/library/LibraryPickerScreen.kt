package com.yokodake.melete.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.ui.components.VariationChoiceDialog
import com.yokodake.melete.ui.module.ModuleRow
import com.yokodake.melete.ui.routine.RoutineRow

/**
 * Adding something saved to one slot of the week: an exercise, a circuit or a module.
 *
 * Exercises first, because they are what is added most. The button at the bottom always creates
 * whichever kind is on screen, and a tap on a row copies it straight into the slot — after asking
 * which plan, for an exercise that has variations.
 */
@Composable
fun LibraryPickerRoute(
    onScheduled: () -> Unit,
    onNewExercise: () -> Unit,
    onNewCircuit: () -> Unit,
    onNewModule: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onEditCircuit: (String) -> Unit,
    onEditModule: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryPickerViewModel = viewModel(factory = LibraryPickerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryPickerScreen(
        state = state,
        onShowView = viewModel::showView,
        onPickExercise = { id, variationId -> viewModel.schedule(id, variationId, onScheduled) },
        onPickCircuit = { viewModel.scheduleCircuit(it, onScheduled) },
        onPickModule = { viewModel.scheduleModule(it, onScheduled) },
        onNew = when (state.view) {
            PickerView.EXERCISES -> onNewExercise
            PickerView.CIRCUITS -> onNewCircuit
            PickerView.MODULES -> onNewModule
        },
        onOpenExercise = onOpenExercise,
        onEditCircuit = onEditCircuit,
        onEditModule = onEditModule,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPickerScreen(
    state: LibraryPickerUiState,
    onShowView: (PickerView) -> Unit,
    onPickExercise: (exerciseId: String, variationId: String?) -> Unit,
    onPickCircuit: (String) -> Unit,
    onPickModule: (String) -> Unit,
    onNew: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onEditCircuit: (String) -> Unit,
    onEditModule: (String) -> Unit,
    onBack: () -> Unit,
) {
    // An exercise with variations, waiting for its plan to be chosen.
    var choosingPlanFor by remember { mutableStateOf<LibraryExercise?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        title = {
                            Column {
                                Text("Add to", style = MaterialTheme.typography.titleMedium)
                                Text(state.targetLabel, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.semantics { contentDescription = "Back" },
                            ) {
                                Text("‹", style = MaterialTheme.typography.headlineMedium)
                            }
                        },
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    ) {
                        PickerView.entries.forEachIndexed { index, view ->
                            SegmentedButton(
                                selected = state.view == view,
                                onClick = { onShowView(view) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = PickerView.entries.size,
                                ),
                                // The label says it; a tick as well only costs width.
                                icon = {},
                            ) { Text(view.label) }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                text = {
                    Text(
                        when (state.view) {
                            PickerView.EXERCISES -> "New exercise"
                            PickerView.CIRCUITS -> "New circuit"
                            PickerView.MODULES -> "New module"
                        }
                    )
                },
                icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val empty = when (state.view) {
                PickerView.EXERCISES -> state.exercises.isEmpty()
                PickerView.CIRCUITS -> state.circuits.isEmpty()
                PickerView.MODULES -> state.modules.isEmpty()
            }
            if (empty) {
                item {
                    Text(
                        text = when (state.view) {
                            PickerView.EXERCISES ->
                                "The library is empty. Create an exercise to get started."
                            PickerView.CIRCUITS ->
                                "No circuits yet. A circuit is an order of library exercises " +
                                    "you run round, with its own rests."
                            PickerView.MODULES ->
                                "No modules yet. Create a module to plan exercises and circuits as a group."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (state.view) {
                PickerView.EXERCISES -> items(items = state.exercises, key = { it.id }) { exercise ->
                    LibraryRow(
                        exercise = exercise,
                        onClick = {
                            if (exercise.variations.isEmpty()) {
                                onPickExercise(exercise.id, null)
                            } else {
                                choosingPlanFor = exercise
                            }
                        },
                        secondaryAction = onOpenExercise to "Open",
                    )
                }

                PickerView.CIRCUITS -> items(items = state.circuits, key = { it.id }) { circuit ->
                    RoutineRow(
                        routine = circuit,
                        onClick = { onPickCircuit(circuit.id) },
                        secondaryAction = { onEditCircuit(circuit.id) } to "Edit",
                    )
                }

                PickerView.MODULES -> items(items = state.modules, key = { it.id }) { module ->
                    ModuleRow(
                        module = module,
                        onClick = { onPickModule(module.id) },
                        secondaryAction = { onEditModule(module.id) } to "Edit",
                    )
                }
            }
        }
    }

    choosingPlanFor?.let { exercise ->
        VariationChoiceDialog(
            exercise = exercise,
            onChoose = { variationId ->
                choosingPlanFor = null
                onPickExercise(exercise.id, variationId)
            },
            onDismiss = { choosingPlanFor = null },
        )
    }
}
