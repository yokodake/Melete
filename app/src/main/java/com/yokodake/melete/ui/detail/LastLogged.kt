package com.yokodake.melete.ui.detail

import com.yokodake.melete.data.PerformedSet
import com.yokodake.melete.data.PlannedOccurrence
import com.yokodake.melete.data.entity.BodySide
import com.yokodake.melete.data.entity.OccurrenceState
import com.yokodake.melete.data.model.Measurement
import com.yokodake.melete.data.model.MeasurementMeaning
import com.yokodake.melete.ui.week.PrescriptionSummary
import java.time.LocalDate

/**
 * The most recent earlier result for a planned exercise, in one line — the load to pick up without
 * opening the logger.
 *
 * [variationTag] is set only when the result comes from a different plan than the one being
 * looked at, so the line can say so.
 */
data class LastLogged(
    val date: LocalDate,
    val summary: String,
    val otherPlan: Boolean = false,
    val variationTag: String? = null,
)

object LastLoggedPicker {

    /**
     * Picks the result to show for [current], from every occurrence of the same exercise and the
     * sets recorded against them.
     *
     * Only real records count: a completed occurrence or one with sets, filed under a training
     * date no later than [current]'s (today's when it has none) — ordered by that date, never by
     * when it was typed in. [current] itself is never its own previous result. The same
     * variation (or the default, for the default) wins over a more recent one cut from another
     * plan; failing that, the most recent of any plan, marked as such. Null when nothing fits.
     */
    fun pick(
        current: PlannedOccurrence,
        history: List<PlannedOccurrence>,
        sets: List<PerformedSet>,
        today: LocalDate,
    ): LastLogged? {
        val reference = current.trainingDate ?: today
        val setsByOccurrence = sets.groupBy { it.occurrenceId }
        val candidates = history
            .asSequence()
            .filter { it.id != current.id }
            .filter { it.state == OccurrenceState.COMPLETED || setsByOccurrence.containsKey(it.id) }
            .mapNotNull { occurrence ->
                val date = setsByOccurrence[occurrence.id]?.firstOrNull()?.trainingDate
                    ?: occurrence.trainingDate
                    ?: return@mapNotNull null
                if (date > reference) return@mapNotNull null
                val summary = summarise(occurrence, setsByOccurrence[occurrence.id].orEmpty())
                    ?: return@mapNotNull null
                Triple(occurrence, date, summary)
            }
            .sortedWith(compareByDescending<Triple<PlannedOccurrence, LocalDate, String>> { it.second }
                .thenByDescending { it.first.orderIndex })
            .toList()
        val same = candidates.firstOrNull { it.first.variationId == current.variationId }
        val chosen = same ?: candidates.firstOrNull() ?: return null
        val other = same == null
        return LastLogged(
            date = chosen.second,
            summary = chosen.third,
            otherPlan = other,
            variationTag = chosen.first.variationTag.takeIf { other },
        )
    }

