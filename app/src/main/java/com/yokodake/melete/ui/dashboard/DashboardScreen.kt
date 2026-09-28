package com.yokodake.melete.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.DetailSection
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import com.yokodake.melete.ui.theme.categoryColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DashboardUiState(
    val loading: Boolean = true,
    val range: DashboardRange = DashboardRange.TWELVE_WEEKS,
    val metric: DashboardMetric = DashboardMetric.HOURS,
    val stats: DashboardStats? = null,
    /** How many whole ranges back from the current one is shown; 0 ends today. */
    val back: Int = 0,
    val hasEarlier: Boolean = false,
) {
    /** Paging applies to every range but all time, which already reaches both ends. */
    val pageable: Boolean get() = range != DashboardRange.ALL_TIME
}

/**
 * Evidence of accumulated work over a range: time, completed exercises, and how they divide by
 * category. Only completed exercise occurrences count; benchmarks, the diary and plans never do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: TrainingRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val range = savedStateHandle.getStateFlow(RANGE_KEY, DashboardRange.TWELVE_WEEKS)
    private val metric = savedStateHandle.getStateFlow(METRIC_KEY, DashboardMetric.HOURS)
    private val back = savedStateHandle.getStateFlow(BACK_KEY, 0)

    val uiState: StateFlow<DashboardUiState> =
        combine(range, back, repository.observeFirstCompletedDate()) { r, b, first -> Triple(r, b, first) }
            .flatMapLatest { (r, b, first) ->
                val today = LocalDate.now()
                val (from, to) = DashboardStats.span(r, today, first, b)
                combine(repository.observeCompleted(from, to), metric) { completed, m ->
                    DashboardUiState(
                        loading = false,
                        range = r,
                        metric = m,
                        stats = DashboardStats.build(completed, from, to, m),
                        back = b,
                        hasEarlier = DashboardStats.hasEarlier(r, today, first, b),
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** A new length starts again from the current range. */
    fun setRange(value: DashboardRange) {
        savedStateHandle[RANGE_KEY] = value
        savedStateHandle[BACK_KEY] = 0
    }

    fun earlier() {
        if (uiState.value.hasEarlier) savedStateHandle[BACK_KEY] = back.value + 1
    }

    fun later() {
        savedStateHandle[BACK_KEY] = (back.value - 1).coerceAtLeast(0)
    }

    fun current() {
        savedStateHandle[BACK_KEY] = 0
    }

    fun setMetric(value: DashboardMetric) {
        savedStateHandle[METRIC_KEY] = value
    }

    companion object {
        private const val RANGE_KEY = "dashboardRange"
        private const val METRIC_KEY = "dashboardMetric"
        private const val BACK_KEY = "dashboardBack"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                DashboardViewModel(application.container.trainingRepository, createSavedStateHandle())
            }
        }
    }
}

