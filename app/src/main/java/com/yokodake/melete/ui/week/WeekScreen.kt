package com.yokodake.melete.ui.week

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.yokodake.melete.ui.components.Chip
import com.yokodake.melete.ui.components.trimNumber
import com.yokodake.melete.ui.components.PlanTarget
import com.yokodake.melete.ui.components.PlanTargetDialog
import kotlinx.coroutines.launch
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.BuildConfig
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.theme.MeleteTheme
import com.yokodake.melete.ui.theme.doneColors
import java.time.LocalDate

@Composable
fun WeekRoute(
    onOpenOccurrence: (String) -> Unit,
    onAddExercise: (weekStart: LocalDate, trainingDate: LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    viewModel: WeekViewModel = viewModel(factory = WeekViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    WeekScreen(
        state = state,
        message = message,
        onMessageShown = viewModel::consumeMessage,
        onRemoveOccurrence = viewModel::removeOccurrence,
        onDeleteWithLog = viewModel::deleteOccurrenceAndLog,
        onLoggedSetCount = viewModel::loggedSetCount,
        onMoveOccurrence = viewModel::moveOccurrence,
        onDuplicateOccurrence = viewModel::duplicateOccurrence,
        onReorderOccurrence = viewModel::reorderOccurrence,
        onPreviousWeek = viewModel::showPreviousWeek,
        onNextWeek = viewModel::showNextWeek,
        onCurrentWeek = viewModel::showCurrentWeek,
        onSeedSampleData = viewModel::seedSampleData,
        onClearSampleData = viewModel::clearSampleData,
        onOpenOccurrence = onOpenOccurrence,
        onAddExercise = { date -> onAddExercise(state.weekStart, date) },
        bottomBar = bottomBar,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    state: WeekUiState,
    message: String? = null,
    onMessageShown: () -> Unit = {},
    onRemoveOccurrence: (String) -> Unit = {},
    onDeleteWithLog: (String, Int) -> Unit = { _, _ -> },
    onLoggedSetCount: suspend (String) -> Int = { 0 },
    onMoveOccurrence: (String, LocalDate, LocalDate?) -> Unit = { _, _, _ -> },
    onDuplicateOccurrence: (String) -> Unit = {},
    onReorderOccurrence: (String, Int) -> Unit = { _, _ -> },
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onSeedSampleData: () -> Unit,
    onClearSampleData: () -> Unit,
    onOpenOccurrence: (String) -> Unit,
    onAddExercise: (LocalDate?) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rows = remember(state) { state.toRows() }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var planning by remember { mutableStateOf<PlanAction?>(null) }
    var removing by remember { mutableStateOf<PlannedOccurrence?>(null) }
    var removingSets by remember { mutableIntStateOf(0) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    // Open near today without hiding the unscheduled section: the heading for today goes to the
    // top of the list, one short scroll away from the items that have no date yet.
    LaunchedEffect(state.weekStart) {
        val todayIndex = rows.indexOfFirst { it is WeekRow.DayHeading && it.isToday }
        listState.scrollToItem(if (todayIndex >= 0) todayIndex else 0)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                // An opaque container so that list rows scrolling underneath are hidden by the
                // bar instead of appearing cut off against an identical background.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Column {
                        Text(
                            text = if (state.isCurrentWeek) "This week" else "Week",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = state.weekLabel,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onPreviousWeek,
                        modifier = Modifier.semantics { contentDescription = "Previous week" },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    if (!state.isCurrentWeek) {
                        TextButton(onClick = onCurrentWeek) { Text("Today") }
                    }
                    IconButton(
                        onClick = onNextWeek,
                        modifier = Modifier.semantics { contentDescription = "Next week" },
                    ) {
                        Text("›", style = MaterialTheme.typography.headlineMedium)
                    }
                    if (BuildConfig.DEBUG) {
                        DeveloperMenu(
                            sampleDataPresent = state.sampleDataPresent,
                            onSeedSampleData = onSeedSampleData,
                            onClearSampleData = onClearSampleData,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = rows, key = { it.key }) { row ->
                when (row) {
                    is WeekRow.SectionHeading -> SectionHeading(
                        title = row.title,
                        subtitle = row.subtitle,
                        onAdd = { onAddExercise(null) },
                    )

                    is WeekRow.DayHeading -> DayHeading(
                        row = row,
                        onAdd = { onAddExercise(row.date) },
                    )

                    is WeekRow.Occurrence -> OccurrenceCard(
                        occurrence = row.occurrence,
                        onClick = { onOpenOccurrence(row.occurrence.id) },
                        onMove = {
                            // Moving carries the log with it, so the dialog has to know whether
                            // there is one before it can word itself honestly.
                            scope.launch {
                                planning = PlanAction(
                                    occurrence = row.occurrence,
                                    loggedSets = onLoggedSetCount(row.occurrence.id),
                                )
                            }
                        },
                        // A duplicate is a fresh plan and never inherits what was logged.
                        onDuplicate = { onDuplicateOccurrence(row.occurrence.id) },
                        onReorder = { onReorderOccurrence(row.occurrence.id, it) },
                        onRemove = {
                            // Ask the record what a deletion would cost before offering one.
                            scope.launch {
                                removingSets = onLoggedSetCount(row.occurrence.id)
                                removing = row.occurrence
                            }
                        },
                    )

                    is WeekRow.Hint -> Hint(row.text)
                }
            }
        }
    }

    planning?.let { action ->
        val occurrence = action.occurrence
        PlanTargetDialog(
            title = if (action.loggedSets > 0) {
                "Move ${occurrence.name} and its ${action.loggedSets} logged " +
                    "${if (action.loggedSets == 1) "set" else "sets"} to"
            } else {
                "Move ${occurrence.name} to"
            },
            initial = PlanTarget(state.weekStart, occurrence.trainingDate),
            confirmLabel = "Move",
            today = state.today,
            // Trained work belongs to a day, so "anytime this week" is not on offer for it.
            allowUnscheduled = action.loggedSets == 0,
            onConfirm = { target ->
                planning = null
                onMoveOccurrence(occurrence.id, target.weekStart, target.trainingDate)
            },
            onDismiss = { planning = null },
        )
    }

    removing?.let { occurrence ->
        RemoveDialog(
            occurrence = occurrence,
            loggedSets = removingSets,
            onDismiss = { removing = null },
            onRemovePlan = {
                removing = null
                onRemoveOccurrence(occurrence.id)
            },
            onDeleteWithLog = {
                removing = null
                onDeleteWithLog(occurrence.id, removingSets)
            },
        )
    }
}

/** A move or a copy waiting for somewhere to go. */
/**
 * A move waiting on a destination.
 *
 * [loggedSets] is what it would carry with it, counted when the dialog opens.
 */
private data class PlanAction(
    val occurrence: PlannedOccurrence,
    val loggedSets: Int = 0,
)

@Composable
private fun DeveloperMenu(
    sampleDataPresent: Boolean,
    onSeedSampleData: () -> Unit,
    onClearSampleData: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        modifier = Modifier.semantics { contentDescription = "Developer actions" },
    ) {
        Text("⋮", style = MaterialTheme.typography.titleLarge)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Insert sample data in this week") },
            onClick = {
                expanded = false
                onSeedSampleData()
            },
        )
        DropdownMenuItem(
            text = { Text("Remove all sample data") },
            enabled = sampleDataPresent,
            onClick = {
                expanded = false
                onClearSampleData()
            },
        )
    }
}

/**
 * What removing a placement would actually destroy, said before it happens.
 *
 * With nothing logged this is an ordinary confirmation. With sets recorded against it there is no
 * quiet option: the dialog names how many, and the only button that removes them says so. History
 * never leaves through a yes that did not mention it.
 */
@Composable
private fun RemoveDialog(
    occurrence: PlannedOccurrence,
    loggedSets: Int,
    onDismiss: () -> Unit,
    onRemovePlan: () -> Unit,
    onDeleteWithLog: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (loggedSets == 0) "Remove ${occurrence.name}?" else "This has been trained")
        },
        text = {
            Text(
                if (loggedSets == 0) {
                    "It leaves the plan. Nothing has been logged against it, so nothing is lost."
                } else {
                    "$loggedSets recorded set${if (loggedSets == 1) "" else "s"} " +
                        "belong${if (loggedSets == 1) "s" else ""} to this. Removing it from the " +
                        "plan would take them with it, because they are filed under this " +
                        "placement. Keeping it costs nothing."
                }
            )
        },
        confirmButton = {
            if (loggedSets == 0) {
                TextButton(onClick = onRemovePlan) { Text("Remove") }
            } else {
                TextButton(onClick = onDeleteWithLog) {
                    Text(
                        text = "Delete it and $loggedSets set${if (loggedSets == 1) "" else "s"}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}

@Composable
private fun AddButton(description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text("+", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String?, onAdd: () -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AddButton("Add an exercise without a date", onAdd)
        }
        HorizontalDivider()
    }
}

@Composable
private fun DayHeading(row: WeekRow.DayHeading, onAdd: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = WeekMath.dayLabel(row.date),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (row.isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = if (row.isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (row.isToday) {
                Chip("Today", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(modifier = Modifier.weight(1f))
            AddButton("Add an exercise to ${WeekMath.dayLabel(row.date)}", onAdd)
        }
        HorizontalDivider(
            color = if (row.isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
    }
}

/**
 * One planned exercise. A tap opens what it is; a long press is the only way to take it back out
 * of the week, which is deliberate — unplanning training should cost a moment of intent, not a
 * mis-hit on a button that sits next to everything else.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OccurrenceCard(
    occurrence: PlannedOccurrence,
    onClick: () -> Unit,
    onMove: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onReorder: (Int) -> Unit = {},
    onRemove: () -> Unit,
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
                    onLongClickLabel = "Workout actions",
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryDot(occurrence.category)
                    // One weighted child only. Weights split the space left over after the
                    // unweighted children, so a second one here capped the name at half the row
                    // however little the chips actually needed.
                    Text(
                        text = occurrence.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (occurrence.isSampleData) {
                        Chip(
                            text = "SAMPLE",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    when (occurrence.state) {
                        OccurrenceState.PLANNED -> Unit
                        OccurrenceState.COMPLETED -> {
                            val (container, content) = doneColors()
                            Chip(text = "Done", container = container, content = content)
                        }

                        OccurrenceState.SKIPPED -> Chip(
                            text = "Skipped",
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = PrescriptionSummary.format(occurrence),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // What it actually took. The number worth seeing at a glance when looking back
                // over a week, so it is the one thing on the card set in bold.
                if (occurrence.state == OccurrenceState.COMPLETED && occurrence.maxLoad != null) {
                    Text(
                        text = buildString {
                            append(trimNumber(occurrence.maxLoad))
                            occurrence.measurementUnit?.let { append(" ").append(it) }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                occurrence.comment?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Move to…") },
                onClick = { menuExpanded = false; onMove() },
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                onClick = { menuExpanded = false; onDuplicate() },
            )
            DropdownMenuItem(
                text = { Text("Move up") },
                onClick = { menuExpanded = false; onReorder(-1) },
            )
            DropdownMenuItem(
                text = { Text("Move down") },
                onClick = { menuExpanded = false; onReorder(1) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Remove workout") },
                onClick = { menuExpanded = false; onRemove() },
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/** Flattened list rows, so that "scroll near today" is a plain list index. */
sealed interface WeekRow {
    val key: String

    data class SectionHeading(
        override val key: String,
        val title: String,
        val subtitle: String?,
    ) : WeekRow

    data class DayHeading(val date: LocalDate, val isToday: Boolean) : WeekRow {
        override val key: String get() = "day-$date"
    }

    data class Occurrence(val occurrence: PlannedOccurrence) : WeekRow {
        override val key: String get() = "occurrence-${occurrence.id}"
    }

    data class Hint(override val key: String, val text: String) : WeekRow
}

private fun WeekUiState.toRows(): List<WeekRow> = buildList {
    add(
        WeekRow.SectionHeading(
            key = "heading-unscheduled",
            title = "Unscheduled",
            subtitle = "Anytime this week",
        )
    )
    if (unscheduled.isEmpty()) {
        add(WeekRow.Hint("hint-unscheduled", "Nothing waiting without a date."))
    } else {
        unscheduled.forEach { add(WeekRow.Occurrence(it)) }
    }
    days.forEach { day ->
        add(WeekRow.DayHeading(day.date, day.isToday))
        if (day.items.isEmpty()) {
            add(WeekRow.Hint("hint-${day.date}", "Nothing planned."))
        } else {
            day.items.forEach { add(WeekRow.Occurrence(it)) }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun WeekScreenPreview() {
    val monday = LocalDate.of(2026, 9, 21)

    fun sample(
        name: String,
        date: LocalDate?,
        unilateral: Boolean = false,
        category: ExerciseCategory? = ExerciseCategory.CONDITIONING,
    ) = PlannedOccurrence(
        id = "$name-$date",
        exerciseId = name,
        name = name,
        mode = ExerciseMode.REPETITIONS,
        unilateral = unilateral,
        measurementUnit = "kg",
        measurementMeaning = MeasurementMeaning.TOTAL_LOAD,
        category = category,
        trainingDate = date,
        weekStart = monday,
        prescriptionId = null,
        prescription = PrescriptionPayload(
            sets = 4,
            targetReps = 8,
            restSeconds = 90,
            measurement = Measurement(22.5, "kg", MeasurementMeaning.TOTAL_LOAD),
            rir = 2,
        ),
        prescriptionUnreadable = false,
        state = OccurrenceState.PLANNED,
        comment = null,
        orderIndex = 0,
        isSampleData = true,
    )

    MeleteTheme {
        WeekScreen(
            state = WeekUiState.build(
                weekStart = monday,
                today = monday.plusDays(2),
                occurrences = listOf(
                    sample("Mobility flow", null, category = ExerciseCategory.OPEN),
                    sample("Back squat", monday.plusDays(1)),
                    sample("Dumbbell row", monday.plusDays(2), unilateral = true),
                ),
                sampleDataPresent = true,
            ),
            onPreviousWeek = {},
            onNextWeek = {},
            onCurrentWeek = {},
            onSeedSampleData = {},
            onClearSampleData = {},
            onOpenOccurrence = {},
            onAddExercise = {},
        )
    }
}

/** A week with real traffic in it: planned, trained, and not yet touched. */
@Preview(name = "Week · busy", showBackground = true, heightDp = 900)
@Composable
private fun BusyWeekPreview() {
    val monday = LocalDate.of(2026, 9, 21)

    fun item(
        name: String,
        date: LocalDate?,
        category: ExerciseCategory?,
        order: Int = 0,
        state: OccurrenceState = OccurrenceState.PLANNED,
    ) = PlannedOccurrence(
        id = "$name-$date-$order",
        exerciseId = name,
        name = name,
        mode = ExerciseMode.REPETITIONS,
        unilateral = false,
        measurementUnit = "kg",
        measurementMeaning = MeasurementMeaning.TOTAL_LOAD,
        category = category,
        trainingDate = date,
        weekStart = monday,
        prescriptionId = null,
        prescription = PrescriptionPayload(sets = 4, targetReps = 6, restSeconds = 180),
        prescriptionUnreadable = false,
        state = state,
        comment = null,
        orderIndex = order,
        isSampleData = false,
    )

    MeleteTheme {
        WeekScreen(
            state = WeekUiState.build(
                weekStart = monday,
                today = monday.plusDays(2),
                occurrences = listOf(
                    item("Mobility flow", null, ExerciseCategory.OPEN),
                    item("Bouldering", null, ExerciseCategory.OPEN, order = 1),
                    // Trained: the card sits on the day the work actually happened, because
                    // moving it moved its sets too.
                    item(
                        "Back squat",
                        monday.plusDays(1),
                        ExerciseCategory.CONDITIONING,
                        state = OccurrenceState.COMPLETED,
                    ),
                    item("Max hangs 20 mm", monday, ExerciseCategory.CONDITIONING, order = 1),
                    item("Dumbbell row", monday.plusDays(2), ExerciseCategory.CONDITIONING),
                    item("Couch stretch", monday.plusDays(2), ExerciseCategory.FLEXIBILITY, order = 1),
                    item("very long name of thing Deadlift", monday.plusDays(4), ExerciseCategory.CONDITIONING, state= OccurrenceState.COMPLETED),
                ),
                sampleDataPresent = false,
            ),
            onPreviousWeek = {},
            onNextWeek = {},
            onCurrentWeek = {},
            onSeedSampleData = {},
            onClearSampleData = {},
            onOpenOccurrence = {},
            onAddExercise = {},
        )
    }
}

/**
 * What a long press on a planned exercise offers.
 *
 * A plain surface rather than a real DropdownMenu, which is a popup window and does not compose
 * into a preview. The items and their order are the point: the destructive one sits apart, below
 * a divider.
 */
@Preview(name = "Week · long-press menu", showBackground = true, widthDp = 280)
@Composable
private fun WeekMenuPreview() {
    MeleteTheme {
        Surface(tonalElevation = 3.dp) {
            Column {
                listOf(
                    "Move to…",
                    "Duplicate",
                    "Move up",
                    "Move down",
                ).forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
                HorizontalDivider()
                Text(
                    text = "Remove workout",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** Removing something that has been trained names what it would cost. */
@Preview(name = "Week · removing logged work", showBackground = true, heightDp = 420)
@Composable
private fun RemoveLoggedPreview() {
    val monday = LocalDate.of(2026, 9, 21)
    MeleteTheme {
        RemoveDialog(
            occurrence = PlannedOccurrence(
                id = "squat",
                exerciseId = "squat",
                name = "Back squat",
                mode = ExerciseMode.REPETITIONS,
                unilateral = false,
                measurementUnit = "kg",
                measurementMeaning = MeasurementMeaning.TOTAL_LOAD,
                category = ExerciseCategory.CONDITIONING,
                trainingDate = monday,
                weekStart = monday,
                prescriptionId = null,
                prescription = PrescriptionPayload(sets = 4, targetReps = 5),
                prescriptionUnreadable = false,
                state = OccurrenceState.COMPLETED,
                comment = null,
                orderIndex = 0,
                isSampleData = false,
            ),
            loggedSets = 4,
            onDismiss = {},
            onRemovePlan = {},
            onDeleteWithLog = {},
        )
    }
}
