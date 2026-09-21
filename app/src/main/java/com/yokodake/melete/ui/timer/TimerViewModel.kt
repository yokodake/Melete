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
import com.yokodake.melete.data.timer.TimerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimerUiState(
    val state: TimerState = TimerState.Idle,
    val remainingMs: Long = 0,
    val totalMs: Long = 0,
    /** Typed minutes and seconds, kept as text so a half-typed field is not silently a zero. */
    val draftMinutes: String = "3",
    val draftSeconds: String = "00",
    val draftPhase: TimerPhase = TimerPhase.REST,
    val cues: CueSettings = CueSettings(),
) {
    val isRunning: Boolean get() = state is TimerState.Running
    val isPaused: Boolean get() = state is TimerState.Paused
    val isIdle: Boolean get() = state is TimerState.Idle

    /** The countdown the typed fields add up to. Clamped only where it is actually started. */
    val draftTotalSeconds: Int
        get() = (draftMinutes.toIntOrNull() ?: 0) * 60 + (draftSeconds.toIntOrNull() ?: 0)

    val canStart: Boolean get() = draftTotalSeconds > 0

    /** What this countdown will actually sound, given its length. */
    val plannedCues: List<PlannedCue>
        get() = CuePlanner.plan(draftPhase, draftTotalSeconds * 1000L, cues)

    val quarterCuesApply: Boolean
        get() = cues.quarterCues &&
            draftPhase == TimerPhase.WORK &&
            draftTotalSeconds * 1000L >= CuePlanner.QUARTER_CUE_MIN_MS

    val thirtySecondWarningApplies: Boolean
        get() = cues.thirtySecondWarning && draftTotalSeconds > 30

    /** The phase of whatever is on screen: the live run, or the one about to be started. */
    val shownPhase: TimerPhase
        get() = when (state) {
            is TimerState.Running -> state.phase
            is TimerState.Paused -> state.phase
            is TimerState.Finished -> state.phase
            is TimerState.Interrupted -> state.phase
            TimerState.Idle -> draftPhase
        }

    val label: String? get() = state.activeLabel

    val progress: Float
        get() = if (totalMs <= 0) 0f else (1f - remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
}

class TimerViewModel(private val controller: TimerController) : ViewModel() {

    private val draft = MutableStateFlow(
        Draft(
            phase = TimerPhase.REST,
            seconds = controller.lastDurationSeconds(TimerPhase.REST),
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
                    TimerState.Idle -> currentDraft.totalSeconds * 1000L
                },
                draftMinutes = currentDraft.minutes,
                draftSeconds = currentDraft.seconds,
                draftPhase = currentDraft.phase,
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
     * Switching between work and rest changes only which kind of countdown is about to start.
     * It deliberately does not reload the last-used duration: a number you have just typed is the
     * thing you care about, and having it replaced the moment you classify it is maddening.
     */
    fun setDraftPhase(phase: TimerPhase) {
        draft.update { it.copy(phase = phase) }
    }

    fun setDraftMinutes(value: String) {
        draft.update { it.copy(minutes = value.take(3)) }
    }

    fun setDraftSeconds(value: String) {
        draft.update { it.copy(seconds = value.take(2)) }
    }

    fun setCueSettings(settings: CueSettings) {
        // Goes through the controller first: a running countdown is replanned there, so a cue
        // switched off mid-rest stops being owed rather than only stopping next time.
        controller.setCueSettings(settings)
        draft.update { it.copy(cues = settings) }
    }

    fun start() {
        val current = draft.value
        val seconds = current.totalSeconds
        if (seconds <= 0) return
        controller.start(current.phase, seconds, current.cues)
    }

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun cancel() = controller.cancel()

    fun dismiss() = controller.dismiss()

    private data class Draft(
        val phase: TimerPhase,
        val minutes: String,
        val seconds: String,
        val cues: CueSettings,
    ) {
        constructor(phase: TimerPhase, seconds: Int, cues: CueSettings) : this(
            phase = phase,
            minutes = (seconds / 60).toString(),
            seconds = (seconds % 60).toString().padStart(2, '0'),
            cues = cues,
        )

        val totalSeconds: Int
            get() = ((minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0))
                .coerceIn(0, MAX_SECONDS)
    }

    companion object {
        private const val MAX_SECONDS = 60 * 60

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                TimerViewModel(application.container.timerController)
            }
        }
    }
}
