package com.yokodake.melete.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.TrainingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(
    private val repository: TrainingRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now(clock)

    private val weekStart = MutableStateFlow(WeekMath.weekStartOf(today))

    private val _message = MutableStateFlow<String?>(null)

    /** One-shot text for the snackbar. Kept apart from the week itself, which is pure data. */
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<WeekUiState> = weekStart
        .flatMapLatest { start ->
            combine(
                repository.observeWeek(start),
                repository.sampleDataPresent,
            ) { occurrences, sampleDataPresent ->
                WeekUiState.build(start, today, occurrences, sampleDataPresent)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeekUiState.build(
                weekStart = weekStart.value,
                today = today,
                occurrences = emptyList(),
                sampleDataPresent = false,
            ),
        )

    fun showPreviousWeek() {
        weekStart.value = weekStart.value.minusWeeks(1)
    }

    fun showNextWeek() {
        weekStart.value = weekStart.value.plusWeeks(1)
    }

    fun showCurrentWeek() {
        weekStart.value = WeekMath.weekStartOf(today)
    }

    fun seedSampleData() {
        val target = weekStart.value
        viewModelScope.launch { repository.seedSampleWeek(target) }
    }

    fun clearSampleData() {
        viewModelScope.launch { repository.clearSampleData() }
    }

    /**
     * Takes a planned exercise back out of the week. Reached only by a long press and a menu,
     * because an accidental swipe through the main screen must never quietly unplan training.
     *
     * Refused outright once anything has been recorded against it: removing evidence is not a
     * side effect of tidying a plan.
     */
    fun removeOccurrence(occurrenceId: String) {
        viewModelScope.launch {
            if (!repository.deleteOccurrenceIfEmpty(occurrenceId)) {
                _message.value = "Sets are recorded for this. Delete them in the logger first."
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                WeekViewModel(application.container.trainingRepository)
            }
        }
    }
}
