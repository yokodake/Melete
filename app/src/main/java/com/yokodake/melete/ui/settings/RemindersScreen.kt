package com.yokodake.melete.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.data.DiaryRepository
import com.yokodake.melete.data.Tracker
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.reminders.ReminderConfig
import com.yokodake.melete.data.reminders.ReminderRules
import com.yokodake.melete.data.reminders.ReminderSettings
import com.yokodake.melete.data.reminders.Reminders
import com.yokodake.melete.data.reminders.TrackerReminder
import com.yokodake.melete.data.reminders.TrackerTrigger
import com.yokodake.melete.data.reminders.WorkoutKind
import com.yokodake.melete.data.reminders.WorkoutMatch
import com.yokodake.melete.data.reminders.timeOf
import com.yokodake.melete.ui.components.BackButton
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.components.ChoiceField
import com.yokodake.melete.ui.components.CompactTextField
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

data class RemindersUiState(
    val config: ReminderConfig = ReminderConfig(),
    val trackers: List<Tracker> = emptyList(),
    /** What an after-workout reminder can follow: the library's exercises, circuits and modules. */
    val workouts: List<WorkoutMatch> = emptyList(),
)

class RemindersViewModel(
    private val settings: ReminderSettings,
    private val reminders: Reminders,
    diary: DiaryRepository,
    training: TrainingRepository,
) : ViewModel() {

    private val workouts = combine(
        training.observeLibrary(),
        training.observeRoutines(),
        training.observeModules(),
    ) { exercises, routines, modules ->
        exercises.filter { it.deletedAtEpochMs == null }.map { WorkoutMatch(WorkoutKind.EXERCISE, it.id, it.name) } +
            routines.map { WorkoutMatch(WorkoutKind.CIRCUIT, it.id, it.name) } +
            modules.map { WorkoutMatch(WorkoutKind.MODULE, it.id, it.name) }
    }

    val uiState: StateFlow<RemindersUiState> = combine(
        settings.config,
        diary.observeTrackers(),
        workouts,
    ) { config, trackers, workouts -> RemindersUiState(config, trackers, workouts.sortedBy { it.name.lowercase() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState(settings.config.value))

    fun update(change: (ReminderConfig) -> ReminderConfig) {
        settings.update(change)
        reminders.reschedule()
    }

    fun saveTracker(reminder: TrackerReminder) = update { config ->
        val others = config.trackers.filter { it.id != reminder.id }
        val at = config.trackers.indexOfFirst { it.id == reminder.id }
        config.copy(trackers = if (at < 0) others + reminder else others.toMutableList().apply { add(at, reminder) })
    }

    fun deleteTracker(id: String) = update { config -> config.copy(trackers = config.trackers.filter { it.id != id }) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MeleteApplication).container
                RemindersViewModel(container.reminderSettings, container.reminders, container.diaryRepository, container.trainingRepository)
            }
        }
    }
}

private fun notificationsAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

