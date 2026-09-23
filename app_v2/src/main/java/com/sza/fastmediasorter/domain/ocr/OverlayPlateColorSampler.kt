package com.sza.fastmediasorter.domain.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * S1714: pure domain colour sampling for OCR translation overlay plates.
 *
 * Replaces the single top-left pixel and the black-or-white-by-luma text colour with:
 * 1. Paper colour - median over a decimated sample of the plate box.
 * 2. Ink colour - median over the sampled pixels that deviate from paper.
 * 3. A ring outside the box that votes on which of the two colours is paper.
 * 4. A contrast floor that replaces sampled ink when the pair would be unreadable.
 *
 * Provenance of every threshold is stated on the constant itself (strategic spec goal 5).
 * Three of them are INHERITED from the neighbouring project's measurements, one is MEASURED
 * on this project's corpus, one is an external standard, and the rest are CHOSEN HERE and
 * not derived from anything - which is why they say so instead of citing a report.
 */
object OverlayPlateColorSampler {

    /**
     * Ceiling on how many plate pixels one call may read.
     *
     * CHOSEN HERE, NOT DERIVED. `ocr-overlay-accuracy.md` section 8 states a pixel ceiling
     * is ours to derive and does not supply one, so citing that document for this value would be
     * citing it for the opposite of what it says. It exists to satisfy the owner's constraint that
     * sampling cost not be proportional to plate area (strategic spec section 3.1). `Carrier: S1717`.
     */
    const val SAMPLE_BUDGET_CEILING: Int = 2000

    /**
     * Ceiling on how many ring pixels the orientation vote may read.
     *
     * CHOSEN HERE, NOT DERIVED, for the same constraint and by the same reasoning as
     * [SAMPLE_BUDGET_CEILING]: the ring grows with the plate's perimeter, so an uncapped ring
     * reintroduces the area-proportional cost the interior ceiling was added to remove.
     * `Carrier: S1717`.
     */
    const val BORDER_SAMPLE_BUDGET_CEILING: Int = 2000

    /**
     * Euclidean RGB distance (0..441.67) at which a sampled pixel stops counting as paper
     * and becomes an ink candidate.
     *
     * CHOSEN HERE, NOT DERIVED. The exchange argues the SHAPE - `ocr-overlay-accuracy.md`
     * section 5, row "Ink colour is a median, never a mean", measured rgb(61,61,61) for the mean
     * against rgb(7,7,7) for the median on source rgb(17,17,17) over rgb(253,253,253) - but it
     * supplies no separation distance. `Carrier: S1717`.
     */
    const val INK_COLOR_DISTANCE_THRESHOLD: Double = 35.0

    /**
     * Share of sampled pixels that must read as ink before the ink median means anything.
     * Below it the pair is built by the fallback path instead.
     *
     * CHOSEN HERE, NOT DERIVED. `Carrier: S1717`.
     */
    const val MIN_INK_FRACTION: Double = 0.05

    /**
     * Thinnest ring, in pixels, the orientation vote will use on a short line.
     *
     * INHERITED. `ocr-overlay-accuracy.md` section 5, row "Paper/ink orientation decided by
     * a ring outside the block, 1/3 line height per side, floor 2 px, >= 40 votes" - recorded there
     * as "not applicable yet", because we did not form an ink/paper pair until this ticket.
     */
    const val MIN_BORDER_BAND_PX: Int = 2

    /**
     * Thickest ring, in pixels, regardless of line height.
     *
     * MEASURED on this project's corpus, and the only threshold here that is:
     * `ocr-overlay-accuracy.md` section 13.2 measured a 16 px free vertical band between lines
     * on the `uniform-multiline-text` scene, and states that a ring taller than that reads the
     * neighbouring line's ink instead of paper - which is the failure this ticket was opened for.
     * One scene, so it is a bound with a source, not a derivation. `Carrier: S1716`.
     */
    const val MAX_BORDER_BAND_PX: Int = 16

