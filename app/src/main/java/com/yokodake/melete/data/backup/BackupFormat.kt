package com.yokodake.melete.data.backup

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
import com.yokodake.melete.data.entity.ModuleInstanceEntity
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.entity.RoutineEntity
import com.yokodake.melete.data.entity.RoutineEntryEntity
import com.yokodake.melete.data.entity.TrackerEntity
import com.yokodake.melete.data.entity.TrackerType
import com.yokodake.melete.data.entity.TrainingSessionEntity
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate

/** What a backup file says it is, so a file of anything else is recognised before it is read. */
const val BACKUP_FORMAT = "melete-backup"

/**
 * The version of the file layout. A file with a higher number was written by a newer app and is
 * refused rather than half-read; a lower one is read by the rules of its version.
 */
const val BACKUP_FORMAT_VERSION = 1

/**
 * The whole training record, as one human-readable file.
 *
 * Every table, with its stable ids, so a restore puts back exactly what was there — identities,
 * relationships, tombstoned definitions still anchoring history, and everything snapshotted onto
 * the week. Dates are written `2026-09-21`; plans, set payloads and circuit snapshots are embedded
 * as the JSON objects they are stored as, fields a newer build added included. The running timer
 * is not training history and lives outside the database, so it is never in here.
 */
@Serializable
data class MeleteBackup(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    /** When it was written, ISO-8601. Informational only. */
    val exportedAt: String,
    /** The database schema it was written from. Informational only. */
    val schemaVersion: Int,
    val exercises: List<ExerciseRecord> = emptyList(),
    val variations: List<VariationRecord> = emptyList(),
    val routines: List<RoutineRecord> = emptyList(),
    val modules: List<ModuleRecord> = emptyList(),
    val circuitInstances: List<CircuitInstanceRecord> = emptyList(),
    val moduleInstances: List<ModuleInstanceRecord> = emptyList(),
    val occurrences: List<OccurrenceRecord> = emptyList(),
    val sessions: List<SessionRecord> = emptyList(),
    val sets: List<SetRecord> = emptyList(),
    val trackers: List<TrackerRecord> = emptyList(),
    val diary: List<DiaryRecord> = emptyList(),
)

@Serializable
data class ExerciseRecord(
    val id: String,
    val name: String,
    val mode: ExerciseMode,
    val unilateral: Boolean,
    val measurementUnit: String? = null,
    val measurementMeaning: MeasurementMeaning? = null,
    val notes: String? = null,
    val description: String? = null,
    val category: ExerciseCategory? = null,
    val defaultPlan: JsonElement? = null,
    val createdAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
)

@Serializable
data class VariationRecord(
    val id: String,
    val exerciseId: String,
    val tag: String,
    val notes: String? = null,
    val plan: JsonElement? = null,
    val orderIndex: Int,
    val createdAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
)

@Serializable
data class RoutineRecord(
    val id: String,
    val name: String,
    val category: ExerciseCategory? = null,
    val rounds: Int,
    val transitionSeconds: Int,
    val roundRestSeconds: Int,
    val structureVersion: Int,
    val createdAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val entries: List<RoutineEntryRecord> = emptyList(),
)

@Serializable
data class RoutineEntryRecord(
    val id: String,
    val orderIndex: Int,
    val exerciseId: String,
    val exerciseNameSnapshot: String,
    val plan: JsonElement? = null,
)

@Serializable
data class ModuleRecord(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val entries: List<ModuleEntryRecord> = emptyList(),
)

@Serializable
data class ModuleEntryRecord(
    val id: String,
    val orderIndex: Int,
    val exerciseId: String? = null,
    val exerciseNameSnapshot: String? = null,
    val variationId: String? = null,
    val variationTagSnapshot: String? = null,
    val plan: JsonElement? = null,
    val routineId: String? = null,
    val routineNameSnapshot: String? = null,
)

@Serializable
data class CircuitInstanceRecord(
    val id: String,
    val routineId: String,
    val routineNameSnapshot: String,
    val categorySnapshot: ExerciseCategory? = null,
    val structureVersion: Int,
    val structureSnapshot: JsonElement,
    val weekStart: String,
    val trainingDate: String? = null,
    val orderIndex: Int,
    val rounds: Int,
    val transitionSeconds: Int,
    val roundRestSeconds: Int,
    val createdAtEpochMs: Long,
    val moduleInstanceId: String? = null,
    val modulePosition: Int? = null,
)

