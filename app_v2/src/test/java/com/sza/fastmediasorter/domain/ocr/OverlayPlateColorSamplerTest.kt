package com.sza.fastmediasorter.domain.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayPlateColorSamplerTest {

    private fun createBitmap(w: Int, h: Int, fillColor: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                bitmap.setPixel(x, y, fillColor)
            }
        }
        return bitmap
    }

    @Test
    fun testMedianOddElements() {
        assertEquals(20, OverlayPlateColorSampler.median(listOf(10, 30, 20)))
    }

    @Test
    fun testMedianEvenElements() {
        assertEquals(20, OverlayPlateColorSampler.median(listOf(10, 20, 30, 40)))
    }

    @Test
    fun testUniformBackgroundFallback() {
        val bg = Color.rgb(240, 240, 240)
        val bmp = createBitmap(100, 50, bg)
        val rect = Rect(10, 10, 90, 40)

        val result = OverlayPlateColorSampler.samplePlateColors(bmp, rect)

        assertEquals(bg, result.paperColor)
        assertEquals(Color.BLACK, result.inkColor)
        assertTrue(result.isFallbackPair)
    }

    @Test
    fun testUniformDarkBackgroundFallback() {
        val bg = Color.rgb(20, 20, 20)
        val bmp = createBitmap(100, 50, bg)
        val rect = Rect(10, 10, 90, 40)

        val result = OverlayPlateColorSampler.samplePlateColors(bmp, rect)

        assertEquals(bg, result.paperColor)
        assertEquals(Color.WHITE, result.inkColor)
        assertTrue(result.isFallbackPair)
    }

    @Test
    fun testDistinctTextPixelsSampleMedianInkColor() {
        val paper = Color.rgb(250, 250, 250)
        val ink = Color.rgb(15, 15, 15)
        val bmp = createBitmap(100, 50, paper)

        // Draw text pixels inside rect(10, 10, 90, 40)
        for (y in 15..25) {
            for (x in 20..55) {
                bmp.setPixel(x, y, ink)
            }
        }

        val result = OverlayPlateColorSampler.samplePlateColors(bmp, Rect(10, 10, 90, 40))

        assertEquals(paper, result.paperColor)
        assertEquals(ink, result.inkColor)
        assertFalse(result.isFallbackPair)
    }

    @Test
    fun testTransparentPixelsIgnored() {
        val bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val solidPaper = Color.rgb(100, 150, 200)

        for (y in 0 until 50) {
            for (x in 0 until 50) {
                if (x < 25) {
                    bmp.setPixel(x, y, Color.TRANSPARENT)
                } else {
                    bmp.setPixel(x, y, solidPaper)
                }
            }
        }

        val result = OverlayPlateColorSampler.samplePlateColors(bmp, Rect(0, 0, 50, 50))
        assertEquals(solidPaper, result.paperColor)
    }

    @Test
    fun testBorderStripVotesInvertPaperAndInk() {
        val outerBackground = Color.rgb(240, 240, 240)
        val darkBoxColor = Color.rgb(20, 20, 20)
        val bmp = createBitmap(200, 100, outerBackground)

        val plateRect = Rect(30, 20, 170, 80)
        // Fill box interior with darkBoxColor
        for (y in plateRect.top until plateRect.bottom) {
            for (x in plateRect.left until plateRect.right) {
                bmp.setPixel(x, y, darkBoxColor)
            }
        }
        // Draw light text inside dark box
        for (y in 30..45) {
            for (x in 40..100) {
                bmp.setPixel(x, y, outerBackground)
            }
        }

        val result = OverlayPlateColorSampler.samplePlateColors(bmp, plateRect)

        // Border voting votes for outerBackground as paper
        assertEquals(outerBackground, result.paperColor)
        assertEquals(darkBoxColor, result.inkColor)
    }

    @Test
    fun testContrastFloorFallback() {
        val paper = Color.rgb(200, 200, 200)
        val lowContrastInk = Color.rgb(180, 160, 160)
        val bmp = createBitmap(100, 50, paper)

        for (y in 15..30) {
            for (x in 20..60) {
                bmp.setPixel(x, y, lowContrastInk)
            }
        }

        val result = OverlayPlateColorSampler.samplePlateColors(bmp, Rect(10, 10, 90, 40))

        assertEquals(paper, result.paperColor)
        assertEquals(Color.BLACK, result.inkColor)
        assertTrue(result.isFallbackPair)
    }

    @Test
    fun testImageBoundaryClamping() {
        val bmp = createBitmap(50, 50, Color.BLUE)
        val rect = Rect(-10, -20, 100, 120)

        val result = OverlayPlateColorSampler.samplePlateColors(bmp, rect)
        assertEquals(Color.BLUE, result.paperColor)
    }
}