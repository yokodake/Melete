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
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.ui.LibraryPickerDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LibraryPickerUiState(
    val targetLabel: String,
    val exercises: List<LibraryExercise> = emptyList(),
)

class LibraryPickerViewModel(
    private val repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val destination = savedStateHandle.toRoute<LibraryPickerDestination>()
    private val weekStart: LocalDate = LocalDate.ofEpochDay(destination.weekStartEpochDay)
    private val trainingDate: LocalDate? = destination.trainingDate

    private val targetLabel: String =
        trainingDate?.let(WeekMath::dayLabel) ?: "Unscheduled · ${WeekMath.weekLabel(weekStart)}"

    val uiState: StateFlow<LibraryPickerUiState> = repository.observeLibrary()
        .map { LibraryPickerUiState(targetLabel, it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryPickerUiState(targetLabel),
        )

    fun schedule(exerciseId: String, onScheduled: () -> Unit) {
        viewModelScope.launch {
            repository.scheduleExercise(exerciseId, weekStart, trainingDate)
            onScheduled()
        }
    }

    companion object {
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
