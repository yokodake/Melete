package com.yokodake.melete.ui.module

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.TrainingModule
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.ui.components.CategoryChip
import com.yokodake.melete.ui.components.dominantCategory
import androidx.compose.foundation.layout.Spacer

/**
 * One saved module: its name, what is in it, and what it is for.
 *
 * With a [secondaryAction] it is a picker row — a tap chooses it and the button beside it does the
 * one other thing. Without one, a long press offers managing it. Either way, entries that have
 * become unavailable are flagged here, before scheduling leaves them out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ModuleRow(
    module: TrainingModule,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onRemove: () -> Unit = {},
    onAddToPlan: (() -> Unit)? = null,
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
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    CategoryChip(module.category, modifier = Modifier.padding(start = 8.dp))
                    Spacer(Modifier.weight(1f))
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
                unavailableNote(module)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            onAddToPlan?.let { add ->
                DropdownMenuItem(
                    text = { Text("Add to plan") },
                    onClick = { menuOpen = false; add() },
                )
            }
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
            DropdownMenuItem(
                text = { Text("Duplicate") },
                onClick = { menuOpen = false; onDuplicate() },
            )
            DropdownMenuItem(text = { Text("Remove") }, onClick = { menuOpen = false; onRemove() })
        }
    }
}

/**
 * What a saved module reads as: the most common category among its entries, a circuit entry
 * counting as its circuit's category, ties to the first. Derived, never stored.
 */
internal val TrainingModule.category: ExerciseCategory?
    get() = dominantCategory(entries.map { if (it.isCircuit) it.routine?.category ?: it.category else it.category })

/** "1 unavailable — left out when added", or null when everything in it still exists. */
internal fun unavailableNote(module: TrainingModule): String? {
    val count = module.unavailableEntries.size
    if (count == 0) return null
    return "$count unavailable"
}