/** The reminders, each off until turned on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersRoute(
    onBack: () -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config = state.config
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = notificationsAllowed(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = notificationsAllowed(context)
    }
    // Turning a reminder on asks for notifications, once, if they are not allowed yet.
    fun turnOn() {
        if (!allowed) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val anyOn = config.daily.enabled || config.weekly.enabled || config.trackers.any { it.enabled }
    var editing by remember { mutableStateOf<TrackerReminder?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                title = { Text("Reminders", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (anyOn && !allowed) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Notifications are off for Melete.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }) { Text("Turn on") }
                        }
                    }
                }
            }

            item { Heading("Logging") }
            item {
                SettingRow("Daily logging reminder", "When today has work left to log") {
                    Switch(
                        checked = config.daily.enabled,
                        onCheckedChange = { on ->
                            if (on) turnOn()
                            viewModel.update { it.copy(daily = it.daily.copy(enabled = on)) }
                        },
                    )
                }
            }
            if (config.daily.enabled) {
                item {
                    SettingRow("Time", null) {
                        TimeButton(config.daily.minuteOfDay) { m -> viewModel.update { it.copy(daily = it.daily.copy(minuteOfDay = m)) } }
                    }
                }
            }

            item { Heading("Weekly review") }
            item {
                SettingRow("Weekly review reminder", "Opens the week to look back on") {
                    Switch(
                        checked = config.weekly.enabled,
                        onCheckedChange = { on ->
                            if (on) turnOn()
                            viewModel.update { it.copy(weekly = it.weekly.copy(enabled = on)) }
                        },
                    )
                }
            }
            if (config.weekly.enabled) {
                item {
                    SettingRow("Day", null) {
                        ChoiceField(
                            value = DayOfWeek.of(config.weekly.day),
                            options = DayOfWeek.entries,
                            optionLabel = { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                            onSelect = { d -> viewModel.update { it.copy(weekly = it.weekly.copy(day = d.value)) } },
                            modifier = Modifier.width(170.dp),
                        )
                    }
                }
                item {
                    SettingRow("Time", null) {
                        TimeButton(config.weekly.minuteOfDay) { m -> viewModel.update { it.copy(weekly = it.weekly.copy(minuteOfDay = m)) } }
                    }
                }
            }

            item { Heading("Trackers") }
            items(config.trackers, key = { it.id }) { reminder ->
                TrackerReminderRow(
                    reminder = reminder,
                    trackers = state.trackers,
                    onOpen = { editing = reminder },
                    onEnabled = { on ->
                        if (on) turnOn()
                        viewModel.saveTracker(reminder.copy(enabled = on))
                    },
                )
            }
            item {
                TextButton(onClick = { editing = TrackerReminder(id = UUID.randomUUID().toString()) }) {
                    Text("+ Add tracker reminder")
                }
            }
        }
    }

    editing?.let { reminder ->
        val isNew = config.trackers.none { it.id == reminder.id }
        TrackerReminderDialog(
            initial = reminder,
            trackers = state.trackers,
            workouts = state.workouts,
            onSave = {
                if (it.enabled) turnOn()
                viewModel.saveTracker(it)
                editing = null
            },
            onDelete = if (isNew) null else ({ viewModel.deleteTracker(reminder.id); editing = null }),
            onDismiss = { editing = null },
        )
    }
}

private fun timeText(context: Context, minuteOfDay: Int): String =
    if (DateFormat.is24HourFormat(context)) {
        "%d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
    } else {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(timeOf(minuteOfDay))
    }

/** The time, as a button that opens a clock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(minuteOfDay: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(timeText(context, minuteOfDay)) }
    if (open) {
        val picker = rememberTimePickerState(
            initialHour = minuteOfDay / 60,
            initialMinute = minuteOfDay % 60,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(picker.hour * 60 + picker.minute)
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = picker) },
        )
    }
}

/** "Energy, Finger discomfort · Mon, Wed at 9:00" or "… · day after Board climbing at 9:00". */
private fun summary(context: Context, reminder: TrackerReminder, trackers: List<Tracker>): String {
    val names = trackers.filter { it.id in reminder.trackerIds }.joinToString(", ") { it.label }.ifEmpty { "No trackers" }
    val whenText = when (reminder.trigger) {
        TrackerTrigger.SCHEDULED -> DayOfWeek.entries.filter { it.value in reminder.days }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            .ifEmpty { "no days" }

        TrackerTrigger.AFTER_WORKOUT -> {
            val things = ExerciseCategory.entries.filter { it.name in reminder.categories }.map { it.label } +
                reminder.workouts.map { it.name }
            "day after " + ReminderRules.listShort(things).ifEmpty { "nothing" }
        }
    }
    return "$names · $whenText at ${timeText(context, reminder.minuteOfDay)}"
}

@Composable
private fun TrackerReminderRow(
    reminder: TrackerReminder,
    trackers: List<Tracker>,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                reminder.phrase.trim().ifEmpty { ReminderRules.DEFAULT_TRACKER_TITLE },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                summary(context, reminder, trackers),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = reminder.enabled, onCheckedChange = onEnabled)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

