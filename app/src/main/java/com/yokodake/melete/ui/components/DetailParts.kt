package com.yokodake.melete.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.ui.week.PrescriptionSummary

/** A heading over a rule, as every detail page separates its parts. */
@Composable
fun DetailSection(title: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

/** The back chevron every focused screen puts in its top bar. */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier.semantics { contentDescription = "Back" },
    ) {
        Text("‹", style = MaterialTheme.typography.headlineMedium)
    }
}

/**
 * One numbered line of a circuit or a module: the position, the category dot, the name and its
 * compact plan, and whatever the page wants to say at the end of the line.
 */
@Composable
fun ContentsLine(
    position: Int,
    category: ExerciseCategory?,
    name: String,
    summary: String?,
    tag: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$position.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CategoryDot(category)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                tag?.let { VariationChip(it) }
            }
            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * A circuit's shape in one line: rounds, the rests in the editor's own words, and the whole
 * circuit's estimate. "3 rounds · switch 15 s · rest 2 min · ≈ 12:30".
 */
fun circuitShapeLine(
    rounds: Int,
    transitionSeconds: Int,
    roundRestSeconds: Int,
    estimatedSeconds: Int?,
): String = listOfNotNull(
    "$rounds ${if (rounds == 1) "round" else "rounds"}",
    transitionSeconds.takeIf { it > 0 }?.let { "switch ${PrescriptionSummary.duration(it)}" },
    roundRestSeconds.takeIf { it > 0 }?.let { "rest ${PrescriptionSummary.duration(it)}" },
    estimatedSeconds?.takeIf { it > 0 }?.let { "≈ ${PrescriptionSummary.duration(it)}" },
).joinToString(" · ")

/** The bar at the foot of a detail page, holding the one or two things worth doing next. */
@Composable
fun DetailActionBar(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
