package com.yokodake.melete.ui.routine

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
import com.yokodake.melete.data.Routine
import com.yokodake.melete.data.RoutineRemoval
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.ui.RoutineListDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class RoutineListUiState(
    val loading: Boolean = true,
    val routines: List<Routine> = emptyList(),
    /** Set when the screen was opened to put a circuit somewhere, rather than to manage them. */
    val target: RoutineTarget? = null,
    val removal: RoutineRemoval? = null,
    val message: String? = null,
) {
    val picking: Boolean get() = target != null
}

/** Where a picked routine is going. */
data class RoutineTarget(val weekStart: LocalDate, val trainingDate: LocalDate?) {
    val label: String
        get() = trainingDate?.let(WeekMath::dayLabel) ?: "Anytime this week"
}

/**
 * The saved circuits: browsing them, and choosing one to put in a week.
 *
 * One screen for both, because the list is the same list and the difference is only what a tap
 * does. Splitting them would mean two screens that have to be kept looking alike.
 */
class RoutineListViewModel(
    private val repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
    clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val destination = savedStateHandle.toRoute<RoutineListDestination>()

    private val target: RoutineTarget? = destination.weekStart?.let {
        RoutineTarget(weekStart = it, trainingDate = destination.trainingDate)
    }

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
                target = target,
                removal = extras.removal,
                message = extras.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RoutineListUiState(target = target),
        )

    /** Copies the routine into the slot this screen was opened for. */
    fun schedule(routineId: String, onScheduled: () -> Unit) {
        val where = target ?: return
        viewModelScope.launch {
            repository.scheduleRoutine(routineId, where.weekStart, where.trainingDate)
            onScheduled()
        }
    }

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
                RoutineListViewModel(
                    application.container.trainingRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
