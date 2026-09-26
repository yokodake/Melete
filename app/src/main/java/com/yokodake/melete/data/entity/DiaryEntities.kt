package com.yokodake.melete.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** What a daily tracker records. */
enum class TrackerType(val label: String) {
    /** A whole number on a range the user chooses, e.g. 0–5. */
    SCALE("Scale"),

    /** A measured number with a unit, e.g. body weight in kg. */
    NUMBER("Number"),

    /** Done or not: a checkmark. */
    CHECK("Checkmark"),

    /** A short comment. */
    TEXT("Comment"),
}

/**
 * Something the diary tracks each day: energy, finger discomfort, body weight, "stretched".
 *
 * Defined by the user as data — a label, a kind, and for a scale its range or for a number its
 * unit — so a new tracker is a row, not a release. Retired rather than deleted, so a day that was
 * tracked keeps saying what it recorded.
 */
@Entity(tableName = "trackers")
data class TrackerEntity(
    /** Stable key, never shown; what a day's value refers to. */
    @PrimaryKey val id: String,
    val label: String,
    val type: TrackerType,
    /** A scale's lowest value. Absent for other kinds. */
    val scaleMin: Int? = null,
    /** A scale's highest value. Absent for other kinds. */
    val scaleMax: Int? = null,
    /** A number's unit, e.g. "kg". Absent for other kinds, and optional for a number. */
    val unit: String? = null,
    val orderIndex: Int,
    val createdAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
)

/**
 * One day's diary: free text, and the tracked values below it.
 *
 * Keyed by the date alone — a day has one entry — and never a workout: nothing counts it, and
 * logging never asks for it.
 */
@Entity(tableName = "diary_entries")
data class DiaryEntryEntity(
    @PrimaryKey val dateEpochDay: Long,
    val text: String?,
    val updatedAtEpochMs: Long,
)

/**
 * One tracker's value on one day. A scale, a number or a checkmark (1 for done) is [number]; a
 * comment is [text]; a value never recorded has no row.
 *
 * The tracker as it was when the value was written travels with it — name, kind, scale and unit
 * — so changing a tracker changes the days to come and never what a past day says: a 3 on a 0–5
 * scale stays a 3 out of 5 after the scale becomes 1–10, and 72.5 kg does not become 72.5 lb.
 */
@Entity(
    tableName = "diary_values",
    primaryKeys = ["dateEpochDay", "trackerId"],
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntryEntity::class,
            parentColumns = ["dateEpochDay"],
            childColumns = ["dateEpochDay"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("trackerId")],
)
data class DiaryValueEntity(
    val dateEpochDay: Long,
    val trackerId: String,
    val labelSnapshot: String,
    val typeSnapshot: TrackerType,
    val scaleMinSnapshot: Int? = null,
    val scaleMaxSnapshot: Int? = null,
    val unitSnapshot: String? = null,
    val number: Double? = null,
    val text: String? = null,
)
