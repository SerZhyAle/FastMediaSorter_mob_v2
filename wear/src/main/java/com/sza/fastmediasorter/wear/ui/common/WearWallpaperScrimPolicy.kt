package com.sza.fastmediasorter.wear.ui.common

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * S2864: how much scrim a delivered photo gets.
 *
 * The scrim is the one contrast mechanism between the wallpaper and the screen content (S2000
 * strategic 3.3.9), and a fixed amount cannot carry that guarantee across an arbitrary photo: a
 * white frame under the tuned floor stayed light enough to wash the white home captions out. The
 * amount therefore follows the frame's own measured luminance - the app still decides the contrast,
 * it reads the input first. Branded wallpapers keep the floor, because their brightness is fixed at
 * build time (S2544, S2729).
 */
object WearWallpaperScrimPolicy {

    /**
     * The amount tuned by S2544/S2729 for surfaces whose brightness the build controls. A photo no
     * brighter than its target band is dimmed by exactly this, so dark photos draw as before.
     */
    const val FLOOR_ALPHA = 0.30f

    /** Safety bound for retuning: no target may ask for more veil than this. */
    const val CEILING_ALPHA = 0.80f

    /** Composite-luminance ceiling the wallpaper is driven under a dark scrim (light content). */
    const val DARK_SCRIM_TARGET_LUMINANCE = 0.25f

    /** The mirrored floor under a light scrim (dark content), on the other side of mid-grey. */
    const val LIGHT_SCRIM_TARGET_LUMINANCE = 0.60f

    private const val SAMPLES_PER_SIDE = 24
    private const val CHANNEL_MASK = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val LUMA_RED = 0.299f
    private const val LUMA_GREEN = 0.587f
    private const val LUMA_BLUE = 0.114f
    private const val BYTE_MAX = 255f

    /**
     * The scrim alpha that brings a frame of [measuredLuminance] into the readable band.
     *
     * [isLightScrim] names the scrim side (S2522): false for black under a dark scheme, true for
     * white under a light one. A frame with no measurement - a failed decode - gets the floor,
     * which is the answer every photo drew before this policy existed.
     */
    fun alphaFor(measuredLuminance: Float?, isLightScrim: Boolean): Float {
        val measured = measuredLuminance ?: return FLOOR_ALPHA
        val needed = if (isLightScrim) {
            (LIGHT_SCRIM_TARGET_LUMINANCE - measured)
                .takeIf { measured < LIGHT_SCRIM_TARGET_LUMINANCE }
                ?.div(1f - measured)
        } else {
            (1f - DARK_SCRIM_TARGET_LUMINANCE / measured)
                .takeIf { measured > DARK_SCRIM_TARGET_LUMINANCE }
        }
        return needed?.coerceIn(FLOOR_ALPHA, CEILING_ALPHA) ?: FLOOR_ALPHA
    }

    /** Mean Rec.601 luma over a coarse grid, the one measurement a single delivered frame needs. */
    fun averageLuminance(bitmap: Bitmap): Float {
        val stride = max(1, min(bitmap.width, bitmap.height) / SAMPLES_PER_SIDE)
        var total = 0f
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                total += pixelLuminance(
                    (pixel shr RED_SHIFT) and CHANNEL_MASK,
                    (pixel shr GREEN_SHIFT) and CHANNEL_MASK,
                    pixel and CHANNEL_MASK
                )
                count++
                x += stride
            }
            y += stride
        }
        return if (count == 0) 0f else total / count
    }

    fun pixelLuminance(red: Int, green: Int, blue: Int): Float =
        (LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue) / BYTE_MAX
}