@Composable
fun DashboardRoute(
    bottomBar: @Composable () -> Unit,
    onOpenRecords: (from: LocalDate, to: LocalDate, category: ExerciseCategory?, uncategorised: Boolean) -> Unit = { _, _, _, _ -> },
    onOpenExercise: (exerciseId: String, from: LocalDate, to: LocalDate) -> Unit = { _, _, _ -> },
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state, viewModel::setRange, viewModel::setMetric, bottomBar,
        onEarlier = viewModel::earlier, onLater = viewModel::later, onCurrent = viewModel::current,
        onOpenRecords = onOpenRecords, onOpenExercise = onOpenExercise,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRange: (DashboardRange) -> Unit,
    onMetric: (DashboardMetric) -> Unit,
    bottomBar: @Composable () -> Unit,
    onEarlier: () -> Unit = {},
    onLater: () -> Unit = {},
    onCurrent: () -> Unit = {},
    onOpenRecords: (from: LocalDate, to: LocalDate, category: ExerciseCategory?, uncategorised: Boolean) -> Unit = { _, _, _, _ -> },
    onOpenExercise: (exerciseId: String, from: LocalDate, to: LocalDate) -> Unit = { _, _, _ -> },
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { RangeTitle(state.range, onRange) },
                actions = { MetricMenu(state.metric, onMetric) },
            )
        },
    ) { padding ->
        val stats = state.stats ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column {
                    // The headline opens what it counts.
                    Text(
                        text = "${DashboardStats.hours(stats.totalSeconds)} · ${stats.totalCount} " +
                            (if (stats.totalCount == 1) "exercise" else "exercises") + " ›",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onOpenRecords(stats.from, stats.to, null, false) }
                            .semantics { contentDescription = "Show the records of this range" },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = spanLabel(stats.from, stats.to),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.pageable) {
                            RangePager(
                                current = state.back == 0,
                                hasEarlier = state.hasEarlier,
                                onEarlier = onEarlier,
                                onCurrent = onCurrent,
                                onLater = onLater,
                            )
                        }
                    }
                }
            }
            item {
                BarChart(
                    bars = stats.bars,
                    metric = state.metric,
                    // A bar's week or month, clipped to the range: the first and last bars can be partial.
                    onOpenBar = { bar ->
                        val end = if (bar.monthly) bar.start.plusMonths(1).minusDays(1) else bar.start.plusDays(6)
                        onOpenRecords(maxOf(bar.start, stats.from), minOf(end, stats.to), null, false)
                    },
                )
            }
            if (stats.categories.isNotEmpty()) {
                item { DetailSection("Categories") }
                items(items = stats.categories, key = { "cat-${it.category}" }) { total ->
                    TotalRow(
                        onClick = { onOpenRecords(stats.from, stats.to, total.category, total.category == null) },
                        category = total.category,
                        name = total.category?.label ?: "No category",
                        values = valuesText(total.seconds, total.count, state.metric, inferred = false) +
                            " · ${(total.share * 100).let { if (it > 0 && it < 1) "<1" else "%.0f".format(Locale.ROOT, it) }}%",
                    )
                }
            }
            if (stats.exercises.isNotEmpty()) {
                item { DetailSection("Exercises") }
                items(items = stats.exercises, key = { "ex-${it.exerciseId}" }) { total ->
                    TotalRow(
                        onClick = { onOpenExercise(total.exerciseId, stats.from, stats.to) },
                        category = total.category,
                        name = total.name,
                        values = valuesText(total.seconds, total.count, state.metric, total.includesInferred),
                    )
                }
            }
        }
    }
}

/** "Dashboard" over the range with its chevron: the one control for what period is shown. */
@Composable
private fun RangeTitle(range: DashboardRange, onRange: (DashboardRange) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .clickable { open = true }
                .semantics { contentDescription = "Range: ${range.label}. Change range" },
        ) {
            Text("Dashboard", style = MaterialTheme.typography.titleMedium)
            Text("${range.label} ▾", style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DashboardRange.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (option == range) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        open = false
                        onRange(option)
                    },
                )
            }
        }
    }
}

/**
 * ‹ • › : back one whole range, back to the current one, forward one. The bullet is filled while
 * the current range (ending today) is shown and hollow when paged away from it.
 */
@Composable
private fun RangePager(
    current: Boolean,
    hasEarlier: Boolean,
    onEarlier: () -> Unit,
    onCurrent: () -> Unit,
    onLater: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onEarlier,
            enabled = hasEarlier,
            modifier = Modifier.semantics { contentDescription = "Earlier" },
        ) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        IconButton(
            onClick = onCurrent,
            enabled = !current,
            modifier = Modifier.semantics { contentDescription = "Back to the current range" },
        ) {
            Text(
                text = if (current) "●" else "○",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        IconButton(
            onClick = onLater,
            enabled = !current,
            modifier = Modifier.semantics { contentDescription = "Later" },
        ) { Text("›", style = MaterialTheme.typography.headlineMedium) }
    }
}

/** What is measured — hours or exercises — as a small menu in the corner rather than a button row. */
@Composable
private fun MetricMenu(metric: DashboardMetric, onMetric: (DashboardMetric) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = "Measure: ${metric.label}. Change" },
        ) { Text("${metric.label} ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DashboardMetric.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (option == metric) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        open = false
                        onMetric(option)
                    },
                )
            }
        }
    }
}

/**
 * Stacked bars, one per week or month, segments in the fixed category order (so a colour always
 * means the same category, whatever the ranking). A tap selects a bar and names its period and
 * totals above the plot; without a tap the latest bar is described.
 */
