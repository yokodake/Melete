package com.yokodake.melete.ui.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.R
import com.yokodake.melete.data.timer.CueSettings
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.data.timer.WorkKind
import com.yokodake.melete.data.timer.title
import com.yokodake.melete.ui.components.NumberField
import com.yokodake.melete.ui.theme.MeleteTheme
import com.yokodake.melete.ui.theme.aboutToStartColor
import com.yokodake.melete.ui.theme.workingColor
import com.yokodake.melete.ui.week.PrescriptionSummary

/** How long before the end of a rest the screen starts warning that work is about to begin. */
private const val ABOUT_TO_START_MS = 5_000L

@Composable
fun TimerRoute(
    onLog: (String) -> Unit = {},
    onReviewCircuit: (String) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: TimerViewModel = viewModel(factory = TimerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TimerScreen(
        state = state,
        viewModel = viewModel,
        onLog = onLog,
        onReviewCircuit = onReviewCircuit,
        bottomBar = bottomBar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    state: TimerUiState,
    viewModel: TimerViewModel,
    onLog: (String) -> Unit = {},
    onReviewCircuit: (String) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Starting is not blocked on the answer; the countdown runs either way. */ }

    fun startWithNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.start()
    }

    val background by animateColorAsState(
        targetValue = backgroundFor(state),
        animationSpec = tween(durationMillis = 350),
        label = "timer-background",
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = background,
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                // The exercise name lives in the body, large, so the bar does not repeat it.
                title = { Text("Timer", style = MaterialTheme.typography.titleMedium) },
                // The cues live behind the overflow because they are settings, not controls: they
                // are tuned once in a while and then want to be out of the way of the clock.
                actions = { CueMenu(state = state, onCues = viewModel::setCueSettings) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val current = state.state) {
                is TimerState.Running, is TimerState.Paused -> ActiveCountdown(
                    state = state,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onCancel = viewModel::cancel,
                )

                is TimerState.AwaitingSet -> AwaitingSet(
                    state = state,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onCancel = viewModel::cancel,
                )

                is TimerState.Finished -> FinishedCard(
                    state = current,
                    onDismiss = viewModel::dismiss,
                    onLog = { occurrenceId ->
                        // Dismiss first: coming back to a timer still showing a run it has
                        // already been thanked for is confusing.
                        viewModel.dismiss()
                        onLog(occurrenceId)
                    },
                    onReview = { circuitId ->
                        viewModel.dismiss()
                        onReviewCircuit(circuitId)
                    },
                )

                is TimerState.Interrupted -> InterruptedCard(
                    phase = current.phase,
                    onDismiss = viewModel::dismiss,
                )

                TimerState.Idle -> IdleControls(
                    state = state,
                    onMode = viewModel::setMode,
                    onWorkMinutes = viewModel::setWorkMinutes,
                    onWorkSeconds = viewModel::setWorkSeconds,
                    onRestMinutes = viewModel::setRestMinutes,
                    onRestSeconds = viewModel::setRestSeconds,
                    onSets = viewModel::setSets,
                    onStart = ::startWithNotifications,
                )
            }
        }
    }
}

/**
 * The colour of the whole screen, which is the part of the timer that can be read from across a
 * room with a bar on your back: green while the work is happening, plain while resting, and amber
 * for the last few seconds of a rest so the next set is never a surprise.
 *
 * A paused countdown deliberately drops back to plain — nothing is happening, and a green screen
 * that is not counting would be a lie. A set of reps is green, because that is work in progress
 * even though nothing is counting it.
 */
