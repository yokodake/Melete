package com.yokodake.melete.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import com.yokodake.melete.ui.benchmark.BenchmarkDetailRoute
import com.yokodake.melete.ui.benchmark.BenchmarkEditorRoute
import com.yokodake.melete.ui.benchmark.BenchmarksRoute
import com.yokodake.melete.ui.dashboard.DashboardRoute
import com.yokodake.melete.ui.home.HomeRoute
import com.yokodake.melete.data.KeepAwake
import com.yokodake.melete.ui.settings.SettingsRoute
import androidx.compose.runtime.collectAsState
import com.yokodake.melete.ui.module.ModuleDetailRoute
import com.yokodake.melete.ui.routine.RoutineDetailRoute
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
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yokodake.melete.MeleteApplication
import com.yokodake.melete.R
import com.yokodake.melete.data.timer.TimerState
import com.yokodake.melete.ui.detail.ExerciseDetailRoute
import com.yokodake.melete.ui.library.ExerciseEditorRoute
import com.yokodake.melete.ui.library.LibraryPickerRoute
import com.yokodake.melete.ui.library.LibraryRoute
import com.yokodake.melete.ui.backup.BackupRoute
import com.yokodake.melete.ui.circuit.CircuitDetailRoute
import com.yokodake.melete.ui.circuit.CircuitReviewRoute
import com.yokodake.melete.ui.logger.LoggerRoute
import com.yokodake.melete.ui.module.ModuleEditorRoute
import com.yokodake.melete.ui.routine.RoutineEditorRoute
import com.yokodake.melete.ui.timer.TimerRoute
import com.yokodake.melete.ui.week.WeekRoute
import com.yokodake.melete.ui.diary.DiaryRoute
import com.yokodake.melete.ui.diary.TrackersRoute
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
data class ExerciseEditorDestination(
    val exerciseId: String? = null,
    /** A new workout, not yet an exercise or a circuit: the editor offers the other kind. */
    val choosingKind: Boolean = false,
)

/**
 * The saved-state key the exercise editor leaves on the screen below it when it has just created
 * an exercise, holding the new id.
 */
const val CREATED_EXERCISE_ID = "createdExerciseId"

/** The same hand-back for a circuit just created from the circuit editor. */
const val CREATED_CIRCUIT_ID = "createdCircuitId"

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
data class LoggerDestination(
    val occurrenceId: String,
    /**
     * The occurrence was created only to be logged — from the library, not placed in a week by
     * hand — so leaving without saving takes it away again rather than leaving a stray plan.
     */
    val discardIfUnlogged: Boolean = false,
)

/** A saved circuit, read from the library. */
@Serializable
data class RoutineDetailDestination(val routineId: String)

/** A saved module, read from the library. */
@Serializable
data class ModuleDetailDestination(val moduleId: String)

@Serializable
data class RoutineEditorDestination(
    val routineId: String? = null,
    /** A new workout, not yet an exercise or a circuit: the editor offers the other kind. */
    val choosingKind: Boolean = false,
)

@Serializable
data class ModuleEditorDestination(val moduleId: String? = null)

/** Export and restore of the whole record. */
@Serializable
data class BackupDestination(
    /** Opens the plan file picker straight away: Home's Import plan shortcut. */
    val pickPlan: Boolean = false,
)

/** Settings: Import / export, and the preferences to come. */
@Serializable
data object SettingsDestination

/** One day's notes and trackers. */
@Serializable
data class DiaryDestination(val dateEpochDay: Long)

/** What the diary tracks each day. */
@Serializable
data object TrackersDestination

/**
 * What a scheduled circuit is, and the two things to do with it.
 *
 * The counterpart of [ExerciseDetailDestination]: a circuit opens as information, not as a form,
 * for exactly the same reason an exercise does.
 */
@Serializable
data class CircuitDetailDestination(val circuitInstanceId: String)

/** Logging a whole scheduled circuit in one review. */
@Serializable
data class CircuitReviewDestination(val circuitInstanceId: String)

/** The Home tab: today at a glance, and the places that are not part of a training day. */
@Serializable
data object HomeDestination

/**
 * The saved-state key on the week's entry that asks it to show today — the current week, scrolled
 * to the day. Its value changes with every request, so asking twice asks twice.
 */
const val GO_TO_TODAY = "goToToday"

