package com.sza.fastmediasorter.wear.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2864: the scrim policy carries the whole contrast guarantee S2000 strategic 3.3.9 promises over
 * a delivered photo, so the formula is asserted where it lives. The wear module has no Robolectric
 * harness (S1948), so the Bitmap-reading half is exercised on the device, not here.
 */
class WearWallpaperScrimPolicyTest {

    private val darkScrim = false
    private val lightScrim = true

    @Test
    fun `a frame with no measurement gets the floor`() {
        assertEquals(
            WearWallpaperScrimPolicy.FLOOR_ALPHA,
            WearWallpaperScrimPolicy.alphaFor(null, darkScrim),
            DELTA
        )
        assertEquals(
            WearWallpaperScrimPolicy.FLOOR_ALPHA,
            WearWallpaperScrimPolicy.alphaFor(null, lightScrim),
            DELTA
        )
    }

    @Test
    fun `a photo already inside the dark-scrim band keeps the floor`() {
        assertEquals(
            WearWallpaperScrimPolicy.FLOOR_ALPHA,
            WearWallpaperScrimPolicy.alphaFor(0.05f, darkScrim),
            DELTA
        )
    }

    @Test
    fun `a white photo under the dark scrim is driven into the target band`() {
        val expected = 1f - WearWallpaperScrimPolicy.DARK_SCRIM_TARGET_LUMINANCE / 0.97f
        assertEquals(expected, WearWallpaperScrimPolicy.alphaFor(0.97f, darkScrim), DELTA)
    }

    @Test
    fun `a dark photo under the light scrim is driven into the target band`() {
        val expected = (WearWallpaperScrimPolicy.LIGHT_SCRIM_TARGET_LUMINANCE - 0.05f) / 0.95f
        assertEquals(expected, WearWallpaperScrimPolicy.alphaFor(0.05f, lightScrim), DELTA)
    }

    @Test
    fun `a photo already inside the light-scrim band keeps the floor`() {
        assertEquals(
            WearWallpaperScrimPolicy.FLOOR_ALPHA,
            WearWallpaperScrimPolicy.alphaFor(0.90f, lightScrim),
            DELTA
        )
    }

    @Test
    fun `a barely too bright photo needs no more than the floor`() {
        assertEquals(
            WearWallpaperScrimPolicy.FLOOR_ALPHA,
            WearWallpaperScrimPolicy.alphaFor(0.26f, darkScrim),
            DELTA
        )
    }

    @Test
    fun `every answer stays between the floor and the ceiling`() {
        val samples = listOf(0.0f, 0.05f, 0.26f, 0.31f, 0.5f, 0.7f, 0.9f, 0.99f, 1.0f, null)
        samples.forEach { measured ->
            listOf(darkScrim, lightScrim).forEach { scrim ->
                val alpha = WearWallpaperScrimPolicy.alphaFor(measured, scrim)
                assertTrue(
                    "measured=$measured lightScrim=$scrim alpha=$alpha",
                    alpha in WearWallpaperScrimPolicy.FLOOR_ALPHA..WearWallpaperScrimPolicy.CEILING_ALPHA
                )
            }
        }
    }

    @Test
    fun `pixel luminance matches the Rec 601 weights`() {
        assertEquals(1f, WearWallpaperScrimPolicy.pixelLuminance(255, 255, 255), DELTA)
        assertEquals(0f, WearWallpaperScrimPolicy.pixelLuminance(0, 0, 0), DELTA)
        assertEquals(0.299f, WearWallpaperScrimPolicy.pixelLuminance(255, 0, 0), DELTA)
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
