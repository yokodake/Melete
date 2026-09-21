package com.yokodake.melete.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.data.TrainingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class LibraryUiState(
    val query: String = "",
    val exercises: List<LibraryExercise> = emptyList(),
    /** True when a search is on and matched nothing, as opposed to an empty library. */
    val noMatches: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val message: String? = null,
)

class LibraryViewModel(
    private val repository: TrainingRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observeLibrary(),
        query,
        message,
    ) { all, currentQuery, currentMessage ->
        val matches = all.filter { it.matches(currentQuery) }
        LibraryUiState(
            query = currentQuery,
            exercises = matches,
            noMatches = matches.isEmpty() && all.isNotEmpty(),
            today = LocalDate.now(clock),
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(today = LocalDate.now(clock)),
    )

    /** The week the library offers first when scheduling: the one you are in. */
    val currentWeekStart: LocalDate get() = WeekMath.weekStartOf(LocalDate.now(clock))

    fun setQuery(value: String) {
        query.value = value
    }

    /** Places a copy of this exercise in the chosen week, on a day or in its unscheduled area. */
    fun schedule(exerciseId: String, weekStart: LocalDate, trainingDate: LocalDate?) {
        viewModelScope.launch {
            repository.scheduleExercise(exerciseId, weekStart, trainingDate)
            message.value = "Added to ${trainingDate?.let(WeekMath::dayLabel) ?: "unscheduled"}"
        }
    }

    /**
     * Retires an exercise from the library.
     *
     * Nothing is destroyed: scheduled copies keep working and logged history keeps grouping under
     * the same identity. What goes away is the offer to plan it again, which is what the user is
     * actually asking for when they delete something from a library.
     */
    fun retire(exercise: LibraryExercise) {
        viewModelScope.launch {
            repository.retireExercise(exercise.id)
            message.value = "${exercise.name} removed from the library"
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        /** Case-insensitive, and on the name only: that is what the user is scanning for. */
        private fun LibraryExercise.matches(query: String): Boolean =
            query.isBlank() || name.contains(query.trim(), ignoreCase = true)

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                LibraryViewModel(application.container.trainingRepository)
            }
        }
    }
}
