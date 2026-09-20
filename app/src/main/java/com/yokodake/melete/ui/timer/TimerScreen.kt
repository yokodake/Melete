package com.yokodake.melete.ui.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.data.timer.CueSettings
import com.yokodake.melete.data.timer.TimerPhase
import com.yokodake.melete.data.timer.TimerState

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text("Timer", style = MaterialTheme.typography.titleMedium) },
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
                    onDismiss = viewModel::dismiss,
                )

                is TimerState.Interrupted -> InterruptedCard(
                    phase = current.phase,
                    onDismiss = viewModel::dismiss,
                )

                TimerState.Idle -> IdleControls(
                    state = state,
                    onPhase = viewModel::setDraftPhase,
                    onAdjust = viewModel::adjustDraftSeconds,
                    onCues = viewModel::setCueSettings,
                    onStart = ::startWithNotifications,
                )
            }
        }
    }
}

@Composable
private fun ActiveCountdown(
    state: TimerUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = state.draftPhaseLabel(),
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
private fun FinishedCard(phase: TimerPhase, onDismiss: () -> Unit) {
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
    onAdjust: (Int) -> Unit,
    onCues: (CueSettings) -> Unit,
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
    Text(
        text = formatClock(state.draftSeconds * 1000L),
        style = MaterialTheme.typography.displayMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-60, -15, 15, 60).forEach { delta ->
            OutlinedButton(onClick = { onAdjust(delta) }) {
                Text(if (delta > 0) "+$delta" else "$delta")
            }
        }
    }
    CueSettingsControls(state = state, onCues = onCues)
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth(0.7f)) {
        Text("Start")
    }
    Text(
        text = "The countdown keeps running with the screen off. It never records a set.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * The cue families, and what this particular countdown will actually sound. A setting that cannot
 * apply to the length you picked says so instead of quietly doing nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CueSettingsControls(state: TimerUiState, onCues: (CueSettings) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Cues", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.cues.thirtySecondWarning,
                onClick = {
                    onCues(
                        state.cues.copy(thirtySecondWarning = !state.cues.thirtySecondWarning)
                    )
                },
                label = { Text("30s left") },
            )
            FilterChip(
                selected = state.cues.finalCountdown,
                onClick = { onCues(state.cues.copy(finalCountdown = !state.cues.finalCountdown)) },
                label = { Text("3–2–1") },
            )
            FilterChip(
                selected = state.cues.quarterCues,
                onClick = { onCues(state.cues.copy(quarterCues = !state.cues.quarterCues)) },
                label = { Text("¼ ½ ¾") },
            )
        }
        Text(
            text = cueExplanation(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

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

private fun TimerUiState.draftPhaseLabel(): String = when (state) {
    is TimerState.Running -> if (state.phase == TimerPhase.WORK) "Work" else "Rest"
    is TimerState.Paused -> if (state.phase == TimerPhase.WORK) "Work" else "Rest"
    else -> ""
}

internal fun formatClock(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
