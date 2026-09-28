package com.yokodake.melete.ui.timer

import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.layout.BoxWithConstraints
import com.yokodake.melete.ui.theme.accentButtonColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material3.Switch
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
        // The form and the countdown want opposite layouts. A countdown is one big thing and
        // belongs in the middle of the screen; a form is a list of rows and belongs anchored, so
        // that changing what kind of timer you are building does not slide the whole page about.
        if (state.state is TimerState.Idle) {
            IdleControls(
                state = state,
                contentPadding = padding,
                onMode = viewModel::setMode,
                onWorkMinutes = viewModel::setWorkMinutes,
                onWorkSeconds = viewModel::setWorkSeconds,
                onRestMinutes = viewModel::setRestMinutes,
                onRestSeconds = viewModel::setRestSeconds,
                onSets = viewModel::setSets,
                onUnilateral = viewModel::setUnilateral,
                onSideSwitch = viewModel::setSideSwitch,
                onRepeaterReps = viewModel::setRepeaterReps,
                onRepeaterWork = viewModel::setRepeaterWork,
                onRepeaterRest = viewModel::setRepeaterRest,
                onIntervalReps = viewModel::setIntervalReps,
                onIntervalRestMinutes = viewModel::setIntervalRestMinutes,
                onIntervalRestSeconds = viewModel::setIntervalRestSeconds,
                onStart = ::startWithNotifications,
            )
            return@Scaffold
        }
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
                    onDismiss = viewModel::dismiss,
                )

                // Handled above: the form is laid out differently from the countdown.
                TimerState.Idle -> Unit
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
        CueItem("Quarter marks", state.cues.quarterCues) {
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
        previousRestarts = state.previousRestarts,
        onPrevious = onPrevious,
        onPrimary = if (state.isRunning) onPause else onResume,
        onNext = onNext,
    )
    OutlinedButton(onClick = onCancel) { Text("Stop timer") }
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
    /** Said for the previous button: whether it restarts this interval or goes back one. */
    previousRestarts: Boolean,
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
                .semantics {
                    contentDescription =
                        if (previousRestarts) "Restart this interval" else "Previous interval"
                },
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
    // One attempt of a paced set is simply "go": which one it is is on the status line.
    val attempt = state.state.currentStep?.let { it.untimed && it.repIndex != null } == true
    Text(
        text = if (attempt) {
            "ALLEZ !"
        } else {
            state.state.activeProgram?.workLabel(unknownReps = "ALLEZ !").orEmpty()
        },
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 72.dp),
    )
    StatusLine(state)
    Transport(
        playing = false,
        reps = true,
        // Nothing is counting in a set of reps, so going back is the only thing it can mean.
        previousRestarts = false,
        onPrevious = onPrevious,
        onPrimary = onNext,
        onNext = onNext,
    )
    OutlinedButton(onClick = onCancel) { Text("Stop timer") }
}

@Composable
private fun FinishedCard(
    state: TimerState.Finished,
    onDismiss: () -> Unit,
    onLog: (String) -> Unit,
    onReview: (String) -> Unit = {},
) {
    // On the screen itself, like the running timer, rather than boxed: the end of a countdown is
    // the same screen reaching its last state, not a message laid over it.
    Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Timer finished",
            style = MaterialTheme.typography.headlineSmall,
        )
        state.program.label?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.program.circuitInstanceId != null || state.program.occurrenceId != null) {
            Text(
                text = "Review and save your log.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
            // Offered only when the countdown came from planned work; a timer built on this
            // screen has nothing to open. A circuit opens its review, which is where all of
            // its exercises are confirmed at once.
            val circuitId = state.program.circuitInstanceId
            when {
                circuitId != null -> Button(onClick = { onReview(circuitId) }, colors = accentButtonColors()) { Text("Log circuit") }
                state.program.occurrenceId != null ->
                    Button(onClick = { onLog(state.program.occurrenceId) }, colors = accentButtonColors()) { Text("Log exercise") }
            }
        }
    }
}

