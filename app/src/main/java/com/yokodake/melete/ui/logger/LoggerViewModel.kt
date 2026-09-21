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
import com.yokodake.melete.data.SetWrite
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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

/**
 * One line of the set table: what the plan asks for, what you are about to record, and whether it
 * has happened.
 *
 * A row exists before anything is logged — that is the point. The plan says four sets, so four
 * lines appear, prefilled, and ticking one is what turns a suggestion into a record. An unticked
 * row is not evidence of anything, which is why prefilling it is safe.
 */
data class SetRow(
    val number: Int,
    /** From the plan, shown but not edited here. */
    val reps: Int?,
    /** The load, or the left-hand load when the exercise is unilateral. */
    val load: String = "",
    val loadRight: String = "",
    /**
     * Whether this set happened.
     *
     * A claim the user makes, not a record that exists: nothing reaches the database until the
     * workout is marked done. Planned rows start true, because the ordinary case is that you did
     * what you planned, and unticking is how you say otherwise.
     */
    val done: Boolean = true,
)

/**
 * The table as a whole.
 *
 * [maxLoad] is a filler and a headline, not a separate record: the heaviest set is the number that
 * matters at a glance, and typing it once is usually the fastest way to fill a session where every
 * set was the same.
 */
data class SetTable(
    val rows: List<SetRow> = emptyList(),
    val maxLoad: String = "",
    val maxLoadRight: String = "",
    val effort: EffortLevel? = null,
    /** True once the sets have been written, so marking done again corrects rather than doubles. */
    val committed: Boolean = false,
) {
    /**
     * The table with its max load read back off the rows.
     *
     * The heaviest set *is* the max load, so it is shown rather than asked for again. Typing into
     * the field still works and still fills the rows; this only keeps the number from sitting
     * empty above a table that plainly answers it.
     */
    fun withDeducedMax(): SetTable {
        fun heaviest(of: (SetRow) -> String): String =
            rows.mapNotNull { of(it).takeIf(String::isNotBlank) }
                .maxByOrNull { it.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY }
                .orEmpty()

        return copy(
            maxLoad = heaviest { it.load }.ifBlank { maxLoad },
            maxLoadRight = heaviest { it.loadRight }.ifBlank { maxLoadRight },
        )
    }

    /** The rows that claim to have happened. Only these are ever written. */
    val doneRows: List<SetRow> get() = rows.filter { it.done }

    /**
     * Whether there is enough here to log, for an exercise that measures a load.
     *
     * Either every set says what it weighed, or the max load does and stands for all of them.
     * Anything less would be recording that work happened without recording what the work was.
     */
    fun loggable(measured: Boolean): Boolean {
        if (!measured) return doneRows.isNotEmpty()
        if (doneRows.isEmpty()) return false
        if (maxLoad.isNotBlank()) return true
        return doneRows.all { it.load.isNotBlank() }
    }

    /** What is missing, said plainly, or null when nothing is. */
    fun blocker(measured: Boolean): String? = when {
        doneRows.isEmpty() -> "Tick at least one set"
        !measured -> null
        loggable(measured = true) -> null
        else -> "Fill in the max load, or every set's load"
    }

    /**
     * The table as it will be written: a row with no load of its own falls back to the max load.
     *
     * This is where "ticked, but nothing typed in the rows" becomes a real number, so the fallback
     * lives in one place rather than being applied differently by each caller.
     */
    fun resolved(): List<SetRow> = doneRows.map { row ->
        row.copy(
            load = row.load.ifBlank { maxLoad },
            loadRight = row.loadRight.ifBlank { maxLoadRight.ifBlank { maxLoad } },
        )
    }
}

