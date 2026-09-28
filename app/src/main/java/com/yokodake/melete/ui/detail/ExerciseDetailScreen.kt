package com.yokodake.melete.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import java.time.format.DateTimeFormatter
import java.util.Locale
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.R
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.ui.components.AddToPlanFlow
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.PrescriptionFields
import com.yokodake.melete.ui.components.PrescriptionFormState
import com.yokodake.melete.ui.components.VariationChip
import com.yokodake.melete.ui.components.VariationEditorDialog
import com.yokodake.melete.ui.components.VariationEditorState
import java.time.LocalDate
import com.yokodake.melete.ui.week.PrescriptionSummary

/**
 * What a workout *is*, before anything is asked of the user.
 *
 * This screen answers "what is this and what am I meant to do" and then offers the two
 * things worth doing: record it, or count it.
 */
@Composable
fun ExerciseDetailRoute(
    onLog: (occurrenceId: String, discardIfUnlogged: Boolean) -> Unit,
    onOpenTimer: () -> Unit,
    onEditExercise: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = viewModel(factory = ExerciseDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseDetailScreen(
        state = state,
        onLog = {
            val occurrenceId = state.occurrenceId
            if (occurrenceId != null) onLog(occurrenceId, false) else viewModel.logFromLibrary(onLog)
        },
        onSelectPlan = viewModel::selectPlan,
        onAdjustAttempt = viewModel::openAttemptEditor,
        onAttemptChange = viewModel::updateAttemptEditor,
        onSaveAttempt = viewModel::saveAttemptPlan,
        onDismissAttempt = viewModel::dismissAttemptEditor,
        onClearAttempt = viewModel::clearAttemptPlan,
        onStartTimer = { viewModel.requestStartTimer(onOpenTimer) },
        onConfirmReplace = { viewModel.confirmStartTimer(onOpenTimer) },
        onDismissReplace = viewModel::dismissReplacePrompt,
        onEditPlan = viewModel::openPrescriptionEditor,
        onPlanChange = viewModel::updatePrescriptionEditor,
        onSavePlan = viewModel::savePrescription,
        onDismissPlan = viewModel::dismissPrescriptionEditor,
        onEditVariation = viewModel::openVariationEditor,
        onVariationChange = viewModel::updateVariationEditor,
        onSaveVariation = viewModel::saveVariation,
        onDeleteVariation = viewModel::deleteVariation,
        onDismissVariation = viewModel::dismissVariationEditor,
        onSchedule = viewModel::schedule,
        onMessageShown = viewModel::consumeMessage,
        onEditExercise = onEditExercise,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    onLog: () -> Unit,
    onStartTimer: () -> Unit,
    onConfirmReplace: () -> Unit,
    onDismissReplace: () -> Unit,
    onEditPlan: () -> Unit,
    onPlanChange: (PrescriptionFormState) -> Unit,
    onSavePlan: () -> Unit,
    onDismissPlan: () -> Unit,
    onEditExercise: (String) -> Unit,
    onBack: () -> Unit,
    onEditVariation: (variationId: String?) -> Unit = {},
    onVariationChange: (VariationEditorState) -> Unit = {},
    onSaveVariation: () -> Unit = {},
    onDeleteVariation: () -> Unit = {},
    onDismissVariation: () -> Unit = {},
    onSchedule: (weekStart: LocalDate, variationId: String?) -> Unit = { _, _ -> },
    onMessageShown: () -> Unit = {},
    onSelectPlan: (variationId: String?) -> Unit = {},
    onAdjustAttempt: () -> Unit = {},
    onAttemptChange: (PrescriptionFormState) -> Unit = {},
    onSaveAttempt: () -> Unit = {},
    onDismissAttempt: () -> Unit = {},
    onClearAttempt: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var addingToPlan by remember { mutableStateOf(false) }
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
                    val exerciseId = state.exerciseId
                    if (state.occurrenceId == null && exerciseId != null) {
                        // In the library, editing the exercise is the page's own explicit action.
                        TextButton(onClick = { onEditExercise(exerciseId) }) { Text("Edit") }
                    }
                    if (exerciseId != null) {
                        ExerciseMenu(
                            onEditExercise = { onEditExercise(exerciseId) }
                                .takeIf { state.occurrenceId != null },
                            // Only while the entry can still be planned: a retired one is history.
                            onAddToPlan = { addingToPlan = true }
                                .takeIf { state.libraryExercise != null },
                            onAdjustAttempt = onAdjustAttempt
                                .takeIf { state.occurrenceId == null && state.libraryExercise != null },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!state.loading && state.canTrain) {
                ActionBar(
                    state = state,
                    onLog = onLog,
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

            val description = state.description?.takeIf { it.isNotBlank() }
            val notes = state.notes?.takeIf { it.isNotBlank() }
            // Why there is no explanation, when that needs saying. A missing description with
            // variation notes underneath is not an empty section, so it gets no apology.
            val absence = when {
                description != null -> null
                state.definitionMissing && state.occurrenceId != null ->
                    "The library exercise is unavailable. Your plan and log are still saved."

                notes != null -> null
                else -> null
            }
            if (description != null || absence != null || notes != null) Section("Description")
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

            val browsingLibrary = state.occurrenceId == null
            Section(
                when {
                    !browsingLibrary -> "Planned"
                    state.variations.isEmpty() -> "Default plan"
                    else -> "Plans"
                }
            )
            // With more than one plan, a tap on a card chooses which one the timer and the logger
            // use; with only the default there is nothing to choose and no marker to read.
            val choosing = browsingLibrary && state.libraryExercise != null && state.variations.isNotEmpty()
            PlanCard(
                summary = state.prescriptionSummary,
                // On a planned copy, the variation it was cut from; the default has no chip.
                tag = state.variationTag.takeIf { !browsingLibrary },
                lines = buildList {
                    add(facts(state))
                    if (state.mode.hasSetStructure) state.timerShapeLine?.let(::add)
                    if (!browsingLibrary) state.variationNotes?.let(::add)
                },
                editDescription = "Edit this plan",
                onEdit = onEditPlan,
                selected = choosing && state.selectedVariationId == null && state.attemptPlan == null,
                onSelect = { onSelectPlan(null) }.takeIf { choosing },
            )
            state.lastLogged?.let { LastLoggedBlock(it) }
            if (browsingLibrary) {
                // The alternatives, each edited where it is shown, exactly like the default.
                state.variations.forEach { variation ->
                    PlanCard(
                        summary = PrescriptionSummary.formatPlan(
                            variation.prescription,
                            state.mode,
                            state.unilateral,
                        ),
                        tag = variation.tag,
                        lines = listOfNotNull(variation.notes),
                        editDescription = "Edit variation ${variation.tag}",
                        onEdit = { onEditVariation(variation.id) },
                        selected = choosing && state.selectedVariationId == variation.id &&
                            state.attemptPlan == null,
                        onSelect = { onSelectPlan(variation.id) }.takeIf { choosing },
                    )
                }
                if (state.libraryExercise != null) {
                    TextButton(onClick = { onEditVariation(null) }) { Text("+  Add a variation") }
                }
                // A change for today only: what the timer and the logger will use, saved nowhere
                // but on the copy they make.
                state.attemptPlan?.let { plan ->
                    AttemptCard(
                        summary = PrescriptionSummary.formatPlan(plan, state.mode, state.unilateral),
                        tag = state.variations.firstOrNull { it.id == state.selectedVariationId }?.tag,
                        onEdit = onAdjustAttempt,
                        onClear = onClearAttempt,
                    )
                }
            }

            state.loggedDurationSeconds?.let { seconds ->
                Section("Duration")
                Text(
                    // A typed number is stated plainly; a worked-out one is marked as approximate.
                    text = (if (state.loggedDurationManual) "" else "≈ ") +
                        PrescriptionSummary.duration(seconds),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            state.comment?.let {
                Section("Comment")
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            if (state.occurrenceState == OccurrenceState.SKIPPED) {
                Text(
                    text = "Skipped",
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
                Text(if (state.occurrenceId == null) "Default plan" else "Edit planned exercise")
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

    state.attemptEditor?.let { form ->
        AlertDialog(
            onDismissRequest = onDismissAttempt,
            title = { Text("This attempt") },
            text = {
                Column {
                    PrescriptionFields(
                        state = form,
                        onStateChange = onAttemptChange,
                        mode = state.mode,
                        unilateral = state.unilateral,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = onSaveAttempt) { Text("Use") } },
            dismissButton = { TextButton(onClick = onDismissAttempt) { Text("Cancel") } },
        )
    }

    state.variationEditor?.let { editor ->
        VariationEditorDialog(
            state = editor,
            mode = state.mode,
            unilateral = state.unilateral,
            onChange = onVariationChange,
            onSave = onSaveVariation,
            onDelete = onDeleteVariation,
            onDismiss = onDismissVariation,
        )
    }

    if (addingToPlan) {
        state.libraryExercise?.let { exercise ->
            AddToPlanFlow(
                exercise = exercise,
                today = state.today,
                onSchedule = { week, variationId ->
                    addingToPlan = false
                    onSchedule(week, variationId)
                },
                onDismiss = { addingToPlan = false },
            )
        }
    }

    state.replacePrompt?.let {
        AlertDialog(
            onDismissRequest = onDismissReplace,
            title = { Text("Replace current timer?") },
            text = {
                Text("This stops the current timer and starts a new one. Saved logs will stay.")
            },
            confirmButton = {
                TextButton(onClick = onConfirmReplace) { Text("Replace timer") }
            },
            dismissButton = {
                TextButton(onClick = onDismissReplace) { Text("Cancel") }
            },
        )
    }
}

/**
 * One plan: the default, a variation, or the copy sitting in a week. The summary on top, the tag
 * beside it when there is one, and the cog to change it right where it is read.
 */
@Composable
private fun PlanCard(
    summary: String,
    tag: String?,
    lines: List<String>,
    editDescription: String,
    onEdit: () -> Unit,
    selected: Boolean = false,
    /** Set when the card can be chosen as the plan for an attempt. */
    onSelect: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onSelect != null) {
                    Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tag?.let { VariationChip(it) }
                    Text(text = summary, style = MaterialTheme.typography.bodyLarge)
                }
                lines.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            // The numbers are edited where they are shown, rather than through a button further
            // down that has to re-explain which plan it means.
            IconButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = editDescription },
            ) {
                Icon(painterResource(R.drawable.ic_edit_plan), contentDescription = null)
            }
        }
    }
}

/**
 * A plan changed for one attempt, shown under the plans it came from. The cog changes it again;
 * the cross goes back to the plan as saved. Nothing here reaches the library.
 */
@Composable
private fun AttemptCard(summary: String, tag: String?, onEdit: () -> Unit, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("This attempt", style = MaterialTheme.typography.labelMedium)
                    tag?.let { VariationChip(it) }
                }
                Text(text = summary, style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = "Change this attempt" },
            ) {
                Icon(painterResource(R.drawable.ic_edit_plan), contentDescription = null)
            }
            IconButton(
                onClick = onClear,
                modifier = Modifier.semantics { contentDescription = "Use the saved plan" },
            ) {
                Text("✕", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** The most recent earlier result, directly under the plan: the load to pick up today. */
@Composable
private fun LastLoggedBlock(last: LastLogged) {
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Last logged · ${lastLoggedDate(last.date)}" +
                    if (last.otherPlan && last.variationTag == null) " · default plan" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (last.otherPlan) last.variationTag?.let { VariationChip(it) }
        }
        Text(text = last.summary, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "24 Sep", with the year once it is not this one. */
private fun lastLoggedDate(date: LocalDate): String {
    val pattern = if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date)
}

@Composable
private fun ExerciseMenu(
    onEditExercise: (() -> Unit)?,
    onAddToPlan: (() -> Unit)?,
    onAdjustAttempt: (() -> Unit)?,
) {
    if (onEditExercise == null && onAddToPlan == null && onAdjustAttempt == null) return
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        modifier = Modifier.semantics { contentDescription = "Exercise actions" },
    ) {
        Text("⋮", style = MaterialTheme.typography.titleLarge)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        onAddToPlan?.let { add ->
            DropdownMenuItem(
                text = { Text("Add to plan") },
                onClick = {
                    expanded = false
                    add()
                },
            )
        }
        onAdjustAttempt?.let { adjust ->
            DropdownMenuItem(
                text = { Text("Adjust this attempt") },
                onClick = {
                    expanded = false
                    adjust()
                },
            )
        }
        onEditExercise?.let { edit ->
            DropdownMenuItem(
                text = { Text("Edit this exercise") },
                onClick = {
                    expanded = false
                    edit()
                },
            )
        }
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
    state.measurementUnit?.let { parts += it }
    // No category: the header already says it.
    return parts.joinToString(" · ")
}
