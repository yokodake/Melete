package com.yokodake.melete.data

import com.yokodake.melete.data.backup.BACKUP_FORMAT_VERSION
import com.yokodake.melete.data.backup.BackupJson
import com.yokodake.melete.data.backup.BackupValidator
import com.yokodake.melete.data.backup.DiaryRecord
import com.yokodake.melete.data.backup.DiaryValueRecord
import com.yokodake.melete.data.backup.MeleteBackup
import com.yokodake.melete.data.backup.SessionRecord
import com.yokodake.melete.data.backup.TrackerRecord
import com.yokodake.melete.data.backup.entryEntities
import com.yokodake.melete.data.backup.toEntity
import com.yokodake.melete.data.backup.toRecord
import com.yokodake.melete.data.backup.valueEntities
import com.yokodake.melete.data.entity.ActualSetEntity
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.CircuitInstanceEntity
import com.yokodake.melete.data.entity.DiaryEntryEntity
import com.yokodake.melete.data.entity.DiaryValueEntity
import com.yokodake.melete.data.entity.ExerciseEntity
import com.yokodake.melete.data.entity.ExerciseOccurrenceEntity
import com.yokodake.melete.data.entity.ExerciseVariationEntity
import com.yokodake.melete.data.entity.ModuleEntity
import com.yokodake.melete.data.entity.ModuleEntryEntity
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.entity.RoutineEntity
import com.yokodake.melete.data.entity.RoutineEntryEntity
import com.yokodake.melete.data.entity.TrackerEntity
import com.yokodake.melete.data.entity.TrackerType
import com.yokodake.melete.data.entity.TrainingSessionEntity
import com.yokodake.melete.data.model.ActualSetJson
import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.CircuitSnapshotJson
import com.yokodake.melete.data.model.CircuitStructureSnapshot
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionJson
import com.yokodake.melete.data.model.PrescriptionPayload
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The backup file, without a device: every kind of row survives the round trip unchanged, and a
 * file that would fail half-way through a restore is refused before one starts.
 */
class BackupFormatTest {

    private val monday = LocalDate.of(2026, 9, 21).toEpochDay()
    private val plan = PrescriptionJson.encode(
        PrescriptionPayload(sets = 3, targetReps = 5, restSeconds = 180, effort = EffortLevel.HARD)
    )

    private val exercise = ExerciseEntity(
        id = "ex-1", name = "Back squat", mode = ExerciseMode.REPETITIONS, measurementUnit = "kg",
        measurementMeaning = MeasurementMeaning.TOTAL_LOAD, unilateral = false, notes = "Belt",
        defaultPrescriptionJson = plan, createdAtEpochMs = 1_000,
        description = "Below parallel.", category = ExerciseCategory.STRENGTH_CONDITIONING,
        // Retired: a tombstone that history hangs from must come back as a tombstone.
        deletedAtEpochMs = 9_000,
    )

    private val occurrence = ExerciseOccurrenceEntity(
        id = "occ-1", weekStartEpochDay = monday, trainingDateEpochDay = monday + 1,
        exerciseId = "ex-1", exerciseNameSnapshot = "Back squat",
        modeSnapshot = ExerciseMode.REPETITIONS, unilateralSnapshot = true,
        measurementUnitSnapshot = "kg", measurementMeaningSnapshot = MeasurementMeaning.ADDED_LOAD,
        prescriptionJson = plan, orderIndex = 2, state = OccurrenceState.COMPLETED,
        comment = "Felt good", createdAtEpochMs = 2_000,
        categorySnapshot = ExerciseCategory.STRENGTH_CONDITIONING, loggedDurationSeconds = 1_500,
        loggedDurationManual = true, loggedEffort = EffortLevel.VERY_HARD, isOneOff = false,
        circuitInstanceId = "circ-1", circuitPosition = 1, variationId = "var-1",
        variationTagSnapshot = "PWR", moduleInstanceId = "mod-i-1", modulePosition = 0,
        optional = true,
    )

    private val set = ActualSetEntity(
        id = "set-1", occurrenceId = "occ-1", sessionId = "ses-1", exerciseId = "ex-1",
        trainingDateEpochDay = monday + 1, orderIndex = 0, side = BodySide.LEFT,
        payloadVersion = 2,
        payloadJson = ActualSetJson.encode(
            ActualSetPayload(reps = 5, measurement = Measurement(40.0, "kg", MeasurementMeaning.ADDED_LOAD))
        ),
        recordedAtEpochMs = 3_000,
    )