@Composable
private fun BarChart(bars: List<DashboardBar>, metric: DashboardMetric, onOpenBar: (DashboardBar) -> Unit) {
    // Kept across a visit to the records, so coming back finds the bar that was chosen.
    var selected by rememberSaveable(bars.firstOrNull()?.start?.toEpochDay(), bars.size) {
        mutableStateOf(bars.lastIndex)
    }
    fun valueOf(bar: DashboardBar, category: ExerciseCategory?): Float = when (metric) {
        DashboardMetric.HOURS -> (bar.seconds[category] ?: 0).toFloat()
        DashboardMetric.EXERCISES -> (bar.counts[category] ?: 0).toFloat()
    }
    fun totalOf(bar: DashboardBar): Float =
        if (metric == DashboardMetric.HOURS) bar.totalSeconds.toFloat() else bar.totalCount.toFloat()
    val max = bars.maxOfOrNull(::totalOf)?.takeIf { it > 0f } ?: 1f
    val order: List<ExerciseCategory?> = ExerciseCategory.entries + listOf(null)
    val colors: Map<ExerciseCategory?, Color> =
        ExerciseCategory.entries.associateWith { categoryColor(it) } +
            mapOf<ExerciseCategory?, Color>(null to MaterialTheme.colorScheme.outline)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.onSurface

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        bars.getOrNull(selected)?.let { bar ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onOpenBar(bar) }
                    .semantics { contentDescription = "Show the records of ${periodLabel(bar)}" },
            ) {
                Text(
                    text = periodLabel(bar),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${DashboardStats.hours(bar.totalSeconds)} · ${bar.totalCount} ›",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(160.dp)
                    .pointerInput(bars) {
                        detectTapGestures { offset ->
                            if (bars.isNotEmpty()) {
                                val slot = size.width.toFloat() / bars.size
                                selected = (offset.x / slot).toInt().coerceIn(0, bars.lastIndex)
                            }
                        }
                    }
                    .semantics {
                        contentDescription = "Stacked bars by category, one per " +
                            if (bars.firstOrNull()?.monthly == true) "month" else "week"
                    },
            ) {
                if (bars.isEmpty()) return@Canvas
                val gap = 2.dp.toPx()
                val slot = size.width / bars.size
                val barWidth = (slot * 0.7f).coerceAtMost(28.dp.toPx())
                // One recessive line at the top of the scale, and the baseline.
                drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
                // Each segment keeps at least 4dp of colour above its 2dp gap, borrowed from the
                // bar's largest segment so the bar's total height stays true.
                val minimum = 4.dp.toPx() + gap
                bars.forEachIndexed { index, bar ->
                    val left = index * slot + (slot - barWidth) / 2
                    var bottom = size.height
                    val values = order.map { valueOf(bar, it) }
                    val heights = DashboardStats.segmentHeights(values, size.height * totalOf(bar) / max, minimum)
                    order.forEachIndexed { position, category ->
                        val height = heights[position]
                        if (height <= 0f) return@forEachIndexed
                        val top = bottom - height
                        // Each segment stops 2px short of the one above, leaving a surface gap.
                        drawRoundRect(
                            color = colors.getValue(category),
                            topLeft = Offset(left, top + gap),
                            size = Size(barWidth, (height - gap).coerceAtLeast(1f)),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                        bottom = top
                    }
                    if (index == selected && totalOf(bar) > 0f) {
                        drawLine(
                            color = highlight,
                            start = Offset(left, size.height + 0f),
                            end = Offset(left + barWidth, size.height + 0f),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .height(160.dp)
                    .padding(start = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(scaleLabel(max, metric), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (bars.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(axisLabel(bars.first()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (bars.size > 1) {
                    Text(axisLabel(bars.last()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(28.dp))
            }
        }
    }
}

/** One category or exercise: its dot and name, and its numbers with the chosen metric first. */
@Composable
private fun TotalRow(category: ExerciseCategory?, name: String, values: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (category != null) {
            CategoryDot(category)
        } else {
            Box(
                Modifier
                    .size(10.dp)
                    .semantics { contentDescription = "No category" }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.Gray, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            }
        }
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(values, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun valuesText(seconds: Int, count: Int, metric: DashboardMetric, inferred: Boolean): String {
    val time = (if (inferred) "≈ " else "") + DashboardStats.hours(seconds)
    val times = "$count×"
    return if (metric == DashboardMetric.HOURS) "$time · $times" else "$times · $time"
}

private fun scaleLabel(max: Float, metric: DashboardMetric): String =
    if (metric == DashboardMetric.HOURS) DashboardStats.hours(max.toInt()) else max.toInt().toString()

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

private fun periodLabel(bar: DashboardBar): String =
    if (bar.monthly) monthYear.format(bar.start) else "Week of ${dayMonth.format(bar.start)}"

private fun axisLabel(bar: DashboardBar): String =
    if (bar.monthly) monthYear.format(bar.start) else dayMonth.format(bar.start)

private fun spanLabel(from: LocalDate, to: LocalDate): String =
    if (from.year == to.year) "${dayMonth.format(from)} – ${dayMonthYear.format(to)}"
    else "${dayMonthYear.format(from)} – ${dayMonthYear.format(to)}"
