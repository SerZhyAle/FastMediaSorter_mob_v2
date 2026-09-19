package com.sza.fastmediasorter.wear.domain.bloodpressure

import com.sza.fastmediasorter.wear.domain.model.BloodPressureCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimateBloodPressureUseCaseTest {

    private val estimate = EstimateBloodPressureUseCase()

    @Test
    fun `no pair and one pair both refuse with the number still needed`() {
        assertEquals(BloodPressureEstimate.NotCalibrated(0, REQUIRED_PAIRS), estimate(features(HR_MID), emptyList()))
        assertEquals(
            BloodPressureEstimate.NotCalibrated(1, REQUIRED_PAIRS),
            estimate(features(HR_MID), listOf(pair(HR_MID, SYSTOLIC_MID, DIASTOLIC_MID, 1L)))
        )
    }

    @Test
    fun `two pairs give an estimate without an error figure`() {
        val pairs = listOf(
            pair(HR_LOW, SYSTOLIC_LOW, DIASTOLIC_LOW, 1L),
            pair(HR_HIGH, SYSTOLIC_HIGH, DIASTOLIC_HIGH, 2L)
        )

        val result = estimate(features(HR_MID), pairs) as BloodPressureEstimate.Estimated

        assertEquals(SYSTOLIC_MID, result.systolic)
        assertEquals(2L, result.newestPairMillis)
        assertNull(result.leaveOneOutError)
    }

    @Test
    fun `three pairs on a line are reproduced at the centre and shrunk toward it at the edge`() {
        val pairs = listOf(
            pair(HR_LOW, SYSTOLIC_LOW, DIASTOLIC_LOW, 1L),
            pair(HR_MID, SYSTOLIC_MID, DIASTOLIC_MID, 2L),
            pair(HR_HIGH, SYSTOLIC_HIGH, DIASTOLIC_HIGH, 3L)
        )

        val centre = estimate(features(HR_MID), pairs) as BloodPressureEstimate.Estimated
        val edge = estimate(features(HR_HIGH), pairs) as BloodPressureEstimate.Estimated

        assertEquals(SYSTOLIC_MID, centre.systolic)
        assertEquals(DIASTOLIC_MID, centre.diastolic)
        assertTrue("ridge shrinks: ${edge.systolic}", edge.systolic in (SYSTOLIC_MID + 1) until SYSTOLIC_HIGH)
        assertNotNull(centre.leaveOneOutError)
    }

    private fun features(heartRate: Double) = PulseWaveFeatures(
        heartRateBpm = heartRate,
        systolicUpstrokeSeconds = UPSTROKE,
        pulseWidth50Seconds = WIDTH,
        perfusionIndex = PERFUSION,
        acceptedBeats = BEATS
    )

    private fun pair(heartRate: Double, systolic: Int, diastolic: Int, at: Long) = BloodPressureCalibration(
        id = at,
        systolic = systolic,
        diastolic = diastolic,
        timestampMillis = at,
        windowFile = null,
        features = features(heartRate)
    )

    private companion object {
        const val REQUIRED_PAIRS = 2
        const val HR_LOW = 60.0
        const val HR_MID = 70.0
        const val HR_HIGH = 80.0
        const val SYSTOLIC_LOW = 160
        const val SYSTOLIC_MID = 170
        const val SYSTOLIC_HIGH = 180
        const val DIASTOLIC_LOW = 100
        const val DIASTOLIC_MID = 105
        const val DIASTOLIC_HIGH = 110
        const val UPSTROKE = 0.35
        const val WIDTH = 0.52
        const val PERFUSION = 0.0006
        const val BEATS = 30
    }
}
