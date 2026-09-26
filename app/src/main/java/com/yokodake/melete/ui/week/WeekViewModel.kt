package com.yokodake.melete.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.DiaryRepository
import com.yokodake.melete.data.NudgeResult
import com.yokodake.melete.data.PlanItemRef
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
    private val diary: DiaryRepository,
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
                repository.observeWeekCircuits(start),
                repository.observeWeekModules(start),
                diary.observeDays(start, start.plusDays(6)),
                diary.observeMetrics(),
            ) { occurrences, circuits, modules, days, metrics ->
                WeekUiState.build(start, today, occurrences, circuits, modules, days, metrics)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeekUiState.build(
                weekStart = weekStart.value,
                today = today,
                occurrences = emptyList(),
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
                _message.value = "This entry has a log and can’t be removed without deleting it."
            }
        }
    }

    /**
     * How much evidence a placement carries, so a deletion can say what it would cost before it
     * happens rather than after.
     */
    suspend fun loggedSetCount(occurrenceId: String): Int =
        repository.loggedSetCount(occurrenceId)

    /**
     * Deletes a placement together with everything logged against it.
     *
     * Only ever reached from a confirmation that names the number of sets it will destroy. History
     * never goes through an ambiguous yes.
     */
    fun deleteOccurrenceAndLog(occurrenceId: String, sets: Int) {
        viewModelScope.launch {
            repository.deleteOccurrenceAndLog(occurrenceId)
            _message.value = if (sets == 0) {
                "Removed, along with its log"
            } else {
                "Removed, along with $sets logged set${if (sets == 1) "" else "s"}"
            }
        }
    }

    /** Makes another copy of a placement, waiting in this week's unscheduled area. */
    fun duplicateOccurrence(occurrenceId: String) {
        viewModelScope.launch {
            repository.duplicateOccurrence(occurrenceId)
            _message.value = "Copy added to Unscheduled"
        }
    }

    /**
     * Nudges a card one place up or down, into the neighbouring day at the edge of its own. Only
     * a refusal is worth a word: a card at the very top or bottom of the week simply stays.
     */
    fun nudge(item: PlanItemRef, delta: Int) {
        viewModelScope.launch {
            if (repository.nudge(item, delta) == NudgeResult.NEEDS_A_DAY) {
                _message.value = "Logged training needs a date."
            }
        }
    }

    // --------------------------------------------------- activities and circuits

    /**
     * Adds an activity that is only a name and a duration.
     *
     * No library entry is created: a run you went on once is an occurrence, not a movement you
     * plan to train. Given a date it lands on that day, otherwise it waits in the week.
     */
    fun addActivity(name: String, trainingDate: LocalDate?, minutes: Int?) {
        val week = weekStart.value
        viewModelScope.launch {
            repository.createOneOffActivity(
                name = name,
                weekStart = trainingDate?.let(WeekMath::weekStartOf) ?: week,
                trainingDate = trainingDate,
                plannedDurationSeconds = minutes?.takeIf { it > 0 }?.let { it * 60 },
            )
            _message.value = "Added ${name.trim()}"
        }
    }

    /** Copies a saved routine into the week as a circuit: a container plus its real stations. */
    fun scheduleRoutine(routineId: String, trainingDate: LocalDate?) {
        val week = weekStart.value
        viewModelScope.launch {
            val scheduled = repository.scheduleRoutine(
                routineId = routineId,
                weekStart = trainingDate?.let(WeekMath::weekStartOf) ?: week,
                trainingDate = trainingDate,
            )
            _message.value =
                if (scheduled == null) "That circuit no longer exists" else "Circuit added"
        }
    }

    /** How many stations of a circuit carry evidence, so a deletion can say what it would cost. */
    suspend fun circuitRecordedStations(circuitId: String): Int =
        repository.circuitRecordedStations(circuitId)

    fun removeCircuit(circuitId: String) {
        viewModelScope.launch {
            if (!repository.deleteCircuitIfEmpty(circuitId)) {
                _message.value = "This circuit has logs. Delete the plan and logs to remove it."
            }
        }
    }

    fun deleteCircuitAndLogs(circuitId: String, recorded: Int) {
        viewModelScope.launch {
            repository.deleteCircuitAndLogs(circuitId)
            _message.value =
                "Circuit removed, including logs for $recorded exercise" +
                    if (recorded == 1) "" else "s"
        }
    }

    // ------------------------------------------------------------- modules

    /** Dissolves the group; every member stays where it is. */
    fun ungroupModule(moduleInstanceId: String) {
        viewModelScope.launch {
            repository.ungroupModule(moduleInstanceId)
            _message.value = "Ungrouped"
        }
    }

    fun takeOutOfModule(occurrenceId: String) {
        viewModelScope.launch { repository.takeOccurrenceOutOfModule(occurrenceId) }
    }

    fun takeCircuitOutOfModule(circuitId: String) {
        viewModelScope.launch { repository.takeCircuitOutOfModule(circuitId) }
    }

    /** How many of a module's exercises carry evidence, so a deletion can say what it costs. */
    suspend fun moduleRecordedExercises(moduleInstanceId: String): Int =
        repository.moduleRecordedExercises(moduleInstanceId)

    fun removeModule(moduleInstanceId: String) {
        viewModelScope.launch {
            if (!repository.removeModuleIfEmpty(moduleInstanceId)) {
                _message.value = "This module has logs. Delete the plan and logs to remove it."
            }
        }
    }

    fun deleteModuleAndLogs(moduleInstanceId: String, recorded: Int) {
        viewModelScope.launch {
            repository.deleteModuleAndLogs(moduleInstanceId)
            _message.value =
                "Module removed, including logs for $recorded exercise" +
                    if (recorded == 1) "" else "s"
        }
    }

    /** Writes one day's diary; emptied completely, it goes. */
    fun saveDiary(date: LocalDate, text: String?, values: Map<String, Int?>) {
        viewModelScope.launch { diary.save(date, text, values) }
    }

    /** The week the planner is currently showing, for defaulting a move or a copy. */
    val shownWeekStart: LocalDate get() = weekStart.value

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                WeekViewModel(
                    application.container.trainingRepository,
                    application.container.diaryRepository,
                )
            }
        }
    }
}
