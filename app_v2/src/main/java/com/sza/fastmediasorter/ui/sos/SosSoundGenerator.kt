package com.sza.fastmediasorter.ui.sos

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.sza.fastmediasorter.domain.model.sos.SosMorseCadence
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

/**
 * S3216: the siren half of the distress signal - one synthesised `... --- ...` cycle played on the alarm
 * channel and looped by the audio hardware.
 *
 * One pre-rendered buffer in a STATIC track rather than a timer switching a tone on and off: a loop the
 * hardware owns keeps its cadence while the process is starved, which a screen-off emergency signal is
 * exactly the case for. It also means [start] and [stop] are the whole surface, so the mode can engage
 * the sound alone (strategic ADR-4).
 *
 * The alarm channel is turned up to full and put back on [stop] (strategic §3.2): the channel is what
 * lets the siren sound through a silenced phone, and leaving the owner's alarm volume changed after an
 * emergency would be a defect of its own.
 */
@Singleton
class SosSoundGenerator @Inject constructor() {

    private var track: AudioTrack? = null
    private var restoreAlarmVolume: Int? = null

    val isPlaying: Boolean get() = track != null

    /** Idempotent: a second call while the siren runs is a no-op rather than a second track. */
    fun start(context: Context) {
        if (track != null) return
        raiseAlarmVolume(context)
        runCatching { buildLoopingTrack() }
            .onFailure { Timber.w(it, "SosSoundGenerator: could not open the alarm track") }
            .onSuccess { built ->
                track = built
                built.play()
            }
    }

    /** Safe to call when nothing is playing, so the caller never has to track state of its own. */
    fun stop(context: Context) {
        track?.let { active ->
            runCatching {
                active.stop()
                active.release()
            }.onFailure { Timber.w(it, "SosSoundGenerator: releasing the alarm track failed") }
        }
        track = null
        restoreAlarmVolume(context)
    }

    private fun buildLoopingTrack(): AudioTrack {
        val samples = renderCycle()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .build()
            .apply {
                write(samples, 0, samples.size)
                setVolume(AudioTrack.getMaxVolume())
                // -1 loops until stop(); the cadence then survives a starved process.
                setLoopPoints(0, samples.size, INFINITE_LOOP)
            }
    }

    /**
     * One cycle of the cadence as 16-bit PCM.
     *
     * The tone is faded in and out over a few milliseconds at each mark boundary. A sine cut mid-period
     * is a step in the waveform, which a small speaker reproduces as a click on every dot - about twenty
     * of them per cycle, loud enough to muddy the pattern the signal exists to be recognised by.
     */
    private fun renderCycle(): ShortArray {
        val total = (SosMorseCadence.cycleMs * SAMPLE_RATE_HZ / MILLIS_PER_SECOND).toInt()
        val samples = ShortArray(total)
        var cursor = 0
        for (span in SosMorseCadence.cycle) {
            val length = (span.durationMs * SAMPLE_RATE_HZ / MILLIS_PER_SECOND).toInt()
                .coerceAtMost(total - cursor)
            if (span.engaged) {
                writeTone(samples, cursor, length)
            }
            cursor += length
        }
        return samples
    }

    private fun writeTone(samples: ShortArray, offset: Int, length: Int) {
        val rampSamples = (RAMP_MS * SAMPLE_RATE_HZ / MILLIS_PER_SECOND).toInt().coerceAtMost(length / 2)
        for (index in 0 until length) {
            val envelope = when {
                rampSamples == 0 -> 1.0
                index < rampSamples -> index.toDouble() / rampSamples
                index >= length - rampSamples -> (length - index).toDouble() / rampSamples
                else -> 1.0
            }
            val angle = TWO_PI * TONE_HZ * index / SAMPLE_RATE_HZ
            samples[offset + index] = (sin(angle) * envelope * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun raiseAlarmVolume(context: Context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            val max = manager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            restoreAlarmVolume = manager.getStreamVolume(AudioManager.STREAM_ALARM)
            manager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }.onFailure { Timber.w(it, "SosSoundGenerator: alarm volume left as the device had it") }
    }

    private fun restoreAlarmVolume(context: Context) {
        val previous = restoreAlarmVolume ?: return
        restoreAlarmVolume = null
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { manager.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0) }
            .onFailure { Timber.w(it, "SosSoundGenerator: could not restore the alarm volume") }
    }

    private companion object {

        /**
         * 22.05 kHz is enough headroom for the tone below and halves the buffer a whole cycle needs -
         * one cycle is over five seconds long, so the saving is the difference between a comfortable
         * static track and one near the platform's limit.
         */
        const val SAMPLE_RATE_HZ = 22_050

        /**
         * 3.2 kHz: near the ear's most sensitive band and near the resonance of the small speakers in a
         * phone and a watch, which is what makes the siren carry (strategic §5).
         */
        const val TONE_HZ = 3_200.0

        const val RAMP_MS = 4L
        const val MILLIS_PER_SECOND = 1_000L
        const val TWO_PI = 2.0 * PI
        const val INFINITE_LOOP = -1
    }
}
