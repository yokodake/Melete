package com.yokodake.melete.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The header every editor shares: back, the title, Save, and optionally a control underneath.
 *
 * One composable rather than a top bar per editor, because switching a new workout between
 * Exercise and Circuit swaps one editor for the other — and any difference in title size, Save's
 * place or the switch's position then shows up as the screen jumping under your thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    /** Shown under the title bar, inside the header, where it stays put as the form scrolls. */
    below: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = canSave) { Text("Save") }
                },
            )
            below?.let {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    it()
                }
            }
        }
    }
}
