package com.yokodake.melete.ui.circuit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.OccurrenceLogWrite
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.ScheduledCircuit
import com.yokodake.melete.data.SetWrite
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.timer.PrescriptionProgram
import com.yokodake.melete.data.timer.StationPlan
import com.yokodake.melete.data.timer.TimerController
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.ui.CircuitReviewDestination
import com.yokodake.melete.ui.components.trimNumber
import com.yokodake.melete.ui.logger.SetRow
import com.yokodake.melete.ui.logger.SetTable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * One exercise of a circuit, as the review screen holds it.
 *
 * The set table is the same one the ordinary logger uses, which is the point: a circuit is not a
 * different kind of record, so it must not have a different kind of load field, a different max-
 * load fallback, or a different idea of what an unticked set means.
 */
data class StationReview(
    val occurrence: PlannedOccurrence,
    val table: SetTable,
    val comment: String,
    val expanded: Boolean,
    /** The circuit's clock, divided; the starting value of this station's logged duration. */
    val suggestedDurationSeconds: Int,
) {
    val measured: Boolean get() = occurrence.measurementUnit != null

    /** Whether this exercise happened. Partly done is still done, with fewer sets. */
    val completed: Boolean get() = table.doneRows.isNotEmpty()

    /** "2 of 3", when some but not all of the prescribed sets were ticked. */
    val partial: String?
        get() = "${table.doneRows.size} of ${table.rows.size}"
            .takeIf { table.doneRows.size in 1 until table.rows.size }

    /** What is actually saved for this station, and whether the number is the user's own. */
    val resolvedDuration: Pair<Int?, Boolean>
        get() = table.durationMinutes.toIntOrNull()
            ?.let { (it * 60) to true }
            ?: (suggestedDurationSeconds to false)
}

data class CircuitReviewUiState(
    val loading: Boolean = true,
    val circuit: ScheduledCircuit? = null,
    val stations: List<StationReview> = emptyList(),
    val targetDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val message: String? = null,
    /** Non-null while the user is being asked whether to call off a countdown already running. */
    val replacePrompt: String? = null,
) {
    val committed: Boolean get() = stations.any { it.occurrence.hasRecord }

    val anyTicked: Boolean get() = stations.any { it.completed }

    /** What is missing, said plainly, or null when nothing is. */
    val blocker: String?
        get() {
            if (!anyTicked) return "Tick at least one exercise"
            val short = stations.filter { it.completed && !it.table.loggable(it.measured) }
            return short.firstOrNull()?.let {
                "${it.occurrence.name}: fill in the max load, or every set's load"
            }
        }
}

/**
 * Logging a whole circuit in one place.
 *
 * A list of expandable rows rather than one screen per exercise, because after a circuit you have
 * four or five things to confirm and walking through four loggers is the friction this app exists
 * to avoid. One Save writes all of them in one transaction: a review that half-landed would be a
 * record of a session that did not happen that way.
 *
 * The timer may have run the circuit, but it wrote nothing. Only this screen can.
 */
