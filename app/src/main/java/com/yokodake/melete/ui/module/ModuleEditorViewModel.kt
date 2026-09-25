package com.yokodake.melete.ui.module

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
import com.yokodake.melete.data.ModuleDraft
import com.yokodake.melete.data.ModuleEntryDraft
import com.yokodake.melete.data.Routine
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.ui.CREATED_EXERCISE_ID
import com.yokodake.melete.ui.ModuleEditorDestination
import com.yokodake.melete.ui.components.PrescriptionFormState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * One entry as the editor holds it: an exercise with this module's own copy of its plan, or a
 * saved circuit, which is copied into the week as it stands when the module is scheduled.
 */
data class ModuleEntryUi(
    /** Stable across reorders, for the list's keys; never stored. */
    val key: String = UUID.randomUUID().toString(),
    val exerciseId: String? = null,
    val variationId: String? = null,
    val variationTag: String? = null,
    val routineId: String? = null,
    val name: String,
    val mode: ExerciseMode = ExerciseMode.REPETITIONS,
    val unilateral: Boolean = false,
    val category: ExerciseCategory? = null,
    val form: PrescriptionFormState = PrescriptionFormState(),
    /** For a circuit: its shape in one line. */
    val circuitSummary: String? = null,
    val expanded: Boolean = false,
) {
    val isCircuit: Boolean get() = routineId != null
}

/** Which list the add dialog is showing. */
enum class ModulePick { EXERCISE, CIRCUIT }

data class ModuleEditorUiState(
    val loading: Boolean = true,
    val existing: Boolean = false,
    val name: String = "",
    val description: String = "",
    val entries: List<ModuleEntryUi> = emptyList(),
    val library: List<LibraryExercise> = emptyList(),
    val circuits: List<Routine> = emptyList(),
    val picking: ModulePick? = null,
    /** An exercise with variations, waiting for its plan to be chosen. */
    val choosingPlanFor: LibraryExercise? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && entries.isNotEmpty()

    fun toDraft(): ModuleDraft = ModuleDraft(
        name = name,
        description = description,
        entries = entries.map { entry ->
            if (entry.isCircuit) {
                ModuleEntryDraft(routineId = entry.routineId)
            } else {
                ModuleEntryDraft(
                    exerciseId = entry.exerciseId,
                    variationId = entry.variationId,
                    prescription = entry.form.toPayload(entry.mode),
                )
            }
        },
    )
}

/**
 * Making or changing a module: a name, an optional description, and an ordered list of
 * exercises and circuits.
 *
 * Each exercise carries its own copy of the plan, seeded from the library default or the variation
 * chosen, so editing it here changes this module only.
 */
