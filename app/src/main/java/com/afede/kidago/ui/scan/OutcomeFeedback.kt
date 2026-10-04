package com.afede.kidago.ui.scan

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import com.afede.kidago.data.Settings
import kotlin.math.PI
import kotlin.math.sin

/** What the user feels or hears for an outcome. Patterns follow FEAT-002 UI Definition, "Outcome cues". */
enum class Cue { Found, NotFound, Error }

/** The two output channels, kept apart so each can be governed by its own setting. */
interface OutcomeCues {
    fun beep(cue: Cue)
    fun vibrate(cue: Cue)
}

/**
 * Fires the audible and haptic cue once per transition into Found, Not Found or Error (C12), each only when its own
 * setting is on. The settings are read at the moment of the transition, so a toggle takes effect immediately.
 * Any other state is ignored, so Idle, Typing and Searching never cue.
 */
class OutcomeFeedbackTrigger(
    private val settings: () -> Settings,
    private val cues: OutcomeCues,
) {
    fun onOutcome(state: CaptureState) {
        val cue = when (state) {
            is CaptureState.Found -> Cue.Found
            is CaptureState.NotFound -> Cue.NotFound
            CaptureState.Error -> Cue.Error
            else -> return
        }
        val now = settings()
        if (now.soundEnabled) cues.beep(cue)
        if (now.vibrationEnabled) cues.vibrate(cue)
    }
}

/** Plays the cues on the device: synthesized tones through AudioTrack and a waveform on the Vibrator. */
class AndroidOutcomeCues(context: Context) : OutcomeCues {
    private val vibrator = context.applicationContext.getSystemService(Vibrator::class.java)

    override fun vibrate(cue: Cue) {
        // Timings alternate off/on from an initial pause; Found is the light one.
        val (timings, amplitude) = when (cue) {
            Cue.Found -> longArrayOf(0, 50) to 100
            Cue.NotFound -> longArrayOf(0, 60, 80, 60) to 255
            Cue.Error -> longArrayOf(0, 400) to 255
        }
        val amplitudes = IntArray(timings.size) { if (it % 2 == 1) amplitude else 0 }
        vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    override fun beep(cue: Cue) {
        // (frequency Hz, duration ms); 0 Hz is a pause. High short beep, lower beep, low double tone.
        val tones = when (cue) {
            Cue.Found -> listOf(1200 to 100)
            Cue.NotFound -> listOf(700 to 150)
            Cue.Error -> listOf(350 to 150, 0 to 80, 350 to 150)
        }
        val pcm = ShortArray(tones.sumOf { (_, ms) -> ms * SAMPLE_RATE / 1000 })
        var at = 0
        for ((hz, ms) in tones) {
            val n = ms * SAMPLE_RATE / 1000
            for (i in 0 until n) {
                // 5 ms fade at both ends so the tone does not click
                val fade = minOf(1.0, i / 220.0, (n - i) / 220.0)
                pcm[at + i] = (if (hz == 0) 0.0 else sin(2 * PI * hz * i / SAMPLE_RATE) * fade * Short.MAX_VALUE * 0.6).toInt().toShort()
            }
            at += n
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) = t.release()
            override fun onPeriodicNotification(t: AudioTrack) {}
        })
        track.play()
    }

    private companion object {
        const val SAMPLE_RATE = 22050
    }
}