    /**
     * What one earlier workout recorded, compactly and truthfully: "3 × 8 · 20 kg · Hard" when
     * every set agrees; when they do not, the set count and the heaviest set with its reps,
     * "4 sets · max 5 × 92.5 kg" (each side's own for unilateral work; on a tie in load, the set
     * with more reps or the longer one). With [perSet], or when no set has a load, the sets are
     * listed one by one instead: "8 × 20 · 8 × 20 · 6 × 22 kg". Only recorded values are shown —
     * no reps or loads are invented — and an activity with no sets says how long and how hard.
     * Null when nothing was recorded worth repeating.
     */
    fun summarise(occurrence: PlannedOccurrence, sets: List<PerformedSet>, perSet: Boolean = false): String? {
        if (sets.isEmpty()) {
            return listOfNotNull(
                occurrence.loggedDurationSeconds?.let(PrescriptionSummary::duration),
                occurrence.loggedEffort?.label,
            ).joinToString(" · ").ifEmpty { null }
        }
        val ordered = sets.sortedWith(compareBy({ it.orderIndex }, { it.side?.ordinal ?: 0 }))
        val lines = if (occurrence.unilateral && ordered.any { it.side != null }) {
            pairs(ordered)
        } else {
            ordered.map { SetLine(it.payload.reps, it.payload.durationSeconds, it.payload.measurement, null) }
        }
        val unit = lines.firstNotNullOfOrNull { it.left?.unit ?: it.right?.unit }
        val effort = sets.mapNotNull { it.payload.effort }.distinct().singleOrNull()
        val uniform = lines.distinct().size == 1
        val body = if (uniform) {
            val line = lines.first()
            val count = lines.size
            val volume = line.target()?.let { "$count × $it" } ?: "$count ${if (count == 1) "set" else "sets"}"
            listOfNotNull(volume, line.loadText(sidesSpelled = true)?.let { "$it $unit" })
                .joinToString(" · ")
        } else if (!perSet && lines.any { it.left != null || it.right != null }) {
            // The heaviest set of each side, which is what to pick up from next time.
            fun heaviest(load: (SetLine) -> Measurement?): SetLine? = lines
                .filter { load(it) != null }
                .maxWithOrNull(compareBy({ load(it)!!.value }, { it.reps ?: 0 }, { it.seconds ?: 0 }))
            val left = heaviest { it.left }
            val right = heaviest { it.right }
            val count = lines.size
            val max = when {
                right == null || left == null || left.target() == right.target() -> {
                    val target = (left ?: right)?.target()
                    val load = SetLine(null, null, left?.left, right?.right).loadText(sidesSpelled = true)
                    listOfNotNull(target, load).joinToString(" × ")
                }
                // The sides peaked on different sets: each with its own reps.
                else -> "L ${left.target()} × ${SetLine(null, null, left.left, null).loadText(true)} / " +
                    "R ${right.target()} × ${SetLine(null, null, right.right, null).loadText(true)}"
            }
            "$count ${if (count == 1) "set" else "sets"} · max $max $unit"
        } else {
            val listed = lines.joinToString(" · ") { line ->
                listOfNotNull(line.target(), line.loadText(sidesSpelled = false)).joinToString(" × ")
                    .ifEmpty { "—" }
            }
            if (unit != null && lines.any { it.left != null || it.right != null }) "$listed $unit" else listed
        }
        return listOfNotNull(body, effort?.label).joinToString(" · ")
    }

    /** A unilateral row wrote a left and a right set; they read as one line of the table. */
    private fun pairs(ordered: List<PerformedSet>): List<SetLine> =
        ordered.chunked(2).map { chunk ->
            val left = chunk.firstOrNull { it.side == BodySide.LEFT } ?: chunk.first()
            val right = chunk.firstOrNull { it.side == BodySide.RIGHT }
            SetLine(
                reps = left.payload.reps,
                seconds = left.payload.durationSeconds,
                left = left.payload.measurement,
                right = right?.payload?.measurement,
            )
        }

    private data class SetLine(
        val reps: Int?,
        val seconds: Int?,
        val left: Measurement?,
        /** Set only for unilateral work. */
        val right: Measurement?,
    ) {
        fun target(): String? = reps?.toString() ?: seconds?.let(PrescriptionSummary::duration)

        fun loadText(sidesSpelled: Boolean): String? {
            val l = left?.let(::number)
            val r = right?.let(::number)
            return when {
                l == null && r == null -> null
                r == null || l == r -> l ?: r
                sidesSpelled -> "L ${l ?: "—"} / R $r"
                else -> "${l ?: "—"}/$r"
            }
        }
    }

    /** A load's number as it reads on its own: signed when it is relative to bodyweight. */
    private fun number(measurement: Measurement): String {
        val magnitude = kotlin.math.abs(measurement.value)
        val digits = if (magnitude == magnitude.toLong().toDouble()) {
            magnitude.toLong().toString()
        } else {
            magnitude.toString()
        }
        return when {
            measurement.meaning == MeasurementMeaning.ADDED_LOAD && measurement.value < 0 -> "−$digits"
            measurement.meaning == MeasurementMeaning.ADDED_LOAD -> "+$digits"
            else -> digits
        }
    }
}
