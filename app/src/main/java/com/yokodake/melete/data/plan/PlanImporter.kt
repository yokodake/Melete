package com.yokodake.melete.data.plan

import androidx.room.withTransaction
import com.yokodake.melete.data.BenchmarkRepository
import com.yokodake.melete.data.MeleteDatabase
import com.yokodake.melete.data.ModuleDraft
import com.yokodake.melete.data.ModuleEntryDraft
import com.yokodake.melete.data.RoutineDraft
import com.yokodake.melete.data.RoutineEntryDraft
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.VariationSave
import com.yokodake.melete.data.backup.BACKUP_FORMAT
import com.yokodake.melete.data.backup.BackupService
import com.yokodake.melete.data.entity.OccurrenceState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.LocalDate

/** A file that could not be read as a plan at all, with the reason in words. */
class PlanUnreadable(message: String) : Exception(message)

/**
 * How an import treats what is already planned. Neither ever deletes a record of training:
 * anything logged, marked done or marked skipped stays exactly as it is, and so does the diary.
 */
enum class ImportMode {
    /**
     * Keeps everything. The file's definitions replace library items of the same name, from now on
     * (planned and logged copies keep their snapshots); its weeks are planned after what is there.
     */
    ADD,

    /**
     * Clears planned work that has not happened, takes library items the file does not name out
     * of the library (retired when they have history), and builds from the file.
     */
    REPLACE,
}

/** Whether an import reaches into the past. */
enum class ImportScope {
    /** Nothing dated before today is planned, or cleared. */
    FROM_TODAY,

    /** The file applies as written, past days included. */
    INCLUDE_PAST,
}

/** What replacing would clear and keep, beyond what the file itself adds. */
data class ReplaceImpact(
    /** Planned cards — exercises, circuits, modules — that have not happened, and go. */
    val cleared: Int = 0,
    /** Cards in range holding something logged, done or skipped, and so kept. */
    val kept: Int = 0,
    val removedExercises: List<String> = emptyList(),
    val removedCircuits: List<String> = emptyList(),
    val removedModules: List<String> = emptyList(),
)

/** The verdict on a file for one mode and scope, with what replacing would do. */
data class ImportCheck(val resolution: PlanResolution, val replace: ReplaceImpact?)

/**
 * Writes a hand-written plan into the app.
 *
 * Everything goes through the training repository, the same calls the editors make, so an import
 * cannot produce a row the app could not have produced itself. The file is resolved whole first
 * ([PlanCheck]); then, in one transaction, planned work is cleared (when replacing), the library is
 * written, then circuits, then modules, then the weeks — so a failure half-way leaves the phone
 * exactly as it was.
 */
