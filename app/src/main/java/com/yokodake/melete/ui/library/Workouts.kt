package com.yokodake.melete.ui.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.yokodake.melete.ui.components.CompactTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.LibraryExercise
import com.yokodake.melete.data.Routine
import com.yokodake.melete.data.TrainingModule
import com.yokodake.melete.data.model.ExerciseCategory

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
 * The library's search: commas separate conditions, and an item must meet every one.
 *
 * A condition is plain text — spaces inside it are part of the text, not another operator. It
 * matches a workout's own text (its name; for a circuit, the exercises in it), its own category by
 * any part of its name or the start of its abbreviation (`finger`, `FN`, `S&C`), or what kind of
 * workout it is (`circuit`, `exercise`, as whole words). `!circuit` leaves circuits out; wider
 * negation is not built. A circuit's category is the circuit's, never one borrowed from its
 * stations.
 */
object LibrarySearch {

    /** The conditions in [query]: trimmed, lower-cased, empty fragments dropped. */
    fun conditions(query: String): List<String> =
        query.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun matches(workout: Workout, conditions: List<String>): Boolean = conditions.all { condition ->
        // The one negation there is so far: everything but circuits.
        if (condition in NOT_CIRCUIT_WORDS) return@all workout !is Workout.Circuit
        when (workout) {
            is Workout.Exercise -> workout.exercise.name.hit(condition) ||
                categoryHit(workout.exercise.category, condition) ||
                condition in EXERCISE_WORDS

            is Workout.Circuit -> workout.routine.name.hit(condition) ||
                workout.routine.entries.any { it.name.hit(condition) } ||
                categoryHit(workout.routine.category, condition) ||
                condition in CIRCUIT_WORDS
        }
    }

    /** Modules have no category and no kind to choose between: text only, every condition. */
    fun matches(module: TrainingModule, conditions: List<String>): Boolean = conditions.all { condition ->
        // A module is not a circuit, so leaving circuits out leaves it in.
        condition in NOT_CIRCUIT_WORDS ||
            module.name.hit(condition) || module.entries.any { it.name.hit(condition) }
    }

    /** A category by any part of its name, or by the start of its abbreviation: `fn` is FNGR. */
    private fun categoryHit(category: ExerciseCategory?, condition: String): Boolean =
        category != null &&
            (category.label.hit(condition) || category.shortLabel.startsWith(condition, ignoreCase = true))

    private fun String.hit(condition: String) = contains(condition, ignoreCase = true)

    private val EXERCISE_WORDS = setOf("exercise", "exercises")
    private val CIRCUIT_WORDS = setOf("circuit", "circuits")
    private val NOT_CIRCUIT_WORDS = setOf("!circuit", "!circuits")
}

/**
 * Exercises and circuits in one alphabetical list, narrowed by [query] as [LibrarySearch] reads it.
 *
 * Searching "hang" finds the hangboard circuit as well as the hang itself; "circuit, FNGR" finds
 * only the circuits filed under finger training.
 */
fun workoutsOf(
    exercises: List<LibraryExercise>,
    circuits: List<Routine>,
    query: String = "",
): List<Workout> {
    val conditions = LibrarySearch.conditions(query)
    return (exercises.map(Workout::Exercise) + circuits.map(Workout::Circuit))
        .filter { LibrarySearch.matches(it, conditions) }
        .sortedBy { it.name.lowercase() }
}

/** The modules [query] finds, in the order given. */
fun modulesOf(modules: List<TrainingModule>, query: String = ""): List<TrainingModule> {
    val conditions = LibrarySearch.conditions(query)
    return modules.filter { LibrarySearch.matches(it, conditions) }
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

/** The one search box, for the library and the week's picker alike. */
@Composable
fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit) {
    CompactTextField(
        value = query,
        onValueChange = onQueryChange,
        label = "Search",
        minHeight = 48,
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = { onQueryChange("") }) { Text("Clear") }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Exercise | Circuit, at the top of a new workout. */
@Composable
fun WorkoutKindSwitch(selected: WorkoutKind, onSelect: (WorkoutKind) -> Unit) =
    SegmentedChoice(WorkoutKind.entries, selected, WorkoutKind::label, onSelect)
