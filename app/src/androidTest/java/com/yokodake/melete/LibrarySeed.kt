package com.yokodake.melete

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.ExerciseDraft
import com.yokodake.melete.data.MeleteDatabase
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
 * Re-running adds only the names that are missing. Stands in until the importer exists.
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
        } finally {
            database.close()
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