class PlanImporter(
    private val database: MeleteDatabase,
    private val repository: TrainingRepository,
    private val backups: BackupService,
    private val benchmarks: BenchmarkRepository = BenchmarkRepository(database),
) {

    private val tables = database.backupDao()

    /** Reads a file's text as a plan, or says in words why it cannot be one. */
    fun decode(text: String): PlanFile {
        val root = runCatching { PlanJson.parseToJsonElement(text).jsonObject }.getOrElse {
            throw PlanUnreadable("This file is not readable JSON: ${firstLine(it)}")
        }
        if ((root["format"] as? JsonPrimitive)?.content == BACKUP_FORMAT) {
            throw PlanUnreadable("This is a full backup, not a plan. Use Restore for it.")
        }
        return try {
            PlanJson.decodeFromJsonElement(PlanFile.serializer(), root as JsonObject)
        } catch (failure: SerializationException) {
            throw PlanUnreadable("This plan cannot be read: ${firstLine(failure)}")
        } catch (failure: IllegalArgumentException) {
            throw PlanUnreadable("This plan cannot be read: ${firstLine(failure)}")
        }
    }

    /** Judges [file] against what the phone holds now, as [mode] and [scope] would see it. */
    suspend fun check(
        file: PlanFile,
        mode: ImportMode,
        scope: ImportScope,
        today: LocalDate = LocalDate.now(),
    ): ImportCheck {
        val library = index()
        val resolution = resolve(file, library, mode, scope, today)
        val replace = if (mode == ImportMode.REPLACE) {
            val cards = plannedCards(scope, today)
            removals(resolution, library).let { (exercises, circuits, modules) ->
                ReplaceImpact(
                    cleared = cards.count { !it.recorded },
                    kept = cards.count { it.recorded },
                    removedExercises = exercises.map { it.second },
                    removedCircuits = circuits.map { it.second },
                    removedModules = modules.map { it.second },
                )
            }
        } else {
            null
        }
        return ImportCheck(resolution, replace)
    }

    /**
     * Imports [file]. Refuses, throwing with nothing changed, unless it resolves cleanly. A
     * replace first writes a safety copy of the whole record into [safetyDirectory].
     */
    suspend fun import(
        file: PlanFile,
        mode: ImportMode,
        scope: ImportScope,
        safetyDirectory: File,
        today: LocalDate = LocalDate.now(),
    ): ImportCheck {
        // Read before the transaction: the library's flows are not for reading inside one.
        val library = index()
        val resolution = resolve(file, library, mode, scope, today)
        val plan = resolution.plan ?: throw PlanUnreadable(resolution.problems.first())
        if (mode == ImportMode.REPLACE) backups.writeSafetyCopy(safetyDirectory)
        var impact: ReplaceImpact? = null
        database.withTransaction {
            if (mode == ImportMode.REPLACE) impact = clearPlanned(scope, today)
            write(plan, library, replacing = mode == ImportMode.REPLACE)
            if (mode == ImportMode.REPLACE) {
                val (exercises, circuits, modules) = removals(resolution, library)
                // Templates before the exercises they are made of.
                modules.forEach { repository.removeModule(it.first) }
                circuits.forEach { repository.removeRoutine(it.first) }
                exercises.forEach { repository.retireExercise(it.first) }
                impact = impact?.copy(
                    removedExercises = exercises.map { it.second },
                    removedCircuits = circuits.map { it.second },
                    removedModules = modules.map { it.second },
                )
            }
        }
        return ImportCheck(resolution, impact)
    }

    private fun resolve(file: PlanFile, library: LibraryIndex, mode: ImportMode, scope: ImportScope, today: LocalDate) =
        PlanCheck.resolve(
            file = file,
            library = library,
            referencesFromLibrary = mode == ImportMode.ADD,
            cutoff = today.takeIf { scope == ImportScope.FROM_TODAY },
        )

    /** Library items, as (id, name), that the file does not define and replacing would remove. */
    private fun removals(resolution: PlanResolution, library: LibraryIndex): Triple<
        List<Pair<String, String>>, List<Pair<String, String>>, List<Pair<String, String>>> {
        val preview = resolution.preview
        val exercises = (preview.exercisesAdded + preview.exercisesUpdated).map(::nameKey).toSet()
        val circuits = (preview.circuitsAdded + preview.circuitsUpdated).map(::nameKey).toSet()
        val modules = (preview.modulesAdded + preview.modulesUpdated).map(::nameKey).toSet()
        return Triple(
            library.exercises.filterKeys { it !in exercises }.values.map { it.id to it.name },
            library.circuitNames.filterKeys { it !in circuits }.map { (key, name) -> library.circuits.getValue(key) to name },
            library.moduleNames.filterKeys { it !in modules }.map { (key, name) -> library.modules.getValue(key) to name },
        )
    }

    // ------------------------------------------------------------------ planned work

    /** One card of a week: what edit mode moves, and what replacing clears or keeps whole. */
    private data class Card(val kind: Kind, val id: String, val weekStart: Long, val date: Long?, val recorded: Boolean) {
        enum class Kind { EXERCISE, CIRCUIT, MODULE }
    }

    /**
     * Every card in [scope]. A card is recorded — and kept — when anything in it was logged, marked
     * done or marked skipped: a circuit or module half done is kept whole, since its planned rest
     * belongs to the session that happened.
     */
    private suspend fun plannedCards(scope: ImportScope, today: LocalDate): List<Card> {
        val setsBy = tables.sets().groupingBy { it.occurrenceId }.eachCount()
        val occurrences = tables.occurrences()
        fun recorded(ids: List<String>) = occurrences.any {
            it.id in ids && (it.state != OccurrenceState.PLANNED || (setsBy[it.id] ?: 0) > 0)
        }
        val cards = buildList {
            tables.moduleInstances().forEach { module ->
                val members = occurrences.filter { it.moduleInstanceId == module.id }.map { it.id }
                add(Card(Card.Kind.MODULE, module.id, module.weekStartEpochDay, module.trainingDateEpochDay, recorded(members)))
            }
            tables.circuitInstances().filter { it.moduleInstanceId == null }.forEach { circuit ->
                val stations = occurrences.filter { it.circuitInstanceId == circuit.id }.map { it.id }
                add(Card(Card.Kind.CIRCUIT, circuit.id, circuit.weekStartEpochDay, circuit.trainingDateEpochDay, recorded(stations)))
            }
            occurrences.filter { it.circuitInstanceId == null && it.moduleInstanceId == null }.forEach {
                add(Card(Card.Kind.EXERCISE, it.id, it.weekStartEpochDay, it.trainingDateEpochDay, recorded(listOf(it.id))))
            }
        }
        if (scope == ImportScope.INCLUDE_PAST) return cards
        val from = today.toEpochDay()
        // An undated card belongs to its whole week: in range while the week has a day left.
        return cards.filter { (it.date ?: (it.weekStart + 6)) >= from }
    }

    /** Clears every unrecorded card in [scope]; says how many went and how many were kept. */
    private suspend fun clearPlanned(scope: ImportScope, today: LocalDate): ReplaceImpact {
        val cards = plannedCards(scope, today)
        cards.filter { !it.recorded }.forEach { card ->
            val cleared = when (card.kind) {
                Card.Kind.MODULE -> repository.removeModuleIfEmpty(card.id)
                Card.Kind.CIRCUIT -> repository.deleteCircuitIfEmpty(card.id)
                Card.Kind.EXERCISE -> repository.deleteOccurrenceIfEmpty(card.id)
            }
            check(cleared) { "a planned ${card.kind.name.lowercase()} turned out to hold a record" }
        }
        return ReplaceImpact(cleared = cards.count { !it.recorded }, kept = cards.count { it.recorded })
    }

    // ------------------------------------------------------------------ writing

    private suspend fun write(plan: ResolvedPlan, library: LibraryIndex, replacing: Boolean) {
        // Exercises, and the ids every later reference resolves to.
        val exerciseIds = library.exercises.mapValues { it.value.id }.toMutableMap()
        plan.exercises.forEach { exercise ->
            val id = exercise.existingId?.also { repository.updateExercise(it, exercise.draft) }
                ?: repository.createExercise(exercise.draft)
            exerciseIds[exercise.key] = id
            val listed = exercise.variations
            if (replacing && exercise.existingId != null) {
                // The library becomes the file's: tags it does not list go (retired if used).
                val keep = listed?.map { it.tag }.orEmpty().toSet()
                repository.getLibraryExercise(id)?.variations
                    ?.filter { !it.retired && it.tag !in keep }
                    ?.forEach { repository.deleteVariation(it.id) }
            }
            listed?.forEach { variation ->
                val existing = repository.getLibraryExercise(id)?.variations
                    ?.firstOrNull { !it.retired && it.tag == variation.tag }
                val saved = if (existing != null) {
                    repository.updateVariation(existing.id, variation.tag, variation.notes, variation.plan)
                } else {
                    repository.createVariation(id, variation.tag, variation.notes, variation.plan)
                }
                check(saved == VariationSave.SAVED) { "variation ${variation.tag} of ${exercise.draft.name}: $saved" }
            }
        }

        fun exerciseId(key: String) = exerciseIds[key] ?: error("unresolved exercise $key")
        suspend fun variationId(pick: ResolvedPick): String? = pick.variationTag?.let { tag ->
            repository.getLibraryExercise(exerciseId(pick.exerciseKey))?.variations
                ?.firstOrNull { !it.retired && it.tag == tag }?.id ?: error("unresolved variation $tag")
        }
        /** The plan a pick takes: its own, else its variation's, else the exercise's default. */
        suspend fun planOf(pick: ResolvedPick) = pick.plan ?: run {
            val exercise = repository.getLibraryExercise(exerciseId(pick.exerciseKey))
            val variation = pick.variationTag?.let { tag -> exercise?.variations?.firstOrNull { it.tag == tag && !it.retired } }
            variation?.prescription ?: exercise?.defaultPrescription
        }

        val circuitIds = library.circuits.toMutableMap()
        plan.circuits.forEach { circuit ->
            val draft = RoutineDraft(
                name = circuit.name,
                rounds = circuit.rounds,
                transitionSeconds = circuit.transitionSeconds,
                roundRestSeconds = circuit.roundRestSeconds,
                entries = circuit.stations.map { RoutineEntryDraft(exerciseId(it.exerciseKey), planOf(it)) },
                category = circuit.category,
            )
            circuitIds[circuit.key] = circuit.existingId?.also { repository.updateRoutine(it, draft) }
                ?: repository.createRoutine(draft)
        }

        val moduleIds = library.modules.toMutableMap()
        plan.modules.forEach { module ->
            val draft = ModuleDraft(
                name = module.name,
                description = module.description,
                entries = module.entries.map { entry ->
                    when (entry) {
                        is ResolvedEntry.Circuit -> ModuleEntryDraft(
                            routineId = circuitIds[entry.key] ?: error("unresolved circuit ${entry.key}"),
                        )
                        is ResolvedEntry.Exercise -> ModuleEntryDraft(
                            exerciseId = exerciseId(entry.pick.exerciseKey),
                            variationId = variationId(entry.pick),
                            prescription = planOf(entry.pick),
                        )
                    }
                },
            )
            moduleIds[module.key] = module.existingId?.also { repository.updateModule(it, draft) }
                ?: repository.createModule(draft)
        }

        // The weeks: each item after whatever the slot already holds, in the file's order.
        plan.slots.forEach { slot ->
            slot.items.forEach { item ->
                when (item) {
                    is ResolvedItem.Exercise -> {
                        val occurrence = repository.scheduleExercise(
                            exerciseId(item.pick.exerciseKey), slot.weekStart, slot.date, variationId(item.pick),
                        )
                        item.pick.plan?.let { repository.updateOccurrencePrescription(occurrence, it) }
                    }
                    is ResolvedItem.Circuit -> {
                        val circuit = repository.scheduleRoutine(
                            circuitIds[item.key] ?: error("unresolved circuit ${item.key}"), slot.weekStart, slot.date,
                        )
                        // This week's rounds, on this copy only; the saved circuit keeps its own.
                        if (circuit != null && item.rounds != null) repository.setCircuitRounds(circuit, item.rounds)
                    }
                    is ResolvedItem.Module -> {
                        val module = repository.scheduleModule(
                            moduleIds[item.key] ?: error("unresolved module ${item.key}"), slot.weekStart, slot.date,
                        )
                        // This week's plans for some of its exercises, on this copy only.
                        if (module != null) {
                            item.plans.forEach { (exercise, plan) ->
                                repository.overrideModulePlan(module, exerciseId(exercise), plan)
                            }
                        }
                    }
                    is ResolvedItem.Activity -> repository.createOneOffActivity(
                        item.name, slot.weekStart, slot.date, item.minutes?.let { it * 60 },
                    )
                }
            }
        }

        // Benchmarks: the definition by name, then the results the phone does not hold yet. Never
        // removed by a replace — they are records, not plans.
        plan.benchmarks.forEach { benchmark ->
            val id = benchmark.existingId?.also { benchmarks.update(it, benchmark.draft) }
                ?: benchmarks.create(benchmark.draft)
            benchmark.results.forEach { result ->
                checkNotNull(
                    benchmarks.record(
                        id, result.date, result.value, result.valueRight, result.note, result.text,
                        result.bodyweightPercent,
                    )
                ) { "a result for ${benchmark.draft.name} was refused" }
            }
        }
    }

    /** The live library by name. A name two live items share is marked, never guessed between. */
    private suspend fun index(): LibraryIndex {
        val exercises = repository.observeLibrary().first().filter { it.deletedAtEpochMs == null }
        val circuits = repository.observeRoutines().first()
        val modules = repository.observeModules().first()
        val ambiguous = listOf(exercises.map { it.name }, circuits.map { it.name }, modules.map { it.name })
            .flatMap { names -> names.groupBy(::nameKey).filterValues { it.size > 1 }.keys }
            .toSet()
        return LibraryIndex(
            exercises = exercises.associate { exercise ->
                nameKey(exercise.name) to IndexedExercise(
                    id = exercise.id,
                    name = exercise.name,
                    mode = exercise.mode,
                    unilateral = exercise.unilateral,
                    variations = exercise.variations.filter { !it.retired }.associate { it.tag to it.id },
                )
            },
            circuits = circuits.associate { nameKey(it.name) to it.id },
            modules = modules.associate { nameKey(it.name) to it.id },
            circuitNames = circuits.associate { nameKey(it.name) to it.name },
            moduleNames = modules.associate { nameKey(it.name) to it.name },
            ambiguous = ambiguous,
            benchmarks = benchmarks.observeStandings().first().associate { standing ->
                nameKey(standing.benchmark.name) to IndexedBenchmark(
                    id = standing.benchmark.id,
                    resultKeys = standing.results.map { resultKey(it.date, it.value, it.valueRight, it.textValue) }.toSet(),
                )
            },
        )
    }

    private fun firstLine(failure: Throwable): String =
        failure.message?.lineSequence()?.firstOrNull()?.trim()?.removeSuffix(".").orEmpty().ifEmpty { "unknown error" } + "."
}
