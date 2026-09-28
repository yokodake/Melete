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
import com.yokodake.melete.data.ModuleRemoval
import com.yokodake.melete.data.RoutineRemoval
import com.yokodake.melete.data.TrainingModule
import com.yokodake.melete.data.TrainingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.WORKOUTS,
    val query: String = "",
    /** Exercises and circuits together, alphabetical, narrowed by the search. */
    val workouts: List<Workout> = emptyList(),
    val modules: List<TrainingModule> = emptyList(),
    /** True when a search is on and matched nothing, as opposed to an empty library. */
    val noMatches: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    /** Set while a removal dialog is up, carrying what removing would cost. */
    val exerciseRemoval: ExerciseRemoval? = null,
    val circuitRemoval: RoutineRemoval? = null,
    val moduleRemoval: ModuleRemoval? = null,
    val message: String? = null,
)

/**
 * The library tab: workouts — exercises and circuits together — and the modules that group them.
 */
class LibraryViewModel(
    private val repository: TrainingRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private data class Local(
        val tab: LibraryTab = LibraryTab.WORKOUTS,
        val query: String = "",
        val exerciseRemoval: ExerciseRemoval? = null,
        val circuitRemoval: RoutineRemoval? = null,
        val moduleRemoval: ModuleRemoval? = null,
        val message: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observeLibrary(),
        repository.observeRoutines(),
        repository.observeModules(),
        local,
    ) { exercises, circuits, modules, current ->
        val searching = LibrarySearch.conditions(current.query).isNotEmpty()
        val workouts = workoutsOf(exercises, circuits, current.query)
        val matchingModules = modulesOf(modules, current.query)
        LibraryUiState(
            tab = current.tab,
            query = current.query,
            workouts = workouts,
            modules = matchingModules,
            noMatches = searching && when (current.tab) {
                LibraryTab.WORKOUTS -> workouts.isEmpty() && (exercises + circuits).isNotEmpty()
                LibraryTab.MODULES -> matchingModules.isEmpty() && modules.isNotEmpty()
            },
            today = LocalDate.now(clock),
            exerciseRemoval = current.exerciseRemoval,
            circuitRemoval = current.circuitRemoval,
            moduleRemoval = current.moduleRemoval,
            message = current.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(today = LocalDate.now(clock)),
    )

    fun showTab(tab: LibraryTab) = local.update { it.copy(tab = tab) }

    fun setQuery(value: String) = local.update { it.copy(query = value) }

    private fun say(text: String) = local.update { it.copy(message = text) }

    fun consumeMessage() = local.update { it.copy(message = null) }

    // ----------------------------------------------------------- adding to a plan

    /** Copies an exercise into a week's unscheduled area, with the plan that was chosen. */
    fun schedule(exerciseId: String, weekStart: LocalDate, variationId: String?) {
        viewModelScope.launch {
            repository.scheduleExercise(exerciseId, weekStart, trainingDate = null, variationId)
            say("Added to ${WeekMath.weekLabel(weekStart)}")
        }
    }

    fun scheduleCircuit(routineId: String, weekStart: LocalDate) {
        viewModelScope.launch {
            repository.scheduleRoutine(routineId, weekStart, trainingDate = null)
            say("Added to ${WeekMath.weekLabel(weekStart)}")
        }
    }

    fun scheduleModule(moduleId: String, weekStart: LocalDate) {
        viewModelScope.launch {
            repository.scheduleModule(moduleId, weekStart, trainingDate = null)
            say("Added to ${WeekMath.weekLabel(weekStart)}")
        }
    }

    // ----------------------------------------------------------------- exercises

    /**
     * Counts what removing this exercise would cost, then opens the dialog that says so.
     *
     * The count happens before the question because the three outcomes are different promises,
     * and the user has to be told which one they are agreeing to.
     */
    fun askToRemove(exercise: LibraryExercise) {
        viewModelScope.launch {
            val impact = repository.removalImpactOf(exercise.id)
            local.update { it.copy(exerciseRemoval = impact) }
        }
    }

    fun cancelRemoval() = local.update { it.copy(exerciseRemoval = null) }

    /** Carries out whichever removal the exercise's own history allows. */
    fun confirmRemoval() {
        val target = local.value.exerciseRemoval ?: return
        local.update { it.copy(exerciseRemoval = null) }
        viewModelScope.launch {
            say(
                when (repository.removeExercise(target.exercise.id)) {
                    ExerciseRemoval.Outcome.DELETED ->
                        "${target.exercise.name} deleted"
                    ExerciseRemoval.Outcome.DELETED_WITH_PLANS ->
                        "${target.exercise.name} deleted, with ${target.plannedCopies} planned " +
                            plural(target.plannedCopies, "copy", "copies")
                    ExerciseRemoval.Outcome.RETIRED ->
                        "${target.exercise.name} removed from the library — history kept"
                }
            )
        }
    }

    // ------------------------------------------------------------------ circuits

    fun duplicateCircuit(routineId: String) {
        viewModelScope.launch {
            repository.duplicateRoutine(routineId)
            say("Duplicated")
        }
    }

    fun askToRemoveCircuit(routineId: String) {
        viewModelScope.launch {
            val impact = repository.routineRemovalImpact(routineId)
            local.update { it.copy(circuitRemoval = impact) }
        }
    }

    fun cancelCircuitRemoval() = local.update { it.copy(circuitRemoval = null) }

    /**
     * Removes the circuit from the library. Scheduled copies are untouched either way: they are
     * real occurrences and real logs, and a template going away says nothing about them.
     */
    fun confirmCircuitRemoval() {
        val target = local.value.circuitRemoval ?: return
        local.update { it.copy(circuitRemoval = null) }
        viewModelScope.launch {
            repository.removeRoutine(target.routine.id)
            say("Circuit removed")
        }
    }

    // ------------------------------------------------------------------- modules

    fun duplicateModule(moduleId: String) {
        viewModelScope.launch {
            repository.duplicateModule(moduleId)
            say("Duplicated")
        }
    }

    fun askToRemoveModule(moduleId: String) {
        viewModelScope.launch {
            val impact = repository.moduleRemovalImpact(moduleId)
            local.update { it.copy(moduleRemoval = impact) }
        }
    }

    fun cancelModuleRemoval() = local.update { it.copy(moduleRemoval = null) }

    /** Removes the template. Copies already in weeks are real planned work and stay as they are. */
    fun confirmModuleRemoval() {
        val target = local.value.moduleRemoval ?: return
        local.update { it.copy(moduleRemoval = null) }
        viewModelScope.launch {
            repository.removeModule(target.module.id)
            say("Module removed")
        }
    }

    companion object {
        internal fun plural(count: Int, one: String, many: String): String =
            if (count == 1) one else many

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                LibraryViewModel(application.container.trainingRepository)
            }
        }
    }
}