@Composable
private fun InterruptedCard(onDismiss: () -> Unit) {
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
                text = "Your phone restarted. Start a new timer to continue.",
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
    contentPadding: PaddingValues,
    onMode: (TimerCreateMode) -> Unit,
    onWorkMinutes: (String) -> Unit,
    onWorkSeconds: (String) -> Unit,
    onRestMinutes: (String) -> Unit,
    onRestSeconds: (String) -> Unit,
    onSets: (String) -> Unit,
    onUnilateral: (Boolean) -> Unit,
    onSideSwitch: (String) -> Unit,
    onRepeaterReps: (String) -> Unit,
    onRepeaterWork: (String) -> Unit,
    onRepeaterRest: (String) -> Unit,
    onStart: () -> Unit,
    onIntervalReps: (String) -> Unit = {},
    onIntervalRestMinutes: (String) -> Unit = {},
    onIntervalRestSeconds: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp),
    ) {
        // The form, then the kind of timer under it, then Start, sitting a little below the
        // middle: somewhat more of the slack goes above than below. Scrolls only if a small screen (or the keyboard) cannot
        // fit it, and then still from the bottom up. Every row has the same label gutter, so
        // the fields keep one left edge whichever kind is selected.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val available = maxHeight
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = available),
                    // Between centred and low: a little more space above than below.
                    verticalArrangement = Arrangement.spacedBy(4.dp, BiasAlignment.Vertical(0.4f)),
                ) {
                    // Intervals has one more row than the others. Its height is held here, above
                    // the form where it reads as part of the space, so switching kinds moves
                    // nothing: every row, the tabs and Start keep their places.
                    if (state.mode != TimerCreateMode.INTERVALS) Spacer(Modifier.height(FieldRowHeight))
                    FieldRow("Work") {
                        when (state.mode) {
                            TimerCreateMode.TIMED -> {
                                NumberField("m", state.work.minutes, onWorkMinutes, FieldWidth)
                                NumberField("s", state.work.seconds, onWorkSeconds, FieldWidth)
                            }

                            // Untimed by nature, so the row says why rather than standing empty.
                            TimerCreateMode.REPS -> Text(
                                text = "At your pace.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )

                            // One set is a series of pulses. "Off" falls between them; the rest below is
                            // still the rest between *sets*, which is a different and much longer thing.
                            TimerCreateMode.REPEATERS -> {
                                NumberField("reps", state.repeaterRepsText, onRepeaterReps, FieldWidth)
                                NumberField("on (s)", state.repeaterWorkText, onRepeaterWork, FieldWidth)
                                NumberField("off (s)", state.repeaterRestText, onRepeaterRest, FieldWidth)
                            }

                            // Attempts at your pace; the timer waits for each one.
                            TimerCreateMode.INTERVALS ->
                                NumberField("reps", state.intervalRepsText, onIntervalReps, FieldWidth)
                        }
                    }

                    // The rest between attempts, inside a set; the rest below is still between sets.
                    if (state.mode == TimerCreateMode.INTERVALS) {
                        FieldRow("Rep rest") {
                            NumberField("m", state.intervalRest.minutes, onIntervalRestMinutes, FieldWidth)
                            NumberField("s", state.intervalRest.seconds, onIntervalRestSeconds, FieldWidth)
                        }
                    }

                    FieldRow(if (state.mode == TimerCreateMode.INTERVALS) "Set Rest" else "Rest") {
                        NumberField("m", state.rest.minutes, onRestMinutes, FieldWidth)
                        NumberField("s", state.rest.seconds, onRestSeconds, FieldWidth)
                    }

                    FieldRow(if (state.unilateral) "Sets per side" else "Sets") {
                        NumberField(null, state.setsText, onSets, FieldWidth)
                        if (state.unilateral) {
                            NumberField("Switch (s)", state.sideSwitchText, onSideSwitch, FieldWidth)
                        }
                    }

                    // One set covers both sides, left then right, so the set count does not double. A row
                    // like the others: the label in the gutter, the switch where the fields start.
                    FieldRow("Unilateral") {
                        Switch(checked = state.unilateral, onCheckedChange = onUnilateral)
                    }

                    // What kind of timer this is, 24 dp under the form it shapes.
                    Spacer(Modifier.height(20.dp))
                    ModeTabs(state.mode, onMode)
                }
            }
        }

        // Bottom, and it stays there: what Start will actually do, and Start.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // One quiet line, always there, so it cannot push the button about.
            Text(
                text = summarise(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = onStart,
                enabled = state.canStart,
                colors = accentButtonColors(),
                modifier = Modifier.fillMaxWidth(0.7f),
            ) {
                Text("Start")
            }
        }
    }
}

