package com.yokodake.melete.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.DiaryRepository
import com.yokodake.melete.data.TrackedValue
import com.yokodake.melete.data.Tracker
import com.yokodake.melete.data.TrackerReading
import com.yokodake.melete.data.entity.TrackerType
import com.yokodake.melete.ui.DiaryDestination
import com.yokodake.melete.ui.components.trimNumber
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Why a field's definition is not simply the tracker's current one. */
enum class FieldNote { AS_RECORDED, NO_LONGER_TRACKED }

/** One tracker on the day's page, in the definition its value is read against. */
data class DiaryField(val tracker: Tracker, val note: FieldNote? = null)

data class DiaryUiState(
    val date: LocalDate,
    val loaded: Boolean = false,
    val text: String = "",
    val fields: List<DiaryField> = emptyList(),
    val readings: Map<String, TrackerReading> = emptyMap(),
    /** Numbers as typed, so "72." survives while it is being written. */
    val typed: Map<String, String> = emptyMap(),
)

/** What is being written, before it is saved. */
private data class Draft(
    val text: String,
    val readings: Map<String, TrackerReading>,
    val typed: Map<String, String>,
    /** The definitions the day's values were recorded with, as they were when the page opened. */
    val recorded: Map<String, Tracker>,
)

/**
 * One day's notes and trackers, written on a page of their own.
 *
 * A value already recorded keeps the tracker as it was that day, even if the tracker has changed
 * since: editing a past day never re-reads its 3 on a scale of 0–5 as a 3 on today's 1–10. Clear
 * such a value and the field returns to the tracker's current definition — setting it again is
 * recording it now. Leaving for the tracker editor and coming back keeps what was being written.
 */
class DiaryViewModel(
    private val diary: DiaryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val date: LocalDate =
        LocalDate.ofEpochDay(savedStateHandle.toRoute<DiaryDestination>().dateEpochDay)

    private val draft = MutableStateFlow<Draft?>(null)

    private val finishedEvents = Channel<Unit>(Channel.BUFFERED)
    val finished = finishedEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val day = diary.observeDays(date, date).first()[date]
            val values = day?.values.orEmpty()
            draft.value = Draft(
                text = day?.text.orEmpty(),
                readings = values.mapValues { it.value.reading },
                typed = values.filterValues { it.tracker.type == TrackerType.NUMBER }
                    .mapValues { it.value.reading.number?.let(::trimNumber).orEmpty() },
                recorded = values.mapValues { it.value.tracker },
            )
        }
    }

    val state: StateFlow<DiaryUiState> = combine(draft, diary.observeTrackers()) { draft, trackers ->
        if (draft == null) return@combine DiaryUiState(date)
        val current = trackers.associateBy { it.id }
        fun field(id: String, now: Tracker?): DiaryField? {
            val then = draft.recorded[id]
            return when {
                // Retired: shown as recorded for as long as the page is open, so clearing it does
                // not make the field vanish under the finger.
                now == null -> then?.let { DiaryField(it, FieldNote.NO_LONGER_TRACKED) }
                then != null && then != now && draft.readings[id] != null ->
                    DiaryField(then, FieldNote.AS_RECORDED)
                else -> DiaryField(now)
            }
        }
        DiaryUiState(
            date = date,
            loaded = true,
            text = draft.text,
            fields = trackers.mapNotNull { field(it.id, it) } +
                draft.recorded.keys.filter { it !in current }.mapNotNull { field(it, null) },
            readings = draft.readings,
            typed = draft.typed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryUiState(date))

    fun setText(text: String) {
        draft.update { it?.copy(text = text) }
    }

    fun setReading(id: String, reading: TrackerReading?) {
        draft.update { d ->
            d?.copy(readings = if (reading == null || reading.isEmpty) d.readings - id else d.readings + (id to reading))
        }
    }

    fun setTyped(id: String, typed: String) {
        draft.update { it?.copy(typed = it.typed + (id to typed)) }
        setReading(id, typed.toDoubleOrNull()?.let { TrackerReading(number = it) })
    }

    fun save() {
        val current = state.value
        if (!current.loaded) return
        viewModelScope.launch {
            diary.save(
                date = date,
                text = current.text,
                values = current.fields.mapNotNull { field ->
                    current.readings[field.tracker.id]?.let { TrackedValue(field.tracker, it) }
                },
            )
            finishedEvents.send(Unit)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                DiaryViewModel(application.container.diaryRepository, createSavedStateHandle())
            }
        }
    }
}
