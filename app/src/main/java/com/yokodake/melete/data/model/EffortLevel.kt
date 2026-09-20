package com.yokodake.melete.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * How hard a set was, on a five-point verbal scale. Stored as the integer [level] so it sorts and
 * aggregates, shown as [label] everywhere a person reads it.
 *
 * The levels are the scale: do not reinterpret them as a 1–10 RPE or as reps in reserve, and do
 * not add levels without a payload version bump, because stored numbers would silently change
 * meaning.
 */
@Serializable(with = EffortLevelSerializer::class)
enum class EffortLevel(val level: Int, val label: String) {
    VERY_EASY(1, "Very easy"),
    EASY(2, "Easy"),
    MODERATE(3, "Moderate"),
    HARD(4, "Hard"),
    VERY_HARD(5, "Very hard");

    companion object {
        fun fromLevel(level: Int): EffortLevel? = entries.firstOrNull { it.level == level }
    }
}

private object EffortLevelSerializer : KSerializer<EffortLevel> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("EffortLevel", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: EffortLevel) = encoder.encodeInt(value.level)

    override fun deserialize(decoder: Decoder): EffortLevel {
        val level = decoder.decodeInt()
        return EffortLevel.fromLevel(level)
            ?: throw IllegalArgumentException("Unknown effort level $level")
    }
}
