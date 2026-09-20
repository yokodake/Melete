package com.yokodake.melete.ui.logger

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.OccurrenceDetail
import com.yokodake.melete.data.PerformedSet
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.PreviousResult
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.LoggerDestination
import com.yokodake.melete.ui.components.PrescriptionFormState
import com.yokodake.melete.ui.components.trimNumber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate

/**
 * The in-progress set. Kept as typed text and stored in [SavedStateHandle], so a half-entered set
 * survives navigating away, rotation and process recreation. Nothing here is recorded until the
 * user confirms it: a prefilled value is a suggestion, never performed work.
 */
@Serializable
data class SetDraft(
    val reps: String = "",
    val durationSeconds: String = "",
    val measurement: String = "",
    val effort: EffortLevel? = null,
    val side: BodySide? = null,
    /** Set when the draft is a correction of an already recorded set. */
    val editingSetId: String? = null,
    val showEffortFields: Boolean = false,
) {
    fun toPayload(unit: String?, meaning: MeasurementMeaning?): ActualSetPayload = ActualSetPayload(
        reps = reps.toIntOrNull(),
        durationSeconds = durationSeconds.toIntOrNull(),
        measurement = measurement.toDoubleOrNull()?.let { value ->
            unit?.takeIf { it.isNotBlank() }?.let {
                Measurement(value, it, meaning ?: MeasurementMeaning.TOTAL_LOAD)
            }
        },
        effort = effort,
    )
}

