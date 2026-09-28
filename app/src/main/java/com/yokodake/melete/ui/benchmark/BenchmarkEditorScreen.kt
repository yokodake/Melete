package com.yokodake.melete.ui.benchmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.BenchmarkDraft
import com.yokodake.melete.data.BenchmarkRepository
import com.yokodake.melete.data.entity.BenchmarkMeasure
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.BenchmarkEditorDestination
import com.yokodake.melete.ui.components.ChoiceField
import com.yokodake.melete.ui.components.CompactTextField
import com.yokodake.melete.ui.components.EditorTopBar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The form as typed. */
data class BenchmarkForm(
    val name: String = "",
    val measure: BenchmarkMeasure = BenchmarkMeasure.LOAD,
    val unit: String = BenchmarkMeasure.LOAD.defaultUnit,
    val loadMeaning: MeasurementMeaning = MeasurementMeaning.TOTAL_LOAD,
    val unilateral: Boolean = false,
    val higherIsBetter: Boolean = true,
    val protocol: String = "",
) {
    val canSave: Boolean get() = name.isNotBlank()

    fun toDraft() = BenchmarkDraft(
        name = name,
        measure = measure,
        unit = unit,
        loadMeaning = loadMeaning.takeIf { measure == BenchmarkMeasure.LOAD },
        unilateral = unilateral,
        higherIsBetter = higherIsBetter,
        protocol = protocol,
    )

    /** A new measure brings its unit along, unless a unit of one's own was typed. */
    fun withMeasure(next: BenchmarkMeasure): BenchmarkForm = copy(
        measure = next,
        unit = if (unit.isBlank() || unit == measure.defaultUnit) next.defaultUnit else unit,
    )
}

data class BenchmarkEditorUiState(
    val isNew: Boolean = true,
    val loading: Boolean = true,
    val form: BenchmarkForm = BenchmarkForm(),
)

class BenchmarkEditorViewModel(
    private val repository: BenchmarkRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id = savedStateHandle.toRoute<BenchmarkEditorDestination>().benchmarkId

    private val _state = MutableStateFlow(BenchmarkEditorUiState(isNew = id == null, loading = id != null))
    val state: StateFlow<BenchmarkEditorUiState> = _state.asStateFlow()

    /** Emitted once saved, so the screen closes. */
    val saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        if (id != null) {
            viewModelScope.launch {
                val benchmark = repository.getBenchmark(id)
                _state.update {
                    it.copy(
                        loading = false,
                        form = benchmark?.let { b ->
                            BenchmarkForm(
                                name = b.name,
                                measure = b.measure,
                                unit = b.unit,
                                loadMeaning = b.loadMeaning ?: MeasurementMeaning.TOTAL_LOAD,
                                unilateral = b.unilateral,
                                higherIsBetter = b.higherIsBetter,
                                protocol = b.protocol.orEmpty(),
                            )
                        } ?: it.form,
                    )
                }
            }
        }
    }

    fun update(form: BenchmarkForm) = _state.update { it.copy(form = form) }

    fun save() {
        val form = _state.value.form
        if (!form.canSave) return
        viewModelScope.launch {
            if (id == null) repository.create(form.toDraft()) else repository.update(id, form.toDraft())
            saved.emit(Unit)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                BenchmarkEditorViewModel(application.container.benchmarkRepository, createSavedStateHandle())
            }
        }
    }
}

@Composable
fun BenchmarkEditorRoute(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: BenchmarkEditorViewModel = viewModel(factory = BenchmarkEditorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.saved.collect { onDone() } }
    BenchmarkEditorScreen(state, viewModel::update, viewModel::save, onBack)
}

/**
 * A benchmark's definition: what it is called, what number it records and in what, one value or
 * both sides, which way is better, and the protocol that keeps results comparable. A change of
 * definition applies from now on; results already recorded keep the terms they were recorded in.
 */
@Composable
fun BenchmarkEditorScreen(
    state: BenchmarkEditorUiState,
    onChange: (BenchmarkForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val form = state.form
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            EditorTopBar(
                title = if (state.isNew) "New benchmark" else "Edit benchmark",
                onBack = onBack,
                onSave = onSave,
                canSave = form.canSave && !state.loading,
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompactTextField(
                value = form.name,
                onValueChange = { onChange(form.copy(name = it)) },
                label = "Name",
                placeholder = "Weighted pull-up",
                minHeight = 48,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField(
                    value = form.measure,
                    options = BenchmarkMeasure.entries,
                    optionLabel = { it.label },
                    onSelect = { onChange(form.withMeasure(it)) },
                    label = "Measurement",
                    modifier = Modifier.weight(1f),
                )
                CompactTextField(
                    value = form.unit,
                    onValueChange = { onChange(form.copy(unit = it.take(8))) },
                    label = "Unit",
                    minHeight = 44,
                    modifier = Modifier.weight(0.6f),
                )
            }
            if (form.measure == BenchmarkMeasure.LOAD) {
                ChoiceField(
                    value = form.loadMeaning,
                    options = MeasurementMeaning.entries,
                    optionLabel = {
                        when (it) {
                            MeasurementMeaning.TOTAL_LOAD -> "Total load"
                            MeasurementMeaning.ADDED_LOAD -> "Added to bodyweight (± assistance)"
                        }
                    },
                    onSelect = { onChange(form.copy(loadMeaning = it)) },
                    label = "Load",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField(
                    value = form.unilateral,
                    options = listOf(false, true),
                    optionLabel = { if (it) "Left and right" else "One value" },
                    onSelect = { onChange(form.copy(unilateral = it)) },
                    label = "Sides",
                    modifier = Modifier.weight(1f),
                )
                ChoiceField(
                    value = form.higherIsBetter,
                    options = listOf(true, false),
                    optionLabel = { if (it) "Higher" else "Lower" },
                    onSelect = { onChange(form.copy(higherIsBetter = it)) },
                    label = "Better is",
                    modifier = Modifier.weight(1f),
                )
            }
            CompactTextField(
                value = form.protocol,
                onValueChange = { onChange(form.copy(protocol = it)) },
                label = "Protocol (optional)",
                placeholder = "20 mm · 7 s · added load",
                singleLine = false,
                minHeight = 48,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