@Composable
private fun backgroundFor(state: TimerUiState): Color {
    val plain = MaterialTheme.colorScheme.surface
    return when (val current = state.state) {
        is TimerState.AwaitingSet -> workingColor()
        is TimerState.Running -> when {
            current.phase == TimerPhase.WORK -> workingColor()
            // A preparation is the same moment as the tail of a rest, so it looks the same.
            current.phase == TimerPhase.PREPARE -> aboutToStartColor()
            state.remainingMs <= ABOUT_TO_START_MS -> aboutToStartColor()
            else -> plain
        }

        else -> plain
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CueMenu(state: TimerUiState, onCues: (CueSettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        modifier = Modifier.semantics { contentDescription = "Cues" },
    ) {
        Text("⋮", style = MaterialTheme.typography.titleLarge)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        // The menu stays open across a toggle: these are three related switches, and a run that is
        // already counting takes each change immediately, so closing after every tap would fight
        // the one moment they are most likely to be used.
        CueItem("30 s left", state.cues.thirtySecondWarning) {
            onCues(state.cues.copy(thirtySecondWarning = it))
        }
        CueItem("3 – 2 – 1", state.cues.finalCountdown) {
            onCues(state.cues.copy(finalCountdown = it))
        }
        CueItem("¼ ½ ¾ of a work set", state.cues.quarterCues) {
            onCues(state.cues.copy(quarterCues = it))
        }
        Text(
            text = cueExplanation(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(220.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CueItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { Checkbox(checked = checked, onCheckedChange = onChange) },
        onClick = { onChange(!checked) },
    )
}

/**
 * What you are doing, and where you are in it.
 *
 * The exercise name leads and is the most prominent thing on the screen, because it is what
 * changes from set to set once circuits and supersets exist: it has to be the part you read first
 * and the part that is easy to make say something else. A countdown built on this screen belongs
 * to no exercise and shows nothing here.
 */
@Composable
private fun ProgramHeading(state: TimerUiState, phaseLabel: String? = null) {
    state.label?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
    state.setProgress?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    phaseLabel?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One line that is always present, whatever the timer is doing.
 *
 * Paused and what-comes-next share the row on purpose: a line that appears and disappears shifts
 * everything below it, and a clock you glance at mid-set should not rearrange itself under you.
 */
@Composable
private fun StatusLine(state: TimerUiState) {
    Text(
        text = when {
            state.isPaused -> "Paused"
            else -> nextUp(state)?.let { "Next: $it" } ?: "Last one"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ActiveCountdown(
    state: TimerUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
) {
    ProgramHeading(
        state = state,
        phaseLabel = state.shownPhase.title(),
    )
    Box {
        CircularProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.size(220.dp),
            strokeWidth = 10.dp,
        )
        Text(
            text = formatClock(state.remainingMs),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.align(Alignment.Center),
        )
    }
    StatusLine(state)
    Transport(
        playing = state.isRunning,
        reps = false,
        onPrevious = onPrevious,
        onPrimary = if (state.isRunning) onPause else onResume,
        onNext = onNext,
    )
    OutlinedButton(onClick = onCancel) { Text("Cancel") }
}

/**
 * Previous, primary, next — the controls of something that plays a sequence, because that is what
 * a program is. The primary slot is play/pause for a countdown and a tick for a set of reps, at
 * the same size either way so that nothing moves when the program changes kind.
 *
 * Cancel sits apart and below: it ends the whole thing, and has no business being a mis-tap away
 * from the button pressed between every set.
 */
@Composable
private fun Transport(
    playing: Boolean,
    reps: Boolean,
    onPrevious: () -> Unit,
    onPrimary: (() -> Unit)?,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = "Previous interval" },
        ) {
            Icon(painterResource(R.drawable.ic_timer_previous), contentDescription = null)
        }
        if (onPrimary != null) {
            FilledIconButton(
                onClick = onPrimary,
                modifier = Modifier
                    .size(72.dp)
                    .semantics {
                        contentDescription = when {
                            reps -> "Set done"
                            playing -> "Pause"
                            else -> "Resume"
                        }
                    },
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(
                    painter = painterResource(
                        when {
                            reps -> R.drawable.ic_timer_done
                            playing -> R.drawable.ic_timer_pause
                            else -> R.drawable.ic_timer_play
                        }
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        IconButton(
            onClick = onNext,
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = "Next interval" },
        ) {
            Icon(painterResource(R.drawable.ic_timer_next), contentDescription = null)
        }
    }
}

/**
 * Reps cannot be counted by a clock, so the program stops here and waits.
 *
 * The tick is the primary control, in the same slot and at the same size as play/pause, so the row
 * does not change shape between a timed set and a counted one. It calls the same thing as next,
 * deliberately: from a waiting set those two land on exactly the same interval, and whether the
 * set really happened is a question for the log, not for the timer, which records nothing either
 * way.
 */
@Composable
private fun AwaitingSet(
    state: TimerUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
) {
    ProgramHeading(state)
    Text(
        text = state.state.activeProgram?.workLabel(unknownReps = "ALLEZ !").orEmpty(),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 72.dp),
    )
    StatusLine(state)
    Transport(
        playing = false,
        reps = true,
        onPrevious = onPrevious,
        onPrimary = onNext,
        onNext = onNext,
    )
    OutlinedButton(onClick = onCancel) { Text("Cancel") }
}

@Composable
private fun FinishedCard(
    state: TimerState.Finished,
    onDismiss: () -> Unit,
    onLog: (String) -> Unit,
    onReview: (String) -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (state.program.sets > 1) {
                    val unit = if (state.program.isCircuit) "rounds" else "sets"
                    "${state.setsCompleted} $unit done"
                } else if (state.phase == TimerPhase.WORK) {
                    "Work finished"
                } else {
                    "Rest finished"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            state.program.label?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "Nothing was recorded here. Only you can say the work happened.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss) { Text("OK") }
                // Offered only when the countdown came from planned work; a timer built on this
                // screen has nothing to open. A circuit opens its review, which is where all of
                // its exercises are confirmed at once.
                val circuitId = state.program.circuitInstanceId
                when {
                    circuitId != null -> Button(onClick = { onReview(circuitId) }) { Text("Review") }
                    state.program.occurrenceId != null ->
                        Button(onClick = { onLog(state.program.occurrenceId) }) { Text("Log") }
                }
            }
        }
    }
}

@Composable
private fun InterruptedCard(phase: TimerPhase, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Timer interrupted",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "The phone restarted while a ${phase.name.lowercase()} countdown was " +
                    "running, so its remaining time is unknown. Start it again if you need it.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

/**
 * Building a timer: what a set is made of, how long the rest is, and how many times round. Those
 * three numbers are what training actually is, and the prescription already knows all of them —
 * this screen is for the times you are counting something that is not in the plan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdleControls(
    state: TimerUiState,
    onMode: (WorkKind) -> Unit,
    onWorkMinutes: (String) -> Unit,
    onWorkSeconds: (String) -> Unit,
    onRestMinutes: (String) -> Unit,
    onRestSeconds: (String) -> Unit,
    onSets: (String) -> Unit,
    onStart: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.mode == WorkKind.TIMED,
            onClick = { onMode(WorkKind.TIMED) },
            label = { Text("Timed sets") },
        )
        FilterChip(
            selected = state.mode == WorkKind.REPS,
            onClick = { onMode(WorkKind.REPS) },
            label = { Text("Reps") },
        )
    }

    if (state.mode == WorkKind.TIMED) {
        DurationRow("Set", state.work, onWorkMinutes, onWorkSeconds)
    } else {
        Text(
            text = "A set of reps is not timed. The rest starts when you say the set is done.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    DurationRow("Rest", state.rest, onRestMinutes, onRestSeconds)

    NumberField(
        label = "Sets",
        value = state.setsText,
        onValueChange = onSets,
        modifier = Modifier.width(140.dp),
    )

    Text(
        text = summarise(state),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onStart,
        enabled = state.canStart,
        modifier = Modifier.fillMaxWidth(0.7f),
    ) {
        Text("Start")
    }
    Text(
        text = cueExplanation(state),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "The countdown keeps running with the screen off. It never records a set.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

/** Typed, not nudged: saying "4:30" beats stepping to it fifteen seconds at a time. */
@Composable
private fun DurationRow(
    label: String,
    value: DurationDraft,
    onMinutes: (String) -> Unit,
    onSeconds: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(48.dp),
        )
        NumberField(
            label = "min",
            value = value.minutes,
            onValueChange = onMinutes,
            modifier = Modifier.width(96.dp),
        )
        Text(":", style = MaterialTheme.typography.titleLarge)
        NumberField(
            label = "sec",
            value = value.seconds,
            onValueChange = onSeconds,
            modifier = Modifier.width(96.dp),
        )
    }
}

/** The whole program in one line, so what Start will do is never a surprise. */
private fun summarise(state: TimerUiState): String {
    val program = state.draftProgram ?: return "—"
    return buildString {
        append(program.sets)
        append(" × ")
        append(
            if (program.work == WorkKind.REPS) {
                "reps"
            } else {
                PrescriptionSummary.duration(program.workSeconds)
            }
        )
        if (program.restSeconds > 0 && program.sets > 1) {
            append(", ")
            append(PrescriptionSummary.duration(program.restSeconds))
            append(" rest")
        }
        program.totalSeconds?.let { append("  ·  ${PrescriptionSummary.duration(it)} total") }
    }
}

/**
 * What one interval of this program will sound. A setting that cannot apply to the length picked
 * says so instead of quietly doing nothing.
 */
private fun cueExplanation(state: TimerUiState): String {
    val notes = mutableListOf<String>()
    if (state.cues.thirtySecondWarning && !state.thirtySecondWarningApplies) {
        notes += "too short for a 30s warning"
    }
    if (state.cues.quarterCues && !state.quarterCuesApply) {
        notes += if (state.shownPhase != TimerPhase.WORK) {
            "quarter cues are for work intervals"
        } else {
            "quarter cues start at one minute"
        }
    }
    val count = state.plannedCues.size
    val sounding = "$count cue${if (count == 1) "" else "s"} per interval"
    return if (notes.isEmpty()) sounding else "$sounding — ${notes.joinToString(", ")}"
}

@Composable
private fun Box(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) =
    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center, content = content)

internal fun formatClock(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * One set of this program, in words.
 *
 * A `when` over [WorkKind] with no `else`: used as an expression it must be exhaustive, so the
 * compiler refuses to build if a kind goes unhandled. That is the whole reason work is an enum
 * rather than an `isTimed` boolean — add a fourth kind and every place that has to care becomes a
 * compile error instead of a silently wrong string.
 */
private fun TimerProgram.workLabel(unknownReps: String = "a set"): String = when (work) {
    // A rep count is optional even for a reps program: one built on this screen has no number.
    WorkKind.REPS -> workReps?.let { "$it reps" } ?: unknownReps
    WorkKind.TIMED -> PrescriptionSummary.duration(workSeconds)
    WorkKind.NONE -> PrescriptionSummary.duration(restSeconds)
}

/**
 * What follows the interval on screen, or null when this is the last one.
 *
 * A preparation is a lead-in to the step it points at rather than a step of its own, so from one
 * the answer is that step itself — otherwise the screen would say the rest is next while the set
 * has not happened yet.
 */
private fun nextUp(state: TimerUiState): String? {
    val program = state.state.activeProgram ?: return null
    val index = state.state.activeStepIndex ?: return null
    val next = if (state.shownPhase == TimerPhase.PREPARE) {
        program.steps.getOrNull(index)
    } else {
        program.stepAfter(index)
    } ?: return null
    val entry = program.entries.getOrNull(next.entryIndex)
    return when {
        next.untimed -> entry?.workReps?.let { "$it reps" } ?: "a set"
        next.phase == TimerPhase.WORK -> buildString {
            append(PrescriptionSummary.duration(next.seconds))
            // In a circuit the interesting thing about what comes next is which exercise it is.
            if (program.isCircuit) entry?.label?.let { append(" · ").append(it) }
        }

        else -> "${PrescriptionSummary.duration(next.seconds)} ${next.phase.restNoun()}"
    }
}

/** What to call a gap of this kind in a one-line "next up". */
private fun TimerPhase.restNoun(): String = when (this) {
    TimerPhase.SWITCH -> "to change sides"
    TimerPhase.REP_REST -> "between reps"
    TimerPhase.TRANSITION -> "to move on"
    TimerPhase.ROUND_REST -> "between rounds"
    else -> "rest"
}

// ---------------------------------------------------------------- previews

/**
 * The screen's body, minus the scaffold, on the background the real screen would be wearing.
 *
 * The colour is the thing worth previewing: it is what the timer says from across a room, and
 * otherwise you have to run a countdown to the right second to see it.
 */
@Composable
private fun PreviewFrame(state: TimerUiState, content: @Composable ColumnScope.() -> Unit) {
    MeleteTheme {
        Surface(color = backgroundFor(state)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                content = content,
            )
        }
    }
}

private val previewHangs = TimerProgram(
    sets = 5,
    work = WorkKind.TIMED,
    workSeconds = 10,
    restSeconds = 180,
    label = "Max hangs 20 mm",
)

private fun previewRunning(
    phase: TimerPhase,
    totalMs: Long,
    remainingMs: Long,
    stepIndex: Int = 2,
    program: TimerProgram = previewHangs,
) = TimerUiState(
    state = TimerState.Running(
        runId = "preview",
        phase = phase,
        totalMs = totalMs,
        deadlineElapsedMs = 0,
        plan = emptyList(),
        program = program,
        stepIndex = stepIndex,
    ),
    // Plain fields on the ui state, so a preview needs no clock ticking behind it.
    remainingMs = remainingMs,
    totalMs = totalMs,
)

@Preview(name = "1 · Get ready", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun PreparePreview() {
    val state = previewRunning(TimerPhase.PREPARE, totalMs = 5_000, remainingMs = 3_000)
    PreviewFrame(state) {
        ActiveCountdown(state, onPause = {}, onResume = {}, onPrevious = {}, onNext = {}, onCancel = {})
    }
}

@Preview(name = "2 · Work", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun WorkPreview() {
    val state = previewRunning(TimerPhase.WORK, totalMs = 10_000, remainingMs = 6_000)
    PreviewFrame(state) {
        ActiveCountdown(state, onPause = {}, onResume = {}, onPrevious = {}, onNext = {}, onCancel = {})
    }
}

@Preview(name = "3 · Rest", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun RestPreview() {
    val state = previewRunning(TimerPhase.REST, totalMs = 180_000, remainingMs = 95_000)
    PreviewFrame(state) {
        ActiveCountdown(state, onPause = {}, onResume = {}, onPrevious = {}, onNext = {}, onCancel = {})
    }
}

/** The last seconds of a rest: the set is about to start, and the screen says so. */
@Preview(name = "4 · Rest, about to end", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun RestEndingPreview() {
    val state = previewRunning(TimerPhase.REST, totalMs = 180_000, remainingMs = 3_000)
    PreviewFrame(state) {
        ActiveCountdown(state, onPause = {}, onResume = {}, onPrevious = {}, onNext = {}, onCancel = {})
    }
}

/** Paused deliberately drops back to plain: a green screen that is not counting would be a lie. */
@Preview(name = "5 · Paused", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun PausedPreview() {
    val state = TimerUiState(
        state = TimerState.Paused(
            runId = "preview",
            phase = TimerPhase.WORK,
            totalMs = 10_000,
            remainingMs = 4_000,
            plan = emptyList(),
            program = previewHangs,
            stepIndex = 2,
        ),
        remainingMs = 4_000,
        totalMs = 10_000,
    )
    PreviewFrame(state) {
        ActiveCountdown(state, onPause = {}, onResume = {}, onPrevious = {}, onNext = {}, onCancel = {})
    }
}
/** Reps: counted */
@Preview(name = "8 · Reps", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun MoreRepsPreview() {
    val state = TimerUiState(
        state = TimerState.AwaitingSet(
            runId = "preview",
            program = TimerProgram(
                sets = 4,
                work = WorkKind.REPS,
                workReps = 8,
                restSeconds = 60,
                label = "Dumbbell row",
            ),
            stepIndex = 2,
        ),
    )
    PreviewFrame(state) {
        AwaitingSet(state, onPrevious = {}, onNext = {}, onCancel = {})
    }
}


@Preview(name = "6 · Finished", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun FinishedPreview() {
    PreviewFrame(TimerUiState()) {
        FinishedCard(
            onLog = {},
            state = TimerState.Finished(
                runId = "preview",
                phase = TimerPhase.WORK,
                totalMs = 10_000,
                program = previewHangs,
                setsCompleted = 5,
            ),
            onDismiss = {},
        )
    }
}
/** Reps: nothing is counting, so there is no clock and no play button — just the set. */
@Preview(name = "7 · Reps", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun AwaitingSetPreview() {
    val state = TimerUiState(
        state = TimerState.AwaitingSet(
            runId = "preview",
            program = TimerProgram(
                sets = 4,
                work = WorkKind.REPS,
                restSeconds = 60,
                label = "Dumbbell row",
            ),
            stepIndex = 2,
        ),
    )
    PreviewFrame(state) {
        AwaitingSet(state, onPrevious = {}, onNext = {}, onCancel = {})
    }
}