data class LoggerUiState(
    val loading: Boolean = true,
    val occurrence: PlannedOccurrence? = null,
    val sets: List<PerformedSet> = emptyList(),
    val previousResults: List<PreviousResult> = emptyList(),
    val draft: SetDraft = SetDraft(),
    /** The training date the next confirmed set will be filed under. */
    val targetDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    /** Last set saved in this screen, offered for a one-tap undo. */
    val undoableSetId: String? = null,
    val prescriptionEditor: PrescriptionFormState? = null,
    val commentEditor: String? = null,
    val message: String? = null,
) {
    val canConfirm: Boolean
        get() = occurrence != null && !draft.toPayload(
            occurrence.measurementUnit,
            occurrence.measurementMeaning,
        ).isEmpty
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoggerViewModel(
    private val repository: TrainingRepository,
    private val savedStateHandle: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val occurrenceId: String = savedStateHandle.toRoute<LoggerDestination>().occurrenceId

    private val draft = MutableStateFlow(savedStateHandle.readDraft())
    private val transient = MutableStateFlow(TransientState())
    private var prefilled = false

    private data class TransientState(
        val targetDate: LocalDate? = null,
        val undoableSetId: String? = null,
        val prescriptionEditor: PrescriptionFormState? = null,
        val commentEditor: String? = null,
        val message: String? = null,
    )

    private val detail = repository.observeOccurrence(occurrenceId)

    val uiState: StateFlow<LoggerUiState> = detail
        .flatMapLatest { occurrenceDetail ->
            val previous = occurrenceDetail
                ?.let { repository.observePreviousResults(it.occurrence.exerciseId, occurrenceId) }
                ?: flowOf(emptyList())
            combine(previous, draft, transient) { previousResults, currentDraft, extras ->
                build(occurrenceDetail, previousResults, currentDraft, extras)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LoggerUiState(today = LocalDate.now(clock)),
        )

    init {
        // Prefill once, from the prescription or from what was done last time, and only into the
        // draft. Suggested values are never written to the record on their own.
        viewModelScope.launch {
            val first = detail.first { it != null } ?: return@launch
            if (prefilled || draft.value != SetDraft()) return@launch
            prefilled = true
            val previous = repository
                .observePreviousResults(first.occurrence.exerciseId, occurrenceId).first()
            draft.value = initialDraft(first, previous)
            persistDraft()
        }
    }

    private fun build(
        occurrenceDetail: OccurrenceDetail?,
        previousResults: List<PreviousResult>,
        currentDraft: SetDraft,
        extras: TransientState,
    ): LoggerUiState {
        val today = LocalDate.now(clock)
        return LoggerUiState(
            loading = occurrenceDetail == null,
            occurrence = occurrenceDetail?.occurrence,
            sets = occurrenceDetail?.sets.orEmpty(),
            previousResults = previousResults,
            draft = currentDraft,
            targetDate = extras.targetDate
                ?: occurrenceDetail?.occurrence?.trainingDate
                ?: today,
            today = today,
            undoableSetId = extras.undoableSetId,
            prescriptionEditor = extras.prescriptionEditor,
            commentEditor = extras.commentEditor,
            message = extras.message,
        )
    }

    private fun initialDraft(
        detail: OccurrenceDetail,
        previousResults: List<PreviousResult>,
    ): SetDraft {
        val occurrence = detail.occurrence
        val lastHere = detail.sets.maxByOrNull { it.orderIndex }
        val lastEver = previousResults.firstOrNull()?.sets?.maxByOrNull { it.orderIndex }
        val source = lastHere ?: lastEver
        val prescription = occurrence.prescription
        val side = if (occurrence.unilateral) nextSide(detail.sets) else null
        return if (source != null) {
            SetDraft(
                reps = source.payload.reps?.toString()
                    ?: prescription?.targetReps?.toString().orEmpty(),
                durationSeconds = source.payload.durationSeconds?.toString()
                    ?: prescription?.targetDurationSeconds?.toString().orEmpty(),
                measurement = source.payload.measurement?.value?.let(::trimNumber)
                    ?: prescription?.measurement?.value?.let(::trimNumber).orEmpty(),
                side = side,
            )
        } else {
            SetDraft(
                reps = prescription?.targetReps?.toString().orEmpty(),
                durationSeconds = prescription?.targetDurationSeconds?.toString().orEmpty(),
                measurement = prescription?.measurement?.value?.let(::trimNumber).orEmpty(),
                side = side,
            )
        }
    }

    /**
     * The side a unilateral exercise is due to train next: the other side of the last recorded
     * set, or left when nothing has been recorded. Confirming one side never marks the other.
     */
    private fun nextSide(sets: List<PerformedSet>): BodySide {
        val last = sets.maxByOrNull { it.orderIndex } ?: return BodySide.LEFT
        return if (last.side == BodySide.LEFT) BodySide.RIGHT else BodySide.LEFT
    }

    // ------------------------------------------------------------- draft

    fun updateDraft(transform: (SetDraft) -> SetDraft) {
        draft.update(transform)
        persistDraft()
    }

    fun setSide(side: BodySide) = updateDraft { it.copy(side = side) }

    fun setEffort(effort: EffortLevel?) = updateDraft { it.copy(effort = effort) }

    fun toggleEffortFields() = updateDraft { it.copy(showEffortFields = !it.showEffortFields) }

    private fun persistDraft() {
        savedStateHandle[DRAFT_KEY] = Json.encodeToString(draft.value)
    }

    /** Saves the confirmed set immediately. No session needs to be started or ended. */
    fun confirmSet() {
        val state = uiState.value
        val occurrence = state.occurrence ?: return
        val payload = state.draft.toPayload(occurrence.measurementUnit, occurrence.measurementMeaning)
        if (payload.isEmpty) return
        val editingId = state.draft.editingSetId
        viewModelScope.launch {
            if (editingId != null) {
                repository.updateSet(editingId, payload, state.draft.side)
                transient.update { it.copy(undoableSetId = null, message = "Set corrected") }
                draft.value = state.draft.copy(editingSetId = null)
            } else {
                val id = repository.logSet(
                    occurrenceId = occurrenceId,
                    payload = payload,
                    side = state.draft.side,
                    trainingDate = state.targetDate,
                )
                transient.update { it.copy(undoableSetId = id, message = null) }
                // Keep the values for the next set; for unilateral work move to the other side
                // without implying that it has already been done.
                draft.value = state.draft.copy(
                    side = state.draft.side?.let {
                        if (it == BodySide.LEFT) BodySide.RIGHT else BodySide.LEFT
                    },
                )
            }
            persistDraft()
        }
    }

    fun undoLastSet() {
        val id = transient.value.undoableSetId ?: return
        viewModelScope.launch {
            repository.deleteSet(id)
            transient.update { it.copy(undoableSetId = null, message = "Set removed") }
        }
    }

    fun editSet(set: PerformedSet) {
        draft.value = SetDraft(
            reps = set.payload.reps?.toString().orEmpty(),
            durationSeconds = set.payload.durationSeconds?.toString().orEmpty(),
            measurement = set.payload.measurement?.value?.let(::trimNumber).orEmpty(),
            effort = set.payload.effort,
            side = set.side,
            editingSetId = set.id,
            showEffortFields = set.payload.effort != null,
        )
        persistDraft()
    }

    fun cancelEdit() {
        draft.update { it.copy(editingSetId = null) }
        persistDraft()
    }

    fun deleteSet(setId: String) {
        viewModelScope.launch {
            repository.deleteSet(setId)
            if (draft.value.editingSetId == setId) cancelEdit()
            transient.update {
                it.copy(
                    undoableSetId = if (it.undoableSetId == setId) null else it.undoableSetId,
                    message = "Set removed",
                )
            }
        }
    }

    // -------------------------------------------------------- occurrence

    /**
     * Chooses the training date an unscheduled item will be filed under, for backfilling. The
     * occurrence itself is only moved when the first set is confirmed. Moving work that already
     * has a date is deliberately not part of this phase: that needs the explicit distinction
     * between rescheduling remaining work and correcting a historical date.
     */
    fun setTargetDate(date: LocalDate) {
        transient.update { it.copy(targetDate = date) }
    }

    /**
     * Takes a mistakenly scheduled exercise back out of the week. Refused once anything has been
     * recorded against it: removing evidence is never a side effect of tidying a plan.
     */
    fun removeOccurrence(onRemoved: () -> Unit) {
        viewModelScope.launch {
            if (repository.deleteOccurrenceIfEmpty(occurrenceId)) {
                onRemoved()
            } else {
                transient.update {
                    it.copy(message = "Delete the recorded sets first")
                }
            }
        }
    }

    fun markState(state: OccurrenceState) {
        viewModelScope.launch { repository.setOccurrenceState(occurrenceId, state) }
    }

    fun openPrescriptionEditor() {
        val occurrence = uiState.value.occurrence ?: return
        transient.update {
            it.copy(prescriptionEditor = PrescriptionFormState.from(occurrence.prescription))
        }
    }

    fun updatePrescriptionEditor(state: PrescriptionFormState) {
        transient.update { it.copy(prescriptionEditor = state) }
    }

    fun dismissPrescriptionEditor() {
        transient.update { it.copy(prescriptionEditor = null) }
    }

    fun savePrescription() {
        val occurrence = uiState.value.occurrence ?: return
        val form = transient.value.prescriptionEditor ?: return
        viewModelScope.launch {
            repository.updateOccurrencePrescription(
                occurrenceId,
                form.toPayload(occurrence.measurementUnit, occurrence.measurementMeaning),
            )
            transient.update { it.copy(prescriptionEditor = null) }
        }
    }

    fun openCommentEditor() {
        transient.update { it.copy(commentEditor = uiState.value.occurrence?.comment.orEmpty()) }
    }

    fun updateCommentEditor(value: String) {
        transient.update { it.copy(commentEditor = value) }
    }

    fun dismissCommentEditor() {
        transient.update { it.copy(commentEditor = null) }
    }

    fun saveComment() {
        val comment = transient.value.commentEditor ?: return
        viewModelScope.launch {
            repository.setOccurrenceComment(occurrenceId, comment)
            transient.update { it.copy(commentEditor = null) }
        }
    }

    fun consumeMessage() {
        transient.update { it.copy(message = null) }
    }

    companion object {
        private const val DRAFT_KEY = "logger-draft"

        private fun SavedStateHandle.readDraft(): SetDraft =
            get<String>(DRAFT_KEY)
                ?.let { runCatching { Json.decodeFromString<SetDraft>(it) }.getOrNull() }
                ?: SetDraft()

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                LoggerViewModel(
                    application.container.trainingRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}

/** True when the exercise records a duration rather than repetitions. */
val ExerciseMode.isTimed: Boolean
    get() = this == ExerciseMode.DURATION || this == ExerciseMode.ACTIVITY
