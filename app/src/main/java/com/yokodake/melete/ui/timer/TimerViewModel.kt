package com.yokodake.melete.ui.timer

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.timer.CuePlanner
import com.yokodake.melete.data.timer.CueSettings
import com.yokodake.melete.data.timer.PlannedCue
import com.yokodake.melete.data.timer.RepeaterSpec
import com.yokodake.melete.data.timer.TimerController
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.data.timer.TimerTransitions
import com.yokodake.melete.data.timer.WorkKind
import com.yokodake.melete.data.timer.positionDetail
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A minutes-and-seconds pair, kept as typed text so a half-typed field is not silently a zero.
 *
 * Never capped: 90 minutes typed is 90 minutes run. The fields themselves bound it (three digits
 * of minutes), so there is no hidden ceiling for a typed number to be quietly cut to.
 */
data class DurationDraft(val minutes: String = "0", val seconds: String = "00") {
    val totalSeconds: Int
        get() = ((minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)).coerceAtLeast(0)

    companion object {
        fun of(seconds: Int) = DurationDraft(
            minutes = (seconds / 60).toString(),
            seconds = (seconds % 60).toString().padStart(2, '0'),
        )
    }
}

/**
 * What kind of thing the create screen is building.
 *
 * Not [WorkKind], because a repeater *is* timed work — the difference is the shape of the set, not
 * whether it is counted by a clock. Three choices here map onto two work kinds plus a pulse spec,
 * which is exactly the distinction the athlete makes and the engine does not.
 */
enum class TimerCreateMode(val label: String) {
    TIMED("Timed"),
    REPS("Reps"),
    REPEATERS("Repeaters"),

    /** Attempts: untimed reps, each waited for, with a counted rest between them. */
    INTERVALS("Intervals"),
}