/** One tracker reminder: what it asks, when, and for which trackers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerReminderDialog(
    initial: TrackerReminder,
    trackers: List<Tracker>,
    workouts: List<WorkoutMatch>,
    onSave: (TrackerReminder) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    val complete = draft.trackerIds.isNotEmpty() && when (draft.trigger) {
        TrackerTrigger.SCHEDULED -> draft.days.isNotEmpty()
        TrackerTrigger.AFTER_WORKOUT -> draft.categories.isNotEmpty() || draft.workouts.isNotEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete == null) "New tracker reminder" else "Tracker reminder") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CompactTextField(
                    value = draft.phrase,
                    onValueChange = { draft = draft.copy(phrase = it) },
                    label = "Phrase",
                    placeholder = "How are the fingers?",
                    minHeight = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
                Label("Trackers")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    trackers.forEach { tracker ->
                        val on = tracker.id in draft.trackerIds
                        FilterChip(
                            selected = on,
                            onClick = {
                                draft = draft.copy(trackerIds = if (on) draft.trackerIds - tracker.id else draft.trackerIds + tracker.id)
                            },
                            label = { Text(tracker.label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Time", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TimeButton(draft.minuteOfDay) { draft = draft.copy(minuteOfDay = it) }
                }
                Label("When")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.trigger == TrackerTrigger.SCHEDULED,
                        onClick = { draft = draft.copy(trigger = TrackerTrigger.SCHEDULED) },
                        label = { Text("On days") },
                    )
                    FilterChip(
                        selected = draft.trigger == TrackerTrigger.AFTER_WORKOUT,
                        onClick = { draft = draft.copy(trigger = TrackerTrigger.AFTER_WORKOUT) },
                        label = { Text("Day after workouts") },
                    )
                }
                when (draft.trigger) {
                    TrackerTrigger.SCHEDULED -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            val on = day.value in draft.days
                            FilterChip(
                                selected = on,
                                onClick = { draft = draft.copy(days = if (on) draft.days - day.value else draft.days + day.value) },
                                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                            )
                        }
                    }

                    TrackerTrigger.AFTER_WORKOUT -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Logged or planned, not skipped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExerciseCategory.entries.forEach { category ->
                                val on = category.name in draft.categories
                                FilterChip(
                                    selected = on,
                                    onClick = {
                                        draft = draft.copy(categories = if (on) draft.categories - category.name else draft.categories + category.name)
                                    },
                                    leadingIcon = { CategoryDot(category) },
                                    label = { Text(category.shortLabel) },
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            draft.workouts.forEach { workout ->
                                InputChip(
                                    selected = false,
                                    onClick = { draft = draft.copy(workouts = draft.workouts - workout) },
                                    label = { Text(workout.name) },
                                    trailingIcon = { Text("✕") },
                                )
                            }
                        }
                        TextButton(onClick = { picking = true }) { Text("+ Workouts") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, enabled = complete) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (picking) {
        WorkoutPicker(
            all = workouts,
            chosen = draft.workouts,
            onDone = { draft = draft.copy(workouts = it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/** Exercises, circuits and modules to follow, ticked in a filterable list. */
@Composable
private fun WorkoutPicker(
    all: List<WorkoutMatch>,
    chosen: List<WorkoutMatch>,
    onDone: (List<WorkoutMatch>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(chosen.associateBy { it.kind to it.id }) }
    var filter by remember { mutableStateOf("") }
    val kindLabel = mapOf(WorkoutKind.EXERCISE to "", WorkoutKind.CIRCUIT to "Circuit", WorkoutKind.MODULE to "Module")
    val shown = all.filter { it.name.contains(filter.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workouts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = "Filter",
                    minHeight = 44,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { "${it.kind}:${it.id}" }) { workout ->
                        val key = workout.kind to workout.id
                        val on = key in picked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (on) picked - key else picked + (key to workout) },
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Text(workout.name, modifier = Modifier.weight(1f))
                            Text(
                                kindLabel[workout.kind].orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(picked.values.sortedBy { it.name.lowercase() }) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