/** The height of one row of the timer form, which a missing row's placeholder also takes. */
private val FieldRowHeight = 64.dp

/** The width of one number in the timer form: room for "999", not the whole screen. */
private val FieldWidth = Modifier.width(80.dp)

/**
 * The kinds of timer as one compact row of tabs, each label on one line. Plain surfaces rather
 * than chips, so four fit across a phone without "Repeaters" breaking in two.
 */
@Composable
private fun ModeTabs(selected: TimerCreateMode, onMode: (TimerCreateMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
    ) {
        TimerCreateMode.entries.forEachIndexed { index, mode ->
            val on = mode == selected
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .selectable(selected = on, onClick = { onMode(mode) }, role = Role.Tab)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * One line of the form: a fixed label gutter, then the fields.
 *
 * The gutter is what keeps every row on one left edge, and the fixed height is what stops the
 * page twitching when a row changes from two fields to three — or to a sentence.
 */
@Composable
private fun FieldRow(
    label: String?,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FieldRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            // Wide enough for the longest label, "Unilateral", so every field starts on one edge.
            modifier = Modifier.width(88.dp),
        )
        content()
    }
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
            label = "m",
            value = value.minutes,
            onValueChange = onMinutes,
            modifier = Modifier.width(96.dp),
        )
        Text(":", style = MaterialTheme.typography.titleLarge)
        NumberField(
            label = "s",
            value = value.seconds,
            onValueChange = onSeconds,
            modifier = Modifier.width(96.dp),
        )
    }
}

/** The whole program in one line, so what Start will do is never a surprise. */
private fun summarise(state: TimerUiState): String {
    val program = state.draftProgram ?: return when {
        state.sets < 1 -> "How many sets?"
        state.mode == TimerCreateMode.REPEATERS ->
            "How many reps, and how long is each?"
        state.mode == TimerCreateMode.INTERVALS ->
            "At least 2 reps, and a rest between them?"
        else -> "How long is a set?"
    }

    // The fields already say the sets and their shape; what they do not say is how long it all
    // takes, which is only honest when every interval has a length. With untimed sets, the rest
    // is the part that can be added up; with no rest at all, there is nothing to count.
    val rest = program.steps.filter { it.phase.isRest }.sumOf { it.seconds }
    return when {
        program.isFullyTimed -> "${PrescriptionSummary.duration(program.estimatedSeconds())} total"
        rest > 0 -> "${PrescriptionSummary.duration(rest)} rest in total"
        else -> "Goes at your pace"
    }
}

/**
 * What one interval of this program will sound. A setting that cannot apply to the length picked
 * says so instead of quietly doing nothing.
 */
private fun cueExplanation(state: TimerUiState): String {
    val notes = mutableListOf<String>()
    if (state.cues.thirtySecondWarning && !state.thirtySecondWarningApplies) {
        notes += "too short for a 30 s warning"
    }
    if (state.cues.quarterCues && !state.quarterCuesApply) {
        notes += if (state.shownPhase != TimerPhase.WORK) {
            "quarter cues are for work intervals"
        } else {
            "quarter cues start at one minute"
        }
    }
    val count = state.plannedCues.size
    val sounding = "$count cue${if (count == 1) "" else "s"} " +
        if (state.cuePreviewIsLive) "in this interval" else "per interval"
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
    WorkKind.REPS -> workReps?.let { "$it ${if (it == 1) "rep" else "reps"}" } ?: unknownReps
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
        next.untimed && next.repIndex != null ->
            "rep ${next.repIndex + 1} of ${entry?.repsPerSetOnTimer ?: "?"}"
        next.untimed -> entry?.workReps?.let { "$it ${if (it == 1) "rep" else "reps"}" } ?: "a set"
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
