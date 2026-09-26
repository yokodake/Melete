package com.yokodake.melete.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A metric the diary can record: energy, finger discomfort.
 *
 * Definitions are data, not code — a label and the words for each point of its scale — so a new
 * metric is a row, and a later label change cannot reinterpret values already written. There is
 * deliberately no screen for building metrics; the defaults are enough for now.
 */
@Entity(tableName = "metric_definitions")
data class MetricDefinitionEntity(
    /** Stable key, never shown: `energy`, `finger_discomfort`. What a value refers to. */
    @PrimaryKey val id: String,
    val label: String,
    /**
     * The words for each point of the scale, lowest first, as a JSON array. A stored value is the
     * 1-based position in this list.
     */
    val scaleLabelsJson: String,
    val orderIndex: Int,
    /** Retired rather than deleted once any day has a value for it. */
    val deletedAtEpochMs: Long? = null,
)

/**
 * One day's diary: free text, and the metric values below it.
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

/** One metric's value on one day: a position on that metric's scale, from 1. */
@Entity(
    tableName = "diary_metric_values",
    primaryKeys = ["dateEpochDay", "metricId"],
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntryEntity::class,
            parentColumns = ["dateEpochDay"],
            childColumns = ["dateEpochDay"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MetricDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["metricId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("metricId")],
)
data class DiaryMetricValueEntity(
    val dateEpochDay: Long,
    val metricId: String,
    val value: Int,
)
