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
private val OpenClimbingVioletLight = Color(0xFF9875D6)
private val OpenClimbingVioletDark = Color(0xFFB79BE8)

private val StructuredClimbingBlueLight = Color(0xFF528DDD)
private val StructuredClimbingBlueDark = Color(0xFF8CB8F0)

// Deep rather than hot. A fire-engine red at dot size reads as an error state, and at this
// lightness it also starts arguing with the orange two rows down; pulling it darker and a little
// less saturated keeps it unmistakably red while letting the orange stay the bright one.
private val BoardClimbingRedLight = Color(0xFFD32F2F)
// Lifted well past the light value, and pushed a few degrees warm so it separates from the
// raspberry on a dark surface -- the two are neighbours in hue, and coral against pink is the
// difference that survives being four millimetres across.
private val BoardClimbingRedDark = Color(0xFFF2776A)
private val FingerRaspberryLight = Color(0xFFDD77AD)
private val FingerRaspberryDark = Color(0xFFEE7BA4)

private val ConditioningOrangeLight = Color(0xFFE2701A)
private val ConditioningOrangeDark = Color(0xFFFF9D4D)

private val FlexibilityYellowLight = Color(0xFFEAC521)
private val FlexibilityYellowDark = Color(0xFFFFE066)

private val OtherActivityGreenLight = Color(0xFF5CA37C)
private val OtherActivityGreenDark = Color(0xFF8ED0A6)

private val DoneGreenLight = Color(0xFF1E7A38)
private val DoneGreenDark = Color(0xFF3FBF63)

/** The dot beside an exercise name. Null category means no dot is drawn at all. */
@Composable
@ReadOnlyComposable
fun categoryColor(category: ExerciseCategory): Color {
    val dark = isSystemInDarkTheme()
    return when (category) {
        ExerciseCategory.OPEN_CLIMBING ->
            if (dark) OpenClimbingVioletDark else OpenClimbingVioletLight

        ExerciseCategory.STRUCTURED_CLIMBING ->
            if (dark) StructuredClimbingBlueDark else StructuredClimbingBlueLight

        ExerciseCategory.BOARD_CLIMBING ->
            if (dark) BoardClimbingRedDark else BoardClimbingRedLight

        ExerciseCategory.FINGER_TRAINING ->
            if (dark) FingerRaspberryDark else FingerRaspberryLight

        ExerciseCategory.STRENGTH_CONDITIONING ->
            if (dark) ConditioningOrangeDark else ConditioningOrangeLight

        ExerciseCategory.FLEXIBILITY ->
            if (dark) FlexibilityYellowDark else FlexibilityYellowLight

        ExerciseCategory.OTHER_ACTIVITY ->
            if (dark) OtherActivityGreenDark else OtherActivityGreenLight
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
