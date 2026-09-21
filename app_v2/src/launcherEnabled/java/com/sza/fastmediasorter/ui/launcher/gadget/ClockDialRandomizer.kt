package com.sza.fastmediasorter.ui.launcher.gadget

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.random.Random

/**
 * S3366: the "random dial" gesture's two choices - the next typeface and the next color - stated
 * once, because the dim clock's up-swipe must offer exactly what the desktop widget's up-swipe
 * offers. Extracted from the widget view unchanged, so the two surfaces cannot drift.
 */
internal object ClockDialRandomizer {

    fun nextTypefaceName(currentName: String): String = ClockDialTypeface.entries
        .filter { it.persistedName != currentName }
        .random()
        .persistedName

    /**
     * A saturated hue at a lightness tuned to the surface, rejected until it contrasts enough to be
     * read on it; the floor keeps a pure-luck dial from becoming unreadable on the same surface.
     */
    fun nextDialColor(surfaceColor: Int): Int {
        val lightness = if (ColorUtils.calculateLuminance(surfaceColor) > SURFACE_LIGHTNESS_THRESHOLD) {
            LIGHT_SURFACE_DIAL_LIGHTNESS
        } else {
            DARK_SURFACE_DIAL_LIGHTNESS
        }
        repeat(MAX_RANDOM_COLOR_ATTEMPTS) {
            val candidate = ColorUtils.HSLToColor(
                floatArrayOf(Random.nextFloat() * HUE_DEGREES, DIAL_SATURATION, lightness)
            )
            if (ColorUtils.calculateContrast(candidate, surfaceColor) >= MINIMUM_DIAL_CONTRAST) {
                return candidate
            }
        }
        return if (ColorUtils.calculateLuminance(surfaceColor) > SURFACE_LIGHTNESS_THRESHOLD) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    /** Distance and velocity thresholds for the widget's swipe directions are the resolver's job. */

    private const val HUE_DEGREES = 360f
    private const val DIAL_SATURATION = 0.72f
    private const val LIGHT_SURFACE_DIAL_LIGHTNESS = 0.25f
    private const val DARK_SURFACE_DIAL_LIGHTNESS = 0.80f
    private const val SURFACE_LIGHTNESS_THRESHOLD = 0.5
    private const val MINIMUM_DIAL_CONTRAST = 4.5
    private const val MAX_RANDOM_COLOR_ATTEMPTS = 24
}
