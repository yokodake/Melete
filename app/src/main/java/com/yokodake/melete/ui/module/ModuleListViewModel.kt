package com.yokodake.melete.ui.module

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.ModuleRemoval
import com.yokodake.melete.data.TrainingModule
import com.yokodake.melete.data.TrainingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModuleListUiState(
    val loading: Boolean = true,
    val modules: List<TrainingModule> = emptyList(),
    val removal: ModuleRemoval? = null,
    val message: String? = null,
)

/**
 * The saved modules, to manage. Putting one in a week happens in the library picker, beside
 * exercises and circuits.
 */
class ModuleListViewModel(private val repository: TrainingRepository) : ViewModel() {

    private data class Transient(
        val removal: ModuleRemoval? = null,
        val message: String? = null,
    )

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<ModuleListUiState> =
        combine(repository.observeModules(), transient) { modules, extras ->
            ModuleListUiState(
                loading = false,
                modules = modules,
                removal = extras.removal,
                message = extras.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ModuleListUiState(),
        )

    fun duplicate(moduleId: String) {
        viewModelScope.launch {
            repository.duplicateModule(moduleId)
            transient.update { it.copy(message = "Duplicated") }
        }
    }

    fun askToRemove(moduleId: String) {
        viewModelScope.launch {
            transient.update { it.copy(removal = repository.moduleRemovalImpact(moduleId)) }
        }
    }

    fun cancelRemoval() = transient.update { it.copy(removal = null) }

    /** Removes the template. Copies already in weeks are real planned work and stay as they are. */
    fun confirmRemoval() {
        val removal = transient.value.removal ?: return
        viewModelScope.launch {
            repository.removeModule(removal.module.id)
            transient.update {
                it.copy(
                    removal = null,
                    message = if (removal.scheduledCopies > 0) {
                        "Removed from the list. The ${removal.scheduledCopies} already scheduled " +
                            "are untouched."
                    } else {
                        "Removed"
                    },
                )
            }
        }
    }

    fun consumeMessage() = transient.update { it.copy(message = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                ModuleListViewModel(application.container.trainingRepository)
            }
        }
    }
}
