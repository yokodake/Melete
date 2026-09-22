package com.yokodake.melete.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.data.OccurrenceDetail
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.data.timer.DurationEstimate
import com.yokodake.melete.data.timer.PrescriptionProgram
import com.yokodake.melete.data.timer.TimerController
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.data.timer.WorkKind
import com.yokodake.melete.ui.ExerciseDetailDestination
import com.yokodake.melete.ui.components.PrescriptionFormState
import com.yokodake.melete.ui.week.PrescriptionSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What is already counting, when the user asks for a new countdown. */
data class RunningCountdown(val phase: TimerPhase, val label: String?)

data class ExerciseDetailUiState(
    val loading: Boolean = true,
    /** Set when this is a planned copy in the week; null when browsing the library entry. */
    val occurrenceId: String? = null,
    /** Set unless the library entry has since been deleted. */
    val exerciseId: String? = null,
    val name: String = "",
    val subtitle: String = "",
    val category: ExerciseCategory? = null,
    val description: String? = null,
    val notes: String? = null,
    val mode: ExerciseMode = ExerciseMode.REPETITIONS,
    val unilateral: Boolean = false,
    val measurementUnit: String? = null,
    val prescription: PrescriptionPayload? = null,
    val prescriptionSummary: String = "",
    val occurrenceState: OccurrenceState? = null,
    val comment: String? = null,
    /** The library entry this copy came from is gone, so there is no explanation to show. */
    val definitionMissing: Boolean = false,
    /** An activity typed in by name, which never had a library entry to begin with. */
    val isOneOff: Boolean = false,
    /** The whole timer this workout implies: sets, work, rest. */
    val timerProgram: TimerProgram? = null,
    /** Non-null while the plan is being edited in place. */
    val prescriptionEditor: PrescriptionFormState? = null,
    /** Non-null while the user is being asked whether to call off a countdown already running. */
    val replacePrompt: RunningCountdown? = null,
    /** How long the plan says this will take, rests included, or null when nothing says. */
    val plannedDurationSeconds: Int? = null,
    /** How long it actually took, once logged. */
    val loggedDurationSeconds: Int? = null,
    /** Whether that logged number was typed rather than worked out. */
    val loggedDurationManual: Boolean = false,
) {
    /**
     * What the logging button offers.
     *
     * Planned work has no log yet, so the button offers to make one. Once the workout has been
     * written the same button reopens what it says, which is a different promise and gets
     * different words.
     */
    val logButtonLabel: String
        get() = if (occurrenceState == OccurrenceState.COMPLETED) "Update log" else "Log the workout"

    /** What the start button offers, spelled out so pressing it holds no surprise. */
    val timerButtonLabel: String
        get() {
            val program = timerProgram ?: return "Start the timer"
            val repeater = program.entry.repeater
            return when {
                program.work == WorkKind.NONE ->
                    "Start ${PrescriptionSummary.duration(program.restSeconds)} rest"

                // A repeater set is a series, so saying its total length would be a different
                // promise from the one the timer is about to keep.
                repeater != null -> "Start ${program.sets} × ${repeater.repsPerSet} × " +
                    PrescriptionSummary.duration(repeater.workSecondsPerRep)

                program.sets == 1 && program.work == WorkKind.TIMED ->
                    "Start ${PrescriptionSummary.duration(program.workSeconds)}"

                program.work == WorkKind.REPS -> "Start ${program.sets} sets"

                else ->
                    "Start ${program.sets} × ${PrescriptionSummary.duration(program.workSeconds)}"
            }
        }

    /** The whole sequence in one line, so both sides and every pulse are accounted for. */
    val timerShapeLine: String?
        get() {
            val program = timerProgram ?: return null
            val parts = buildList {
                if (unilateral) {
                    add(
                        "both sides, " +
                            PrescriptionSummary.duration(program.entry.sideSwitchSeconds) +
                            " to switch"
                    )
                }
                program.entry.repeater?.let {
                    add(
                        "${it.repsPerSet} reps of " +
                            PrescriptionSummary.duration(it.workSecondsPerRep) +
                            " with " + PrescriptionSummary.duration(it.restSecondsBetweenReps)
                    )
                }
                plannedDurationSeconds?.let {
                    add("about ${PrescriptionSummary.duration(it)} in all")
                }
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" \u00b7 ")
        }
}

