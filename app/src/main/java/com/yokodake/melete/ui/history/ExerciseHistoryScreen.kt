package com.yokodake.melete.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.navigation.toRoute
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.ui.ExerciseHistoryDestination
import com.yokodake.melete.ui.components.BackButton
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.ChartLine
import com.yokodake.melete.ui.components.ChartPoint
import com.yokodake.melete.ui.components.DetailSection
import com.yokodake.melete.ui.components.LineChart
import com.yokodake.melete.ui.components.VariationChip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ExerciseHistoryUiState(
    val loading: Boolean = true,
    val name: String = "",
    val category: ExerciseCategory? = null,
    val history: ExerciseHistory? = null,
    /** The range it was opened with, from the dashboard; null once cleared or when there was none. */
    val from: LocalDate? = null,
    val to: LocalDate? = null,
)

/**
 * One exercise across weeks: whichever way it was reached — an exercise row of the dashboard, or
 * the exercise's own page — this is the one place its history lives.
 */
class ExerciseHistoryViewModel(
    repository: TrainingRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val destination = savedStateHandle.toRoute<ExerciseHistoryDestination>()
    private val plan = savedStateHandle.getStateFlow<String?>(PLAN_KEY, null)
    private val target = savedStateHandle.getStateFlow<Int?>(TARGET_KEY, null)
    private val allTime = savedStateHandle.getStateFlow(ALL_TIME_KEY, false)

    val uiState: StateFlow<ExerciseHistoryUiState> = combine(
        repository.observeExerciseRecords(destination.exerciseId),
        repository.observeLibraryExercise(destination.exerciseId),
        plan,
        target,
        allTime,
    ) { details, library, p, t, all ->
        // The library entry decides the name and how loads are compared; a copy stands in when
        // the entry is gone, so a deleted exercise's history still reads.
        val latest = details.map { it.occurrence }.maxByOrNull { it.trainingDate ?: it.weekStart }
        val from = destination.from.takeIf { !all }
        val to = destination.to.takeIf { !all }
        ExerciseHistoryUiState(
            loading = false,
            name = library?.name ?: latest?.name.orEmpty(),
            category = library?.category ?: latest?.category,
            history = ExerciseHistory.build(
                details = details,
                mode = library?.mode ?: latest?.mode ?: ExerciseMode.REPETITIONS,
                unit = library?.measurementUnit ?: latest?.measurementUnit,
                meaning = library?.measurementMeaning ?: latest?.measurementMeaning,
                plan = p,
                target = t,
                from = from,
                to = to,
            ),
            from = from,
            to = to,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseHistoryUiState())

    fun setPlan(key: String?) {
        savedStateHandle[PLAN_KEY] = key
    }

    fun setTarget(value: Int?) {
        savedStateHandle[TARGET_KEY] = value
    }

    fun showAllTime() {
        savedStateHandle[ALL_TIME_KEY] = true
    }

    companion object {
        private const val PLAN_KEY = "historyPlan"
        private const val TARGET_KEY = "historyTarget"
        private const val ALL_TIME_KEY = "historyAllTime"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                ExerciseHistoryViewModel(application.container.trainingRepository, createSavedStateHandle())
            }
        }
    }
}