class CircuitReviewViewModel(
    private val repository: TrainingRepository,
    private val timer: TimerController,
    savedStateHandle: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val circuitId: String =
        savedStateHandle.toRoute<CircuitReviewDestination>().circuitInstanceId

    private val edits = MutableStateFlow<Map<String, StationEdit>>(emptyMap())
    private val transient = MutableStateFlow(Transient())

    /** Emitted once the review has been written, so the screen knows to close itself. */
    val finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var seeded = false

    private data class StationEdit(
        val table: SetTable,
        val comment: String,
        val expanded: Boolean = false,
    )

    private data class Transient(
        val targetDate: LocalDate? = null,
        val message: String? = null,
        val replacePrompt: String? = null,
    )

    val uiState: StateFlow<CircuitReviewUiState> = combine(
        repository.observeScheduledCircuit(circuitId),
        edits,
        transient,
    ) { circuit, currentEdits, extras ->
        val today = LocalDate.now(clock)
        val shares = circuit?.let(::durationShares).orEmpty()
        CircuitReviewUiState(
            loading = circuit == null,
            circuit = circuit,
            stations = circuit?.stations.orEmpty().mapIndexed { index, station ->
                val edit = currentEdits[station.id] ?: StationEdit(SetTable(), "")
                StationReview(
                    occurrence = station,
                    table = edit.table,
                    comment = edit.comment,
                    expanded = edit.expanded,
                    suggestedDurationSeconds = shares.getOrElse(index) { 0 },
                )
            },
            targetDate = extras.targetDate
                ?: circuit?.trainingDate
                ?: today,
            today = today,
            message = extras.message,
            replacePrompt = extras.replacePrompt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CircuitReviewUiState(today = LocalDate.now(clock)),
    )

    init {
        viewModelScope.launch {
            val circuit = repository.observeScheduledCircuit(circuitId).first { it != null }
                ?: return@launch
            if (seeded) return@launch
            seeded = true
            edits.value = circuit.stations.associate { it.id to seed(it) }
        }
    }

    /**
     * The circuit's own program, which is where every number about it comes from.
     *
     * Built once from the stations and their copied prescriptions, so the time the review shows
     * and the sequence the timer runs are the same arithmetic.
     */
    private fun programOf(circuit: ScheduledCircuit): TimerProgram = PrescriptionProgram.circuit(
        label = circuit.name,
        rounds = circuit.rounds,
        transitionSeconds = circuit.transitionSeconds,
        roundRestSeconds = circuit.roundRestSeconds,
        stations = circuit.stations.map {
            StationPlan(it.name, it.mode, it.unilateral, it.prescription, it.id)
        },
        circuitInstanceId = circuit.id,
    )

    /**
     * The circuit's elapsed time, divided between its exercises so every second is attributed
     * exactly once. Summing these gives the circuit's own total back, which is what keeps a
     * later dashboard from counting the circuit and its parts.
     */
    private fun durationShares(circuit: ScheduledCircuit): List<Int> =
        if (circuit.stations.isEmpty()) emptyList()
        else programOf(circuit).estimatedSecondsByEntry()

    /** The program to run, so the review screen can offer to start the circuit. */
    fun timerProgram(): TimerProgram? = uiState.value.circuit?.let(::programOf)

    /**
     * Offers to run the circuit.
     *
     * There is one timer at a time by design, so replacing a countdown someone is in the middle of
     * is a decision rather than a side effect of pressing a button on another screen.
     */
    fun requestStartTimer(onStarted: () -> Unit) {
        val active = timer.state.value.currentExerciseLabel
            ?.takeIf { timer.hasActiveProgram }
        if (active == null && !timer.hasActiveProgram) {
            startTimer(onStarted)
        } else {
            transient.update { it.copy(replacePrompt = active ?: "a countdown") }
        }
    }

    fun confirmStartTimer(onStarted: () -> Unit) {
        transient.update { it.copy(replacePrompt = null) }
        startTimer(onStarted)
    }

    fun dismissReplacePrompt() = transient.update { it.copy(replacePrompt = null) }

    private fun startTimer(onStarted: () -> Unit) {
        val program = timerProgram() ?: return
        timer.start(program)
        onStarted()
    }

    /**
     * Builds one station's table from its plan and from whatever is already recorded.
     *
     * Exactly as the single-exercise logger does it, including the pairing of a unilateral row's
     * two sets, so reopening a saved circuit shows what it says rather than a fresh guess.
     */
    private fun seed(station: PlannedOccurrence): StationEdit {
        val planned = station.prescription?.sets ?: 0
        // A unilateral row wrote two sets, left then right, so they come back in pairs.
        val recorded = if (station.unilateral) station.loggedSets / 2 else station.loggedSets
        val count = maxOf(planned, recorded, 1)
        return StationEdit(
            table = SetTable(
                rows = (0 until count).map { index ->
                    SetRow(
                        number = index + 1,
                        reps = station.prescription?.targetReps,
                        // A fresh row starts ticked, because the ordinary case is that you did
                        // what you planned. A reopened one shows what it actually says, so two
                        // rounds of three comes back as two rounds of three.
                        done = if (station.loggedSets > 0) index < recorded else true,
                    )
                },
                maxLoad = station.maxLoad?.let(::trimNumber).orEmpty(),
                effort = station.loggedEffort ?: station.prescription?.effort,
                committed = station.hasRecord,
                durationMinutes = station.loggedDurationSeconds
                    ?.takeIf { station.loggedDurationManual }
                    ?.let { ((it + 30) / 60).toString() }
                    .orEmpty(),
            ),
            comment = station.comment.orEmpty(),
        )
    }

    private fun edit(stationId: String, transform: (StationEdit) -> StationEdit) {
        edits.update { current ->
            val existing = current[stationId] ?: StationEdit(SetTable(), "")
            current + (stationId to transform(existing))
        }
    }

    fun toggleExpanded(stationId: String) =
        edit(stationId) { it.copy(expanded = !it.expanded) }

    /**
     * Ticking a row selects its prescribed sets; unticking clears them.
     *
     * The whole-station tick is the quick path — the one you use standing at the wall — and the
     * expanded rows are there for the session where the fourth round did not happen.
     */
    fun toggleStation(stationId: String) {
        edit(stationId) { current ->
            val allDone = current.table.rows.all { it.done }
            current.copy(
                table = current.table.copy(
                    rows = current.table.rows.map { it.copy(done = !allDone) },
                )
            )
        }
    }

    fun toggleSet(stationId: String, number: Int) {
        edit(stationId) { current ->
            current.copy(
                table = current.table.copy(
                    rows = current.table.rows.map {
                        if (it.number == number) it.copy(done = !it.done) else it
                    },
                )
            )
        }
    }

    fun setMaxLoad(stationId: String, value: String, right: Boolean = false) {
        edit(stationId) { current ->
            val rows = current.table.rows.map {
                if (right) it.copy(loadRight = value) else it.copy(load = value)
            }
            current.copy(
                table = if (right) {
                    current.table.copy(maxLoadRight = value, rows = rows)
                } else {
                    current.table.copy(maxLoad = value, rows = rows)
                }
            )
        }
    }

    fun setRowLoad(stationId: String, number: Int, value: String, right: Boolean = false) {
        edit(stationId) { current ->
            current.copy(
                table = current.table.copy(
                    rows = current.table.rows.map { row ->
                        when {
                            row.number < number -> row
                            right -> row.copy(loadRight = value)
                            else -> row.copy(load = value)
                        }
                    },
                ).withDeducedMax()
            )
        }
    }

    fun setEffort(stationId: String, effort: EffortLevel?) =
        edit(stationId) { it.copy(table = it.table.copy(effort = effort)) }

    fun setComment(stationId: String, value: String) =
        edit(stationId) { it.copy(comment = value) }

    fun setDurationMinutes(stationId: String, value: String) = edit(stationId) {
        it.copy(table = it.table.copy(durationMinutes = value.filter(Char::isDigit).take(4)))
    }

    fun setTargetDate(date: LocalDate) = transient.update { it.copy(targetDate = date) }

    /**
     * Writes the whole review at once.
     *
     * Every station in one transaction, and saving again replaces rather than appends, so
     * correcting a circuit is the same gesture as logging it. Each exercise counts once no matter
     * how many rounds it was done for, and the circuit itself counts nothing.
     */
    fun save() {
        val state = uiState.value
        val blocker = state.blocker
        if (blocker != null) {
            transient.update { it.copy(message = blocker) }
            return
        }
        viewModelScope.launch {
            repository.saveLogs(
                trainingDate = state.targetDate,
                writes = state.stations.map { station ->
                    val (duration, manual) = station.resolvedDuration
                    OccurrenceLogWrite(
                        occurrenceId = station.occurrence.id,
                        completed = station.completed,
                        sets = writesFor(station),
                        comment = station.comment,
                        effort = station.table.effort,
                        // A station that did not happen carries no time either; the circuit's
                        // share of the clock belongs to the work that was actually done.
                        durationSeconds = duration.takeIf { station.completed },
                        durationManual = manual,
                    )
                },
            )
            finished.emit(Unit)
        }
    }

    /** One row becomes one set, or a left/right pair when the exercise is unilateral. */
    private fun writesFor(station: StationReview): List<SetWrite> {
        val occurrence = station.occurrence
        fun payload(load: String) = ActualSetPayload(
            reps = occurrence.prescription?.targetReps,
            durationSeconds = occurrence.prescription?.targetDurationSeconds
                ?.takeIf { occurrence.mode == ExerciseMode.DURATION },
            measurement = load.toDoubleOrNull()?.let { value ->
                occurrence.measurementUnit?.let { unit ->
                    Measurement(
                        value,
                        unit,
                        occurrence.measurementMeaning ?: MeasurementMeaning.TOTAL_LOAD,
                    )
                }
            },
            effort = station.table.effort,
        )
        return station.table.resolved().flatMap { row ->
            if (occurrence.unilateral) {
                listOf(
                    SetWrite(payload(row.load), BodySide.LEFT),
                    SetWrite(payload(row.loadRight), BodySide.RIGHT),
                )
            } else {
                listOf(SetWrite(payload(row.load), null))
            }
        }
    }

    fun consumeMessage() = transient.update { it.copy(message = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                CircuitReviewViewModel(
                    application.container.trainingRepository,
                    application.container.timerController,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
