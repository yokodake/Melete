package com.yokodake.melete.data.timer

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Everything a countdown can sound, in the order a run meets them. */
enum class TimerCue {
    /** A quarter of a long set is done. */
    QUARTER,

    /** Halfway through a long set. */
    HALF,

    /** Three quarters of a long set is done. */
    THREE_QUARTERS,

    /** Thirty seconds left. */
    THIRTY_SECONDS,

    COUNT_3,
    COUNT_2,
    COUNT_1,

    /** Zero. */
    FINISH,
}

/** A cue and how much time is left when it sounds. */
@Serializable
data class PlannedCue(
    val cue: TimerCue,
    val remainingMs: Long,
)

/** Which families of cue the user wants. All optional, all on by default. */
@Serializable
data class CueSettings(
    /** A single heads-up at thirty seconds left. */
    val thirtySecondWarning: Boolean = true,

    /** Ticks at three, two and one second left. */
    val finalCountdown: Boolean = true,

    /** Progress through a long work interval, at a quarter, a half and three quarters. */
    val quarterCues: Boolean = true,
)

object CuePlanner {

    /** Progress cues would be noise on anything shorter than this. */
    const val QUARTER_CUE_MIN_MS: Long = 60_000

    private const val THIRTY_SECONDS_MS: Long = 30_000

    /** Cues closer together than this are the same moment as far as an ear is concerned. */
    private const val COLLISION_MS: Long = 500

    /**
     * Builds the cue plan for one run, in the order the run meets it.
     *
     * Progress cues are for the work itself, which is what has a middle worth marking; a rest
     * interval only needs to say how much is left. Cues that would fall outside the countdown, or
     * on top of each other, are dropped rather than crowded together — the point is to be
     * informative, not to beep constantly.
     */
    fun plan(phase: TimerPhase, totalMs: Long, settings: CueSettings): List<PlannedCue> {
        val candidates = buildList {
            if (settings.quarterCues && phase == TimerPhase.WORK && totalMs >= QUARTER_CUE_MIN_MS) {
                add(PlannedCue(TimerCue.QUARTER, remainingMs = totalMs * 3 / 4))
                add(PlannedCue(TimerCue.HALF, remainingMs = totalMs / 2))
                add(PlannedCue(TimerCue.THREE_QUARTERS, remainingMs = totalMs / 4))
            }
            if (settings.thirtySecondWarning && totalMs > THIRTY_SECONDS_MS) {
                add(PlannedCue(TimerCue.THIRTY_SECONDS, remainingMs = THIRTY_SECONDS_MS))
            }
            if (settings.finalCountdown) {
                listOf(
                    TimerCue.COUNT_3 to 3_000L,
                    TimerCue.COUNT_2 to 2_000L,
                    TimerCue.COUNT_1 to 1_000L,
                ).forEach { (cue, remaining) ->
                    if (totalMs > remaining) add(PlannedCue(cue, remaining))
                }
            }
            add(PlannedCue(TimerCue.FINISH, remainingMs = 0))
        }

        // Chronological order, and only one cue per moment: the more specific one wins.
        return candidates
            .sortedByDescending { it.remainingMs }
            .fold(mutableListOf<PlannedCue>()) { kept, candidate ->
                val clash = kept.lastOrNull()
                    ?.takeIf { abs(it.remainingMs - candidate.remainingMs) < COLLISION_MS }
                when {
                    clash == null -> kept.add(candidate)
                    priority(candidate.cue) > priority(clash.cue) -> {
                        kept[kept.lastIndex] = candidate
                    }

                    else -> Unit
                }
                kept
            }
    }

    /** Higher wins when two cues land on the same moment. */
    private fun priority(cue: TimerCue): Int = when (cue) {
        TimerCue.QUARTER, TimerCue.HALF, TimerCue.THREE_QUARTERS -> 0
        TimerCue.THIRTY_SECONDS -> 1
        TimerCue.COUNT_3, TimerCue.COUNT_2, TimerCue.COUNT_1 -> 2
        TimerCue.FINISH -> 3
    }
}
