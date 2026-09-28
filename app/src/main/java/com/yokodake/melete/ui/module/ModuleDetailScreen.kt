package com.yokodake.melete.ui.module

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.yokodake.melete.data.ModuleEntryView
import com.yokodake.melete.data.TrainingModule
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.ui.ModuleDetailDestination
import com.yokodake.melete.ui.components.BackButton
import com.yokodake.melete.ui.components.CategoryChip
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import com.yokodake.melete.ui.components.ContentsLine
import com.yokodake.melete.ui.components.DetailActionBar
import com.yokodake.melete.ui.components.DetailSection
import com.yokodake.melete.ui.components.WeekTargetDialog
import com.yokodake.melete.ui.routine.routineSummary
import com.yokodake.melete.ui.week.PrescriptionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ModuleDetailUiState(
    val loading: Boolean = true,
    val module: TrainingModule? = null,
    val today: LocalDate = LocalDate.now(),
    val message: String? = null,
)

/**
 * A saved module, read rather than edited: its purpose and what it holds. Organisational only —
 * there is nothing here to time or to log, because a module is never trained as such.
 */
class ModuleDetailViewModel(
    private val repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val moduleId = savedStateHandle.toRoute<ModuleDetailDestination>().moduleId
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ModuleDetailUiState> = combine(
        repository.observeModule(moduleId),
        message,
    ) { module, text ->
        ModuleDetailUiState(loading = false, module = module, message = text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModuleDetailUiState())

    fun schedule(weekStart: LocalDate) {
        viewModelScope.launch {
            repository.scheduleModule(moduleId, weekStart, trainingDate = null)
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
                ModuleDetailViewModel(application.container.trainingRepository, createSavedStateHandle())
            }
        }
    }
}

@Composable
fun ModuleDetailRoute(
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ModuleDetailViewModel = viewModel(factory = ModuleDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModuleDetailScreen(
        state = state,
        onEdit = onEdit,
        onSchedule = viewModel::schedule,
        onMessageShown = viewModel::consumeMessage,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDetailScreen(
    state: ModuleDetailUiState,
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
    val module = state.module
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
                        Text(module?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Module", style = MaterialTheme.typography.bodySmall)
                            CategoryChip(module?.category)
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (module != null) TextButton(onClick = { onEdit(module.id) }) { Text("Edit") }
                },
            )
        },
        bottomBar = {
            if (module != null) {
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
            if (module == null) {
                Text("This module is no longer in the library.", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            module.description?.takeIf { it.isNotBlank() }?.let {
                DetailSection("Description")
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            DetailSection("Contents")
            if (module.entries.isEmpty()) {
                Text(
                    text = "No exercises or circuits",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            module.entries.forEachIndexed { index, entry ->
                ContentsLine(
                    position = index + 1,
                    category = entry.category ?: entry.routine?.category,
                    name = entry.name,
                    summary = entrySummary(entry),
                    tag = entry.variationTag,
                    trailing = if (entry.definitionMissing) {
                        {
                            Text(
                                text = "Unavailable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        null
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (addingToPlan && module != null) {
        WeekTargetDialog(
            // What will be left out is said before the week is chosen, not discovered after.
            title = "Add ${module.name} to" +
                (unavailableNote(module)?.let { "\n$it — won’t be added" } ?: ""),
            today = state.today,
            onConfirm = { week ->
                addingToPlan = false
                onSchedule(week)
            },
            onDismiss = { addingToPlan = false },
        )
    }
}

/** A circuit entry reads as the library lists circuits; an exercise as its own copied plan. */
private fun entrySummary(entry: ModuleEntryView): String? = when {
    entry.routine != null -> "Circuit · " + routineSummary(entry.routine)
    entry.isCircuit -> "Circuit"
    else -> PrescriptionSummary.formatPlan(entry.prescription, entry.mode, entry.unilateral)
}
