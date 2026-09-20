package com.sza.fastmediasorter.ui.sos

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.sza.fastmediasorter.domain.model.sos.SosSirenWaveform
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
        val samples = SosSirenWaveform.render(SAMPLE_RATE_HZ)
        Timber.i(
            "SosSoundGenerator: siren waveform peak %.3f, rms %.3f of full scale",
            SosSirenWaveform.peakRatio(samples),
            SosSirenWaveform.rmsRatio(samples, SAMPLE_RATE_HZ),
        )
        Timber.d("S3347: phone siren rendered with the band-limited warbled waveform")
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
     * Raises the alarm channel and states, in the log, what it actually reached.
     *
     * The reached value is read back rather than assumed (S3333): a firmware with Do Not Disturb active
     * accepts [AudioManager.setStreamVolume] and leaves the channel where it was, so the only evidence
     * that the siren is at full volume is the channel answering with its own maximum afterwards.
     */
    private fun raiseAlarmVolume(context: Context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val baseline = runCatching { manager.getStreamVolume(AudioManager.STREAM_ALARM) }.getOrNull() ?: return
        restoreAlarmVolume = baseline
        runCatching { manager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0) }
            .onFailure { Timber.w(it, "SosSoundGenerator: the alarm channel refused a direct set") }
        var reached = currentAlarmVolume(manager, baseline)
        if (reached < max) {
            reached = raiseStepwise(manager, max, reached)
        }
        if (reached < max) {
            Timber.w(
                "SosSoundGenerator: siren below full volume - baseline %d, max %d, reached %d",
                baseline,
                max,
                reached,
            )
        } else {
            Timber.i("SosSoundGenerator: siren at full alarm volume - baseline %d, max %d", baseline, max)
        }
    }

    /**
     * Walks the channel up one notch at a time when the direct set did not land.
     *
     * Bounded by the channel's own maximum and stopped as soon as a step fails to move the value: a
     * firmware that refuses the raise altogether would otherwise spin here for the life of the siren.
     */
    private fun raiseStepwise(manager: AudioManager, max: Int, from: Int): Int {
        var reached = from
        var attempts = 0
        var stalled = false
        while (!stalled && reached < max && attempts < max) {
            attempts++
            val accepted = runCatching {
                manager.adjustStreamVolume(
                    AudioManager.STREAM_ALARM,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_ALLOW_RINGER_MODES,
                )
            }.onFailure { failure ->
                Timber.w(failure, "SosSoundGenerator: the alarm channel refused a stepwise raise")
            }.isSuccess
            val next = if (accepted) currentAlarmVolume(manager, reached) else reached
            if (next > reached) reached = next else stalled = true
        }
        return reached
    }

    private fun restoreAlarmVolume(context: Context) {
        val previous = restoreAlarmVolume ?: return
        restoreAlarmVolume = null
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { manager.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0) }
            .onFailure { Timber.w(it, "SosSoundGenerator: could not restore the alarm volume") }
        val now = currentAlarmVolume(manager, previous)
        if (now == previous) {
            Timber.i("SosSoundGenerator: alarm volume back at %d", previous)
        } else {
            Timber.w("SosSoundGenerator: alarm volume not restored - expected %d, now %d", previous, now)
        }
    }

    /** [fallback] rather than a thrown read: a channel that cannot be read must not end the siren. */
    private fun currentAlarmVolume(manager: AudioManager, fallback: Int): Int =
        runCatching { manager.getStreamVolume(AudioManager.STREAM_ALARM) }.getOrDefault(fallback)

    private companion object {

        /**
         * 22.05 kHz halves the buffer a whole cycle needs - one cycle is over five seconds long, so the
         * saving is the difference between a comfortable static track and one near the platform's limit.
         * [SosSirenWaveform] derives its partial ceiling from this rate, so lowering it drops the upper
         * harmonics rather than aliasing them down.
         */
        const val SAMPLE_RATE_HZ = 22_050

        const val INFINITE_LOOP = -1
    }
}