    @Test
    fun `every kind of row comes back exactly as it went out`() {
        assertEquals(exercise, exercise.toRecord().toEntity())
        assertEquals(occurrence, occurrence.toRecord().toEntity())
        assertEquals(set, set.toRecord().toEntity())

        val variation = ExerciseVariationEntity("var-1", "ex-1", "PWR", "Fast", plan, 0, 1_500, 8_000)
        assertEquals(variation, variation.toRecord().toEntity())

        val routine = RoutineEntity(
            id = "r-1", name = "Pull", rounds = 3, transitionSeconds = 30, roundRestSeconds = 120,
            structureVersion = 2, createdAtEpochMs = 1_000, category = ExerciseCategory.FINGER_TRAINING,
        )
        val entries = listOf(RoutineEntryEntity("re-1", "r-1", 0, "ex-1", "Back squat", plan))
        val routineRecord = routine.toRecord(entries)
        assertEquals(routine, routineRecord.toEntity())
        assertEquals(entries, routineRecord.entryEntities())

        val module = ModuleEntity("m-1", "Fingers", "Base block", 1_000, null)
        val moduleEntries = listOf(
            ModuleEntryEntity("me-1", "m-1", 0, exerciseId = "ex-1", exerciseNameSnapshot = "Back squat", prescriptionJson = plan, optional = true),
            ModuleEntryEntity("me-2", "m-1", 1, routineId = "r-1", routineNameSnapshot = "Pull"),
        )
        val moduleRecord = module.toRecord(moduleEntries)
        assertEquals(module, moduleRecord.toEntity())
        assertEquals(moduleEntries, moduleRecord.entryEntities())

        val circuit = CircuitInstanceEntity(
            id = "circ-1", routineId = "r-1", routineNameSnapshot = "Pull", structureVersion = 2,
            structureSnapshotJson = CircuitSnapshotJson.encode(
                CircuitStructureSnapshot(
                    routineId = "r-1", routineName = "Pull", structureVersion = 2, rounds = 3,
                    transitionSeconds = 30, roundRestSeconds = 120, entries = emptyList(),
                )
            ),
            weekStartEpochDay = monday, trainingDateEpochDay = null, orderIndex = 0, rounds = 3,
            transitionSeconds = 30, roundRestSeconds = 120, createdAtEpochMs = 1_000,
            moduleInstanceId = "mod-i-1", modulePosition = 1,
            categorySnapshot = ExerciseCategory.FINGER_TRAINING,
        )
        assertEquals(circuit, circuit.toRecord().toEntity())

        val session = TrainingSessionEntity("ses-1", monday + 1, 0, 3_000)
        assertEquals(session, session.toRecord().toEntity())

        val diary = DiaryEntryEntity(monday, "Tired", 4_000)
        // One of each kind: a scale, a number, a checkmark and a comment.
        // Each carries the tracker as it was that day.
        val values = listOf(
            DiaryValueEntity(monday, "energy", "Energy", TrackerType.SCALE, 0, 5, number = 2.0),
            DiaryValueEntity(monday, "skin", "Skin", TrackerType.TEXT, text = "Split on the left index"),
            DiaryValueEntity(monday, "stretched", "Stretched", TrackerType.CHECK, number = 1.0),
            DiaryValueEntity(monday, "weight", "Weight", TrackerType.NUMBER, unitSnapshot = "kg", number = 72.5),
        )
        val diaryRecord = diary.toRecord(values)
        assertEquals(diary, diaryRecord.toEntity())
        assertEquals(values, diaryRecord.valueEntities())

        val tracker = TrackerEntity("weight", "Weight", TrackerType.NUMBER, null, null, "kg", 1, 5_000, 6_000)
        assertEquals(tracker, tracker.toRecord().toEntity())
    }

    @Test
    fun `the file is readable and survives being written and read back`() {
        val backup = MeleteBackup(
            exportedAt = "2026-09-26T10:00:00Z",
            schemaVersion = 8,
            exercises = listOf(exercise.toRecord()),
            occurrences = listOf(occurrence.toRecord()),
        )
        val text = BackupJson.encodeToString(MeleteBackup.serializer(), backup)
        // Dates as dates, enums by name, the plan as an object rather than an escaped string.
        assertTrue(text.contains("\"trainingDate\": \"2026-09-22\""))
        assertTrue(text.contains("\"mode\": \"REPETITIONS\""))
        assertTrue(text.contains("\"targetReps\": 5"))
        assertEquals(backup, BackupJson.decodeFromString(MeleteBackup.serializer(), text))
    }

