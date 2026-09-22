package com.yokodake.melete.data

import com.yokodake.melete.data.model.ExerciseMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which of the three removals an exercise qualifies for.
 *
 * The rule is the user's: keep the fact that something was done, never the fact that it was
 * merely planned. A single logged set is therefore the whole difference between a row that can be
 * deleted and one that has to survive as an anchor, and it outranks any number of planned copies.
 */
class ExerciseRemovalTest {

    private fun removal(plannedCopies: Int, loggedSets: Int) = ExerciseRemoval(
        exercise = LibraryExercise(
            id = "pull-up",
            name = "Pull-up",
            mode = ExerciseMode.REPETITIONS,
            unilateral = false,
            measurementUnit = null,
            measurementMeaning = null,
            notes = null,
            description = null,
            category = null,
            defaultPrescription = null,
        ),
        plannedCopies = plannedCopies,
        loggedSets = loggedSets,
    )

    @Test
    fun anExerciseNothingRefersToIsDeletedOutright() {
        assertEquals(ExerciseRemoval.Kind.UNUSED, removal(plannedCopies = 0, loggedSets = 0).kind)
    }

    @Test
    fun plannedButNeverTrainedTakesItsPlansWithIt() {
        assertEquals(
            ExerciseRemoval.Kind.PLANNED_NEVER_LOGGED,
            removal(plannedCopies = 3, loggedSets = 0).kind,
        )
    }

    @Test
    fun oneLoggedSetIsEnoughToKeepTheRow() {
        assertEquals(
            ExerciseRemoval.Kind.LOGGED,
            removal(plannedCopies = 1, loggedSets = 1).kind,
        )
    }

    @Test
    fun logsOutrankAnyNumberOfPlans() {
        assertEquals(
            ExerciseRemoval.Kind.LOGGED,
            removal(plannedCopies = 40, loggedSets = 1).kind,
        )
    }

    /**
     * A set whose planned copy was already removed still protects the definition: history is
     * counted on the exercise's own lineage, not on what currently sits in a week.
     */
    @Test
    fun aLogWithoutAnySurvivingPlanStillKeepsTheRow() {
        assertEquals(
            ExerciseRemoval.Kind.LOGGED,
            removal(plannedCopies = 0, loggedSets = 5).kind,
        )
    }
}
