package com.yokodake.melete.ui.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.data.Tracker
import com.yokodake.melete.data.entity.TrackerType
import com.yokodake.melete.ui.components.ChoiceField
import com.yokodake.melete.ui.components.CompactTextField
import com.yokodake.melete.ui.components.NumberField

@Composable
fun TrackersRoute(
    onBack: () -> Unit,
    viewModel: TrackersViewModel = viewModel(factory = TrackersViewModel.Factory),
) {
    val trackers by viewModel.trackers.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    TrackersScreen(
        trackers = trackers.orEmpty(),
        onAdd = viewModel::startNew,
        onEdit = viewModel::startEdit,
        onMove = viewModel::move,
        onBack = onBack,
    )
    draft?.let {
        TrackerDialog(
            draft = it,
            onChange = viewModel::change,
            onSave = viewModel::save,
            onRetire = { id -> viewModel.retire(id) },
            onDismiss = viewModel::dismiss,
        )
    }
}

/** What the diary tracks each day, in the order its dialog asks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackersScreen(
    trackers: List<Tracker>,
    onAdd: () -> Unit,
    onEdit: (Tracker) -> Unit,
    onMove: (String, Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text("Daily trackers", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.semantics { contentDescription = "Add a tracker" },
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "Asked in this order in each day's notes. None of them is ever required. " +
                        "Changing or removing one leaves days already recorded as they were.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trackers.isEmpty()) {
                item {
                    Text(
                        text = "Nothing tracked. Days keep their notes all the same.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            itemsIndexed(trackers, key = { _, it -> it.id }) { index, tracker ->
                TrackerRow(
                    tracker = tracker,
                    canMoveUp = index > 0,
                    canMoveDown = index < trackers.lastIndex,
                    onClick = { onEdit(tracker) },
                    onMove = { delta -> onMove(tracker.id, delta) },
                )
            }
        }
    }
}

@Composable
private fun TrackerRow(
    tracker: Tracker,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tracker.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = kindLine(tracker),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { onMove(-1) },
                enabled = canMoveUp,
                modifier = Modifier.semantics { contentDescription = "Move ${tracker.label} up" },
            ) { Text("▲") }
            IconButton(
                onClick = { onMove(1) },
                enabled = canMoveDown,
                modifier = Modifier.semantics { contentDescription = "Move ${tracker.label} down" },
            ) { Text("▼") }
        }
    }
}

private fun kindLine(tracker: Tracker): String = when (tracker.type) {
    TrackerType.SCALE -> "Scale ${tracker.scaleMin}–${tracker.scaleMax}"
    TrackerType.NUMBER -> "Number" + tracker.unit?.let { " in $it" }.orEmpty()
    TrackerType.CHECK -> "Checkmark"
    TrackerType.TEXT -> "Comment"
}

@Composable
private fun TrackerDialog(
    draft: TrackerDraft,
    onChange: (TrackerDraft) -> Unit,
    onSave: () -> Unit,
    onRetire: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "New tracker" else "Edit tracker") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactTextField(
                    value = draft.label,
                    onValueChange = { onChange(draft.copy(label = it)) },
                    label = "Name",
                    placeholder = "Sleep, weight, skin…",
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceField(
                    value = draft.type,
                    options = TrackerType.entries,
                    optionLabel = { it.label },
                    onSelect = { onChange(draft.copy(type = it)) },
                    label = "Kind",
                    modifier = Modifier.fillMaxWidth(),
                )
                when (draft.type) {
                    TrackerType.SCALE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            label = "Lowest",
                            value = draft.scaleMin,
                            onValueChange = { onChange(draft.copy(scaleMin = it)) },
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            label = "Highest",
                            value = draft.scaleMax,
                            onValueChange = { onChange(draft.copy(scaleMax = it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    TrackerType.NUMBER -> CompactTextField(
                        value = draft.unit,
                        onValueChange = { onChange(draft.copy(unit = it)) },
                        label = "Unit (optional)",
                        placeholder = "kg",
                        minHeight = 48,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    TrackerType.CHECK, TrackerType.TEXT -> Unit
                }
                if (draft.id != null) {
                    Text(
                        text = "Changes apply from now on. Days already recorded keep what they say.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                draft.problem?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (draft.id != null) {
                    TextButton(onClick = { onRetire(draft.id) }) {
                        Text("Stop tracking", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
