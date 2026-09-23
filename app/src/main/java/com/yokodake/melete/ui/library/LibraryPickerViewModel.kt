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
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.ui.LibraryPickerDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Which half of the library the picker is showing. */
enum class PickerView { EXERCISES, CIRCUITS }

data class LibraryPickerUiState(
    val targetLabel: String,
    val view: PickerView = PickerView.EXERCISES,
    val exercises: List<LibraryExercise> = emptyList(),
    val circuits: List<Routine> = emptyList(),
)

/**
 * The library as a picker: one place to add anything saved to a slot of the week, exercises or
 * circuits, rather than two entries on the plus that lead to two lookalike lists.
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
        // trip to the editor and back, or after the process is recreated.
        savedStateHandle.getStateFlow(VIEW_KEY, PickerView.EXERCISES),
        repository.observeLibrary(),
        repository.observeRoutines(),
    ) { view, exercises, circuits ->
        LibraryPickerUiState(targetLabel, view, exercises, circuits)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryPickerUiState(targetLabel),
    )

    fun toggleView() {
        val current = savedStateHandle.get<PickerView>(VIEW_KEY) ?: PickerView.EXERCISES
        savedStateHandle[VIEW_KEY] = when (current) {
            PickerView.EXERCISES -> PickerView.CIRCUITS
            PickerView.CIRCUITS -> PickerView.EXERCISES
        }
    }

    fun schedule(exerciseId: String, onScheduled: () -> Unit) {
        viewModelScope.launch {
            repository.scheduleExercise(exerciseId, weekStart, trainingDate)
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