    /**
     * Fewest ring pixels that must vote before the vote is allowed to flip the pair.
     *
     * INHERITED, same row of section 5 as [MIN_BORDER_BAND_PX].
     */
    const val MIN_ORIENTATION_VOTES: Int = 40

    /**
     * Lowest paper-to-ink contrast ratio (1.0..21.0) the sampled pair may ship with.
     *
     * EXTERNAL STANDARD, not from the exchange: WCAG 2.1 success criterion 1.4.3 sets 3:1 as the
     * minimum for large-scale text, and overlay plate text is sized to the line it covers.
     */
    const val CONTRAST_FLOOR: Double = 3.0

    /** INHERITED, same row of section 5: the ring is 1/3 of the line height per side. */
    private const val BORDER_BAND_LINE_HEIGHT_DIVISOR = 3

    /** A pixel this transparent cannot report a colour, so it is not sampled (strategic spec 3.1). */
    private const val OPAQUE_ALPHA_THRESHOLD = 128

    private const val CHANNEL_MAX = 255.0
    private const val PERCEIVED_LUMA_RED = 0.299
    private const val PERCEIVED_LUMA_GREEN = 0.587
    private const val PERCEIVED_LUMA_BLUE = 0.114
    private const val PERCEIVED_LUMA_MIDPOINT = 128.0
    private const val WCAG_LUMINANCE_RED = 0.2126
    private const val WCAG_LUMINANCE_GREEN = 0.7152
    private const val WCAG_LUMINANCE_BLUE = 0.0722
    private const val WCAG_CONTRAST_OFFSET = 0.05
    private const val SRGB_LINEAR_CUTOFF = 0.04045
    private const val SRGB_LINEAR_DIVISOR = 12.92
    private const val SRGB_GAMMA_OFFSET = 0.055
    private const val SRGB_GAMMA_DIVISOR = 1.055
    private const val SRGB_GAMMA_EXPONENT = 2.4

    /** Result of sampling colours for one OCR plate. */
    data class PlateColorResult(
        val paperColor: Int,
        val inkColor: Int,
        val isFallbackPair: Boolean
    )

    private data class ColorPair(val paper: Int, val ink: Int)

    private data class BorderVotes(val paper: Int, val ink: Int) {
        val total: Int get() = paper + ink
    }

    /** How one ring pixel testifies. [NONE] is an abstention, never a vote for paper. */
    private enum class Vote { PAPER, INK, NONE }

    /**
     * Samples the paper and ink colours of [plateRect] out of [sourceBitmap].
     *
     * [plateRect] must already be in [sourceBitmap] coordinate space - mapping OCR coordinates
     * into it is the caller's job (S1704). The rect is clamped, so a box that runs off the image
     * yields the colours of the part that is on it rather than an exception.
     */
    fun samplePlateColors(sourceBitmap: Bitmap, plateRect: Rect): PlateColorResult {
        val rect = clampToBitmap(plateRect, sourceBitmap)
        val samples = if (rect == null) emptyList() else collectSamples(sourceBitmap, rect)
        return if (rect == null || samples.isEmpty()) {
            PlateColorResult(Color.WHITE, Color.BLACK, isFallbackPair = true)
        } else {
            resolvePair(sourceBitmap, rect, samples)
        }
    }

    private fun resolvePair(bitmap: Bitmap, rect: Rect, samples: List<Int>): PlateColorResult {
        val paperColor = medianColor(samples)
        val inkCandidates = samples.filter {
            colorDistance(paperColor, it) >= INK_COLOR_DISTANCE_THRESHOLD
        }
        val inkFraction = inkCandidates.size.toDouble() / samples.size.toDouble()

        // Too little ink for its median to mean anything - the pair comes from the fallback path.
        val sampledPair = if (inkFraction < MIN_INK_FRACTION) {
            null
        } else {
            orientPair(bitmap, rect, paperColor, medianColor(inkCandidates))
        }

        val pair = sampledPair ?: ColorPair(paperColor, getFallbackContrastColor(paperColor))
        return applyContrastFloor(pair, sampledPair == null)
    }

