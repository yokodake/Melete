package com.yokodake.melete

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.ExerciseDraft
import com.yokodake.melete.data.MeleteDatabase
import com.yokodake.melete.data.ModuleDraft
import com.yokodake.melete.data.ModuleEntryDraft
import com.yokodake.melete.data.RoutineDraft
import com.yokodake.melete.data.RoutineEntryDraft
import com.yokodake.melete.data.TrainingRepository
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseCategory.BOARD_CLIMBING
import com.yokodake.melete.data.model.ExerciseCategory.FINGER_TRAINING
import com.yokodake.melete.data.model.ExerciseCategory.FLEXIBILITY
import com.yokodake.melete.data.model.ExerciseCategory.OPEN_CLIMBING
import com.yokodake.melete.data.model.ExerciseCategory.OTHER_ACTIVITY
import com.yokodake.melete.data.model.ExerciseCategory.STRENGTH_CONDITIONING
import com.yokodake.melete.data.model.ExerciseCategory.STRUCTURED_CLIMBING
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.ExerciseMode.ACTIVITY
import com.yokodake.melete.data.model.ExerciseMode.DURATION
import com.yokodake.melete.data.model.ExerciseMode.REPEATERS
import com.yokodake.melete.data.model.ExerciseMode.REPETITIONS
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.data.model.MeasurementMeaning.ADDED_LOAD
import com.yokodake.melete.data.model.MeasurementMeaning.ASSISTANCE
import com.yokodake.melete.data.model.MeasurementMeaning.TOTAL_LOAD
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.data.model.RepeaterPrescription
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test: fills the real app library with exercises covering the combinations the screens
 * have to render — every mode, sides, each load meaning, rest or none, effort or none, and a few
 * deliberately incomplete or contradictory plans.
 *
 * It writes to the app's own database, so it is skipped unless asked for by name:
 *
 *     adb shell am instrument -w -e seed library \
 *         -e class com.yokodake.melete.LibrarySeed \
 *         com.yokodake.melete.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Also seeds variations, circuits and modules built from those exercises; pass
 * `-e planning false` for the library alone.
 *
 * Re-running adds only what is missing, by name. Stands in until the importer exists.
 */
@RunWith(AndroidJUnit4::class)
class LibrarySeed {

