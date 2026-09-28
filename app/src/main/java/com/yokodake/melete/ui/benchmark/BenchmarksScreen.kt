package com.yokodake.melete.ui.benchmark

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.BenchmarkRepository
import com.yokodake.melete.data.BenchmarkStanding
import com.yokodake.melete.ui.components.BackButton
import com.yokodake.melete.ui.components.Chip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BenchmarksUiState(
    val loading: Boolean = true,
    val standings: List<BenchmarkStanding> = emptyList(),
    val hiddenCount: Int = 0,
    val showHidden: Boolean = false,
    /** The benchmark a result is being recorded for, while the dialog is up. */
    val recording: BenchmarkStanding? = null,
    val message: String? = null,
)

class BenchmarksViewModel(private val repository: BenchmarkRepository) : ViewModel() {

    private data class Local(
        val showHidden: Boolean = false,
        val recordingId: String? = null,
        val message: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<BenchmarksUiState> = combine(repository.observeStandings(), local) { all, current ->
        BenchmarksUiState(
            loading = false,
            standings = if (current.showHidden) all else all.filter { !it.benchmark.hidden },
            hiddenCount = all.count { it.benchmark.hidden },
            showHidden = current.showHidden,
            recording = all.firstOrNull { it.benchmark.id == current.recordingId },
            message = current.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BenchmarksUiState())

    fun toggleHidden() = local.update { it.copy(showHidden = !it.showHidden) }

    fun startRecording(id: String) = local.update { it.copy(recordingId = id) }

    fun cancelRecording() = local.update { it.copy(recordingId = null) }

    fun record(input: ResultInput) {
        val id = local.value.recordingId ?: return
        local.update { it.copy(recordingId = null) }
        viewModelScope.launch {
            repository.record(id, input.date, input.value, input.valueRight, input.note)
            local.update { it.copy(message = "Result saved") }
        }
    }

    fun setHidden(id: String, hidden: Boolean) {
        viewModelScope.launch { repository.setHidden(id, hidden) }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                BenchmarksViewModel(application.container.benchmarkRepository)
            }
        }
    }
}

@Composable
fun BenchmarksRoute(
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BenchmarksViewModel = viewModel(factory = BenchmarksViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BenchmarksScreen(
        state = state,
        onOpen = onOpen,
        onNew = onNew,
        onEdit = onEdit,
        onRecord = viewModel::startRecording,
        onSaveResult = viewModel::record,
        onCancelResult = viewModel::cancelRecording,
        onSetHidden = viewModel::setHidden,
        onToggleHidden = viewModel::toggleHidden,
        onMessageShown = viewModel::consumeMessage,
        onBack = onBack,
    )
}

/**
 * Where you stand on each reference test, one compact row each: latest and best, and a + to record
 * a new result. A tap opens its history; a long press edits or hides it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarksScreen(
    state: BenchmarksUiState,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onRecord: (String) -> Unit,
    onSaveResult: (ResultInput) -> Unit,
    onCancelResult: () -> Unit,
    onSetHidden: (String, Boolean) -> Unit,
    onToggleHidden: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }
    // A name shared by two benchmarks is told apart by its protocol; a unique one needs nothing.
    val ambiguous = state.standings.groupBy { it.benchmark.name.trim().lowercase() }
        .filterValues { it.size > 1 }.keys

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text("Benchmarks", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (state.hiddenCount > 0 || state.showHidden) {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { open = true },
                                modifier = Modifier.semantics { contentDescription = "More" },
                            ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.showHidden) "Hide hidden" else "Show hidden (${state.hiddenCount})"
                                        )
                                    },
                                    onClick = { open = false; onToggleHidden() },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
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
            if (!state.loading && state.standings.isEmpty()) {
                item {
                    Text(
                        text = "No benchmarks yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = state.standings, key = { it.benchmark.id }) { standing ->
                BenchmarkRow(
                    standing = standing,
                    showProtocol = standing.benchmark.name.trim().lowercase() in ambiguous,
                    onOpen = { onOpen(standing.benchmark.id) },
                    onRecord = { onRecord(standing.benchmark.id) },
                    onEdit = { onEdit(standing.benchmark.id) },
                    onSetHidden = { onSetHidden(standing.benchmark.id, it) },
                )
            }
        }
    }

    state.recording?.let { standing ->
        ResultDialog(
            benchmark = standing.benchmark,
            existing = null,
            onSave = onSaveResult,
            onDelete = null,
            onDismiss = onCancelResult,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BenchmarkRow(
    standing: BenchmarkStanding,
    showProtocol: Boolean,
    onOpen: () -> Unit,
    onRecord: () -> Unit,
    onEdit: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
) {
    val benchmark = standing.benchmark
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onLongClickLabel = "Benchmark actions",
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                        Text(
                            text = benchmark.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (benchmark.hidden) {
                            Chip(
                                "Hidden",
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (showProtocol) {
                        benchmark.protocol?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val latest = standing.latest
                    if (latest == null) {
                        Text(
                            text = "No results yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = buildAnnotatedString {
                                append("Latest ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(latest.text) }
                                append(" · ${benchmarkDate(latest.date)}")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        standing.bestText?.let {
                            Text(
                                text = "Best $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onRecord,
                    modifier = Modifier.semantics { contentDescription = "Record a result for ${benchmark.name}" },
                ) {
                    Text("+", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
            DropdownMenuItem(
                text = { Text(if (benchmark.hidden) "Show again" else "Hide") },
                onClick = { menuOpen = false; onSetHidden(!benchmark.hidden) },
            )
        }
    }
}
