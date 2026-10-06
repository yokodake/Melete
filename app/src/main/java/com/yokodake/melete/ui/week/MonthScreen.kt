package com.yokodake.melete.ui.week

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.BenchmarkRepository
import com.yokodake.melete.data.DiaryRepository
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.Tracker
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.Chip
import com.yokodake.melete.ui.components.VariationChip
import com.yokodake.melete.ui.components.trimNumber
import com.yokodake.melete.ui.theme.categoryColor
import com.yokodake.melete.ui.theme.doneColors
import com.yokodake.melete.ui.theme.progressColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** One month of the calendar: every day of it, built exactly as the week builds its days. */
data class MonthUiState(
    val month: YearMonth,
    val today: LocalDate,
    val days: List<DaySection> = emptyList(),
    val trackers: List<Tracker> = emptyList(),
    val loading: Boolean = true,
) {
    val isCurrentMonth: Boolean get() = month == YearMonth.from(today)
}

/** What a day cell shows: a category, and whether any of it was done. */
data class DayMarker(val category: ExerciseCategory?, val done: Boolean)

object MonthView {

    /** The Mondays of every week the month touches, first to last. */
    fun weekStarts(month: YearMonth): List<LocalDate> =
        generateSequence(WeekMath.weekStartOf(month.atDay(1))) { it.plusWeeks(1) }
            .takeWhile { it <= month.atEndOfMonth() }
            .toList()

    /** True when the item holds a record: done, or with sets, or skipped on purpose. */
    private fun PlannedOccurrence.logged(): Boolean =
        state == OccurrenceState.COMPLETED || loggedSets > 0

    /**
     * The items worth showing: everything, or with [loggedOnly] only what was logged — plans
     * left out, a circuit or module kept for the parts that were done.
     */
    fun visible(items: List<WeekItem>, loggedOnly: Boolean): List<WeekItem> {
        if (!loggedOnly) return items
        return items.mapNotNull { item ->
            when (item) {
                is WeekItem.Single -> item.takeIf { it.occurrence.logged() }
                is WeekItem.Circuit -> item.takeIf { c -> c.stations.any { it.logged() } }
                is WeekItem.Module -> visible(item.members, true).takeIf { it.isNotEmpty() }
                    ?.let { item.copy(members = it) }
            }
        }
    }