    /**
     * Decides which of the two sampled colours is paper by voting with the ring OUTSIDE the block.
     *
     * Deliberately not "which colour covers more of the box": section 5's measurement is that a
     * large capital fills its own tight box more than the paper between its letters does, so the
     * inside ratio answers wrongly exactly on big text, where the error shows most.
     */
    private fun orientPair(bitmap: Bitmap, rect: Rect, paperColor: Int, inkColor: Int): ColorPair {
        val bandThickness = max(
            MIN_BORDER_BAND_PX,
            min(MAX_BORDER_BAND_PX, rect.height() / BORDER_BAND_LINE_HEIGHT_DIVISOR)
        )
        val candidate = ColorPair(paperColor, inkColor)
        val votes = voteOuterBorder(bitmap, rect, bandThickness, candidate)
        val flipped = votes.total >= MIN_ORIENTATION_VOTES && votes.ink > votes.paper
        return if (flipped) ColorPair(candidate.ink, candidate.paper) else candidate
    }

    private fun applyContrastFloor(pair: ColorPair, cameFromFallback: Boolean): PlateColorResult {
        val readable = calculateContrastRatio(pair.paper, pair.ink) >= CONTRAST_FLOOR
        return PlateColorResult(
            paperColor = pair.paper,
            inkColor = if (readable) pair.ink else getFallbackContrastColor(pair.paper),
            isFallbackPair = cameFromFallback || !readable
        )
    }

