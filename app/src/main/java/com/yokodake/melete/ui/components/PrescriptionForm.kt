package com.yokodake.melete.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yokodake.melete.data.model.EffortLevel
import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.PrescriptionPayload
import com.yokodake.melete.data.model.RepeaterPrescription
import com.yokodake.melete.data.timer.DurationEstimate
import com.yokodake.melete.data.timer.TimerProgram
import com.yokodake.melete.ui.week.PrescriptionSummary

/**
 * Raw text state for prescription input. Values are kept as typed so half-finished input survives
 * recomposition and navigation, and so an empty field stays *absent* instead of becoming zero.
 *
 * There is deliberately no load here. What a plan fixes is the shape of the work — how many sets,
 * how long or how many reps, how much rest, how hard it should feel — while the weight on the bar
 * is what the day decides and what the logger records. A prescribed load would be a number the
 * user has to argue with before every set.
 */
data class PrescriptionFormState(
    val sets: String = "3",
    val targetReps: String = "",
    val targetDurationSeconds: String = "",
    val restSeconds: String = "",
    val effort: EffortLevel? = null,
    /**
     * An activity's length, in whole minutes. Separate from [targetDurationSeconds] because a set
     * is seconds and a session is minutes, and one field holding either depending on the mode
     * would change meaning under the text when the mode is switched.
     */
    val activityMinutes: String = "",
    /**
     * Whole minutes, because a plan of an hour typed in seconds is a plan nobody types. Empty asks
     * for the estimate rather than meaning zero.
     */
    val plannedDurationMinutes: String = "",
    /** Empty means the default. Zero is a real answer and runs the sides back to back. */
    val sideSwitchSeconds: String = "",
    val repeaterReps: String = "",
    val repeaterWorkSeconds: String = "",
    val repeaterRestSeconds: String = "",
) {
    /** The repeater the fields describe, or null when they do not describe one yet. */
    fun toRepeater(): RepeaterPrescription? {
        val reps = repeaterReps.toIntOrNull() ?: return null
        val work = repeaterWorkSeconds.toIntOrNull() ?: return null
        if (reps < 1 || work < 1) return null
        return RepeaterPrescription(
            repsPerSet = reps,
            workSecondsPerRep = work,
            restSecondsBetweenReps = repeaterRestSeconds.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        )
    }

    /**
     * The payload these fields describe.
     *
     * The mode is an argument rather than a field because it belongs to the *exercise*, not to the
     * plan: it is what decides whether the pulse numbers mean anything, so passing it here is what
     * stops a plain timed set from quietly carrying a repeater it never shows.
     *
     * Written with `measurement = null`. An older prescription that still carries a load keeps it
     * on disk — rows are never mutated — but re-saving one drops it, which is the intended
     * migration away from prescribed weight.
     *
     * An activity is one block of time and nothing else: one set, its length, how hard. Whatever
     * the other fields still hold from before the mode was switched is dropped here rather than
     * merely hidden, so no form — editor, week copy or routine station — can save an activity
     * with rests or sides it would never show.
     */
    fun toPayload(mode: ExerciseMode): PrescriptionPayload =
        if (!mode.hasSetStructure) {
            PrescriptionPayload(
                sets = 1,
                targetDurationSeconds = activityMinutes.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { it * 60 },
                effort = effort,
            )
        } else {
            PrescriptionPayload(
                sets = sets.toIntOrNull()?.coerceAtLeast(0) ?: 1,
                targetReps = targetReps.toIntOrNull()?.takeIf { mode == ExerciseMode.REPETITIONS },
                targetDurationSeconds = targetDurationSeconds.toIntOrNull()
                    ?.takeIf { mode == ExerciseMode.DURATION },
                restSeconds = restSeconds.toIntOrNull(),
                measurement = null,
                effort = effort,
                plannedDurationSeconds = plannedDurationMinutes.toIntOrNull()?.let { it * 60 },
                sideSwitchSeconds = sideSwitchSeconds.toIntOrNull(),
                repeater = toRepeater().takeIf { mode == ExerciseMode.REPEATERS },
            )
        }

    companion object {
        fun from(payload: PrescriptionPayload?): PrescriptionFormState = PrescriptionFormState(
            sets = payload?.sets?.toString() ?: "3",
            targetReps = payload?.targetReps?.toString().orEmpty(),
            targetDurationSeconds = payload?.targetDurationSeconds?.toString().orEmpty(),
            restSeconds = payload?.restSeconds?.toString().orEmpty(),
            effort = payload?.effort,
            // Filled from the same stored length as the seconds field; only the mode decides which
            // one is read back, so switching modes shows each number in its own unit.
            activityMinutes = payload?.targetDurationSeconds
                ?.let { ((it + 30) / 60).toString() }
                .orEmpty(),
            // Rounded to the nearest minute on the way in, because that is the unit it is typed
            // in; a value that was never typed stays empty and keeps asking for the estimate.
            plannedDurationMinutes = payload?.plannedDurationSeconds
                ?.let { ((it + 30) / 60).toString() }
                .orEmpty(),
            sideSwitchSeconds = payload?.sideSwitchSeconds?.toString().orEmpty(),
            repeaterReps = payload?.repeater?.repsPerSet?.toString().orEmpty(),
            repeaterWorkSeconds = payload?.repeater?.workSecondsPerRep?.toString().orEmpty(),
            repeaterRestSeconds = payload?.repeater?.restSecondsBetweenReps?.toString().orEmpty(),
        )
    }
}

fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/**
 * The prescription half of a form. Shared by the exercise editor (a library default), by editing
 * the copy that sits in a week, and by a station of a routine — the same fields, three owners.
 */
@Composable
fun PrescriptionFields(
    state: PrescriptionFormState,
    onStateChange: (PrescriptionFormState) -> Unit,
    mode: ExerciseMode,
    unilateral: Boolean,
    modifier: Modifier = Modifier,
    /**
     * A station of a circuit. Its set count, set rest and total time are not asked, because the
     * circuit overrides all three — one set per round, the circuit's own rests, one clock for the
     * whole thing — and a field whose value is ignored is a question with a hidden wrong answer.
     */
    inCircuit: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mode.hasSetStructure) {
            SetStructureFields(state, onStateChange, mode, unilateral, inCircuit)
        } else {
            // An activity is a block of time: how long, in the unit a session is thought of in.
            // No sets, rests or sides to ask about, and no separate planned duration, because
            // the length *is* the plan.
            NumberField(
                label = "Minutes",
                value = state.activityMinutes,
                onValueChange = { onStateChange(state.copy(activityMinutes = it)) },
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        }

        HorizontalDivider()
        Text("Target effort", style = MaterialTheme.typography.bodyMedium)
        EffortSelector(
            modifier = Modifier.fillMaxWidth(),
            selected = state.effort,
            onSelect = { onStateChange(state.copy(effort = it)) },
        )
    }
}