/**
 * Opening a workout shows what it is before it shows anything to fill in.
 *
 * The same screen serves a planned copy in the week and a library entry: the difference is what
 * you can do next, not what you are looking at. Editing is opt-in either way — the cog on the plan
 * for its numbers, the overflow for what the exercise itself is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModel(
    private val repository: TrainingRepository,
    private val timer: TimerController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val destination = savedStateHandle.toRoute<ExerciseDetailDestination>()

    private data class Transient(
        val replacePrompt: RunningCountdown? = null,
        val prescriptionEditor: PrescriptionFormState? = null,
    )

    private val transient = MutableStateFlow(Transient())

    private val source: Flow<Pair<OccurrenceDetail?, LibraryExercise?>> =
        when (val occurrenceId = destination.occurrenceId) {
            null -> repository.observeLibraryExercise(destination.exerciseId.orEmpty())
                .map { null to it }

            else -> repository.observeOccurrence(occurrenceId).flatMapLatest { detail ->
                // The explanation is read from the library rather than from the snapshot: a
                // correction to how a movement is done should reach every copy of it.
                when (detail) {
                    null -> flowOf<Pair<OccurrenceDetail?, LibraryExercise?>>(null to null)
                    else -> repository.observeLibraryExercise(detail.occurrence.exerciseId)
                        .map { library -> detail to library }
                }
            }
        }

    val uiState: StateFlow<ExerciseDetailUiState> =
        combine(source, transient) { (detail, library), extras ->
            build(detail, library, extras)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExerciseDetailUiState(occurrenceId = destination.occurrenceId),
        )

    private fun build(
        detail: OccurrenceDetail?,
        library: LibraryExercise?,
        extras: Transient,
    ): ExerciseDetailUiState {
        val occurrence = detail?.occurrence
        // Reaching here at all means the query has answered; `loading` belongs to the initial
        // value alone, so a row that is genuinely gone says so instead of spinning forever.
        if (occurrence == null && library == null) {
            return ExerciseDetailUiState(
                loading = false,
                occurrenceId = null,
                definitionMissing = true,
            )
        }
        val name = occurrence?.name ?: library?.name.orEmpty()
        val mode = occurrence?.mode ?: library?.mode ?: ExerciseMode.REPETITIONS
        val unilateral = occurrence?.unilateral ?: library?.unilateral ?: false
        val prescription = if (occurrence != null) {
            occurrence.prescription
        } else {
            library?.defaultPrescription
        }
        val summary = if (occurrence != null) {
            PrescriptionSummary.format(occurrence)
        } else {
            library?.let(PrescriptionSummary::formatDefault).orEmpty()
        }
        return ExerciseDetailUiState(
            loading = false,
            occurrenceId = occurrence?.id,
            exerciseId = library?.id ?: occurrence?.exerciseId,
            name = name,
            subtitle = when {
                occurrence == null -> "Library"
                occurrence.trainingDate != null -> WeekMath.dayLabel(occurrence.trainingDate)
                else -> "Unscheduled"
            },
            category = occurrence?.category ?: library?.category,
            description = library?.description,
            notes = library?.notes,
            mode = mode,
            unilateral = unilateral,
            measurementUnit = occurrence?.measurementUnit ?: library?.measurementUnit,
            prescription = prescription,
            prescriptionSummary = summary,
            occurrenceState = occurrence?.state,
            comment = occurrence?.comment,
            // A one-off never had a definition, which is a different thing from having lost one.
            definitionMissing = library == null && occurrence?.isOneOff != true,
            isOneOff = occurrence?.isOneOff == true,
            timerProgram = timerProgram(name, mode, unilateral, prescription, occurrence?.id),
            prescriptionEditor = extras.prescriptionEditor,
            replacePrompt = extras.replacePrompt,
            plannedDurationSeconds = occurrence?.estimatedDurationSeconds
                ?: prescription?.plannedDurationSeconds
                ?: DurationEstimate.forPrescription(mode, unilateral, prescription),
            loggedDurationSeconds = occurrence?.loggedDurationSeconds,
            loggedDurationManual = occurrence?.loggedDurationManual == true,
        )
    }

    /**
     * The whole timer the plan implies. A prescription already says how many sets, how long each
     * one is, how much rest goes between them, whether both sides are trained and whether the set
     * is a series of pulses — which is exactly a program, so there is nothing left to ask. One
     * function builds it and the duration estimate alike, so what the planner says a workout will
     * take is measured against the sequence the timer actually runs.
     */
    private fun timerProgram(
        name: String,
        mode: ExerciseMode,
        unilateral: Boolean,
        prescription: PrescriptionPayload?,
        occurrenceId: String? = null,
    ): TimerProgram = PrescriptionProgram.of(
        label = name,
        mode = mode,
        unilateral = unilateral,
        prescription = prescription,
        // Only for running it: an exercise that says nothing about rest still gets the rest you
        // last used rather than none. The *estimate* never sees this, because a planned duration
        // has to mean the same thing tomorrow as it does today.
        fallbackRestSeconds = timer.lastDurationSeconds(TimerPhase.REST),
        occurrenceId = occurrenceId,
    )

    // ------------------------------------------------------------- timer

    /**
     * Starting a program from a workout while another one is still counting would silently throw
     * away a rest the user is in the middle of, so it asks first. There is one timer at a time by
     * design; replacing it is a decision, not a side effect of opening a screen.
     */
    fun requestStartTimer(onStarted: () -> Unit) {
        val active = when (val state = timer.state.value) {
            is TimerState.Running -> RunningCountdown(state.phase, state.activeLabel)
            is TimerState.Paused -> RunningCountdown(state.phase, state.activeLabel)
            is TimerState.AwaitingSet -> RunningCountdown(TimerPhase.WORK, state.activeLabel)
            else -> null
        }
        if (active == null) {
            startTimer(onStarted)
        } else {
            transient.update { it.copy(replacePrompt = active) }
        }
    }

    fun confirmStartTimer(onStarted: () -> Unit) {
        transient.update { it.copy(replacePrompt = null) }
        startTimer(onStarted)
    }

    fun dismissReplacePrompt() {
        transient.update { it.copy(replacePrompt = null) }
    }

    private fun startTimer(onStarted: () -> Unit) {
        val program = uiState.value.timerProgram ?: return
        timer.start(program)
        onStarted()
    }

    // ------------------------------------------------------ prescription

    /**
     * Editing the plan is opt-in and lives on the plan itself. What it edits depends on what is
     * being looked at: from the week it is this copy, which is the point of copying by value; from
     * the library it is the default every future copy will be cut from.
     */
    fun openPrescriptionEditor() {
        val state = uiState.value
        if (state.occurrenceId == null && state.exerciseId == null) return
        transient.update {
            it.copy(prescriptionEditor = PrescriptionFormState.from(state.prescription))
        }
    }

    fun updatePrescriptionEditor(form: PrescriptionFormState) {
        transient.update { it.copy(prescriptionEditor = form) }
    }

    fun dismissPrescriptionEditor() {
        transient.update { it.copy(prescriptionEditor = null) }
    }

    fun savePrescription() {
        val state = uiState.value
        val form = transient.value.prescriptionEditor ?: return
        val payload = form.toPayload(state.mode)
        viewModelScope.launch {
            val occurrenceId = state.occurrenceId
            if (occurrenceId != null) {
                repository.updateOccurrencePrescription(occurrenceId, payload)
            } else {
                state.exerciseId?.let { repository.updateDefaultPrescription(it, payload) }
            }
            transient.update { it.copy(prescriptionEditor = null) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                ExerciseDetailViewModel(
                    application.container.trainingRepository,
                    application.container.timerController,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
