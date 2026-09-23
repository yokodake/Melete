package com.yokodake.melete.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.Routine
import com.yokodake.melete.data.RoutineRemoval
import com.yokodake.melete.data.TrainingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineListUiState(
    val loading: Boolean = true,
    val routines: List<Routine> = emptyList(),
    val removal: RoutineRemoval? = null,
    val message: String? = null,
)

/**
 * The saved circuits, to manage. Choosing one for a week happens in the library picker, which
 * lists circuits beside exercises.
 */
class RoutineListViewModel(
    private val repository: TrainingRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    private data class Transient(
        val removal: RoutineRemoval? = null,
        val message: String? = null,
    )

    val uiState: StateFlow<RoutineListUiState> =
        combine(repository.observeRoutines(), transient) { routines, extras ->
            RoutineListUiState(
                loading = false,
                routines = routines,
                removal = extras.removal,
                message = extras.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RoutineListUiState(),
        )

    fun duplicate(routineId: String) {
        viewModelScope.launch {
            repository.duplicateRoutine(routineId)
            transient.update { it.copy(message = "Duplicated") }
        }
    }

    fun askToRemove(routineId: String) {
        viewModelScope.launch {
            transient.update { it.copy(removal = repository.routineRemovalImpact(routineId)) }
        }
    }

    fun cancelRemoval() = transient.update { it.copy(removal = null) }

    /**
     * Removes the routine the dialog is about. Scheduled copies are untouched either way: they are
     * real occurrences and real logs, and a template going away says nothing about them.
     */
    fun confirmRemoval() {
        val removal = transient.value.removal ?: return
        viewModelScope.launch {
            repository.removeRoutine(removal.routine.id)
            transient.update {
                it.copy(
                    removal = null,
                    message = if (removal.scheduledCopies > 0) {
                        "Removed from the list. The ${removal.scheduledCopies} already scheduled " +
                            "are untouched."
                    } else {
                        "Removed"
                    },
                )
            }
        }
    }

    fun consumeMessage() = transient.update { it.copy(message = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                RoutineListViewModel(application.container.trainingRepository)
            }
        }
    }
}