@Serializable
data class ModuleInstanceRecord(
    val id: String,
    val moduleId: String,
    val moduleNameSnapshot: String,
    val weekStart: String,
    val trainingDate: String? = null,
    val orderIndex: Int,
    val createdAtEpochMs: Long,
)

@Serializable
data class OccurrenceRecord(
    val id: String,
    val weekStart: String,
    val trainingDate: String? = null,
    val exerciseId: String,
    val exerciseNameSnapshot: String,
    val modeSnapshot: ExerciseMode,
    val unilateralSnapshot: Boolean,
    val measurementUnitSnapshot: String? = null,
    val measurementMeaningSnapshot: MeasurementMeaning? = null,
    val categorySnapshot: ExerciseCategory? = null,
    val plan: JsonElement? = null,
    val orderIndex: Int,
    val state: OccurrenceState,
    val comment: String? = null,
    val createdAtEpochMs: Long,
    val loggedDurationSeconds: Int? = null,
    /** Whether the logged duration was typed (true) or worked out (false). */
    val loggedDurationManual: Boolean = false,
    val loggedEffort: EffortLevel? = null,
    val isOneOff: Boolean = false,
    val circuitInstanceId: String? = null,
    val circuitPosition: Int? = null,
    val variationId: String? = null,
    val variationTagSnapshot: String? = null,
    val moduleInstanceId: String? = null,
    val modulePosition: Int? = null,
)

@Serializable
data class SessionRecord(
    val id: String,
    val trainingDate: String,
    val ordinal: Int,
    val createdAtEpochMs: Long,
)

@Serializable
data class SetRecord(
    val id: String,
    val occurrenceId: String,
    val sessionId: String,
    val exerciseId: String,
    val trainingDate: String,
    val orderIndex: Int,
    val side: BodySide? = null,
    val payloadVersion: Int,
    val payload: JsonElement,
    val recordedAtEpochMs: Long,
)

@Serializable
data class TrackerRecord(
    val id: String,
    val label: String,
    val type: TrackerType,
    val scaleMin: Int? = null,
    val scaleMax: Int? = null,
    val unit: String? = null,
    val orderIndex: Int,
    val createdAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
)

@Serializable
data class DiaryRecord(
    val date: String,
    val text: String? = null,
    val updatedAtEpochMs: Long,
    val values: List<DiaryValueRecord> = emptyList(),
)

/**
 * One tracked value, with the tracker as it was on that day — the definition the value means
 * something against, which may no longer be the tracker's current one.
 */
@Serializable
data class DiaryValueRecord(
    val tracker: String,
    val label: String,
    val type: TrackerType,
    val scaleMin: Int? = null,
    val scaleMax: Int? = null,
    val unit: String? = null,
    /** A number for a scale or a number, 1 for a checkmark, a string for a comment. */
    val value: JsonElement,
)

/** The file's JSON: indented for reading, tolerant of fields a newer app added. */
internal val BackupJson = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

// ------------------------------------------------------------------ rows → records

private fun String?.element(): JsonElement? = this?.let { BackupJson.parseToJsonElement(it) }
private fun Long.date(): String = LocalDate.ofEpochDay(this).toString()

internal fun ExerciseEntity.toRecord() = ExerciseRecord(
    id, name, mode, unilateral, measurementUnit, measurementMeaning, notes, description, category,
    defaultPrescriptionJson.element(), createdAtEpochMs, deletedAtEpochMs,
)

internal fun ExerciseVariationEntity.toRecord() = VariationRecord(
    id, exerciseId, tag, notes, prescriptionJson.element(), orderIndex, createdAtEpochMs,
    deletedAtEpochMs,
)

internal fun RoutineEntity.toRecord(entries: List<RoutineEntryEntity>) = RoutineRecord(
    id, name, category, rounds, transitionSeconds, roundRestSeconds, structureVersion,
    createdAtEpochMs, deletedAtEpochMs,
    entries = entries.sortedBy { it.orderIndex }.map {
        RoutineEntryRecord(it.id, it.orderIndex, it.exerciseId, it.exerciseNameSnapshot, it.prescriptionJson.element())
    },
)

internal fun ModuleEntity.toRecord(entries: List<ModuleEntryEntity>) = ModuleRecord(
    id, name, description, createdAtEpochMs, deletedAtEpochMs,
    entries = entries.sortedBy { it.orderIndex }.map {
        ModuleEntryRecord(
            it.id, it.orderIndex, it.exerciseId, it.exerciseNameSnapshot, it.variationId,
            it.variationTagSnapshot, it.prescriptionJson.element(), it.routineId, it.routineNameSnapshot,
        )
    },
)