data class TimerUiState(
    val state: TimerState = TimerState.Idle,
    val remainingMs: Long = 0,
    val totalMs: Long = 0,
    val mode: TimerCreateMode = TimerCreateMode.TIMED,
    val work: DurationDraft = DurationDraft.of(30),
    val rest: DurationDraft = DurationDraft.of(180),
    val setsText: String = "3",
    /** Both sides inside one set, left first. */
    val unilateral: Boolean = false,
    val sideSwitchText: String = TimerProgram.DEFAULT_SIDE_SWITCH_SECONDS.toString(),
    val repeaterRepsText: String = "6",
    val repeaterWorkText: String = "7",
    val repeaterRestText: String = "3",
    val intervalRepsText: String = "3",
    val intervalRest: DurationDraft = DurationDraft.of(180),
    val cues: CueSettings = CueSettings(),
) {
    val isRunning: Boolean get() = state is TimerState.Running
    val isPaused: Boolean get() = state is TimerState.Paused
    val isIdle: Boolean get() = state is TimerState.Idle
    val isAwaitingSet: Boolean get() = state is TimerState.AwaitingSet

    val sets: Int get() = (setsText.toIntOrNull() ?: 0).coerceIn(0, 99)

    val sideSwitchSeconds: Int
        get() = (sideSwitchText.toIntOrNull() ?: TimerProgram.DEFAULT_SIDE_SWITCH_SECONDS)
            .coerceIn(0, 300)

    /** The pulse shape, or null while the fields do not yet describe one. */
    val repeaterSpec: RepeaterSpec?
        get() {
            val reps = repeaterRepsText.toIntOrNull() ?: return null
            val work = repeaterWorkText.toIntOrNull() ?: return null
            if (reps < 1 || work < 1) return null
            return RepeaterSpec(
                repsPerSet = reps,
                workSecondsPerRep = work,
                restSecondsBetweenReps = repeaterRestText.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            )
        }

    /** The program the fields add up to, or null when they do not yet describe a valid one. */
    val draftProgram: TimerProgram?
        get() {
            if (sets < 1) return null
            fun program(work: WorkKind, workSeconds: Int = 0, repeater: RepeaterSpec? = null) =
                TimerProgram(
                    sets = sets,
                    work = work,
                    workSeconds = workSeconds,
                    restSeconds = rest.totalSeconds,
                    unilateral = unilateral,
                    sideSwitchSeconds = sideSwitchSeconds,
                    repeater = repeater,
                )
            return when (mode) {
                TimerCreateMode.TIMED ->
                    if (work.totalSeconds < 1) null
                    else program(WorkKind.TIMED, workSeconds = work.totalSeconds)

                TimerCreateMode.REPS -> program(WorkKind.REPS)

                TimerCreateMode.REPEATERS ->
                    repeaterSpec?.let { program(WorkKind.TIMED, repeater = it) }

                // At least two attempts and a rest between them; anything less is plain reps.
                TimerCreateMode.INTERVALS -> {
                    val reps = intervalRepsText.toIntOrNull() ?: 0
                    if (reps < 2 || intervalRest.totalSeconds < 1) {
                        null
                    } else {
                        TimerProgram(
                            sets = sets,
                            work = WorkKind.REPS,
                            workReps = reps,
                            restSeconds = rest.totalSeconds,
                            unilateral = unilateral,
                            sideSwitchSeconds = sideSwitchSeconds,
                            repRestSeconds = intervalRest.totalSeconds,
                        )
                    }
                }
            }
        }

    val canStart: Boolean get() = draftProgram != null

    /**
     * The interval the cue preview describes.
     *
     * For repeaters that is one pulse, not the whole set: the pulse is what the cues actually land
     * inside, and saying "no 30-second warning" about a seven-second effort is the useful answer.
     */
    private val previewPhase: TimerPhase
        get() = when (state) {
            is TimerState.Running -> state.phase
            is TimerState.Paused -> state.phase
            else -> when (mode) {
                TimerCreateMode.REPS -> TimerPhase.REST
                TimerCreateMode.INTERVALS -> TimerPhase.REP_REST
                else -> TimerPhase.WORK
            }
        }

    /**
     * The length the cue preview is worked out for: the interval on screen while one is counting,
     * otherwise what the create form describes. A planned timer is not the form's timer, so
     * explaining its cues from the form's numbers answered a question nobody asked.
     */
    private val previewMs: Long
        get() = when {
            cuePreviewIsLive -> totalMs
            else -> when (mode) {
                TimerCreateMode.TIMED -> work.totalSeconds * 1000L
                TimerCreateMode.REPS -> rest.totalSeconds * 1000L
                TimerCreateMode.REPEATERS -> (repeaterSpec?.workSecondsPerRep ?: 0) * 1000L
                TimerCreateMode.INTERVALS -> intervalRest.totalSeconds * 1000L
            }
        }

    /** Whether the cue preview describes a live interval rather than the create form. */
    val cuePreviewIsLive: Boolean
        get() = state is TimerState.Running || state is TimerState.Paused

    /**
     * Whether the previous button will restart this interval rather than go back one, decided by
     * the same rule the transition uses.
     */
    val previousRestarts: Boolean
        get() = TimerTransitions.previousRestarts(
            (totalMs - remainingMs).takeIf { cuePreviewIsLive }
        )

    /** What one interval of this program will sound, given its length. */
    val plannedCues: List<PlannedCue>
        get() = CuePlanner.plan(previewPhase, previewMs, cues)

    val quarterCuesApply: Boolean
        get() = cues.quarterCues &&
            previewPhase == TimerPhase.WORK &&
            previewMs >= CuePlanner.QUARTER_CUE_MIN_MS

    val thirtySecondWarningApplies: Boolean
        get() = cues.thirtySecondWarning && previewMs > 30_000

    /** The routine a circuit is running, shown above the station it is on. */
    val routineLabel: String?
        get() = state.activeProgram?.takeIf { it.isCircuit }?.label

    /** The phase of whatever is on screen: the live interval, or the one about to start. */
    val shownPhase: TimerPhase
        get() = when (state) {
            is TimerState.Running -> state.phase
            is TimerState.Paused -> state.phase
            is TimerState.AwaitingSet -> TimerPhase.WORK
            is TimerState.Finished -> state.phase
            is TimerState.Interrupted -> state.phase
            TimerState.Idle -> previewPhase
        }

    /** The exercise on screen: a circuit names its current station, not the routine. */
    val label: String? get() = state.currentExerciseLabel

    /**
     * "Set 2 of 5 · left · rep 3 of 6", or null when there is nothing to distinguish.
     *
     * The same sentence the notification carries, so glancing at the shade and glancing at the
     * screen never disagree about where you are.
     */
    val setProgress: String?
        get() = state.positionDetail()?.replaceFirstChar { it.uppercase() }

    val progress: Float
        get() = if (totalMs <= 0) 0f else (1f - remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
}

class TimerViewModel(private val controller: TimerController) : ViewModel() {

    private val draft = MutableStateFlow(
        Draft(
            mode = TimerCreateMode.TIMED,
            work = DurationDraft.of(controller.lastDurationSeconds(TimerPhase.WORK)),
            rest = DurationDraft.of(controller.lastDurationSeconds(TimerPhase.REST)),
            sets = controller.lastSets.toString(),
            unilateral = controller.lastUnilateral,
            sideSwitch = controller.lastSideSwitchSeconds.toString(),
            repeaterReps = controller.lastRepeaterReps.toString(),
            repeaterWork = controller.lastRepeaterWorkSeconds.toString(),
            repeaterRest = controller.lastRepeaterRestSeconds.toString(),
            intervalReps = controller.lastIntervalReps.toString(),
            intervalRest = DurationDraft.of(controller.lastIntervalRestSeconds),
            cues = controller.cueSettings,
        )
    )

    private val ticker = MutableStateFlow(SystemClock.elapsedRealtime())

    val uiState: StateFlow<TimerUiState> =
        combine(controller.state, draft, ticker) { state, currentDraft, nowMs ->
            TimerUiState(
                state = state,
                remainingMs = when (state) {
                    is TimerState.Running -> state.remainingMs(nowMs)
                    is TimerState.Paused -> state.remainingMs
                    else -> 0
                },
                totalMs = when (state) {
                    is TimerState.Running -> state.totalMs
                    is TimerState.Paused -> state.totalMs
                    is TimerState.Finished -> state.totalMs
                    is TimerState.Interrupted -> state.totalMs
                    is TimerState.AwaitingSet -> 0
                    TimerState.Idle -> currentDraft.work.totalSeconds * 1000L
                },
                mode = currentDraft.mode,
                work = currentDraft.work,
                rest = currentDraft.rest,
                setsText = currentDraft.sets,
                unilateral = currentDraft.unilateral,
                sideSwitchText = currentDraft.sideSwitch,
                repeaterRepsText = currentDraft.repeaterReps,
                repeaterWorkText = currentDraft.repeaterWork,
                repeaterRestText = currentDraft.repeaterRest,
                intervalRepsText = currentDraft.intervalReps,
                intervalRest = currentDraft.intervalRest,
                cues = currentDraft.cues,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TimerUiState(),
        )

    init {
        // Only the display ticks. The countdown itself is a deadline, so a missed tick loses a
        // frame of animation rather than a second of training.
        viewModelScope.launch {
            while (true) {
                ticker.value = SystemClock.elapsedRealtime()
                delay(200)
            }
        }
    }

    /**
     * Timed sets or reps. Switching deliberately keeps every number already typed: the rest and
     * the set count mean the same thing either way, and having them replaced the moment you
     * classify the work is maddening.
     */
    fun setMode(mode: TimerCreateMode) = draft.update { it.copy(mode = mode) }

    fun setUnilateral(value: Boolean) = draft.update { it.copy(unilateral = value) }

    fun setSideSwitch(value: String) =
        draft.update { it.copy(sideSwitch = value.filter(Char::isDigit).take(3)) }

    fun setRepeaterReps(value: String) =
        draft.update { it.copy(repeaterReps = value.filter(Char::isDigit).take(2)) }

    fun setRepeaterWork(value: String) =
        draft.update { it.copy(repeaterWork = value.filter(Char::isDigit).take(3)) }

    fun setRepeaterRest(value: String) =
        draft.update { it.copy(repeaterRest = value.filter(Char::isDigit).take(3)) }

    fun setIntervalReps(value: String) =
        draft.update { it.copy(intervalReps = value.filter(Char::isDigit).take(2)) }

    fun setIntervalRestMinutes(value: String) =
        draft.update { it.copy(intervalRest = it.intervalRest.copy(minutes = value.take(3))) }

    fun setIntervalRestSeconds(value: String) =
        draft.update { it.copy(intervalRest = it.intervalRest.copy(seconds = value.take(2))) }

    fun setWorkMinutes(value: String) =
        draft.update { it.copy(work = it.work.copy(minutes = value.take(3))) }

    fun setWorkSeconds(value: String) =
        draft.update { it.copy(work = it.work.copy(seconds = value.take(2))) }

    fun setRestMinutes(value: String) =
        draft.update { it.copy(rest = it.rest.copy(minutes = value.take(3))) }

    fun setRestSeconds(value: String) =
        draft.update { it.copy(rest = it.rest.copy(seconds = value.take(2))) }

    fun setSets(value: String) = draft.update { it.copy(sets = value.take(2)) }

    fun setCueSettings(settings: CueSettings) {
        // Goes through the controller first: a running countdown is replanned there, so a cue
        // switched off mid-rest stops being owed rather than only stopping next time.
        controller.setCueSettings(settings)
        draft.update { it.copy(cues = settings) }
    }

    fun start() {
        val program = uiState.value.draftProgram ?: return
        controller.start(program, draft.value.cues)
    }

    fun completeSet() = controller.completeSet()

    fun previous() = controller.previous()

    fun next() = controller.next()

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun cancel() = controller.cancel()

    fun dismiss() = controller.dismiss()

    private data class Draft(
        val mode: TimerCreateMode,
        val work: DurationDraft,
        val rest: DurationDraft,
        val sets: String,
        val unilateral: Boolean,
        val sideSwitch: String,
        val repeaterReps: String,
        val repeaterWork: String,
        val repeaterRest: String,
        val intervalReps: String,
        val intervalRest: DurationDraft,
        val cues: CueSettings,
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                TimerViewModel(application.container.timerController)
            }
        }
    }
}
