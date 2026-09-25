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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.ui.components.CompactTextField
import com.yokodake.melete.ui.components.EffortSelector
import com.yokodake.melete.ui.components.NumberField
import com.yokodake.melete.ui.components.PrescriptionFields
import com.yokodake.melete.ui.components.trimNumber
import com.yokodake.melete.ui.components.VariationChip
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
    // Marking done ends the screen: the workout is written, and there is nothing further to say
    // about it here.
    LaunchedEffect(Unit) {
        viewModel.finished.collect { onBack() }
    }
    LoggerScreen(state = state, viewModel = viewModel, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggerScreen(
    state: LoggerUiState,
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
                        // Bigger than a top bar usually goes: the screen is a table of numbers,
                        // and what those numbers are about should not be the smallest claim on it.
                        Text(
                            text = occurrence?.name ?: "",
                            style = MaterialTheme.typography.titleLarge,
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
                actions = {
                    if (occurrence != null) {
                        LoggerMenu(
                            state = occurrence.state,
                            onMark = viewModel::markState,
                        )
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
            if (!(state.isActivity && occurrence.prescription?.targetDurationSeconds == null)) {
                item {
                    PlannedCard(
                        occurrence = occurrence,
                        onEdit = viewModel::openPrescriptionEditor,
                    )
                }
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
            if (state.isActivity && occurrence.isOneOff) {
                item {
                    ActivityNameField(
                        name = occurrence.name,
                        onRename = viewModel::renameActivity,
                    )
                }
            }
            item {
                // Label and control on one line. A heading, a rule and a full-width box was
                // three bands of screen for one optional word.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Effort",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    EffortSelector(
                        selected = state.table.effort,
                        onSelect = viewModel::setTableEffort,
                        modifier = Modifier.width(190.dp),
                    )
                }
            }
            if (!state.isActivity) {
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
                item {
                    // One item holding the whole table: the gap between sets is then 4dp rather
                    // than the 8dp the page puts between its sections, which is the right
                    // relationship — rows of one table belong closer together than sections of a
                    // screen.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.table.rows.forEach { row ->
                            SetTableRow(
                                row = row,
                                unit = occurrence.measurementUnit,
                                unilateral = occurrence.unilateral,
                                onLoad = viewModel::setRowLoad,
                                onToggle = { viewModel.toggleRow(row.number) },
                            )
                        }
                    }
                }
                item {
                    TextButton(
                        onClick = viewModel::addRow,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text("+  Add set")
                    }
                }
            }
            item {
                DurationRow(
                    state = state,
                    onValueChange = viewModel::setDurationMinutes,
                    onToggle = viewModel::toggleDuration,
                )
            }
            item {
                CommentBox(comment = state.comment, onChange = viewModel::updateComment)
            }
            item {
                DoneRow(
                    table = state.table,
                    // An activity has nothing to check: that it happened is the whole claim.
                    measured = !state.isActivity && occurrence.measurementUnit != null,
                    blocked = !state.isActivity,
                    onDone = viewModel::markDone,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Planned", style = MaterialTheme.typography.labelMedium)
                    occurrence.variationTag?.let { VariationChip(it) }
                }
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
private fun CommentBox(comment: String, onChange: (String) -> Unit) {
    CompactTextField(
        value = comment,
        onValueChange = onChange,
        label = "Comment",
        placeholder = "A note about this exercise today",
        singleLine = false,
        minHeight = 64,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Finishing the workout, which is the one thing this screen is for.
 *
 * A filled button rather than an outlined one: this is the action the screen exists to take, and
 * it commits — everything above it is a draft until it is pressed. It stays enabled even when the
 * table is short of what it needs, and says what is missing instead, because a dead button that
 * will not explain itself is the worst of both.
 */
@Composable
private fun DoneRow(
    table: SetTable,
    measured: Boolean,
    blocked: Boolean,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (table.committed) "Save changes" else "Mark done")
        }
        table.blocker(measured).takeIf { blocked }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * How long it took.
 *
 * Behind a control for anything with a set table, because most sessions do not need the number
 * typed and one more field in the logging path is one more thing between you and the next set. An
 * activity is the exception: its duration is most of what it has to say, so it is simply there.
 *
 * An empty field is not zero. The estimate sits in it in grey — that is what will be saved, marked
 * as inferred — and typing over it makes the number yours. Clearing it hands the question back.
 */
@Composable
private fun DurationRow(
    state: LoggerUiState,
    onValueChange: (String) -> Unit,
    onToggle: () -> Unit,
) {
    if (!state.table.showDuration) {
        TextButton(onClick = onToggle) {
            Text(
                state.inferredDurationSeconds
                    ?.let { "+  Time taken (${PrescriptionSummary.duration(it)})" }
                    ?: "+  Time taken"
            )
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Time taken",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        NumberField(
            label = "min",
            value = state.table.durationMinutes,
            onValueChange = onValueChange,
            placeholder = state.inferredDurationMinutes,
            modifier = Modifier.width(110.dp),
        )
    }
}

/**
 * The name of a one-off activity, editable in place.
 *
 * A typed-in activity has no library entry to open and rename, and "Runnign" staring back at you
 * from the diary for a year is not acceptable. Correcting it also corrects its derived identity,
 * so two sessions spelled the same way still group together.
 */
@Composable
private fun ActivityNameField(name: String, onRename: (String) -> Unit) {
    var typed by remember(name) { mutableStateOf(name) }
    CompactTextField(
        value = typed,
        onValueChange = {
            typed = it
            onRename(it)
        },
        label = "Activity",
        minHeight = 48,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The things you do to a workout now and again, kept out of the way of the things you always do. */
@Composable
private fun LoggerMenu(state: OccurrenceState, onMark: (OccurrenceState) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "Workout actions" },
        ) {
            Text("⋮", style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(if (state == OccurrenceState.SKIPPED) "Not skipped after all" else "Skip")
                },
                onClick = {
                    expanded = false
                    onMark(
                        if (state == OccurrenceState.SKIPPED) {
                            OccurrenceState.PLANNED
                        } else {
                            OccurrenceState.SKIPPED
                        }
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaxLoadRow(
    table: SetTable,
    unit: String,
    unilateral: Boolean,
    onMaxLoad: (String, Boolean) -> Unit,
) {
    // Label beside the fields. This only became possible once the fields stopped being
    // OutlinedTextFields, which demand 280dp each and left a heading no room to render into.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Max load ($unit)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (unilateral) {
            SideLabel("L")
            LoadField(table.maxLoad, modifier = Modifier.width(76.dp)) { onMaxLoad(it, false) }
            Spacer(Modifier.width(8.dp))
            SideLabel("R")
            LoadField(table.maxLoadRight, modifier = Modifier.width(76.dp)) { onMaxLoad(it, true) }
        } else {
            LoadField(table.maxLoad, modifier = Modifier.width(120.dp)) { onMaxLoad(it, false) }
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
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
                // A recorded row stays editable. The tick says the set happened; the load says
                // what it weighed, and correcting the second is not a statement about the first.
                LoadField(row.load, modifier = Modifier.weight(1f)) {
                    onLoad(row.number, it, false)
                }
                if (unilateral) {
                    Spacer(Modifier.width(8.dp))
                    LoadField(row.loadRight, modifier = Modifier.weight(1f)) {
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
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    CompactTextField(
        value = value,
        onValueChange = { typed ->
            onValueChange(typed.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.'))
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.widthIn(min = 64.dp),
    )
}

/** The tick. Green and filled once the set has happened, an empty outline until then. */
@Composable
private fun DoneCheck(done: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val (container, content) = doneColors()
    // Not an IconButton: that enforces a 48dp touch target, and with a 40dp field beside it the
    // tick alone was setting the height of every row. 40dp is still a comfortable target.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clickable(onClick = onToggle)
            .semantics {
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