internal fun CircuitInstanceEntity.toRecord() = CircuitInstanceRecord(
    id, routineId, routineNameSnapshot, categorySnapshot, structureVersion,
    BackupJson.parseToJsonElement(structureSnapshotJson), weekStartEpochDay.date(),
    trainingDateEpochDay?.date(), orderIndex, rounds, transitionSeconds, roundRestSeconds,
    createdAtEpochMs, moduleInstanceId, modulePosition,
)

internal fun ModuleInstanceEntity.toRecord() = ModuleInstanceRecord(
    id, moduleId, moduleNameSnapshot, weekStartEpochDay.date(), trainingDateEpochDay?.date(),
    orderIndex, createdAtEpochMs,
)

internal fun ExerciseOccurrenceEntity.toRecord() = OccurrenceRecord(
    id = id,
    weekStart = weekStartEpochDay.date(),
    trainingDate = trainingDateEpochDay?.date(),
    exerciseId = exerciseId,
    exerciseNameSnapshot = exerciseNameSnapshot,
    modeSnapshot = modeSnapshot,
    unilateralSnapshot = unilateralSnapshot,
    measurementUnitSnapshot = measurementUnitSnapshot,
    measurementMeaningSnapshot = measurementMeaningSnapshot,
    categorySnapshot = categorySnapshot,
    plan = prescriptionJson.element(),
    orderIndex = orderIndex,
    state = state,
    comment = comment,
    createdAtEpochMs = createdAtEpochMs,
    loggedDurationSeconds = loggedDurationSeconds,
    loggedDurationManual = loggedDurationManual,
    loggedEffort = loggedEffort,
    isOneOff = isOneOff,
    circuitInstanceId = circuitInstanceId,
    circuitPosition = circuitPosition,
    variationId = variationId,
    variationTagSnapshot = variationTagSnapshot,
    moduleInstanceId = moduleInstanceId,
    modulePosition = modulePosition,
)

internal fun TrainingSessionEntity.toRecord() =
    SessionRecord(id, trainingDateEpochDay.date(), ordinal, createdAtEpochMs)

internal fun ActualSetEntity.toRecord() = SetRecord(
    id, occurrenceId, sessionId, exerciseId, trainingDateEpochDay.date(), orderIndex, side,
    payloadVersion, BackupJson.parseToJsonElement(payloadJson), recordedAtEpochMs,
)

internal fun TrackerEntity.toRecord() = TrackerRecord(
    id, label, type, scaleMin, scaleMax, unit, orderIndex, createdAtEpochMs, deletedAtEpochMs,
)

internal fun DiaryEntryEntity.toRecord(values: List<DiaryValueEntity>) = DiaryRecord(
    dateEpochDay.date(), text, updatedAtEpochMs,
    values.sortedBy { it.trackerId }.map { value ->
        DiaryValueRecord(
            tracker = value.trackerId,
            label = value.labelSnapshot,
            type = value.typeSnapshot,
            scaleMin = value.scaleMinSnapshot,
            scaleMax = value.scaleMaxSnapshot,
            unit = value.unitSnapshot,
            value = value.text?.let(::JsonPrimitive) ?: JsonPrimitive(value.number),
        )
    },
)

// ------------------------------------------------------------------ records → rows

/** Back to the compact text the app stores, not the file's indented layout. */
private fun JsonElement?.text(): String? = this?.let { Json.encodeToString(JsonElement.serializer(), it) }
private fun String.epochDay(): Long = LocalDate.parse(this).toEpochDay()

internal fun ExerciseRecord.toEntity() = ExerciseEntity(
    id = id, name = name, mode = mode, measurementUnit = measurementUnit,
    measurementMeaning = measurementMeaning, unilateral = unilateral, notes = notes,
    defaultPrescriptionJson = defaultPlan.text(), createdAtEpochMs = createdAtEpochMs,
    description = description, category = category, deletedAtEpochMs = deletedAtEpochMs,
)

internal fun VariationRecord.toEntity() = ExerciseVariationEntity(
    id, exerciseId, tag, notes, plan.text(), orderIndex, createdAtEpochMs, deletedAtEpochMs,
)

internal fun RoutineRecord.toEntity() = RoutineEntity(
    id = id, name = name, rounds = rounds, transitionSeconds = transitionSeconds,
    roundRestSeconds = roundRestSeconds, structureVersion = structureVersion,
    createdAtEpochMs = createdAtEpochMs, deletedAtEpochMs = deletedAtEpochMs, category = category,
)

