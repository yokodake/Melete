package com.yokodake.melete.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.DiaryRepository
import com.yokodake.melete.data.Tracker
import com.yokodake.melete.data.entity.TrackerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A tracker being added (no [id]) or changed, as the dialog holds it. */
data class TrackerDraft(
    val id: String? = null,
    val label: String = "",
    val type: TrackerType = TrackerType.SCALE,
    val scaleMin: String = "0",
    val scaleMax: String = "5",
    val unit: String = "",
    /** Why the last save was refused, in words. */
    val problem: String? = null,
)

/** The list of daily trackers, and the one dialog that adds or changes them. */
class TrackersViewModel(private val diary: DiaryRepository) : ViewModel() {

    val trackers: StateFlow<List<Tracker>?> = diary.observeTrackers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _draft = MutableStateFlow<TrackerDraft?>(null)
    val draft: StateFlow<TrackerDraft?> = _draft.asStateFlow()

    fun startNew() {
        _draft.value = TrackerDraft()
    }

    fun startEdit(tracker: Tracker) {
        _draft.value = TrackerDraft(
            id = tracker.id,
            label = tracker.label,
            type = tracker.type,
            scaleMin = (tracker.scaleMin ?: 0).toString(),
            scaleMax = (tracker.scaleMax ?: 5).toString(),
            unit = tracker.unit.orEmpty(),
        )
    }

    fun change(draft: TrackerDraft) {
        _draft.value = draft.copy(problem = null)
    }

    fun dismiss() {
        _draft.value = null
    }

    fun save() {
        val draft = _draft.value ?: return
        viewModelScope.launch {
            val min = draft.scaleMin.toIntOrNull()
            val max = draft.scaleMax.toIntOrNull()
            val unit = draft.unit.ifBlank { null }
            val problem = if (draft.id == null) {
                diary.createTracker(draft.label, draft.type, min, max, unit)
            } else {
                diary.updateTracker(draft.id, draft.label, draft.type, min, max, unit)
            }
            _draft.value = if (problem == null) null else draft.copy(problem = problem)
        }
    }

    fun retire(id: String) {
        _draft.value = null
        viewModelScope.launch { diary.retireTracker(id) }
    }

    fun move(id: String, delta: Int) {
        viewModelScope.launch { diary.moveTracker(id, delta) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                TrackersViewModel(application.container.diaryRepository)
            }
        }
    }
}
