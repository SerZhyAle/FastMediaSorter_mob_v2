package com.sza.fastmediasorter.wear.complication

import com.sza.fastmediasorter.wear.domain.model.WearClockStyle
import com.sza.fastmediasorter.wear.ui.common.particleArgb
import com.sza.fastmediasorter.wear.ui.common.rollPaletteHues
import com.sza.fastmediasorter.wear.ui.common.waveLineArgb
import kotlin.random.Random

/**
 * S3557: packs a [WearClockStyle] into what a RANGED_VALUE complication can carry - one number and
 * a colour list - because that slot is the only data channel a Watch Face Format face has (ADR-2).
 *
 * The watch face (`watchface/`) decodes the same layout declaratively. This is the one place it is
 * written down; a change here without the same change in the face shows the wrong font or palette.
 *
 * Value (min [CODE_MIN], max [CODE_MAX]):
 * - `code = seconds + 2 * typeface + 10 * palette`
 * - `seconds`: 0 hidden, 1 shown - `code % 2`
 * - `typeface`: 0 default, 1 condensed, 2 serif, 3 monospace, 4 casual - `(code % 10) / 2`
 * - `palette`: 0 DYNAMIC, 1 GREEN, 2 PINK, 3 BLUE - `code / 10`
 *
 * Colour ramp, not interpolated, [COLOR_COUNT] entries in this order:
 * - 0: the dial colour, white when the phone uses its theme colour
 * - 1..4: wave lanes 0..3, hue `(base + lane * step) mod 360`, HSL saturation 0.80, lightness 0.65
 * - 5: particles, the palette's particle hue, HSL saturation 0.90, lightness 0.70
 *
 * `base` and `step` are the WAVE-PARTICLES section 3.3 roll for the palette, drawn from a generator
 * seeded with [WearClockStyle.sentAt]: a style yields the same colours on every request, and a new
 * style from the phone may roll new ones, as a new session on the phone does.
 */
object WearClockStyleFaceEncoder {

    const val CODE_MIN = 0
    const val CODE_MAX = 100
    const val LANE_COUNT = 4
    const val COLOR_COUNT = LANE_COUNT + 2

    private const val TYPEFACE_WEIGHT = 2
    private const val PALETTE_WEIGHT = 10
    private const val FULL_CIRCLE_DEG = 360f
    private const val DEFAULT_DIAL_ARGB = 0xFFFFFFFF.toInt()

    fun code(style: WearClockStyle): Int {
        val seconds = if (style.secondsVisible) 1 else 0
        return seconds + TYPEFACE_WEIGHT * style.typeface.ordinal + PALETTE_WEIGHT * style.palette.ordinal
    }

    fun colors(style: WearClockStyle): IntArray {
        val hues = rollPaletteHues(style.palette, Random(style.sentAt))
        val lanes = List(LANE_COUNT) { lane -> waveLineArgb(laneHue(hues.lineBase, hues.lineStep, lane)) }
        return (listOf(style.dialColor ?: DEFAULT_DIAL_ARGB) + lanes + particleArgb(hues.particleBase))
            .toIntArray()
    }

    /** The lane hues [colors] paints, in degrees - exposed so a test can hold them to the palette band. */
    internal fun laneHues(style: WearClockStyle): List<Float> {
        val hues = rollPaletteHues(style.palette, Random(style.sentAt))
        return List(LANE_COUNT) { lane -> laneHue(hues.lineBase, hues.lineStep, lane) }
    }

    private fun laneHue(base: Float, step: Float, lane: Int): Float = (base + lane * step) % FULL_CIRCLE_DEG
}
