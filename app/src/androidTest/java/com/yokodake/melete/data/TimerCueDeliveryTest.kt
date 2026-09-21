package com.yokodake.melete.data

import android.media.AudioAttributes
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.timer.TimerCue
import com.yokodake.melete.data.timer.TimerCuePlayer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Whether the audio system actually lets the cue play.
 *
 * Android 17 blocks background audio by failing it *silently*, so a test that only checked the
 * countdown arithmetic would pass just as happily on a device that never makes a sound. The signal
 * asserted here is the platform's own register of active players: if the tone appears there, the
 * platform started it rather than dropping it on the floor.
 *
 * This replaced an assertion on the audio-focus result. Focus is no longer requested at all — it
 * was part of what made music apps stop for every tick — and being granted focus never proved a
 * sound came out anyway.
 *
 * Run with `adb shell cmd audio set-hardening throw` to make violations throw instead of being
 * swallowed, and `adb shell cmd audio clear-hardening` afterwards. (Not `set-enable-hardening`,
 * which is not a command and fails quietly, leaving the test looking like it proved something.)
 */
@RunWith(AndroidJUnit4::class)
class TimerCueDeliveryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val audioManager =
        context.getSystemService(AudioManager::class.java) as AudioManager

    /** True while this app has a player the audio system considers active. */
    private fun cuePlaybackActive(): Boolean =
        audioManager.activePlaybackConfigurations.any {
            it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA
        }

    /** Polls, because "active" is reported asynchronously and a cue is under a second long. */
    private fun awaitCuePlayback(timeoutMs: Long = 1_500): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cuePlaybackActive()) return true
            Thread.sleep(25)
        }
        return false
    }

    @Test
    fun theAudioSystemStartsTheCueRatherThanDroppingIt() {
        val player = TimerCuePlayer(context)

        player.play(TimerCue.FINISH)

        assertTrue(
            "No active player appeared, so the cue would not have been heard",
            awaitCuePlayback(),
        )
        Thread.sleep(1_200)
    }

    @Test
    fun everyCueIsPlayable() {
        val player = TimerCuePlayer(context)
        TimerCue.entries.forEach { cue ->
            player.play(cue)
            assertTrue("$cue never became an active player", awaitCuePlayback())
            Thread.sleep(800)
        }
    }

    /**
     * The point of the change: nothing is asked of whatever else is playing.
     *
     * A cue that took audio focus, even as transient-may-duck, handed the music app the decision
     * of whether to duck or stop. Not requesting focus is half of leaving it alone — the other
     * half is not using alarm usage — and this pins the half that is testable.
     */
    @Test
    fun aCueDoesNotTakeAudioFocusFromWhateverElseIsPlaying() {
        val losses = mutableListOf<Int>()
        val listener = AudioManager.OnAudioFocusChangeListener { change -> losses += change }
        // Transient, and handed back in a finally: an outright AUDIOFOCUS_GAIN is a *permanent*
        // loss for whatever was playing, and apps do not come back from one of those. Running
        // this test should cost the user a pause, not their music for the rest of the session.
        val request = android.media.AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(listener)
            .build()
        val held = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(request)
        assertTrue("the test could not take focus to begin with", held)

        try {
            TimerCuePlayer(context).play(TimerCue.FINISH)
            Thread.sleep(1_500)

            assertTrue(
                "the cue took focus away, which is part of what makes music apps stop: $losses",
                losses.isEmpty(),
            )
        } finally {
            audioManager.abandonAudioFocusRequest(request)
        }
    }
}
