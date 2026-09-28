package com.yokodake.melete.data.backup

import com.yokodake.melete.data.model.ActualSetPayload
import com.yokodake.melete.data.model.CircuitStructureSnapshot
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.data.entity.TrackerType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.LocalDate

/**
 * Checks a whole backup before anything is written, and says what is wrong in words.
 *
 * A restore replaces the record, so it must never begin on a file that would fail half-way: every
 * check the database would make — unique ids, the references its foreign keys enforce, the one
 * session per date and ordinal — is made here first, plus the ones it cannot make: that every date
 * is a date and every plan and set payload can actually be read. An empty list means the file is
 * safe to restore.
 *
 * Lineage references that the schema deliberately leaves loose — an occurrence's exercise, a
 * circuit's routine — are not required to resolve, because history is allowed to outlive its
 * templates, and the record itself keeps them that way.
 */
object BackupValidator {

    /**
     * Whether a diary value is the kind of thing its own definition says, and within its range.
     * Checked against the definition the value carries, not the tracker's current one.
     */
    private fun fits(tracker: DiaryValueRecord, value: JsonElement): Boolean {
        val primitive = value as? JsonPrimitive ?: return false
        return when (tracker.type) {
            TrackerType.TEXT -> primitive.isString
            TrackerType.NUMBER -> !primitive.isString && primitive.doubleOrNull != null
            TrackerType.CHECK -> !primitive.isString && primitive.doubleOrNull == 1.0
            TrackerType.SCALE -> {
                val number = primitive.doubleOrNull
                !primitive.isString && number != null && number == number.toInt().toDouble() &&
                    tracker.scaleMin != null && tracker.scaleMax != null &&
                    number.toInt() in tracker.scaleMin..tracker.scaleMax
            }
        }
    }

    fun problems(backup: MeleteBackup): List<String> = buildList {
        if (backup.format != BACKUP_FORMAT) {
            add("This is not a Melete backup (format \"${backup.format}\").")
            return@buildList
        }
        if (backup.formatVersion > BACKUP_FORMAT_VERSION) {
            add(
                "This backup was written by a newer version of Melete (format " +
                    "${backup.formatVersion}; this app reads up to $BACKUP_FORMAT_VERSION)."
            )
            return@buildList
        }
        if (backup.formatVersion < 1) add("Unknown backup format version ${backup.formatVersion}.")

        fun unique(what: String, ids: List<String>) {
            ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.firstOrNull()?.let {
                add("Two $what share the id \"$it\".")
            }
        }
        unique("exercises", backup.exercises.map { it.id })
        unique("variations", backup.variations.map { it.id })
        unique("circuits", backup.routines.map { it.id })
        unique("circuit stations", backup.routines.flatMap { r -> r.entries.map { it.id } })
        unique("modules", backup.modules.map { it.id })
        unique("module entries", backup.modules.flatMap { m -> m.entries.map { it.id } })
        unique("scheduled circuits", backup.circuitInstances.map { it.id })
        unique("scheduled modules", backup.moduleInstances.map { it.id })
        unique("planned exercises", backup.occurrences.map { it.id })
        unique("sessions", backup.sessions.map { it.id })
        unique("logged sets", backup.sets.map { it.id })
        unique("trackers", backup.trackers.map { it.id })
        unique("diary days", backup.diary.map { it.date })
        unique("benchmarks", backup.benchmarks.map { it.id })
        unique("benchmark results", backup.benchmarks.flatMap { b -> b.results.map { it.id } })

        // What the foreign keys will insist on.
        val exercises = backup.exercises.map { it.id }.toSet()
        backup.variations.filter { it.exerciseId !in exercises }.firstOrNull()?.let {
            add("Variation ${it.tag} belongs to an exercise that is not in the file.")
        }
        val occurrences = backup.occurrences.map { it.id }.toSet()
        val sessions = backup.sessions.map { it.id }.toSet()
        backup.sets.firstOrNull { it.occurrenceId !in occurrences }?.let {
            add("A logged set (${it.id}) belongs to a planned exercise that is not in the file.")
        }
        backup.sets.firstOrNull { it.sessionId !in sessions }?.let {
            add("A logged set (${it.id}) belongs to a session that is not in the file.")
        }
        val trackers = backup.trackers.associateBy { it.id }
        backup.diary.firstOrNull { day -> day.values.any { it.tracker !in trackers } }?.let {
            add("The diary for ${it.date} tracks something that is not in the file.")
        }
        backup.diary.firstOrNull { day -> day.values.map { it.tracker }.toSet().size < day.values.size }?.let {
            add("The diary for ${it.date} has two values for the same tracker.")
        }
        backup.diary.forEach { day ->
            day.values.forEach { value ->
                if (!fits(value, value.value)) {
                    add("The diary for ${day.date} has a value for ${value.label} that does not fit it.")
                }
            }
        }
        backup.sessions.groupBy { it.trainingDate to it.ordinal }.values.firstOrNull { it.size > 1 }
            ?.let { add("Two sessions are both number ${it.first().ordinal} on ${it.first().trainingDate}.") }

        // What the database cannot check: that the values mean something.
        fun date(label: String, value: String?) {
            if (value != null && runCatching { LocalDate.parse(value) }.isFailure) {
                add("$label has an unreadable date \"$value\".")
            }
        }
        backup.occurrences.forEach {
            date(it.exerciseNameSnapshot, it.weekStart)
            date(it.exerciseNameSnapshot, it.trainingDate)
        }
        backup.circuitInstances.forEach {
            date(it.routineNameSnapshot, it.weekStart)
            date(it.routineNameSnapshot, it.trainingDate)
        }
        backup.moduleInstances.forEach {
            date(it.moduleNameSnapshot, it.weekStart)
            date(it.moduleNameSnapshot, it.trainingDate)
        }
        backup.sessions.forEach { date("A session", it.trainingDate) }
        backup.sets.forEach { date("A logged set", it.trainingDate) }
        backup.diary.forEach { date("A diary day", it.date) }
        backup.benchmarks.forEach { b -> b.results.forEach { date("A result for ${b.name}", it.date) } }
        backup.benchmarks.firstOrNull { b -> b.results.any { it.value == null && it.valueRight == null } }
            ?.let { add("A result for ${it.name} holds no value.") }

        fun plan(label: String, element: JsonElement?) {
            if (element != null &&
                runCatching { BackupJson.decodeFromJsonElement<PrescriptionPayload>(element) }.isFailure
            ) {
                add("The plan for $label cannot be read.")
            }
        }
        backup.exercises.forEach { plan(it.name, it.defaultPlan) }
        backup.variations.forEach { plan("variation ${it.tag}", it.plan) }
        backup.routines.forEach { r -> r.entries.forEach { plan("${it.exerciseNameSnapshot} in ${r.name}", it.plan) } }
        backup.modules.forEach { m -> m.entries.forEach { plan("an entry of ${m.name}", it.plan) } }
        backup.occurrences.forEach { plan(it.exerciseNameSnapshot, it.plan) }
        backup.sets.firstOrNull {
            runCatching { BackupJson.decodeFromJsonElement<ActualSetPayload>(it.payload) }.isFailure
        }?.let { add("A logged set (${it.id}) cannot be read.") }
        backup.circuitInstances.firstOrNull {
            runCatching { BackupJson.decodeFromJsonElement<CircuitStructureSnapshot>(it.structureSnapshot) }
                .isFailure
        }?.let { add("The scheduled circuit ${it.routineNameSnapshot} has an unreadable snapshot.") }
    }
}
