package com.yokodake.melete.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yokodake.melete.data.dao.TrainingDao
import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.PrescriptionEntity

/**
 * The training record. There is deliberately no destructive-migration fallback: this database is
 * the only copy of the user's training history, so a missing migration must fail loudly in
 * development rather than silently wipe real data on a phone.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        PrescriptionEntity::class,
        ExerciseOccurrenceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(MeleteConverters::class)
abstract class MeleteDatabase : RoomDatabase() {

    abstract fun trainingDao(): TrainingDao

    companion object {
        const val DATABASE_NAME = "melete.db"

        fun build(context: Context): MeleteDatabase =
            Room.databaseBuilder(context, MeleteDatabase::class.java, DATABASE_NAME)
                .build()
    }
}
