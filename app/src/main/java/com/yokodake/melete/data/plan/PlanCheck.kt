package com.yokodake.melete.data.plan

import com.yokodake.melete.data.ExerciseDraft
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.data.model.RepeaterPrescription
import com.yokodake.melete.data.model.VariationTag
import java.time.DayOfWeek
import java.time.LocalDate

/** A name as it is matched: case, and runs of spaces, do not make two things different. */
internal fun nameKey(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

/** What the library holds already, by name — the other side of every reference in a file. */
data class LibraryIndex(
    val exercises: Map<String, IndexedExercise> = emptyMap(),
    val circuits: Map<String, String> = emptyMap(),
    val modules: Map<String, String> = emptyMap(),
    /** Circuit and module names as written, by key, for saying what replacing would remove. */
    val circuitNames: Map<String, String> = emptyMap(),
    val moduleNames: Map<String, String> = emptyMap(),
    /** Names two live library items share, which a file therefore cannot point at. */
    val ambiguous: Set<String> = emptySet(),
) {
    companion object {
        val EMPTY = LibraryIndex()
    }
}

data class IndexedExercise(
    val id: String,
    val name: String,
    val mode: ExerciseMode,
    val unilateral: Boolean,
    /** Live variations only, tag to id. */
    val variations: Map<String, String> = emptyMap(),
)

// ------------------------------------------------------------------ the resolved plan

data class ResolvedVariation(val tag: String, val notes: String?, val plan: PrescriptionPayload)

data class ResolvedExercise(
    val key: String,
    /** The library entry this definition replaces, or null for a new one. */
    val existingId: String?,
    val draft: ExerciseDraft,
    /** Null leaves the library's variations untouched. */
    val variations: List<ResolvedVariation>?,
)

/** An exercise placed somewhere, by the name it resolves to, with the plan it takes there. */
data class ResolvedPick(val exerciseKey: String, val variationTag: String?, val plan: PrescriptionPayload?)

data class ResolvedCircuit(
    val key: String,
    val existingId: String?,
    val name: String,
    val category: ExerciseCategory?,
    val rounds: Int,
    val transitionSeconds: Int,
    val roundRestSeconds: Int,
    val stations: List<ResolvedPick>,
)

sealed interface ResolvedEntry {
    data class Exercise(val pick: ResolvedPick) : ResolvedEntry
    data class Circuit(val key: String) : ResolvedEntry
}

data class ResolvedModule(
    val key: String,
    val existingId: String?,
    val name: String,
    val description: String?,
    val entries: List<ResolvedEntry>,
)

sealed interface ResolvedItem {
    data class Exercise(val pick: ResolvedPick) : ResolvedItem
    data class Circuit(val key: String) : ResolvedItem
    data class Module(val key: String) : ResolvedItem
    data class Activity(val name: String, val minutes: Int?) : ResolvedItem
}

/** One slot of one week: a day, or the week's undated area when [date] is null. */
data class ResolvedSlot(val weekStart: LocalDate, val date: LocalDate?, val items: List<ResolvedItem>)

data class ResolvedPlan(
    val exercises: List<ResolvedExercise>,
    val circuits: List<ResolvedCircuit>,
    val modules: List<ResolvedModule>,
    val slots: List<ResolvedSlot>,
)

/** What an import would do, in the terms the preview shows. */
data class PlanPreview(
    val exercisesAdded: List<String> = emptyList(),
    val exercisesUpdated: List<String> = emptyList(),
    val variations: Int = 0,
    val circuitsAdded: List<String> = emptyList(),
    val circuitsUpdated: List<String> = emptyList(),
    val modulesAdded: List<String> = emptyList(),
    val modulesUpdated: List<String> = emptyList(),
    val weeks: Int = 0,
    val planned: Int = 0,
    /** Items dated before the cut-off, left out because the import is from today on. */
    val skippedPast: Int = 0,
)

/**
 * The verdict on a file: [problems] refuse it, [warnings] are what will be left out and why, and
 * [plan] — present only when there are no problems — is what the importer writes.
 */
data class PlanResolution(
    val problems: List<String>,
    val warnings: List<String>,
    val preview: PlanPreview,
    val plan: ResolvedPlan?,
)

/**
 * Reads a plan file against the library, and either says in words everything wrong with it or
 * produces the exact plan to write. Pure: nothing here touches the database, so a file is judged
 * whole before a single row changes.
 */
object PlanCheck {

    /**
     * @param referencesFromLibrary whether a name the file does not define may resolve to the
     *   library. False when replacing: the library is about to become the file's, so the file has
     *   to stand on its own. Definitions still match library items by name either way, so an
     *   exercise that is kept keeps its id and its history.
     * @param cutoff the first day planned; items dated earlier (and the undated area of weeks
     *   ending earlier) are left out and counted. Null plans everything, past included.
     */
    fun resolve(
        file: PlanFile,
        library: LibraryIndex,
        referencesFromLibrary: Boolean = true,
        cutoff: LocalDate? = null,
    ): PlanResolution {
        val problems = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (file.format != PLAN_FORMAT) {
            problems += if (file.format == null) {
                "The file does not say what it is: add \"format\": \"$PLAN_FORMAT\"."
            } else {
                "This is not a Melete plan (format \"${file.format}\")."
            }
            return PlanResolution(problems, warnings, PlanPreview(), null)
        }
        when {
            file.version == null -> problems += "The file has no \"version\"; this app reads version $PLAN_FORMAT_VERSION."
            file.version > PLAN_FORMAT_VERSION ->
                problems += "This plan is version ${file.version}; this app reads up to $PLAN_FORMAT_VERSION."
            file.version < 1 -> problems += "Unknown plan version ${file.version}."
        }

        fun duplicates(what: String, names: List<String?>) {
            names.filterNotNull().groupBy(::nameKey).values.firstOrNull { it.size > 1 }?.let {
                problems += "Two $what are called \"${it.first().trim()}\"."
            }
        }
        duplicates("exercises", file.exercises.map { it.name })
        duplicates("circuits", file.circuits.map { it.name })
        duplicates("modules", file.modules.map { it.name })

        fun ambiguous(name: String): Boolean {
            if (nameKey(name) in library.ambiguous) {
                problems += "More than one library item is called \"${name.trim()}\"; rename one first."
                return true
            }
            return false
        }

        // ---------------------------------------------------------- exercises

        val exercises = file.exercises.mapIndexedNotNull { index, source ->
            val name = source.name?.trim()?.takeIf { it.isNotEmpty() }
            if (name == null) {
                problems += "Exercise ${index + 1} has no name."
                return@mapIndexedNotNull null
            }
            if (ambiguous(name)) return@mapIndexedNotNull null
            val mode = source.mode?.let { parseMode(it) }
            if (mode == null) {
                problems += if (source.mode == null) "$name has no mode." else "$name: unknown mode \"${source.mode}\"."
                return@mapIndexedNotNull null
            }
            val category = source.category?.let { raw ->
                parseEnum<ExerciseCategory>(raw) ?: run {
                    problems += "$name: unknown category \"$raw\"."
                    null
                }
            }
            var unilateral = source.unilateral
            var unit = source.unit?.trim()?.takeIf { it.isNotEmpty() }
            var meaning = source.meaning?.let { raw ->
                parseEnum<MeasurementMeaning>(raw) ?: run {
                    problems += "$name: unknown meaning \"$raw\"."
                    null
                }
            }
            if (mode == ExerciseMode.ACTIVITY) {
                // What the editor refuses to save for an activity, the file cannot sneak in.
                if (unilateral) warnings += "$name: an activity has no sides; \"unilateral\" left out."
                if (unit != null) warnings += "$name: an activity has no load; \"unit\" left out."
                unilateral = false
                unit = null
                meaning = null
            } else if (unit != null && meaning == null && source.meaning == null) {
                problems += "$name: \"unit\" needs a \"meaning\" (TOTAL_LOAD, or ADDED_LOAD — negative for assistance)."
            } else if (unit == null && meaning != null) {
                warnings += "$name: \"meaning\" without a \"unit\" left out."
                meaning = null
            }
            val plan = payloadFor(source.plan, mode, unilateral, name, station = false, problems, warnings)
            val existing = library.exercises[nameKey(name)]
            val variations = source.variations?.let { list ->
                val seen = mutableSetOf<String>()
                list.mapIndexedNotNull { i, variation ->
                    val tag = variation.tag?.trim()?.uppercase()
                    when {
                        tag.isNullOrEmpty() -> {
                            problems += "$name: variation ${i + 1} has no tag."
                            null
                        }
                        !VariationTag.isValid(tag) -> {
                            problems += "$name: \"${variation.tag}\" is not a tag (capitals or digits, at most ${VariationTag.MAX_LENGTH})."
                            null
                        }
                        !seen.add(tag) -> {
                            problems += "$name: two variations are tagged $tag."
                            null
                        }
                        else -> ResolvedVariation(
                            tag = tag,
                            notes = variation.notes?.trim()?.takeIf { it.isNotEmpty() },
                            plan = payloadFor(variation.plan, mode, unilateral, "$name $tag", station = false, problems, warnings),
                        )
                    }
                }
            }
            ResolvedExercise(
                key = nameKey(name),
                existingId = existing?.id,
                draft = ExerciseDraft(
                    name = name,
                    mode = mode,
                    unilateral = unilateral,
                    measurementUnit = unit,
                    measurementMeaning = meaning,
                    notes = source.notes?.trim()?.takeIf { it.isNotEmpty() },
                    description = source.description?.trim()?.takeIf { it.isNotEmpty() },
                    category = category,
                    defaultPrescription = plan,
                ),
                variations = variations,
            )
        }

        /** An exercise as a reference sees it: the file's definition first, else the library's. */
        data class Target(val name: String, val mode: ExerciseMode, val unilateral: Boolean, val tags: Set<String>)
        val fileExercises = exercises.associateBy { it.key }
        fun target(name: String): Target? {
            val key = nameKey(name)
            val existing = library.exercises[key]
            fileExercises[key]?.let { defined ->
                // Variations the file does not list stay in the library when adding, so they still
                // resolve; when replacing they go.
                val kept = if (referencesFromLibrary) existing?.variations?.keys.orEmpty() else emptySet()
                val tags = defined.variations?.map { it.tag }.orEmpty().toSet() + kept
                return Target(defined.draft.name, defined.draft.mode, defined.draft.unilateral, tags)
            }
            if (key in library.ambiguous || !referencesFromLibrary) return null
            return existing?.let { Target(it.name, it.mode, it.unilateral, it.variations.keys) }
        }

        /** An exercise placed somewhere; [where] names the place for messages. */
        fun pick(item: PlanItem, where: String, station: Boolean, overridable: Boolean): ResolvedPick? {
            val name = item.exercise!!.trim()
            val found = target(name)
            if (found == null) {
                problems += "$where: no exercise called \"$name\" in the file or the library."
                return null
            }
            val tag = item.variation?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            if (tag != null && tag !in found.tags) {
                problems += "$where: ${found.name} has no variation $tag."
                return null
            }
            val plan = item.plan?.let {
                payloadFor(it, found.mode, found.unilateral, "${found.name} in $where", station, problems, warnings)
            }
            if (!overridable && plan != null) warnings += "$where: a plan here is not used."
            return ResolvedPick(nameKey(name), tag, plan)
        }

        /** Exactly one kind per item, and only the kinds [allowed] where it stands. */
        fun kindOf(item: PlanItem, where: String, allowed: Set<String>): String? {
            val kinds = listOfNotNull(
                "exercise".takeIf { item.exercise != null },
                "circuit".takeIf { item.circuit != null },
                "module".takeIf { item.module != null },
                "activity".takeIf { item.activity != null },
            )
            val kind = kinds.singleOrNull()
            when {
                kinds.isEmpty() -> problems += "$where: an item names nothing (exercise, circuit, module or activity)."
                kinds.size > 1 -> problems += "$where: an item names both ${kinds[0]} and ${kinds[1]}; use one each."
                kind !in allowed -> problems += "$where: a $kind cannot go here."
                else -> {
                    if (kind != "exercise" && (item.variation != null || item.plan != null)) {
                        problems += "$where: \"variation\" and \"plan\" only go with an exercise."
                    }
                    if (kind != "activity" && item.minutes != null) {
                        problems += "$where: \"minutes\" only goes with an activity; put it in \"plan\" for an exercise."
                    }
                    return kind
                }
            }
            return null
        }

        // ---------------------------------------------------------- circuits

        val fileCircuits = file.circuits.mapNotNull { it.name?.trim()?.takeIf { n -> n.isNotEmpty() } }
            .map(::nameKey).toSet()
        val circuits = file.circuits.mapIndexedNotNull { index, source ->
            val name = source.name?.trim()?.takeIf { it.isNotEmpty() }
            if (name == null) {
                problems += "Circuit ${index + 1} has no name."
                return@mapIndexedNotNull null
            }
            if (ambiguous(name)) return@mapIndexedNotNull null
            val category = source.category?.let { raw ->
                parseEnum<ExerciseCategory>(raw) ?: run {
                    problems += "$name: unknown category \"$raw\"."
                    null
                }
            }
            if (source.rounds < 1) problems += "$name: rounds must be at least 1."
            if (source.transitionSeconds < 0 || source.roundRestSeconds < 0) {
                problems += "$name: rests cannot be negative."
            }
            if (source.stations.isEmpty()) problems += "$name has no stations."
            val stations = source.stations.mapNotNull { item ->
                kindOf(item, name, setOf("exercise")) ?: return@mapNotNull null
                pick(item, name, station = true, overridable = true)
            }
            ResolvedCircuit(
                key = nameKey(name),
                existingId = library.circuits[nameKey(name)],
                name = name,
                category = category,
                rounds = source.rounds,
                transitionSeconds = source.transitionSeconds,
                roundRestSeconds = source.roundRestSeconds,
                stations = stations,
            )
        }
        fun circuitKnown(name: String) =
            nameKey(name).let { it in fileCircuits || (referencesFromLibrary && it in library.circuits) }

        // ---------------------------------------------------------- modules

        val fileModules = file.modules.mapNotNull { it.name?.trim()?.takeIf { n -> n.isNotEmpty() } }
            .map(::nameKey).toSet()
        val modules = file.modules.mapIndexedNotNull { index, source ->
            val name = source.name?.trim()?.takeIf { it.isNotEmpty() }
            if (name == null) {
                problems += "Module ${index + 1} has no name."
                return@mapIndexedNotNull null
            }
            if (ambiguous(name)) return@mapIndexedNotNull null
            if (source.entries.isEmpty()) problems += "$name has no entries."
            val entries = source.entries.mapNotNull { item ->
                when (kindOf(item, name, setOf("exercise", "circuit"))) {
                    "exercise" -> pick(item, name, station = false, overridable = true)?.let(ResolvedEntry::Exercise)
                    "circuit" -> {
                        val circuit = item.circuit!!.trim()
                        if (circuitKnown(circuit)) {
                            ResolvedEntry.Circuit(nameKey(circuit))
                        } else {
                            problems += "$name: no circuit called \"$circuit\" in the file or the library."
                            null
                        }
                    }
                    else -> null
                }
            }
            ResolvedModule(
                key = nameKey(name),
                existingId = library.modules[nameKey(name)],
                name = name,
                description = source.description?.trim()?.takeIf { it.isNotEmpty() },
                entries = entries,
            )
        }
        fun moduleKnown(name: String) =
            nameKey(name).let { it in fileModules || (referencesFromLibrary && it in library.modules) }

        // ---------------------------------------------------------- weeks

        val seenWeeks = mutableSetOf<LocalDate>()
        var skippedPast = 0
        val slots = file.weeks.flatMapIndexed { index, week ->
            val start = week.weekStart?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
            when {
                week.weekStart == null -> {
                    problems += "Week ${index + 1} has no weekStart."
                    return@flatMapIndexed emptyList()
                }
                start == null -> {
                    problems += "Week ${index + 1}: \"${week.weekStart}\" is not a date (YYYY-MM-DD)."
                    return@flatMapIndexed emptyList()
                }
                start.dayOfWeek != DayOfWeek.MONDAY -> {
                    problems += "Week ${index + 1}: $start is a ${start.dayOfWeek.name.lowercase()}; weekStart must be a Monday."
                    return@flatMapIndexed emptyList()
                }
                !seenWeeks.add(start) -> {
                    problems += "The week of $start is listed twice."
                    return@flatMapIndexed emptyList()
                }
            }
            val named = listOf(null to week.unscheduled) + week.days.map { (offset, items) -> start!!.plusDays(offset.toLong()) to items }
            fun past(date: LocalDate?) = cutoff != null && (date ?: start!!.plusDays(6)) < cutoff
            named.filter { (date, items) ->
                // Still checked, so a mistake in the past is reported all the same.
                if (items.isNotEmpty() && past(date)) skippedPast += items.size
                items.isNotEmpty()
            }.map { (date, items) ->
                val where = date?.let { "${it.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase)} $it" }
                    ?: "Unscheduled, week of $start"
                ResolvedSlot(
                    weekStart = start!!,
                    date = date,
                    items = items.mapNotNull { item ->
                        when (kindOf(item, where, setOf("exercise", "circuit", "module", "activity"))) {
                            "exercise" -> pick(item, where, station = false, overridable = true)?.let(ResolvedItem::Exercise)
                            "circuit" -> item.circuit!!.trim().let { circuit ->
                                if (circuitKnown(circuit)) {
                                    ResolvedItem.Circuit(nameKey(circuit))
                                } else {
                                    problems += "$where: no circuit called \"$circuit\" in the file or the library."
                                    null
                                }
                            }
                            "module" -> item.module!!.trim().let { module ->
                                if (moduleKnown(module)) {
                                    ResolvedItem.Module(nameKey(module))
                                } else {
                                    problems += "$where: no module called \"$module\" in the file or the library."
                                    null
                                }
                            }
                            "activity" -> {
                                val name = item.activity!!.trim()
                                if (name.isEmpty()) problems += "$where: an activity needs a name."
                                if (item.minutes != null && item.minutes < 0) problems += "$where: $name has negative minutes."
                                ResolvedItem.Activity(name, item.minutes?.takeIf { it > 0 })
                            }
                            else -> null
                        }
                    },
                )
            }
        }

        val preview = PlanPreview(
            exercisesAdded = exercises.filter { it.existingId == null }.map { it.draft.name },
            exercisesUpdated = exercises.filter { it.existingId != null }.map { it.draft.name },
            variations = exercises.sumOf { it.variations?.size ?: 0 },
            circuitsAdded = circuits.filter { it.existingId == null }.map { it.name },
            circuitsUpdated = circuits.filter { it.existingId != null }.map { it.name },
            modulesAdded = modules.filter { it.existingId == null }.map { it.name },
            modulesUpdated = modules.filter { it.existingId != null }.map { it.name },
            weeks = slots.filter { cutoff == null || (it.date ?: it.weekStart.plusDays(6)) >= cutoff }
                .map { it.weekStart }.distinct().size,
            planned = slots.filter { cutoff == null || (it.date ?: it.weekStart.plusDays(6)) >= cutoff }
                .sumOf { it.items.size },
            skippedPast = skippedPast,
        )
        val distinctProblems = problems.distinct()
        return PlanResolution(
            problems = distinctProblems,
            warnings = warnings.distinct(),
            preview = preview,
            plan = if (distinctProblems.isEmpty()) {
                ResolvedPlan(
                    exercises, circuits, modules,
                    slots.filter { cutoff == null || (it.date ?: it.weekStart.plusDays(6)) >= cutoff },
                )
            } else {
                null
            },
        )
    }

    /**
     * A plan as the editor would save it for this [mode]: the fields the mode reads, and nothing
     * else. A field that means nothing here is dropped with a warning rather than stored, so the
     * file cannot hold a plan the app could not have made. In a circuit ([station]) the rounds
     * decide the sets, so every station does one.
     */
    internal fun payloadFor(
        spec: PlanSpec?,
        mode: ExerciseMode,
        unilateral: Boolean,
        what: String,
        station: Boolean,
        problems: MutableList<String>,
        warnings: MutableList<String>,
    ): PrescriptionPayload {
        val effort = spec?.effort?.let { raw ->
            parseEnum<EffortLevel>(raw) ?: run {
                problems += "$what: unknown effort \"$raw\" (VERY_EASY, EASY, MODERATE, HARD, VERY_HARD)."
                null
            }
        }
        if (spec == null) return PrescriptionPayload(sets = 1)
        val numbers = listOf(
            "sets" to spec.sets, "reps" to spec.reps, "seconds" to spec.seconds,
            "restSeconds" to spec.restSeconds, "sideSwitchSeconds" to spec.sideSwitchSeconds,
            "minutes" to spec.minutes, "plannedMinutes" to spec.plannedMinutes,
            "repeater.reps" to spec.repeater?.reps, "repeater.workSeconds" to spec.repeater?.workSeconds,
            "repeater.restSeconds" to spec.repeater?.restSeconds,
        )
        numbers.firstOrNull { (_, value) -> value != null && value < 0 }?.let { (field, _) ->
            problems += "$what: \"$field\" cannot be negative."
        }
        fun dropped(field: String, value: Any?, why: String) {
            if (value != null) warnings += "$what: \"$field\" left out — $why."
        }

        if (mode == ExerciseMode.ACTIVITY) {
            val why = "an activity is only a length"
            dropped("sets", spec.sets, why)
            dropped("reps", spec.reps, why)
            dropped("seconds", spec.seconds, "an activity's length is \"minutes\"")
            dropped("restSeconds", spec.restSeconds, why)
            dropped("sideSwitchSeconds", spec.sideSwitchSeconds, why)
            dropped("plannedMinutes", spec.plannedMinutes, "an activity's length is \"minutes\"")
            dropped("repeater", spec.repeater, why)
            return PrescriptionPayload(
                sets = 1,
                targetDurationSeconds = spec.minutes?.takeIf { it > 0 }?.let { it * 60 },
                effort = effort,
            )
        }

        dropped("minutes", spec.minutes, "that is an activity's length; use \"plannedMinutes\"")
        if (mode != ExerciseMode.REPETITIONS) dropped("reps", spec.reps, "only reps exercises count reps")
        if (mode != ExerciseMode.DURATION) dropped("seconds", spec.seconds, "only timed sets have a length")
        if (mode != ExerciseMode.REPEATERS) dropped("repeater", spec.repeater, "only repeaters have pulses")
        if (!unilateral) dropped("sideSwitchSeconds", spec.sideSwitchSeconds, "it is not one side at a time")
        if (station) dropped("sets", spec.sets, "a circuit's rounds decide the sets")

        val repeater = spec.repeater?.takeIf { mode == ExerciseMode.REPEATERS }?.let {
            if (it.reps == null || it.workSeconds == null || it.reps < 1 || it.workSeconds < 1) {
                problems += "$what: a repeater needs \"reps\" and \"workSeconds\" of at least 1."
                null
            } else {
                RepeaterPrescription(it.reps, it.workSeconds, it.restSeconds.coerceAtLeast(0))
            }
        }
        return PrescriptionPayload(
            sets = if (station) 1 else (spec.sets?.coerceAtLeast(0) ?: 1),
            targetReps = spec.reps?.takeIf { mode == ExerciseMode.REPETITIONS },
            targetDurationSeconds = spec.seconds?.takeIf { mode == ExerciseMode.DURATION },
            restSeconds = spec.restSeconds,
            effort = effort,
            plannedDurationSeconds = spec.plannedMinutes?.let { it * 60 },
            sideSwitchSeconds = spec.sideSwitchSeconds?.takeIf { unilateral },
            repeater = repeater,
        )
    }

    private fun parseMode(raw: String): ExerciseMode? = when (normaliseEnum(raw)) {
        "REPS" -> ExerciseMode.REPETITIONS
        "TIMED", "TIMED_SETS" -> ExerciseMode.DURATION
        else -> parseEnum<ExerciseMode>(raw)
    }

    private fun normaliseEnum(raw: String) = raw.trim().uppercase().replace(Regex("[\\s-]+"), "_")

    private inline fun <reified T : Enum<T>> parseEnum(raw: String): T? {
        val wanted = normaliseEnum(raw)
        return enumValues<T>().firstOrNull { it.name == wanted }
    }
}