@Composable
fun ExerciseHistoryRoute(
    onOpenRecord: (occurrenceId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExerciseHistoryViewModel = viewModel(factory = ExerciseHistoryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseHistoryScreen(
        state = state,
        onPlan = viewModel::setPlan,
        onTarget = viewModel::setTarget,
        onAllTime = viewModel::showAllTime,
        onOpenRecord = onOpenRecord,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseHistoryScreen(
    state: ExerciseHistoryUiState,
    onPlan: (String?) -> Unit,
    onTarget: (Int?) -> Unit,
    onAllTime: () -> Unit,
    onOpenRecord: (String) -> Unit,
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
                            Text("History", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        val history = state.history ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val filters = state.from != null || history.planOptions.isNotEmpty() || history.targetOptions.isNotEmpty()
            if (filters) {
                item {
                    // Wraps rather than scrolls: a range and two filters do not fit one phone line.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.from != null && state.to != null) {
                            FilterChip(
                                selected = true,
                                onClick = onAllTime,
                                label = { Text("${historyDate(state.from)} – ${historyDate(state.to)}  ✕") },
                                modifier = Modifier.semantics { contentDescription = "Show all time" },
                            )
                        }
                        if (history.planOptions.isNotEmpty()) {
                            Choice(
                                current = history.planOptions.firstOrNull { it.key == history.plan }?.label ?: "All plans",
                                options = listOf<Pair<String?, String>>(null to "All plans") +
                                    history.planOptions.map { it.key to it.label },
                                onChoose = onPlan,
                            )
                        }
                        if (history.targetOptions.isNotEmpty()) {
                            val timed = history.records.firstOrNull()?.occurrence?.mode == ExerciseMode.DURATION
                            fun label(value: Int) = if (timed) "${value} s sets" else "$value reps"
                            Choice(
                                current = history.target?.let(::label) ?: if (timed) "All lengths" else "All reps",
                                options = listOf<Pair<Int?, String>>(null to if (timed) "All lengths" else "All reps") +
                                    history.targetOptions.map { it to label(it) },
                                onChoose = onTarget,
                            )
                        }
                    }
                }
            }
            if (history.records.isEmpty()) {
                item {
                    Text(
                        text = "Nothing logged yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@LazyColumn
            }
            if (history.hasGraph) {
                item { Standing(history) }
                item {
                    LineChart(
                        lines = history.series.map { series ->
                            ChartLine(
                                label = when (series.side) {
                                    BodySide.LEFT -> "L"
                                    BodySide.RIGHT -> "R"
                                    null -> null
                                },
                                points = series.points.map { ChartPoint(it.date, it.value) },
                                square = series.side == BodySide.RIGHT,
                            )
                        },
                        format = history::format,
                        description = "${history.measure?.label} over time",
                    )
                }
            }
            item { DetailSection("Results") }
            items(items = history.records, key = { it.occurrence.id }) { record ->
                RecordRow(record, onClick = { onOpenRecord(record.occurrence.id) })
                HorizontalDivider()
            }
        }
    }
}

/** Latest and best, one line each: the measure's name, then each side. */
@Composable
private fun Standing(history: ExerciseHistory) {
    fun line(marks: Map<BodySide?, HistoryMark>): String {
        val sides = listOf(BodySide.LEFT, BodySide.RIGHT, null).mapNotNull { side ->
            marks[side]?.let { side to it }
        }
        val sameDate = sides.map { it.second.date }.distinct().size == 1
        val values = sides.joinToString(" · ") { (side, mark) ->
            val prefix = when (side) {
                BodySide.LEFT -> "L "
                BodySide.RIGHT -> "R "
                null -> ""
            }
            prefix + history.format(mark.value) + if (sameDate) "" else " (${historyDate(mark.date)})"
        }
        return if (sameDate) "$values · ${historyDate(sides.first().second.date)}" else values
    }
    Column {
        Text(
            text = history.measure?.label.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Latest ${line(history.latest)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Best ${line(history.best)}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RecordRow(record: HistoryRecord, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = historyDate(record.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            record.occurrence.variationTag?.let { VariationChip(it) }
        }
        Text(
            text = record.summary ?: "Done",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** A small dropdown: the current choice, and the others under it. */
@Composable
private fun <T> Choice(current: String, options: List<Pair<T, String>>, onChoose: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { open = true },
            label = { Text("$current ▾") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Text(label, fontWeight = if (label == current) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    onClick = {
                        open = false
                        onChoose(value)
                    },
                )
            }
        }
    }
}

/** "24 Sep", with the year once it is not this one. */
internal fun historyDate(date: LocalDate): String {
    val pattern = if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date)
}
