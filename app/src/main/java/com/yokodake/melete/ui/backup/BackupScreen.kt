package com.yokodake.melete.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.yokodake.melete.data.backup.BackupSummary
import com.yokodake.melete.data.plan.ImportMode
import com.yokodake.melete.data.plan.ImportScope
import com.yokodake.melete.data.plan.ReplaceImpact
import com.yokodake.melete.data.plan.PlanPreview
import com.yokodake.melete.ui.library.SegmentedChoice
import java.time.LocalDate

@Composable
fun BackupRoute(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Android's own file picker, both ways: the file goes wherever the user can see and move it.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportTo) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::load) }
    val planLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::loadPlan) }

    BackupScreen(
        state = state,
        onExport = { exportLauncher.launch("melete-${LocalDate.now()}.json") },
        onPickRestore = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onPickPlan = { planLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onPlanMode = viewModel::setPlanMode,
        onPlanScope = viewModel::setPlanScope,
        onConfirmPlan = viewModel::confirmPlan,
        onCancelPlan = viewModel::cancelPlan,
        onRestoreSafetyCopy = viewModel::loadSafetyCopy,
        onConfirmRestore = viewModel::confirmRestore,
        onCancelRestore = viewModel::cancelRestore,
        onDismissProblems = viewModel::dismissProblems,
        onMessageShown = viewModel::consumeMessage,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    state: BackupUiState,
    onExport: () -> Unit,
    onPickRestore: () -> Unit,
    onPickPlan: () -> Unit = {},
    onPlanMode: (ImportMode) -> Unit = {},
    onPlanScope: (ImportScope) -> Unit = {},
    onConfirmPlan: () -> Unit = {},
    onCancelPlan: () -> Unit = {},
    onRestoreSafetyCopy: (SafetyCopy) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onDismissProblems: () -> Unit,
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
                title = { Text("Backup & restore", style = MaterialTheme.typography.titleMedium) },
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

            item {
                Section("On this phone")
                state.current?.let { SummaryText(it) }
            }
            item {
                Button(onClick = onExport, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Export")
                }
                Text(
                    text = "Library, plans, logs and daily notes in one file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedButton(onClick = onPickRestore, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Restore…")
                }
                Text(
                    text = "Replaces your Melete data. A recovery copy is saved first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Section("Plan")
                OutlinedButton(onClick = onPickPlan, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Import a plan…")
                }
                Text(
                    text = "A hand-written file of exercises, circuits, modules and weeks. You see what " +
                        "it will add or change before anything is written. Logged work is never deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.safetyCopies.isNotEmpty()) {
                item { Section("Recovery copies") }
                items(items = state.safetyCopies, key = { it.file.name }) { copy ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(copy.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRestoreSafetyCopy(copy) }, enabled = !state.busy) {
                            Text("Restore")
                        }
                    }
                }
            }
        }
    }

    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("Replace Melete data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${pending.source} · ${pending.summary.exportedAt.take(16).replace('T', ' ')}")
                    SummaryCard(pending.summary)
                    state.current?.let {
                        Text("Current data")
                        SummaryCard(it)
                    }
                    Text(
                        text = "Your current data will be saved under Recovery copies.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmRestore) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("Cancel") } },
        )
    }

    state.pendingPlan?.let { pending ->
        PlanDialog(
            pending = pending,
            onMode = onPlanMode,
            onScope = onPlanScope,
            onConfirm = onConfirmPlan,
            onCancel = onCancelPlan,
        )
    }

    if (state.problems.isNotEmpty()) {
        var showDetails by remember(state.problems) { mutableStateOf(false) }
        val summaries = state.problems.map(::problemSummary).distinct()
        val hasDetails = state.problems.any { problemSummary(it) != it }
        AlertDialog(
            onDismissRequest = onDismissProblems,
            title = { Text(state.problemsTitle) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Nothing on the phone was changed.", style = MaterialTheme.typography.bodyMedium)
                    summaries.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                    if (hasDetails) {
                        TextButton(onClick = { showDetails = !showDetails }) {
                            Text(if (showDetails) "Hide details" else "Details")
                        }
                        if (showDetails) {
                            state.problems.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismissProblems) { Text("OK") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SummaryText(summary: BackupSummary) {
    Text(summaryLine(summary), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SummaryCard(summary: BackupSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(summaryLine(summary), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
    }
}

private fun summaryLine(summary: BackupSummary): String = listOf(
    countLabel(summary.exercises, "exercise"),
    countLabel(summary.circuits, "circuit"),
    countLabel(summary.modules, "module"),
    countLabel(summary.planned, "training entry", "training entries"),
    countLabel(summary.loggedSets, "logged set"),
    countLabel(summary.diaryDays, "daily entry", "daily entries"),
).joinToString(" · ")

private fun countLabel(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"

private fun problemSummary(problem: String): String = when {
    problem.startsWith("This backup was written by a newer version") ->
        "This backup needs a newer version of Melete."
    problem.startsWith("This is not a Melete backup (format") -> "This is not a Melete backup."
    problem.startsWith("Two ") || problem.contains("has two values for the same tracker") -> "Duplicate entries"
    problem.endsWith("not in the file.") -> "Missing training data"
    problem.startsWith("The scheduled circuit ") && problem.endsWith(" has an unreadable snapshot.") ->
        "Couldn’t read " + problem.removePrefix("The scheduled circuit ")
            .removeSuffix(" has an unreadable snapshot.")
    problem.startsWith("A logged set (") && problem.endsWith("cannot be read.") -> "Couldn’t read a logged set"
    else -> problem
}

/**
 * What a plan would do, before it does it: the mode, what is added and what is changed, what is
 * left out and why, and — if the file cannot be imported in this mode — every reason.
 */
@Composable
private fun PlanDialog(
    pending: PendingPlan,
    onMode: (ImportMode) -> Unit,
    onScope: (ImportScope) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val resolution = pending.check.resolution
    var showWarnings by remember(resolution) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import plan") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(pending.source, style = MaterialTheme.typography.bodySmall)
                SegmentedChoice(
                    options = ImportMode.entries,
                    selected = pending.mode,
                    label = { if (it == ImportMode.ADD) "Add" else "Replace plans" },
                    onSelect = onMode,
                )
                SegmentedChoice(
                    options = ImportScope.entries,
                    selected = pending.scope,
                    label = { if (it == ImportScope.FROM_TODAY) "From today" else "Include past" },
                    onSelect = onScope,
                )
                val reach = if (pending.scope == ImportScope.FROM_TODAY) "from today on" else "past days included"
                Text(
                    text = when (pending.mode) {
                        ImportMode.ADD -> "Keeps everything. Items with the same name are updated from " +
                            "now on; weeks already planned and logged keep what they say. Plans $reach."
                        ImportMode.REPLACE -> "Clears planned work that hasn't happened, $reach, and " +
                            "takes what the file doesn't name out of the library, then builds from the " +
                            "file. Anything logged, done or skipped stays, and so do daily notes. A " +
                            "recovery copy is saved."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pending.check.replace?.let { impact ->
                    replaceLines(impact).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                if (resolution.problems.isNotEmpty()) {
                    Text(
                        text = "Can't import in this mode:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    resolution.problems.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                } else {
                    planLines(resolution.preview).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                if (resolution.warnings.isNotEmpty()) {
                    TextButton(onClick = { showWarnings = !showWarnings }) {
                        val count = resolution.warnings.size
                        Text(if (showWarnings) "Hide what is left out" else "$count thing${if (count == 1) "" else "s"} left out")
                    }
                    if (showWarnings) {
                        resolution.warnings.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = resolution.plan != null) {
                Text(if (pending.mode == ImportMode.REPLACE) "Replace" else "Import")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** What replacing clears, keeps and removes, before the file's own additions. */
private fun replaceLines(impact: ReplaceImpact): List<String> {
    fun names(list: List<String>) = if (list.size <= 4) ": " + list.joinToString(", ") else ""
    return listOfNotNull(
        "Clears ${countLabel(impact.cleared, "planned item")} that haven't happened",
        impact.kept.takeIf { it > 0 }?.let { "Keeps ${countLabel(it, "logged or skipped item")}" },
        impact.removedExercises.takeIf { it.isNotEmpty() }?.let {
            "Removes ${countLabel(it.size, "exercise")} from the library${names(it)}"
        },
        impact.removedCircuits.takeIf { it.isNotEmpty() }?.let {
            "Removes ${countLabel(it.size, "circuit")}${names(it)}"
        },
        impact.removedModules.takeIf { it.isNotEmpty() }?.let {
            "Removes ${countLabel(it.size, "module")}${names(it)}"
        },
    )
}

/** The preview in lines: added and updated per kind, with names while there are few. */
private fun planLines(preview: PlanPreview): List<String> {
    fun names(list: List<String>) = if (list.size <= 4) ": " + list.joinToString(", ") else ""
    fun line(verb: String, list: List<String>, singular: String) =
        list.takeIf { it.isNotEmpty() }?.let { "$verb ${countLabel(it.size, singular)}${names(it)}" }
    return listOfNotNull(
        line("Adds", preview.exercisesAdded, "exercise"),
        line("Updates", preview.exercisesUpdated, "exercise"),
        preview.variations.takeIf { it > 0 }?.let { "Sets ${countLabel(it, "variation")}" },
        line("Adds", preview.circuitsAdded, "circuit"),
        line("Updates", preview.circuitsUpdated, "circuit"),
        line("Adds", preview.modulesAdded, "module"),
        line("Updates", preview.modulesUpdated, "module"),
        preview.planned.takeIf { it > 0 }?.let {
            "Plans ${countLabel(it, "item")} across ${countLabel(preview.weeks, "week")}"
        },
        preview.skippedPast.takeIf { it > 0 }?.let { "Leaves out ${countLabel(it, "item")} dated before today" },
    ).ifEmpty { listOf("The file holds nothing to import.") }
}
