package com.yokodake.melete.ui.routine

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.Routine
import com.yokodake.melete.data.timer.PrescriptionProgram
import com.yokodake.melete.data.timer.StationPlan
import com.yokodake.melete.ui.components.CategoryDot
import com.yokodake.melete.ui.week.PrescriptionSummary

/**
 * One saved circuit, as it appears among the workouts: built exactly like an exercise row — the
 * category dot and the name on top, one line of what it is underneath — so the list reads as one
 * list. The line is what tells them apart: "3 rounds · Pull-up → Plank → Push-up".
 *
 * With a [secondaryAction] it is a picker row — a tap chooses it and the button beside it does the
 * one other thing, as an exercise row does in the same picker. Without one, the overflow and a long
 * press both offer managing it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RoutineRow(
    routine: Routine,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onRemove: () -> Unit = {},
    onAddToPlan: (() -> Unit)? = null,
    secondaryAction: Pair<() -> Unit, String>? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (secondaryAction == null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        }
                    },
                    onLongClickLabel = "Circuit actions",
                ),
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
                        CategoryDot(routine.category)
                        Text(
                            text = routine.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Text(
                        text = routineSummary(routine),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (secondaryAction != null) {
                    val (action, label) = secondaryAction
                    TextButton(onClick = action) { Text(label) }
                } else {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.semantics { contentDescription = "Circuit actions" },
                    ) {
                        Text("⋮", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            onAddToPlan?.let { add ->
                DropdownMenuItem(
                    text = { Text("Add to plan") },
                    onClick = {
                        menuOpen = false
                        add()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    menuOpen = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                onClick = {
                    menuOpen = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

/**
 * A circuit in one line, the way a plan summary reads: how many times round, the exercises in
 * order, and how long the whole thing takes.
 */
internal fun routineSummary(routine: Routine): String = listOf(
    "${routine.rounds} ${if (routine.rounds == 1) "round" else "rounds"}",
    routine.entries.joinToString(" → ") { it.name }.ifEmpty { "no exercises" },
    routineLength(routine),
).joinToString(" · ")

/**
 * How long the whole circuit takes, worked out once from the sequence it will actually run.
 *
 * Never by adding up what its exercises would take on their own: a station in a circuit does one
 * set per round and rests by the circuit's rules, so its standalone plan is the wrong arithmetic.
 */
internal fun routineLength(routine: Routine): String {
    if (routine.entries.isEmpty()) return "empty"
    val program = PrescriptionProgram.circuit(
        label = routine.name,
        rounds = routine.rounds,
        transitionSeconds = routine.transitionSeconds,
        roundRestSeconds = routine.roundRestSeconds,
        stations = routine.entries.map {
            StationPlan(
                label = it.name,
                mode = it.mode,
                unilateral = it.unilateral,
                prescription = it.prescription,
            )
        },
    )
    return "≈ ${PrescriptionSummary.duration(program.estimatedSeconds())}"
}
