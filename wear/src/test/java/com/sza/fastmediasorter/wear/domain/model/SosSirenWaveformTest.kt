package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3347: the watch is where the owner heard the siren as a tolerable beep, so the waveform is what this
 * ticket changes - and the RMS assertion below is the whole objective claim behind it.
 */
class SosSirenWaveformTest {

    @Test
    fun `renders exactly one cadence cycle`() {
        val samples = SosSirenWaveform.render(SAMPLE_RATE_HZ)

        assertEquals((SosMorseCadence.cycleMs * SAMPLE_RATE_HZ / MILLIS_PER_SECOND).toInt(), samples.size)
    }

    @Test
    fun `uses the whole peak the format allows`() {
        val samples = SosSirenWaveform.render(SAMPLE_RATE_HZ)

        // Neither clipped nor short of full scale: the buffer is normalised onto the peak, not guessed at.
        assertEquals(1.0, SosSirenWaveform.peakRatio(samples), PEAK_TOLERANCE)
    }

    @Test
    fun `carries more energy than a sine at the same peak`() {
        val samples = SosSirenWaveform.render(SAMPLE_RATE_HZ)

        // A sine scores SINE_RMS_RATIO whatever its frequency, which is the beep the owner complained about.
        val rms = SosSirenWaveform.rmsRatio(samples, SAMPLE_RATE_HZ)
        assertTrue("rms $rms is not above a sine's $SINE_RMS_RATIO", rms >= MIN_RMS_RATIO)
        assertTrue("rms $rms exceeds full scale", rms < 1.0)
    }

    @Test
    fun `leaves the silent spans of the cadence silent`() {
        val samples = SosSirenWaveform.render(SAMPLE_RATE_HZ)
        var cursor = 0
        var checkedSilences = 0

        for (span in SosMorseCadence.cycle) {
            val length = (span.durationMs * SAMPLE_RATE_HZ / MILLIS_PER_SECOND).toInt()
            if (!span.engaged) {
                // The torch strobes on these same spans, so a leaking mark would desynchronise light and sound.
                val loudest = (cursor until cursor + length).maxOf { samples[it].toInt() }
                assertEquals("span at $cursor is not silent", 0, loudest)
                checkedSilences++
            }
            cursor += length
        }

        assertTrue("the cadence carried no silence to check", checkedSilences > 0)
    }

    @Test
    fun `fades a mark in rather than starting it at full amplitude`() {
        val samples = SosSirenWaveform.render(SAMPLE_RATE_HZ)

        // A mark that begins at full amplitude is a step in the waveform, which a small speaker clicks on.
        assertEquals(0, samples[0].toInt())
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 22_050
        const val MILLIS_PER_SECOND = 1_000L
        const val SINE_RMS_RATIO = 0.707
        const val MIN_RMS_RATIO = 0.78
        const val PEAK_TOLERANCE = 0.0001
    }
}
