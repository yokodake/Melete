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
import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.data.RoutineDraft
import com.yokodake.melete.data.RoutineEntryDraft
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.timer.PrescriptionProgram
import com.yokodake.melete.data.timer.StationPlan
import com.yokodake.melete.ui.CREATED_EXERCISE_ID
import com.yokodake.melete.ui.RoutineEditorDestination
import com.yokodake.melete.ui.components.PrescriptionFormState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One station as the editor holds it: the library exercise it came from, and this circuit's own
 * copy of the numbers.
 *
 * The prescription is a copy from the moment the station is added, so editing the hang in this
 * circuit never reaches the library default, a standalone plan, or another circuit's hang.
 */
data class StationDraft(
    val exerciseId: String,
    val name: String,
    val mode: ExerciseMode,
    val unilateral: Boolean,
    val form: PrescriptionFormState,
    val expanded: Boolean = false,
)

data class RoutineEditorUiState(
    val loading: Boolean = true,
    val existing: Boolean = false,
    val name: String = "",
    val rounds: String = "3",
    val transitionSeconds: String = "30",
    val roundRestSeconds: String = "120",
    val stations: List<StationDraft> = emptyList(),
    val library: List<LibraryExercise> = emptyList(),
    val picking: Boolean = false,
    val category: ExerciseCategory? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && stations.isNotEmpty()

    /**
     * How long the whole circuit will take, from the sequence it will actually run.
     *
     * Computed once from the circuit, never by summing what each station would take standing
     * alone: in a circuit a station does one set per round and rests by the circuit's rules.
     */
    val estimatedSeconds: Int?
        get() {
            if (stations.isEmpty()) return null
            return PrescriptionProgram.circuit(
                label = name,
                rounds = rounds.toIntOrNull() ?: 1,
                transitionSeconds = transitionSeconds.toIntOrNull() ?: 0,
                roundRestSeconds = roundRestSeconds.toIntOrNull() ?: 0,
                stations = stations.map {
                    StationPlan(it.name, it.mode, it.unilateral, it.form.toPayload(it.mode))
                },
            ).estimatedSeconds()
        }

    fun toDraft(): RoutineDraft = RoutineDraft(
        name = name,
        rounds = rounds.toIntOrNull() ?: 1,
        transitionSeconds = transitionSeconds.toIntOrNull() ?: 0,
        roundRestSeconds = roundRestSeconds.toIntOrNull() ?: 0,
        entries = stations.map { RoutineEntryDraft(it.exerciseId, it.form.toPayload(it.mode)) },
        category = category,
    )
}

/**
 * Making or changing a circuit.
 *
 * Editing it never touches anything already scheduled: saving writes fresh prescription rows and
 * bumps the routine's structure version, and the copies in weeks keep pointing at what they were
 * given. That is the same rule as an exercise's default plan, for the same reason.
 */
