package com.yokodake.melete.ui.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.yokodake.melete.ui.components.NumberField
import com.yokodake.melete.ui.components.PrescriptionFields
import com.yokodake.melete.ui.week.PrescriptionSummary

/**
 * Making or changing a circuit: an order of exercises, how many times round, and what falls
 * between.
 *
 * Each station carries its own copy of the numbers, opened in place. Editing one here changes this
 * circuit and nothing else — not the library default, not a standalone plan, and not a copy
 * already sitting in a week.
 */
@Composable
fun RoutineEditorRoute(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: RoutineEditorViewModel = viewModel(factory = RoutineEditorViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RoutineEditorScreen(
        state = state,
        onName = viewModel::setName,
        onRounds = viewModel::setRounds,
        onTransition = viewModel::setTransitionSeconds,
        onRoundRest = viewModel::setRoundRestSeconds,
        onAddStation = viewModel::openPicker,
        onPickStation = viewModel::addStation,
        onDismissPicker = viewModel::dismissPicker,
        onToggleStation = viewModel::toggleStation,
        onMoveStation = viewModel::moveStation,
        onRemoveStation = viewModel::removeStation,
        onStationForm = viewModel::updateStationForm,
        onSave = { viewModel.save(onDone) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    state: RoutineEditorUiState,
    onName: (String) -> Unit,
    onRounds: (String) -> Unit,
    onTransition: (String) -> Unit,
    onRoundRest: (String) -> Unit,
    onAddStation: () -> Unit,
    onPickStation: (com.yokodake.melete.data.LibraryExercise) -> Unit,
    onDismissPicker: () -> Unit,
    onToggleStation: (Int) -> Unit,
    onMoveStation: (Int, Int) -> Unit,
    onRemoveStation: (Int) -> Unit,
    onStationForm: (Int, com.yokodake.melete.ui.components.PrescriptionFormState) -> Unit,
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
                title = {
                    Text(
                        text = if (state.existing) "Edit circuit" else "New circuit",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = state.canSave) { Text("Save") }
                },
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
                    onValueChange = onName,
                    label = "Name",
                    placeholder = "Pull circuit",
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        label = "Rounds",
                        value = state.rounds,
                        onValueChange = onRounds,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        label = "Between exercises",
                        value = state.transitionSeconds,
                        onValueChange = onTransition,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        label = "Between rounds",
                        value = state.roundRestSeconds,
                        onValueChange = onRoundRest,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Text(
                    text = "These replace each exercise's own set rest while the circuit runs. " +
                        "Rest between repeater reps and the side switch still apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                state.estimatedSeconds?.let {
                    Text(
                        text = "About ${PrescriptionSummary.duration(it)} in all, " +
                            "worked out from the sequence it will run.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            itemsIndexed(state.stations, key = { index, it -> "${it.exerciseId}-$index" }) { index, station ->
                StationCard(
                    index = index,
                    last = index == state.stations.lastIndex,
                    station = station,
                    onToggle = { onToggleStation(index) },
                    onMove = { onMoveStation(index, it) },
                    onRemove = { onRemoveStation(index) },
                    onForm = { onStationForm(index, it) },
                )
            }

            item {
                TextButton(onClick = onAddStation) { Text("+  Add an exercise") }
            }
        }
    }

    if (state.picking) {
        AlertDialog(
            onDismissRequest = onDismissPicker,
            title = { Text("Add an exercise") },
            text = {
                if (state.library.isEmpty()) {
                    Text("The library is empty. Create an exercise first.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(items = state.library, key = { it.id }) { exercise ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickStation(exercise) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CategoryDot(exercise.category)
                                Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismissPicker) { Text("Close") } },
        )
    }
}

/**
 * One station, collapsed to its name and a one-line plan until you open it.
 *
 * Collapsed by default because most of the time a circuit is reordered rather than re-planned, and
 * four open prescription forms is a screen you cannot see the shape of.
 */
@Composable
private fun StationCard(
    index: Int,
    last: Boolean,
    station: StationDraft,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onForm: (com.yokodake.melete.ui.components.PrescriptionFormState) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onToggle),
                ) {
                    Text(
                        text = "${index + 1}. ${station.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stationLine(station),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onMove(-1) },
                    enabled = index > 0,
                    modifier = Modifier.semantics { contentDescription = "Move up" },
                ) { Text("↑") }
                IconButton(
                    onClick = { onMove(1) },
                    enabled = !last,
                    modifier = Modifier.semantics { contentDescription = "Move down" },
                ) { Text("↓") }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.semantics { contentDescription = "Remove" },
                ) { Text("×", style = MaterialTheme.typography.titleLarge) }
            }
            if (station.expanded) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "This circuit's own copy. The library default is untouched.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PrescriptionFields(
                            state = station.form,
                            onStateChange = onForm,
                            mode = station.mode,
                            unilateral = station.unilateral,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One station in one line: what a round of it is, not what it would be on its own. */
private fun stationLine(station: StationDraft): String {
    val payload = station.form.toPayload(station.mode)
    val parts = mutableListOf<String>()
    payload.repeater?.let {
        parts += "${it.repsPerSet} × ${PrescriptionSummary.duration(it.workSecondsPerRep)}"
    }
    if (payload.repeater == null) {
        payload.targetDurationSeconds?.let { parts += PrescriptionSummary.duration(it) }
        payload.targetReps?.let { parts += "$it reps" }
    }
    if (station.unilateral) parts += "both sides"
    if (parts.isEmpty()) parts += "one set per round"
    return parts.joinToString(" · ")
}
