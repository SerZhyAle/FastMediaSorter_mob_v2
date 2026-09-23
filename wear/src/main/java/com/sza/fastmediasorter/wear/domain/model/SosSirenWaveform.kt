package com.sza.fastmediasorter.wear.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * S3347: the shape of the watch siren's sound - one full [SosMorseCadence] cycle rendered as 16-bit PCM.
 *
 * The numbers below are the phone half's, not tuned per device: the two are one signal, and a listener has
 * to recognise the same sound from either.
 *
 * Pure and separate from the audio track so the shape is measurable on the JVM. The complaint that
 * created this object is subjective ("a tolerable beep at full volume"), and the only objective handles
 * on it are [peakRatio] and [rmsRatio]; a private method inside a class that needs a real AudioTrack
 * could not be held to either.
 *
 * The channel volume is not part of the answer - S3333 already pins it to the device maximum and proved
 * it by reading the channel back. Three properties of the WAVEFORM carry the loudness instead:
 * - A band-limited square rather than a sine. Playback is limited by the peak sample, and loudness at a
 *   fixed peak follows RMS, where a sine is the quietest waveform that exists: exactly 0.707 of full
 *   scale at any frequency.
 * - Partials spread over roughly 1.5-7 kHz instead of one tone, so more than one critical band of the
 *   ear is excited and no single null in the speaker or in the room can swallow the signal.
 * - A warbled fundamental. A steady tone is adapted to within seconds, which is what turns an alarm into
 *   something one can sit next to; a frequency that keeps moving cannot be tuned out.
 */
object SosSirenWaveform {

    /**
     * One cadence cycle, peak-normalised to full scale.
     *
     * [sampleRateHz] is the caller's: the partial ceiling is derived from it, so a lower rate drops the
     * upper harmonics rather than folding them back down as aliases.
     */
    fun render(sampleRateHz: Int): ShortArray {
        val total = (SosMorseCadence.cycleMs * sampleRateHz / MILLIS_PER_SECOND).toInt()
        val shaped = DoubleArray(total)
        forEachSpan(sampleRateHz, total) { span, offset, length ->
            if (span.engaged) {
                writeMark(shaped, offset, length, sampleRateHz)
            }
        }
        return normalise(shaped)
    }

    /** Peak as a fraction of full scale: 1.0 by construction, which is why it is worth asserting. */
    fun peakRatio(samples: ShortArray): Double {
        val peak = samples.maxOfOrNull { abs(it.toInt()) } ?: 0
        return peak / FULL_SCALE
    }

    /**
     * RMS of the sounding spans only, as a fraction of full scale.
     *
     * Silence is excluded because the cadence's duty cycle would otherwise dominate the figure. A sine
     * scores 0.707 here whatever its frequency, so this single number says whether a change of waveform
     * actually bought any loudness.
     */
    fun rmsRatio(samples: ShortArray, sampleRateHz: Int): Double {
        var square = 0.0
        var counted = 0
        forEachSpan(sampleRateHz, samples.size) { span, offset, length ->
            if (span.engaged) {
                for (index in offset until offset + length) {
                    square += samples[index].toDouble() * samples[index]
                }
                counted += length
            }
        }
        return if (counted == 0) 0.0 else sqrt(square / counted) / FULL_SCALE
    }

    /**
     * Walks the cadence once, handing each span its own slice of a buffer of [total] samples.
     *
     * Shared by the renderer and the RMS measurement so the two cannot disagree about where a mark ends -
     * a disagreement that would show up as a plausible-looking number rather than as a failure.
     */
    private inline fun forEachSpan(sampleRateHz: Int, total: Int, action: (SosMorseCadence.Span, Int, Int) -> Unit) {
        var cursor = 0
        for (span in SosMorseCadence.cycle) {
            val length = (span.durationMs * sampleRateHz / MILLIS_PER_SECOND).toInt()
                .coerceAtMost(total - cursor)
            action(span, cursor, length)
            cursor += length
        }
    }

