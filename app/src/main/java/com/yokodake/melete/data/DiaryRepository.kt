package com.yokodake.melete.data

import androidx.room.withTransaction
import com.yokodake.melete.data.entity.DiaryEntryEntity
import com.yokodake.melete.data.entity.DiaryMetricValueEntity
import com.yokodake.melete.data.entity.MetricDefinitionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** A metric the diary records, with the words for each point of its scale, lowest first. */
data class MetricDefinition(
    val id: String,
    val label: String,
    val scale: List<String>,
) {
    /** The word for a stored value, or null for a value off the scale. */
    fun labelFor(value: Int): String? = scale.getOrNull(value - 1)
}

/** One day's diary: its text and its metric values, keyed by metric id. */
data class DiaryDay(
    val date: LocalDate,
    val text: String?,
    val values: Map<String, Int>,
) {
    val isEmpty: Boolean get() = text.isNullOrBlank() && values.isEmpty()
}

/**
 * The metrics every new database starts with. Stored as data like any other definition, so they
 * can be relabelled or joined by more without a code change to what was already written.
 */
object DiaryDefaults {
    val metrics = listOf(
        MetricDefinition(
            id = "energy",
            label = "Energy",
            scale = listOf("Very low", "Low", "Moderate", "High", "Very high"),
        ),
        MetricDefinition(
            id = "finger_discomfort",
            label = "Finger discomfort",
            scale = listOf("None", "Slight", "Noticeable", "Painful", "Severe"),
        ),
    )
}

/** Scale labels as stored: a JSON array of strings. */
internal object ScaleJson {
    private val serializer = ListSerializer(String.serializer())
    fun encode(labels: List<String>): String = Json.encodeToString(serializer, labels)
    fun decode(json: String): List<String> =
        runCatching { Json.decodeFromString(serializer, json) }.getOrDefault(emptyList())
}

/**
 * The date-based diary: a few lines and a couple of ratings for a day.
 *
 * Its own repository, apart from training: a diary entry is never a workout, never counted, and
 * never asked for while logging.
 */
class DiaryRepository(private val database: MeleteDatabase) {

    private val diary = database.diaryDao()

    /** The metrics that can be recorded, the defaults added first if they are missing. */
    fun observeMetrics(): Flow<List<MetricDefinition>> =
        diary.observeMetrics()
            .onStart { ensureDefaults() }
            .map { rows -> rows.map { it.toMetric() } }

    /** The diary for each day from [from] to [to] inclusive that has an entry. */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DiaryDay>> = combine(
        diary.observeEntries(from.toEpochDay(), to.toEpochDay()),
        diary.observeValues(from.toEpochDay(), to.toEpochDay()),
    ) { entries, values ->
        val byDay = values.groupBy { it.dateEpochDay }
        entries.associate { entry ->
            val date = LocalDate.ofEpochDay(entry.dateEpochDay)
            date to DiaryDay(
                date = date,
                text = entry.text,
                values = byDay[entry.dateEpochDay].orEmpty().associate { it.metricId to it.value },
            )
        }
    }

    /**
     * Writes one day's diary, replacing what was there. Emptied completely — no text and no
     * values — it is removed rather than kept as a blank row.
     */
    suspend fun save(date: LocalDate, text: String?, values: Map<String, Int?>) {
        database.withTransaction {
            val day = date.toEpochDay()
            val cleaned = text?.trim()?.takeIf { it.isNotEmpty() }
            val kept = values.mapNotNull { (id, value) -> value?.let { id to it } }
            diary.deleteValues(day)
            if (cleaned == null && kept.isEmpty()) {
                diary.deleteEntry(day)
                return@withTransaction
            }
            diary.upsertEntry(DiaryEntryEntity(day, cleaned, System.currentTimeMillis()))
            diary.insertValues(kept.map { (id, value) -> DiaryMetricValueEntity(day, id, value) })
        }
    }

    private suspend fun ensureDefaults() {
        DiaryDefaults.metrics.forEachIndexed { index, metric ->
            diary.insertMetricIfAbsent(
                MetricDefinitionEntity(
                    id = metric.id,
                    label = metric.label,
                    scaleLabelsJson = ScaleJson.encode(metric.scale),
                    orderIndex = index,
                )
            )
        }
    }
}

private fun MetricDefinitionEntity.toMetric() =
    MetricDefinition(id = id, label = label, scale = ScaleJson.decode(scaleLabelsJson))
