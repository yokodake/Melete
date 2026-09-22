package com.yokodake.melete.ui.circuit

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
import com.yokodake.melete.ui.components.CategoryDot
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
                        ) { Text("Start the circuit") }
                        Button(
                            onClick = { onLog(circuit.id) },
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

            Section("The circuit")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${circuit.rounds} rounds of ${state.stations.size} exercises",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = restLine(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            state.estimatedSeconds?.let {
                Text(
                    text = "≈ ${PrescriptionSummary.duration(it)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Section("In order")
            state.stations.forEachIndexed { index, station ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CategoryDot(station.occurrence.category)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = station.occurrence.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = PrescriptionSummary.format(station.occurrence),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (station.occurrence.hasRecord) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.titleMedium,
                            color = doneColors().first,
                        )
                    } else {
                        Text(
                            text = PrescriptionSummary.duration(station.estimatedSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }

            if (state.recorded > 0) {
                Section("Recorded")
                Text(
                    text = if (state.completed) {
                        "All ${state.stations.size} exercises are logged. Each counts once, " +
                            "however many rounds it took."
                    } else {
                        "${state.recorded} of ${state.stations.size} exercises are logged."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    state.replacePrompt?.let { label ->
        AlertDialog(
            onDismissRequest = onDismissReplace,
            title = { Text("Another timer is running") },
            text = { Text("Starting this circuit calls off $label. Nothing recorded is affected.") },
            confirmButton = { TextButton(onClick = onConfirmReplace) { Text("Start anyway") } },
            dismissButton = {
                TextButton(onClick = onDismissReplace) { Text("Keep the current one") }
            },
        )
    }
}

/** What falls between the exercises and between the rounds, when anything does. */
private fun restLine(state: CircuitDetailUiState): String {
    val circuit = state.circuit ?: return ""
    val parts = buildList {
        if (circuit.transitionSeconds > 0) {
            add("${PrescriptionSummary.duration(circuit.transitionSeconds)} rest")
        }
        if (circuit.roundRestSeconds > 0) {
            add("${PrescriptionSummary.duration(circuit.roundRestSeconds)} set rest")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "Straight through, no rests"
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}
