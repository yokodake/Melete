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
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.ScheduledCircuit
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.timer.PrescriptionProgram
import com.yokodake.melete.data.timer.StationPlan
import com.yokodake.melete.data.timer.TimerController
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.ui.CircuitDetailDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** One station, with the share of the circuit's clock that belongs to it. */
data class CircuitStation(
    val occurrence: PlannedOccurrence,
    val estimatedSeconds: Int,
)

data class CircuitDetailUiState(
    val loading: Boolean = true,
    val circuit: ScheduledCircuit? = null,
    val stations: List<CircuitStation> = emptyList(),
    val estimatedSeconds: Int? = null,
    /** Non-null while the user is being asked whether to call off a countdown already running. */
    val replacePrompt: String? = null,
) {
    val recorded: Int get() = stations.count { it.occurrence.hasRecord }

    val completed: Boolean get() = stations.isNotEmpty() && recorded == stations.size

    /**
     * What the logging button offers.
     *
     * Planned work has no log yet, so the button offers to make one. Once anything has been
     * written the same button reopens what it says, which is a different promise.
     */
    val logButtonLabel: String get() = if (recorded > 0) "Update log" else "Log the workout"
}

/**
 * What a scheduled circuit *is*, before anything is asked of you.
 *
 * The same shape as opening an exercise: the thing first, then the two things worth doing next.
 * Going straight into the review meant a circuit could only be answered, never read — and the
 * order of its stations and how long it will take are exactly what you want to see beforehand.
 */
class CircuitDetailViewModel(
    repository: TrainingRepository,
    private val timer: TimerController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val circuitId: String =
        savedStateHandle.toRoute<CircuitDetailDestination>().circuitInstanceId

    private val transient = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CircuitDetailUiState> = combine(
        repository.observeScheduledCircuit(circuitId),
        transient,
    ) { circuit, prompt ->
        val program = circuit?.let(::programOf)
        val shares = program?.estimatedSecondsByEntry().orEmpty()
        CircuitDetailUiState(
            loading = circuit == null,
            circuit = circuit,
            stations = circuit?.stations.orEmpty().mapIndexed { index, station ->
                CircuitStation(station, shares.getOrElse(index) { 0 })
            },
            estimatedSeconds = program?.estimatedSeconds(),
            replacePrompt = prompt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CircuitDetailUiState(),
    )

    /**
     * The circuit's own program, which every number on this screen comes from.
     *
     * The same function the review and the timer use, so what this screen promises is what the
     * countdown will actually run.
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
     * Offers to run the circuit. There is one timer at a time by design, so replacing a countdown
     * someone is in the middle of is a decision rather than a side effect of opening a screen.
     */
    fun requestStartTimer(onStarted: () -> Unit) {
        if (!timer.hasActiveProgram) {
            startTimer(onStarted)
        } else {
            transient.value = timer.state.value.currentExerciseLabel ?: "a countdown"
        }
    }

    fun confirmStartTimer(onStarted: () -> Unit) {
        transient.value = null
        startTimer(onStarted)
    }

    fun dismissReplacePrompt() = transient.update { null }

    private fun startTimer(onStarted: () -> Unit) {
        val circuit = uiState.value.circuit ?: return
        timer.start(programOf(circuit))
        onStarted()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                CircuitDetailViewModel(
                    application.container.trainingRepository,
                    application.container.timerController,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
