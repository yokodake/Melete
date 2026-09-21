package com.yokodake.melete.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.yokodake.melete.data.model.ExerciseCategory

/**
 * Colours that carry a meaning rather than a mood.
 *
 * These sit outside the Material scheme on purpose: the scheme is dynamic and follows the
 * wallpaper, so a category or a "done" mark taken from it would change hue between phones and
 * stop meaning anything. Each has a light and a dark variant so it stays legible either way —
 * the dark ones are lifted and slightly desaturated, because a fully saturated dot on a dark
 * surface glares.
 */
private val OpenOrangeLight = Color(0xFFE2701A)
private val OpenOrangeDark = Color(0xFFFF9D4D)

private val ConditioningGreenLight = Color(0xFF2E8B45)
private val ConditioningGreenDark = Color(0xFF5FD27E)

private val FlexibilityYellowLight = Color(0xFFD2A70C)
private val FlexibilityYellowDark = Color(0xFFF2CC45)

private val DoneGreenLight = Color(0xFF1E7A38)
private val DoneGreenDark = Color(0xFF3FBF63)

/** The dot beside an exercise name. Null category means no dot is drawn at all. */
@Composable
@ReadOnlyComposable
fun categoryColor(category: ExerciseCategory): Color {
    val dark = isSystemInDarkTheme()
    return when (category) {
        ExerciseCategory.OPEN -> if (dark) OpenOrangeDark else OpenOrangeLight
        ExerciseCategory.CONDITIONING -> if (dark) ConditioningGreenDark else ConditioningGreenLight
        ExerciseCategory.FLEXIBILITY -> if (dark) FlexibilityYellowDark else FlexibilityYellowLight
    }
}

/** Container and content for the "Done" mark: saturated green, so it reads at a glance. */
@Composable
@ReadOnlyComposable
fun doneColors(): Pair<Color, Color> =
    if (isSystemInDarkTheme()) {
        DoneGreenDark to Color(0xFF07240F)
    } else {
        DoneGreenLight to Color.White
    }

/** The last seconds of a rest, when the work is about to start. */
@Composable
@ReadOnlyComposable
fun aboutToStartColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF6A3200) else Color(0xFFFFB264)

/** A work interval in progress. */
@Composable
@ReadOnlyComposable
fun workingColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF0E3F1E) else Color(0xFF9FE0B2)
