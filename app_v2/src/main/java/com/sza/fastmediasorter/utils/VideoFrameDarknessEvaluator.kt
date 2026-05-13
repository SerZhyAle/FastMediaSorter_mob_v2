package com.sza.fastmediasorter.utils

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Evaluates whether a video frame bitmap is "dark" (e.g. a black leader or fade-to-black).
 * Uses downscaled full-pixel luma scan compatible with API 23+.
 * S0178 ADR-1, ADR-2.
 */
object VideoFrameDarknessEvaluator {

    /**
     * Returns true if [bitmap] is classified as dark according to [VideoFrameExtractionPolicy.DARKNESS_LUMA_THRESHOLD].
     *
     * Algorithm:
     * 1. Downscale to DOWNSCALE_SIZE × DOWNSCALE_SIZE.
     * 2. Read all pixels via getPixels.
     * 3. Compute mean luma using BT.601 formula (API 1+ compatible).
     * 4. Compare mean luma against threshold.
     */
    fun isDark(bitmap: Bitmap): Boolean {
        val size = VideoFrameExtractionPolicy.DOWNSCALE_SIZE
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, false)
        return try {
            val pixelCount = scaled.width * scaled.height
            val pixels = IntArray(pixelCount)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)

            var lumaSum = 0.0
            for (pixel in pixels) {
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // BT.601 luma formula, compatible with API 23+
                lumaSum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            }
            val meanLuma = lumaSum / pixelCount
            meanLuma < VideoFrameExtractionPolicy.DARKNESS_LUMA_THRESHOLD
        } finally {
            // Recycle the downscaled copy only if it is a different object than the input
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }
    }
}
