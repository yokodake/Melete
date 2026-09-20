package com.yokodake.melete.data

import androidx.room.withTransaction
import com.yokodake.melete.data.dao.OccurrenceWithPrescription
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.PrescriptionJson
import com.yokodake.melete.data.model.PrescriptionPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * A planned exercise as the rest of the app sees it: the snapshot that was placed in the week,
 * not a live view of the library entry it came from.
 */
data class PlannedOccurrence(
    val id: String,
    val exerciseId: String,
    val name: String,
    val mode: ExerciseMode,
    val unilateral: Boolean,
    val measurementUnit: String?,
    val trainingDate: LocalDate?,
    val prescription: PrescriptionPayload?,
    /** True when a prescription row exists but its payload could not be read. */
    val prescriptionUnreadable: Boolean,
    val state: OccurrenceState,
    val comment: String?,
    val orderIndex: Int,
    val isSampleData: Boolean,
)

class TrainingRepository(private val database: MeleteDatabase) {

    private val dao = database.trainingDao()

    fun observeWeek(weekStart: LocalDate): Flow<List<PlannedOccurrence>> =
        dao.observeWeek(weekStart.toEpochDay()).map { rows -> rows.map { it.toPlanned() } }

    val sampleDataPresent: Flow<Boolean> =
        dao.observeSampleOccurrenceCount().map { it > 0 }

    /**
     * Inserts explicitly marked development data for [weekStart]. Only ever called from an
     * explicit debug action: sample rows are never inserted on launch and never mixed into real
     * records without the flag that makes them removable again.
     */
    suspend fun seedSampleWeek(weekStart: LocalDate) {
        val sample = DevSampleData.build(weekStart)
        database.withTransaction {
            dao.insertPrescriptions(sample.prescriptions)
            dao.insertExercises(sample.exercises)
            dao.insertOccurrences(sample.occurrences)
        }
    }

    suspend fun clearSampleData() {
        database.withTransaction {
            // Occurrences first: they reference prescriptions with ON DELETE RESTRICT.
            dao.deleteSampleOccurrences()
            dao.deleteSampleExercises()
            dao.deleteSamplePrescriptions()
        }
    }
}

private fun OccurrenceWithPrescription.toPlanned(): PlannedOccurrence {
    val payload: PrescriptionPayload? = prescription?.let {
        runCatching { PrescriptionJson.decode(it.payloadJson) }.getOrNull()
    }
    return PlannedOccurrence(
        id = occurrence.id,
        exerciseId = occurrence.exerciseId,
        name = occurrence.exerciseNameSnapshot,
        mode = occurrence.modeSnapshot,
        unilateral = occurrence.unilateralSnapshot,
        measurementUnit = occurrence.measurementUnitSnapshot,
        trainingDate = occurrence.trainingDateEpochDay?.let(LocalDate::ofEpochDay),
        prescription = payload,
        prescriptionUnreadable = prescription != null && payload == null,
        state = occurrence.state,
        comment = occurrence.comment,
        orderIndex = occurrence.orderIndex,
        isSampleData = occurrence.isSampleData,
    )
}
