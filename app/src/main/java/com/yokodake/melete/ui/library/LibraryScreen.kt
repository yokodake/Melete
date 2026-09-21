package com.yokodake.melete.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.week.PrescriptionSummary

/**
 * The library as a tab: browse what exists. A tap opens the exercise, not a form — what it is
 * comes first, and editing it is a button on that screen.
 */
@Composable
fun LibraryRoute(
    onOpenExercise: (String) -> Unit,
    onNewExercise: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    LibraryScreen(
        title = "Library",
        subtitle = null,
        exercises = exercises,
        emptyMessage = "No exercises yet. Everything here is yours to define — " +
            "nothing is built in.",
        onRowClick = onOpenExercise,
        onNewExercise = onNewExercise,
        onBack = null,
        bottomBar = bottomBar,
    )
}

/** The library as a picker: choosing an exercise copies it into the chosen slot of the week. */
@Composable
fun LibraryPickerRoute(
    onScheduled: () -> Unit,
    onNewExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryPickerViewModel = viewModel(factory = LibraryPickerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        title = "Add to",
        subtitle = state.targetLabel,
        exercises = state.exercises,
        emptyMessage = "The library is empty. Create an exercise to get started — " +
            "nothing is built in.",
        onRowClick = { viewModel.schedule(it, onScheduled) },
        onSecondaryAction = onOpenExercise to "Open",
        onNewExercise = onNewExercise,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    title: String,
    subtitle: String?,
    exercises: List<LibraryExercise>,
    emptyMessage: String,
    onRowClick: (String) -> Unit,
    onNewExercise: () -> Unit,
    onBack: (() -> Unit)?,
    onSecondaryAction: Pair<(String) -> Unit, String>? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.semantics { contentDescription = "Back" },
                        ) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewExercise,
                text = { Text("New exercise") },
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
            if (exercises.isEmpty()) {
                item {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = exercises, key = { it.id }) { exercise ->
                LibraryRow(
                    exercise = exercise,
                    onClick = { onRowClick(exercise.id) },
                    secondaryAction = onSecondaryAction,
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(
    exercise: LibraryExercise,
    onClick: () -> Unit,
    secondaryAction: Pair<(String) -> Unit, String>?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryDot(exercise.category)
                    Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    text = PrescriptionSummary.formatDefault(exercise),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // No explanation here on purpose: the list is for finding an exercise, and a
                // paragraph under every row turns scanning it into reading it. The description
                // belongs on the exercise, which is one tap away.
            }
            if (secondaryAction != null) {
                val (action, label) = secondaryAction
                TextButton(onClick = { action(exercise.id) }) { Text(label) }
            }
        }
    }
}
