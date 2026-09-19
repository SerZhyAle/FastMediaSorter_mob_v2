package com.sza.fastmediasorter.wear.domain.bloodpressure

import com.sza.fastmediasorter.wear.data.bodysensor.PpgWindowCsv
import com.sza.fastmediasorter.wear.domain.bodysensor.MotionSample
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * S3113: the feature rules against a 30 s window recorded on the owner's Galaxy Watch 7, worn and still,
 * while a cuff on the other arm read 150/98 with a pulse of 71 (research 03).
 */
class ExtractPulseWaveFeaturesUseCaseTest {

    private val extract = ExtractPulseWaveFeaturesUseCase()
    private val still: PpgWindow = PpgWindowCsv.parse(fixture())

    @Test
    fun `the recorded still window yields the cuff pulse within five beats`() {
        val analysis = extract(still)

        assertTrue("expected features, got $analysis", analysis is PulseWaveAnalysis.Accepted)
        val features = (analysis as PulseWaveAnalysis.Accepted).features
        assertTrue("pulse ${features.heartRateBpm}", abs(features.heartRateBpm - CUFF_PULSE) <= PULSE_TOLERANCE)
        assertTrue("beats ${features.acceptedBeats}", features.acceptedBeats >= MIN_EXPECTED_BEATS)
        assertTrue(features.systolicUpstrokeSeconds > 0.0)
        assertTrue(features.pulseWidth50Seconds > features.systolicUpstrokeSeconds / 2)
    }

    @Test
    fun `a moving wrist is refused as motion`() {
        val moving = still.copy(
            motion = still.motion.mapIndexed { index, sample ->
                MotionSample(sample.timestampNanos, if (index % 2 == 0) LOW_G else HIGH_G)
            }
        )

        assertEquals(PulseWaveAnalysis.Rejected(PulseWaveRejection.MOTION), extract(moving))
    }

    @Test
    fun `a flat channel is refused as weak signal`() {
        val flat = still.copy(ppg = still.ppg.map { it.copy(channels = it.channels.map { FLAT_COUNT }) })

        assertEquals(PulseWaveAnalysis.Rejected(PulseWaveRejection.WEAK_SIGNAL), extract(flat))
    }

    @Test
    fun `a clean but short window is refused as too few beats`() {
        val short = still.copy(ppg = still.ppg.take(SHORT_WINDOW_SAMPLES))

        assertEquals(PulseWaveAnalysis.Rejected(PulseWaveRejection.TOO_FEW_BEATS), extract(short))
    }

    private fun fixture(): String =
        requireNotNull(javaClass.classLoader?.getResource("s3113/ppg_still.csv")) { "fixture missing" }.readText()

    private companion object {
        const val CUFF_PULSE = 71.0
        const val PULSE_TOLERANCE = 5.0
        const val MIN_EXPECTED_BEATS = 25
        const val LOW_G = 9.0f
        const val HIGH_G = 10.6f
        const val FLAT_COUNT = 1_985_000f
        const val SHORT_WINDOW_SAMPLES = 250
    }
}
