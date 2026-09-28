package com.yokodake.melete.ui.benchmark

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import com.yokodake.melete.data.BenchmarkFormat
import com.yokodake.melete.data.BenchmarkRepository
import com.yokodake.melete.data.BenchmarkResult
import com.yokodake.melete.data.BenchmarkStanding
import com.yokodake.melete.data.BestValue
import com.yokodake.melete.data.entity.BenchmarkMeasure
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.BenchmarkDetailDestination
import com.yokodake.melete.ui.components.BackButton
import com.yokodake.melete.ui.components.DetailActionBar
import com.yokodake.melete.ui.components.DetailSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which result dialog is up: a new result, or a correction of one already recorded. */
sealed interface ResultEditing {
    data object New : ResultEditing
    data class Existing(val result: BenchmarkResult) : ResultEditing
}

data class BenchmarkDetailUiState(
    val loading: Boolean = true,
    val standing: BenchmarkStanding? = null,
    val editing: ResultEditing? = null,
    val message: String? = null,
)

class BenchmarkDetailViewModel(
    private val repository: BenchmarkRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id = savedStateHandle.toRoute<BenchmarkDetailDestination>().benchmarkId

    private data class Local(val editing: ResultEditing? = null, val message: String? = null)

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<BenchmarkDetailUiState> =
        combine(repository.observeStanding(id), local) { standing, current ->
            BenchmarkDetailUiState(
                loading = false,
                standing = standing,
                editing = current.editing,
                message = current.message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BenchmarkDetailUiState())

    fun startNew() = local.update { it.copy(editing = ResultEditing.New) }

    fun startEdit(result: BenchmarkResult) = local.update { it.copy(editing = ResultEditing.Existing(result)) }

    fun cancel() = local.update { it.copy(editing = null) }

    fun save(input: ResultInput) {
        val editing = local.value.editing ?: return
        local.update { it.copy(editing = null) }
        viewModelScope.launch {
            when (editing) {
                ResultEditing.New -> repository.record(
                    id, input.date, input.value, input.valueRight, input.note, input.text, input.bodyweightPercent,
                )
                is ResultEditing.Existing -> repository.updateResult(
                    editing.result.id, input.date, input.value, input.valueRight, input.note, input.text,
                    input.bodyweightPercent,
                )
            }
        }
    }

    fun deleteEditing() {
        val editing = local.value.editing as? ResultEditing.Existing ?: return
        local.update { it.copy(editing = null) }
        viewModelScope.launch {
            repository.deleteResult(editing.result.id)
            local.update { it.copy(message = "Result deleted") }
        }
    }

    fun setHidden(hidden: Boolean) {
        viewModelScope.launch {
            repository.setHidden(id, hidden)
            local.update { it.copy(message = if (hidden) "Hidden — its results are kept" else "Shown again") }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (repository.deleteIfUnused(id)) onDeleted()
            else local.update { it.copy(message = "It has results: hide it instead") }
        }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                BenchmarkDetailViewModel(application.container.benchmarkRepository, createSavedStateHandle())
            }
        }
    }
}

