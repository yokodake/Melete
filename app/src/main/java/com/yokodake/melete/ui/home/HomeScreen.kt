package com.yokodake.melete.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import com.yokodake.melete.ui.theme.progressColor
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.R
import com.yokodake.melete.core.WeekMath
import com.yokodake.melete.data.AppSettings
import com.yokodake.melete.data.BenchmarkRepository
import com.yokodake.melete.data.DiaryRepository
import com.yokodake.melete.ui.week.DiarySummary
import com.yokodake.melete.ui.week.DiarySummaryText
import com.yokodake.melete.ui.week.diarySummary
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.ui.dashboard.DashboardStats
import com.yokodake.melete.ui.theme.doneColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val summary: TodaySummary? = null,
    val reminder: BenchmarkReminder? = null,
    /** Anything still planned, today on or undated this week on. Unknown until read. */
    val hasUpcomingPlan: Boolean = true,
    /** Today's diary, when there is an entry: opening it updates, not starts. */
    val dailyNote: DiarySummary? = null,
)

/**
 * Home: today at a glance, the daily note, one quiet benchmark reminder, and the places that are
 * not part of a training day. Counts nothing of its own — it reads what the week and the
 * benchmarks already hold.
 */
class HomeViewModel(
    training: TrainingRepository,
    benchmarks: BenchmarkRepository,
    diary: DiaryRepository,
    private val preferences: SharedPreferences,
    settings: AppSettings,
) : ViewModel() {

    private val today = LocalDate.now()
    private val dismissedUntil = MutableStateFlow(
        preferences.getLong(DISMISSED_KEY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
    )

    private val reminder = combine(
        benchmarks.observeStandings(),
        dismissedUntil,
        settings.benchmarkReminderMonths,
    ) { standings, dismissed, months ->
        BenchmarkReminder.pick(standings, today, dismissed, months)
    }

    private val dailyNote = combine(diary.observeDays(today, today), diary.observeTrackers()) { days, trackers ->
        days[today]?.takeIf { !it.isEmpty }?.let { diarySummary(it, trackers) }
            ?.takeIf { !it.isEmpty }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        training.observeWeek(WeekMath.weekStartOf(today)),
        training.observeWeekCircuits(WeekMath.weekStartOf(today)),
        reminder,
        training.observeHasUpcomingPlan(today),
        dailyNote,
    ) { occurrences, circuits, reminder, upcoming, note ->
        HomeUiState(
            today = today,
            summary = TodaySummary.build(today, occurrences, circuits),
            reminder = reminder,
            hasUpcomingPlan = upcoming,
            dailyNote = note,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(today = today))

    /** Hides the reminder for a month. A display preference, not part of the training record. */
    fun dismissReminder() {
        val until = today.plusMonths(1)
        preferences.edit().putLong(DISMISSED_KEY, until.toEpochDay()).apply()
        dismissedUntil.value = until
    }

    companion object {
        private const val DISMISSED_KEY = "benchmarkReminderDismissedUntil"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeleteApplication
                HomeViewModel(
                    application.container.trainingRepository,
                    application.container.benchmarkRepository,
                    application.container.diaryRepository,
                    application.getSharedPreferences("home", Context.MODE_PRIVATE),
                    application.container.settings,
                )
            }
        }
    }
}

