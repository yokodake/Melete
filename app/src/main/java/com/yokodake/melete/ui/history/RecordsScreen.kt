package com.yokodake.melete.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.yokodake.melete.data.OccurrenceDetail
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.ui.RecordsDestination
import com.yokodake.melete.ui.components.BackButton
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.VariationChip
import com.yokodake.melete.ui.dashboard.DashboardStats
import com.yokodake.melete.ui.detail.LastLoggedPicker
import com.yokodake.melete.ui.week.PrescriptionSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One completed exercise, as a line of the records. */
data class RecordLine(
    val occurrenceId: String,
    val exerciseId: String,
    val name: String,
    val category: ExerciseCategory?,
    val variationTag: String?,
    val summary: String?,
    val durationSeconds: Int?,
    val durationManual: Boolean,
)

data class RecordDay(val date: LocalDate, val lines: List<RecordLine>)

data class RecordsUiState(
    val loading: Boolean = true,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    /** The category narrowed to, when one was; see [uncategorised] for "no category". */
    val category: ExerciseCategory? = null,
    val uncategorised: Boolean = false,
    val days: List<RecordDay> = emptyList(),
) {
    val count: Int get() = days.sumOf { it.lines.size }
    val seconds: Int get() = days.sumOf { day -> day.lines.sumOf { it.durationSeconds ?: 0 } }
}

object Records {
    /**
     * The completed exercises behind a dashboard number, newest day first and in each day's own
     * order — exactly the occurrences the dashboard counted there, so the list and the total agree.
     */
    fun days(
        details: List<OccurrenceDetail>,
        category: ExerciseCategory?,
        uncategorised: Boolean,
    ): List<RecordDay> = details
        .filter { detail ->
            when {
                uncategorised -> detail.occurrence.category == null
                category != null -> detail.occurrence.category == category
                else -> true
            }
        }
        .mapNotNull { detail ->
            val date = detail.occurrence.trainingDate ?: return@mapNotNull null
            date to detail
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap(compareByDescending { it })
        .map { (date, list) ->
            RecordDay(
                date = date,
                lines = list.sortedBy { it.occurrence.orderIndex }.map { detail ->
                    val o = detail.occurrence
                    RecordLine(
                        occurrenceId = o.id,
                        exerciseId = o.exerciseId,
                        name = o.name,
                        category = o.category,
                        variationTag = o.variationTag,
                        summary = LastLoggedPicker.summarise(o, detail.sets),
                        durationSeconds = o.loggedDurationSeconds,
                        durationManual = o.loggedDurationManual,
                    )
                },
            )
        }
}

class RecordsViewModel(
    repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val destination = savedStateHandle.toRoute<RecordsDestination>()
    private val category = destination.category?.let { name -> ExerciseCategory.entries.firstOrNull { it.name == name } }

    val uiState: StateFlow<RecordsUiState> =
        repository.observeCompletedRecords(destination.from, destination.to).map { details ->
            RecordsUiState(
                loading = false,
                from = destination.from,
                to = destination.to,
                category = category,
                uncategorised = destination.uncategorised,
                days = Records.days(details, category, destination.uncategorised),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUiState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                RecordsViewModel(application.container.trainingRepository, createSavedStateHandle())
            }
        }
    }
}

@Composable
fun RecordsRoute(
    onOpenRecord: (occurrenceId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: RecordsViewModel = viewModel(factory = RecordsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecordsScreen(state, onOpenRecord, onBack)
}

/**
 * What a dashboard number is made of: the completed exercises of a range, a bar's week or month,
 * or one category of either, by day. A tap opens the record itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    state: RecordsUiState,
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryDot(state.category)
                        Column {
                            Text(
                                text = when {
                                    state.uncategorised -> "No category"
                                    state.category != null -> state.category.label
                                    else -> "Records"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (state.from != null && state.to != null) {
                                Text(spanText(state.from, state.to), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                Text(
                    text = "${DashboardStats.hours(state.seconds)} · ${state.count} " +
                        if (state.count == 1) "exercise" else "exercises",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (state.days.isEmpty()) {
                item {
                    Text(
                        text = "Nothing completed here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.days.forEach { day ->
                item(key = "day-${day.date}") {
                    Text(
                        text = dayText(day.date),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(items = day.lines, key = { it.occurrenceId }) { line ->
                    LineRow(line, onClick = { onOpenRecord(line.occurrenceId) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LineRow(line: RecordLine, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryDot(line.category)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                line.variationTag?.let { VariationChip(it) }
            }
            line.summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        line.durationSeconds?.let { seconds ->
            Text(
                text = (if (line.durationManual) "" else "≈ ") + PrescriptionSummary.duration(seconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun dayText(date: LocalDate): String {
    val pattern = if (date.year == LocalDate.now().year) "EEEE d MMM" else "EEEE d MMM yyyy"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date)
}

private fun spanText(from: LocalDate, to: LocalDate): String =
    if (from == to) historyDate(from) else "${historyDate(from)} – ${historyDate(to)}"
