package com.yokodake.melete.ui.circuit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.CompactTextField
import com.yokodake.melete.ui.components.EffortSelector
import com.yokodake.melete.ui.components.NumberField
import com.yokodake.melete.ui.theme.doneColors
import com.yokodake.melete.ui.week.PrescriptionSummary

/**
 * Logging a whole circuit: one row per exercise, expanded only where you have something to say.
 *
 * After a circuit there are four or five things to confirm and one of them was probably not quite
 * as planned. Collapsed rows make the ordinary case two taps; expanding one gives it the same set
 * table, max-load fallback and effort scale the single-exercise logger has, because it is the same
 * record and must not be a different kind of one.
 */
@Composable
fun CircuitReviewRoute(
    onBack: () -> Unit,
    onStartTimer: () -> Unit,
    viewModel: CircuitReviewViewModel = viewModel(factory = CircuitReviewViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.finished.collect { onBack() } }
    CircuitReviewScreen(
        state = state,
        viewModel = viewModel,
        onStartTimer = { viewModel.requestStartTimer(onStartTimer) },
        onConfirmReplace = { viewModel.confirmStartTimer(onStartTimer) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitReviewScreen(
    state: CircuitReviewUiState,
    viewModel: CircuitReviewViewModel,
    onStartTimer: () -> Unit,
    onConfirmReplace: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Column {
                        Text(
                            text = state.circuit?.name.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = state.circuit?.trainingDate?.let(WeekMath::dayLabel)
                                ?: "Unscheduled",
                            style = MaterialTheme.typography.bodySmall,
                        )
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
        },
    ) { padding ->
        val circuit = state.circuit ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Planned", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${circuit.rounds} rounds of ${circuit.stations.size} " +
                                "exercises, one set of each per round",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = timingLine(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            item {
                TextButton(onClick = onStartTimer) { Text("Run the circuit on the timer") }
            }
            item { HorizontalDivider() }

            state.stations.forEach { station ->
                item(key = station.occurrence.id) {
                    StationRow(station = station, viewModel = viewModel)
                }
            }

            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.committed) "Save changes" else "Mark done")
                    }
                    state.blocker?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        text = "Each exercise counts once, however many rounds it took. " +
                            "The circuit itself counts nothing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    state.replacePrompt?.let { label ->
        ReplacePrompt(
            label = label,
            onConfirm = onConfirmReplace,
            onDismiss = viewModel::dismissReplacePrompt,
        )
    }
}

@Composable
private fun ReplacePrompt(
    label: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Another timer is running") },
        text = { Text("Starting this circuit calls off $label. Nothing recorded is affected.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Start anyway") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep the current one") } },
    )
}

/** The circuit's own timing, worked out once from the sequence rather than per exercise. */
private fun timingLine(state: CircuitReviewUiState): String {
    val total = state.stations.sumOf { it.suggestedDurationSeconds }
    val parts = mutableListOf<String>()
    if (total > 0) parts += "about ${PrescriptionSummary.duration(total)}"
    state.circuit?.transitionSeconds?.takeIf { it > 0 }?.let {
        parts += "${PrescriptionSummary.duration(it)} between exercises"
    }
    state.circuit?.roundRestSeconds?.takeIf { it > 0 }?.let {
        parts += "${PrescriptionSummary.duration(it)} between rounds"
    }
    return parts.joinToString(" · ")
}

/**
 * One exercise: a tick, a name and a max load, with everything else behind the row.
 *
 * The collapsed row is what you use with chalk on your hands. The max load sits there because it
 * is the one number that is almost always worth recording and the one that stands for every set
 * that says nothing of its own.
 */
@Composable
private fun StationRow(station: StationReview, viewModel: CircuitReviewViewModel) {
    val id = station.occurrence.id
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DoneCheck(
                    done = station.completed,
                    onToggle = { viewModel.toggleStation(id) },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                        .clickable { viewModel.toggleExpanded(id) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryDot(station.occurrence.category)
                        Text(
                            text = station.occurrence.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = listOfNotNull(
                            PrescriptionSummary.format(station.occurrence),
                            station.partial,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                station.occurrence.measurementUnit?.let { unit ->
                    NumberField(
                        label = unit,
                        value = station.table.maxLoad,
                        onValueChange = { viewModel.setMaxLoad(id, it) },
                        decimal = true,
                        modifier = Modifier.width(88.dp),
                    )
                }
                Text(
                    text = if (station.expanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable { viewModel.toggleExpanded(id) },
                )
            }

            if (!station.expanded) return@Column

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Effort",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    EffortSelector(
                        selected = station.table.effort,
                        onSelect = { viewModel.setEffort(id, it) },
                        modifier = Modifier.width(180.dp),
                    )
                }
                station.table.rows.forEach { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DoneCheck(
                            done = row.done,
                            onToggle = { viewModel.toggleSet(id, row.number) },
                        )
                        Text(
                            text = "Round ${row.number}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        station.occurrence.measurementUnit?.let {
                            NumberField(
                                label = if (station.occurrence.unilateral) "L" else it,
                                value = row.load,
                                onValueChange = { value ->
                                    viewModel.setRowLoad(id, row.number, value)
                                },
                                decimal = true,
                                modifier = Modifier.width(80.dp),
                            )
                            if (station.occurrence.unilateral) {
                                NumberField(
                                    label = "R",
                                    value = row.loadRight,
                                    onValueChange = { value ->
                                        viewModel.setRowLoad(id, row.number, value, right = true)
                                    },
                                    decimal = true,
                                    modifier = Modifier.width(80.dp),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Time taken", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (station.table.durationMinutes.isBlank()) {
                                "This exercise's share of the circuit"
                            } else {
                                "Your own number"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    NumberField(
                        label = "min",
                        value = station.table.durationMinutes,
                        onValueChange = { viewModel.setDurationMinutes(id, it) },
                        placeholder = ((station.suggestedDurationSeconds + 30) / 60).toString(),
                        modifier = Modifier.width(100.dp),
                    )
                }
                CompactTextField(
                    value = station.comment,
                    onValueChange = { viewModel.setComment(id, it) },
                    label = "Comment",
                    singleLine = false,
                    minHeight = 56,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The tick.
 *
 * Large and unmistakable on purpose: it is the one control that is pressed with tired hands, and
 * it is the difference between a plan and a record.
 */
@Composable
private fun DoneCheck(done: Boolean, onToggle: () -> Unit) {
    val (container, content) = doneColors()
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = if (done) container else MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = if (done) container else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onToggle)
            .semantics { contentDescription = if (done) "Done" else "Not done" },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Text("✓", style = MaterialTheme.typography.titleMedium, color = content)
        }
    }
}
