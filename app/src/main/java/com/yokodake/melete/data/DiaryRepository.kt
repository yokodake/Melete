package com.yokodake.melete.data

import androidx.room.withTransaction
import com.yokodake.melete.data.entity.DiaryEntryEntity
import com.yokodake.melete.data.entity.DiaryValueEntity
import com.yokodake.melete.data.entity.TrackerEntity
import com.yokodake.melete.data.entity.TrackerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate
import java.util.UUID

/** A daily tracker as the rest of the app sees it. */
data class Tracker(
    val id: String,
    val label: String,
    val type: TrackerType,
    val scaleMin: Int? = null,
    val scaleMax: Int? = null,
    val unit: String? = null,
) {
    /** The values a scale offers, lowest first; empty for other kinds. */
    val scale: List<Int>
        get() = if (type == TrackerType.SCALE && scaleMin != null && scaleMax != null && scaleMin <= scaleMax) {
            (scaleMin..scaleMax).toList()
        } else {
            emptyList()
        }

    /** A reading in words for a one-line summary, or null when there is nothing worth showing. */
    fun format(reading: TrackerReading): String? = when (type) {
        TrackerType.SCALE -> reading.number?.let { "$label ${it.toInt()}" }
        TrackerType.NUMBER -> reading.number?.let { "$label ${trim(it)}${unit?.let { u -> " $u" }.orEmpty()}" }
        TrackerType.CHECK -> label.takeIf { reading.number == 1.0 }?.let { "$it ✓" }
        TrackerType.TEXT -> reading.text?.takeIf { it.isNotBlank() }?.let { "$label: $it" }
    }

    /** Whether [reading] is something this tracker can hold: the right kind, and on the scale. */
    fun accepts(reading: TrackerReading): Boolean = when (type) {
        TrackerType.SCALE -> reading.text == null && reading.number?.let { n ->
            n == n.toInt().toDouble() && n.toInt() in scale
        } == true
        TrackerType.NUMBER -> reading.text == null && reading.number != null
        TrackerType.CHECK -> reading.text == null && reading.number == 1.0
        TrackerType.TEXT -> reading.number == null && !reading.text.isNullOrBlank()
    }

    private fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}

/**
 * One tracker's value on one day. A scale, a number and a checkmark (1 for done) are [number]; a
 * comment is [text]. The tracker's kind says which applies.
 */
data class TrackerReading(val number: Double? = null, val text: String? = null) {
    val isEmpty: Boolean get() = number == null && text.isNullOrBlank()
}

/**
 * A value as it was recorded, with the tracker as it was then: [tracker] is that day's definition,
 * not necessarily today's.
 */
data class TrackedValue(val tracker: Tracker, val reading: TrackerReading)

/** One day's diary: its text and its tracked values, keyed by tracker id. */
data class DiaryDay(
    val date: LocalDate,
    val text: String?,
    val values: Map<String, TrackedValue>,
) {
    val isEmpty: Boolean get() = text.isNullOrBlank() && values.values.all { it.reading.isEmpty }

    /** The values in the order [trackers] lists them; those no longer tracked come last. */
    fun orderedBy(trackers: List<Tracker>): List<TrackedValue> {
        val position = trackers.withIndex().associate { it.value.id to it.index }
        return values.values.sortedWith(compareBy({ position[it.tracker.id] ?: Int.MAX_VALUE }, { it.tracker.label }))
    }
}

/**
 * The trackers every new database starts with. Ordinary rows once written: the user can rename,
 * rescale, reorder or retire them like any other.
 */
object DiaryDefaults {
    val trackers = listOf(
        Tracker(id = "energy", label = "Energy", type = TrackerType.SCALE, scaleMin = 0, scaleMax = 5),
        Tracker(
            id = "finger_discomfort", label = "Finger discomfort", type = TrackerType.SCALE,
            scaleMin = 0, scaleMax = 5,
        ),
    )
}

