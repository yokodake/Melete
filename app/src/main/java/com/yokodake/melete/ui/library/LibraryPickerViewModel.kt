package com.yokodake.melete.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.TrainingModule
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.ui.LibraryPickerDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LibraryPickerUiState(
    val targetLabel: String,
    val tab: LibraryTab = LibraryTab.WORKOUTS,
    val query: String = "",
    /** Exercises and circuits together, alphabetical — the same list as the library tab. */
    val workouts: List<Workout> = emptyList(),
    val modules: List<TrainingModule> = emptyList(),
    /** A search is on and matched nothing, as opposed to an empty library. */
    val noMatches: Boolean = false,
)

/**
 * The library as a picker: anything saved, added to one slot of the week. The same two tabs as
 * the library itself, so a thing is found in the same place whichever screen you start from.
 *
 * The slot is fixed by the route for the life of the screen, so switching tabs or detouring to
 * create something never loses where the pick is going.
 */
class LibraryPickerViewModel(
    private val repository: TrainingRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val destination = savedStateHandle.toRoute<LibraryPickerDestination>()
    private val weekStart: LocalDate = LocalDate.ofEpochDay(destination.weekStartEpochDay)
    private val trainingDate: LocalDate? = destination.trainingDate

    private val targetLabel: String =
        trainingDate?.let(WeekMath::dayLabel) ?: "Unscheduled · ${WeekMath.weekLabel(weekStart)}"

    val uiState: StateFlow<LibraryPickerUiState> = combine(
        // Saved state rather than a field, so the tab you chose is still the one showing after a
        // trip to an editor and back, or after the process is recreated.
        savedStateHandle.getStateFlow(TAB_KEY, LibraryTab.WORKOUTS),
        savedStateHandle.getStateFlow(QUERY_KEY, ""),
        repository.observeLibrary(),
        repository.observeRoutines(),
        repository.observeModules(),
    ) { tab, query, exercises, circuits, modules ->
        val workouts = workoutsOf(exercises, circuits, query)
        val matchingModules = modulesOf(modules, query)
        LibraryPickerUiState(
            targetLabel = targetLabel,
            tab = tab,
            query = query,
            workouts = workouts,
            modules = matchingModules,
            noMatches = LibrarySearch.conditions(query).isNotEmpty() && when (tab) {
                LibraryTab.WORKOUTS -> workouts.isEmpty() && (exercises + circuits).isNotEmpty()
                LibraryTab.MODULES -> matchingModules.isEmpty() && modules.isNotEmpty()
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryPickerUiState(targetLabel),
    )

    fun showTab(tab: LibraryTab) {
        savedStateHandle[TAB_KEY] = tab
    }

    fun setQuery(value: String) {
        savedStateHandle[QUERY_KEY] = value
    }

    /** Copies an exercise into the slot, cut from the default plan or from the chosen variation. */
    fun schedule(exerciseId: String, variationId: String?, onScheduled: () -> Unit) {
        viewModelScope.launch {
            repository.scheduleExercise(exerciseId, weekStart, trainingDate, variationId)
            onScheduled()
        }
    }

    /** Copies the circuit into the same slot an exercise would have gone to. */
    fun scheduleCircuit(routineId: String, onScheduled: () -> Unit) {
        viewModelScope.launch {
            repository.scheduleRoutine(routineId, weekStart, trainingDate)
            onScheduled()
        }
    }

    /** Copies the module, and everything in it that still exists, into the slot. */
    fun scheduleModule(moduleId: String, onScheduled: () -> Unit) {
        viewModelScope.launch {
            repository.scheduleModule(moduleId, weekStart, trainingDate)
            onScheduled()
        }
    }

    companion object {
        private const val TAB_KEY = "pickerTab"
        private const val QUERY_KEY = "pickerQuery"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                LibraryPickerViewModel(
                    application.container.trainingRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
