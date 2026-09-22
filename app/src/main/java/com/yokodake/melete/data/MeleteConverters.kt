package com.yokodake.melete.data

import androidx.room.TypeConverter
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning

/**
 * Enums are stored by name. The stored names are part of the on-disk format: rename a constant
 * only together with a migration.
 */
class MeleteConverters {

    @TypeConverter
    fun exerciseModeToString(value: ExerciseMode): String = value.name

    @TypeConverter
    fun stringToExerciseMode(value: String): ExerciseMode = ExerciseMode.valueOf(value)

    @TypeConverter
    fun occurrenceStateToString(value: OccurrenceState): String = value.name

    @TypeConverter
    fun stringToOccurrenceState(value: String): OccurrenceState = OccurrenceState.valueOf(value)

    @TypeConverter
    fun measurementMeaningToString(value: MeasurementMeaning?): String? = value?.name

    @TypeConverter
    fun stringToMeasurementMeaning(value: String?): MeasurementMeaning? =
        value?.let(MeasurementMeaning::valueOf)

    @TypeConverter
    fun exerciseCategoryToString(value: ExerciseCategory?): String? = value?.name

    /**
     * A category written by a newer version is read as "no category" rather than crashing the
     * whole query: an unknown label must not make the training record unreadable.
     */
    @TypeConverter
    fun stringToExerciseCategory(value: String?): ExerciseCategory? =
        value?.let { name -> ExerciseCategory.entries.firstOrNull { it.name == name } }

    @TypeConverter
    fun effortLevelToString(value: EffortLevel?): String? = value?.name

    /** An unknown level reads as "not rated" rather than making the whole row unreadable. */
    @TypeConverter
    fun stringToEffortLevel(value: String?): EffortLevel? =
        value?.let { name -> EffortLevel.entries.firstOrNull { it.name == name } }

    @TypeConverter
    fun bodySideToString(value: BodySide?): String? = value?.name

    @TypeConverter
    fun stringToBodySide(value: String?): BodySide? = value?.let(BodySide::valueOf)
}