    /**
     * A day's markers: one per category, in the order the day lists them, filled when any of that
     * category was done. Skipped work marks nothing.
     */
    fun markers(items: List<WeekItem>): List<DayMarker> {
        val seen = LinkedHashMap<ExerciseCategory?, Boolean>()
        fun add(category: ExerciseCategory?, done: Boolean) {
            seen[category] = (seen[category] ?: false) || done
        }
        fun walk(item: WeekItem) {
            when (item) {
                is WeekItem.Single -> item.occurrence.takeIf { it.state != OccurrenceState.SKIPPED }
                    ?.let { add(it.category, it.state == OccurrenceState.COMPLETED) }
                is WeekItem.Circuit -> item.stations.takeIf { s -> s.any { it.state != OccurrenceState.SKIPPED } }
                    ?.let { add(item.circuit.category, item.completed) }
                is WeekItem.Module -> item.members.forEach(::walk)
            }
        }
        items.forEach(::walk)
        return seen.map { (category, done) -> DayMarker(category, done) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModel(
    private val repository: TrainingRepository,
    private val diary: DiaryRepository,
    private val benchmarks: BenchmarkRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now()

    private val month = savedStateHandle.getStateFlow(MONTH_KEY, YearMonth.from(today).toString())

    val uiState: StateFlow<MonthUiState> = month
        .flatMapLatest { text ->
            val shown = YearMonth.parse(text)
            val starts = MonthView.weekStarts(shown)
            val from = starts.first()
            val to = starts.last().plusDays(6)
            val weeks = starts.map { start ->
                combine(
                    repository.observeWeek(start),
                    repository.observeWeekCircuits(start),
                    repository.observeWeekModules(start),
                ) { occurrences, circuits, modules -> Triple(start, occurrences, circuits) to modules }
            }
            combine(
                combine(weeks) { it.toList() },
                diary.observeDays(from, to),
                diary.observeTrackers(),
                benchmarks.observeDayResults(from, to),
            ) { built, days, trackers, results ->
                val now = today
                val sections = built.flatMap { (week, modules) ->
                    val (start, occurrences, circuits) = week
                    WeekUiState.build(start, now, occurrences, circuits, modules, days, trackers, results).days
                }.filter { YearMonth.from(it.date) == shown }
                MonthUiState(shown, now, sections, trackers, loading = false)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MonthUiState(YearMonth.parse(month.value), today),
        )

    fun showMonth(value: YearMonth) {
        savedStateHandle[MONTH_KEY] = value.toString()
    }

    fun setSkipped(occurrences: List<PlannedOccurrence>, skipped: Boolean) {
        viewModelScope.launch { repository.setSkipped(occurrences, skipped) }
    }

    fun unlog(occurrences: List<PlannedOccurrence>) {
        viewModelScope.launch { repository.unlog(occurrences) }
    }

    fun addActivity(name: String, trainingDate: LocalDate, minutes: Int?) {
        viewModelScope.launch {
            repository.createOneOffActivity(
                name = name,
                weekStart = WeekMath.weekStartOf(trainingDate),
                trainingDate = trainingDate,
                plannedDurationSeconds = minutes?.takeIf { it > 0 }?.let { it * 60 },
            )
        }
    }

    companion object {
        private const val MONTH_KEY = "calendarMonth"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                MonthViewModel(
                    application.container.trainingRepository,
                    application.container.diaryRepository,
                    application.container.benchmarkRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}

@Composable
fun MonthRoute(
    /** The month to open on when there is none yet: the one the week was showing. */
    initialDate: LocalDate,
    onShowWeek: (LocalDate) -> Unit,
    onOpenOccurrence: (String) -> Unit,
    onOpenCircuit: (String) -> Unit,
    onAddExercise: (weekStart: LocalDate, date: LocalDate) -> Unit,
    onOpenDiary: (LocalDate) -> Unit,
    onOpenBenchmark: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
    onLog: (occurrenceId: String) -> Unit = {},
    onReviewCircuit: (circuitInstanceId: String) -> Unit = {},
    viewModel: MonthViewModel = viewModel(factory = MonthViewModel.Factory),
) {
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!opened) {
            viewModel.showMonth(YearMonth.from(initialDate))
            opened = true
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MonthScreen(
        state = state,
        initialDate = initialDate,
        onMonth = viewModel::showMonth,
        onShowWeek = onShowWeek,
        onOpenOccurrence = onOpenOccurrence,
        onOpenCircuit = onOpenCircuit,
        onAddExercise = onAddExercise,
        onAddActivity = viewModel::addActivity,
        onOpenDiary = onOpenDiary,
        onOpenBenchmark = onOpenBenchmark,
        bottomBar = bottomBar,
        actions = MonthItemActions(
            onOpenOccurrence = onOpenOccurrence,
            onOpenCircuit = onOpenCircuit,
            onLog = onLog,
            onReviewCircuit = onReviewCircuit,
            onSkip = viewModel::setSkipped,
            onUnlog = viewModel::unlog,
        ),
    )
}

/** What a line of the month can be asked: open it, log or unlog it, skip or unskip it. */
data class MonthItemActions(
    val onOpenOccurrence: (String) -> Unit = {},
    val onOpenCircuit: (String) -> Unit = {},
    val onLog: (String) -> Unit = {},
    val onReviewCircuit: (String) -> Unit = {},
    val onSkip: (List<PlannedOccurrence>, Boolean) -> Unit = { _, _ -> },
    val onUnlog: (List<PlannedOccurrence>) -> Unit = {},
)

/**
 * The month: a grid of days with a mark per category, above the month's days in order. Tapping a
 * day selects it and moves the list there; the grid folds to the selected week to give the list
 * the screen. "Logged" hides plans and keeps what was done, the diary and benchmark results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    state: MonthUiState,
    initialDate: LocalDate,
    onMonth: (YearMonth) -> Unit,
    onShowWeek: (LocalDate) -> Unit,
    onOpenOccurrence: (String) -> Unit,
    onOpenCircuit: (String) -> Unit,
    onAddExercise: (LocalDate, LocalDate) -> Unit,
    onAddActivity: (String, LocalDate, Int?) -> Unit,
    onOpenDiary: (LocalDate) -> Unit,
    onOpenBenchmark: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
    actions: MonthItemActions = MonthItemActions(onOpenOccurrence = onOpenOccurrence, onOpenCircuit = onOpenCircuit),
) {
    var selectedDay by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var loggedOnly by rememberSaveable { mutableStateOf(false) }
    var addingActivityOn by remember { mutableStateOf<LocalDate?>(null) }
    // What "Unlog" would take the log off, and its name, while that is being confirmed.
    var unlogging by remember { mutableStateOf<Pair<String, List<PlannedOccurrence>>?>(null) }
    val itemActions = actions.copy(onUnlog = { list ->
        val name = list.singleOrNull()?.name ?: "this circuit"
        unlogging = name to list
    })
    val selected = LocalDate.ofEpochDay(selectedDay)
    val listState = rememberLazyListState()

    // A month that does not hold the selected day selects today in it, or its first day.
    LaunchedEffect(state.month) {
        if (YearMonth.from(selected) != state.month) {
            selectedDay = (if (state.isCurrentMonth) state.today else state.month.atDay(1)).toEpochDay()
        }
    }
    val days = state.days.map { it.copy(items = MonthView.visible(it.items, loggedOnly)) }
    // The list's index of each day heading, so selecting a day can scroll to it.
    val headingIndex = remember(days) {
        var index = 1 // the filter row
        days.associate { day ->
            val at = index
            index += 1 + day.items.size
            day.date to at
        }
    }
    LaunchedEffect(selectedDay, state.loading, loggedOnly) {
        if (!state.loading) headingIndex[selected]?.let { listState.animateScrollToItem(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    CalendarTitle(
                        title = "Month",
                        subtitle = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                        month = true,
                        onWeek = { onShowWeek(selected) },
                        onMonth = {},
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onMonth(state.month.minusMonths(1)) },
                        modifier = Modifier.semantics { contentDescription = "Previous month" },
                    ) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                },
                actions = {
                    if (!state.isCurrentMonth || selected != state.today) {
                        TextButton(onClick = {
                            onMonth(YearMonth.from(state.today))
                            selectedDay = state.today.toEpochDay()
                        }) { Text("Today") }
                    }
                    IconButton(
                        onClick = { onMonth(state.month.plusMonths(1)) },
                        modifier = Modifier.semantics { contentDescription = "Next month" },
                    ) { Text("›", style = MaterialTheme.typography.headlineMedium) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MonthGrid(
                month = state.month,
                today = state.today,
                selected = selected,
                collapsed = collapsed,
                markers = days.associate { it.date to MonthView.markers(it.items) },
                onSelect = { selectedDay = it.toEpochDay() },
            )
            // The handle: folds the grid to the selected week, or opens it again.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { collapsed = !collapsed }
                    .padding(vertical = 2.dp)
                    .semantics { contentDescription = if (collapsed) "Show the whole month" else "Fold the month" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (collapsed) "▾" else "▴",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item(key = "filter") {
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(
                            selected = loggedOnly,
                            onClick = { loggedOnly = !loggedOnly },
                            label = { Text("Logged only") },
                        )
                    }
                }
                days.forEach { day ->
                    item(key = "day-${day.date}") {
                        DayHeading(
                            row = WeekRow.DayHeading(day.date, day.isToday, day.diary, day.benchmarks),
                            trackers = state.trackers,
                            onAddExercise = { onAddExercise(WeekMath.weekStartOf(day.date), day.date) },
                            onAddActivity = { addingActivityOn = day.date },
                            onOpenDiary = { onOpenDiary(day.date) },
                            onOpenBenchmark = onOpenBenchmark,
                        )
                    }
                    day.items.forEach { item ->
                        item(key = item.key) {
                            MonthItem(item, itemActions)
                        }
                    }
                }
            }
        }
    }

    unlogging?.let { (name, occurrences) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { unlogging = null },
            title = { Text("Unlog $name?") },
            text = { Text("Its logged sets, notes, effort and duration are deleted. It stays in the calendar as planned.") },
            confirmButton = {
                TextButton(onClick = {
                    unlogging = null
                    actions.onUnlog(occurrences)
                }) { Text("Unlog", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { unlogging = null }) { Text("Cancel") } },
        )
    }

    addingActivityOn?.let { date ->
        ActivityDialog(
            date = date,
            onDismiss = { addingActivityOn = null },
            onConfirm = { name, minutes ->
                addingActivityOn = null
                onAddActivity(name, date, minutes)
            },
        )
    }
}

/**
 * The calendar's title: what is shown, and the period as a button that switches straight to the
 * other view — the week's dates open the month, the month's name goes back to the week.
 */
@Composable
internal fun CalendarTitle(
    title: String,
    subtitle: String,
    month: Boolean,
    onWeek: () -> Unit,
    onMonth: () -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        androidx.compose.material3.Surface(
            onClick = { if (month) onWeek() else onMonth() },
            shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.semantics {
                contentDescription = "$subtitle. Show the ${if (month) "week" else "month"}"
            },
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** The month's days, Monday first, with a mark per category; folded, only the selected week. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    collapsed: Boolean,
    markers: Map<LocalDate, List<DayMarker>>,
    onSelect: (LocalDate) -> Unit,
) {
    val weeks = MonthView.weekStarts(month)
    val shownWeeks = if (collapsed) weeks.filter { selected in it..it.plusDays(6) }.ifEmpty { weeks.take(1) } else weeks
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        shownWeeks.forEach { start ->
            Row {
                (0L..6L).forEach { offset ->
                    val date = start.plusDays(offset)
                    Box(modifier = Modifier.weight(1f)) {
                        if (YearMonth.from(date) == month) {
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selected,
                                markers = markers[date].orEmpty(),
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One day: its number, ringed in blue when it is today and filled when it is selected, and up
 * to two category marks — filled for done, hollow for planned — then a "+" when there are more.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    markers: List<DayMarker>,
    onClick: () -> Unit,
) {
    val blue = progressColor()
    val outline = MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics {
                contentDescription = WeekMath.dayLabel(date) +
                    (if (isToday) ", today" else "") +
                    (if (markers.isEmpty()) ", nothing" else ", ${markers.size} categories")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                .then(if (isToday) Modifier.border(1.5.dp, blue, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            modifier = Modifier.height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            markers.take(2).forEach { marker ->
                val color = marker.category?.let { categoryColor(it) } ?: outline
                Canvas(Modifier.size(7.dp)) {
                    if (marker.done) {
                        drawCircle(color)
                    } else {
                        drawCircle(color, radius = size.minDimension / 2 - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
            if (markers.size > 2) {
                Text("+", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A compact line for one item of a day; a module lists its members under its name. */
@Composable
private fun MonthItem(
    item: WeekItem,
    actions: MonthItemActions,
    indent: Boolean = false,
) {
    when (item) {
        is WeekItem.Single -> {
            val o = item.occurrence
            val logged = o.state == OccurrenceState.COMPLETED || o.loggedSets > 0
            MonthLine(
                category = o.category,
                name = o.name,
                tag = o.variationTag,
                state = o.state,
                optional = o.optional,
                summary = if (o.state == OccurrenceState.COMPLETED) {
                    listOfNotNull(
                        o.maxLoad?.let { load -> trimNumber(load) + (o.measurementUnit?.let { " $it" } ?: "") },
                        o.loggedDurationSeconds?.let(PrescriptionSummary::duration),
                    ).joinToString(" · ").ifEmpty { null }
                } else {
                    PrescriptionSummary.format(o)
                },
                indent = indent,
                onClick = { actions.onOpenOccurrence(o.id) },
                logged = logged,
                onLog = { if (logged) actions.onUnlog(listOf(o)) else actions.onLog(o.id) },
                skip = when {
                    o.state == OccurrenceState.SKIPPED -> false
                    o.state == OccurrenceState.PLANNED && o.loggedSets == 0 && o.trainingDate != null -> true
                    else -> null
                },
                onSkip = { actions.onSkip(listOf(o), it) },
            )
        }

        is WeekItem.Circuit -> MonthLine(
            category = item.circuit.category,
            name = item.circuit.name,
            tag = null,
            state = when {
                item.completed -> OccurrenceState.COMPLETED
                item.stations.isNotEmpty() && item.stations.all { it.state == OccurrenceState.SKIPPED } -> OccurrenceState.SKIPPED
                else -> OccurrenceState.PLANNED
            },
            optional = false,
            summary = circuitSummary(item),
            indent = indent,
            onClick = { actions.onOpenCircuit(item.circuit.id) },
            logged = item.stations.any { it.state == OccurrenceState.COMPLETED || it.loggedSets > 0 },
            onLog = {
                val logged = item.stations.any { it.state == OccurrenceState.COMPLETED || it.loggedSets > 0 }
                if (logged) actions.onUnlog(item.stations) else actions.onReviewCircuit(item.circuit.id)
            },
            skip = when {
                item.stations.isNotEmpty() && item.stations.all { it.state == OccurrenceState.SKIPPED } -> false
                item.circuit.trainingDate != null &&
                    item.stations.any { it.state == OccurrenceState.PLANNED && it.loggedSets == 0 } -> true
                else -> null
            },
            onSkip = { actions.onSkip(item.stations, it) },
        )

        is WeekItem.Module -> Column {
            Text(
                text = item.module.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            item.members.forEach { MonthItem(it, actions, indent = true) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthLine(
    category: ExerciseCategory?,
    name: String,
    tag: String?,
    state: OccurrenceState,
    optional: Boolean,
    summary: String?,
    indent: Boolean,
    onClick: () -> Unit,
    /** Whether it holds a log: the menu offers Unlog rather than Log workout. */
    logged: Boolean = false,
    onLog: () -> Unit = {},
    /** True offers Skip, false Unskip, null neither. */
    skip: Boolean? = null,
    onSkip: (Boolean) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }, onLongClickLabel = "Workout actions")
                .padding(start = if (indent) 12.dp else 0.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryDot(category)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    tag?.let { VariationChip(it) }
                }
                summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                state == OccurrenceState.COMPLETED -> {
                    val (container, content) = doneColors()
                    Chip(text = "Done", container = container, content = content)
                }
                state == OccurrenceState.SKIPPED -> Chip(
                    text = "Skipped",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                optional -> Chip(
                    text = "Optional",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The week card's first actions, the ones a look back over a month calls for.
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (logged) "Unlog" else "Log workout") },
                onClick = { menuOpen = false; onLog() },
            )
            skip?.let { skipping ->
                DropdownMenuItem(
                    text = { Text(if (skipping) "Skip" else "Unskip") },
                    onClick = { menuOpen = false; onSkip(skipping) },
                )
            }
        }
    }
}
