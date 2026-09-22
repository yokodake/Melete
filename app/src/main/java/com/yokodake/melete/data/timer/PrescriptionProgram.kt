package com.yokodake.melete.data.timer

import com.yokodake.melete.data.model.ExerciseMode
import com.yokodake.melete.data.model.PrescriptionPayload

/**
 * Turns a plan into the thing the timer counts, and into the minutes the planner shows.
 *
 * A prescription already says how many sets, how long each is, how much rest goes between them,
 * whether both sides are trained and whether the set is a series of pulses. That *is* a program,
 * so there is nothing left to ask — and because it is one pure function, the duration the week
 * estimates and the sequence the timer runs can never drift apart.
 */
object PrescriptionProgram {

    /** One planned exercise, as the timer would execute it. */
    fun entryOf(
        label: String?,
        mode: ExerciseMode,
        unilateral: Boolean,
        prescription: PrescriptionPayload?,
        fallbackRestSeconds: Int = 0,
        occurrenceId: String? = null,
    ): TimerEntry {
        val sets = (prescription?.sets ?: 1).coerceIn(1, 99)
        val rest = prescription?.restSeconds?.takeIf { it > 0 } ?: fallbackRestSeconds
        val timed = mode == ExerciseMode.DURATION ||
            mode == ExerciseMode.ACTIVITY ||
            mode == ExerciseMode.REPEATERS
        val repeater = prescription?.repeater
            ?.takeIf { mode == ExerciseMode.REPEATERS }
            ?.toSpec()
        val workSeconds = prescription?.targetDurationSeconds?.takeIf { timed && it > 0 }
        val switch = prescription?.sideSwitchSeconds
            ?.coerceAtLeast(0)
            ?: TimerProgram.DEFAULT_SIDE_SWITCH_SECONDS
        return when {
            repeater != null -> TimerEntry(
                label = label,
                work = WorkKind.TIMED,
                sets = sets,
                restSeconds = rest,
                unilateral = unilateral,
                sideSwitchSeconds = switch,
                repeater = repeater,
                occurrenceId = occurrenceId,
            )

            workSeconds != null -> TimerEntry(
                label = label,
                work = WorkKind.TIMED,
                workSeconds = workSeconds,
                sets = sets,
                restSeconds = rest,
                unilateral = unilateral,
                sideSwitchSeconds = switch,
                occurrenceId = occurrenceId,
            )

            mode == ExerciseMode.REPETITIONS -> TimerEntry(
                label = label,
                work = WorkKind.REPS,
                workReps = prescription?.targetReps,
                sets = sets,
                restSeconds = rest,
                unilateral = unilateral,
                sideSwitchSeconds = switch,
                occurrenceId = occurrenceId,
            )

            // A timed exercise with no target length: there is nothing to count but the rest.
            else -> TimerEntry(
                label = label,
                work = WorkKind.NONE,
                restSeconds = rest.coerceAtLeast(1),
                occurrenceId = occurrenceId,
            )
        }
    }

    /**
     * One station of a circuit, with the numbers a circuit overrides already stripped out.
     *
     * A station keeps its repeater shape, its sides and its own lengths; what it loses is its
     * standalone set count and set rest, because in a circuit those are the round count and the
     * transition and round rests. Multiplying the two would turn four exercises of four sets into
     * sixteen rounds of work nobody planned.
     */
    fun stationOf(station: StationPlan): TimerEntry {
        val entry = entryOf(
            label = station.label,
            mode = station.mode,
            unilateral = station.unilateral,
            prescription = station.prescription,
            occurrenceId = station.occurrenceId,
        )
        // Standalone, a timed exercise with no target length becomes a bare rest, because resting
        // is the only thing left to count. In a circuit that would be a station you stand still
        // for: the rests belong to the circuit, so it waits for you instead, exactly as a set of
        // reps does. Copying `restSeconds = 0` onto a rest-only entry would also be invalid.
        return if (entry.work == WorkKind.NONE) {
            entry.copy(work = WorkKind.REPS, sets = 1, restSeconds = 0)
        } else {
            entry.copy(sets = 1, restSeconds = 0)
        }
    }

    /** A circuit: one set of each station, in order, so many times round. */
    fun circuit(
        label: String?,
        rounds: Int,
        transitionSeconds: Int,
        roundRestSeconds: Int,
        stations: List<StationPlan>,
        circuitInstanceId: String? = null,
    ): TimerProgram = TimerProgram.circuit(
        entries = stations.map(::stationOf),
        rounds = rounds.coerceAtLeast(1),
        transitionSeconds = transitionSeconds.coerceAtLeast(0),
        roundRestSeconds = roundRestSeconds.coerceAtLeast(0),
        label = label,
        circuitInstanceId = circuitInstanceId,
    )

    /** The whole standalone program one planned exercise implies. */
    fun of(
        label: String?,
        mode: ExerciseMode,
        unilateral: Boolean,
        prescription: PrescriptionPayload?,
        fallbackRestSeconds: Int = 0,
        occurrenceId: String? = null,
    ): TimerProgram = TimerProgram(
        entries = listOf(
            entryOf(label, mode, unilateral, prescription, fallbackRestSeconds, occurrenceId)
        ),
        label = label,
        occurrenceId = occurrenceId,
    )
}

/** One planned exercise of a circuit, as much of it as the timer needs. */
data class StationPlan(
    val label: String?,
    val mode: ExerciseMode,
    val unilateral: Boolean,
    val prescription: PrescriptionPayload?,
    val occurrenceId: String? = null,
)

/**
 * How long a plan will take, in seconds, including the rests that fall inside it.
 *
 * This is the *planning* number, distinct from work time: total training time counts the rests and
 * the getting-ready, while work time counts only what the clock spends on the exercise itself.
 * Keeping the two apart is the whole point — an hour of finger training and an hour of hangs are
 * not the same hour.
 *
 * It answers null rather than guessing when there is nothing to go on. A timed exercise with no
 * target duration has no length; an activity with no duration has none either. A missing estimate
 * is a valid answer and is never backfilled with a fabricated number.
 */
object DurationEstimate {

    /**
     * The estimate for one planned exercise, or null when no useful one exists.
     *
     * Rests come from the prescription alone, never from what the timer happened to be set to
     * last: an estimate shown in the planner has to mean the same thing tomorrow.
     */
    fun forPrescription(
        mode: ExerciseMode,
        unilateral: Boolean,
        prescription: PrescriptionPayload?,
    ): Int? {
        if (prescription == null) return null
        // An activity has no set structure: its duration is simply what was planned for it.
        if (mode == ExerciseMode.ACTIVITY) return prescription.targetDurationSeconds
        val hasLength = prescription.repeater != null ||
            (prescription.targetDurationSeconds ?: 0) > 0
        val timed = mode == ExerciseMode.DURATION || mode == ExerciseMode.REPEATERS
        if (timed && !hasLength) return null
        return PrescriptionProgram
            .of(label = null, mode = mode, unilateral = unilateral, prescription = prescription)
            .estimatedSeconds()
    }
}
