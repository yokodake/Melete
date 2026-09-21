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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.data.timer.CueSettings
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.ui.components.NumberField
import com.yokodake.melete.ui.theme.aboutToStartColor
import com.yokodake.melete.ui.theme.workingColor

/** How long before the end of a rest the screen starts warning that work is about to begin. */
private const val ABOUT_TO_START_MS = 5_000L

@Composable
fun TimerRoute(
    bottomBar: @Composable () -> Unit = {},
    viewModel: TimerViewModel = viewModel(factory = TimerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TimerScreen(state = state, viewModel = viewModel, bottomBar = bottomBar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    state: TimerUiState,
    viewModel: TimerViewModel,
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
                title = {
                    Column {
                        Text("Timer", style = MaterialTheme.typography.titleMedium)
                        state.label?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val current = state.state) {
                is TimerState.Running, is TimerState.Paused -> ActiveCountdown(
                    state = state,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onCancel = viewModel::cancel,
                )

                is TimerState.Finished -> FinishedCard(
                    phase = current.phase,
                    label = current.label,
                    onDismiss = viewModel::dismiss,
                )

                is TimerState.Interrupted -> InterruptedCard(
                    phase = current.phase,
                    onDismiss = viewModel::dismiss,
                )

                TimerState.Idle -> IdleControls(
                    state = state,
                    onPhase = viewModel::setDraftPhase,
                    onMinutes = viewModel::setDraftMinutes,
                    onSeconds = viewModel::setDraftSeconds,
                    onStart = ::startWithNotifications,
                )
            }
        }
    }
}

/**
 * The colour of the whole screen, which is the part of the timer that can be read from across a
 * room with a bar on your back: green while the work interval is running, plain while resting, and
 * amber for the last few seconds of a rest so the next set is never a surprise.
 *
 * A paused countdown deliberately drops back to plain — nothing is happening, and a green screen
 * that is not counting would be a lie.
 */
@Composable
private fun backgroundFor(state: TimerUiState): Color {
    val plain = MaterialTheme.colorScheme.surface
    val running = state.state as? TimerState.Running ?: return plain
    return when {
        running.phase == TimerPhase.WORK -> workingColor()
        state.remainingMs <= ABOUT_TO_START_MS -> aboutToStartColor()
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

@Composable
private fun ActiveCountdown(
    state: TimerUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = if (state.shownPhase == TimerPhase.WORK) "Work" else "Rest",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    if (state.isPaused) {
        Text(
            text = "Paused",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.isRunning) {
            OutlinedButton(onClick = onPause) { Text("Pause") }
        } else {
            Button(onClick = onResume) { Text("Resume") }
        }
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun FinishedCard(phase: TimerPhase, label: String?, onDismiss: () -> Unit) {
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
                text = if (phase == TimerPhase.WORK) "Work finished" else "Rest finished",
                style = MaterialTheme.typography.headlineSmall,
            )
            label?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "Nothing was recorded. Confirm a set in the logger if you did the work.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onDismiss) { Text("Done") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdleControls(
    state: TimerUiState,
    onPhase: (TimerPhase) -> Unit,
    onMinutes: (String) -> Unit,
    onSeconds: (String) -> Unit,
    onStart: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.draftPhase == TimerPhase.REST,
            onClick = { onPhase(TimerPhase.REST) },
            label = { Text("Rest") },
        )
        FilterChip(
            selected = state.draftPhase == TimerPhase.WORK,
            onClick = { onPhase(TimerPhase.WORK) },
            label = { Text("Work") },
        )
    }
    // Typed, not nudged. Stepping to four and a half minutes fifteen seconds at a time is a
    // worse way to say "4:30" than saying it.
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberField(
            label = "Minutes",
            value = state.draftMinutes,
            onValueChange = onMinutes,
            modifier = Modifier.width(120.dp),
        )
        Text(":", style = MaterialTheme.typography.headlineMedium)
        NumberField(
            label = "Seconds",
            value = state.draftSeconds,
            onValueChange = onSeconds,
            modifier = Modifier.width(120.dp),
        )
    }
    Text(
        text = formatClock(state.draftTotalSeconds * 1000L),
        style = MaterialTheme.typography.displaySmall,
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
    )
}

/**
 * What this particular countdown will actually sound. A setting that cannot apply to the length
 * picked says so instead of quietly doing nothing.
 */
private fun cueExplanation(state: TimerUiState): String {
    val notes = mutableListOf<String>()
    if (state.cues.thirtySecondWarning && !state.thirtySecondWarningApplies) {
        notes += "too short for a 30s warning"
    }
    if (state.cues.quarterCues && !state.quarterCuesApply) {
        notes += if (state.draftPhase != TimerPhase.WORK) {
            "quarter cues are for work intervals"
        } else {
            "quarter cues start at one minute"
        }
    }
    val count = state.plannedCues.size
    val sounding = "$count cue${if (count == 1) "" else "s"} this countdown"
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
