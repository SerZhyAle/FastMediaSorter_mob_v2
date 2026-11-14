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
     * S4562: hue, saturation and lightness are all rolled, so one swipe can land on a soft, nearly pastel tone
     * and the next on a dense bright one. The lightness band follows the surface - light tones on a dark
     * surface, dark ones on a light surface - and a candidate is still rejected until it contrasts enough to
     * be read on it; the floor keeps a pure-luck dial from becoming unreadable on the same surface.
     */
    fun nextDialColor(surfaceColor: Int, random: Random = Random): Int {
        val lightSurface = ColorUtils.calculateLuminance(surfaceColor) > SURFACE_LIGHTNESS_THRESHOLD
        val minLightness = if (lightSurface) LIGHT_SURFACE_MIN_LIGHTNESS else DARK_SURFACE_MIN_LIGHTNESS
        val maxLightness = if (lightSurface) LIGHT_SURFACE_MAX_LIGHTNESS else DARK_SURFACE_MAX_LIGHTNESS
        repeat(MAX_RANDOM_COLOR_ATTEMPTS) {
            val candidate = ColorUtils.HSLToColor(
                floatArrayOf(
                    random.nextFloat() * HUE_DEGREES,
                    random.nextFloat(MIN_SATURATION, MAX_SATURATION),
                    random.nextFloat(minLightness, maxLightness),
                )
            )
            if (ColorUtils.calculateContrast(candidate, surfaceColor) >= MINIMUM_DIAL_CONTRAST) {
                return candidate
            }
        }
        return if (lightSurface) Color.BLACK else Color.WHITE
    }

    private fun Random.nextFloat(from: Float, until: Float): Float = from + nextFloat() * (until - from)

    /** Distance and velocity thresholds for the widget's swipe directions are the resolver's job. */

    private const val HUE_DEGREES = 360f

    // Low saturation reads as pale, high as vivid.
    private const val MIN_SATURATION = 0.15f
    private const val MAX_SATURATION = 1.0f

    // On a dark surface the low end is a dense bright tone and the high end an almost white pastel.
    private const val DARK_SURFACE_MIN_LIGHTNESS = 0.55f
    private const val DARK_SURFACE_MAX_LIGHTNESS = 0.95f
    private const val LIGHT_SURFACE_MIN_LIGHTNESS = 0.08f
    private const val LIGHT_SURFACE_MAX_LIGHTNESS = 0.40f
    private const val SURFACE_LIGHTNESS_THRESHOLD = 0.5
    private const val MINIMUM_DIAL_CONTRAST = 4.5
    private const val MAX_RANDOM_COLOR_ATTEMPTS = 24
}
