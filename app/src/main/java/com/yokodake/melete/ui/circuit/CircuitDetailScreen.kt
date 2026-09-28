package com.yokodake.melete.ui.circuit

import com.yokodake.melete.ui.theme.accentButtonColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.ui.components.ContentsLine
import com.yokodake.melete.ui.components.DetailSection
import com.yokodake.melete.ui.components.circuitShapeLine
import com.yokodake.melete.ui.theme.doneColors
import com.yokodake.melete.ui.week.PrescriptionSummary

/**
 * What a scheduled circuit is, before anything is asked of you.
 *
 * The same shape as opening an exercise, because it answers the same question: what is this, and
 * what are the two things worth doing next. Going straight into the review meant a circuit could
 * only be answered and never read, and the order of its stations and how long it will take are
 * exactly what you want in front of you before you start.
 */
@Composable
fun CircuitDetailRoute(
    onLog: (String) -> Unit,
    onOpenTimer: () -> Unit,
    onBack: () -> Unit,
    viewModel: CircuitDetailViewModel = viewModel(factory = CircuitDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CircuitDetailScreen(
        state = state,
        onLog = onLog,
        onStartTimer = { viewModel.requestStartTimer(onOpenTimer) },
        onConfirmReplace = { viewModel.confirmStartTimer(onOpenTimer) },
        onDismissReplace = viewModel::dismissReplacePrompt,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitDetailScreen(
    state: CircuitDetailUiState,
    onLog: (String) -> Unit,
    onStartTimer: () -> Unit,
    onConfirmReplace: () -> Unit,
    onDismissReplace: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Column {
                        Text(
                            text = state.circuit?.name.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
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
        bottomBar = {
            state.circuit?.let { circuit ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onStartTimer,
                            modifier = Modifier.weight(1f),
                        ) { Text("Start timer") }
                        Button(
                            onClick = { onLog(circuit.id) },
                            colors = accentButtonColors(),
                            modifier = Modifier.weight(1f),
                        ) { Text(state.logButtonLabel) }
                    }
                }
            }
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
            if (state.loading) {
                Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            val circuit = state.circuit ?: run {
                Text(
                    text = "This circuit is no longer in the week.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Column
            }

            // The shape and the whole circuit's estimate. Stations carry no time of their own
            // here: their share of the clock includes the rests, and read beside "1 × 30 s" it
            // only looked like a contradiction.
            DetailSection(
                circuitShapeLine(
                    rounds = circuit.rounds,
                    transitionSeconds = circuit.transitionSeconds,
                    roundRestSeconds = circuit.roundRestSeconds,
                    estimatedSeconds = state.estimatedSeconds,
                ) + if (state.recorded in 1 until state.stations.size) {
                    " · ${state.recorded} of ${state.stations.size} logged"
                } else {
                    ""
                }
            )
            state.stations.forEachIndexed { index, station ->
                ContentsLine(
                    position = index + 1,
                    category = station.occurrence.category,
                    name = station.occurrence.name,
                    summary = PrescriptionSummary.formatStation(
                        station.occurrence.prescription,
                        station.occurrence.mode,
                        station.occurrence.unilateral,
                    ),
                    tag = station.occurrence.variationTag,
                    trailing = if (station.occurrence.hasRecord) {
                        {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.titleMedium,
                                color = doneColors().first,
                            )
                        }
                    } else {
                        null
                    },
                )
                HorizontalDivider()
            }
        }
    }

    state.replacePrompt?.let {
        AlertDialog(
            onDismissRequest = onDismissReplace,
            title = { Text("Replace current timer?") },
            text = { Text("This stops the current timer and starts a new one. Saved logs will stay.") },
            confirmButton = { TextButton(onClick = onConfirmReplace) { Text("Replace timer") } },
            dismissButton = {
                TextButton(onClick = onDismissReplace) { Text("Cancel") }
            },
        )
    }
}