/** Sets, their length, the rests and sides between them, and the planned duration they add up to. */
@Composable
private fun SetStructureFields(
    state: PrescriptionFormState,
    onStateChange: (PrescriptionFormState) -> Unit,
    mode: ExerciseMode,
    unilateral: Boolean,
    inCircuit: Boolean,
) {
    // What the shape of the work implies, so the duration field can show it in grey rather than
    // asking for a number the app can already work out.
    val estimate = DurationEstimate.forPrescription(
        mode = mode,
        unilateral = unilateral,
        prescription = state.toPayload(mode).copy(plannedDurationSeconds = null),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!inCircuit) {
            NumberField(
                label = if (unilateral) "Sets per side" else "Sets",
                value = state.sets,
                onValueChange = { onStateChange(state.copy(sets = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        when (mode) {
            ExerciseMode.REPETITIONS -> NumberField(
                label = "Target reps",
                value = state.targetReps,
                onValueChange = { onStateChange(state.copy(targetReps = it)) },
                modifier = Modifier.weight(1f),
            )

            ExerciseMode.DURATION -> NumberField(
                label = "Set duration (s)",
                value = state.targetDurationSeconds,
                onValueChange = { onStateChange(state.copy(targetDurationSeconds = it)) },
                modifier = Modifier.weight(1f),
            )

            // A repeater states its own per-rep length below, so asking for a set length here
            // would be the same question twice with two different answers.
            ExerciseMode.REPEATERS, ExerciseMode.ACTIVITY -> Unit
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!inCircuit) {
            NumberField(
                label = "Between sets (s)",
                value = state.restSeconds,
                onValueChange = { onStateChange(state.copy(restSeconds = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        if (unilateral) {
            NumberField(
                label = "Switch sides (s)",
                value = state.sideSwitchSeconds,
                onValueChange = { onStateChange(state.copy(sideSwitchSeconds = it)) },
                placeholder = TimerProgram.DEFAULT_SIDE_SWITCH_SECONDS.toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (mode == ExerciseMode.REPEATERS) {
        HorizontalDivider()
        RepeaterFields(state, onStateChange)
    }

    // A circuit is timed once, from its own sequence; a station has no total of its own.
    if (inCircuit) return

    HorizontalDivider()
    NumberField(
        label = "Total time (min)",
        value = state.plannedDurationMinutes,
        onValueChange = { onStateChange(state.copy(plannedDurationMinutes = it)) },
        // The estimate, shown where the answer would go. Typing overrules it; clearing the
        // field hands the question back rather than recording a zero.
        placeholder = estimate?.let { (it + 30) / 60 }?.toString(),
        modifier = Modifier.fillMaxWidth(0.5f),
    )
    Text(
        text = estimate
            ?.let { "≈ ${PrescriptionSummary.duration(it)} total" }
            ?: "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * What one set of repeaters is made of.
 *
 * Always shown for a repeater exercise, because it is the whole of what such a set *is* — there is
 * no plain duration to fall back on. Never shown for anything else, so an ordinary hang cannot end
 * up carrying pulse numbers it does not use.
 */
@Composable
private fun RepeaterFields(
    state: PrescriptionFormState,
    onStateChange: (PrescriptionFormState) -> Unit,
) {
    Text(
        text = "One set",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
            label = "Reps per set",
            value = state.repeaterReps,
            onValueChange = { onStateChange(state.copy(repeaterReps = it)) },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Seconds on",
            value = state.repeaterWorkSeconds,
            onValueChange = { onStateChange(state.copy(repeaterWorkSeconds = it)) },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Seconds off",
            value = state.repeaterRestSeconds,
            onValueChange = { onStateChange(state.copy(repeaterRestSeconds = it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The five-point effort scale, as a dropdown.
 *
 * Five chips across the width of a phone wrapped onto two lines and took a whole band of the
 * screen to say something optional. A closed dropdown states the answer in one line and costs one
 * tap to change, which is the right weight for a field that is usually left alone.
 *
 * "Not set" is a real entry rather than a gesture, because clearing a rating must be as findable
 * as setting one.
 */
@Composable
fun EffortSelector(
    selected: EffortLevel?,
    onSelect: (EffortLevel?) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Int = 44,
) {
    ChoiceField(
        value = selected,
        // "Not set" is a real entry rather than a gesture, because clearing a rating must be as
        // findable as setting one.
        options = listOf<EffortLevel?>(null) + EffortLevel.entries,
        optionLabel = { it?.label ?: NOT_SET },
        onSelect = onSelect,
        modifier = modifier,
        minHeight = minHeight,
    )
}

private const val NOT_SET = "Not set"

/**
 * A number input. [label] is nullable because a field whose row already names it does not need to
 * say the same word twice.
 */
@Composable
fun NumberField(
    label: String?,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    placeholder: String? = null,
    minHeight: Int = 48,
    /** Allows a negative value: added load below zero is assistance. Implies [decimal]. */
    signed: Boolean = false,
) {
    CompactTextField(
        value = value,
        onValueChange = { typed ->
            val filtered = if (signed) {
                loadInput(typed, signed = true)
            } else {
                typed.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.replace(',', '.')
            }
            onValueChange(filtered)
        },
        label = label,
        placeholder = placeholder,
        minHeight = minHeight,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * What a load field keeps of what was typed: digits and one decimal point, and — for added load,
 * which goes below zero for assistance — a sign. A minus typed anywhere flips the sign, so "15-"
 * reads as "-15" and a second minus takes it away again; decimal keyboards that hide the minus key
 * are covered by [SignKey].
 */
fun loadInput(typed: String, signed: Boolean): String {
    val digits = typed.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
    val negative = signed && typed.count { it == '-' } % 2 == 1
    return if (negative) "-$digits" else digits
}

/** The same load with the other sign; empty stays empty but remembers a sign to type after. */
fun flipSign(value: String): String = if (value.startsWith("-")) value.removePrefix("-") else "-$value"

/** A small ± beside a load field, for keyboards without a minus key. */
@androidx.compose.runtime.Composable
fun SignKey(what: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
        modifier = Modifier
            .width(36.dp)
            .semantics { contentDescription = "Change the sign of $what" },
    ) {
        androidx.compose.material3.Text("±", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
    }
}
