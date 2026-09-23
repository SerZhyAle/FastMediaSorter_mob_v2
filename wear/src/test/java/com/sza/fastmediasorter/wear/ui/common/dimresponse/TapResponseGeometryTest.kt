package com.sza.fastmediasorter.wear.ui.common.dimresponse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3370: the spark pair's shape.
 *
 * The pair must stay exactly opposite for every azimuth - that geometry is how the direction reads
 * without color (strategic §3.2 accessibility) - and the width profile is what makes a stripe read
 * as a "spark" rather than a bar.
 */
class TapResponseGeometryTest {

    @Test
    fun `azimuth zero gives the up vector and azimuth one eighty the down vector`() {
        val up = TapResponseGeometry.directionUnitVector(0f)
        assertEquals(0f, up.first, DELTA)
        assertEquals(-1f, up.second, DELTA)

        val down = TapResponseGeometry.directionUnitVector(180f)
        assertEquals(0f, down.first, DELTA)
        assertEquals(1f, down.second, DELTA)
    }

    @Test
    fun `the pair is exactly one hundred eighty degrees apart for any input`() {
        for (azimuth in 0 until 360 step 15) {
            val north = TapResponseGeometry.directionUnitVector(azimuth.toFloat())
            val south = TapResponseGeometry.directionUnitVector(azimuth + 180f)
            val flipped = TapResponseGeometry.oppositeDirection(north)

            assertEquals(0f, north.first + south.first, DELTA)
            assertEquals(0f, north.second + south.second, DELTA)
            assertEquals(flipped.first, south.first, DELTA)
            assertEquals(flipped.second, south.second, DELTA)
        }
    }

    @Test
    fun `width profile peaks at the midpoint`() {
        assertTrue(TapResponseGeometry.widthProfile(0.5f) > TapResponseGeometry.widthProfile(0.25f))
        assertTrue(TapResponseGeometry.widthProfile(0.25f) > TapResponseGeometry.widthProfile(0.05f))
        assertEquals(
            TapResponseGeometry.widthProfile(0.25f),
            TapResponseGeometry.widthProfile(0.75f),
            DELTA,
        )
        assertEquals(0f, TapResponseGeometry.widthProfile(0f), DELTA)
        assertEquals(0f, TapResponseGeometry.widthProfile(1f), DELTA)
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
