package com.yokodake.melete.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.Routine
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.timer.PrescriptionProgram
import com.yokodake.melete.data.timer.StationPlan
import com.yokodake.melete.ui.RoutineDetailDestination
import com.yokodake.melete.ui.components.BackButton
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.ContentsLine
import com.yokodake.melete.ui.components.DetailActionBar
import com.yokodake.melete.ui.components.DetailSection
import com.yokodake.melete.ui.components.WeekTargetDialog
import com.yokodake.melete.ui.components.circuitShapeLine
import com.yokodake.melete.ui.week.PrescriptionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RoutineDetailUiState(
    val loading: Boolean = true,
    val routine: Routine? = null,
    val estimatedSeconds: Int? = null,
    val today: LocalDate = LocalDate.now(),
    val message: String? = null,
)

/**
 * A saved circuit, read rather than edited: what it holds, in order, and how long it takes. The
 * template itself — no scheduled copy is needed to look at it.
 */
class RoutineDetailViewModel(
    private val repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routineId = savedStateHandle.toRoute<RoutineDetailDestination>().routineId
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<RoutineDetailUiState> = combine(
        repository.observeRoutine(routineId),
        message,
    ) { routine, text ->
        RoutineDetailUiState(
            loading = false,
            routine = routine,
            estimatedSeconds = routine?.takeIf { it.entries.isNotEmpty() }?.let { saved ->
                PrescriptionProgram.circuit(
                    label = saved.name,
                    rounds = saved.rounds,
                    transitionSeconds = saved.transitionSeconds,
                    roundRestSeconds = saved.roundRestSeconds,
                    stations = saved.entries.map {
                        StationPlan(it.name, it.mode, it.unilateral, it.prescription)
                    },
                ).estimatedSeconds()
            },
            message = text,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineDetailUiState())

    fun schedule(weekStart: LocalDate) {
        viewModelScope.launch {
            repository.scheduleRoutine(routineId, weekStart, trainingDate = null)
            message.value = "Added to ${WeekMath.weekLabel(weekStart)}"
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                RoutineDetailViewModel(application.container.trainingRepository, createSavedStateHandle())
            }
        }
    }
}

@Composable
fun RoutineDetailRoute(
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RoutineDetailViewModel = viewModel(factory = RoutineDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RoutineDetailScreen(
        state = state,
        onEdit = onEdit,
        onSchedule = viewModel::schedule,
        onMessageShown = viewModel::consumeMessage,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(
    state: RoutineDetailUiState,
    onEdit: (String) -> Unit,
    onSchedule: (LocalDate) -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var addingToPlan by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }
    val routine = state.routine
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        CategoryDot(routine?.category)
                        Column {
                            Text(routine?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = routine?.category?.label ?: "Circuit",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (routine != null) TextButton(onClick = { onEdit(routine.id) }) { Text("Edit") }
                },
            )
        },
        bottomBar = {
            if (routine != null) {
                DetailActionBar {
                    Button(onClick = { addingToPlan = true }, modifier = Modifier.weight(1f)) {
                        Text("Add to plan")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.loading) return@Column
            if (routine == null) {
                Text("This circuit is no longer in the library.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            DetailSection(circuitShapeLine(
                rounds = routine.rounds,
                transitionSeconds = routine.transitionSeconds,
                roundRestSeconds = routine.roundRestSeconds,
                estimatedSeconds = state.estimatedSeconds,
            ))
            if (routine.entries.isEmpty()) {
                Text(
                    text = "No exercises",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            routine.entries.forEachIndexed { index, entry ->
                ContentsLine(
                    position = index + 1,
                    category = entry.category,
                    name = entry.name,
                    summary = PrescriptionSummary.formatStation(entry.prescription, entry.mode, entry.unilateral),
                )
                HorizontalDivider()
            }
        }
    }

    if (addingToPlan && routine != null) {
        WeekTargetDialog(
            title = "Add ${routine.name} to",
            today = state.today,
            onConfirm = { week ->
                addingToPlan = false
                onSchedule(week)
            },
            onDismiss = { addingToPlan = false },
        )
    }
}
