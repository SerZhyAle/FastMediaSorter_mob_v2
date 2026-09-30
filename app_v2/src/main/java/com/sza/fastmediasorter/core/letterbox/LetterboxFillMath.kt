package com.sza.fastmediasorter.core.letterbox

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The numbers of the LETTERBOX-BARS 0.1 and LETTERBOX-HALO 0.1 contracts, with no drawing API behind
 * them, so every value can be held to the catalog's vectors by a plain JVM test.
 *
 * Rounding is part of the contract: every "round" below is half to EVEN ([Math.rint]). Kotlin's
 * `Math.round` / `roundToInt` go half up and would differ on the 1x1000 image rect, the 7500-entry
 * smooth radius and the stop table.
 *
 * Colours are packed `0xRRGGBB` ints; any alpha byte is ignored on input and never produced.
 */
@Suppress("TooManyFunctions")
object LetterboxFillMath {

    /** LETTERBOX-BARS rule 1. */
    const val ASPECT_DECIMALS = 2

    /** LETTERBOX-BARS rule 3; applies when the displayed side is at least three pixels. */
    const val EDGE_INSET = 1

    /** LETTERBOX-BARS rule 5. */
    const val EDGE_OVERLAP = 2

    /** LETTERBOX-BARS rule 7. */
    const val SEAM_BAND = 2

    /** LETTERBOX-BARS rule 4. */
    const val SAMPLE_STEP = 100

    /** LETTERBOX-BARS rule 4 (sampling) and rule 5 (trimmed mean). */
    const val TRIM_PERCENT = 7

    /** LETTERBOX-BARS rule 4. */
    const val DEVIATION_PERCENT = 4

    /** LETTERBOX-BARS rule 6. */
    const val SMOOTH_FACTOR = 0.0006

    /** LETTERBOX-HALO rule 2. */
    const val FALLOFF_GAMMA = 1.4

    /** LETTERBOX-HALO rule 2. */
    const val BLEND_STOPS = 12

    /** LETTERBOX-HALO rule 5; medium is half of it, fast a quarter. */
    const val DURATION_SLOW_MS = 350.0

    /** LETTERBOX-HALO rule 6. */
    const val FRAME_INTERVAL_MS = 15L

    /** LETTERBOX-HALO rule 5: the stored speed keys. */
    const val SPEED_SLOW = "slow"
    const val SPEED_MEDIUM = "medium"
    const val SPEED_FAST = "fast"

    private const val CHANNELS = 3
    private const val CHANNEL_MAX = 255
    private const val ALPHA_MAX = 255.0
    private const val PERCENT = 100.0
    private const val STOP_POSITION_DECIMALS = 6
    private const val FAST_DIVISOR = 4.0
    private const val MEDIUM_DIVISOR = 2.0
    private const val SHIFT_RED = 16
    private const val SHIFT_GREEN = 8
    private const val BYTE_MASK = 0xFF
    private const val ERROR_SAMPLES = 10_000

    /** LETTERBOX-HALO rule 2: distances are measured to pixel centres. */
    private const val PIXEL_CENTRE = 0.5

    enum class Axis(val wireName: String) { PILLARBOX("pillarbox"), LETTERBOX("letterbox") }

    enum class BarMode(val wireName: String) { UNIFORM("uniform"), PERSPECTIVE("perspective") }

    /** Where the fitted image sits in the surface, LETTERBOX-BARS rule 2. */
    data class ImageRect(val left: Int, val top: Int, val width: Int, val height: Int)

    /** One painted span along the bar axis: `[start, start + length)`. */
    data class Span(val start: Int, val length: Int)

    /** LETTERBOX-BARS rules 5 and 7, in painting order: the two bars, then the two seam bands. */
    data class BarBands(val nearBar: Span, val farBar: Span, val nearSeam: Span, val farSeam: Span)

    fun roundHalfEven(value: Double): Double = Math.rint(value)

    private fun roundDecimals(value: Double, decimals: Int): Double {
        val scale = 10.0.pow(decimals)
        return Math.rint(value * scale) / scale
    }

    // ---------------------------------------------------------------------------------------------
    // LETTERBOX-BARS
    // ---------------------------------------------------------------------------------------------

    /** Rule 1. Null when the image needs no bars. */
    fun barsAxis(imageW: Int, imageH: Int, surfaceW: Int, surfaceH: Int): Axis? {
        if (minOf(imageW, imageH) <= 1 || minOf(surfaceW, surfaceH) <= 2) return null
        val imageAspect = imageW / imageH.toDouble()
        val surfaceAspect = surfaceW / surfaceH.toDouble()
        val same = roundDecimals(imageAspect, ASPECT_DECIMALS) == roundDecimals(surfaceAspect, ASPECT_DECIMALS)
        return when {
            same -> null
            imageAspect < surfaceAspect -> Axis.PILLARBOX
            else -> Axis.LETTERBOX
        }
    }

