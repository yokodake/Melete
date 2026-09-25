package com.yokodake.melete.ui.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.data.Routine

/**
 * Something that can be trained on its own: an exercise, or a saved circuit.
 *
 * The two are listed together because they answer the same question — what will I do — and a
 * module is the only thing that groups them. Keeping circuits in a separate list made "where is my
 * pull circuit" a question about which screen it lived on.
 */
sealed interface Workout {
    val id: String
    val name: String

    data class Exercise(val exercise: LibraryExercise) : Workout {
        override val id: String get() = "exercise-${exercise.id}"
        override val name: String get() = exercise.name
    }

    data class Circuit(val routine: Routine) : Workout {
        override val id: String get() = "circuit-${routine.id}"
        override val name: String get() = routine.name
    }
}

/**
 * Exercises and circuits in one alphabetical list, narrowed by [query].
 *
 * An exercise matches on its name or its category; a circuit on its name or any exercise in it,
 * so searching "hang" finds the hangboard circuit as well as the hang itself.
 */
fun workoutsOf(
    exercises: List<LibraryExercise>,
    circuits: List<Routine>,
    query: String = "",
): List<Workout> {
    val q = query.trim()
    fun String.hit() = contains(q, ignoreCase = true)
    val matchingExercises = exercises
        .filter { q.isEmpty() || it.name.hit() || it.category?.label?.hit() == true }
        .map(Workout::Exercise)
    val matchingCircuits = circuits
        .filter { q.isEmpty() || it.name.hit() || it.entries.any { entry -> entry.name.hit() } }
        .map(Workout::Circuit)
    return (matchingExercises + matchingCircuits).sortedBy { it.name.lowercase() }
}

/** The two halves of the library. */
enum class LibraryTab(val label: String) {
    WORKOUTS("Workouts"),
    MODULES("Modules"),
}

/** What a new workout will be. Chosen at the top of the editor, and deciding the rest of it. */
enum class WorkoutKind(val label: String) {
    EXERCISE("Exercise"),
    CIRCUIT("Circuit"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Enum<T>> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                // The label says it; a tick as well only costs width.
                icon = {},
            ) { Text(label(option)) }
        }
    }
}

/** Workouts | Modules, for the library tab and the week's picker alike. */
@Composable
fun LibraryTabs(selected: LibraryTab, onSelect: (LibraryTab) -> Unit) =
    SegmentedChoice(
        options = LibraryTab.entries,
        selected = selected,
        label = LibraryTab::label,
        onSelect = onSelect,
        // Under a top bar, so it takes the page's gutters itself.
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )

/** Exercise | Circuit, at the top of a new workout. */
@Composable
fun WorkoutKindSwitch(selected: WorkoutKind, onSelect: (WorkoutKind) -> Unit) =
    SegmentedChoice(WorkoutKind.entries, selected, WorkoutKind::label, onSelect)