    private fun clampToBitmap(rect: Rect, bitmap: Bitmap): Rect? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        return Rect(
            left,
            top,
            rect.right.coerceIn(left + 1, bitmap.width),
            rect.bottom.coerceIn(top + 1, bitmap.height)
        )
    }

    private fun collectSamples(bitmap: Bitmap, rect: Rect): List<Int> {
        val step = decimationStep(rect.width() * rect.height(), SAMPLE_BUDGET_CEILING)
        val samples = ArrayList<Int>()
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) >= OPAQUE_ALPHA_THRESHOLD) {
                    samples.add(opaque(pixel))
                }
                x += step
            }
            y += step
        }
        return samples
    }

    private fun voteOuterBorder(
        sourceBitmap: Bitmap,
        plateRect: Rect,
        bandThickness: Int,
        candidate: ColorPair
    ): BorderVotes {
        val outerLeft = max(0, plateRect.left - bandThickness)
        val outerTop = max(0, plateRect.top - bandThickness)
        val outerRight = min(sourceBitmap.width, plateRect.right + bandThickness)
        val outerBottom = min(sourceBitmap.height, plateRect.bottom + bandThickness)

        val ringArea = (outerRight - outerLeft) * (outerBottom - outerTop) -
            plateRect.width() * plateRect.height()
        val step = decimationStep(ringArea, BORDER_SAMPLE_BUDGET_CEILING)

        var votesPaper = 0
        var votesInk = 0
        var y = outerTop
        while (y < outerBottom) {
            var x = outerLeft
            while (x < outerRight) {
                when (ringVote(sourceBitmap, plateRect, x, y, candidate)) {
                    Vote.PAPER -> votesPaper++
                    Vote.INK -> votesInk++
                    Vote.NONE -> Unit
                }
                x += step
            }
            y += step
        }
        return BorderVotes(votesPaper, votesInk)
    }

    /**
     * The vote of the pixel at ([x], [y]), which the traversal reaches by walking the whole outer
     * box rather than the ring alone.
     *
     * A pixel inside the plate is not ring at all, and one too transparent to carry a colour cannot
     * testify - both abstain, because counting either as paper would let a plate sitting on
     * transparency out-vote the material actually around it.
     */
    private fun ringVote(
        bitmap: Bitmap,
        plateRect: Rect,
        x: Int,
        y: Int,
        candidate: ColorPair
    ): Vote {
        if (plateRect.contains(x, y)) return Vote.NONE
        val pixel = bitmap.getPixel(x, y)
        val toPaper = colorDistance(candidate.paper, pixel)
        val toInk = colorDistance(candidate.ink, pixel)
        return when {
            Color.alpha(pixel) < OPAQUE_ALPHA_THRESHOLD -> Vote.NONE
            toPaper < toInk -> Vote.PAPER
            toInk < toPaper -> Vote.INK
            else -> Vote.NONE
        }
    }

    /**
     * Stride that keeps a [pixelCount]-pixel region inside [budget] samples.
     *
     * Rounded UP on purpose: truncating leaves a region of 2.5x the budget sampled at stride 1,
     * which overshoots the ceiling the constant's name promises by up to four times.
     */
    internal fun decimationStep(pixelCount: Int, budget: Int): Int {
        if (pixelCount <= budget) return 1
        return max(1, ceil(sqrt(pixelCount.toDouble() / budget)).toInt())
    }

    private fun opaque(color: Int): Int =
        Color.rgb(Color.red(color), Color.green(color), Color.blue(color))

    private fun medianColor(colors: List<Int>): Int = Color.rgb(
        median(colors.map { Color.red(it) }),
        median(colors.map { Color.green(it) }),
        median(colors.map { Color.blue(it) })
    )

    /**
     * Median of an integer list, taking the lower of the two middle values on an even count.
     *
     * Matches [OcrLineGeometry]'s median so the two do not disagree on the same input.
     */
    internal fun median(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }

    /** Euclidean distance between two packed colours in RGB space (0..441.67). */
    internal fun colorDistance(color1: Int, color2: Int): Double {
        val dr = (Color.red(color1) - Color.red(color2)).toDouble()
        val dg = (Color.green(color1) - Color.green(color2)).toDouble()
        val db = (Color.blue(color1) - Color.blue(color2)).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    /** WCAG 2.1 relative-luminance contrast ratio between two colours (1.0..21.0). */
    internal fun calculateContrastRatio(color1: Int, color2: Int): Double {
        val lum1 = relativeLuminance(color1)
        val lum2 = relativeLuminance(color2)
        return (max(lum1, lum2) + WCAG_CONTRAST_OFFSET) / (min(lum1, lum2) + WCAG_CONTRAST_OFFSET)
    }

    private fun relativeLuminance(color: Int): Double =
        WCAG_LUMINANCE_RED * sRgbToLinear(Color.red(color) / CHANNEL_MAX) +
            WCAG_LUMINANCE_GREEN * sRgbToLinear(Color.green(color) / CHANNEL_MAX) +
            WCAG_LUMINANCE_BLUE * sRgbToLinear(Color.blue(color) / CHANNEL_MAX)

    private fun sRgbToLinear(value: Double): Double = if (value <= SRGB_LINEAR_CUTOFF) {
        value / SRGB_LINEAR_DIVISOR
    } else {
        ((value + SRGB_GAMMA_OFFSET) / SRGB_GAMMA_DIVISOR).pow(SRGB_GAMMA_EXPONENT)
    }

    /**
     * Near-black or near-white ink for [backgroundColor], used when sampling found too little ink
     * or when the sampled pair fell below [CONTRAST_FLOOR].
     */
    internal fun getFallbackContrastColor(backgroundColor: Int): Int {
        val luma = PERCEIVED_LUMA_RED * Color.red(backgroundColor) +
            PERCEIVED_LUMA_GREEN * Color.green(backgroundColor) +
            PERCEIVED_LUMA_BLUE * Color.blue(backgroundColor)
        return if (luma < PERCEIVED_LUMA_MIDPOINT) Color.WHITE else Color.BLACK
    }
}
