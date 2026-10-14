package com.sza.fastmediasorter.domain.tourist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarometricAltitudeCalibratorTest {

    @Test
    fun `sea level pressure gives a calibrated altitude`() {
        val altitude = BarometricAltitudeCalibrator().altitude(1013.25f, 1013.25, null, null)

        assertEquals(0.0, altitude.metres, 0.5)
        assertFalse(altitude.approximate)
    }

    @Test
    fun `no anchor is approximate standard atmosphere`() {
        val altitude = BarometricAltitudeCalibrator().altitude(899f, null, null, null)

        assertEquals(1000.0, altitude.metres, 10.0)
        assertTrue(altitude.approximate)
    }

    @Test
    fun `a loose gps fix does not anchor`() {
        val altitude = BarometricAltitudeCalibrator().altitude(1000f, null, 500.0, 40f)

        assertTrue(altitude.approximate)
    }

    @Test
    fun `a tight gps fix anchors and the offset is remembered`() {
        val calibrator = BarometricAltitudeCalibrator()
        val anchored = calibrator.altitude(1013.25f, null, 200.0, 5f)
        val later = calibrator.altitude(1013.25f, null, null, null)

        assertEquals(200.0, anchored.metres, 0.5)
        assertEquals(200.0, later.metres, 0.5)
        assertFalse(later.approximate)
    }
}
