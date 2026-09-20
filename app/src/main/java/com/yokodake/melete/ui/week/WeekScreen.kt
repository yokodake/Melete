package com.yokodake.melete.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.BuildConfig
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.ui.theme.MeleteTheme
import java.time.LocalDate

@Composable
fun WeekRoute(
    onOpenOccurrence: (String) -> Unit,
    onAddExercise: (weekStart: LocalDate, trainingDate: LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    viewModel: WeekViewModel = viewModel(factory = WeekViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeekScreen(
        state = state,
        onPreviousWeek = viewModel::showPreviousWeek,
        onNextWeek = viewModel::showNextWeek,
        onCurrentWeek = viewModel::showCurrentWeek,
        onSeedSampleData = viewModel::seedSampleData,
        onClearSampleData = viewModel::clearSampleData,
        onOpenOccurrence = onOpenOccurrence,
        onAddExercise = { date -> onAddExercise(state.weekStart, date) },
        bottomBar = bottomBar,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    state: WeekUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onSeedSampleData: () -> Unit,
    onClearSampleData: () -> Unit,
    onOpenOccurrence: (String) -> Unit,
    onAddExercise: (LocalDate?) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rows = remember(state) { state.toRows() }
    val listState = rememberLazyListState()

    // Open near today without hiding the unscheduled section: the heading for today goes to the
    // top of the list, one short scroll away from the items that have no date yet.
    LaunchedEffect(state.weekStart) {
        val todayIndex = rows.indexOfFirst { it is WeekRow.DayHeading && it.isToday }
        listState.scrollToItem(if (todayIndex >= 0) todayIndex else 0)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                // An opaque container so that list rows scrolling underneath are hidden by the
                // bar instead of appearing cut off against an identical background.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Column {
                        Text(
                            text = if (state.isCurrentWeek) "This week" else "Week",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = state.weekLabel,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onPreviousWeek,
                        modifier = Modifier.semantics { contentDescription = "Previous week" },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    if (!state.isCurrentWeek) {
                        TextButton(onClick = onCurrentWeek) { Text("Today") }
                    }
                    IconButton(
                        onClick = onNextWeek,
                        modifier = Modifier.semantics { contentDescription = "Next week" },
                    ) {
                        Text("›", style = MaterialTheme.typography.headlineMedium)
                    }
                    if (BuildConfig.DEBUG) {
                        DeveloperMenu(
                            sampleDataPresent = state.sampleDataPresent,
                            onSeedSampleData = onSeedSampleData,
                            onClearSampleData = onClearSampleData,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = rows, key = { it.key }) { row ->
                when (row) {
                    is WeekRow.SectionHeading -> SectionHeading(
                        title = row.title,
                        subtitle = row.subtitle,
                        onAdd = { onAddExercise(null) },
                    )

                    is WeekRow.DayHeading -> DayHeading(
                        row = row,
                        onAdd = { onAddExercise(row.date) },
                    )

                    is WeekRow.Occurrence -> OccurrenceCard(
                        occurrence = row.occurrence,
                        onClick = { onOpenOccurrence(row.occurrence.id) },
                    )

                    is WeekRow.Hint -> Hint(row.text)
                }
            }
        }
    }
}

@Composable
private fun DeveloperMenu(
    sampleDataPresent: Boolean,
    onSeedSampleData: () -> Unit,
    onClearSampleData: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        modifier = Modifier.semantics { contentDescription = "Developer actions" },
    ) {
        Text("⋮", style = MaterialTheme.typography.titleLarge)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Insert sample data in this week") },
            onClick = {
                expanded = false
                onSeedSampleData()
            },
        )
        DropdownMenuItem(
            text = { Text("Remove all sample data") },
            enabled = sampleDataPresent,
            onClick = {
                expanded = false
                onClearSampleData()
            },
        )
    }
}

@Composable
private fun AddButton(description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text("+", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String?, onAdd: () -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AddButton("Add an exercise without a date", onAdd)
        }
        HorizontalDivider()
    }
}

@Composable
private fun DayHeading(row: WeekRow.DayHeading, onAdd: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = WeekMath.dayLabel(row.date),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (row.isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = if (row.isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (row.isToday) {
                Chip("Today", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            }
            Surface(modifier = Modifier.weight(1f), color = Color.Transparent) {}
            AddButton("Add an exercise to ${WeekMath.dayLabel(row.date)}", onAdd)
        }
        HorizontalDivider(
            color = if (row.isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
    }
}

@Composable
private fun OccurrenceCard(occurrence: PlannedOccurrence, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = occurrence.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (occurrence.isSampleData) {
                    Chip(
                        text = "SAMPLE",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                when (occurrence.state) {
                    OccurrenceState.PLANNED -> Unit
                    OccurrenceState.COMPLETED -> Chip(
                        text = "Done",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )

                    OccurrenceState.SKIPPED -> Chip(
                        text = "Skipped",
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = PrescriptionSummary.format(occurrence),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            occurrence.comment?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/** Flattened list rows, so that "scroll near today" is a plain list index. */
sealed interface WeekRow {
    val key: String

    data class SectionHeading(
        override val key: String,
        val title: String,
        val subtitle: String?,
    ) : WeekRow

    data class DayHeading(val date: LocalDate, val isToday: Boolean) : WeekRow {
        override val key: String get() = "day-$date"
    }

    data class Occurrence(val occurrence: PlannedOccurrence) : WeekRow {
        override val key: String get() = "occurrence-${occurrence.id}"
    }

    data class Hint(override val key: String, val text: String) : WeekRow
}

private fun WeekUiState.toRows(): List<WeekRow> = buildList {
    add(
        WeekRow.SectionHeading(
            key = "heading-unscheduled",
            title = "Unscheduled",
            subtitle = "Anytime this week",
        )
    )
    if (unscheduled.isEmpty()) {
        add(WeekRow.Hint("hint-unscheduled", "Nothing waiting without a date."))
    } else {
        unscheduled.forEach { add(WeekRow.Occurrence(it)) }
    }
    days.forEach { day ->
        add(WeekRow.DayHeading(day.date, day.isToday))
        if (day.items.isEmpty()) {
            add(WeekRow.Hint("hint-${day.date}", "Nothing planned."))
        } else {
            day.items.forEach { add(WeekRow.Occurrence(it)) }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun WeekScreenPreview() {
    val monday = LocalDate.of(2026, 9, 21)

    fun sample(name: String, date: LocalDate?, unilateral: Boolean = false) = PlannedOccurrence(
        id = "$name-$date",
        exerciseId = name,
        name = name,
        mode = ExerciseMode.REPETITIONS,
        unilateral = unilateral,
        measurementUnit = "kg",
        measurementMeaning = MeasurementMeaning.TOTAL_LOAD,
        trainingDate = date,
        weekStart = monday,
        prescriptionId = null,
        prescription = PrescriptionPayload(
            sets = 4,
            targetReps = 8,
            restSeconds = 90,
            measurement = Measurement(22.5, "kg", MeasurementMeaning.TOTAL_LOAD),
            rir = 2,
        ),
        prescriptionUnreadable = false,
        state = OccurrenceState.PLANNED,
        comment = null,
        orderIndex = 0,
        isSampleData = true,
    )

    MeleteTheme {
        WeekScreen(
            state = WeekUiState.build(
                weekStart = monday,
                today = monday.plusDays(2),
                occurrences = listOf(
                    sample("Mobility flow", null),
                    sample("Back squat", monday.plusDays(1)),
                    sample("Dumbbell row", monday.plusDays(2), unilateral = true),
                ),
                sampleDataPresent = true,
            ),
            onPreviousWeek = {},
            onNextWeek = {},
            onCurrentWeek = {},
            onSeedSampleData = {},
            onClearSampleData = {},
            onOpenOccurrence = {},
            onAddExercise = {},
        )
    }
}
