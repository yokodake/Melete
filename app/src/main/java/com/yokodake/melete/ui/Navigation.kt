package com.yokodake.melete.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yokodake.melete.ui.library.ExerciseEditorRoute
import com.yokodake.melete.ui.library.LibraryPickerRoute
import com.yokodake.melete.ui.logger.LoggerRoute
import com.yokodake.melete.ui.week.WeekRoute
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Navigation-argument sentinel for "no training date". Type-safe routes cannot carry a nullable
 * `Long`, and every real epoch day is a valid value, so the sentinel has to sit outside the range.
 */
const val NO_TRAINING_DATE: Long = Long.MIN_VALUE

@Serializable
data object WeekDestination

@Serializable
data class LibraryPickerDestination(
    val weekStartEpochDay: Long,
    val trainingDateEpochDay: Long = NO_TRAINING_DATE,
) {
    val trainingDate: LocalDate?
        get() = trainingDateEpochDay.takeIf { it != NO_TRAINING_DATE }?.let(LocalDate::ofEpochDay)
}

@Serializable
data class ExerciseEditorDestination(val exerciseId: String? = null)

@Serializable
data class LoggerDestination(val occurrenceId: String)

@Composable
fun MeleteApp(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = WeekDestination) {
        composable<WeekDestination> {
            WeekRoute(
                onOpenOccurrence = { navController.navigate(LoggerDestination(it)) },
                onAddExercise = { weekStart, date ->
                    navController.navigate(
                        LibraryPickerDestination(
                            weekStartEpochDay = weekStart.toEpochDay(),
                            trainingDateEpochDay = date?.toEpochDay() ?: NO_TRAINING_DATE,
                        )
                    )
                },
            )
        }
        composable<LibraryPickerDestination> {
            LibraryPickerRoute(
                onScheduled = { navController.popBackStack() },
                onNewExercise = { navController.navigate(ExerciseEditorDestination()) },
                onEditExercise = { navController.navigate(ExerciseEditorDestination(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ExerciseEditorDestination> {
            ExerciseEditorRoute(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable<LoggerDestination> {
            LoggerRoute(onBack = { navController.popBackStack() })
        }
    }
}
