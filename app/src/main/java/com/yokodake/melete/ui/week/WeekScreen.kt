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
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.ui.components.CompactTextField
import com.yokodake.melete.ui.components.NumberField
import com.yokodake.melete.ui.theme.doneColors
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
    onAddCircuit: (weekStart: LocalDate, trainingDate: LocalDate?) -> Unit = { _, _ -> },
    onOpenCircuit: (String) -> Unit = {},
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
        onOpenOccurrence = onOpenOccurrence,
        onAddExercise = { date -> onAddExercise(state.weekStart, date) },
        onAddActivity = viewModel::addActivity,
        onAddCircuit = { date -> onAddCircuit(state.weekStart, date) },
        onOpenCircuit = onOpenCircuit,
        onMoveCircuit = viewModel::moveCircuit,
        onRemoveCircuit = viewModel::removeCircuit,
        onDeleteCircuitWithLog = viewModel::deleteCircuitAndLogs,
        onCircuitRecordedStations = viewModel::circuitRecordedStations,
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
    onOpenOccurrence: (String) -> Unit,
    onAddExercise: (LocalDate?) -> Unit,
    onAddActivity: (String, LocalDate?, Int?) -> Unit = { _, _, _ -> },
    onAddCircuit: (LocalDate?) -> Unit = {},
    onOpenCircuit: (String) -> Unit = {},
    onMoveCircuit: (String, LocalDate, LocalDate?) -> Unit = { _, _, _ -> },
    onRemoveCircuit: (String) -> Unit = {},
    onDeleteCircuitWithLog: (String, Int) -> Unit = { _, _ -> },
    onCircuitRecordedStations: suspend (String) -> Int = { 0 },
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
    // A LocalDate, or the sentinel for the week's undated area. Null means no dialog is open.
    var addingActivityOn by remember { mutableStateOf<LocalDate?>(null) }
    var movingCircuit by remember { mutableStateOf<WeekItem.Circuit?>(null) }
    var removingCircuit by remember { mutableStateOf<WeekItem.Circuit?>(null) }
    var removingCircuitStations by remember { mutableIntStateOf(0) }

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
                        onAddExercise = { onAddExercise(null) },
                        onAddActivity = { addingActivityOn = UndatedSlot },
                        onAddCircuit = { onAddCircuit(null) },
                    )

                    is WeekRow.DayHeading -> DayHeading(
                        row = row,
                        onAddExercise = { onAddExercise(row.date) },
                        onAddActivity = { addingActivityOn = row.date },
                        onAddCircuit = { onAddCircuit(row.date) },
                    )

                    is WeekRow.Item -> when (val item = row.item) {
                        is WeekItem.Single -> OccurrenceCard(
                            occurrence = item.occurrence,
                            onClick = { onOpenOccurrence(item.occurrence.id) },
                            onMove = {
                                // Moving carries the log with it, so the dialog has to know
                                // whether there is one before it can word itself honestly.
                                scope.launch {
                                    planning = PlanAction(
                                        occurrence = item.occurrence,
                                        loggedSets = onLoggedSetCount(item.occurrence.id),
                                    )
                                }
                            },
                            // A duplicate is a fresh plan and never inherits what was logged.
                            onDuplicate = { onDuplicateOccurrence(item.occurrence.id) },
                            onReorder = { onReorderOccurrence(item.occurrence.id, it) },
                            onRemove = {
                                // Ask the record what a deletion would cost before offering one.
                                scope.launch {
                                    removingSets = onLoggedSetCount(item.occurrence.id)
                                    removing = item.occurrence
                                }
                            },
                        )

                        is WeekItem.Circuit -> CircuitCard(
                            item = item,
                            onClick = { onOpenCircuit(item.circuit.id) },
                            onMove = { movingCircuit = item },
                            onRemove = {
                                scope.launch {
                                    removingCircuitStations =
                                        onCircuitRecordedStations(item.circuit.id)
                                    removingCircuit = item
                                }
                            },
                        )
                    }

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

    addingActivityOn?.let { slot ->
        val date = slot.takeIf { it != UndatedSlot }
        ActivityDialog(
            date = date,
            onDismiss = { addingActivityOn = null },
            onConfirm = { name, minutes ->
                addingActivityOn = null
                onAddActivity(name, date, minutes)
            },
        )
    }

    movingCircuit?.let { item ->
        PlanTargetDialog(
            title = if (item.recordedStations > 0) {
                "Move ${item.circuit.name} and its ${item.recordedStations} recorded " +
                    "${if (item.recordedStations == 1) "exercise" else "exercises"} to"
            } else {
                "Move ${item.circuit.name} to"
            },
            initial = PlanTarget(state.weekStart, item.circuit.trainingDate),
            confirmLabel = "Move",
            today = state.today,
            allowUnscheduled = item.recordedStations == 0,
            onConfirm = { target ->
                movingCircuit = null
                onMoveCircuit(item.circuit.id, target.weekStart, target.trainingDate)
            },
            onDismiss = { movingCircuit = null },
        )
    }

    removingCircuit?.let { item ->
        CircuitRemoveDialog(
            item = item,
            recorded = removingCircuitStations,
            onDismiss = { removingCircuit = null },
            onRemovePlan = {
                removingCircuit = null
                onRemoveCircuit(item.circuit.id)
            },
            onDeleteWithLog = {
                removingCircuit = null
                onDeleteCircuitWithLog(item.circuit.id, removingCircuitStations)
            },
        )
    }
}

