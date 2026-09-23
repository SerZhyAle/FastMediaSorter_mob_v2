package com.sza.fastmediasorter.core.icon

import androidx.annotation.ColorInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * S3433: the on-plate rule of the icon contract (ICON-RENDER 0.10 section 10, item D).
 *
 * A glyph on a decorated plate is white wherever white reaches the 3:1 non-text contrast floor against the
 * plate, and near-black otherwise. White-first rather than "whichever contrasts more": one glyph colour
 * across a family of plates is what makes the decorated looks read as one set, and only a plate too light
 * for white (amber, yellow) takes the dark glyph. Pure arithmetic on ARGB ints, alpha ignored, so the rule
 * is tested on the JVM and the contract exporter's copy of it can be checked against the same cases.
 */
object PlateContrast {

    /** WCAG 2.1 success criterion 1.4.11, the floor for graphical objects. */
    const val MIN_CONTRAST = 3.0

    @ColorInt
    const val ON_PLATE_LIGHT: Int = 0xFFFFFFFF.toInt()

    @ColorInt
    const val ON_PLATE_DARK: Int = 0xFF1F1F1F.toInt()

    private const val FLARE = 0.05
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val CHANNEL_MASK = 0xFF
    private const val CHANNEL_MAX = 255.0
    private const val LINEAR_KNEE = 0.03928
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_DIVISOR = 1.055
    private const val GAMMA = 2.4

    @ColorInt
    fun onPlateColour(@ColorInt plate: Int): Int =
        if (contrastRatio(plate, ON_PLATE_LIGHT) >= MIN_CONTRAST) ON_PLATE_LIGHT else ON_PLATE_DARK

    fun contrastRatio(@ColorInt first: Int, @ColorInt second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + FLARE) / (min(a, b) + FLARE)
    }

    private fun luminance(@ColorInt colour: Int): Double =
        RED_WEIGHT * channel(colour shr RED_SHIFT) +
            GREEN_WEIGHT * channel(colour shr GREEN_SHIFT) +
            BLUE_WEIGHT * channel(colour)

    private fun channel(bits: Int): Double {
        val c = (bits and CHANNEL_MASK) / CHANNEL_MAX
        return if (c <= LINEAR_KNEE) c / LINEAR_DIVISOR else ((c + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
    }
}
