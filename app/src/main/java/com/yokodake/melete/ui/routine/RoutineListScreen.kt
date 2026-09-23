package com.yokodake.melete.ui.routine

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.data.Routine
import com.yokodake.melete.data.timer.PrescriptionProgram
import com.yokodake.melete.data.timer.StationPlan
import com.yokodake.melete.ui.week.PrescriptionSummary

/**
 * The saved circuits, as a list to manage. Putting one in a week is the library picker's job, in
 * its circuit view, so there is one way to add something to a day rather than two.
 */
@Composable
fun RoutineListRoute(
    onNewRoutine: () -> Unit,
    onEditRoutine: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RoutineListViewModel = viewModel(factory = RoutineListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RoutineListScreen(
        state = state,
        onRowClick = onEditRoutine,
        onEdit = onEditRoutine,
        onDuplicate = viewModel::duplicate,
        onAskRemove = viewModel::askToRemove,
        onConfirmRemove = viewModel::confirmRemoval,
        onCancelRemove = viewModel::cancelRemoval,
        onNewRoutine = onNewRoutine,
        onMessageShown = viewModel::consumeMessage,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    state: RoutineListUiState,
    onRowClick: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onAskRemove: (String) -> Unit,
    onConfirmRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onNewRoutine: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text("Circuits", style = MaterialTheme.typography.titleMedium) },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewRoutine,
                text = { Text("New circuit") },
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
            if (state.routines.isEmpty() && !state.loading) {
                item {
                    Text(
                        text = "No circuits yet. A circuit is an order of exercises you run " +
                            "round, with its own rests — the exercises themselves stay in the " +
                            "library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = state.routines, key = { it.id }) { routine ->
                RoutineRow(
                    routine = routine,
                    onClick = { onRowClick(routine.id) },
                    onEdit = { onEdit(routine.id) },
                    onDuplicate = { onDuplicate(routine.id) },
                    onRemove = { onAskRemove(routine.id) },
                )
            }
        }
    }

    state.removal?.let { removal ->
        AlertDialog(
            onDismissRequest = onCancelRemove,
            title = { Text(removal.routine.name) },
            text = {
                Text(
                    text = when {
                        removal.recordedCopies > 0 ->
                            "This has been trained ${removal.recordedCopies} " +
                                "${if (removal.recordedCopies == 1) "time" else "times"}. " +
                                "Removing it takes it out of this list only — every scheduled " +
                                "copy and everything logged stays exactly as it is."

                        removal.scheduledCopies > 0 ->
                            "${removal.scheduledCopies} copies are scheduled. Removing it takes " +
                                "it out of this list only; those copies stay in their weeks."

                        else -> "Nothing has been cut from this, so it goes completely."
                    },
                )
            },
            confirmButton = { TextButton(onClick = onConfirmRemove) { Text("Remove") } },
            dismissButton = { TextButton(onClick = onCancelRemove) { Text("Keep it") } },
        )
    }
}

/**
 * One saved circuit: its name, its length and its order.
 *
 * With a [secondaryAction] it is a picker row — a tap chooses it and the button beside it does the
 * one other thing, as an exercise row does in the same picker. Without one, a long press offers
 * managing it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RoutineRow(
    routine: Routine,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onRemove: () -> Unit = {},
    secondaryAction: Pair<() -> Unit, String>? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (secondaryAction == null) menuOpen = true },
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = routineLength(routine),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    secondaryAction?.let { (action, label) ->
                        TextButton(onClick = action) { Text(label) }
                    }
                }
                Text(
                    text = "${routine.rounds} rounds · " +
                        routine.entries.joinToString(" → ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    menuOpen = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                onClick = {
                    menuOpen = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

/**
 * How long the whole circuit takes, worked out once from the sequence it will actually run.
 *
 * Never by adding up what its exercises would take on their own: a station in a circuit does one
 * set per round and rests by the circuit's rules, so its standalone plan is the wrong arithmetic.
 */
internal fun routineLength(routine: Routine): String {
    if (routine.entries.isEmpty()) return "empty"
    val program = PrescriptionProgram.circuit(
        label = routine.name,
        rounds = routine.rounds,
        transitionSeconds = routine.transitionSeconds,
        roundRestSeconds = routine.roundRestSeconds,
        stations = routine.entries.map {
            StationPlan(
                label = it.name,
                mode = it.mode,
                unilateral = it.unilateral,
                prescription = it.prescription,
            )
        },
    )
    return "about ${PrescriptionSummary.duration(program.estimatedSeconds())}"
}
