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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.PrescriptionFields
import com.yokodake.melete.ui.week.PrescriptionSummary

/**
 * What a workout *is*, before anything is asked of the user.
 *
 * This screen answers "what is this and what am I meant to do" and then offers the two
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
        onEditPlan = viewModel::openPrescriptionEditor,
        onPlanChange = viewModel::updatePrescriptionEditor,
        onSavePlan = viewModel::savePrescription,
        onDismissPlan = viewModel::dismissPrescriptionEditor,
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
    onEditPlan: () -> Unit,
    onPlanChange: (com.yokodake.melete.ui.components.PrescriptionFormState) -> Unit,
    onSavePlan: () -> Unit,
    onDismissPlan: () -> Unit,
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
                            Text(state.category?.label ?: "", style = MaterialTheme.typography.bodySmall)
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
                // Changing what the exercise *is* — its name, category, explanation, how a set is
                // measured — is rarer than changing its numbers, so it sits one level further in.
                actions = {
                    state.exerciseId?.let { ExerciseMenu(it, onEditExercise) }
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

            Section("Description")
            val description = state.description?.takeIf { it.isNotBlank() }
            val notes = state.notes?.takeIf { it.isNotBlank() }
            // Why there is no explanation, when that needs saying. A missing description with
            // variation notes underneath is not an empty section, so it gets no apology.
            val absence = when {
                description != null -> null
                state.isOneOff ->
                    "An activity you typed in, so there is no library entry behind it. " +
                        "Create one in the library if it is something you will plan again."

                state.definitionMissing ->
                    "The library entry this came from no longer exists, so there is " +
                        "nothing left to explain it. What was planned and what was " +
                        "logged are intact."

                notes != null -> null
                else -> "No explanation written yet."
            }
            (description ?: absence)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (description == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            notes?.let {
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
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.prescriptionSummary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = facts(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        if (state.mode.hasSetStructure) {
                            state.timerShapeLine?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                    // The numbers are edited where they are shown, rather than through a button
                    // further down that has to re-explain which plan it means.
                    IconButton(
                        onClick = onEditPlan,
                        modifier = Modifier.semantics {
                            contentDescription = "Edit this plan"
                        },
                    ) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            state.loggedDurationSeconds?.let { seconds ->
                Section("Duration")
                Text(
                    text = if (state.loggedDurationManual) "" else "≈"
                            + PrescriptionSummary.duration(seconds),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            state.comment?.let {
                Section("Comment")
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            if (state.occurrenceState == OccurrenceState.SKIPPED) {
                Text(
                    text = "Marked skipped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        }
    }

    state.prescriptionEditor?.let { form ->
        AlertDialog(
            onDismissRequest = onDismissPlan,
            title = {
                Text(if (state.occurrenceId == null) "Default plan" else "Plan for this copy")
            },
            text = {
                Column {
                    PrescriptionFields(
                        state = form,
                        onStateChange = onPlanChange,
                        mode = state.mode,
                        unilateral = state.unilateral,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = onSavePlan) { Text("Save") } },
            dismissButton = { TextButton(onClick = onDismissPlan) { Text("Cancel") } },
        )
    }

    state.replacePrompt?.let { running ->
        AlertDialog(
            onDismissRequest = onDismissReplace,
            title = { Text("Start a new workout?") },
            text = {
                Text("The current timer will be stopped and progress will be lost.")
            },
            confirmButton = {
                TextButton(onClick = onConfirmReplace) { Text("START") }
            },
            dismissButton = {
                TextButton(onClick = onDismissReplace) { Text("CANCEL") }
            },
        )
    }
}

@Composable
private fun ExerciseMenu(exerciseId: String, onEditExercise: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        modifier = Modifier.semantics { contentDescription = "Exercise actions" },
    ) {
        Text("⋮", style = MaterialTheme.typography.titleLarge)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Edit this exercise") },
            onClick = {
                expanded = false
                onEditExercise(exerciseId)
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
                Text(state.timerButtonLabel)
            }
            Button(onClick = onLog, modifier = Modifier.weight(1f)) {
                Text(state.logButtonLabel)
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
        ExerciseMode.REPETITIONS -> "reps"
        ExerciseMode.DURATION -> "timed"
        ExerciseMode.REPEATERS -> "repeaters"
        ExerciseMode.ACTIVITY -> "activity"
    }
    if (state.unilateral) parts += "unilateral"
//    state.measurementUnit?.let { parts += "logged in $it" }
    state.category?.let { parts += it.label.lowercase() }
    return parts.joinToString(" · ")
}