data class LoggerUiState(
    val loading: Boolean = true,
    val occurrence: PlannedOccurrence? = null,
    val sets: List<PerformedSet> = emptyList(),
    val previousResults: List<PreviousResult> = emptyList(),
    val draft: SetDraft = SetDraft(),
    val table: SetTable = SetTable(),
    /** The training date the next confirmed set will be filed under. */
    val targetDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    /** Last set saved in this screen, offered for a one-tap undo. */
    val undoableSetId: String? = null,
    val prescriptionEditor: PrescriptionFormState? = null,
    /** The note as it currently reads: the edit in progress, or what is stored. */
    val comment: String = "",
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
    private val table = MutableStateFlow(SetTable())

    /** Emitted once the workout has been written, so the screen knows to close itself. */
    val finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val transient = MutableStateFlow(TransientState())
    private var prefilled = false
    private var seeded = false

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
            combine(previous, draft, transient, table) { previousResults, currentDraft, extras, rows ->
                build(occurrenceDetail, previousResults, currentDraft, extras, rows)
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
            seedTable(first, previous)
        }
    }

    private fun build(
        occurrenceDetail: OccurrenceDetail?,
        previousResults: List<PreviousResult>,
        currentDraft: SetDraft,
        extras: TransientState,
        rows: SetTable,
    ): LoggerUiState {
        val today = LocalDate.now(clock)
        return LoggerUiState(
            loading = occurrenceDetail == null,
            occurrence = occurrenceDetail?.occurrence,
            sets = occurrenceDetail?.sets.orEmpty(),
            previousResults = previousResults,
            draft = currentDraft,
            table = rows,
            targetDate = extras.targetDate
                ?: occurrenceDetail?.occurrence?.trainingDate
                ?: today,
            today = today,
            undoableSetId = extras.undoableSetId,
            prescriptionEditor = extras.prescriptionEditor,
            comment = extras.commentEditor ?: occurrenceDetail?.occurrence?.comment.orEmpty(),
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

    // ------------------------------------------------------------- table

    /**
     * Builds the table once, from the plan and from whatever is already recorded.
     *
     * Seeded rather than derived on every emission because the user types into it: recomputing it
     * from the database would throw away a half-entered load the moment anything else changed.
     */
    private fun seedTable(detail: OccurrenceDetail, previousResults: List<PreviousResult>) {
        if (seeded) return
        seeded = true
        val occurrence = detail.occurrence
        val planned = occurrence.prescription?.sets ?: 0
        val recorded = detail.sets.sortedBy { it.orderIndex }
        // A unilateral row wrote two sets, left then right, so they come back in pairs.
        val grouped = if (occurrence.unilateral) recorded.chunked(2) else recorded.map { listOf(it) }

        // What was already written, if this workout has been logged before. Reopening it shows
        // what it said, so that marking done again corrects the record rather than adding to it.
        val count = maxOf(planned, grouped.size).coerceAtLeast(1)
        table.value = SetTable(
            rows = (0 until count).map { index ->
                val written = grouped.getOrNull(index)
                SetRow(
                    number = index + 1,
                    reps = occurrence.prescription?.targetReps,
                    load = written?.getOrNull(0)?.load().orEmpty(),
                    loadRight = written?.getOrNull(1)?.load().orEmpty(),
                    // Everything planned starts ticked; a reopened log shows what it has.
                    done = if (recorded.isEmpty()) true else written != null,
                )
            },
            // The plan says how hard this was meant to feel, so that is what the logger starts
            // from; a reopened log shows what it was actually rated.
            effort = recorded.firstOrNull()?.payload?.effort ?: occurrence.prescription?.effort,
            committed = recorded.isNotEmpty(),
        ).withDeducedMax()
    }

    private fun PerformedSet.load(): String? = payload.measurement?.value?.let(::trimNumber)

    /**
     * Sets one row's load, and carries it down to every row below that has not been ticked yet.
     *
     * Usually one set is the odd one out and the rest follow it, so typing the change once and
     * having the remainder agree is the whole ergonomic point. Rows already recorded are left
     * alone: those are history, and history does not get rewritten by typing above it.
     */
    /**
     * Sets a row's load, and lets every row below it follow.
     *
     * A load typed once describes the rest of the session; a set that differs is corrected in its
     * own row and carries downward from there. Nothing here touches the database: the table is a
     * draft until the workout is marked done.
     */
    fun setRowLoad(number: Int, value: String, right: Boolean = false) {
        table.update { current ->
            current.copy(
                rows = current.rows.map { row ->
                    if (row.number < number) row else row.withLoad(value, right)
                }
            ).withDeducedMax()
        }
    }

    /** Fills every row that has not been ticked yet. */
    fun setMaxLoad(value: String, right: Boolean = false) {
        table.update { current ->
            val rows = current.rows.map { it.withLoad(value, right) }
            if (right) {
                current.copy(maxLoadRight = value, rows = rows)
            } else {
                current.copy(maxLoad = value, rows = rows)
            }
        }
    }

    private fun SetRow.withLoad(value: String, right: Boolean) =
        if (right) copy(loadRight = value) else copy(load = value)

    fun setTableEffort(effort: EffortLevel?) {
        table.update { it.copy(effort = effort) }
    }

    /**
     * Records the row, or takes it back.
     *
     * A unilateral row writes two actual sets, one per side, because left and right are separately
     * performed work even though they are one line on screen. Unticking deletes exactly what that
     * row wrote and nothing else.
     */
    /** Says whether a set happened. Costs nothing until the workout is marked done. */
    fun toggleRow(number: Int) {
        table.update { current ->
            current.copy(
                rows = current.rows.map {
                    if (it.number == number) it.copy(done = !it.done) else it
                }
            )
        }
    }

    private suspend fun record(
        occurrence: PlannedOccurrence,
        row: SetRow,
        load: String,
        side: BodySide?,
        trainingDate: LocalDate,
    ): String = repository.logSet(
        occurrenceId = occurrenceId,
        payload = payloadFor(occurrence, row, load),
        side = side,
        trainingDate = trainingDate,
    )

    private fun payloadFor(
        occurrence: PlannedOccurrence,
        row: SetRow,
        load: String,
    ): ActualSetPayload = ActualSetPayload(
        reps = row.reps,
        measurement = load.toDoubleOrNull()?.let { value ->
            occurrence.measurementUnit?.let { unit ->
                Measurement(
                    value,
                    unit,
                    occurrence.measurementMeaning ?: MeasurementMeaning.TOTAL_LOAD,
                )
            }
        },
        effort = uiState.value.table.effort,
    )

    /** An extra set beyond the plan. Four planned and six performed is a perfectly good session. */
    fun addRow() {
        table.update { current ->
            val last = current.rows.lastOrNull()
            current.copy(
                rows = current.rows + SetRow(
                    number = (last?.number ?: 0) + 1,
                    reps = last?.reps,
                    load = last?.load.orEmpty(),
                    loadRight = last?.loadRight.orEmpty(),
                )
            )
        }
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
                // Confirming a set never starts a countdown by itself: resting is offered, not
                // imposed, and the timer must stay independent of what was recorded.
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
     * Marks the workout done: writes what the table says, and finishes.
     *
     * This is the moment the session becomes a record. Everything before it is a draft that can be
     * ticked, unticked and retyped freely, which is why nothing is written as you go: a half-filled
     * table is not a claim about anything.
     *
     * Marking done a second time replaces what was written rather than adding to it, so correcting
     * a logged workout is the same gesture as logging it.
     */
    fun markDone() {
        val state = uiState.value
        val occurrence = state.occurrence ?: return
        val measured = occurrence.measurementUnit != null
        val blocker = state.table.blocker(measured)
        if (blocker != null) {
            transient.update { it.copy(message = blocker) }
            return
        }
        val rows = state.table.resolved()
        viewModelScope.launch {
            repository.replaceSetsForOccurrence(
                occurrenceId = occurrenceId,
                trainingDate = state.targetDate,
                sets = rows.flatMap { row -> writesFor(occurrence, row) },
            )
            repository.setOccurrenceComment(occurrenceId, state.comment)
            repository.setOccurrenceState(occurrenceId, OccurrenceState.COMPLETED)
            finished.emit(Unit)
        }
    }

    /** One row becomes one set, or a left/right pair when the exercise is unilateral. */
    private fun writesFor(
        occurrence: PlannedOccurrence,
        row: SetRow,
    ): List<SetWrite> = if (occurrence.unilateral) {
        listOf(
            SetWrite(payloadFor(occurrence, row, row.load), BodySide.LEFT),
            SetWrite(payloadFor(occurrence, row, row.loadRight), BodySide.RIGHT),
        )
    } else {
        listOf(SetWrite(payloadFor(occurrence, row, row.load), null))
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
            repository.updateOccurrencePrescription(occurrenceId, form.toPayload())
            transient.update { it.copy(prescriptionEditor = null) }
        }
    }

    /**
     * Types into the note.
     *
     * Held with the rest of the draft and written when the workout is marked done, so that one
     * button still accounts for everything this screen has to say.
     */
    fun updateComment(value: String) {
        transient.update { it.copy(commentEditor = value) }
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
