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
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.Chip
import com.yokodake.melete.ui.week.PrescriptionSummary

/**
 * The library as a tab: browse what exists. A tap opens the exercise, not a form — what it is
 * comes first, and editing it is a button on that screen.
 */
@Composable
fun LibraryRoute(
    onOpenExercise: (String) -> Unit,
    onNewExercise: () -> Unit,
    onOpenCircuits: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        title = "Library",
        subtitle = null,
        exercises = state.exercises,
        emptyMessage = if (state.noMatches) {
            "Nothing matches \"${state.query}\"."
        } else {
            "No exercises yet. Everything here is yours to define — nothing is built in."
        },
        query = state.query,
        onQueryChange = viewModel::setQuery,
        message = state.message,
        onMessageShown = viewModel::consumeMessage,
        today = state.today,
        defaultWeekStart = viewModel.currentWeekStart,
        onSchedule = viewModel::schedule,
        removal = state.removal,
        onAskRemove = viewModel::askToRemove,
        onConfirmRemove = viewModel::confirmRemoval,
        onCancelRemove = viewModel::cancelRemoval,
        onRowClick = onOpenExercise,
        onNewExercise = onNewExercise,
        onOpenCircuits = onOpenCircuits,
        onBack = null,
        bottomBar = bottomBar,
    )
}

/** The library as a picker: choosing an exercise copies it into the chosen slot of the week. */
@Composable
fun LibraryPickerRoute(
    onScheduled: () -> Unit,
    onNewExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryPickerViewModel = viewModel(factory = LibraryPickerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        title = "Add to",
        subtitle = state.targetLabel,
        exercises = state.exercises,
        emptyMessage = "The library is empty. Create an exercise to get started — " +
            "nothing is built in.",
        onRowClick = { viewModel.schedule(it, onScheduled) },
        onSecondaryAction = onOpenExercise to "Open",
        onNewExercise = onNewExercise,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    title: String,
    subtitle: String?,
    exercises: List<LibraryExercise>,
    emptyMessage: String,
    onRowClick: (String) -> Unit,
    onNewExercise: () -> Unit,
    onBack: (() -> Unit)?,
    onSecondaryAction: Pair<(String) -> Unit, String>? = null,
    query: String? = null,
    onQueryChange: (String) -> Unit = {},
    message: String? = null,
    onMessageShown: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
    defaultWeekStart: LocalDate = WeekMath.weekStartOf(today),
    onSchedule: (String, LocalDate, LocalDate?) -> Unit = { _, _, _ -> },
    /** What removing the exercise under consideration would cost; null when nothing is pending. */
    removal: ExerciseRemoval? = null,
    onAskRemove: (LibraryExercise) -> Unit = {},
    onConfirmRemove: () -> Unit = {},
    onCancelRemove: () -> Unit = {},
    /** Null in the picker: choosing a circuit for a slot is a different flow from browsing them. */
    onOpenCircuits: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var scheduling by remember { mutableStateOf<LibraryExercise?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.semantics { contentDescription = "Back" },
                        ) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                },
                // Circuits are made of library exercises and are edited the same way, so this is
                // where they belong; they are not a fourth tab, because they are used far less
                // often than the three that are.
                actions = {
                    if (onOpenCircuits != null) {
                        TextButton(onClick = onOpenCircuits) { Text("Circuits") }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewExercise,
                text = { Text("New exercise") },
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
            if (query != null) {
                item {
                    CompactTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        label = "Search",
                        minHeight = 48,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                TextButton(onClick = { onQueryChange("") }) { Text("Clear") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (exercises.isEmpty()) {
                item {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = exercises, key = { it.id }) { exercise ->
                LibraryRow(
                    exercise = exercise,
                    onClick = { onRowClick(exercise.id) },
                    secondaryAction = onSecondaryAction,
                    onAddToPlan = { scheduling = exercise },
                    onRetire = { onAskRemove(exercise) },
                )
            }
        }
    }

    scheduling?.let { exercise ->
        WeekTargetDialog(
            title = "Add ${exercise.name} to",
            today = today,
            onConfirm = { week ->
                scheduling = null
                // Unscheduled: the week is the decision, the day is the planner's job.
                onSchedule(exercise.id, week, null)
            },
            onDismiss = { scheduling = null },
        )
    }

    removal?.let { RemovalDialog(it, onConfirmRemove, onCancelRemove) }
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
    val sets = removal.loggedSets
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
                        "You have never planned or logged it, so it goes completely — " +
                            "nothing refers to it."
                    ExerciseRemoval.Kind.PLANNED_NEVER_LOGGED ->
                        "You have never logged it. It goes completely, and so do the " +
                            "$copies planned ${if (copies == 1) "copy" else "copies"} " +
                            "still sitting in your weeks."
                    ExerciseRemoval.Kind.LOGGED ->
                        "You have logged $sets ${if (sets == 1) "set" else "sets"} of this, " +
                            "so it is kept. It stops being offered when you plan, and " +
                            "everything already scheduled or logged stays exactly as it is — " +
                            "including in previous results."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    when (removal.kind) {
                        ExerciseRemoval.Kind.UNUSED -> "Delete"
                        ExerciseRemoval.Kind.PLANNED_NEVER_LOGGED -> "Delete it and the plans"
                        ExerciseRemoval.Kind.LOGGED -> "Remove from library"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
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
private fun LibraryRow(
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
                    if (exercise.isSampleData) {
                        Chip(
                            text = "SAMPLE",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
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
    sample: Boolean = false,
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
    isSampleData = sample,
    deletedAtEpochMs = if (retired) 1_700_000_000_000 else null,
)

private val previewLibrary = listOf(
    previewExercise("Back squat", ExerciseCategory.STRENGTH_CONDITIONING, reps = 5),
    previewExercise("Bouldering session", ExerciseCategory.OPEN_CLIMBING, sets = 1, reps = null, seconds = 5400, unit = null),
    previewExercise("Couch stretch", ExerciseCategory.FLEXIBILITY, sets = 2, reps = null, seconds = 90, unit = null),
    previewExercise("Dumbbell row", ExerciseCategory.STRENGTH_CONDITIONING),
    previewExercise("Max hangs 20 mm", ExerciseCategory.STRENGTH_CONDITIONING, sets = 5, reps = null, seconds = 10),
    previewExercise("Pullups", ExerciseCategory.STRENGTH_CONDITIONING, sets = 4, reps = 4, unit= "kg", sample=true),
)

@Preview(name = "Library · populated", showBackground = true, heightDp = 760)
@Composable
private fun LibraryPopulatedPreview() {
    MeleteTheme {
        LibraryScreen(
            title = "Library",
            subtitle = null,
            exercises = previewLibrary,
            emptyMessage = "",
            query = "",
            onRowClick = {},
            onNewExercise = {},
            onBack = null,
        )
    }
}

@Preview(name = "Library · search results", showBackground = true, heightDp = 760)
@Composable
private fun LibrarySearchPreview() {
    MeleteTheme {
        LibraryScreen(
            title = "Library",
            subtitle = null,
            exercises = previewLibrary.filter { it.name.contains("ha", ignoreCase = true) },
            emptyMessage = "",
            query = "ha",
            onRowClick = {},
            onNewExercise = {},
            onBack = null,
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
            title = "Previously trained",
            subtitle = "Removed from the library",
            exercises = listOf(previewExercise("Pull-up", ExerciseCategory.STRENGTH_CONDITIONING, retired = true)),
            emptyMessage = "",
            onRowClick = {},
            onNewExercise = {},
            onBack = {},
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