@Composable
fun HomeRoute(
    onOpenToday: () -> Unit,
    onOpenDailyNote: (LocalDate) -> Unit,
    onOpenBenchmarks: () -> Unit,
    onOpenLibrary: () -> Unit,
    onImportPlan: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onOpenToday = onOpenToday,
        onOpenDailyNote = { onOpenDailyNote(state.today) },
        onOpenBenchmarks = onOpenBenchmarks,
        onDismissReminder = viewModel::dismissReminder,
        onOpenLibrary = onOpenLibrary,
        onImportPlan = onImportPlan,
        onOpenSettings = onOpenSettings,
        bottomBar = bottomBar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenToday: () -> Unit,
    onOpenDailyNote: () -> Unit,
    onOpenBenchmarks: () -> Unit,
    onDismissReminder: () -> Unit,
    onOpenLibrary: () -> Unit,
    onImportPlan: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text("Home", style = MaterialTheme.typography.titleMedium) },
                // Profile arrives with phase 7B and is inert until then; Settings holds Import / export.
                actions = {
                    IconButton(onClick = {}, modifier = Modifier.semantics { contentDescription = "Profile" }) {
                        Icon(painterResource(R.drawable.ic_menu_profile), contentDescription = null)
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.semantics { contentDescription = "Settings" }) {
                        Icon(painterResource(R.drawable.ic_menu_settings), contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { TodayCard(state.today, state.summary, onOpenToday) }
            // Only when nothing is left to do: importing a plan is then the likely next step.
            // Its permanent home is Settings → Import / export.
            if (!state.hasUpcomingPlan) {
                item { HomeRow("Import plan", R.drawable.ic_menu_import_export, onImportPlan) }
            }
            item {
                HomeRow(
                    label = "Daily note",
                    icon = R.drawable.ic_home_note,
                    onClick = onOpenDailyNote,
                    detail = state.dailyNote?.let { summary ->
                        { DiarySummaryText(summary) }
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier.padding(top = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeTile(
                        label = "Library",
                        icon = R.drawable.ic_nav_library,
                        detail = null,
                        onClick = onOpenLibrary,
                        modifier = Modifier.weight(1f),
                    )
                    // The benchmark due a test rides inside its own destination, not beside it.
                    HomeTile(
                        label = "Benchmarks",
                        icon = R.drawable.ic_menu_benchmarks,
                        detail = state.reminder?.let { "${it.name} · ${it.ageText(state.today)}" },
                        onClick = onOpenBenchmarks,
                        onHideDetail = onDismissReminder.takeIf { state.reminder != null },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Today in four lines: the date, how much is done, a bar of it, and what is left in time when that
 * can honestly be said. A tap opens the calendar on today.
 */
@Composable
private fun TodayCard(today: LocalDate, summary: TodaySummary?, onClick: () -> Unit) {
    val (doneColor, _) = doneColors()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Today · ${WeekMath.dayLabel(today)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // The whole card opens the calendar; the chevron says so.
                Chevron()
            }
            if (summary == null) return@Column
            val progress = when {
                summary.nothingPlanned -> "Nothing planned"
                else -> "${summary.completed} / ${summary.target} completed"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = progress,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (summary.skipped > 0) {
                    Text(
                        text = "${summary.skipped} skipped",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!summary.nothingPlanned) {
                ProgressSegments(summary.completed, summary.target, if (summary.allDone) doneColor else progressColor())
            }
            if (!summary.allDone) {
                summary.remainingSeconds?.let {
                    Text(
                        text = "≈ ${DashboardStats.hours(it)} remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One block per exercise while they fit; a plain bar once there are too many to tell apart. */
@Composable
private fun ProgressSegments(done: Int, target: Int, color: androidx.compose.ui.graphics.Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    if (target > 12) {
        LinearProgressIndicator(
            progress = { done.toFloat() / target },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = track,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(target) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index < done) color else track),
            )
        }
    }
}

@Composable
private fun Chevron() {
    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A compact row that goes somewhere: icon, label, chevron, and optionally one quiet line. */
@Composable
private fun HomeRow(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    detail: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                detail?.invoke()
            }
            Chevron()
        }
    }
}

/**
 * One of the low tiles at the bottom: icon and label, and optionally one quiet line under them.
 * When [onHideDetail] is given, a long press offers to hide that line for a month.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTile(
    label: String,
    @DrawableRes icon: Int,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onHideDetail: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onHideDetail?.let {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        }
                    },
                    onLongClickLabel = if (onHideDetail != null) "Hide the reminder" else null,
                ),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(label, style = MaterialTheme.typography.titleSmall)
                }
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (onHideDetail != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Hide reminder for a month") },
                    onClick = {
                        menuOpen = false
                        onHideDetail()
                    },
                )
            }
        }
    }
}
