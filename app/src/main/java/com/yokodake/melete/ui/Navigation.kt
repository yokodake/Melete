package com.yokodake.melete.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.R
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.ui.detail.ExerciseDetailRoute
import com.yokodake.melete.ui.library.ExerciseEditorRoute
import com.yokodake.melete.ui.library.LibraryPickerRoute
import com.yokodake.melete.ui.library.LibraryRoute
import com.yokodake.melete.ui.logger.LoggerRoute
import com.yokodake.melete.ui.timer.TimerRoute
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
data object LibraryDestination

@Serializable
data object TimerDestination

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

/**
 * What an exercise *is*, before anything is asked of the user. One destination serves both the
 * planned copy in a week and the library entry behind it, because the page is the same page —
 * only what you can do from it differs.
 */
@Serializable
data class ExerciseDetailDestination(
    val occurrenceId: String? = null,
    val exerciseId: String? = null,
)

@Serializable
data class LoggerDestination(val occurrenceId: String)

/**
 * The tabs of the app. Focused flows opened from a tab — the picker, the exercise editor, the
 * logger — deliberately hide the bar: they are one task with a back button, not a place to switch
 * away from mid-set.
 */
private enum class Tab(
    val label: String,
    @param:DrawableRes val icon: Int,
    val route: Any,
    val matches: (NavDestination) -> Boolean,
) {
    WEEK("Week", R.drawable.ic_nav_week, WeekDestination, { it.hasRoute<WeekDestination>() }),
    LIBRARY(
        "Library",
        R.drawable.ic_nav_library,
        LibraryDestination,
        { it.hasRoute<LibraryDestination>() },
    ),
    TIMER("Timer", R.drawable.ic_nav_timer, TimerDestination, { it.hasRoute<TimerDestination>() }),
}

@Composable
fun MeleteApp(navController: NavHostController = rememberNavController()) {
    KeepScreenOnWhileCountingDown()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val bottomBar: @Composable () -> Unit = {
        MeleteNavigationBar(
            current = backStackEntry?.destination,
            onSelect = { tab -> navController.switchTab(tab.route) },
        )
    }

    NavHost(navController = navController, startDestination = WeekDestination) {
        composable<WeekDestination> {
            WeekRoute(
                onOpenOccurrence = {
                    navController.navigate(ExerciseDetailDestination(occurrenceId = it))
                },
                onAddExercise = { weekStart, date ->
                    navController.navigate(
                        LibraryPickerDestination(
                            weekStartEpochDay = weekStart.toEpochDay(),
                            trainingDateEpochDay = date?.toEpochDay() ?: NO_TRAINING_DATE,
                        )
                    )
                },
                bottomBar = bottomBar,
            )
        }
        composable<LibraryDestination> {
            LibraryRoute(
                onOpenExercise = {
                    navController.navigate(ExerciseDetailDestination(exerciseId = it))
                },
                onNewExercise = { navController.navigate(ExerciseEditorDestination()) },
                bottomBar = bottomBar,
            )
        }
        composable<TimerDestination> {
            TimerRoute(bottomBar = bottomBar)
        }
        composable<LibraryPickerDestination> {
            LibraryPickerRoute(
                onScheduled = { navController.popBackStack() },
                onNewExercise = { navController.navigate(ExerciseEditorDestination()) },
                onOpenExercise = {
                    navController.navigate(ExerciseDetailDestination(exerciseId = it))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ExerciseEditorDestination> {
            ExerciseEditorRoute(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ExerciseDetailDestination> {
            ExerciseDetailRoute(
                onLog = { navController.navigate(LoggerDestination(it)) },
                // Starting a countdown moves to the timer tab, which is where it lives for as
                // long as it runs; the exercise stays behind it on the back stack.
                onOpenTimer = { navController.switchTab(TimerDestination) },
                onEditExercise = { navController.navigate(ExerciseEditorDestination(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<LoggerDestination> {
            LoggerRoute(onBack = { navController.popBackStack() })
        }
    }
}

/**
 * Holds the screen awake while a countdown is actually running.
 *
 * This is a window flag rather than a wake lock, so it only applies while the app is on screen and
 * cannot leave the display on once you put the phone down — and it is tied to a running countdown
 * rather than to the timer tab being open, so parking on that tab does not burn the screen. Pause,
 * cancel or the end releases it immediately.
 */
@Composable
private fun KeepScreenOnWhileCountingDown() {
    val application = LocalContext.current.applicationContext as? MeleteApplication ?: return
    val timerState by application.container.timerController.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val keepAwake = timerState is TimerState.Running
    DisposableEffect(view, keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun MeleteNavigationBar(current: NavDestination?, onSelect: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            val selected = current?.hierarchy()?.any(tab.matches) == true
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}

private fun NavDestination.hierarchy(): Sequence<NavDestination> =
    generateSequence(this) { it.parent }

/**
 * Switching tabs keeps a single entry per tab on the back stack and restores where that tab was,
 * so leaving the week to glance at the library and coming back does not reset the shown week.
 */
private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