/** Every benchmark and where it stands. */
@Serializable
data object BenchmarksDestination

/** One benchmark: latest, best and every result. */
@Serializable
data class BenchmarkDetailDestination(val benchmarkId: String)

/** A benchmark's definition, new or existing. */
@Serializable
data class BenchmarkEditorDestination(val benchmarkId: String? = null)

/** The Dashboard tab. */
@Serializable
data object DashboardDestination

/**
 * The tabs of the app, in the order Home · Calendar · Timer · Dashboard. Focused flows
 * opened from a tab — the picker, the exercise editor, the logger — deliberately hide the bar: they
 * are one task with a back button, not a place to switch away from mid-set.
 */
private enum class Tab(
    val label: String,
    @param:DrawableRes val icon: Int,
    val route: Any,
    val matches: (NavDestination) -> Boolean,
) {
    HOME("Home", R.drawable.ic_nav_home, HomeDestination, { it.hasRoute<HomeDestination>() }),
    CALENDAR("Calendar", R.drawable.ic_nav_week, WeekDestination, { it.hasRoute<WeekDestination>() }),
    TIMER("Timer", R.drawable.ic_nav_timer, TimerDestination, { it.hasRoute<TimerDestination>() }),
    DASHBOARD(
        "Dashboard",
        R.drawable.ic_nav_dashboard,
        DashboardDestination,
        { it.hasRoute<DashboardDestination>() },
    ),
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
    MeleteNavHost(navController, bottomBar)
}