internal fun RoutineRecord.entryEntities() = entries.map {
    RoutineEntryEntity(it.id, id, it.orderIndex, it.exerciseId, it.exerciseNameSnapshot, it.plan.text())
}

internal fun ModuleRecord.toEntity() = ModuleEntity(id, name, description, createdAtEpochMs, deletedAtEpochMs)

internal fun ModuleRecord.entryEntities() = entries.map {
    ModuleEntryEntity(
        it.id, id, it.orderIndex, it.exerciseId, it.exerciseNameSnapshot, it.variationId,
        it.variationTagSnapshot, it.plan.text(), it.routineId, it.routineNameSnapshot,
    )
}

internal fun CircuitInstanceRecord.toEntity() = CircuitInstanceEntity(
    id = id, routineId = routineId, routineNameSnapshot = routineNameSnapshot,
    structureVersion = structureVersion, structureSnapshotJson = structureSnapshot.text()!!,
    weekStartEpochDay = weekStart.epochDay(), trainingDateEpochDay = trainingDate?.epochDay(),
    orderIndex = orderIndex, rounds = rounds, transitionSeconds = transitionSeconds,
    roundRestSeconds = roundRestSeconds, createdAtEpochMs = createdAtEpochMs,
    moduleInstanceId = moduleInstanceId, modulePosition = modulePosition,
    categorySnapshot = categorySnapshot,
)

internal fun ModuleInstanceRecord.toEntity() = ModuleInstanceEntity(
    id, moduleId, moduleNameSnapshot, weekStart.epochDay(), trainingDate?.epochDay(), orderIndex,
    createdAtEpochMs,
)

internal fun OccurrenceRecord.toEntity() = ExerciseOccurrenceEntity(
    id = id,
    weekStartEpochDay = weekStart.epochDay(),
    trainingDateEpochDay = trainingDate?.epochDay(),
    exerciseId = exerciseId,
    exerciseNameSnapshot = exerciseNameSnapshot,
    modeSnapshot = modeSnapshot,
    unilateralSnapshot = unilateralSnapshot,
    measurementUnitSnapshot = measurementUnitSnapshot,
    measurementMeaningSnapshot = measurementMeaningSnapshot,
    prescriptionJson = plan.text(),
    orderIndex = orderIndex,
    state = state,
    comment = comment,
    createdAtEpochMs = createdAtEpochMs,
    categorySnapshot = categorySnapshot,
    loggedDurationSeconds = loggedDurationSeconds,
    loggedDurationManual = loggedDurationManual,
    loggedEffort = loggedEffort,
    isOneOff = isOneOff,
    circuitInstanceId = circuitInstanceId,
    circuitPosition = circuitPosition,
    variationId = variationId,
    variationTagSnapshot = variationTagSnapshot,
    moduleInstanceId = moduleInstanceId,
    modulePosition = modulePosition,
)

internal fun SessionRecord.toEntity() =
    TrainingSessionEntity(id, trainingDate.epochDay(), ordinal, createdAtEpochMs)

internal fun SetRecord.toEntity() = ActualSetEntity(
    id = id, occurrenceId = occurrenceId, sessionId = sessionId, exerciseId = exerciseId,
    trainingDateEpochDay = trainingDate.epochDay(), orderIndex = orderIndex, side = side,
    payloadVersion = payloadVersion, payloadJson = payload.text()!!,
    recordedAtEpochMs = recordedAtEpochMs,
)

internal fun TrackerRecord.toEntity() = TrackerEntity(
    id, label, type, scaleMin, scaleMax, unit, orderIndex, createdAtEpochMs, deletedAtEpochMs,
)

internal fun DiaryRecord.toEntity() = DiaryEntryEntity(date.epochDay(), text, updatedAtEpochMs)

internal fun DiaryRecord.valueEntities() = values.map { record ->
    val primitive = record.value as? JsonPrimitive
    val isText = primitive != null && primitive.isString
    DiaryValueEntity(
        dateEpochDay = date.epochDay(),
        trackerId = record.tracker,
        labelSnapshot = record.label,
        typeSnapshot = record.type,
        scaleMinSnapshot = record.scaleMin,
        scaleMaxSnapshot = record.scaleMax,
        unitSnapshot = record.unit,
        number = if (isText) null else primitive?.doubleOrNull,
        text = if (isText) primitive.content else null,
    )
}
