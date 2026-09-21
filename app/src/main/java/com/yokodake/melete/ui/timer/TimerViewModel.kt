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
import com.yokodake.melete.data.timer.TimerController
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.data.timer.WorkKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A minutes-and-seconds pair, kept as typed text so a half-typed field is not silently a zero. */
data class DurationDraft(val minutes: String = "0", val seconds: String = "00") {
    val totalSeconds: Int
        get() = ((minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0))
            .coerceIn(0, MAX_SECONDS)

    companion object {
        const val MAX_SECONDS = 60 * 60

        fun of(seconds: Int) = DurationDraft(
            minutes = (seconds / 60).toString(),
            seconds = (seconds % 60).toString().padStart(2, '0'),
        )
    }
}

data class TimerUiState(
    val state: TimerState = TimerState.Idle,
    val remainingMs: Long = 0,
    val totalMs: Long = 0,
    val mode: WorkKind = WorkKind.TIMED,
    val work: DurationDraft = DurationDraft.of(30),
    val rest: DurationDraft = DurationDraft.of(180),
    val setsText: String = "3",
    val cues: CueSettings = CueSettings(),
) {
    val isRunning: Boolean get() = state is TimerState.Running
    val isPaused: Boolean get() = state is TimerState.Paused
    val isIdle: Boolean get() = state is TimerState.Idle
    val isAwaitingSet: Boolean get() = state is TimerState.AwaitingSet

    val sets: Int get() = (setsText.toIntOrNull() ?: 0).coerceIn(0, 99)

    /** The program the fields add up to, or null when they do not yet describe a valid one. */
    val draftProgram: TimerProgram?
        get() {
            if (sets < 1) return null
            if (mode == WorkKind.TIMED && work.totalSeconds < 1) return null
            return TimerProgram(
                sets = sets,
                work = mode,
                workSeconds = work.totalSeconds,
                restSeconds = rest.totalSeconds,
            )
        }

    val canStart: Boolean get() = draftProgram != null

    /** The phase whose length the cue preview should describe: the work, or the rest alone. */
    private val previewPhase: TimerPhase
        get() = if (mode == WorkKind.TIMED) TimerPhase.WORK else TimerPhase.REST

    private val previewMs: Long
        get() = if (mode == WorkKind.TIMED) {
            work.totalSeconds * 1000L
        } else {
            rest.totalSeconds * 1000L
        }

    /** What one interval of this program will sound, given its length. */
    val plannedCues: List<PlannedCue>
        get() = CuePlanner.plan(previewPhase, previewMs, cues)

    val quarterCuesApply: Boolean
        get() = cues.quarterCues &&
            previewPhase == TimerPhase.WORK &&
            previewMs >= CuePlanner.QUARTER_CUE_MIN_MS

    val thirtySecondWarningApplies: Boolean
        get() = cues.thirtySecondWarning && previewMs > 30_000

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

    val label: String? get() = state.activeLabel

    /** "set 2 of 5", or null when there is only one set or nothing running. */
    val setProgress: String?
        get() {
            val program = state.activeProgram ?: return null
            val current = state.currentSet ?: return null
            return if (program.sets > 1) "Set $current of ${program.sets}" else null
        }

    val progress: Float
        get() = if (totalMs <= 0) 0f else (1f - remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
}

class TimerViewModel(private val controller: TimerController) : ViewModel() {

    private val draft = MutableStateFlow(
        Draft(
            mode = WorkKind.TIMED,
            work = DurationDraft.of(controller.lastDurationSeconds(TimerPhase.WORK)),
            rest = DurationDraft.of(controller.lastDurationSeconds(TimerPhase.REST)),
            sets = controller.lastSets.toString(),
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
    fun setMode(mode: WorkKind) = draft.update { it.copy(mode = mode) }

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

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun cancel() = controller.cancel()

    fun dismiss() = controller.dismiss()

    private data class Draft(
        val mode: WorkKind,
        val work: DurationDraft,
        val rest: DurationDraft,
        val sets: String,
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