@Composable
fun BenchmarkDetailRoute(
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BenchmarkDetailViewModel = viewModel(factory = BenchmarkDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BenchmarkDetailScreen(
        state = state,
        onRecord = viewModel::startNew,
        onOpenResult = viewModel::startEdit,
        onSave = viewModel::save,
        onDeleteResult = viewModel::deleteEditing,
        onCancel = viewModel::cancel,
        onEdit = onEdit,
        onSetHidden = viewModel::setHidden,
        onDelete = { viewModel.delete(onBack) },
        onMessageShown = viewModel::consumeMessage,
        onBack = onBack,
    )
}

/**
 * One benchmark: its protocol, where it stands — latest and best, kept apart — and every dated
 * result underneath, each opened to correct it. The graph over time comes with the exercise graphs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkDetailScreen(
    state: BenchmarkDetailUiState,
    onRecord: () -> Unit,
    onOpenResult: (BenchmarkResult) -> Unit,
    onSave: (ResultInput) -> Unit,
    onDeleteResult: () -> Unit,
    onCancel: () -> Unit,
    onEdit: (String) -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmingDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }
    val standing = state.standing
    val benchmark = standing?.benchmark

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Column {
                        Text(benchmark?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        benchmark?.let {
                            Text(definitionLine(it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (benchmark != null) {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { open = true },
                                modifier = Modifier.semantics { contentDescription = "Benchmark actions" },
                            ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = { open = false; onEdit(benchmark.id) },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (benchmark.hidden) "Show again" else "Hide") },
                                    onClick = { open = false; onSetHidden(!benchmark.hidden) },
                                )
                                // Only while nothing has been recorded: results are never lost to a tidy-up.
                                if (standing.results.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = { open = false; confirmingDelete = true },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (benchmark != null) {
                DetailActionBar {
                    Button(onClick = onRecord, modifier = Modifier.weight(1f)) { Text("Record result") }
                }
            }
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        if (standing == null) {
            Text(
                text = "This benchmark no longer exists.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Protocol on the left, goal on the right: one line for both.
            val protocol = standing.benchmark.protocol
            val goal = standing.benchmark.goal
            if (protocol != null || goal != null) {
                item {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = protocol.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        goal?.let {
                            Text(
                                text = "Goal $it",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
            item { StandingCard(standing) }
            if (standing.results.isNotEmpty()) {
                item { DetailSection("Results") }
                items(items = standing.results, key = { it.id }) { result ->
                    ResultRow(result, onClick = { onOpenResult(result) })
                    HorizontalDivider()
                }
            }
        }
    }

    if (benchmark != null) {
        when (val editing = state.editing) {
            ResultEditing.New -> ResultDialog(benchmark, null, onSave, null, onCancel)
            is ResultEditing.Existing -> ResultDialog(benchmark, editing.result, onSave, onDeleteResult, onCancel)
            null -> Unit
        }
    }

    if (confirmingDelete && benchmark != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete ${benchmark.name}?") },
            text = { Text("It has no results yet.") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

/** "Load · kg added · L/R", the definition in one line. */
private fun definitionLine(benchmark: com.yokodake.melete.data.Benchmark): String = if (benchmark.isText) {
    listOfNotNull("Text result", "hidden".takeIf { benchmark.hidden }).joinToString(" · ")
} else listOfNotNull(
    benchmark.measure.label,
    benchmark.unit.takeIf { it.isNotBlank() }?.let {
        if (benchmark.measure == BenchmarkMeasure.LOAD && benchmark.loadMeaning == MeasurementMeaning.ADDED_LOAD) {
            "$it added"
        } else {
            it
        }
    },
    "L/R".takeIf { benchmark.unilateral },
    // Higher is the rule and goes unsaid; only the exception is worth the words.
    "lower is better".takeIf { !benchmark.higherIsBetter },
    "hidden".takeIf { benchmark.hidden },
).joinToString(" · ")

/** Latest and best, kept apart; each side's best keeps its own date. */
@Composable
private fun StandingCard(standing: BenchmarkStanding) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val latest = standing.latest
            if (latest == null) {
                Text("No results yet", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Text(
                text = buildAnnotatedString {
                    append("Latest ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(latest.text) }
                    latest.bodyweightText?.let { append(" ($it)") }
                    append(" · ${benchmarkDate(latest.date)}")
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            bestLines(standing).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

private fun bestLines(standing: BenchmarkStanding): List<String> {
    val benchmark = standing.benchmark
    val unit = benchmark.unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    fun text(best: BestValue) = BenchmarkFormat.number(best.value, benchmark.loadMeaning)
    val left = standing.best
    val right = standing.bestRight
    return when {
        !benchmark.unilateral -> listOfNotNull(left?.let { "Best ${text(it)}$unit · ${benchmarkDate(it.date)}" })
        left != null && right != null && left.date == right.date ->
            listOf("Best L ${text(left)} · R ${text(right)}$unit · ${benchmarkDate(left.date)}")
        else -> listOfNotNull(
            left?.let { "Best L ${text(it)}$unit · ${benchmarkDate(it.date)}" },
            right?.let { "Best R ${text(it)}$unit · ${benchmarkDate(it.date)}" },
        )
    }
}

/**
 * One recorded result. Correcting or deleting it takes a long press: a record should not change
 * from a tap made while scrolling.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(result: BenchmarkResult, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                onLongClickLabel = "Correct this result",
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        // A date with its year is wider than one without; the gap keeps it off the value.
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = benchmarkDate(result.date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 104.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.text + (result.bodyweightText?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
            )
            result.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
