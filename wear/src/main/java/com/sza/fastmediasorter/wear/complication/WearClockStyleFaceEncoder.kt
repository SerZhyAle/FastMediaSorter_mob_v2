package com.sza.fastmediasorter.wear.complication

import com.sza.fastmediasorter.wear.domain.model.PhoneBatteryReport
import com.sza.fastmediasorter.wear.domain.model.WearClockStyle
import com.sza.fastmediasorter.wear.domain.model.WearFaceBackdrop
import com.sza.fastmediasorter.wear.domain.repository.WearPhoneBatteryRepository
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
 * Value (min [CODE_MIN], max [CODE_MAX]) - S4562 layout, every weight divides the next one, so each lower
 * field reads as `code mod W` with no need to strip the fields above it:
 * - `code = seconds + 2 * typeface + 64 * palette + 256 * backdrop + 1024 * phoneBand`
 * - `seconds`: 0 hidden, 1 shown - `code mod 2`
 * - `typeface`: [WearClockTypeface] ordinal, [TYPEFACE_SLOTS] slots - `(code mod 64) / 2`; the face draws
 *   one time branch per `code mod 64` value
 * - `palette`: 0 DYNAMIC, 1 GREEN, 2 PINK, 3 BLUE - `(code mod 256) / 64`
 * - `backdrop` (S3707): 0 animation, 1 still, 2 none, 3 photo (S3708) ([WearFaceBackdrop]) - `(code mod 1024) / 256`
 * - `phoneBattery` (S3764): 0..100 discharging, 101..201 charging, [BAND_STALE] stale or absent - the face
 *   decodes the band as `floor(clamp(code, 0, 262143) / 1024)`. Fresh percent is `band % 101`; charging is
 *   `band in 101..201`. The face (`watchface/src/main/res/raw/watchface.xml`) ships in the same release.
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

    /** The multiplex weight the paired phone's battery band rides above the style digits (S3764, S4562). */
    const val PHONE_BAND_WEIGHT = 1024

    private const val BAND_MAX = 255

    /** Every band value with every style below it; the face clamps to one less. */
    const val CODE_MAX = PHONE_BAND_WEIGHT * (BAND_MAX + 1)

    /** Typeface slots the layout reserves; the set may grow up to it without another relayout (S4562). */
    const val TYPEFACE_SLOTS = 32

    /** The phone band value that composes an empty track on the face - stale or never received. */
    const val BAND_STALE = 255

    /** Separate full discharging from empty charging while retaining both percentage endpoints. */
    const val PHONE_CHARGING_OFFSET = 101
    private const val FULL_BATTERY_PERCENT = 100
    const val LANE_COUNT = 4
    const val COLOR_COUNT = LANE_COUNT + 2

    private const val TYPEFACE_WEIGHT = 2
    private const val PALETTE_WEIGHT = TYPEFACE_WEIGHT * TYPEFACE_SLOTS
    private const val BACKDROP_WEIGHT = 256
    private const val FULL_CIRCLE_DEG = 360f
    private const val DEFAULT_DIAL_ARGB = 0xFFFFFFFF.toInt()

    fun code(style: WearClockStyle): Int {
        val seconds = if (style.secondsVisible) 1 else 0
        return seconds + TYPEFACE_WEIGHT * style.typeface.ordinal + PALETTE_WEIGHT * style.palette.ordinal
    }

    fun code(style: WearClockStyle, backdrop: WearFaceBackdrop): Int = code(style) + BACKDROP_WEIGHT * backdrop.ordinal

    fun phoneBatteryBand(report: PhoneBatteryReport?, nowMs: Long): Int {
        if (report == null || nowMs - report.timestampMs > WearPhoneBatteryRepository.STALE_AFTER_MS) {
            return BAND_STALE
        }
        val percent = report.percent.coerceIn(0, FULL_BATTERY_PERCENT)
        return percent + if (report.isCharging) PHONE_CHARGING_OFFSET else 0
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
