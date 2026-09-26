package com.yokodake.melete.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.ui.tooling.preview.Preview
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.ui.theme.MeleteTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.yokodake.melete.ui.components.CompactTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.ui.components.AddToPlanFlow
import com.yokodake.melete.ui.components.VariationChip
import com.yokodake.melete.ui.components.WeekTargetDialog
import java.time.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.data.ExerciseRemoval
import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.data.TrainingModule
import com.yokodake.melete.ui.module.ModuleRow
import com.yokodake.melete.ui.module.unavailableNote
import com.yokodake.melete.ui.routine.RoutineRow
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.Chip
import com.yokodake.melete.ui.week.PrescriptionSummary

/** Everything the library tab can be asked to do. */
data class LibraryActions(
    val onShowTab: (LibraryTab) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onOpenExercise: (String) -> Unit = {},
    val onEditCircuit: (String) -> Unit = {},
    val onEditModule: (String) -> Unit = {},
    val onNewWorkout: () -> Unit = {},
    val onNewModule: () -> Unit = {},
    val onScheduleExercise: (exerciseId: String, weekStart: LocalDate, variationId: String?) -> Unit =
        { _, _, _ -> },
    val onScheduleCircuit: (routineId: String, weekStart: LocalDate) -> Unit = { _, _ -> },
    val onScheduleModule: (moduleId: String, weekStart: LocalDate) -> Unit = { _, _ -> },
    val onAskRemoveExercise: (LibraryExercise) -> Unit = {},
    val onConfirmRemoveExercise: () -> Unit = {},
    val onCancelRemoveExercise: () -> Unit = {},
    val onDuplicateCircuit: (String) -> Unit = {},
    val onAskRemoveCircuit: (String) -> Unit = {},
    val onConfirmRemoveCircuit: () -> Unit = {},
    val onCancelRemoveCircuit: () -> Unit = {},
    val onDuplicateModule: (String) -> Unit = {},
    val onAskRemoveModule: (String) -> Unit = {},
    val onConfirmRemoveModule: () -> Unit = {},
    val onCancelRemoveModule: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onOpenBackup: () -> Unit = {},
)

/**
 * The library as a tab: workouts — exercises and circuits together — and the modules that group
 * them. A tap on an exercise opens it, not a form: what it is comes first, and editing it is a
 * button on that screen. A circuit or a module has no such page, so a tap edits it.
 */
@Composable
fun LibraryRoute(
    onOpenExercise: (String) -> Unit,
    onNewWorkout: () -> Unit,
    onEditCircuit: (String) -> Unit,
    onNewModule: () -> Unit,
    onEditModule: (String) -> Unit,
    onOpenBackup: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        actions = LibraryActions(
            onShowTab = viewModel::showTab,
            onQueryChange = viewModel::setQuery,
            onOpenExercise = onOpenExercise,
            onEditCircuit = onEditCircuit,
            onEditModule = onEditModule,
            onNewWorkout = onNewWorkout,
            onNewModule = onNewModule,
            onScheduleExercise = viewModel::schedule,
            onScheduleCircuit = viewModel::scheduleCircuit,
            onScheduleModule = viewModel::scheduleModule,
            onAskRemoveExercise = viewModel::askToRemove,
            onConfirmRemoveExercise = viewModel::confirmRemoval,
            onCancelRemoveExercise = viewModel::cancelRemoval,
            onDuplicateCircuit = viewModel::duplicateCircuit,
            onAskRemoveCircuit = viewModel::askToRemoveCircuit,
            onConfirmRemoveCircuit = viewModel::confirmCircuitRemoval,
            onCancelRemoveCircuit = viewModel::cancelCircuitRemoval,
            onDuplicateModule = viewModel::duplicateModule,
            onAskRemoveModule = viewModel::askToRemoveModule,
            onConfirmRemoveModule = viewModel::confirmModuleRemoval,
            onCancelRemoveModule = viewModel::cancelModuleRemoval,
            onMessageShown = viewModel::consumeMessage,
            onOpenBackup = onOpenBackup,
        ),
        bottomBar = bottomBar,
    )
}