    /** Rule 2. */
    fun imageRect(imageW: Int, imageH: Int, surfaceW: Int, surfaceH: Int): ImageRect {
        if (minOf(imageW, imageH, surfaceW, surfaceH) <= 0) return ImageRect(0, 0, 0, 0)
        val scale = min(surfaceW / imageW.toDouble(), surfaceH / imageH.toDouble())
        val w = roundHalfEven(imageW * scale).toInt().coerceIn(1, surfaceW)
        val h = roundHalfEven(imageH * scale).toInt().coerceIn(1, surfaceH)
        return ImageRect(Math.floorDiv(surfaceW - w, 2), Math.floorDiv(surfaceH - h, 2), w, h)
    }

    /** Rule 3. Index of the displayed column (row) sampled for the near or the far edge. */
    fun edgeIndex(displayedSize: Int, farEdge: Boolean): Int {
        val inset = if (displayedSize >= EDGE_INSET + 2) EDGE_INSET else 0
        return if (farEdge) displayedSize - 1 - inset else inset
    }

    /** Rule 3. Which displayed row feeds surface row [surfaceIndex] (pillarbox; transpose for letterbox). */
    fun edgeSourceIndex(surfaceIndex: Int, imageOffset: Int, displayedSize: Int): Int =
        (surfaceIndex - imageOffset).coerceIn(0, max(0, displayedSize - 1))

    /** Rule 4. Every [SAMPLE_STEP]-th entry from index 0; the last entry alone when that yields none. */
    fun steppedIndices(count: Int): IntArray = when {
        count <= 0 -> IntArray(0)
        else -> IntArray((count + SAMPLE_STEP - 1) / SAMPLE_STEP) { it * SAMPLE_STEP }
    }

    /** Rule 4. Sum over channels of `max - min`, after trimming when there are more than 7 samples. */
    fun edgeSpread(colors: IntArray): Int {
        val n = colors.size
        if (n == 0) return 0
        var spread = 0
        for (channel in 0 until CHANNELS) {
            val values = channelValues(colors, channel)
            val trim = if (n > TRIM_PERCENT) floor(n * TRIM_PERCENT / PERCENT).toInt() else 0
            spread += values[n - 1 - trim] - values[trim]
        }
        return spread
    }

    /** Rule 4: 4 % of 3 x 255 = 30.6. */
    fun spreadThreshold(): Double = DEVIATION_PERCENT / PERCENT * CHANNEL_MAX * CHANNELS

    /** Rule 4. */
    fun barMode(sampledColors: IntArray): BarMode =
        if (edgeSpread(sampledColors) < spreadThreshold()) BarMode.UNIFORM else BarMode.PERSPECTIVE

    /** Rule 5. Per-channel trimmed mean, rounded half to even. */
    fun trimmedMeanColor(colors: IntArray): Int {
        val n = colors.size
        if (n == 0) return 0
        var packed = 0
        for (channel in 0 until CHANNELS) {
            val values = channelValues(colors, channel)
            val trim = min(floor(n * TRIM_PERCENT / PERCENT).toInt(), max(0, (n - 1) / 2))
            var sum = 0L
            for (i in trim..(n - 1 - trim)) sum += values[i]
            val mean = roundHalfEven(sum.toDouble() / (n - 2 * trim)).toInt()
            packed = packed or (mean shl channelShift(channel))
        }
        return packed
    }

    /** Rule 6. */
    fun smoothRadius(count: Int): Int = roundHalfEven(count * SMOOTH_FACTOR).toInt() + 1

    /** Rule 6. Box filter clipped at the ends, per-channel floor mean; lists shorter than 3 pass through. */
    fun smoothedColors(colors: IntArray, radius: Int): IntArray {
        val n = colors.size
        if (radius < 1 || n < CHANNELS) return colors.copyOf()
        val out = IntArray(n)
        for (i in 0 until n) {
            val from = max(0, i - radius)
            val to = min(n - 1, i + radius)
            var r = 0
            var g = 0
            var b = 0
            for (j in from..to) {
                r += red(colors[j])
                g += green(colors[j])
                b += blue(colors[j])
            }
            val k = to - from + 1
            out[i] = rgb(r / k, g / k, b / k)
        }
        return out
    }

