package com.yokodake.melete.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder for the countdown built in phase 3. It deliberately shows no fake controls: a timer
 * that looks usable but does not survive a locked screen would be worse than an honest gap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerRoute(bottomBar: @Composable () -> Unit = {}) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Not built yet",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Phase 3 adds one countdown for work or rest, started from an exercise: " +
                    "pause and resume, an advance warning before it ends, and remaining time in a " +
                    "notification while the screen is locked.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
