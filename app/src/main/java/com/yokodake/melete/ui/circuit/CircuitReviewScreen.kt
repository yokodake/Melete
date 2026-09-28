package com.yokodake.melete.ui.circuit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import com.yokodake.melete.ui.components.circuitShapeLine
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.components.SignKey
import com.yokodake.melete.ui.components.flipSign
import com.yokodake.melete.ui.components.TrainingDateCard
import com.yokodake.melete.ui.components.TrainingDatePickerDialog
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.CompactTextField
import com.yokodake.melete.ui.components.EffortSelector
import com.yokodake.melete.ui.components.NumberField
import com.yokodake.melete.ui.theme.doneColors
import com.yokodake.melete.ui.week.PrescriptionSummary

/**
 * Logging a whole circuit: one row per exercise, expanded only where you have something to say.
 *
 * After a circuit there are four or five things to confirm and one of them was probably not quite
 * as planned. Collapsed rows make the ordinary case two taps; expanding one gives it the same set
 * table, max-load fallback and effort scale the single-exercise logger has, because it is the same
 * record and must not be a different kind of one.
 */
@Composable
fun CircuitReviewRoute(
    onBack: () -> Unit,
    viewModel: CircuitReviewViewModel = viewModel(factory = CircuitReviewViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.finished.collect { onBack() } }
    CircuitReviewScreen(state = state, viewModel = viewModel, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitReviewScreen(
    state: CircuitReviewUiState,
    viewModel: CircuitReviewViewModel,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    if (showDatePicker) {
        TrainingDatePickerDialog(
            initial = state.targetDate,
            onPick = viewModel::setTargetDate,
            onDismiss = { showDatePicker = false },
        )
    }
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
                            text = state.circuit?.name.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = state.circuit?.trainingDate?.let(WeekMath::dayLabel)
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
        // Anchored, as in the exercise logger: reachable with every station expanded, and above
        // the keyboard together with the reason it cannot save yet.
        bottomBar = {
            if (state.circuit != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        state.blocker?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.committed) "Save changes" else "Mark done")
                        }
                    }
                }
            }
        },
    ) { padding ->
        val circuit = state.circuit ?: run {
            if (!state.loading) {
                Text(
                    text = "This circuit is no longer in the week.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            }
            return@Scaffold
        }
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
            // An unscheduled circuit is filed under a day when it is logged. Say which, and let
            // it be changed, exactly as the single-exercise logger does.
            if (circuit.trainingDate == null) {
                item {
                    TrainingDateCard(
                        targetDate = state.targetDate,
                        today = state.today,
                        onChangeDate = { showDatePicker = true },
                    )
                }
            }
            item {
                // The shape in one line; the stations below say what they are themselves.
                Text(
                    text = circuitShapeLine(
                        rounds = circuit.rounds,
                        transitionSeconds = circuit.transitionSeconds,
                        roundRestSeconds = circuit.roundRestSeconds,
                        estimatedSeconds = state.stations.sumOf { it.suggestedDurationSeconds },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { HorizontalDivider() }

            state.stations.forEach { station ->
                item(key = station.occurrence.id) {
                    StationRow(station = station, viewModel = viewModel)
                }
            }
        }
    }

}

/**
 * One exercise: a tick, a name and a max load, with everything else behind the row.
 *
 * The collapsed row is what you use with chalk on your hands. The max load sits there because it
 * is the one number that is almost always worth recording and the one that stands for every set
 * that says nothing of its own.
 */
@Composable
private fun StationRow(station: StationReview, viewModel: CircuitReviewViewModel) {
    val id = station.occurrence.id
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // One height for every collapsed row. A load field is taller than two lines of text,
            // so without this a list of stations stepped up and down depending on which of them
            // happened to track a weight.
            Row(
                modifier = Modifier.heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DoneCheck(
                    label = station.occurrence.name,
                    done = station.completed,
                    onToggle = { viewModel.toggleStation(id) },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                        .clickable(
                            onClickLabel = "${if (station.expanded) "Collapse" else "Expand"} ${station.occurrence.name}",
                        ) { viewModel.toggleExpanded(id) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryDot(station.occurrence.category)
                        Text(
                            text = station.occurrence.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = listOfNotNull(
                            PrescriptionSummary.format(station.occurrence),
                            station.partial,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val signed = station.occurrence.measurementMeaning == MeasurementMeaning.ADDED_LOAD
                station.occurrence.measurementUnit?.let { unit ->
                    NumberField(
                        label = unit,
                        value = station.table.maxLoad,
                        onValueChange = { viewModel.setMaxLoad(id, it) },
                        decimal = true,
                        signed = signed,
                        modifier = Modifier.width(88.dp).semantics {
                            contentDescription = "${station.occurrence.name}, max load ($unit)"
                        },
                    )
                    if (signed) {
                        SignKey("${station.occurrence.name} max load") {
                            viewModel.setMaxLoad(id, flipSign(station.table.maxLoad))
                        }
                    }
                }
                Text(
                    text = if (station.expanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable { viewModel.toggleExpanded(id) }
                        .semantics {
                            contentDescription = "${if (station.expanded) "Collapse" else "Expand"} ${station.occurrence.name}"
                        },
                )
            }

            if (!station.expanded) return@Column

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Effort",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    EffortSelector(
                        selected = station.table.effort,
                        onSelect = { viewModel.setEffort(id, it) },
                        modifier = Modifier.width(180.dp),
                    )
                }
                station.table.rows.forEach { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DoneCheck(
                            label = "${station.occurrence.name}, round ${row.number}",
                            done = row.done,
                            onToggle = { viewModel.toggleSet(id, row.number) },
                        )
                        Text(
                            text = "Round ${row.number}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        station.occurrence.measurementUnit?.let {
                            NumberField(
                                label = if (station.occurrence.unilateral) "L" else it,
                                value = row.load,
                                onValueChange = { value ->
                                    viewModel.setRowLoad(id, row.number, value)
                                },
                                decimal = true,
                                signed = station.occurrence.measurementMeaning == MeasurementMeaning.ADDED_LOAD,
                                modifier = Modifier.width(80.dp).semantics {
                                    contentDescription = "${station.occurrence.name}, round ${row.number}, " +
                                        "${if (station.occurrence.unilateral) "left load" else "load"} ($it)"
                                },
                            )
                            if (station.occurrence.unilateral) {
                                NumberField(
                                    label = "R",
                                    value = row.loadRight,
                                    onValueChange = { value ->
                                        viewModel.setRowLoad(id, row.number, value, right = true)
                                    },
                                    decimal = true,
                                    signed = station.occurrence.measurementMeaning == MeasurementMeaning.ADDED_LOAD,
                                    modifier = Modifier.width(80.dp).semantics {
                                        contentDescription = "${station.occurrence.name}, round ${row.number}, right load ($it)"
                                    },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Time taken", style = MaterialTheme.typography.bodyMedium)
                    }
                    NumberField(
                        label = "m",
                        value = station.table.durationMinutes,
                        onValueChange = { viewModel.setDurationMinutes(id, it) },
                        placeholder = ((station.suggestedDurationSeconds + 30) / 60).toString(),
                        modifier = Modifier.width(100.dp),
                    )
                }
                CompactTextField(
                    value = station.comment,
                    onValueChange = { viewModel.setComment(id, it) },
                    label = "Comment",
                    singleLine = false,
                    minHeight = 56,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The tick.
 *
 * Large and unmistakable on purpose: it is the one control that is pressed with tired hands, and
 * it is the difference between a plan and a record.
 */
@Composable
private fun DoneCheck(label: String, done: Boolean, onToggle: () -> Unit) {
    val (container, content) = doneColors()
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = if (done) container else MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = if (done) container else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics {
                contentDescription = label
                stateDescription = if (done) "Done" else "Not done"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Text("✓", style = MaterialTheme.typography.titleMedium, color = content)
        }
    }
}