    /** Rules 5 and 7, pillarbox coordinates (x); transpose for letterbox. */
    fun barBands(surfaceSize: Int, imageStart: Int, imageLength: Int): BarBands {
        val imageEnd = imageStart + imageLength
        val farStart = max(0, imageEnd - EDGE_OVERLAP)
        val nearSeam = min(SEAM_BAND, imageStart)
        val farSeam = min(SEAM_BAND, surfaceSize - imageEnd)
        return BarBands(
            nearBar = Span(0, min(surfaceSize, imageStart + EDGE_OVERLAP)),
            farBar = Span(farStart, surfaceSize - farStart),
            nearSeam = Span(imageStart - nearSeam, nearSeam),
            farSeam = Span(imageEnd, farSeam),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // LETTERBOX-HALO
    // ---------------------------------------------------------------------------------------------

    /** Rule 5. Missing, empty or unknown keys read as medium. */
    fun haloDurationMs(speed: String?): Double = when (speed.orEmpty().trim().lowercase()) {
        SPEED_SLOW -> DURATION_SLOW_MS
        SPEED_FAST -> DURATION_SLOW_MS / FAST_DIVISOR
        else -> DURATION_SLOW_MS / MEDIUM_DIVISOR
    }

    /** Rule 5: the key a stored value normalises to. */
    fun normalizeSpeed(speed: String?): String = when (speed.orEmpty().trim().lowercase()) {
        SPEED_SLOW -> SPEED_SLOW
        SPEED_FAST -> SPEED_FAST
        else -> SPEED_MEDIUM
    }

    /** Rule 4. Quadratic ease-out, input clamped to `[0, 1]`. */
    fun haloEase(progress: Double): Double {
        val p = progress.coerceIn(0.0, 1.0)
        return 1.0 - (1.0 - p) * (1.0 - p)
    }

    /** Rule 3. Pixels of a bar of length [barLength] the ramp covers at eased growth [eased]. */
    fun haloReach(barLength: Int, eased: Double): Int = floor(barLength * eased.coerceIn(0.0, 1.0)).toInt()

    /**
     * Rule 2. Stop positions, rounded to six decimals exactly as the catalog's stop table publishes them,
     * so interpolation between them reproduces the vectors' alpha rows to the fourth decimal.
     */
    val haloStopPositions: DoubleArray = DoubleArray(BLEND_STOPS) { j ->
        roundDecimals(j / (BLEND_STOPS - 1.0), STOP_POSITION_DECIMALS)
    }

    /** Rule 2. Background alpha at each stop: `round(255 * (j/11)^1.4)`, half to even. */
    val haloStopAlphas: IntArray = IntArray(BLEND_STOPS) { j ->
        val d = j / (BLEND_STOPS - 1.0)
        roundHalfEven(ALPHA_MAX * d.pow(FALLOFF_GAMMA)).toInt().coerceIn(0, CHANNEL_MAX)
    }

    /** Rule 2. Alpha at normalized distance [distance]: straight line between the stops. */
    fun haloAlphaAt(distance: Double): Double {
        val d = distance.coerceIn(0.0, 1.0)
        val segment = min(BLEND_STOPS - 2, floor(d * (BLEND_STOPS - 1)).toInt())
        val d0 = haloStopPositions[segment]
        val d1 = haloStopPositions[segment + 1]
        val a0 = haloStopAlphas[segment].toDouble()
        val a1 = haloStopAlphas[segment + 1].toDouble()
        return a0 + (a1 - a0) * ((d - d0) / (d1 - d0))
    }

    /** Rules 2-3. Background alpha of every pixel of one bar, k = 0 touching the photo. */
    fun haloAlphaRow(barLength: Int, eased: Double): DoubleArray {
        val reach = haloReach(barLength, eased)
        return DoubleArray(max(0, barLength)) { k ->
            if (k >= reach) ALPHA_MAX else haloAlphaAt((k + PIXEL_CENTRE) / reach)
        }
    }

    /** Rule 7. Opacity of the previous frame laid over the surface. */
    fun underlayOpacity(eased: Double): Double = (1.0 - eased).coerceIn(0.0, 1.0)

    /** Rule 6. Progress an ideal timer produces: 0, then one per 15 ms, the last one exactly 1. */
    fun frameSchedule(durationMs: Double): DoubleArray {
        val out = ArrayList<Double>()
        out.add(0.0)
        var t = 0L
        while (true) {
            t += FRAME_INTERVAL_MS
            val p = t / durationMs
            if (p >= 1.0) {
                out.add(1.0)
                break
            }
            out.add(p)
        }
        return out.toDoubleArray()
    }

    /** Rule 6. Progress for real elapsed time: never the frame count. */
    fun progressAt(elapsedMs: Double, durationMs: Double): Double =
        if (durationMs <= 0.0) 1.0 else (elapsedMs / durationMs).coerceIn(0.0, 1.0)

    /** Rule 2 (informational). Largest gap between the stop table and the exact curve. */
    fun stopApproximationMaxError(samples: Int = ERROR_SAMPLES): Double {
        var worst = 0.0
        for (i in 0..samples) {
            val d = i / samples.toDouble()
            val diff = kotlin.math.abs(haloAlphaAt(d) - ALPHA_MAX * d.pow(FALLOFF_GAMMA))
            if (diff > worst) worst = diff
        }
        return worst
    }

    // ---------------------------------------------------------------------------------------------
    // Colour helpers
    // ---------------------------------------------------------------------------------------------

    fun rgb(r: Int, g: Int, b: Int): Int = (r shl SHIFT_RED) or (g shl SHIFT_GREEN) or b

    fun red(color: Int): Int = (color shr SHIFT_RED) and BYTE_MASK

    fun green(color: Int): Int = (color shr SHIFT_GREEN) and BYTE_MASK

    fun blue(color: Int): Int = color and BYTE_MASK

    private fun channelShift(channel: Int): Int = when (channel) {
        0 -> SHIFT_RED
        1 -> SHIFT_GREEN
        else -> 0
    }

    private fun channelValues(colors: IntArray, channel: Int): IntArray {
        val shift = channelShift(channel)
        val values = IntArray(colors.size) { (colors[it] shr shift) and BYTE_MASK }
        values.sort()
        return values
    }
}
