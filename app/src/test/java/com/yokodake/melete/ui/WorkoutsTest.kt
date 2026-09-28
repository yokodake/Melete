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

    // ------------------------------------------------ comma conditions

    private val searchable = listOf(
        exercise("Pull-up", ExerciseCategory.STRENGTH_CONDITIONING),
        exercise("Pull-up negative", ExerciseCategory.STRENGTH_CONDITIONING),
        exercise("Pullover stretch", ExerciseCategory.FLEXIBILITY),
        exercise("Max hangs", ExerciseCategory.FINGER_TRAINING),
    )
    private val searchableCircuits = listOf(
        circuit("Repeater density", "Dead hang").copy(category = ExerciseCategory.FINGER_TRAINING),
        circuit("Pull circuit", "Pull-up").copy(category = ExerciseCategory.STRENGTH_CONDITIONING),
        // Holds a finger exercise, but is not itself filed under finger training.
        circuit("Mixed", "Max hangs"),
    )

    private fun search(query: String) = workoutsOf(searchable, searchableCircuits, query).map { it.name }

    @Test
    fun `commas are AND, and a condition can be a category abbreviation`() {
        assertEquals(listOf("Pull circuit", "Pull-up", "Pull-up negative"), search("pull, S&C"))
        assertEquals(listOf("Pull circuit", "Pull-up", "Pull-up negative"), search("pull,s&c"))
    }

    @Test
    fun `a kind and a category narrow to circuits in that category, not by their stations`() {
        assertEquals(listOf("Repeater density"), search("circuit, FNGR"))
        assertEquals(listOf("Max hangs"), search("exercise, fngr"))
    }

    @Test
    fun `category names match case-insensitively and empty fragments are ignored`() {
        assertEquals(search("FNGR"), search(" , finger training ,, "))
        assertEquals(search(""), search(" , ,"))
    }

    @Test
    fun `an abbreviation matches from its start, so FN lists all of finger training`() {
        assertEquals(listOf("Max hangs", "Repeater density"), search("FN"))
        assertEquals(listOf("Max hangs", "Repeater density"), search("fngr"))
    }

    @Test
    fun `not-circuit leaves circuits out and combines with the rest`() {
        assertEquals(listOf("Max hangs"), search("!circuit, fn"))
        assertEquals(listOf("Pull-up", "Pull-up negative"), search("pull, !circuit, S&C"))
        // Kinds stay whole words: "circ" is only text, and nothing is called that.
        assertTrue(search("circ, fn").isEmpty())
    }

    @Test
    fun `spaces stay part of the text rather than splitting it`() {
        assertEquals(listOf("Pull-up negative"), search("up neg"))
        assertTrue(search("pull stretch").isEmpty())
    }

    @Test
    fun `modules match every condition by text only`() {
        val module = com.yokodake.melete.data.TrainingModule(
            id = "m", name = "Upper A", description = null, entries = emptyList(),
        )
        assertEquals(1, com.yokodake.melete.ui.library.modulesOf(listOf(module), "upper, a").size)
        assertTrue(com.yokodake.melete.ui.library.modulesOf(listOf(module), "upper, lower").isEmpty())
        assertTrue(com.yokodake.melete.ui.library.modulesOf(listOf(module), "S&C").isEmpty())
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