/** Something from the library waiting for a week to be added to. */
private sealed interface Scheduling {
    data class Exercise(val exercise: LibraryExercise) : Scheduling
    data class Circuit(val id: String, val name: String) : Scheduling
    data class Module(val module: TrainingModule) : Scheduling
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    bottomBar: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var scheduling by remember { mutableStateOf<Scheduling?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        title = { Text("Library", style = MaterialTheme.typography.titleMedium) },
                        actions = { LibraryMenu(onOpenBackup = actions.onOpenBackup) },
                    )
                    LibraryTabs(selected = state.tab, onSelect = actions.onShowTab)
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = when (state.tab) {
                    LibraryTab.WORKOUTS -> actions.onNewWorkout
                    LibraryTab.MODULES -> actions.onNewModule
                },
                text = { Text("New") },
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
            item {
                CompactTextField(
                    value = state.query,
                    onValueChange = actions.onQueryChange,
                    label = "Search",
                    minHeight = 48,
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            TextButton(onClick = { actions.onQueryChange("") }) { Text("Clear") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val empty = when (state.tab) {
                LibraryTab.WORKOUTS -> state.workouts.isEmpty()
                LibraryTab.MODULES -> state.modules.isEmpty()
            }
            if (empty) {
                item {
                    Text(
                        text = when {
                            state.noMatches -> "Nothing matches \"${state.query.trim()}\"."
                            state.tab == LibraryTab.WORKOUTS ->
                                "No workouts yet. Create an exercise or a circuit to get started."
                            else ->
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
                            onClick = { actions.onOpenExercise(workout.exercise.id) },
                            secondaryAction = null,
                            onAddToPlan = { scheduling = Scheduling.Exercise(workout.exercise) },
                            onRetire = { actions.onAskRemoveExercise(workout.exercise) },
                        )

                        is Workout.Circuit -> RoutineRow(
                            routine = workout.routine,
                            onClick = { actions.onEditCircuit(workout.routine.id) },
                            onEdit = { actions.onEditCircuit(workout.routine.id) },
                            onDuplicate = { actions.onDuplicateCircuit(workout.routine.id) },
                            onRemove = { actions.onAskRemoveCircuit(workout.routine.id) },
                            onAddToPlan = {
                                scheduling = Scheduling.Circuit(workout.routine.id, workout.routine.name)
                            },
                        )
                    }
                }

                LibraryTab.MODULES -> items(items = state.modules, key = { it.id }) { module ->
                    ModuleRow(
                        module = module,
                        onClick = { actions.onEditModule(module.id) },
                        onEdit = { actions.onEditModule(module.id) },
                        onDuplicate = { actions.onDuplicateModule(module.id) },
                        onRemove = { actions.onAskRemoveModule(module.id) },
                        onAddToPlan = { scheduling = Scheduling.Module(module) },
                    )
                }
            }
        }
    }

    // Unscheduled throughout: the week is the decision, the day is the planner's job.
    when (val target = scheduling) {
        is Scheduling.Exercise -> AddToPlanFlow(
            exercise = target.exercise,
            today = state.today,
            onSchedule = { week, variationId ->
                scheduling = null
                actions.onScheduleExercise(target.exercise.id, week, variationId)
            },
            onDismiss = { scheduling = null },
        )

        is Scheduling.Circuit -> WeekTargetDialog(
            title = "Add ${target.name} to",
            today = state.today,
            onConfirm = { week ->
                scheduling = null
                actions.onScheduleCircuit(target.id, week)
            },
            onDismiss = { scheduling = null },
        )

        is Scheduling.Module -> WeekTargetDialog(
            // What will be left out is said before the week is chosen, not discovered after.
            title = "Add ${target.module.name} to" +
                (unavailableNote(target.module)?.let { "\n$it — won’t be added" } ?: ""),
            today = state.today,
            onConfirm = { week ->
                scheduling = null
                actions.onScheduleModule(target.module.id, week)
            },
            onDismiss = { scheduling = null },
        )

        null -> Unit
    }

    state.exerciseRemoval?.let {
        RemovalDialog(it, actions.onConfirmRemoveExercise, actions.onCancelRemoveExercise)
    }

    state.circuitRemoval?.let { removal ->
        AlertDialog(
            onDismissRequest = actions.onCancelRemoveCircuit,
            title = { Text(removal.routine.name) },
            text = {
                Text(
                    text = if (removal.scheduledCopies > 0 || removal.recordedCopies > 0) {
                        "Remove this saved circuit? Existing plans and logs will stay."
                    } else {
                        "Remove this saved circuit?"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = actions.onConfirmRemoveCircuit) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = actions.onCancelRemoveCircuit) { Text("Cancel") }
            },
        )
    }

    state.moduleRemoval?.let { removal ->
        AlertDialog(
            onDismissRequest = actions.onCancelRemoveModule,
            title = { Text("Remove ${removal.module.name}?") },
            text = {
                Text(
                    if (removal.scheduledCopies > 0) {
                        "Remove this saved module? Modules already in your plan will stay."
                    } else {
                        "Remove this saved module?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = actions.onConfirmRemoveModule) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = actions.onCancelRemoveModule) { Text("Cancel") }
            },
        )
    }
}

/** The library's overflow: the things about the whole record rather than any one entry. */
@Composable
private fun LibraryMenu(onOpenBackup: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = "More" },
        ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Backup & restore") },
                onClick = { open = false; onOpenBackup() },
            )
        }
    }
}

