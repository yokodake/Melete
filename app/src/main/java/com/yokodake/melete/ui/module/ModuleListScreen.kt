package com.yokodake.melete.ui.module

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.data.TrainingModule

/** The saved modules, as a list to manage. A tap edits one; a long press offers the rest. */
@Composable
fun ModuleListRoute(
    onNewModule: () -> Unit,
    onEditModule: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ModuleListViewModel = viewModel(factory = ModuleListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModuleListScreen(
        state = state,
        onEdit = onEditModule,
        onDuplicate = viewModel::duplicate,
        onAskRemove = viewModel::askToRemove,
        onConfirmRemove = viewModel::confirmRemoval,
        onCancelRemove = viewModel::cancelRemoval,
        onNewModule = onNewModule,
        onMessageShown = viewModel::consumeMessage,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleListScreen(
    state: ModuleListUiState,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onAskRemove: (String) -> Unit,
    onConfirmRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onNewModule: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text("Modules", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewModule,
                text = { Text("New module") },
                icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.modules.isEmpty() && !state.loading) {
                item {
                    Text(
                        text = "No modules yet. Create a module to plan exercises and circuits as a group.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = state.modules, key = { it.id }) { module ->
                ModuleRow(
                    module = module,
                    onClick = { onEdit(module.id) },
                    onEdit = { onEdit(module.id) },
                    onDuplicate = { onDuplicate(module.id) },
                    onRemove = { onAskRemove(module.id) },
                )
            }
        }
    }

    state.removal?.let { removal ->
        AlertDialog(
            onDismissRequest = onCancelRemove,
            title = { Text("Remove ${removal.module.name}?") },
            text = {
                Text(
                    if (removal.scheduledCopies > 0) {
                        "Remove this saved module? Modules already in your plan will stay."
                    } else {
                        "Remove this saved module?"
                    }
                )
            },
            confirmButton = { TextButton(onClick = onConfirmRemove) { Text("Remove") } },
            dismissButton = { TextButton(onClick = onCancelRemove) { Text("Cancel") } },
        )
    }
}

/**
 * One saved module: its name, what is in it, and what it is for.
 *
 * With a [secondaryAction] it is a picker row — a tap chooses it and the button beside it does the
 * one other thing. Without one, a long press offers managing it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ModuleRow(
    module: TrainingModule,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onRemove: () -> Unit = {},
    secondaryAction: Pair<() -> Unit, String>? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (secondaryAction == null) menuOpen = true },
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    secondaryAction?.let { (action, label) ->
                        TextButton(onClick = action) { Text(label) }
                    }
                }
                Text(
                    text = module.entries.joinToString(" · ") { it.name }.ifEmpty { "No exercises or circuits" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                module.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
            DropdownMenuItem(
                text = { Text("Duplicate") },
                onClick = { menuOpen = false; onDuplicate() },
            )
            DropdownMenuItem(text = { Text("Remove") }, onClick = { menuOpen = false; onRemove() })
        }
    }
}
