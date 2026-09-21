package com.yokodake.melete.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.ExerciseRemoval
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
    /** Set while the removal dialog is up, carrying what removing would cost. */
    val removal: ExerciseRemoval? = null,
    val message: String? = null,
)

class LibraryViewModel(
    private val repository: TrainingRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)
    private val removal = MutableStateFlow<ExerciseRemoval?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observeLibrary(),
        query,
        message,
        removal,
    ) { all, currentQuery, currentMessage, currentRemoval ->
        val matches = all.filter { it.matches(currentQuery) }
        LibraryUiState(
            query = currentQuery,
            exercises = matches,
            noMatches = matches.isEmpty() && all.isNotEmpty(),
            today = LocalDate.now(clock),
            removal = currentRemoval,
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
     * Counts what removing this exercise would cost, then opens the dialog that says so.
     *
     * The count happens before the question because the three outcomes are different promises,
     * and the user has to be told which one they are agreeing to.
     */
    fun askToRemove(exercise: LibraryExercise) {
        viewModelScope.launch {
            removal.value = repository.removalImpactOf(exercise.id)
        }
    }

    fun cancelRemoval() {
        removal.value = null
    }

    /** Carries out whichever removal the exercise's own history allows. */
    fun confirmRemoval() {
        val target = removal.value ?: return
        removal.value = null
        viewModelScope.launch {
            message.value = when (repository.removeExercise(target.exercise.id)) {
                ExerciseRemoval.Outcome.DELETED ->
                    "${target.exercise.name} deleted"
                ExerciseRemoval.Outcome.DELETED_WITH_PLANS ->
                    "${target.exercise.name} deleted, with ${target.plannedCopies} planned " +
                        plural(target.plannedCopies, "copy", "copies")
                ExerciseRemoval.Outcome.RETIRED ->
                    "${target.exercise.name} removed from the library — history kept"
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        internal fun plural(count: Int, one: String, many: String): String =
            if (count == 1) one else many

        /** Case-insensitive, and on the name only: that is what the user is scanning for. */
        private fun LibraryExercise.matches(query: String): Boolean {
            val trimmed = query.trim()
            return trimmed.isBlank()
                    || name.contains(trimmed, ignoreCase = true)
                    || category?.label?.contains(trimmed, ignoreCase = true) == true
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                LibraryViewModel(application.container.trainingRepository)
            }
        }
    }
}
