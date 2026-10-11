package com.sza.fastmediasorter.wear.domain.tourist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearBarometricAltitudeCalibratorTest {

    @Test
    fun `sea level pressure gives a calibrated altitude`() {
        val altitude = WearBarometricAltitudeCalibrator().altitude(1013.25f, 1013.25, null, null, false)

        assertEquals(0.0, altitude.metres, 0.5)
        assertFalse(altitude.approximate)
    }

    @Test
    fun `no anchor is approximate standard atmosphere`() {
        val altitude = WearBarometricAltitudeCalibrator().altitude(899f, null, null, null, false)

        assertEquals(1000.0, altitude.metres, 10.0)
        assertTrue(altitude.approximate)
    }

    @Test
    fun `a loose gps fix does not anchor`() {
        val altitude = WearBarometricAltitudeCalibrator().altitude(1000f, null, 500.0, 40f, false)

        assertTrue(altitude.approximate)
    }

    @Test
    fun `a tight gps fix anchors and the offset is remembered`() {
        val calibrator = WearBarometricAltitudeCalibrator()
        val anchored = calibrator.altitude(1013.25f, null, 200.0, 5f, false)
        val later = calibrator.altitude(1013.25f, null, null, null, false)

        assertEquals(200.0, anchored.metres, 0.5)
        assertEquals(200.0, later.metres, 0.5)
        assertFalse(later.approximate)
    }

    @Test
    fun `a tight ellipsoid fix does not anchor`() {
        val altitude = WearBarometricAltitudeCalibrator().altitude(
            pressureHpa = 1013.25f,
            seaLevelHpa = null,
            gpsAltitudeM = 240.0,
            gpsVerticalAccuracyM = 3f,
            gpsAltitudeIsEllipsoid = true,
        )

        assertTrue(altitude.approximate)
        assertEquals(0.0, altitude.metres, 0.5)
    }
}
