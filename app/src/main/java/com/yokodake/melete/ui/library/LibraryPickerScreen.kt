package com.yokodake.melete.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.yokodake.melete.data.TrainingModule
import com.yokodake.melete.ui.components.VariationChoiceDialog
import com.yokodake.melete.ui.module.ModuleRow
import com.yokodake.melete.ui.module.unavailableNote
import com.yokodake.melete.ui.routine.RoutineRow

/**
 * Adding something saved to one slot of the week: a workout — exercise or circuit — or a module.
 *
 * A tap on a row copies it straight into the slot, after asking which plan for an exercise that
 * has variations, and after saying what will be left out for a module with unavailable entries.
 * The button at the bottom creates whichever kind the tab lists.
 */
@Composable
fun LibraryPickerRoute(
    onScheduled: () -> Unit,
    onNewWorkout: () -> Unit,
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
        onShowTab = viewModel::showTab,
        onPickExercise = { id, variationId -> viewModel.schedule(id, variationId, onScheduled) },
        onPickCircuit = { viewModel.scheduleCircuit(it, onScheduled) },
        onPickModule = { viewModel.scheduleModule(it, onScheduled) },
        onNew = when (state.tab) {
            LibraryTab.WORKOUTS -> onNewWorkout
            LibraryTab.MODULES -> onNewModule
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
    onShowTab: (LibraryTab) -> Unit,
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
    // A module with unavailable entries, waiting for a yes before they are left out.
    var confirmingModule by remember { mutableStateOf<TrainingModule?>(null) }

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
                    LibraryTabs(selected = state.tab, onSelect = onShowTab)
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                text = {
                    Text(
                        when (state.tab) {
                            LibraryTab.WORKOUTS -> "New workout"
                            LibraryTab.MODULES -> "New module"
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
            val empty = when (state.tab) {
                LibraryTab.WORKOUTS -> state.workouts.isEmpty()
                LibraryTab.MODULES -> state.modules.isEmpty()
            }
            if (empty) {
                item {
                    Text(
                        text = when (state.tab) {
                            LibraryTab.WORKOUTS ->
                                "No workouts yet. Create an exercise or a circuit to get started."
                            LibraryTab.MODULES ->
                                "No modules yet. Create a module to plan exercises and circuits as a group."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (state.tab) {
                LibraryTab.WORKOUTS -> items(items = state.workouts, key = { it.id }) { workout ->
                    when (workout) {
                        is Workout.Exercise -> LibraryRow(
                            exercise = workout.exercise,
                            onClick = {
                                if (workout.exercise.variations.isEmpty()) {
                                    onPickExercise(workout.exercise.id, null)
                                } else {
                                    choosingPlanFor = workout.exercise
                                }
                            },
                            secondaryAction = onOpenExercise to "Open",
                        )

                        is Workout.Circuit -> RoutineRow(
                            routine = workout.routine,
                            onClick = { onPickCircuit(workout.routine.id) },
                            secondaryAction = { onEditCircuit(workout.routine.id) } to "Edit",
                        )
                    }
                }

                LibraryTab.MODULES -> items(items = state.modules, key = { it.id }) { module ->
                    ModuleRow(
                        module = module,
                        onClick = {
                            if (module.unavailableEntries.isEmpty()) {
                                onPickModule(module.id)
                            } else {
                                confirmingModule = module
                            }
                        },
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

    confirmingModule?.let { module ->
        AlertDialog(
            onDismissRequest = { confirmingModule = null },
            title = { Text("Add ${module.name}?") },
            text = {
                Text(
                    (unavailableNote(module) ?: "") + "\n" +
                        module.unavailableEntries.joinToString("\n") { "· ${it.name}" }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingModule = null
                    onPickModule(module.id)
                }) { Text("Add the rest") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmingModule = null
                    onEditModule(module.id)
                }) { Text("Edit module") }
            },
        )
    }
}
