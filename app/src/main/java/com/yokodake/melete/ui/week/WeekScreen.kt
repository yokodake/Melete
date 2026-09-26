package com.yokodake.melete.ui.week

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.yokodake.melete.data.DiaryDay
import com.yokodake.melete.data.Tracker
import com.yokodake.melete.data.PlanItemKind
import com.yokodake.melete.data.PlanItemRef
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
import com.yokodake.melete.ui.components.VariationChip
import com.yokodake.melete.ui.theme.MeleteTheme
import com.yokodake.melete.ui.theme.doneColors
import java.time.LocalDate

@Composable
fun WeekRoute(
    onOpenOccurrence: (String) -> Unit,
    onAddExercise: (weekStart: LocalDate, trainingDate: LocalDate?) -> Unit,
    onOpenCircuit: (String) -> Unit = {},
    onOpenDiary: (LocalDate) -> Unit = {},
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
        onDuplicateOccurrence = viewModel::duplicateOccurrence,
        onNudge = viewModel::nudge,
        onPreviousWeek = viewModel::showPreviousWeek,
        onNextWeek = viewModel::showNextWeek,
        onCurrentWeek = viewModel::showCurrentWeek,
        onOpenOccurrence = onOpenOccurrence,
        onAddExercise = { date -> onAddExercise(state.weekStart, date) },
        onAddActivity = viewModel::addActivity,
        onOpenCircuit = onOpenCircuit,
        onOpenDiary = onOpenDiary,
        onRemoveCircuit = viewModel::removeCircuit,
        onDeleteCircuitWithLog = viewModel::deleteCircuitAndLogs,
        onCircuitRecordedStations = viewModel::circuitRecordedStations,
        onTakeOutOfModule = viewModel::takeOutOfModule,
        onTakeCircuitOutOfModule = viewModel::takeCircuitOutOfModule,
        onUngroupModule = viewModel::ungroupModule,
        onRemoveModule = viewModel::removeModule,
        onDeleteModuleWithLog = viewModel::deleteModuleAndLogs,
        onModuleRecordedExercises = viewModel::moduleRecordedExercises,
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
    onDuplicateOccurrence: (String) -> Unit = {},
    /** Moves a card one place, crossing into the next day at an edge. Offered in edit mode. */
    onNudge: (PlanItemRef, Int) -> Unit = { _, _ -> },
    onOpenDiary: (LocalDate) -> Unit = {},
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onOpenOccurrence: (String) -> Unit,
    onAddExercise: (LocalDate?) -> Unit,
    onAddActivity: (String, LocalDate?, Int?) -> Unit = { _, _, _ -> },
    onOpenCircuit: (String) -> Unit = {},
    onRemoveCircuit: (String) -> Unit = {},
    onDeleteCircuitWithLog: (String, Int) -> Unit = { _, _ -> },
    onCircuitRecordedStations: suspend (String) -> Int = { 0 },
    onTakeOutOfModule: (String) -> Unit = {},
    onTakeCircuitOutOfModule: (String) -> Unit = {},
    onUngroupModule: (String) -> Unit = {},
    onRemoveModule: (String) -> Unit = {},
    onDeleteModuleWithLog: (String, Int) -> Unit = { _, _ -> },
    onModuleRecordedExercises: suspend (String) -> Int = { 0 },
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rows = remember(state) { state.toRows() }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<PlannedOccurrence?>(null) }
    var removingSets by remember { mutableIntStateOf(0) }
    // A LocalDate, or the sentinel for the week's undated area. Null means no dialog is open.
    var addingActivityOn by remember { mutableStateOf<LocalDate?>(null) }
    var removingCircuit by remember { mutableStateOf<WeekItem.Circuit?>(null) }
    var removingCircuitStations by remember { mutableIntStateOf(0) }
    // Survives rotation and a trip into an exercise and back, so reorganising is not interrupted.
    var editing by rememberSaveable { mutableStateOf(false) }
    var removingModule by remember { mutableStateOf<WeekItem.Module?>(null) }
    var removingModuleRecorded by remember { mutableIntStateOf(0) }

    /**
     * One card for one item, wherever it sits. A module draws its members with exactly this, so a
     * member keeps every action it would have on its own, plus the one way out of the group.
     */
    @Composable
    fun ItemCard(item: WeekItem, inModule: Boolean) {
        when (item) {
            is WeekItem.Single -> OccurrenceCard(
                occurrence = item.occurrence,
                onClick = { onOpenOccurrence(item.occurrence.id) },
                // A duplicate is a fresh plan and never inherits what was logged.
                onDuplicate = { onDuplicateOccurrence(item.occurrence.id) },
                onRemove = {
                    // Ask the record what a deletion would cost before offering one.
                    scope.launch {
                        removingSets = onLoggedSetCount(item.occurrence.id)
                        removing = item.occurrence
                    }
                },
                onTakeOut = { onTakeOutOfModule(item.occurrence.id) }.takeIf { inModule },
            )

            is WeekItem.Circuit -> CircuitCard(
                item = item,
                onClick = { onOpenCircuit(item.circuit.id) },
                onRemove = {
                    scope.launch {
                        removingCircuitStations = onCircuitRecordedStations(item.circuit.id)
                        removingCircuit = item
                    }
                },
                onTakeOut = { onTakeCircuitOutOfModule(item.circuit.id) }.takeIf { inModule },
            )

            is WeekItem.Module -> ModuleCard(
                item = item,
                onUngroup = { onUngroupModule(item.module.id) },
                onRemove = {
                    scope.launch {
                        removingModuleRecorded = onModuleRecordedExercises(item.module.id)
                        removingModule = item
                    }
                },
                member = { ItemCard(it, inModule = true) },
            )
        }
    }

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
                    // Reorganising is a mode you enter on purpose, so cards only move when you
                    // have said that is what you are doing — never from a stray tap while
                    // reading the week.
                    TextButton(onClick = { editing = !editing }) {
                        Text(if (editing) "Done" else "Edit")
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
                    )

                    is WeekRow.DayHeading -> DayHeading(
                        row = row,
                        trackers = state.trackers,
                        onAddExercise = { onAddExercise(row.date) },
                        onAddActivity = { addingActivityOn = row.date },
                        onOpenDiary = { onOpenDiary(row.date) },
                    )

                    is WeekRow.Item -> if (editing) {
                        NudgeRow(
                            onUp = { onNudge(row.item.ref(), -1) },
                            onDown = { onNudge(row.item.ref(), 1) },
                        ) { ItemCard(row.item, inModule = false) }
                    } else {
                        ItemCard(row.item, inModule = false)
                    }

                    is WeekRow.Hint -> Hint(row.text)
                }
            }
        }
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

    removingModule?.let { item ->
        val recorded = removingModuleRecorded
        AlertDialog(
            onDismissRequest = { removingModule = null },
            title = { Text(item.module.name) },
            text = {
                Text(
                    if (recorded > 0) {
                        "This will remove ${item.module.name} and its exercises from this week, " +
                            "including logs for $recorded ${if (recorded == 1) "exercise" else "exercises"}. " +
                            "To keep the exercises and logs, choose Ungroup."
                    } else {
                        "Remove ${item.module.name} from this week? " +
                            "This also removes its exercises and circuits. Choose Ungroup to keep them."
                    }
                )
            },
            confirmButton = {
                if (recorded > 0) {
                    TextButton(onClick = {
                        removingModule = null
                        onDeleteModuleWithLog(item.module.id, recorded)
                    }) {
                        Text(
                            text = "Delete plan and logs",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    TextButton(onClick = {
                        removingModule = null
                        onRemoveModule(item.module.id)
                    }) { Text("Remove from week") }
                }
            },
            dismissButton = { TextButton(onClick = { removingModule = null }) { Text("Cancel") } },
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

@Composable
private fun RemoveDialog(
    occurrence: PlannedOccurrence,
    loggedSets: Int,
    onDismiss: () -> Unit,
    onRemovePlan: () -> Unit,
    onDeleteWithLog: () -> Unit,
) {
    // A log is sets *or* completion: a finished activity has no sets and is still a record, and
    // judging by set count alone offered a plain removal the repository then refused.
    val logged = loggedSets > 0 || occurrence.hasRecord
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (!logged) "Remove ${occurrence.name}?" else "Delete ${occurrence.name} and its log?")
        },
        text = {
            Text(
                when {
                    !logged -> "Remove ${occurrence.name} from this week?"
                    loggedSets == 0 -> "This will delete ${occurrence.name} from your plan and its log."
                    else -> "This will delete ${occurrence.name} from your plan and its " +
                        "$loggedSets logged ${if (loggedSets == 1) "set" else "sets"}."
                }
            )
        },
        confirmButton = {
            if (!logged) {
                TextButton(onClick = onRemovePlan) { Text("Remove") }
            } else {
                TextButton(onClick = onDeleteWithLog) {
                    Text(
                        text = if (occurrence.mode == ExerciseMode.ACTIVITY) {
                            "Delete activity and log"
                        } else {
                            "Delete plan and log"
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The one way to put something in a slot of the week.
 *
 * Two kinds of thing can go there and they are genuinely different — something saved in the
 * library, exercise or circuit, and an activity that is only a name and a duration — so the plus
 * asks which rather than assuming the commonest and making the other hard to find.
 */
@Composable
private fun AddButton(
    description: String,
    onAddExercise: () -> Unit,
    onAddActivity: () -> Unit,
    onOpenDiary: (() -> Unit)? = null,
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
            // Exercises and circuits both: the picker lists either, so the plus does not have to
            // ask which kind of saved thing before you have seen what there is.
            DropdownMenuItem(
                text = { Text("From library") },
                onClick = {
                    expanded = false
                    onAddExercise()
                },
            )
            DropdownMenuItem(
                text = { Text("Add activity") },
                onClick = {
                    expanded = false
                    onAddActivity()
                },
            )
            // Only a day has a diary; the undated area is a place for plans, not for notes.
            if (onOpenDiary != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Daily notes") },
                    onClick = {
                        expanded = false
                        onOpenDiary()
                    },
                )
            }
        }
    }
}


@Composable
private fun SectionHeading(
    title: String,
    subtitle: String?,
    onAddExercise: () -> Unit,
    onAddActivity: () -> Unit,
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
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun DayHeading(
    row: WeekRow.DayHeading,
    trackers: List<Tracker>,
    onAddExercise: () -> Unit,
    onAddActivity: () -> Unit,
    onOpenDiary: () -> Unit,
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
                onOpenDiary = onOpenDiary,
            )
        }
        HorizontalDivider(
            color = if (row.isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
        row.diary?.takeIf { !it.isEmpty }?.let { DiaryLine(it, trackers, onOpenDiary) }
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
    onDuplicate: () -> Unit = {},
    onRemove: () -> Unit,
    /** Set only for a module member: leaves the group and stays where it is. */
    onTakeOut: (() -> Unit)? = null,
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
                    occurrence.variationTag?.let { VariationChip(it) }
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
                text = { Text("Duplicate") },
                onClick = { menuExpanded = false; onDuplicate() },
            )
            onTakeOut?.let { takeOut ->
                DropdownMenuItem(
                    text = { Text("Take out of module") },
                    onClick = { menuExpanded = false; takeOut() },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Remove from week") },
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
 * A scheduled circuit: one card for the whole thing, built like an exercise card — its category
 * dot and name on top, and its stations enumerated on one line underneath, in order.
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
    onRemove: () -> Unit,
    /** Set only for a module member: leaves the group and stays where it is. */
    onTakeOut: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onLongClickLabel = "Circuit actions",
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
                    CategoryDot(item.circuit.category)
                    Text(
                        text = item.circuit.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.completed) {
                        val colors = doneColors()
                        Chip("Done", colors.first, colors.second)
                    }
                }
                Text(
                    text = circuitSummary(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            onTakeOut?.let { takeOut ->
                DropdownMenuItem(
                    text = { Text("Take out of module") },
                    onClick = {
                        menuOpen = false
                        takeOut()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Remove from week") },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

/**
 * A scheduled module: its name over an outlined group of the cards it holds.
 *
 * An outline rather than another filled card, so the members read as the cards they are — each
 * opened, moved and logged on its own — sitting inside a named boundary, and the week stays
 * compact. The group has its own menu for what applies to all of it at once.
 */
@Composable
private fun ModuleCard(
    item: WeekItem.Module,
    onUngroup: () -> Unit,
    onRemove: () -> Unit,
    member: @Composable (WeekItem) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.module.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )
                if (item.completed) {
                    val colors = doneColors()
                    Chip("Done", colors.first, colors.second)
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.semantics { contentDescription = "Module actions" },
                    ) { Text("⋮", style = MaterialTheme.typography.titleMedium) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Ungroup") },
                            onClick = { menuOpen = false; onUngroup() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Remove from week") },
                            onClick = { menuOpen = false; onRemove() },
                        )
                    }
                }
            }
            if (item.members.isEmpty()) {
                Hint("No items in this module.")
            }
            item.members.forEach { member(it) }
        }
    }
}

/** The shape of a circuit in one line: how many times round, and what falls between. */
/**
 * A card with the edit-mode arrows beside it. Up past the top of a day carries it into the day
 * above, down past the bottom into the day below — the arrows never stop at a day's edge.
 */
@Composable
private fun NudgeRow(onUp: () -> Unit, onDown: () -> Unit, card: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) { card() }
        Column {
            IconButton(
                onClick = onUp,
                modifier = Modifier.semantics { contentDescription = "Move up" },
            ) { Text("↑", style = MaterialTheme.typography.titleMedium) }
            IconButton(
                onClick = onDown,
                modifier = Modifier.semantics { contentDescription = "Move down" },
            ) { Text("↓", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** The card of the week an item stands for, for moving it as a whole. */
private fun WeekItem.ref(): PlanItemRef = when (this) {
    is WeekItem.Single -> PlanItemRef(PlanItemKind.EXERCISE, occurrence.id)
    is WeekItem.Circuit -> PlanItemRef(PlanItemKind.CIRCUIT, circuit.id)
    is WeekItem.Module -> PlanItemRef(PlanItemKind.MODULE, module.id)
}

/**
 * The circuit in one line, as the library lists it: rounds and the stations in order, plus how
 * much of it has been logged when that is some but not all.
 */
private fun circuitSummary(item: WeekItem.Circuit): String {
    val parts = mutableListOf<String>()
    parts += "${item.circuit.rounds} ${if (item.circuit.rounds == 1) "round" else "rounds"}"
    parts += item.stations.joinToString(" → ") { it.name }.ifEmpty { "no exercises" }
    if (item.recordedStations in 1 until item.stations.size) {
        parts += "${item.recordedStations} of ${item.stations.size} logged"
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
        title = { Text("Add activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = date?.let { "On ${WeekMath.dayLabel(it)}." }
                        ?: "Unscheduled this week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Activity",
                    placeholder = "Outdoor bouldering",
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(
                    label = "Planned minutes (optional)",
                    value = minutes,
                    onValueChange = { minutes = it },
                    modifier = Modifier.fillMaxWidth(0.6f),
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
                    "This will remove ${item.circuit.name} and its exercises from this week, " +
                        "including logs for $recorded ${if (recorded == 1) "exercise" else "exercises"}."
                } else {
                    "Remove ${item.circuit.name} from this week?"
                },
            )
        },
        confirmButton = {
            if (recorded > 0) {
                TextButton(onClick = onDeleteWithLog) {
                    Text("Delete plan and logs")
                }
            } else {
                TextButton(onClick = onRemovePlan) { Text("Remove from week") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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

    data class DayHeading(
        val date: LocalDate,
        val isToday: Boolean,
        val diary: DiaryDay? = null,
    ) : WeekRow {
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
        add(WeekRow.Hint("hint-unscheduled", "Nothing unscheduled."))
    } else {
        unscheduled.forEach { add(WeekRow.Item(it)) }
    }
    days.forEach { day ->
        add(WeekRow.DayHeading(day.date, day.isToday, day.diary))
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
        prescription = PrescriptionPayload(
            sets = 4,
            targetReps = 8,
            restSeconds = 90,
            measurement = Measurement(22.5, "kg", MeasurementMeaning.TOTAL_LOAD),
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
                    "Duplicate",
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
