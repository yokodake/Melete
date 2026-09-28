package com.yokodake.melete.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.BenchmarkDayResult
import com.yokodake.melete.data.DiaryDay
import com.yokodake.melete.data.Tracker

/**
 * A day's diary in one line under its heading: the text, then each recorded value as it was
 * recorded, in today's tracker order. Shown only when there is an entry; tapping it opens the day.
 */
@Composable
fun DiaryLine(day: DiaryDay, trackers: List<Tracker>, onClick: () -> Unit) {
    val recorded = day.orderedBy(trackers).mapNotNull { it.tracker.format(it.reading) }
    Text(
        text = listOfNotNull(day.text?.takeIf { it.isNotBlank() }, recorded.joinToString(" · ").ifEmpty { null })
            .joinToString(" — "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

/**
 * A benchmark result on the day it was recorded, read-only like the diary: the name and the value.
 * A record apart from training: no card, nothing to log, nothing counted. A tap opens the benchmark.
 */
@Composable
fun BenchmarkLine(result: BenchmarkDayResult, onClick: () -> Unit) {
    Text(
        text = "\u2691 ${result.name} \u00b7 ${result.text}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}
