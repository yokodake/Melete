package com.yokodake.melete.ui.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import com.yokodake.melete.R
import com.yokodake.melete.ui.theme.doneColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.PerformedSet
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.PreviousResult
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.ui.timer.formatClock
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.ui.components.EffortSelector
import com.yokodake.melete.ui.components.NumberField
import com.yokodake.melete.ui.components.PrescriptionFields
import com.yokodake.melete.ui.components.trimNumber
import com.yokodake.melete.ui.week.PrescriptionSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun LoggerRoute(
    onBack: () -> Unit,
    viewModel: LoggerViewModel = viewModel(factory = LoggerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timer by viewModel.timerState.collectAsStateWithLifecycle()
    LoggerScreen(state = state, timer = timer, viewModel = viewModel, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggerScreen(
    state: LoggerUiState,
    timer: LoggerTimerState,
    viewModel: LoggerViewModel,
    onBack: () -> Unit,
) {
    val occurrence = state.occurrence
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Column {
                        Text(
                            text = occurrence?.name ?: "",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = occurrence?.trainingDate?.let(WeekMath::dayLabel)
                                ?: "Unscheduled",
                            style = MaterialTheme.typography.bodySmall,
                        )
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
            )
        },

    ) { padding ->
        if (occurrence == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PlannedCard(
                    occurrence = occurrence,
                    onEdit = viewModel::openPrescriptionEditor,
                )
            }
            item {
                TimerRow(
                    timer = timer,
                    canStartWork = occurrence.mode.isTimed &&
                        occurrence.prescription?.targetDurationSeconds != null,
                    restSeconds = occurrence.prescription?.restSeconds,
                    onStartWork = viewModel::startWork,
                    onStartRest = viewModel::startRest,
                )
            }
            if (occurrence.trainingDate == null) {
                item {
                    UnscheduledCard(
                        targetDate = state.targetDate,
                        today = state.today,
                        onChangeDate = { showDatePicker = true },
                    )
                }
            }
            if (state.previousResults.isNotEmpty()) {
                item { SectionLabel("Previous results") }
                items(items = state.previousResults, key = { it.trainingDate.toEpochDay() }) {
                    PreviousResultCard(it, occurrence)
                }
            }
            item {
                SectionLabel("How hard was this exercise?")
                EffortSelector(
                    selected = state.table.effort,
                    onSelect = viewModel::setTableEffort,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (occurrence.measurementUnit != null) {
                item {
                    MaxLoadRow(
                        table = state.table,
                        unit = occurrence.measurementUnit,
                        unilateral = occurrence.unilateral,
                        onMaxLoad = viewModel::setMaxLoad,
                    )
                }
            }
            item {
                SetTableHeader(
                    unit = occurrence.measurementUnit,
                    unilateral = occurrence.unilateral,
                )
            }
            items(items = state.table.rows, key = { it.number }) { row ->
                SetTableRow(
                    row = row,
                    unit = occurrence.measurementUnit,
                    unilateral = occurrence.unilateral,
                    onLoad = viewModel::setRowLoad,
                    onToggle = { viewModel.toggleRow(row.number) },
                )
            }
            item {
                TextButton(
                    onClick = viewModel::addRow,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("+  Add set")
                }
            }
            item {
                CommentRow(comment = occurrence.comment, onClick = viewModel::openCommentEditor)
            }
            item {
                StateRow(
                    state = occurrence.state,
                    onMark = viewModel::markState,
                )
            }
            // Taking an exercise back out of the week lives on the week screen, behind a long
            // press: it has no business sitting one mis-tap away from the sets being logged.
        }
    }

    state.prescriptionEditor?.let { form ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPrescriptionEditor,
            title = { Text("Plan for this copy") },
            text = {
                Column {
                    Text(
                        text = "Changes stay in this week. The library default is untouched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrescriptionFields(
                        state = form,
                        onStateChange = viewModel::updatePrescriptionEditor,
                        mode = occurrence?.mode ?: ExerciseMode.REPETITIONS,
                        unilateral = occurrence?.unilateral == true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::savePrescription) { Text("Save") } },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPrescriptionEditor) { Text("Cancel") }
            },
        )
    }

    state.commentEditor?.let { comment ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCommentEditor,
            title = { Text("Comment") },
            text = {
                Column {
                    Text(
                        text = "Belongs to this exercise on this day, not to a single set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = comment,
                        onValueChange = viewModel::updateCommentEditor,
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::saveComment) { Text("Save") } },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCommentEditor) { Text("Cancel") }
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.targetDate
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.setTargetDate(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text("Use this date") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** The set number shown for a row: counted per side when the exercise is unilateral. */
fun displayNumber(sets: List<PerformedSet>, set: PerformedSet, unilateral: Boolean): Int =
    if (unilateral && set.side != null) {
        sets.count { it.side == set.side && it.orderIndex <= set.orderIndex }
    } else {
        sets.count { it.orderIndex <= set.orderIndex }
    }

@Composable
private fun SectionLabel(text: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * The countdown, reachable from the exercise being logged. Starting one is always a deliberate
 * tap, and a running one shows its remaining time here so there is no need to leave the logger.
 */
@Composable
private fun TimerRow(
    timer: LoggerTimerState,
    canStartWork: Boolean,
    restSeconds: Int?,
    onStartWork: () -> Unit,
    onStartRest: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (timer.active) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (timer.active) {
                        val phase = if (timer.phase == TimerPhase.WORK) "Work" else "Rest"
                        if (timer.paused) "$phase paused" else phase
                    } else {
                        "Timer"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = if (timer.active) {
                        formatClock(timer.remainingMs)
                    } else {
                        "Not running"
                    },
                    style = if (timer.active) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (timer.active) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (canStartWork) {
                TextButton(onClick = onStartWork) { Text("Work") }
            }
            TextButton(onClick = onStartRest) {
                Text(restSeconds?.let { "Rest ${PrescriptionSummary.duration(it)}" } ?: "Rest")
            }
        }
    }
}

@Composable
private fun PlannedCard(occurrence: PlannedOccurrence, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Planned", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = PrescriptionSummary.format(occurrence),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

@Composable
private fun UnscheduledCard(targetDate: LocalDate, today: LocalDate, onChangeDate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("No date yet", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "Will be filed under ${WeekMath.dayLabel(targetDate)}" +
                        if (targetDate == today) " (today)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onChangeDate) { Text("Change") }
        }
    }
}

@Composable
private fun PreviousResultCard(result: PreviousResult, occurrence: PlannedOccurrence) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = WeekMath.dayLabel(result.trainingDate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = result.sets.joinToString("  ") {
                    formatSet(it.payload, it.side, occurrence.measurementUnit)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PerformedSetRow(
    set: PerformedSet,
    number: Int,
    unit: String?,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = if (isEditing) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append("Set ")
                    append(number)
                    set.side?.let { append(if (it == BodySide.LEFT) " L" else " R") }
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(72.dp),
            )
            Text(
                text = formatSet(set.payload, side = null, unit = unit),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun CommentRow(comment: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Comment", style = MaterialTheme.typography.labelMedium)
            Text(
                text = comment ?: "Add a note about this exercise today",
                style = MaterialTheme.typography.bodyMedium,
                color = if (comment == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun StateRow(state: OccurrenceState, onMark: (OccurrenceState) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                onMark(
                    if (state == OccurrenceState.COMPLETED) {
                        OccurrenceState.PLANNED
                    } else {
                        OccurrenceState.COMPLETED
                    }
                )
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(if (state == OccurrenceState.COMPLETED) "Done ✓" else "Mark done")
        }
        OutlinedButton(
            onClick = {
                onMark(
                    if (state == OccurrenceState.SKIPPED) {
                        OccurrenceState.PLANNED
                    } else {
                        OccurrenceState.SKIPPED
                    }
                )
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(if (state == OccurrenceState.SKIPPED) "Skipped" else "Skip")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetEntryBar(
    state: LoggerUiState,
    occurrence: PlannedOccurrence,
    onDraftChange: ((SetDraft) -> SetDraft) -> Unit,
    onSide: (BodySide) -> Unit,
    onEffort: (EffortLevel?) -> Unit,
    onToggleEffort: () -> Unit,
    onConfirm: () -> Unit,
    onUndo: () -> Unit,
    onCancelEdit: () -> Unit,
    onStartRest: () -> Unit,
) {
    val draft = state.draft
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (occurrence.unilateral) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Side", style = MaterialTheme.typography.labelLarge)
                    FilterChip(
                        selected = draft.side == BodySide.LEFT,
                        onClick = { onSide(BodySide.LEFT) },
                        label = { Text("Left") },
                    )
                    FilterChip(
                        selected = draft.side == BodySide.RIGHT,
                        onClick = { onSide(BodySide.RIGHT) },
                        label = { Text("Right") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (occurrence.mode.isTimed) {
                    NumberField(
                        label = "Seconds",
                        value = draft.durationSeconds,
                        onValueChange = { value -> onDraftChange { it.copy(durationSeconds = value) } },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    NumberField(
                        label = "Reps",
                        value = draft.reps,
                        onValueChange = { value -> onDraftChange { it.copy(reps = value) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                occurrence.measurementUnit?.let { unit ->
                    NumberField(
                        label = unit,
                        value = draft.measurement,
                        onValueChange = { value -> onDraftChange { it.copy(measurement = value) } },
                        decimal = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (draft.showEffortFields) {
                Text("How hard?", style = MaterialTheme.typography.labelLarge)
                EffortSelector(selected = draft.effort, onSelect = onEffort)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onToggleEffort) {
                    Text(if (draft.showEffortFields) "Hide RPE" else "RPE")
                }
                if (draft.editingSetId != null) {
                    TextButton(onClick = onCancelEdit) { Text("Cancel") }
                } else if (state.undoableSetId != null) {
                    TextButton(onClick = onUndo) { Text("Undo") }
                    TextButton(onClick = onStartRest) { Text("Rest") }
                }
                Button(
                    onClick = onConfirm,
                    enabled = state.canConfirm,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when {
                            draft.editingSetId != null -> "Save correction"
                            draft.side == BodySide.LEFT -> "Confirm left"
                            draft.side == BodySide.RIGHT -> "Confirm right"
                            else -> "Confirm set"
                        }
                    )
                }
            }
        }
    }
}

/**
 * The heaviest set, which is the number worth seeing first, and the fastest way to fill a session
 * where every set was the same. Typing here fills every row that has not been ticked yet.
 */
@Composable
private fun MaxLoadRow(
    table: SetTable,
    unit: String,
    unilateral: Boolean,
    onMaxLoad: (String, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Max load",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "($unit)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        if (unilateral) {
            SideLabel("L")
            LoadField(table.maxLoad, enabled = true) { onMaxLoad(it, false) }
            Spacer(Modifier.width(8.dp))
            SideLabel("R")
            LoadField(table.maxLoadRight, enabled = true) { onMaxLoad(it, true) }
        } else {
            LoadField(table.maxLoad, enabled = true) { onMaxLoad(it, false) }
        }
    }
}

@Composable
private fun SetTableHeader(unit: String?, unilateral: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("Set", Modifier.width(40.dp))
        HeaderCell("Reps", Modifier.width(56.dp))
        if (unit != null) {
            if (unilateral) {
                HeaderCell("Left ($unit)", Modifier.weight(1f))
                HeaderCell("Right ($unit)", Modifier.weight(1f))
            } else {
                HeaderCell("Load ($unit)", Modifier.weight(1f))
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        HeaderCell("Done", Modifier.width(56.dp))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * One planned set, filled in and ticked off.
 *
 * There is no delete: a row is either recorded or it is not, and unticking takes back exactly what
 * ticking wrote. Keeping the planned rows on screen either way is what will let a later screen say
 * what was planned against what was actually done.
 */
@Composable
private fun SetTableRow(
    row: SetRow,
    unit: String?,
    unilateral: Boolean,
    onLoad: (Int, String, Boolean) -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${row.number}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
            )
            Text(
                text = row.reps?.toString() ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(56.dp),
            )
            if (unit != null) {
                // A recorded row is not editable in place: untick it to change what it says, so
                // that altering history is always a deliberate two-step.
                LoadField(row.load, enabled = !row.done, modifier = Modifier.weight(1f)) {
                    onLoad(row.number, it, false)
                }
                if (unilateral) {
                    Spacer(Modifier.width(8.dp))
                    LoadField(row.loadRight, enabled = !row.done, modifier = Modifier.weight(1f)) {
                        onLoad(row.number, it, true)
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            DoneCheck(done = row.done, onToggle = onToggle, modifier = Modifier.width(56.dp))
        }
    }
}

@Composable
private fun SideLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 4.dp),
    )
}

@Composable
private fun LoadField(
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            onValueChange(typed.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.'))
        },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.widthIn(min = 72.dp),
    )
}

/** The tick. Green and filled once the set has happened, an empty outline until then. */
@Composable
private fun DoneCheck(done: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val (container, content) = doneColors()
    IconButton(
        onClick = onToggle,
        modifier = modifier.semantics {
            contentDescription = if (done) "Recorded, tap to take it back" else "Record this set"
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (done) container else Color.Transparent,
                    shape = CircleShape,
                )
                .border(
                    width = if (done) 0.dp else 1.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_timer_done),
                contentDescription = null,
                tint = if (done) content else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Compact rendering of one performed set. Absent values are simply not shown. */
private fun formatSet(payload: ActualSetPayload, side: BodySide?, unit: String?): String {
    val parts = mutableListOf<String>()
    payload.reps?.let { parts += "$it reps" }
    payload.durationSeconds?.let { parts += PrescriptionSummary.duration(it) }
    payload.measurement?.let { measurement ->
        val value = trimNumber(measurement.value)
        parts += when (measurement.meaning) {
            MeasurementMeaning.TOTAL_LOAD -> "$value ${measurement.unit}"
            MeasurementMeaning.ADDED_LOAD -> "+$value ${measurement.unit}"
            MeasurementMeaning.ASSISTANCE -> "−$value ${measurement.unit} assist"
        }
    }
    payload.effort?.let { parts += it.label.lowercase() }
    side?.let { parts += if (it == BodySide.LEFT) "L" else "R" }
    if (parts.isEmpty() && unit != null) parts += "no values"
    return parts.joinToString(" · ")
}
