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
import com.yokodake.melete.data.OccurrenceLogWrite
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

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
    /**
     * How long it took, in whole minutes, as typed.
     *
     * Empty is not zero: it means the app should work the number out from the plan, and the grey
     * value in the field is what it would save. Typing makes the number yours; clearing it hands
     * the question back.
     */
    val durationMinutes: String = "",
    /** Whether the duration row is on screen. Off by default for anything with a set table. */
    val showDuration: Boolean = false,
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
    val table: SetTable = SetTable(),
    /** The training date this workout will be filed under. */
    val targetDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val prescriptionEditor: PrescriptionFormState? = null,
    /** The note as it currently reads: the edit in progress, or what is stored. */
    val comment: String = "",
    val message: String? = null,
) {
    /**
     * A duration-only activity: a climbing session, a class, a run.
     *
     * There is no set table because there are no sets. What it records is that it happened, how
     * long it took and how it felt — and that has to be enough, because inventing a set record so
     * the rest of the app has something to count would be a lie about the training.
     */
    val isActivity: Boolean get() = occurrence?.mode == ExerciseMode.ACTIVITY

    /** What the app would save if the duration field is left alone. Null when it cannot tell. */
    val inferredDurationSeconds: Int? get() = occurrence?.estimatedDurationSeconds

    /** Whole minutes of [inferredDurationSeconds], for showing in grey where the answer goes. */
    val inferredDurationMinutes: String?
        get() = inferredDurationSeconds?.let { ((it + 30) / 60).coerceAtLeast(0).toString() }

    /**
     * The duration that will actually be written, and whether it counts as the user's own number.
     *
     * One place decides it, so the grey hint, the saved value and its provenance flag can never
     * disagree about what the screen was offering.
     */
    val resolvedDuration: Pair<Int?, Boolean>
        get() = table.durationMinutes.toIntOrNull()
            ?.let { (it * 60) to true }
            ?: (inferredDurationSeconds to false)
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoggerViewModel(
    private val repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val occurrenceId: String = savedStateHandle.toRoute<LoggerDestination>().occurrenceId

    private val table = MutableStateFlow(SetTable())

    /** Emitted once the workout has been written, so the screen knows to close itself. */
    val finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val transient = MutableStateFlow(TransientState())
    private var seeded = false

    private data class TransientState(
        val targetDate: LocalDate? = null,
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
            combine(previous, transient, table) { previousResults, extras, rows ->
                build(occurrenceDetail, previousResults, extras, rows)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LoggerUiState(today = LocalDate.now(clock)),
        )

    init {
        // Seed the table once, from the plan and from whatever is already recorded. Prefilled
        // values are a suggestion: nothing reaches the record until the workout is marked done.
        viewModelScope.launch {
            val first = detail.first { it != null } ?: return@launch
            val previous = repository
                .observePreviousResults(first.occurrence.exerciseId, occurrenceId).first()
            seedTable(first, previous)
        }
    }

    private fun build(
        occurrenceDetail: OccurrenceDetail?,
        previousResults: List<PreviousResult>,
        extras: TransientState,
        rows: SetTable,
    ): LoggerUiState {
        val today = LocalDate.now(clock)
        return LoggerUiState(
            loading = occurrenceDetail == null,
            occurrence = occurrenceDetail?.occurrence,
            sets = occurrenceDetail?.sets.orEmpty(),
            previousResults = previousResults,
            table = rows,
            targetDate = extras.targetDate
                ?: occurrenceDetail?.occurrence?.trainingDate
                ?: today,
            today = today,
            prescriptionEditor = extras.prescriptionEditor,
            comment = extras.commentEditor ?: occurrenceDetail?.occurrence?.comment.orEmpty(),
            message = extras.message,
        )
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
            effort = recorded.firstOrNull()?.payload?.effort
                ?: occurrence.loggedEffort
                ?: occurrence.prescription?.effort,
            committed = recorded.isNotEmpty(),
            // Only a duration the user typed comes back as text. One that was inferred stays
            // inferred, so re-saving picks up a better estimate instead of freezing an old one.
            durationMinutes = occurrence.loggedDurationSeconds
                ?.takeIf { occurrence.loggedDurationManual }
                ?.let { ((it + 30) / 60).toString() }
                .orEmpty(),
            showDuration = occurrence.loggedDurationManual || occurrence.mode == ExerciseMode.ACTIVITY,
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
     * Types a duration, in whole minutes.
     *
     * Blank is a real state and means "work it out for me", which is why this never substitutes
     * the inferred value into the field: the moment it did, an untouched estimate would start
     * looking like something the user had said.
     */
    fun setDurationMinutes(value: String) {
        table.update { it.copy(durationMinutes = value.filter(Char::isDigit).take(4)) }
    }

    /** Shows or hides the duration row. Hiding it never discards what has been typed. */
    fun toggleDuration() {
        table.update { it.copy(showDuration = !it.showDuration) }
    }

    /** Corrects the name of an activity that was typed in rather than picked from the library. */
    fun renameActivity(name: String) {
        viewModelScope.launch { repository.renameOneOffActivity(occurrenceId, name) }
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
        // An activity has no sets to check: that it happened, and roughly how long it took, is the
        // whole of what it has to say, and a missing duration is still a workout.
        if (!state.isActivity) {
            val blocker = state.table.blocker(occurrence.measurementUnit != null)
            if (blocker != null) {
                transient.update { it.copy(message = blocker) }
                return
            }
        }
        val sets = if (state.isActivity) {
            emptyList()
        } else {
            state.table.resolved().flatMap { row -> writesFor(occurrence, row) }
        }
        val (durationSeconds, durationManual) = state.resolvedDuration
        viewModelScope.launch {
            // One transaction for the sets, the note, the duration and the state: logging is a
            // single act, and half of it landing would be a record of a workout that did not
            // happen that way.
            repository.saveLogs(
                trainingDate = state.targetDate,
                writes = listOf(
                    OccurrenceLogWrite(
                        occurrenceId = occurrenceId,
                        completed = true,
                        sets = sets,
                        comment = state.comment,
                        effort = state.table.effort,
                        durationSeconds = durationSeconds,
                        durationManual = durationManual,
                    )
                ),
            )
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
            repository.updateOccurrencePrescription(occurrenceId, form.toPayload(occurrence.mode))
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
    get() = this == ExerciseMode.DURATION ||
        this == ExerciseMode.ACTIVITY ||
        this == ExerciseMode.REPEATERS
