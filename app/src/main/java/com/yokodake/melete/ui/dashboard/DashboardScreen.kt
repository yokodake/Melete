package com.yokodake.melete.ui.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The Dashboard tab, in its place in the bar ahead of phase 6A, which gives it totals, the
 * category breakdown and the weekly and monthly bars. Empty until then: no numbers are better than
 * made-up ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(bottomBar: @Composable () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text("Dashboard", style = MaterialTheme.typography.titleMedium) },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding).fillMaxSize())
    }
}
