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
import kotlinx.coroutines.launch

data class TimerUiState(
    val state: TimerState = TimerState.Idle,
    val remainingMs: Long = 0,
    val totalMs: Long = 0,
    val draftSeconds: Int = 180,
    val draftPhase: TimerPhase = TimerPhase.REST,
    val cues: CueSettings = CueSettings(),
) {
    val isRunning: Boolean get() = state is TimerState.Running
    val isPaused: Boolean get() = state is TimerState.Paused
    val isIdle: Boolean get() = state is TimerState.Idle

    /** What this countdown will actually sound, given its length. */
    val plannedCues: List<PlannedCue>
        get() = CuePlanner.plan(draftPhase, draftSeconds * 1000L, cues)

    val quarterCuesApply: Boolean
        get() = cues.quarterCues &&
            draftPhase == TimerPhase.WORK &&
            draftSeconds * 1000L >= CuePlanner.QUARTER_CUE_MIN_MS

    val thirtySecondWarningApplies: Boolean
        get() = cues.thirtySecondWarning && draftSeconds > 30

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
                    TimerState.Idle -> currentDraft.seconds * 1000L
                },
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

    fun setDraftPhase(phase: TimerPhase) {
        draft.value = draft.value.copy(
            phase = phase,
            seconds = controller.lastDurationSeconds(phase),
        )
    }

    fun setDraftSeconds(seconds: Int) {
        draft.value = draft.value.copy(seconds = seconds.coerceIn(1, 60 * 60))
    }

    fun adjustDraftSeconds(delta: Int) = setDraftSeconds(draft.value.seconds + delta)

    fun setCueSettings(settings: CueSettings) {
        controller.setCueSettings(settings)
        draft.value = draft.value.copy(cues = settings)
    }

    fun start() {
        val current = draft.value
        controller.start(current.phase, current.seconds, current.cues)
    }

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun cancel() = controller.cancel()

    fun dismiss() = controller.dismiss()

    private data class Draft(
        val phase: TimerPhase,
        val seconds: Int,
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
