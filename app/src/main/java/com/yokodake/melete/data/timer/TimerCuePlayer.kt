package com.yokodake.melete.data.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** The sound and feel of a cue, behind a seam so delivery can be tested without a speaker. */
interface CuePlayer {
    fun play(cue: TimerCue)
}

/**
 * Plays the timer cues.
 *
 * The tone is generated rather than shipped as an asset so the exact [AudioAttributes] are under
 * our control. That matters on Android 17: an app in the background may only touch audio through a
 * while-in-use-capable foreground service, and the one waiver is for `USAGE_ALARM` streams from an
 * app holding exact-alarm permission — which is precisely the path taken when a cue has to sound
 * after the process was killed. Violations fail *silently*, so this class must not be the place
 * where a wrong usage hides.
 *
 * Audio focus is requested as transient-may-duck: music the user is training to should dip for the
 * beep, not stop.
 */
class TimerCuePlayer(context: Context) : CuePlayer {

    private val appContext = context.applicationContext

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * The audio system's answer to the last focus request. Refusal is how the platform says it is
     * blocking this process from making a sound, and it is the only signal available: playback
     * itself fails silently.
     */
    @Volatile
    var lastFocusResult: Int = AudioManager.AUDIOFOCUS_REQUEST_FAILED
        private set

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun play(cue: TimerCue) {
        // Each family sounds different, so the cue itself says which moment it is without
        // anyone having to look at the screen mid-set.
        val pattern = when (cue) {
            // Progress through a long set: one soft, low beep, easy to ignore.
            TimerCue.QUARTER, TimerCue.HALF, TimerCue.THREE_QUARTERS ->
                Pattern(beeps = 1, toneMs = 90, gapMs = 60, frequencyHz = 660.0)

            // Thirty seconds left: two beeps, the traditional heads-up.
            TimerCue.THIRTY_SECONDS ->
                Pattern(beeps = 2, toneMs = 110, gapMs = 90, frequencyHz = 880.0)

            // The last three seconds: one short tick each, like a starting light.
            TimerCue.COUNT_3, TimerCue.COUNT_2, TimerCue.COUNT_1 ->
                Pattern(beeps = 1, toneMs = 70, gapMs = 50, frequencyHz = 1046.0)

            // Zero: unmistakable.
            TimerCue.FINISH ->
                Pattern(beeps = 3, toneMs = 220, gapMs = 110, frequencyHz = 1175.0)
        }
        vibrate(pattern)
        playTone(pattern)
    }

    private fun playTone(pattern: Pattern) {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        val granted = audioManager.requestAudioFocus(focusRequest)
        lastFocusResult = granted
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // Refused focus means the platform is blocking background audio for this process.
            // Say so in the log rather than leaving a silent beep looking like a delivered cue.
            Log.w(TAG, "Audio focus refused ($granted); the cue may not be audible")
        }
        val samples = generateSamples(pattern)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        runCatching {
            track.write(samples, 0, samples.size)
            track.setNotificationMarkerPosition(samples.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(playedTrack: AudioTrack?) {
                        runCatching { playedTrack?.release() }
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }

                    override fun onPeriodicNotification(playedTrack: AudioTrack?) = Unit
                }
            )
            track.play()
        }.onFailure { error ->
            Log.w(TAG, "Cue playback failed", error)
            runCatching { track.release() }
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun vibrate(pattern: Pattern) {
        val vibrator = runCatching {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        }.getOrNull() ?: return
        if (!vibrator.hasVibrator()) return
        val timings = mutableListOf(0L)
        val amplitudes = mutableListOf(0)
        repeat(pattern.beeps) {
            timings += pattern.toneMs.toLong()
            amplitudes += VibrationEffect.DEFAULT_AMPLITUDE
            timings += pattern.gapMs.toLong()
            amplitudes += 0
        }
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1)
            )
        }.onFailure { Log.w(TAG, "Vibration failed", it) }
    }

    private fun generateSamples(pattern: Pattern): ShortArray {
        val toneSamples = SAMPLE_RATE * pattern.toneMs / 1000
        val gapSamples = SAMPLE_RATE * pattern.gapMs / 1000
        val total = pattern.beeps * (toneSamples + gapSamples)
        val output = ShortArray(total)
        var index = 0
        repeat(pattern.beeps) {
            for (sample in 0 until toneSamples) {
                val angle = 2.0 * PI * sample * pattern.frequencyHz / SAMPLE_RATE
                // A short fade at each end; a square-edged tone clicks.
                val fade = min(
                    1.0,
                    min(sample, toneSamples - sample - 1).toDouble() / FADE_SAMPLES,
                )
                output[index++] = (sin(angle) * fade * Short.MAX_VALUE * 0.6).toInt().toShort()
            }
            index += gapSamples
        }
        return output
    }

    private data class Pattern(
        val beeps: Int,
        val toneMs: Int,
        val gapMs: Int,
        val frequencyHz: Double,
    )

    private companion object {
        const val TAG = "TimerCuePlayer"
        const val SAMPLE_RATE = 44_100
        const val FADE_SAMPLES = 220.0
    }
}