    // ------------------------------------------------------------------ validation

    private val valid = MeleteBackup(
        exportedAt = "2026-09-26T10:00:00Z",
        schemaVersion = 8,
        exercises = listOf(exercise.toRecord()),
        occurrences = listOf(occurrence.toRecord()),
        sessions = listOf(SessionRecord("ses-1", "2026-09-22", 0, 3_000)),
        sets = listOf(set.toRecord()),
        trackers = listOf(
            TrackerRecord("energy", "Energy", TrackerType.SCALE, 1, 10, orderIndex = 0, createdAtEpochMs = 0),
            TrackerRecord("weight", "Weight", TrackerType.NUMBER, unit = "kg", orderIndex = 1, createdAtEpochMs = 0),
            TrackerRecord("stretched", "Stretched", TrackerType.CHECK, orderIndex = 2, createdAtEpochMs = 0),
            TrackerRecord("skin", "Skin", TrackerType.TEXT, orderIndex = 3, createdAtEpochMs = 0),
        ),
        diary = listOf(
            DiaryRecord(
                "2026-09-21", "Tired", 4_000,
                listOf(
                    // Recorded on a 0–5 scale, though Energy is 1–10 now: the day's own
                    // definition is what the value is checked against.
                    DiaryValueRecord("energy", "Energy", TrackerType.SCALE, 0, 5, value = JsonPrimitive(0)),
                    DiaryValueRecord("weight", "Weight", TrackerType.NUMBER, unit = "kg", value = JsonPrimitive(72.5)),
                    DiaryValueRecord("stretched", "Stretched", TrackerType.CHECK, value = JsonPrimitive(1)),
                    DiaryValueRecord("skin", "Skin", TrackerType.TEXT, value = JsonPrimitive("Sore")),
                ),
            )
        ),
    )

    @Test
    fun `a sound file has no problems`() {
        assertEquals(emptyList<String>(), BackupValidator.problems(valid))
    }

    @Test
    fun `a file from something else, or from a newer app, is refused outright`() {
        assertTrue(BackupValidator.problems(valid.copy(format = "other")).single().contains("not a Melete backup"))
        assertTrue(
            BackupValidator.problems(valid.copy(formatVersion = BACKUP_FORMAT_VERSION + 1))
                .single().contains("newer version")
        )
    }

    @Test
    fun `everything the foreign keys would reject is caught first`() {
        assertTrue(BackupValidator.problems(valid.copy(sessions = emptyList())).any { "session" in it })
        assertTrue(BackupValidator.problems(valid.copy(occurrences = emptyList())).any { "planned exercise" in it })
        assertTrue(BackupValidator.problems(valid.copy(trackers = emptyList())).any { "tracks something" in it })
        assertTrue(
            BackupValidator.problems(valid.copy(exercises = valid.exercises + valid.exercises))
                .any { "share the id" in it }
        )
    }

    @Test
    fun `values that cannot mean anything are caught too`() {
        val badDate = valid.copy(occurrences = listOf(occurrence.toRecord().copy(weekStart = "last monday")))
        assertTrue(BackupValidator.problems(badDate).any { "unreadable date" in it })

        fun day(value: JsonPrimitive, id: String = "energy", type: TrackerType = TrackerType.SCALE) =
            valid.copy(
                diary = listOf(
                    DiaryRecord(
                        "2026-09-21", null, 4_000,
                        listOf(DiaryValueRecord(id, id, type, 0, 5, value = value)),
                    )
                )
            )
        val misfits = listOf(
            day(JsonPrimitive(9)),      // off the day's scale, even though today's goes to 10
            day(JsonPrimitive(2.5)),    // between steps
            day(JsonPrimitive("heavy"), "weight", TrackerType.NUMBER),
            day(JsonPrimitive(0), "stretched", TrackerType.CHECK),   // a checkmark is only ever done
            day(JsonPrimitive(3), "skin", TrackerType.TEXT),
        )
        misfits.forEach { assertTrue(BackupValidator.problems(it).any { p -> "does not fit" in p }) }

        val badPlan = valid.copy(
            occurrences = listOf(
                occurrence.toRecord().copy(plan = BackupJson.parseToJsonElement("{\"sets\": \"many\"}"))
            )
        )
        assertTrue(BackupValidator.problems(badPlan).any { "cannot be read" in it })
    }
}