/**
 * Removing an exercise, worded as whichever of the three things it is about to do.
 *
 * A mistake should leave nothing behind, so an exercise nothing has been done with is deleted
 * outright — along with planned copies that were never trained, which are mistakes too. The moment
 * one set exists the row stops being disposable, because it is what that set's history hangs from,
 * and the only honest offer left is to stop being shown it.
 */
@Composable
private fun RemovalDialog(
    removal: ExerciseRemoval,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = removal.exercise.name
    val copies = removal.plannedCopies
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (removal.kind) {
                    ExerciseRemoval.Kind.UNUSED,
                    ExerciseRemoval.Kind.PLANNED_NEVER_LOGGED -> "Delete $name?"
                    ExerciseRemoval.Kind.LOGGED -> "Remove $name?"
                }
            )
        },
        text = {
            Text(
                when (removal.kind) {
                    ExerciseRemoval.Kind.UNUSED ->
                        "Delete this exercise from your library?"
                    ExerciseRemoval.Kind.PLANNED_NEVER_LOGGED ->
                        "This will delete the exercise and its $copies planned " +
                            "${if (copies == 1) "entry" else "entries"}."
                    ExerciseRemoval.Kind.LOGGED ->
                        "Remove this exercise from your library? Existing plans and logs will stay."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    when (removal.kind) {
                        ExerciseRemoval.Kind.UNUSED -> "Delete"
                        ExerciseRemoval.Kind.PLANNED_NEVER_LOGGED -> "Delete exercise and plans"
                        ExerciseRemoval.Kind.LOGGED -> "Remove from library"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * One library entry.
 *
 * A tap opens it; a long press offers the things that change the plan or the library itself. The
 * overflow beside it does the same job for anyone who cannot discover or perform a long press —
 * an invisible gesture must not be the only route to an action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryRow(
    exercise: LibraryExercise,
    onClick: () -> Unit,
    secondaryAction: Pair<(String) -> Unit, String>?,
    onAddToPlan: () -> Unit = {},
    onRetire: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuExpanded = true
                },
                onLongClickLabel = "Exercise actions",
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
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
                    CategoryDot(exercise.category)
                    // fill = false: a short name takes only what it needs, so the chip sits
                    // beside it rather than being pushed to the far edge of the row.
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Which alternatives exist, at a glance; what each one is lives on the exercise.
                    exercise.activeVariations.forEach { VariationChip(it.tag) }
                }
                Text(
                    text = PrescriptionSummary.formatDefault(exercise),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // No explanation here on purpose: the list is for finding an exercise, and a
                // paragraph under every row turns scanning it into reading it. The description
                // belongs on the exercise, which is one tap away.
            }
            if (secondaryAction != null) {
                val (action, label) = secondaryAction
                TextButton(onClick = { action(exercise.id) }) { Text(label) }
            } else {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.semantics { contentDescription = "Exercise actions" },
                ) {
                    Text("⋮", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Add to plan") },
                onClick = {
                    menuExpanded = false
                    onAddToPlan()
                },
            )
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = {
                    menuExpanded = false
                    onClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Remove from library") },
                onClick = {
                    menuExpanded = false
                    onRetire()
                },
            )
        }
    }
}

// ---------------------------------------------------------------- previews

private fun previewExercise(
    name: String,
    category: ExerciseCategory?,
    sets: Int = 4,
    reps: Int? = 8,
    seconds: Int? = null,
    unit: String? = "kg",
    retired: Boolean = false,
) = LibraryExercise(
    id = name,
    name = name,
    mode = if (seconds != null) ExerciseMode.DURATION else ExerciseMode.REPETITIONS,
    unilateral = false,
    measurementUnit = unit,
    measurementMeaning = unit?.let { MeasurementMeaning.TOTAL_LOAD },
    notes = null,
    description = "How to do it, read on the exercise itself rather than in this list.",
    category = category,
    defaultPrescription = PrescriptionPayload(
        sets = sets,
        targetReps = reps,
        targetDurationSeconds = seconds,
        restSeconds = 180,
    ),
    deletedAtEpochMs = if (retired) 1_700_000_000_000 else null,
)

private val previewLibrary = listOf(
    previewExercise("Back squat", ExerciseCategory.STRENGTH_CONDITIONING, reps = 5),
    previewExercise("Bouldering session", ExerciseCategory.OPEN_CLIMBING, sets = 1, reps = null, seconds = 5400, unit = null),
    previewExercise("Couch stretch", ExerciseCategory.FLEXIBILITY, sets = 2, reps = null, seconds = 90, unit = null),
    previewExercise("Dumbbell row", ExerciseCategory.STRENGTH_CONDITIONING),
    previewExercise("Max hangs 20 mm", ExerciseCategory.STRENGTH_CONDITIONING, sets = 5, reps = null, seconds = 10),
    previewExercise("Pullups", ExerciseCategory.STRENGTH_CONDITIONING, sets = 4, reps = 4, unit= "kg", ),
)

@Preview(name = "Library · populated", showBackground = true, heightDp = 760)
@Composable
private fun LibraryPopulatedPreview() {
    MeleteTheme {
        LibraryScreen(
            state = LibraryUiState(workouts = previewLibrary.map(Workout::Exercise)),
            actions = LibraryActions(),
        )
    }
}

@Preview(name = "Library · search results", showBackground = true, heightDp = 760)
@Composable
private fun LibrarySearchPreview() {
    MeleteTheme {
        LibraryScreen(
            state = LibraryUiState(
                query = "ha",
                workouts = workoutsOf(previewLibrary, emptyList(), "ha"),
            ),
            actions = LibraryActions(),
        )
    }
}

/**
 * A retired definition still rendering correctly.
 *
 * It no longer appears in the library — that is the whole point of retiring it — but everything
 * that refers to it keeps working, so the row still has a name, a category and a plan to show
 * wherever history puts it in front of you.
 */
@Preview(name = "Library · retired, history intact", showBackground = true, heightDp = 400)
@Composable
private fun LibraryRetiredPreview() {
    MeleteTheme {
        LibraryScreen(
            state = LibraryUiState(
                workouts = listOf(
                    Workout.Exercise(
                        previewExercise("Pull-up", ExerciseCategory.STRENGTH_CONDITIONING, retired = true)
                    )
                ),
            ),
            actions = LibraryActions(),
        )
    }
}

/**
 * Adding from the library asks for a week and nothing finer.
 *
 * Last week is offered too, so a session that was trained but never written down can still be put
 * where it happened.
 */
@Preview(name = "Library · choose a week", showBackground = true, heightDp = 700)
@Composable
private fun WeekTargetPreview() {
    MeleteTheme {
        WeekTargetDialog(
            title = "Add Max hangs 20 mm to",
            today = LocalDate.of(2026, 9, 23),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

/**
 * What a long press offers.
 *
 * Rendered as a plain surface rather than a real DropdownMenu: a menu is a popup window, and
 * popups do not compose into a preview. The items and their order are what this is for.
 */
@Preview(name = "Library · long-press menu", showBackground = true, widthDp = 260)
@Composable
private fun LibraryMenuPreview() {
    MeleteTheme {
        Surface(tonalElevation = 3.dp) {
            Column {
                listOf("Add to plan", "Open", "Remove from library").forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

/**
 * The three removals, side by side.
 *
 * They are one menu item and three different promises, which is exactly why each states its own
 * consequence and its button says what it does rather than "OK".
 */
@Preview(name = "Library · delete an unused exercise", showBackground = true, heightDp = 320)
@Composable
private fun RemoveUnusedPreview() {
    MeleteTheme {
        RemovalDialog(
            removal = ExerciseRemoval(
                exercise = previewExercise("Sissy squat", ExerciseCategory.STRENGTH_CONDITIONING),
                plannedCopies = 0,
                loggedSets = 0,
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Library · delete, plans and all", showBackground = true, heightDp = 320)
@Composable
private fun RemovePlannedPreview() {
    MeleteTheme {
        RemovalDialog(
            removal = ExerciseRemoval(
                exercise = previewExercise("Sissy squat", ExerciseCategory.STRENGTH_CONDITIONING),
                plannedCopies = 3,
                loggedSets = 0,
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Library · remove something trained", showBackground = true, heightDp = 360)
@Composable
private fun RemoveLoggedPreview() {
    MeleteTheme {
        RemovalDialog(
            removal = ExerciseRemoval(
                exercise = previewExercise("Back squat", ExerciseCategory.STRENGTH_CONDITIONING, reps = 5),
                plannedCopies = 6,
                loggedSets = 24,
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