    @Test
    fun seedLibrary() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("seed") == "library")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = MeleteDatabase.build(context)
        try {
            val repository = TrainingRepository(database)
            val existing = repository.observeLibrary().first().map { it.name }.toSet()
            exercises.filter { it.name !in existing }.forEach { repository.createExercise(it) }
            if (InstrumentationRegistry.getArguments().getString("planning") != "false") {
                seedPlanning(repository)
            }
        } finally {
            database.close()
        }
    }

    // ------------------------------------------------------------------ planning

    /** One station or module entry: an exercise by name, with its plan or a variation's. */
    private data class Pick(
        val exercise: String,
        val plan: PrescriptionPayload? = null,
        val variation: String? = null,
    )

    private data class CircuitSeed(
        val name: String,
        val rounds: Int,
        val transitionSeconds: Int,
        val roundRestSeconds: Int,
        val stations: List<Pick>,
        val category: ExerciseCategory? = null,
    )

    /** A module entry is an exercise [Pick] or, when [circuit] is set, a saved circuit by name. */
    private data class EntrySeed(val pick: Pick? = null, val circuit: String? = null)

    private data class ModuleSeed(
        val name: String,
        val description: String?,
        val entries: List<EntrySeed>,
    )

    private fun ex(name: String, plan: PrescriptionPayload? = null, variation: String? = null) =
        EntrySeed(pick = Pick(name, plan, variation))

    private fun circuit(name: String) = EntrySeed(circuit = name)

    private val variationSeeds = listOf(
        Triple("Back squat", "PWR", "Fast concentric at about 70% of 1RM.") to
            PrescriptionPayload(sets = 5, targetReps = 3, restSeconds = 180),
        Triple("Back squat", "STR", null) to
            PrescriptionPayload(sets = 4, targetReps = 5, restSeconds = 240),
        Triple("Max hangs 20 mm", "MAX", "Heaviest load that still gives a clean 10 s.") to
            PrescriptionPayload(sets = 5, targetDurationSeconds = 10, restSeconds = 180),
        Triple(
            "Max hangs 20 mm", "END",
            "First 3 reps at 60% with emphasis on speed, then add load for RIR 1–2 on the last 3.",
        ) to PrescriptionPayload(sets = 6, targetDurationSeconds = 10, restSeconds = 90),
        Triple("Pull-up", "A", null) to PrescriptionPayload(sets = 5, targetReps = 5, restSeconds = 150),
        Triple("Pull-up", "B", "Pause 2 s at the top of every rep.") to
            PrescriptionPayload(sets = 3, targetReps = 8, restSeconds = 120),
    )

    private val circuitSeeds = listOf(
        // The ordinary case: reps, a hold and reps, with both rests.
        CircuitSeed(
            "Pull + core", rounds = 3, transitionSeconds = 30, roundRestSeconds = 120,
            category = STRENGTH_CONDITIONING,
            stations = listOf(
                Pick("Pull-up", PrescriptionPayload(sets = 1, targetReps = 5)),
                Pick("Plank", PrescriptionPayload(sets = 1, targetDurationSeconds = 45)),
                Pick("Push-up", PrescriptionPayload(sets = 1, targetReps = 12)),
            ),
        ),
        // Every station one side at a time, so the switch rests appear inside the rounds.
        CircuitSeed(
            "Leg circuit", rounds = 4, transitionSeconds = 20, roundRestSeconds = 90,
            category = STRENGTH_CONDITIONING,
            stations = listOf(
                Pick("Bulgarian split squat", PrescriptionPayload(sets = 1, targetReps = 8, sideSwitchSeconds = 0)),
                Pick("Pistol squat", PrescriptionPayload(sets = 1, targetReps = 5)),
                Pick("Couch stretch", PrescriptionPayload(sets = 1, targetDurationSeconds = 60)),
            ),
        ),
        // A repeater station, a plain hold, and a timed station with no length that must wait.
        CircuitSeed(
            "Hangboard density", rounds = 3, transitionSeconds = 60, roundRestSeconds = 180,
            category = FINGER_TRAINING,
            stations = listOf(
                Pick(
                    "7/3 repeaters 20 mm",
                    PrescriptionPayload(
                        sets = 1,
                        repeater = RepeaterPrescription(repsPerSet = 6, workSecondsPerRep = 7, restSecondsBetweenReps = 3),
                    ),
                ),
                Pick("Dead hang", PrescriptionPayload(sets = 1, targetDurationSeconds = 30)),
                Pick("Front lever hold", PrescriptionPayload(sets = 1)),
            ),
        ),
        // A superset: two stations back to back, rest only after the pair.
        CircuitSeed(
            "Pull/push superset", rounds = 5, transitionSeconds = 0, roundRestSeconds = 90,
            stations = listOf(
                Pick("Weighted pull-up", PrescriptionPayload(sets = 1, targetReps = 3)),
                Pick("Push-up", PrescriptionPayload(sets = 1, targetReps = 10)),
            ),
        ),
    )

    private val moduleSeeds = listOf(
        ModuleSeed(
            "Fingers + core",
            "Base block: finger strength first, then pulling and core while fresh enough.",
            listOf(ex("Max hangs 20 mm", variation = "END"), circuit("Pull + core"), ex("Couch stretch")),
        ),
        ModuleSeed(
            "Strength day",
            "Max strength. Heavy, long rests, no climbing the same day.",
            listOf(
                ex("Back squat", variation = "STR"),
                circuit("Leg circuit"),
                ex("Weighted pull-up", PrescriptionPayload(sets = 4, targetReps = 3, restSeconds = 240)),
            ),
        ),
        // Activities inside a module, beside a structured climbing exercise.
        ModuleSeed(
            "Climbing session",
            "Performance.",
            listOf(ex("Bouldering session"), ex("4×4 boulders"), ex("Yoga")),
        ),
        // No description: the empty case the list and editor must handle.
        ModuleSeed(
            "Recovery",
            null,
            listOf(ex("Run"), ex("Couch stretch"), ex("Yoga")),
        ),
        // Mostly a circuit, with a variation chosen for the standalone exercise.
        ModuleSeed(
            "Hangboard block",
            "Finger endurance.",
            listOf(circuit("Hangboard density"), ex("One-arm repeaters"), ex("Pull-up", variation = "B")),
        ),
    )

    /**
     * Variations, circuits and modules on top of the library, so the planning screens have
     * something to show. Each piece is skipped when it already exists by name, and an entry whose
     * exercise has been renamed or removed is left out rather than failing the whole seed.
     */
    private suspend fun seedPlanning(repository: TrainingRepository) {
        variationSeeds.forEach { (key, plan) ->
            val (name, tag, notes) = key
            val exercise = repository.observeLibrary().first().firstOrNull { it.name == name }
                ?: return@forEach
            if (exercise.variations.none { it.tag == tag }) {
                repository.createVariation(exercise.id, tag, notes, plan)
            }
        }

        // Read once the variations exist, so picks can name them.
        val library = repository.observeLibrary().first().associateBy { it.name }

        val savedCircuits = repository.observeRoutines().first().map { it.name }.toSet()
        circuitSeeds.filter { it.name !in savedCircuits }.forEach { seed ->
            val entries = seed.stations.mapNotNull { pick ->
                library[pick.exercise]?.let { RoutineEntryDraft(it.id, pick.plan ?: it.defaultPrescription) }
            }
            if (entries.isEmpty()) return@forEach
            repository.createRoutine(
                RoutineDraft(
                    seed.name, seed.rounds, seed.transitionSeconds, seed.roundRestSeconds, entries,
                    category = seed.category,
                )
            )
        }

        val circuitsByName = repository.observeRoutines().first().associateBy { it.name }
        val savedModules = repository.observeModules().first().map { it.name }.toSet()
        moduleSeeds.filter { it.name !in savedModules }.forEach { seed ->
            val entries = seed.entries.mapNotNull { entry ->
                entry.circuit?.let { name ->
                    return@mapNotNull circuitsByName[name]?.let { ModuleEntryDraft(routineId = it.id) }
                }
                val pick = entry.pick ?: return@mapNotNull null
                val exercise = library[pick.exercise] ?: return@mapNotNull null
                val variation = pick.variation?.let { tag -> exercise.variations.firstOrNull { it.tag == tag } }
                ModuleEntryDraft(
                    exerciseId = exercise.id,
                    variationId = variation?.id,
                    prescription = pick.plan ?: variation?.prescription ?: exercise.defaultPrescription,
                )
            }
            if (entries.isEmpty()) return@forEach
            repository.createModule(ModuleDraft(seed.name, seed.description, entries))
        }
    }

    private fun exercise(
        name: String,
        mode: ExerciseMode,
        category: ExerciseCategory?,
        prescription: PrescriptionPayload,
        unilateral: Boolean = false,
        unit: String? = null,
        meaning: MeasurementMeaning? = null,
        description: String? = null,
        notes: String? = null,
    ) = ExerciseDraft(
        name = name,
        mode = mode,
        unilateral = unilateral,
        measurementUnit = unit,
        measurementMeaning = meaning,
        notes = notes,
        description = description,
        category = category,
        defaultPrescription = prescription,
    )

    private val exercises = listOf(
        // --- repetitions ---------------------------------------------------------------------
        exercise(
            "Pull-up", REPETITIONS, STRENGTH_CONDITIONING,
            PrescriptionPayload(sets = 4, targetReps = 6, restSeconds = 180, effort = EffortLevel.HARD),
            description = "Dead hang to chin over bar, no kip.",
        ),
        exercise(
            "Weighted pull-up", REPETITIONS, STRENGTH_CONDITIONING,
            PrescriptionPayload(sets = 5, targetReps = 3, restSeconds = 240),
            unit = "kg", meaning = ADDED_LOAD,
        ),
        exercise(
            "Band-assisted pull-up", REPETITIONS, STRENGTH_CONDITIONING,
            PrescriptionPayload(sets = 3, targetReps = 8, restSeconds = 120, effort = EffortLevel.MODERATE),
            unit = "kg", meaning = ASSISTANCE,
        ),
        exercise(
            "Back squat", REPETITIONS, STRENGTH_CONDITIONING,
            // A planned duration that overrules the estimate.
            PrescriptionPayload(
                sets = 5, targetReps = 5, restSeconds = 180,
                effort = EffortLevel.VERY_HARD, plannedDurationSeconds = 30 * 60,
            ),
            unit = "kg", meaning = TOTAL_LOAD,
        ),
        exercise(
            "Bulgarian split squat", REPETITIONS, STRENGTH_CONDITIONING,
            // Unilateral with the sides back to back.
            PrescriptionPayload(sets = 3, targetReps = 8, restSeconds = 90, sideSwitchSeconds = 0),
            unilateral = true, unit = "kg", meaning = TOTAL_LOAD,
        ),
        exercise(
            "Pistol squat", REPETITIONS, STRENGTH_CONDITIONING,
            // Unilateral, default switch, no rest, no unit.
            PrescriptionPayload(sets = 3, targetReps = 5),
            unilateral = true,
        ),
        exercise(
            "Push-up", REPETITIONS, null,
            // No target reps and no category.
            PrescriptionPayload(sets = 3, restSeconds = 60),
        ),
        exercise(
            "4×4 boulders", REPETITIONS, STRUCTURED_CLIMBING,
            PrescriptionPayload(sets = 4, targetReps = 4, restSeconds = 240, effort = EffortLevel.HARD),
            notes = "Four problems back to back, three grades below max.",
        ),

        // --- duration ------------------------------------------------------------------------
        exercise(
            "Max hangs 20 mm", DURATION, FINGER_TRAINING,
            PrescriptionPayload(sets = 5, targetDurationSeconds = 10, restSeconds = 180, effort = EffortLevel.HARD),
            unit = "kg", meaning = ADDED_LOAD,
            description = "Half crimp, both hands, feet on the floor for the first second.",
            notes = "Beastmaker 1000, middle edge",
        ),
        exercise(
            "One-arm hang", DURATION, FINGER_TRAINING,
            PrescriptionPayload(sets = 4, targetDurationSeconds = 7, restSeconds = 120, sideSwitchSeconds = 30),
            unilateral = true, unit = "kg", meaning = ASSISTANCE,
        ),
        exercise(
            "Plank", DURATION, STRENGTH_CONDITIONING,
            PrescriptionPayload(sets = 3, targetDurationSeconds = 60, restSeconds = 60),
        ),
        exercise(
            "Couch stretch", DURATION, FLEXIBILITY,
            PrescriptionPayload(sets = 2, targetDurationSeconds = 90),
            unilateral = true,
        ),
        exercise(
            "Dead hang", DURATION, FINGER_TRAINING,
            // One set: the timer button reads as a single length.
            PrescriptionPayload(sets = 1, targetDurationSeconds = 120),
        ),
        exercise(
            "Front lever hold", DURATION, STRENGTH_CONDITIONING,
            // Timed with no target length: no estimate, rest-only timer.
            PrescriptionPayload(sets = 4, restSeconds = 120),
        ),

        // --- repeaters -----------------------------------------------------------------------
        exercise(
            "7/3 repeaters 20 mm", REPEATERS, FINGER_TRAINING,
            PrescriptionPayload(
                sets = 3, restSeconds = 180, effort = EffortLevel.HARD,
                repeater = RepeaterPrescription(repsPerSet = 6, workSecondsPerRep = 7, restSecondsBetweenReps = 3),
            ),
            unit = "kg", meaning = ADDED_LOAD,
        ),
        exercise(
            "One-arm repeaters", REPEATERS, FINGER_TRAINING,
            PrescriptionPayload(
                sets = 3, restSeconds = 120, sideSwitchSeconds = 20,
                repeater = RepeaterPrescription(repsPerSet = 5, workSecondsPerRep = 10, restSecondsBetweenReps = 5),
            ),
            unilateral = true, unit = "kg", meaning = ASSISTANCE,
        ),
        exercise(
            "Continuous pulses", REPEATERS, FINGER_TRAINING,
            // No rest between pulses: the "/rest" part of the summary disappears.
            PrescriptionPayload(
                sets = 2, restSeconds = 90,
                repeater = RepeaterPrescription(repsPerSet = 4, workSecondsPerRep = 10, restSecondsBetweenReps = 0),
            ),
        ),
        exercise(
            "Repeaters, not set up", REPEATERS, FINGER_TRAINING,
            PrescriptionPayload(sets = 3, restSeconds = 180),
        ),

        // --- activity ------------------------------------------------------------------------
        exercise(
            "Bouldering session", ACTIVITY, OPEN_CLIMBING,
            PrescriptionPayload(sets = 1, targetDurationSeconds = 120 * 60, effort = EffortLevel.MODERATE),
        ),
        exercise(
            "Board session", ACTIVITY, BOARD_CLIMBING,
            PrescriptionPayload(sets = 1, targetDurationSeconds = 90 * 60, effort = EffortLevel.HARD),
            notes = "Moonboard 2019, 40°",
        ),
        exercise(
            "Run", ACTIVITY, OTHER_ACTIVITY,
            PrescriptionPayload(sets = 1, targetDurationSeconds = 45 * 60),
        ),
        exercise(
            "Yoga", ACTIVITY, FLEXIBILITY,
            // No duration: "Duration not set", and no estimate.
            PrescriptionPayload(sets = 1),
        ),
        exercise(
            "Hike (bad: sides, sets, rest, kg)", ACTIVITY, OTHER_ACTIVITY,
            // Contradictory on purpose: what an activity must never show, all at once.
            PrescriptionPayload(
                sets = 3, targetDurationSeconds = 20 * 60, restSeconds = 60,
                sideSwitchSeconds = 15,
            ),
            unilateral = true, unit = "kg", meaning = TOTAL_LOAD,
        ),

        // --- layout stress -------------------------------------------------------------------
        exercise(
            "Single-arm offset kettlebell bottoms-up Turkish get-up to windmill", REPETITIONS,
            STRENGTH_CONDITIONING,
            PrescriptionPayload(
                sets = 12, targetReps = 15, restSeconds = 3725,
                effort = EffortLevel.VERY_EASY, sideSwitchSeconds = 45,
            ),
            unilateral = true, unit = "kg", meaning = TOTAL_LOAD,
            description = "A long name, a long description and large numbers, to see what wraps " +
                "and what truncates. ".repeat(4).trim(),
            notes = "Rest over an hour, so the duration formatter's h:mm path shows up too.",
        ),
    )
}
