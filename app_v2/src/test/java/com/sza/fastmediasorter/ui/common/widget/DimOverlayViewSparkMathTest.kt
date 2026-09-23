package com.sza.fastmediasorter.ui.common.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3370: the pure spark math behind the dim overlay's spark pair.
 *
 * The pair must stay exactly opposite for every azimuth - that geometry is how the direction reads
 * without color (strategic §3.2 accessibility) - and the width profile is what makes a stripe read
 * as a "spark" rather than a bar.
 */
class DimOverlayViewSparkMathTest {

    @Test
    fun `azimuth zero points north up and azimuth one eighty points down`() {
        val (upX, upY) = DimOverlayView.sparkNorthDirectionComponents(0f)
        assertEquals(0f, upX, DELTA)
        assertEquals(-1f, upY, DELTA)

        val (downX, downY) = DimOverlayView.sparkNorthDirectionComponents(180f)
        assertEquals(0f, downX, DELTA)
        assertEquals(1f, downY, DELTA)
    }

    @Test
    fun `the pair stays opposite for any azimuth`() {
        for (azimuth in 0 until 360 step 15) {
            val (northX, northY) = DimOverlayView.sparkNorthDirectionComponents(azimuth.toFloat())
            val (southX, southY) = DimOverlayView.sparkNorthDirectionComponents(azimuth + 180f)

            assertEquals(0f, northX + southX, DELTA)
            assertEquals(0f, northY + southY, DELTA)
        }
    }

    @Test
    fun `width profile peaks mid stroke and tapers to both ends`() {
        assertTrue(DimOverlayView.sparkWidthProfile(0.5f) > DimOverlayView.sparkWidthProfile(0.25f))
        assertTrue(DimOverlayView.sparkWidthProfile(0.25f) > DimOverlayView.sparkWidthProfile(0.05f))
        assertEquals(
            DimOverlayView.sparkWidthProfile(0.25f),
            DimOverlayView.sparkWidthProfile(0.75f),
            DELTA,
        )
        assertEquals(0f, DimOverlayView.sparkWidthProfile(0f), DELTA)
        assertEquals(0f, DimOverlayView.sparkWidthProfile(1f), DELTA)
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