/** Why a tracker could not be saved, in words; null when it can. */
fun trackerProblem(label: String, type: TrackerType, scaleMin: Int?, scaleMax: Int?): String? = when {
    label.isBlank() -> "Enter a name."
    type == TrackerType.SCALE && (scaleMin == null || scaleMax == null) -> "Enter both limits."
    type == TrackerType.SCALE && scaleMin!! >= scaleMax!! -> "The highest value must be above the lowest."
    type == TrackerType.SCALE && scaleMax!! - scaleMin!! > 100 -> "Maximum range: 100."
    else -> null
}

/**
 * The date-based diary and the trackers it records.
 *
 * Its own repository, apart from training: a diary entry is never a workout, never counted, and
 * never asked for while logging.
 */
class DiaryRepository(private val database: MeleteDatabase) {

    private val diary = database.diaryDao()

    /** The trackers in use, the defaults written first on a brand-new database. */
    fun observeTrackers(): Flow<List<Tracker>> =
        diary.observeTrackers()
            .onStart { ensureDefaults() }
            .map { rows -> rows.map { it.toTracker() } }

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
                values = byDay[entry.dateEpochDay].orEmpty().associate { value ->
                    value.trackerId to TrackedValue(
                        tracker = Tracker(
                            id = value.trackerId,
                            label = value.labelSnapshot,
                            type = value.typeSnapshot,
                            scaleMin = value.scaleMinSnapshot,
                            scaleMax = value.scaleMaxSnapshot,
                            unit = value.unitSnapshot,
                        ),
                        reading = TrackerReading(value.number, value.text),
                    )
                },
            )
        }
    }

    /**
     * Writes one day's diary, replacing what was there. Each value is written with the tracker it
     * was recorded against — the day's own definition, which a later change to the tracker leaves
     * alone. Emptied completely — no text and nothing tracked — the day is removed rather than
     * kept as a blank row. A value its tracker cannot hold is dropped, never stored.
     */
    suspend fun save(date: LocalDate, text: String?, values: List<TrackedValue>) {
        database.withTransaction {
            val day = date.toEpochDay()
            val cleaned = text?.trim()?.takeIf { it.isNotEmpty() }
            val kept = values
                .map { it.copy(reading = it.reading.copy(text = it.reading.text?.trim())) }
                .filter { it.tracker.accepts(it.reading) }
                .distinctBy { it.tracker.id }
            diary.deleteValues(day)
            if (cleaned == null && kept.isEmpty()) {
                diary.deleteEntry(day)
                return@withTransaction
            }
            diary.upsertEntry(DiaryEntryEntity(day, cleaned, System.currentTimeMillis()))
            diary.insertValues(
                kept.map { (tracker, reading) ->
                    DiaryValueEntity(
                        dateEpochDay = day,
                        trackerId = tracker.id,
                        labelSnapshot = tracker.label,
                        typeSnapshot = tracker.type,
                        scaleMinSnapshot = tracker.scaleMin,
                        scaleMaxSnapshot = tracker.scaleMax,
                        unitSnapshot = tracker.unit,
                        number = reading.number,
                        text = reading.text,
                    )
                }
            )
        }
    }

    // --------------------------------------------------------------- trackers

    /** Adds a tracker at the end of the list. Returns why not, in words, or null when added. */
    suspend fun createTracker(
        label: String,
        type: TrackerType,
        scaleMin: Int?,
        scaleMax: Int?,
        unit: String?,
    ): String? = database.withTransaction {
        trackerProblem(label, type, scaleMin, scaleMax)?.let { return@withTransaction it }
        diary.insertTracker(
            TrackerEntity(
                id = UUID.randomUUID().toString(),
                label = label.trim(),
                type = type,
                scaleMin = scaleMin.takeIf { type == TrackerType.SCALE },
                scaleMax = scaleMax.takeIf { type == TrackerType.SCALE },
                unit = unit?.trim()?.takeIf { it.isNotEmpty() && type == TrackerType.NUMBER },
                orderIndex = diary.nextTrackerOrder(),
                createdAtEpochMs = System.currentTimeMillis(),
            )
        )
        null
    }

    /**
     * Changes a tracker from now on. Anything about it can change — name, kind, scale, unit —
     * because days already recorded keep the definition they were written with. Returns why not,
     * in words, or null when saved.
     */
    suspend fun updateTracker(
        id: String,
        label: String,
        type: TrackerType,
        scaleMin: Int?,
        scaleMax: Int?,
        unit: String?,
    ): String? = database.withTransaction {
        val existing = diary.getTracker(id) ?: return@withTransaction null
        trackerProblem(label, type, scaleMin, scaleMax)?.let { return@withTransaction it }
        diary.updateTracker(
            existing.copy(
                label = label.trim(),
                type = type,
                scaleMin = scaleMin.takeIf { type == TrackerType.SCALE },
                scaleMax = scaleMax.takeIf { type == TrackerType.SCALE },
                unit = unit?.trim()?.takeIf { it.isNotEmpty() && type == TrackerType.NUMBER },
            )
        )
        null
    }

    /**
     * Stops tracking it: gone from the diary from now on. Days that recorded it keep their values,
     * shown with the definition they carry. Retired rather than deleted, so those values still
     * name a tracker in a backup, and the defaults do not come back because the table looks new.
     */
    suspend fun retireTracker(id: String) {
        database.withTransaction {
            val existing = diary.getTracker(id) ?: return@withTransaction
            diary.updateTracker(existing.copy(deletedAtEpochMs = System.currentTimeMillis()))
        }
    }

    /**
     * Whether bodyweight is tracked: the diary's own "Bodyweight" tracker, active. One history —
     * the diary's values — for the diary and, later, the profile alike.
     */
    fun observeBodyweightTracked(): Flow<Boolean> =
        observeTrackers().map { trackers -> trackers.any { it.id == BODYWEIGHT_TRACKER_ID } }

    /**
     * Starts or stops tracking bodyweight. On adds the tracker (a number in kg, at the end of the
     * list) or brings a retired one back; off retires it, and every value recorded stays.
     */
    suspend fun setBodyweightTracked(tracked: Boolean) {
        database.withTransaction {
            val existing = diary.getTracker(BODYWEIGHT_TRACKER_ID)
            when {
                !tracked -> if (existing != null && existing.deletedAtEpochMs == null) {
                    diary.updateTracker(existing.copy(deletedAtEpochMs = System.currentTimeMillis()))
                }
                existing == null -> diary.insertTracker(
                    TrackerEntity(
                        id = BODYWEIGHT_TRACKER_ID,
                        label = "Bodyweight",
                        type = TrackerType.NUMBER,
                        unit = "kg",
                        orderIndex = diary.nextTrackerOrder(),
                        createdAtEpochMs = System.currentTimeMillis(),
                    )
                )
                existing.deletedAtEpochMs != null ->
                    diary.updateTracker(existing.copy(deletedAtEpochMs = null, orderIndex = diary.nextTrackerOrder()))
            }
        }
    }

    /** Moves a tracker one place up or down the list the diary shows them in. */
    suspend fun moveTracker(id: String, delta: Int) {
        database.withTransaction {
            val list = diary.activeTrackers().toMutableList()
            val from = list.indexOfFirst { it.id == id }
            val to = from + delta
            if (from < 0 || to !in list.indices) return@withTransaction
            list.add(to, list.removeAt(from))
            list.forEachIndexed { index, tracker ->
                if (tracker.orderIndex != index) diary.updateTracker(tracker.copy(orderIndex = index))
            }
        }
    }

    private suspend fun ensureDefaults() = database.withTransaction {
        // Inside a transaction, so two screens asking at once cannot both write them.
        if (diary.countAllTrackers() > 0) return@withTransaction
        val now = System.currentTimeMillis()
        DiaryDefaults.trackers.forEachIndexed { index, tracker ->
            diary.insertTracker(
                TrackerEntity(
                    id = tracker.id, label = tracker.label, type = tracker.type,
                    scaleMin = tracker.scaleMin, scaleMax = tracker.scaleMax, unit = tracker.unit,
                    orderIndex = index, createdAtEpochMs = now,
                )
            )
        }
    }
}

private fun TrackerEntity.toTracker() = Tracker(id, label, type, scaleMin, scaleMax, unit)

/** The bodyweight tracker's stable id, so turning tracking off and on again finds the same history. */
const val BODYWEIGHT_TRACKER_ID = "bodyweight"