/**
 * The stand-in for "the week's undated area" in a nullable-date dialog slot.
 *
 * `null` already means "no dialog open", so the undated case needs a value of its own rather than
 * a second boolean that could disagree with the first.
 */
private val UndatedSlot: LocalDate = LocalDate.MIN

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

/**
 * The one way to put something in a slot of the week.
 *
 * Three kinds of thing can go there and they are genuinely different — a movement from the
 * library, an activity that is only a name and a duration, a saved circuit — so the plus asks
 * which rather than assuming the commonest and making the others hard to find.
 */
@Composable
private fun AddButton(
    description: String,
    onAddExercise: () -> Unit,
    onAddActivity: () -> Unit,
    onAddCircuit: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Exercise from the library") },
                onClick = {
                    expanded = false
                    onAddExercise()
                },
            )
            DropdownMenuItem(
                text = { Text("Other activity") },
                onClick = {
                    expanded = false
                    onAddActivity()
                },
            )
            DropdownMenuItem(
                text = { Text("Circuit") },
                onClick = {
                    expanded = false
                    onAddCircuit()
                },
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String?,
    onAddExercise: () -> Unit,
    onAddActivity: () -> Unit,
    onAddCircuit: () -> Unit,
) {
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
            AddButton(
                description = "Add something without a date",
                onAddExercise = onAddExercise,
                onAddActivity = onAddActivity,
                onAddCircuit = onAddCircuit,
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun DayHeading(
    row: WeekRow.DayHeading,
    onAddExercise: () -> Unit,
    onAddActivity: () -> Unit,
    onAddCircuit: () -> Unit,
) {
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
            AddButton(
                description = "Add something to ${WeekMath.dayLabel(row.date)}",
                onAddExercise = onAddExercise,
                onAddActivity = onAddActivity,
                onAddCircuit = onAddCircuit,
            )
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
                // What it actually took. The numbers worth seeing at a glance when looking back
                // over a week, so they are the one thing on the card set in bold. Time appears
                // only once it has been recorded: an estimate is not a thing that happened.
                if (occurrence.state == OccurrenceState.COMPLETED) {
                    val done = listOfNotNull(
                        occurrence.maxLoad?.let { load ->
                            buildString {
                                append(trimNumber(load))
                                occurrence.measurementUnit?.let { append(" ").append(it) }
                            }
                        },
                        occurrence.loggedDurationSeconds?.let(PrescriptionSummary::duration),
                    )
                    if (done.isNotEmpty()) {
                        Text(
                            text = done.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
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


/**
 * A scheduled circuit: one card for the whole thing, with its stations listed inside it.
 *
 * One card because a circuit is one decision — you do the whole thing or you do not — and because
 * four stations loose in a day would read as four unrelated exercises that happen to be adjacent.
 * It is not itself a piece of training: the stations are, the card only says they belong together,
 * and nothing counts it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CircuitCard(
    item: WeekItem.Circuit,
    onClick: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.circuit.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = circuitSummary(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.completed) {
                        val colors = doneColors()
                        Chip("Done", colors.first, colors.second)
                    }
                }
                item.stations.forEach { station ->
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryDot(station.category)
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (station.hasRecord) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.bodyMedium,
                                color = doneColors().first,
                            )
                        }
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Move") },
                onClick = {
                    menuOpen = false
                    onMove()
                },
            )
            DropdownMenuItem(
                text = { Text("Remove from the week") },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

/** The shape of a circuit in one line: how many times round, and what falls between. */
private fun circuitSummary(item: WeekItem.Circuit): String {
    val parts = mutableListOf<String>()
    parts += "${item.circuit.rounds} × ${item.stations.size} exercises"
    if (item.circuit.transitionSeconds > 0) {
        parts += "${PrescriptionSummary.duration(item.circuit.transitionSeconds)} between"
    }
    if (item.circuit.roundRestSeconds > 0) {
        parts += "${PrescriptionSummary.duration(item.circuit.roundRestSeconds)} per round"
    }
    if (item.recordedStations in 1 until item.stations.size) {
        parts += "${item.recordedStations} of ${item.stations.size} recorded"
    }
    return parts.joinToString(" · ")
}

/**
 * Naming an activity that is only a duration.
 *
 * Two fields and no library: the point of this path is that a climbing session or a class can be
 * put in the week in a few seconds, without first deciding whether it deserves a definition. The
 * minutes are optional, because you often add it before you have done it.
 */
@Composable
private fun ActivityDialog(
    date: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (String, Int?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Other activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = date?.let { "On ${WeekMath.dayLabel(it)}." }
                        ?: "Waiting in this week, with no date yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "What was it",
                    placeholder = "Outdoor bouldering",
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(
                    label = "Minutes (optional)",
                    value = minutes,
                    onValueChange = { minutes = it },
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                Text(
                    text = "No library entry is created. Use the library for something you will " +
                        "plan again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, minutes.toIntOrNull()) },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Taking a circuit back out of the week.
 *
 * Same rule as a single exercise: a plan can go, and a record can only go by name. The count is
 * of *exercises* with something recorded, because that is what would be destroyed — the circuit
 * container itself holds nothing.
 */
@Composable
private fun CircuitRemoveDialog(
    item: WeekItem.Circuit,
    recorded: Int,
    onDismiss: () -> Unit,
    onRemovePlan: () -> Unit,
    onDeleteWithLog: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.circuit.name) },
        text = {
            Text(
                text = if (recorded > 0) {
                    "$recorded of its ${item.stations.size} exercises have been recorded. " +
                        "Removing the circuit would destroy that too."
                } else {
                    "Nothing has been recorded here, so removing it loses only the plan."
                },
            )
        },
        confirmButton = {
            if (recorded > 0) {
                TextButton(onClick = onDeleteWithLog) {
                    Text("Delete it and the $recorded recorded")
                }
            } else {
                TextButton(onClick = onRemovePlan) { Text("Remove from the week") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } },
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

    /** One card: a standalone exercise, or a whole circuit. */
    data class Item(val item: WeekItem) : WeekRow {
        override val key: String get() = item.key
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
        unscheduled.forEach { add(WeekRow.Item(it)) }
    }
    days.forEach { day ->
        add(WeekRow.DayHeading(day.date, day.isToday))
        if (day.items.isEmpty()) {
            add(WeekRow.Hint("hint-${day.date}", "Nothing planned."))
        } else {
            day.items.forEach { add(WeekRow.Item(it)) }
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
        category: ExerciseCategory? = ExerciseCategory.STRENGTH_CONDITIONING,
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
    )

    MeleteTheme {
        WeekScreen(
            state = WeekUiState.build(
                weekStart = monday,
                today = monday.plusDays(2),
                occurrences = listOf(
                    sample("Mobility flow", null, category = ExerciseCategory.OPEN_CLIMBING),
                    sample("Back squat", monday.plusDays(1)),
                    sample("Dumbbell row", monday.plusDays(2), unilateral = true),
                ),
            ),
            onPreviousWeek = {},
            onNextWeek = {},
            onCurrentWeek = {},
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
    )

    MeleteTheme {
        WeekScreen(
            state = WeekUiState.build(
                weekStart = monday,
                today = monday.plusDays(2),
                occurrences = listOf(
                    item("Mobility flow", null, ExerciseCategory.OPEN_CLIMBING),
                    item("Bouldering", null, ExerciseCategory.OPEN_CLIMBING, order = 1),
                    // Trained: the card sits on the day the work actually happened, because
                    // moving it moved its sets too.
                    item(
                        "Back squat",
                        monday.plusDays(1),
                        ExerciseCategory.STRENGTH_CONDITIONING,
                        state = OccurrenceState.COMPLETED,
                    ),
                    item("Max hangs 20 mm", monday, ExerciseCategory.STRENGTH_CONDITIONING, order = 1),
                    item("Dumbbell row", monday.plusDays(2), ExerciseCategory.STRENGTH_CONDITIONING),
                    item("Couch stretch", monday.plusDays(2), ExerciseCategory.FLEXIBILITY, order = 1),
                    item("very long name of thing Deadlift", monday.plusDays(4), ExerciseCategory.STRENGTH_CONDITIONING, state= OccurrenceState.COMPLETED),
                ),
            ),
            onPreviousWeek = {},
            onNextWeek = {},
            onCurrentWeek = {},
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
                category = ExerciseCategory.STRENGTH_CONDITIONING,
                trainingDate = monday,
                weekStart = monday,
                prescriptionId = null,
                prescription = PrescriptionPayload(sets = 4, targetReps = 5),
                prescriptionUnreadable = false,
                state = OccurrenceState.COMPLETED,
                comment = null,
                orderIndex = 0,
            ),
            loggedSets = 4,
            onDismiss = {},
            onRemovePlan = {},
            onDeleteWithLog = {},
        )
    }
}
