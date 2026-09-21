package com.yokodake.melete.ui.detail

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
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.ui.components.CategoryDot

/**
 * What a workout *is*, before anything is asked of the user.
 *
 * Tapping an exercise used to land straight in the logger, which answered a question nobody had
 * asked yet. This screen answers "what is this and what am I meant to do" and then offers the two
 * things worth doing: record it, or count it.
 */
@Composable
fun ExerciseDetailRoute(
    onLog: (String) -> Unit,
    onOpenTimer: () -> Unit,
    onEditExercise: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = viewModel(factory = ExerciseDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseDetailScreen(
        state = state,
        onLog = onLog,
        onStartTimer = { viewModel.requestStartTimer(onOpenTimer) },
        onConfirmReplace = { viewModel.confirmStartTimer(onOpenTimer) },
        onDismissReplace = viewModel::dismissReplacePrompt,
        onEditExercise = onEditExercise,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    onLog: (String) -> Unit,
    onStartTimer: () -> Unit,
    onConfirmReplace: () -> Unit,
    onDismissReplace: () -> Unit,
    onEditExercise: (String) -> Unit,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryDot(state.category)
                        Column {
                            Text(state.name, style = MaterialTheme.typography.titleMedium)
                            Text(state.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
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
            if (state.occurrenceId != null) {
                ActionBar(
                    state = state,
                    onLog = { onLog(state.occurrenceId) },
                    onStartTimer = onStartTimer,
                )
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

            Section("What it is")
            Text(
                text = state.description
                    ?: if (state.definitionMissing) {
                        "The library entry this came from no longer exists, so there is nothing " +
                            "left to explain it. What was planned and what was logged are intact."
                    } else {
                        "No explanation written yet."
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.description == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            state.notes?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section(if (state.occurrenceId == null) "Default plan" else "Planned")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = state.prescriptionSummary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = facts(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            state.comment?.let {
                Section("Your note")
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            if (state.occurrenceState == OccurrenceState.SKIPPED) {
                Text(
                    text = "Marked skipped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Editing the definition is opt-in. The screen shows what the exercise is; changing
            // what it is, or what it prescribes by default, is a separate decision.
            state.exerciseId?.let { exerciseId ->
                OutlinedButton(
                    onClick = { onEditExercise(exerciseId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
                ) {
                    Text("Edit this exercise and its default plan")
                }
            }
        }
    }

    state.replacePrompt?.let { running ->
        AlertDialog(
            onDismissRequest = onDismissReplace,
            title = { Text("A countdown is already running") },
            text = {
                Text(
                    buildString {
                        append(if (running.phase == TimerPhase.WORK) "A work" else "A rest")
                        append(" countdown")
                        running.label?.let { append(" for $it") }
                        append(
                            " is still going. Starting this one cancels it — nothing that was " +
                                "recorded is affected."
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmReplace) { Text("Cancel it and start") }
            },
            dismissButton = {
                TextButton(onClick = onDismissReplace) { Text("Keep the current one") }
            },
        )
    }
}

@Composable
private fun ActionBar(
    state: ExerciseDetailUiState,
    onLog: () -> Unit,
    onStartTimer: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onStartTimer, modifier = Modifier.weight(1f)) {
                Text(state.timerPlan?.buttonLabel ?: "Start the timer")
            }
            Button(onClick = onLog, modifier = Modifier.weight(1f)) {
                Text("Log the workout")
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

/** The handful of facts that change how a set is performed, spelled out rather than implied. */
private fun facts(state: ExerciseDetailUiState): String {
    val parts = mutableListOf<String>()
    parts += when (state.mode) {
        ExerciseMode.REPETITIONS -> "counted reps"
        ExerciseMode.DURATION -> "timed sets"
        ExerciseMode.ACTIVITY -> "duration only"
    }
    if (state.unilateral) parts += "left and right separately"
    state.measurementUnit?.let { parts += "logged in $it" }
    state.category?.let { parts += it.label.lowercase() }
    return parts.joinToString(" · ")
}
