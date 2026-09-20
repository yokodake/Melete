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
import com.yokodake.melete.data.ExerciseDraft
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.ExerciseEditorDestination
import com.yokodake.melete.ui.components.PrescriptionFormState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseEditorUiState(
    val exerciseId: String? = null,
    val name: String = "",
    val mode: ExerciseMode = ExerciseMode.REPETITIONS,
    val unilateral: Boolean = false,
    val unit: String = "kg",
    val meaning: MeasurementMeaning = MeasurementMeaning.TOTAL_LOAD,
    val notes: String = "",
    val prescription: PrescriptionFormState = PrescriptionFormState(),
) {
    val isNew: Boolean get() = exerciseId == null
    val canSave: Boolean get() = name.isNotBlank()
}

class ExerciseEditorViewModel(
    private val repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val exerciseId: String? =
        savedStateHandle.toRoute<ExerciseEditorDestination>().exerciseId

    private val _uiState = MutableStateFlow(ExerciseEditorUiState(exerciseId = exerciseId))
    val uiState: StateFlow<ExerciseEditorUiState> = _uiState.asStateFlow()

    init {
        if (exerciseId != null) {
            viewModelScope.launch {
                val exercise = repository.getLibraryExercise(exerciseId) ?: return@launch
                _uiState.value = ExerciseEditorUiState(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    mode = exercise.mode,
                    unilateral = exercise.unilateral,
                    unit = exercise.measurementUnit.orEmpty(),
                    meaning = exercise.measurementMeaning ?: MeasurementMeaning.TOTAL_LOAD,
                    notes = exercise.notes.orEmpty(),
                    prescription = PrescriptionFormState.from(exercise.defaultPrescription),
                )
            }
        }
    }

    fun setName(value: String) = _uiState.update { it.copy(name = value) }

    fun setMode(value: ExerciseMode) = _uiState.update { it.copy(mode = value) }

    fun setUnilateral(value: Boolean) = _uiState.update { it.copy(unilateral = value) }

    fun setUnit(value: String) = _uiState.update { it.copy(unit = value) }

    fun setMeaning(value: MeasurementMeaning) = _uiState.update { it.copy(meaning = value) }

    fun setNotes(value: String) = _uiState.update { it.copy(notes = value) }

    fun setPrescription(value: PrescriptionFormState) = _uiState.update { it.copy(prescription = value) }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return
        val unit = state.unit.trim().takeIf { it.isNotBlank() }
        val draft = ExerciseDraft(
            name = state.name,
            mode = state.mode,
            unilateral = state.unilateral,
            measurementUnit = unit,
            measurementMeaning = unit?.let { state.meaning },
            notes = state.notes,
            defaultPrescription = state.prescription.toPayload(unit, state.meaning),
        )
        viewModelScope.launch {
            if (state.exerciseId == null) {
                repository.createExercise(draft)
            } else {
                repository.updateExercise(state.exerciseId, draft)
            }
            onSaved()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                ExerciseEditorViewModel(
                    application.container.trainingRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
