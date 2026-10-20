package com.sza.fastmediasorter.broadcast

import kotlin.math.max

/**
 * S4233: the size of the stream that carries the phone's screen. The stream keeps the screen's own
 * aspect at the moment the session starts and is capped on its long side by the configured video
 * size, so a tall phone screen does not become a 16:9 letterbox and a 4K tablet does not ask the
 * hardware encoder for more than a camera session would.
 */
object ScreenStreamGeometry {

    data class Size(val width: Int, val height: Int)

    /**
     * Both sides come out even and at least [MIN_SIDE]: an odd or zero side is refused by the H.264
     * encoder when the session opens, which only a device would otherwise show. A screen smaller than
     * [maxLongSide] is never scaled up.
     */
    fun fit(screenWidth: Int, screenHeight: Int, maxLongSide: Int): Size {
        val width = screenWidth.coerceAtLeast(MIN_SIDE)
        val height = screenHeight.coerceAtLeast(MIN_SIDE)
        val cap = maxLongSide.coerceAtLeast(MIN_SIDE)
        val longSide = max(width, height)
        return Size(evenSide(scaled(width, cap, longSide)), evenSide(scaled(height, cap, longSide)))
    }

    // Integer arithmetic: a floating scale turns an exact 576 into 575 and the even rounding then drops it to 574.
    private fun scaled(side: Int, cap: Int, longSide: Int): Int =
        if (longSide > cap) (side.toLong() * cap / longSide).toInt() else side

    private fun evenSide(side: Int): Int = (side and 1.inv()).coerceAtLeast(MIN_SIDE)

    private const val MIN_SIDE = 2
}
