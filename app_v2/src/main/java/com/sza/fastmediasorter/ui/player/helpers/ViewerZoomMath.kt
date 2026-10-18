package com.sza.fastmediasorter.ui.player.helpers

/**
 * MEDIA-VIEWER rules 3.1/3.2: the ordinary zoom step is multiplicative 1.25 and "actual size" is a
 * reachable target scale expressed relative to the fitted size. Shared by the still and video zoom
 * surfaces so screen buttons, keys and Ctrl+wheel move in the same increments.
 */
object ViewerZoomMath {

    const val STEP = 1.25f

    fun stepIn(scale: Float, max: Float): Float = (scale * STEP).coerceAtMost(max)

    fun stepOut(scale: Float, min: Float): Float = (scale / STEP).coerceAtLeast(min)

    /**
     * Scale relative to the fitted size at which one content pixel maps to one screen pixel: the fit
     * shows [contentPx] wide content at min(fitWidthPx/contentPx, fitHeightPx/contentPy) of its true
     * size, so actual size needs the reciprocal. 1f when any input is not positive.
     */
    fun actualSizeScale(contentPx: Int, contentPy: Int, fitWidthPx: Float, fitHeightPx: Float): Float {
        val contentValid = contentPx > 0 && contentPy > 0
        val fitValid = fitWidthPx > 0f && fitHeightPx > 0f
        if (!contentValid || !fitValid) return 1f
        val fitScale = minOf(fitWidthPx / contentPx, fitHeightPx / contentPy)
        return 1f / fitScale
    }
}
