package com.sza.fastmediasorter.core.systeminfo

/**
 * Renders a sensor's active pixel rectangle for the System info report.
 *
 * Takes four ints rather than a `Rect` so the arithmetic carries no Android type and stays provable on
 * the JVM. The origin is printed only when it is not `0,0`: a physical sub-lens can report a non-zero
 * one, and that offset is the difference between a crop computed against the right frame and the wrong
 * one.
 */
internal object CameraActiveArrayText {

    fun format(left: Int, top: Int, right: Int, bottom: Int): String {
        val size = "${right - left}x${bottom - top}"
        return if (left == 0 && top == 0) size else "$size at $left,$top"
    }
}
