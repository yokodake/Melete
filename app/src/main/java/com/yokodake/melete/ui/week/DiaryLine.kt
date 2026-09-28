package com.yokodake.melete.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.BenchmarkDayResult
import com.yokodake.melete.data.DiaryDay
import com.yokodake.melete.data.Tracker
import com.yokodake.melete.data.TrackerReading
import com.yokodake.melete.data.entity.TrackerType
import com.yokodake.melete.ui.theme.progressColor

data class DiarySummary(
    val note: String? = null,
    val values: List<DiarySummaryValue> = emptyList(),
) {
    val isEmpty: Boolean get() = note.isNullOrBlank() && values.isEmpty()
}

data class DiarySummaryValue(val label: String, val value: String)

/**
 * A day's diary under its heading: the text, then each recorded value as it was recorded, in
 * today's tracker order, on the next line — as Home shows it. Shown only when there is an entry;
 * tapping it opens the day.
 */
@Composable
fun DiaryLine(day: DiaryDay, trackers: List<Tracker>, onClick: () -> Unit) {
    DiarySummaryText(
        summary = diarySummary(day, trackers),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

/**
 * A day's diary in short, kept structured so its labels do not compete with what was recorded.
 */
fun diarySummary(day: DiaryDay, trackers: List<Tracker>): DiarySummary = DiarySummary(
    note = day.text?.takeIf { it.isNotBlank() },
    values = day.orderedBy(trackers).mapNotNull { tracked ->
        tracked.tracker.summaryValue(tracked.reading)?.let {
            DiarySummaryValue(tracked.tracker.label, it)
        }
    },
)

@Composable
fun DiarySummaryText(summary: DiarySummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        summary.note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (summary.values.isNotEmpty()) {
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            val valueColor = MaterialTheme.colorScheme.onSurface
            Text(
                text = buildAnnotatedString {
                    summary.values.forEachIndexed { index, item ->
                        if (index > 0) withStyle(SpanStyle(color = labelColor)) { append(" · ") }
                        withStyle(SpanStyle(color = labelColor)) {
                            append(item.label.replace(' ', '\u00a0'))
                            append('\u00a0')
                        }
                        withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.SemiBold)) {
                            append(item.value)
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Tracker.summaryValue(reading: TrackerReading): String? = when (type) {
    TrackerType.SCALE -> reading.number?.toInt()?.toString()
    TrackerType.NUMBER -> reading.number?.let { number ->
        val text = if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()
        text + unit?.let { " $it" }.orEmpty()
    }
    TrackerType.CHECK -> "✓".takeIf { reading.number == 1.0 }
    TrackerType.TEXT -> reading.text?.takeIf { it.isNotBlank() }
}

/**
 * A benchmark result on the day it was recorded, read-only like the diary: the name and the value.
 * A record apart from training: no card, nothing to log, nothing counted. A tap opens the benchmark.
 */
@Composable
fun BenchmarkLine(result: BenchmarkDayResult, onClick: () -> Unit) {
    // The flag in the progress blue, like the Benchmarks icon on Home, so a test day stands out
    // from the diary line and the cards around it without reading as an error.
    val flag = progressColor()
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = flag)) { append("\u2691") }
            append(" ${result.name} \u00b7 ${result.text}")
        },
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
