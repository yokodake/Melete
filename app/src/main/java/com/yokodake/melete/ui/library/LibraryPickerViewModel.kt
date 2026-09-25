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
import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.data.Routine
import com.yokodake.melete.data.TrainingModule
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.ui.LibraryPickerDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Which kind of saved thing the picker is showing. */
enum class PickerView(val label: String) {
    EXERCISES("Exercises"),
    CIRCUITS("Circuits"),
    MODULES("Modules"),
}

data class LibraryPickerUiState(
    val targetLabel: String,
    val view: PickerView = PickerView.EXERCISES,
    val exercises: List<LibraryExercise> = emptyList(),
    val circuits: List<Routine> = emptyList(),
    val modules: List<TrainingModule> = emptyList(),
)

/**
 * The library as a picker: one place to add anything saved to a slot of the week — an exercise,
 * a circuit or a module — rather than one entry on the plus per kind, each leading to a lookalike
 * list.
 *
 * The slot is fixed by the route for the life of the screen, so switching views or detouring to
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
        // Saved state rather than a field, so the view you chose is still the one showing after a
        // trip to an editor and back, or after the process is recreated.
        savedStateHandle.getStateFlow(VIEW_KEY, PickerView.EXERCISES),
        repository.observeLibrary(),
        repository.observeRoutines(),
        repository.observeModules(),
    ) { view, exercises, circuits, modules ->
        LibraryPickerUiState(targetLabel, view, exercises, circuits, modules)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryPickerUiState(targetLabel),
    )

    fun showView(view: PickerView) {
        savedStateHandle[VIEW_KEY] = view
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

    /** Copies the module, and everything in it, into the slot. */
    fun scheduleModule(moduleId: String, onScheduled: () -> Unit) {
        viewModelScope.launch {
            repository.scheduleModule(moduleId, weekStart, trainingDate)
            onScheduled()
        }
    }

    companion object {
        private const val VIEW_KEY = "pickerView"

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
