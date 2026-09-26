package com.yokodake.melete.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.TrackerReading
import com.yokodake.melete.data.entity.TrackerType
import com.yokodake.melete.ui.components.ChoiceField
import com.yokodake.melete.ui.components.CompactTextField
import com.yokodake.melete.ui.components.NumberField

@Composable
fun DiaryRoute(
    onBack: () -> Unit,
    onEditTrackers: () -> Unit,
    viewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Saving ends the page, as marking a workout done ends the logger.
    LaunchedEffect(Unit) {
        viewModel.finished.collect { onBack() }
    }
    DiaryScreen(
        state = state,
        onText = viewModel::setText,
        onReading = viewModel::setReading,
        onTyped = viewModel::setTyped,
        onSave = viewModel::save,
        onEditTrackers = onEditTrackers,
        onBack = onBack,
    )
}

/**
 * One day's notes and trackers. Nothing here is ever required — an untouched tracker is simply
 * not recorded — and nothing here is a workout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    state: DiaryUiState,
    onText: (String) -> Unit,
    onReading: (String, TrackerReading?) -> Unit,
    onTyped: (String, String) -> Unit,
    onSave: () -> Unit,
    onEditTrackers: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Column {
                        Text(WeekMath.dayLabel(state.date), style = MaterialTheme.typography.titleLarge)
                        Text("Daily notes", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = { DiaryMenu(onEditTrackers = onEditTrackers) },
            )
        },
        // Anchored, so saving is always a thumb away however long the page grows.
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = onSave,
                    enabled = state.loaded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Save") }
            }
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CompactTextField(
                    value = state.text,
                    onValueChange = onText,
                    label = "Notes",
                    placeholder = "Slept badly, skin still sore from Tuesday.",
                    singleLine = false,
                    minLines = 4,
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(items = state.fields, key = { it.tracker.id }) { field ->
                TrackerField(
                    field = field,
                    reading = state.readings[field.tracker.id],
                    typed = state.typed[field.tracker.id].orEmpty(),
                    onReading = { onReading(field.tracker.id, it) },
                    onTyped = { onTyped(field.tracker.id, it) },
                )
            }
        }
    }
}

@Composable
private fun TrackerField(
    field: DiaryField,
    reading: TrackerReading?,
    typed: String,
    onReading: (TrackerReading?) -> Unit,
    onTyped: (String) -> Unit,
) {
    val tracker = field.tracker
    Column {
        when (tracker.type) {
            TrackerType.SCALE -> ChoiceField(
                value = reading?.number?.toInt(),
                // "Not set" first: leaving it unrecorded is as findable as recording it.
                options = listOf<Int?>(null) + tracker.scale,
                optionLabel = { it?.toString() ?: "Not set" },
                onSelect = { value -> onReading(value?.let { TrackerReading(number = it.toDouble()) }) },
                label = "${tracker.label} (${tracker.scaleMin}–${tracker.scaleMax})",
                modifier = Modifier.fillMaxWidth(),
            )

            TrackerType.NUMBER -> NumberField(
                label = tracker.label + tracker.unit?.let { " ($it)" }.orEmpty(),
                value = typed,
                onValueChange = onTyped,
                decimal = true,
                modifier = Modifier.fillMaxWidth(),
            )

            TrackerType.CHECK -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tracker.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = reading?.number == 1.0,
                    onCheckedChange = { on -> onReading(if (on) TrackerReading(number = 1.0) else null) },
                )
            }

            TrackerType.TEXT -> CompactTextField(
                value = reading?.text.orEmpty(),
                onValueChange = { onReading(TrackerReading(text = it)) },
                label = tracker.label,
                singleLine = false,
                minHeight = 48,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (field.note) {
            FieldNote.AS_RECORDED -> "As recorded that day. Clear it to use the tracker as it is now."
            FieldNote.NO_LONGER_TRACKED -> "No longer tracked. Kept as recorded that day."
            null -> null
        }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun DiaryMenu(onEditTrackers: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "More" },
        ) {
            Text("⋮", style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit daily trackers") },
                onClick = {
                    expanded = false
                    onEditTrackers()
                },
            )
        }
    }
}