    /**
     * One sounding mark.
     *
     * The phase is accumulated rather than computed from the sample index: the fundamental moves inside
     * the mark, and a frequency change applied to an absolute-time formula jumps the phase, which the
     * speaker reproduces as a click in the middle of every mark.
     */
    private fun writeMark(target: DoubleArray, offset: Int, length: Int, sampleRateHz: Int) {
        val rampSamples = (RAMP_MS * sampleRateHz / MILLIS_PER_SECOND).toInt().coerceAtMost(length / 2)
        val partialCeilingHz = sampleRateHz * PARTIAL_CEILING_RATIO
        var phase = 0.0
        for (index in 0 until length) {
            val fundamental = warbledFundamental(index, sampleRateHz)
            phase += TWO_PI * fundamental / sampleRateHz
            target[offset + index] = partialSum(phase, fundamental, partialCeilingHz) *
                envelope(index, length, rampSamples)
        }
    }

    /**
     * Odd partials at 1/n amplitude - a band-limited square, the highest RMS a peak-limited buffer holds.
     *
     * A partial is included only while it stays under [ceilingHz]. Above half the sample rate it does not
     * simply vanish: it folds back down as an alias and sounds as a foreign tone unrelated to the siren.
     */
    private fun partialSum(phase: Double, fundamentalHz: Double, ceilingHz: Double): Double {
        var sum = 0.0
        var harmonic = 1
        while (harmonic <= HIGHEST_PARTIAL && fundamentalHz * harmonic <= ceilingHz) {
            sum += sin(phase * harmonic) / harmonic
            harmonic += PARTIAL_STEP
        }
        return sum
    }

    /** A triangular sweep between the two bounds: the slope never rests, so the ear cannot settle on it. */
    private fun warbledFundamental(index: Int, sampleRateHz: Int): Double {
        val periodSamples = (sampleRateHz / WARBLE_HZ).toInt().coerceAtLeast(1)
        val position = (index % periodSamples).toDouble() / periodSamples
        val triangle = if (position < HALF) position / HALF else (1.0 - position) / HALF
        return WARBLE_LOW_HZ + (WARBLE_HIGH_HZ - WARBLE_LOW_HZ) * triangle
    }

    /** Linear fades at both edges of a mark; a waveform cut mid-period is a step, and a step is a click. */
    private fun envelope(index: Int, length: Int, rampSamples: Int): Double = when {
        rampSamples == 0 -> 1.0
        index < rampSamples -> index.toDouble() / rampSamples
        index >= length - rampSamples -> (length - index).toDouble() / rampSamples
        else -> 1.0
    }

    /**
     * Scales the whole cycle so its loudest sample lands exactly on full scale.
     *
     * Measured rather than assumed: the partial count changes across the warble, so the crest of the sum
     * is not a number that can be written down in advance. Normalising it leaves neither clipping nor
     * unused headroom.
     */
    private fun normalise(shaped: DoubleArray): ShortArray {
        val peak = shaped.maxOfOrNull { abs(it) } ?: 0.0
        val scale = if (peak > 0.0) Short.MAX_VALUE / peak else 0.0
        return ShortArray(shaped.size) { index -> (shaped[index] * scale).toInt().toShort() }
    }

    /** Full scale as a double, so a ratio never silently becomes integer division. */
    private const val FULL_SCALE = 32_767.0

    /**
     * 1.5-2.4 kHz for the fundamental: its first three odd partials then cover the band where the ear is
     * most sensitive and where the small speakers of a phone and a watch resonate.
     */
    private const val WARBLE_LOW_HZ = 1_500.0
    private const val WARBLE_HIGH_HZ = 2_400.0

    /** 8 Hz - fast enough to read as one urgent sound rather than two alternating tones. */
    private const val WARBLE_HZ = 8.0

    /** 0.42 of the sample rate: a margin below Nyquist, where a partial would fold back as an alias. */
    private const val PARTIAL_CEILING_RATIO = 0.42

    private const val HIGHEST_PARTIAL = 5
    private const val PARTIAL_STEP = 2
    private const val RAMP_MS = 4L
    private const val MILLIS_PER_SECOND = 1_000L
    private const val TWO_PI = 2.0 * PI
    private const val HALF = 0.5
}
