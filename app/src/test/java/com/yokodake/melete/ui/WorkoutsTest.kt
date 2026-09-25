package com.yokodake.melete.ui

import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.data.Routine
import com.yokodake.melete.data.RoutineEntryView
import com.yokodake.melete.data.model.ExerciseCategory
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.timer.TimerTransitions
import com.yokodake.melete.ui.library.Workout
import com.yokodake.melete.ui.library.workoutsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutsTest {

    private fun exercise(name: String, category: ExerciseCategory? = null) = LibraryExercise(
        id = name,
        name = name,
        mode = ExerciseMode.REPETITIONS,
        unilateral = false,
        measurementUnit = null,
        measurementMeaning = null,
        notes = null,
        description = null,
        category = category,
        defaultPrescription = null,
    )

    private fun circuit(name: String, vararg stations: String) = Routine(
        id = name,
        name = name,
        rounds = 3,
        transitionSeconds = 30,
        roundRestSeconds = 120,
        structureVersion = 1,
        entries = stations.mapIndexed { index, station ->
            RoutineEntryView(
                id = "$name-$index",
                exerciseId = station,
                name = station,
                mode = ExerciseMode.REPETITIONS,
                unilateral = false,
                measurementUnit = null,
                measurementMeaning = null,
                category = null,
                prescription = null,
                orderIndex = index,
                definitionMissing = false,
            )
        },
    )

    private val exercises = listOf(
        exercise("Pull-up"),
        exercise("Max hangs", ExerciseCategory.FINGER_TRAINING),
        exercise("back squat"),
    )
    private val circuits = listOf(circuit("Leg circuit", "Pistol squat"), circuit("Hangboard density", "Dead hang"))

    @Test
    fun `exercises and circuits are one alphabetical list, ignoring case`() {
        assertEquals(
            listOf("back squat", "Hangboard density", "Leg circuit", "Max hangs", "Pull-up"),
            workoutsOf(exercises, circuits).map { it.name },
        )
    }

    @Test
    fun `each entry keeps what it is, and keys cannot collide across kinds`() {
        val same = workoutsOf(listOf(exercise("Core")), listOf(circuit("Core")))
        assertEquals(2, same.size)
        assertEquals(2, same.map { it.id }.toSet().size)
        assertTrue(same.any { it is Workout.Exercise } && same.any { it is Workout.Circuit })
    }

    @Test
    fun `a search finds circuits by what is in them and exercises by their category`() {
        // "hang" is in an exercise's name and in a circuit's station.
        assertEquals(
            listOf("Hangboard density", "Max hangs"),
            workoutsOf(exercises, circuits, "hang").map { it.name },
        )
        assertEquals(listOf("Leg circuit"), workoutsOf(exercises, circuits, "pistol").map { it.name })
        assertEquals(listOf("Max hangs"), workoutsOf(exercises, circuits, "finger").map { it.name })
        assertTrue(workoutsOf(exercises, circuits, "rowing").isEmpty())
    }

    // ------------------------------------------------ the previous button's promise

    @Test
    fun `previous restarts an interval that has been running, and steps back otherwise`() {
        assertTrue(TimerTransitions.previousRestarts(elapsedMs = 60_000))
        assertFalse(TimerTransitions.previousRestarts(elapsedMs = 200))
        // A set of reps has nothing counting, so going back is all it can mean.
        assertFalse(TimerTransitions.previousRestarts(elapsedMs = null))
    }
}