class ModuleEditorViewModel(
    private val repository: TrainingRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val moduleId: String? = savedStateHandle.toRoute<ModuleEditorDestination>().moduleId

    private val form = MutableStateFlow(ModuleEditorUiState(loading = moduleId != null))

    val uiState: StateFlow<ModuleEditorUiState> = combine(
        form,
        repository.observeLibrary(),
        repository.observeRoutines(),
    ) { current, library, circuits ->
        current.copy(existing = moduleId != null, library = library, circuits = circuits)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModuleEditorUiState(loading = moduleId != null),
    )

    init {
        // An exercise created from the add dialog comes back as an id on this screen's saved
        // state and becomes the next entry. Cleared once used, so it is added only once.
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(CREATED_EXERCISE_ID, null)
                .filterNotNull()
                .collect { createdId ->
                    savedStateHandle[CREATED_EXERCISE_ID] = null
                    repository.getLibraryExercise(createdId)?.let { addExercise(it, null) }
                }
        }
        if (moduleId != null) {
            viewModelScope.launch {
                val module = repository.getModule(moduleId)
                form.update { current ->
                    if (module == null) return@update current.copy(loading = false)
                    current.copy(
                        loading = false,
                        name = module.name,
                        description = module.description.orEmpty(),
                        entries = module.entries.map { entry ->
                            ModuleEntryUi(
                                exerciseId = entry.exerciseId,
                                variationId = entry.variationId,
                                variationTag = entry.variationTag,
                                routineId = entry.routineId,
                                name = entry.name,
                                mode = entry.mode,
                                unilateral = entry.unilateral,
                                category = entry.category,
                                form = PrescriptionFormState.from(entry.prescription),
                                circuitSummary = entry.routine?.let(::circuitLine),
                            )
                        },
                    )
                }
            }
        }
    }

    fun setName(value: String) = form.update { it.copy(name = value) }
    fun setDescription(value: String) = form.update { it.copy(description = value) }

    fun openPicker(kind: ModulePick) = form.update { it.copy(picking = kind) }

    fun dismissPicker() = form.update { it.copy(picking = null, choosingPlanFor = null) }

    /** Leaves the picker to create an exercise; it comes back as the next entry. */
    fun createExercise(onOpenEditor: () -> Unit) {
        dismissPicker()
        onOpenEditor()
    }

    /** An exercise picked from the list: added at once, or after its plan is chosen. */
    fun pickExercise(exercise: LibraryExercise) {
        if (exercise.variations.isEmpty()) {
            addExercise(exercise, null)
        } else {
            form.update { it.copy(picking = null, choosingPlanFor = exercise) }
        }
    }

    fun choosePlan(variationId: String?) {
        val exercise = form.value.choosingPlanFor ?: return
        addExercise(exercise, variationId)
    }

    private fun addExercise(exercise: LibraryExercise, variationId: String?) {
        val variation = exercise.variations.firstOrNull { it.id == variationId }
        form.update { current ->
            current.copy(
                picking = null,
                choosingPlanFor = null,
                entries = current.entries + ModuleEntryUi(
                    exerciseId = exercise.id,
                    variationId = variation?.id,
                    variationTag = variation?.tag,
                    name = exercise.name,
                    mode = exercise.mode,
                    unilateral = exercise.unilateral,
                    category = exercise.category,
                    // Seeded, not linked: the copy is this module's from the moment it is added.
                    form = PrescriptionFormState.from(
                        if (variation != null) variation.prescription else exercise.defaultPrescription
                    ),
                ),
            )
        }
    }

    fun pickCircuit(circuit: Routine) {
        form.update { current ->
            current.copy(
                picking = null,
                entries = current.entries + ModuleEntryUi(
                    routineId = circuit.id,
                    name = circuit.name,
                    circuitSummary = circuitLine(circuit),
                ),
            )
        }
    }

    fun removeEntry(index: Int) {
        form.update { it.copy(entries = it.entries.filterIndexed { i, _ -> i != index }) }
    }

    fun moveEntry(index: Int, delta: Int) {
        form.update { current ->
            val target = index + delta
            if (index !in current.entries.indices || target !in current.entries.indices) {
                return@update current
            }
            val reordered = current.entries.toMutableList()
            reordered.add(target, reordered.removeAt(index))
            current.copy(entries = reordered)
        }
    }

    fun toggleEntry(index: Int) {
        form.update { current ->
            current.copy(
                entries = current.entries.mapIndexed { i, entry ->
                    if (i == index) entry.copy(expanded = !entry.expanded) else entry
                }
            )
        }
    }

    fun updateEntryForm(index: Int, value: PrescriptionFormState) {
        form.update { current ->
            current.copy(
                entries = current.entries.mapIndexed { i, entry ->
                    if (i == index) entry.copy(form = value) else entry
                }
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val id = moduleId
            if (id == null) repository.createModule(state.toDraft())
            else repository.updateModule(id, state.toDraft())
            onSaved()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                ModuleEditorViewModel(
                    application.container.trainingRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}

/** A circuit in one line: how many times round and what is in it. */
internal fun circuitLine(circuit: Routine): String =
    "${circuit.rounds} × " + circuit.entries.joinToString(" → ") { it.name }.ifEmpty { "empty" }
