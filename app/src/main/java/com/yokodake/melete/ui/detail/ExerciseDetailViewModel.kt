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
import com.yokodake.melete.data.timer.TimerController
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.ui.ExerciseDetailDestination
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

/** What "Start the timer" would do, worked out from the plan rather than asked for again. */
data class TimerStartPlan(
    val phase: TimerPhase,
    val seconds: Int,
    val label: String,
) {
    val buttonLabel: String
        get() {
            val what = if (phase == TimerPhase.WORK) "work" else "rest"
            return "Start ${PrescriptionSummary.duration(seconds)} $what"
        }
}

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
    val timerPlan: TimerStartPlan? = null,
    /** Non-null while the user is being asked whether to call off a countdown already running. */
    val replacePrompt: RunningCountdown? = null,
)

/**
 * Opening a workout shows what it is before it shows anything to fill in.
 *
 * The same screen serves a planned copy in the week and a library entry: the difference is what
 * you can do next, not what you are looking at. Editing the definition is a button you press,
 * never the screen you land on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModel(
    private val repository: TrainingRepository,
    private val timer: TimerController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val destination = savedStateHandle.toRoute<ExerciseDetailDestination>()

    private val prompt = MutableStateFlow<RunningCountdown?>(null)

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
        combine(source, prompt) { (detail, library), replacePrompt ->
            build(detail, library, replacePrompt)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExerciseDetailUiState(occurrenceId = destination.occurrenceId),
        )

    private fun build(
        detail: OccurrenceDetail?,
        library: LibraryExercise?,
        replacePrompt: RunningCountdown?,
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
            definitionMissing = library == null,
            timerPlan = timerPlan(name, mode, prescription),
            replacePrompt = replacePrompt,
        )
    }

    /**
     * A timed exercise counts its own work interval; anything else counts the rest after a set.
     * The duration comes from the plan, falling back to whatever was last used rather than to a
     * number invented here.
     */
    private fun timerPlan(
        name: String,
        mode: ExerciseMode,
        prescription: PrescriptionPayload?,
    ): TimerStartPlan {
        val timed = mode == ExerciseMode.DURATION || mode == ExerciseMode.ACTIVITY
        val work = prescription?.targetDurationSeconds?.takeIf { timed && it > 0 }
        return if (work != null) {
            TimerStartPlan(TimerPhase.WORK, work, name)
        } else {
            TimerStartPlan(
                phase = TimerPhase.REST,
                seconds = prescription?.restSeconds?.takeIf { it > 0 }
                    ?: timer.lastDurationSeconds(TimerPhase.REST),
                label = name,
            )
        }
    }

    /**
     * Starting a countdown from a workout while another one is still counting would silently throw
     * away a rest the user is in the middle of, so it asks first. There is one countdown at a
     * time by design; replacing it is a decision, not a side effect of opening a screen.
     */
    fun requestStartTimer(onStarted: () -> Unit) {
        val active = when (val state = timer.state.value) {
            is TimerState.Running -> RunningCountdown(state.phase, state.label)
            is TimerState.Paused -> RunningCountdown(state.phase, state.label)
            else -> null
        }
        if (active == null) {
            startTimer(onStarted)
        } else {
            prompt.value = active
        }
    }

    fun confirmStartTimer(onStarted: () -> Unit) {
        prompt.value = null
        startTimer(onStarted)
    }

    fun dismissReplacePrompt() {
        prompt.update { null }
    }

    private fun startTimer(onStarted: () -> Unit) {
        val plan = uiState.value.timerPlan ?: return
        timer.start(
            phase = plan.phase,
            durationSeconds = plan.seconds,
            label = plan.label,
        )
        onStarted()
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