class RoutineEditorViewModel(
    private val repository: TrainingRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routineId: String? =
        savedStateHandle.toRoute<RoutineEditorDestination>().routineId

    private val form = MutableStateFlow(Form())

    private data class Form(
        val loaded: Boolean = false,
        val name: String = "",
        val rounds: String = "3",
        val transitionSeconds: String = "30",
        val roundRestSeconds: String = "120",
        val stations: List<StationDraft> = emptyList(),
        val picking: Boolean = false,
        val category: ExerciseCategory? = null,
    )

    val uiState: StateFlow<RoutineEditorUiState> =
        combine(form, repository.observeLibrary()) { current, library ->
            RoutineEditorUiState(
                loading = routineId != null && !current.loaded,
                existing = routineId != null,
                name = current.name,
                rounds = current.rounds,
                transitionSeconds = current.transitionSeconds,
                roundRestSeconds = current.roundRestSeconds,
                stations = current.stations,
                library = library,
                picking = current.picking,
                category = current.category,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RoutineEditorUiState(loading = routineId != null),
        )

    init {
        // An exercise created from the picker comes back here as an id on this screen's saved
        // state, and becomes the next station — that is what the detour was for. Cleared once
        // used, so returning to this screen later does not add it a second time.
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(CREATED_EXERCISE_ID, null)
                .filterNotNull()
                .collect { createdId ->
                    savedStateHandle[CREATED_EXERCISE_ID] = null
                    repository.getLibraryExercise(createdId)?.let(::addStation)
                }
        }
        val id = routineId
        if (id == null) {
            form.update { it.copy(loaded = true) }
        } else {
            viewModelScope.launch {
                val routine = repository.observeRoutine(id).first()
                form.value = Form(
                    loaded = true,
                    name = routine?.name.orEmpty(),
                    rounds = routine?.rounds?.toString() ?: "3",
                    transitionSeconds = routine?.transitionSeconds?.toString() ?: "30",
                    roundRestSeconds = routine?.roundRestSeconds?.toString() ?: "120",
                    category = routine?.category,
                    stations = routine?.entries.orEmpty().map { entry ->
                        StationDraft(
                            exerciseId = entry.exerciseId,
                            name = entry.name,
                            mode = entry.mode,
                            unilateral = entry.unilateral,
                            form = PrescriptionFormState.from(entry.prescription),
                        )
                    },
                )
            }
        }
    }

    fun setName(value: String) = form.update { it.copy(name = value) }

    /** Tapping the selected category clears it again: a circuit may simply have none. */
    fun setCategory(value: ExerciseCategory?) = form.update { it.copy(category = value) }

    fun setRounds(value: String) = form.update { it.copy(rounds = value.digits(2)) }

    fun setTransitionSeconds(value: String) =
        form.update { it.copy(transitionSeconds = value.digits(4)) }

    fun setRoundRestSeconds(value: String) =
        form.update { it.copy(roundRestSeconds = value.digits(4)) }

    fun openPicker() = form.update { it.copy(picking = true) }

    fun dismissPicker() = form.update { it.copy(picking = false) }

    /**
     * Leaves the picker to create an exercise. The picker closes rather than waiting underneath,
     * because the new exercise is added as a station on return and there is nothing left to pick.
     */
    fun createExercise(onOpenEditor: () -> Unit) {
        dismissPicker()
        onOpenEditor()
    }

    /**
     * Adds a station, seeded from the library default.
     *
     * Seeded, not linked: the copy is made here so that the first edit to it is already private to
     * this circuit, rather than the plan changing meaning the moment the default does.
     */
    fun addStation(exercise: LibraryExercise) {
        form.update { current ->
            current.copy(
                picking = false,
                stations = current.stations + StationDraft(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    mode = exercise.mode,
                    unilateral = exercise.unilateral,
                    form = PrescriptionFormState.from(exercise.defaultPrescription)
                        // A circuit says how many times round; a station's own set count would be
                        // a second volume number that the sequencer deliberately ignores.
                        .copy(sets = "1"),
                ),
            )
        }
    }

    fun removeStation(index: Int) {
        form.update { it.copy(stations = it.stations.filterIndexed { i, _ -> i != index }) }
    }

    fun moveStation(index: Int, delta: Int) {
        form.update { current ->
            val target = index + delta
            if (index !in current.stations.indices || target !in current.stations.indices) {
                return@update current
            }
            val reordered = current.stations.toMutableList()
            reordered.add(target, reordered.removeAt(index))
            current.copy(stations = reordered)
        }
    }

    fun toggleStation(index: Int) {
        form.update { current ->
            current.copy(
                stations = current.stations.mapIndexed { i, station ->
                    if (i == index) station.copy(expanded = !station.expanded) else station
                }
            )
        }
    }

    fun updateStationForm(index: Int, value: PrescriptionFormState) {
        form.update { current ->
            current.copy(
                stations = current.stations.mapIndexed { i, station ->
                    if (i == index) station.copy(form = value) else station
                }
            )
        }
    }

    /** Saves, then reports the new circuit's id — or null when an existing one was edited. */
    fun save(onSaved: (createdId: String?) -> Unit) {
        val state = uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val id = routineId
            if (id == null) {
                onSaved(repository.createRoutine(state.toDraft()))
            } else {
                repository.updateRoutine(id, state.toDraft())
                onSaved(null)
            }
        }
    }

    private fun String.digits(max: Int) = filter(Char::isDigit).take(max)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                RoutineEditorViewModel(
                    application.container.trainingRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
