package com.yokodake.melete.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** One dated value. */
data class ChartPoint(val date: LocalDate, val value: Double)

/**
 * One line. [label] names it in the legend and the readout ("L", "R"); null for a lone series,
 * which the screen's title already names. [square] draws its markers as squares, so two lines
 * differ by shape as well as colour.
 */
data class ChartLine(val label: String?, val points: List<ChartPoint>, val square: Boolean = false)

/**
 * A value over time, for one line or two (left and right).
 *
 * Tapping picks the nearest date; the line above the plot then says that date and every line's
 * value on it, which is how a point is inspected. Without a tap the latest date is described.
 * The scale fits the values rather than starting at zero — the change is the point of the graph
 * — and a zero line is drawn when the values cross it, since zero and below are real loads.
 */
@Composable
fun LineChart(
    lines: List<ChartLine>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    description: String = "Graph over time",
) {
    val dates = lines.flatMap { line -> line.points.map { it.date } }.distinct().sorted()
    if (dates.isEmpty()) return
    var selectedDay by rememberSaveable(dates.first(), dates.last(), dates.size) {
        mutableStateOf(dates.last().toEpochDay())
    }
    val selected = LocalDate.ofEpochDay(selectedDay)
    val colors = seriesColors(lines.size)
    val values = lines.flatMap { line -> line.points.map { it.value } }
    // The scale runs from the lowest recorded value to the highest, so its two labels are real
    // values rather than rounded guesses; a flat history sits across the middle.
    val low = values.min()
    val high = values.max()
    val first = dates.first().toEpochDay()
    val last = dates.last().toEpochDay()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chartDate(selected),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = lines.mapNotNull { line ->
                    val value = line.points.filter { it.date == selected }.maxOfOrNull { it.value }
                        ?: return@mapNotNull null
                    listOfNotNull(line.label, format(value)).joinToString(" ")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
            )
        }
        Row {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
                    .pointerInput(dates) {
                        detectTapGestures { offset ->
                            val inset = 8.dp.toPx()
                            val width = size.width - 2 * inset
                            val day = if (last == first) {
                                first
                            } else {
                                first + ((offset.x - inset) / width * (last - first)).toLong()
                            }
                            selectedDay = dates.minBy { abs(it.toEpochDay() - day) }.toEpochDay()
                        }
                    }
                    .semantics { contentDescription = description },
            ) {
                val inset = 8.dp.toPx()
                val width = size.width - 2 * inset
                fun x(date: LocalDate): Float =
                    if (last == first) size.width / 2 else inset + width * (date.toEpochDay() - first) / (last - first)
                val pad = 12.dp.toPx()
                fun y(value: Double): Float =
                    if (high == low) size.height / 2
                    else (pad + (size.height - 2 * pad) * (high - value) / (high - low)).toFloat()

                drawLine(grid, Offset(0f, y(high)), Offset(size.width, y(high)), strokeWidth = 1f)
                if (high != low) drawLine(grid, Offset(0f, y(low)), Offset(size.width, y(low)), strokeWidth = 1f)
                if (low < 0 && high > 0) {
                    drawLine(
                        grid, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }
                // The inspected date, as a crosshair.
                drawLine(
                    color = muted.copy(alpha = 0.5f),
                    start = Offset(x(selected), 0f),
                    end = Offset(x(selected), size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                lines.forEachIndexed { index, line ->
                    val color = colors[index]
                    val ordered = line.points.sortedBy { it.date }
                    if (ordered.size > 1) {
                        val path = Path()
                        ordered.forEachIndexed { i, p ->
                            if (i == 0) path.moveTo(x(p.date), y(p.value)) else path.lineTo(x(p.date), y(p.value))
                        }
                        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                    }
                    ordered.forEach { p ->
                        val big = p.date == selected
                        marker(Offset(x(p.date), y(p.value)), color, surface, line.square, if (big) 6.dp.toPx() else 4.dp.toPx())
                    }
                }
            }
            // Each label centred on its line, 12dp in from the plot's edge.
            Column(
                modifier = Modifier
                    .height(180.dp)
                    .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = if (high == low) Arrangement.Center else Arrangement.SpaceBetween,
            ) {
                Text(format(high), style = MaterialTheme.typography.labelSmall, color = muted)
                if (high != low) Text(format(low), style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(chartDate(dates.first()), style = MaterialTheme.typography.labelSmall, color = muted)
            Spacer(Modifier.weight(1f))
            // A legend only when there is more than one line to tell apart.
            if (lines.size > 1) {
                lines.forEachIndexed { index, line ->
                    Box(Modifier.size(10.dp)) {
                        Canvas(Modifier.size(10.dp)) {
                            marker(center, colors[index], surface, line.square, 4.dp.toPx())
                        }
                    }
                    Text(
                        text = line.label.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = ink,
                        modifier = Modifier.padding(start = 4.dp, end = 12.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            if (dates.size > 1) {
                Text(chartDate(dates.last()), style = MaterialTheme.typography.labelSmall, color = muted)
            }
            Spacer(Modifier.width(28.dp))
        }
    }
}

/** A marker with a ring of the surface around it, so overlapping points stay apart. */
private fun DrawScope.marker(at: Offset, color: Color, ring: Color, square: Boolean, radius: Float) {
    val halo = radius + 2.dp.toPx()
    if (square) {
        drawRect(ring, Offset(at.x - halo, at.y - halo), Size(halo * 2, halo * 2))
        drawRect(color, Offset(at.x - radius, at.y - radius), Size(radius * 2, radius * 2))
    } else {
        drawCircle(ring, halo, at)
        drawCircle(color, radius, at)
    }
}

/**
 * The lines' colours, in a fixed order: a lone line and the left side are blue, the right side
 * orange — a pair that stays apart for colour-blind eyes, and the shapes differ as well.
 */
@Composable
private fun seriesColors(count: Int): List<Color> {
    val dark = isSystemInDarkTheme()
    val blue = if (dark) Color(0xFF8CB8F0) else Color(0xFF2F6FC4)
    val orange = if (dark) Color(0xFFFF9D4D) else Color(0xFFC75A0C)
    return List(count) { if (it % 2 == 0) blue else orange }
}

/** "24 Sep", with the year once it is not this one. */
private fun chartDate(date: LocalDate): String {
    val pattern = if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date)
}
