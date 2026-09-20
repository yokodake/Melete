package com.yokodake.melete.data

import androidx.room.TypeConverter
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseMode

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
}
