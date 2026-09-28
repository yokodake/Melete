package com.yokodake.melete.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yokodake.melete.data.model.MeasurementMeaning

/** What a benchmark measures: a number with a unit (the kind picks sensible defaults), or words. */
enum class BenchmarkMeasure(val label: String, val defaultUnit: String) {
    LOAD("Load", "kg"),
    DURATION("Duration", "s"),
    DISTANCE("Distance", "m"),
    OTHER("Other", ""),

    /** A result in words ("touching heels"), for tests that have no honest number. No best. */
    TEXT("Text", ""),
}

/**
 * A reference test: a small, named definition that results are recorded against.
 *
 * Deliberately not a library exercise. A benchmark is where you stand, not something planned or
 * trained: its results live in [BenchmarkResultEntity] alone, create no occurrence and count
 * nothing. Hidden rather than deleted once it has results, so they stay readable.
 */
@Entity(tableName = "benchmarks")
data class BenchmarkEntity(
    @PrimaryKey val id: String,
    val name: String,
    val measure: BenchmarkMeasure,
    /** "kg", "s", "m", "reps" — whatever the number is in. May be empty. */
    val unit: String,
    /**
     * For a load only: everything lifted, or relative to bodyweight (signed, so assistance is
     * below zero). Null for every other measure.
     */
    val loadMeaning: MeasurementMeaning? = null,
    /** Separate left and right values. */
    val unilateral: Boolean,
    /** Whether a bigger number is the better result. */
    val higherIsBetter: Boolean,
    /** The fixed conditions that make results comparable, e.g. "20 mm · 7 s · added load". */
    val protocol: String? = null,
    /** What it is aiming for, as written: "150% BW", "179 cm", "face to knees". */
    val goal: String? = null,
    val orderIndex: Int,
    val createdAtEpochMs: Long,
    val hiddenAtEpochMs: Long? = null,
)

/**
 * One recorded result: the best valid attempt on one date.
 *
 * [value] is the result, or the left side's when the benchmark is unilateral; [valueRight] is the
 * right side's; [textValue] is a result in words. At least one of them is present. The unit, load
 * meaning and sidedness the result was recorded under travel with it, so editing the definition never changes what an old result
 * says. Zero and negative values are real results, not blanks.
 */
@Entity(
    tableName = "benchmark_results",
    foreignKeys = [
        ForeignKey(
            entity = BenchmarkEntity::class,
            parentColumns = ["id"],
            childColumns = ["benchmarkId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("benchmarkId"), Index("dateEpochDay")],
)
data class BenchmarkResultEntity(
    @PrimaryKey val id: String,
    val benchmarkId: String,
    val dateEpochDay: Long,
    val value: Double? = null,
    val valueRight: Double? = null,
    /** A result in words, for a text benchmark. */
    val textValue: String? = null,
    /** Bodyweight percentage as reported alongside a load, never derived. */
    val bodyweightPercent: Double? = null,
    val unitSnapshot: String,
    val loadMeaningSnapshot: MeasurementMeaning? = null,
    val unilateralSnapshot: Boolean,
    val note: String? = null,
    val recordedAtEpochMs: Long,
)