@Composable
private fun MeleteNavHost(navController: NavHostController, bottomBar: @Composable () -> Unit) {
    NavHost(navController = navController, startDestination = HomeDestination) {
        composable<HomeDestination> {
            HomeRoute(
                onOpenToday = {
                    navController.switchTab(WeekDestination)
                    navController.getBackStackEntry<WeekDestination>().savedStateHandle[GO_TO_TODAY] =
                        System.nanoTime()
                },
                onOpenDailyNote = { navController.navigate(DiaryDestination(it.toEpochDay())) },
                onOpenBenchmarks = { navController.navigate(BenchmarksDestination) },
                onOpenLibrary = { navController.navigate(LibraryDestination) },
                onImportPlan = { navController.navigate(BackupDestination(pickPlan = true)) },
                onOpenSettings = { navController.navigate(SettingsDestination) },
                bottomBar = bottomBar,
            )
        }
        composable<BenchmarksDestination> {
            BenchmarksRoute(
                onOpen = { navController.navigate(BenchmarkDetailDestination(it)) },
                onNew = { navController.navigate(BenchmarkEditorDestination()) },
                onEdit = { navController.navigate(BenchmarkEditorDestination(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<BenchmarkDetailDestination> {
            BenchmarkDetailRoute(
                onEdit = { navController.navigate(BenchmarkEditorDestination(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<BenchmarkEditorDestination> {
            BenchmarkEditorRoute(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable<DashboardDestination> {
            DashboardRoute(bottomBar = bottomBar)
        }
        composable<WeekDestination> { entry ->
            val todayRequest by entry.savedStateHandle.getStateFlow(GO_TO_TODAY, 0L).collectAsState()
            WeekRoute(
                todayRequest = todayRequest,
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
                onOpenCircuit = { navController.navigate(CircuitDetailDestination(it)) },
                onOpenDiary = { navController.navigate(DiaryDestination(it.toEpochDay())) },
                onOpenBenchmark = { navController.navigate(BenchmarkDetailDestination(it)) },
                bottomBar = bottomBar,
            )
        }
        composable<LibraryDestination> {
            LibraryRoute(
                onOpenExercise = {
                    navController.navigate(ExerciseDetailDestination(exerciseId = it))
                },
                onOpenCircuit = { navController.navigate(RoutineDetailDestination(it)) },
                onOpenModule = { navController.navigate(ModuleDetailDestination(it)) },
                onNewWorkout = {
                    navController.navigate(ExerciseEditorDestination(choosingKind = true))
                },
                onEditCircuit = { navController.navigate(RoutineEditorDestination(it)) },
                onNewModule = { navController.navigate(ModuleEditorDestination()) },
                onEditModule = { navController.navigate(ModuleEditorDestination(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<RoutineDetailDestination> {
            RoutineDetailRoute(
                onEdit = { navController.navigate(RoutineEditorDestination(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ModuleDetailDestination> {
            ModuleDetailRoute(
                onEdit = { navController.navigate(ModuleEditorDestination(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<TimerDestination> {
            TimerRoute(
                onLog = { navController.navigate(LoggerDestination(it)) },
                onReviewCircuit = { navController.navigate(CircuitReviewDestination(it)) },
                bottomBar = bottomBar,
            )
        }
        composable<LibraryPickerDestination> {
            LibraryPickerRoute(
                onScheduled = { navController.popBackStack() },
                onNewWorkout = {
                    navController.navigate(ExerciseEditorDestination(choosingKind = true))
                },
                onNewModule = { navController.navigate(ModuleEditorDestination()) },
                onOpenExercise = {
                    navController.navigate(ExerciseDetailDestination(exerciseId = it))
                },
                onOpenCircuit = { navController.navigate(RoutineDetailDestination(it)) },
                onOpenModule = { navController.navigate(ModuleDetailDestination(it)) },
                onEditModule = { navController.navigate(ModuleEditorDestination(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ExerciseEditorDestination> { entry ->
            val destination = entry.toRoute<ExerciseEditorDestination>()
            ExerciseEditorRoute(
                // Switching kind replaces this editor rather than stacking the other on top, so
                // back still leads to wherever the new workout was asked for.
                onSwitchToCircuit = {
                    navController.navigate(RoutineEditorDestination(choosingKind = true)) {
                        popUpTo<ExerciseEditorDestination> { inclusive = true }
                    }
                }.takeIf { destination.choosingKind },
                onDone = { createdId ->
                    // Handed back to whoever opened the editor, so a screen that asked for a new
                    // exercise can use it straight away. Screens that did not ask never read it.
                    if (createdId != null) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(CREATED_EXERCISE_ID, createdId)
                    }
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ExerciseDetailDestination> {
            ExerciseDetailRoute(
                onLog = { id, discardIfUnlogged ->
                    navController.navigate(LoggerDestination(id, discardIfUnlogged))
                },
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
        composable<RoutineEditorDestination> { entry ->
            val destination = entry.toRoute<RoutineEditorDestination>()
            RoutineEditorRoute(
                onSwitchToExercise = {
                    navController.navigate(ExerciseEditorDestination(choosingKind = true)) {
                        popUpTo<RoutineEditorDestination> { inclusive = true }
                    }
                }.takeIf { destination.choosingKind },
                onNewExercise = { navController.navigate(ExerciseEditorDestination()) },
                onDone = { createdId ->
                    // Handed back like a new exercise, for a module waiting on this circuit.
                    if (createdId != null) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(CREATED_CIRCUIT_ID, createdId)
                    }
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ModuleEditorDestination> {
            ModuleEditorRoute(
                onNewExercise = { navController.navigate(ExerciseEditorDestination()) },
                onNewCircuit = { navController.navigate(RoutineEditorDestination()) },
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable<SettingsDestination> {
            SettingsRoute(
                onOpenImportExport = { navController.navigate(BackupDestination()) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<BackupDestination> { entry ->
            BackupRoute(
                pickPlan = entry.toRoute<BackupDestination>().pickPlan,
                onBack = { navController.popBackStack() },
            )
        }
        composable<DiaryDestination> {
            DiaryRoute(
                onBack = { navController.popBackStack() },
                onEditTrackers = { navController.navigate(TrackersDestination) },
            )
        }
        composable<TrackersDestination> {
            TrackersRoute(onBack = { navController.popBackStack() })
        }
        composable<CircuitDetailDestination> {
            CircuitDetailRoute(
                onLog = { navController.navigate(CircuitReviewDestination(it)) },
                // Starting a countdown moves to the timer tab, which is where it lives for as
                // long as it runs; the circuit stays behind it on the back stack.
                onOpenTimer = { navController.switchTab(TimerDestination) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<CircuitReviewDestination> {
            CircuitReviewRoute(onBack = { navController.popBackStack() })
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
    val setting by application.container.settings.keepAwake.collectAsStateWithLifecycle()
    val view = LocalView.current
    // "Always" holds it for as long as the app is in front: reading, logging between sets.
    val keepAwake = setting == KeepAwake.ALWAYS || timerState is TimerState.Running
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
