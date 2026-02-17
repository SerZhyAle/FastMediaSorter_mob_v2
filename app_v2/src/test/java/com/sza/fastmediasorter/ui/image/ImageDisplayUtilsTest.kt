package com.sza.fastmediasorter.ui.image

import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDisplayUtilsTest {

    @Test
    fun `isOrientationMatch returns true for landscape image and landscape device`() {
        val result = ImageDisplayUtils.isOrientationMatch(
            imageWidth = 1920,
            imageHeight = 1080,
            deviceWidth = 2400,
            deviceHeight = 1080
        )

        assertTrue(result)
    }

    @Test
    fun `isOrientationMatch returns true for portrait image and portrait device`() {
        val result = ImageDisplayUtils.isOrientationMatch(
            imageWidth = 1080,
            imageHeight = 1920,
            deviceWidth = 1080,
            deviceHeight = 2400
        )

        assertTrue(result)
    }

    @Test
    fun `isOrientationMatch returns false for mismatched orientations`() {
        val result = ImageDisplayUtils.isOrientationMatch(
            imageWidth = 1080,
            imageHeight = 1920,
            deviceWidth = 2400,
            deviceHeight = 1080
        )

        assertFalse(result)
    }

    @Test
    fun `isOrientationMatch returns true for square image on landscape device`() {
        val result = ImageDisplayUtils.isOrientationMatch(
            imageWidth = 1000,
            imageHeight = 1000,
            deviceWidth = 2400,
            deviceHeight = 1080
        )

        assertTrue(result)
    }

    @Test
    fun `determineImageScaleType returns fit center when crop setting disabled`() {
        val scaleType = ImageDisplayUtils.determineImageScaleType(
            cropImagesToFullscreen = false,
            isFullscreenOrSlideshow = true,
            imageWidth = 1920,
            imageHeight = 1080,
            deviceWidth = 2400,
            deviceHeight = 1080
        )

        assertEquals(ImageView.ScaleType.FIT_CENTER, scaleType)
    }

    @Test
    fun `determineImageScaleType returns fit center when not fullscreen or slideshow`() {
        val scaleType = ImageDisplayUtils.determineImageScaleType(
            cropImagesToFullscreen = true,
            isFullscreenOrSlideshow = false,
            imageWidth = 1920,
            imageHeight = 1080,
            deviceWidth = 2400,
            deviceHeight = 1080
        )

        assertEquals(ImageView.ScaleType.FIT_CENTER, scaleType)
    }

    @Test
    fun `determineImageScaleType returns center crop when enabled and orientations match`() {
        val scaleType = ImageDisplayUtils.determineImageScaleType(
            cropImagesToFullscreen = true,
            isFullscreenOrSlideshow = true,
            imageWidth = 1920,
            imageHeight = 1080,
            deviceWidth = 2400,
            deviceHeight = 1080
        )

        assertEquals(ImageView.ScaleType.CENTER_CROP, scaleType)
    }

    @Test
    fun `determineImageScaleType returns fit center when enabled and orientations mismatch`() {
        val scaleType = ImageDisplayUtils.determineImageScaleType(
            cropImagesToFullscreen = true,
            isFullscreenOrSlideshow = true,
            imageWidth = 1080,
            imageHeight = 1920,
            deviceWidth = 2400,
            deviceHeight = 1080
        )

        assertEquals(ImageView.ScaleType.FIT_CENTER, scaleType)
    }
}
