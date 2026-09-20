package com.yokodake.melete.data

import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.timer.TimerCue
import com.yokodake.melete.data.timer.TimerCuePlayer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Whether the audio system accepts the cue at all.
 *
 * Android 17 blocks background audio by failing it *silently*, so a test that only checked the
 * countdown arithmetic would pass just as happily on a device that never makes a sound. The focus
 * request is the one place the platform answers out loud, so that answer is what is asserted here.
 *
 * Run with `adb shell cmd audio set-enable-hardening throw` to make violations throw instead of
 * being swallowed.
 */
@RunWith(AndroidJUnit4::class)
class TimerCueDeliveryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theAudioSystemGrantsFocusForAnAlarmCue() {
        val player = TimerCuePlayer(context)

        player.play(TimerCue.FINISH)
        Thread.sleep(1_500)

        assertEquals(
            "Focus was refused, so the cue would not have been heard",
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
            player.lastFocusResult,
        )
    }

    @Test
    fun bothCuesArePlayable() {
        val player = TimerCuePlayer(context)
        player.play(TimerCue.WARNING)
        Thread.sleep(800)
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, player.lastFocusResult)
        player.play(TimerCue.FINISH)
        Thread.sleep(1_200)
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, player.lastFocusResult)
    }
}
