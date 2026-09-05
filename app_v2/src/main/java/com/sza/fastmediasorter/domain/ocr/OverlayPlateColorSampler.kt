package com.sza.fastmediasorter.domain.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * S1714: pure domain color sampling for OCR translation overlay plates.
 *
 * Replaces single top-left pixel sampling and naive luma-128 threshold with:
 * 1. Median paper (background) color sampled over the plate bounding box.
 * 2. Median ink (text) color sampled from pixels deviating from paper color within the block.
 * 3. Outer border strip voting around the bounding box to determine paper vs ink orientation.
 * 4. Contrast floor fallback to ensure readability.
 *
 * All constants are marked as inherited from neighbouring project measurements
 * (docs/OCR_OVERLAY_ACCURACY.md §5 / S1714 Strategic Spec §5.1).
 */
object OverlayPlateColorSampler {

    /**
     * Maximum number of pixels to sample per plate to keep performance within budget.
     * Inherited from docs/OCR_OVERLAY_ACCURACY.md §5.1.
     */
    const val SAMPLE_BUDGET_CEILING: Int = 2000

    /**
     * Minimum Euclidean color distance in RGB space (0..441.67) from paper color
     * to consider a pixel as candidate ink.
     * Inherited from docs/OCR_OVERLAY_ACCURACY.md §5.1.
     */
    const val INK_COLOR_DISTANCE_THRESHOLD: Double = 35.0

    /**
     * Minimum fraction of sampled pixels that must deviate from paper color
     * to consider ink successfully detected. If lower, fallback ink is used.
     * Inherited from docs/OCR_OVERLAY_ACCURACY.md §5.1.
     */
    const val MIN_INK_FRACTION: Double = 0.05

    /**
     * Minimum thickness of the outer border strip in pixels.
     * Inherited from docs/OCR_OVERLAY_ACCURACY.md §5.
     */
    const val MIN_BORDER_BAND_PX: Int = 2

    /**
     * Maximum thickness of the outer border strip in pixels (to avoid sampling neighbouring lines).
     * Measured in S1716 §13.2 / docs/OCR_OVERLAY_ACCURACY.md §13.2.
     */
    const val MAX_BORDER_BAND_PX: Int = 16

    /**
     * Minimum number of valid outer border votes required to consider orientation voting decisive.
     * Inherited from docs/OCR_OVERLAY_ACCURACY.md §5.
     */
    const val MIN_ORIENTATION_VOTES: Int = 40

    /**
     * Minimum acceptable contrast ratio (1.0..21.0) between paper and ink.
     * Inherited from WCAG 2.0 AA large text / docs/OCR_OVERLAY_ACCURACY.md §5.
     */
    const val CONTRAST_FLOOR: Double = 3.0

    /**
     * Result of sampling colors for an OCR plate.
     */
    data class PlateColorResult(
        val paperColor: Int,
        val inkColor: Int,
        val isFallbackPair: Boolean
    )

    /**
     * Samples paper and ink colors from [sourceBitmap] for the given [plateRect].
     *
     * Coordinates of [plateRect] must be in [sourceBitmap] coordinate space.
     */
    fun samplePlateColors(sourceBitmap: Bitmap, plateRect: Rect): PlateColorResult {
        // Clamp rect to bitmap bounds
        val left = plateRect.left.coerceIn(0, sourceBitmap.width - 1)
        val top = plateRect.top.coerceIn(0, sourceBitmap.height - 1)
        val right = plateRect.right.coerceIn(left + 1, sourceBitmap.width)
        val bottom = plateRect.bottom.coerceIn(top + 1, sourceBitmap.height)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            return PlateColorResult(
                paperColor = Color.WHITE,
                inkColor = Color.BLACK,
                isFallbackPair = true
            )
        }

        // 1. Proportional step to enforce sample budget ceiling
        val totalPixels = width * height
        val step = max(1, sqrt(totalPixels.toDouble() / SAMPLE_BUDGET_CEILING).toInt())

        val sampledReds = ArrayList<Int>()
        val sampledGreens = ArrayList<Int>()
        val sampledBlues = ArrayList<Int>()
        val allSampledColors = ArrayList<Int>()

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = sourceBitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                // Filter out fully transparent / near-transparent pixels
                if (alpha >= 128) {
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    sampledReds.add(r)
                    sampledGreens.add(g)
                    sampledBlues.add(b)
                    allSampledColors.add(Color.rgb(r, g, b))
                }
                x += step
            }
            y += step
        }

        if (allSampledColors.isEmpty()) {
            return PlateColorResult(
                paperColor = Color.WHITE,
                inkColor = Color.BLACK,
                isFallbackPair = true
            )
        }

        // 2. Paper color is median across all sampled pixels
        val medianPaperR = median(sampledReds)
        val medianPaperG = median(sampledGreens)
        val medianPaperB = median(sampledBlues)
        var paperColor = Color.rgb(medianPaperR, medianPaperG, medianPaperB)

        // 3. Ink color: sample pixels deviating from paper color
        val inkReds = ArrayList<Int>()
        val inkGreens = ArrayList<Int>()
        val inkBlues = ArrayList<Int>()

        for (color in allSampledColors) {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val dist = colorDistance(medianPaperR, medianPaperG, medianPaperB, r, g, b)
            if (dist >= INK_COLOR_DISTANCE_THRESHOLD) {
                inkReds.add(r)
                inkGreens.add(g)
                inkBlues.add(b)
            }
        }

        val inkFraction = inkReds.size.toDouble() / allSampledColors.size.toDouble()
        var inkColor: Int
        var isFallback = false

        if (inkFraction < MIN_INK_FRACTION || inkReds.isEmpty()) {
            // Not enough ink pixels found -> fallback based on paper luma
            inkColor = getFallbackContrastColor(paperColor)
            isFallback = true
        } else {
            // Median of candidate ink pixels
            val medianInkR = median(inkReds)
            val medianInkG = median(inkGreens)
            val medianInkB = median(inkBlues)
            inkColor = Color.rgb(medianInkR, medianInkG, medianInkB)

            // 4. Outer border strip voting for orientation (is paper actually ink?)
            val bandThickness = max(
                MIN_BORDER_BAND_PX,
                min(MAX_BORDER_BAND_PX, height / 3)
            )

            val (votesPaper, votesInk) = voteOuterBorder(
                sourceBitmap = sourceBitmap,
                plateRect = Rect(left, top, right, bottom),
                bandThickness = bandThickness,
                paperColor = paperColor,
                inkColor = inkColor
            )

            val totalVotes = votesPaper + votesInk
            if (totalVotes >= MIN_ORIENTATION_VOTES && votesInk > votesPaper) {
                // Border strip is closer to ink color than paper color -> swap orientation
                val temp = paperColor
                paperColor = inkColor
                inkColor = temp
            }
        }

        // 5. Contrast floor check
        val contrast = calculateContrastRatio(paperColor, inkColor)
        if (contrast < CONTRAST_FLOOR) {
            inkColor = getFallbackContrastColor(paperColor)
            isFallback = true
        }

        return PlateColorResult(
            paperColor = paperColor,
            inkColor = inkColor,
            isFallbackPair = isFallback
        )
    }

    private fun voteOuterBorder(
        sourceBitmap: Bitmap,
        plateRect: Rect,
        bandThickness: Int,
        paperColor: Int,
        inkColor: Int
    ): Pair<Int, Int> {
        var votesPaper = 0
        var votesInk = 0

        val pR = Color.red(paperColor)
        val pG = Color.green(paperColor)
        val pB = Color.blue(paperColor)

        val iR = Color.red(inkColor)
        val iG = Color.green(inkColor)
        val iB = Color.blue(inkColor)

        val outerLeft = max(0, plateRect.left - bandThickness)
        val outerTop = max(0, plateRect.top - bandThickness)
        val outerRight = min(sourceBitmap.width, plateRect.right + bandThickness)
        val outerBottom = min(sourceBitmap.height, plateRect.bottom + bandThickness)

        // Sample outer strip (points outside plateRect but within outerRect)
        for (y in outerTop until outerBottom) {
            for (x in outerLeft until outerRight) {
                if (x >= plateRect.left && x < plateRect.right &&
                    y >= plateRect.top && y < plateRect.bottom
                ) {
                    continue // Skip interior
                }

                val pixel = sourceBitmap.getPixel(x, y)
                if (Color.alpha(pixel) < 128) continue

                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val distPaper = colorDistance(pR, pG, pB, r, g, b)
                val distInk = colorDistance(iR, iG, iB, r, g, b)

                if (distPaper < distInk) {
                    votesPaper++
                } else if (distInk < distPaper) {
                    votesInk++
                }
            }
        }

        return votesPaper to votesInk
    }

    /**
     * Median of integer list. For even count, takes the lower of the two middle values.
     */
    internal fun median(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }

    /**
     * Euclidean distance in RGB color space.
     */
    internal fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        val dr = (r1 - r2).toDouble()
        val dg = (g1 - g2).toDouble()
        val db = (b1 - b2).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    /**
     * Calculates WCAG relative luminance contrast ratio between two colors (1.0 to 21.0).
     */
    internal fun calculateContrastRatio(color1: Int, color2: Int): Double {
        val lum1 = calculateRelativeLuminance(color1)
        val lum2 = calculateRelativeLuminance(color2)
        val lighter = max(lum1, lum2)
        val darker = min(lum1, lum2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Calculates WCAG 2.0 relative luminance for an sRGB color.
     */
    private fun calculateRelativeLuminance(color: Int): Double {
        val r = sRgbToLinear(Color.red(color) / 255.0)
        val g = sRgbToLinear(Color.green(color) / 255.0)
        val b = sRgbToLinear(Color.blue(color) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun sRgbToLinear(value: Double): Double {
        return if (value <= 0.04045) {
            value / 12.92
        } else {
            Math.pow((value + 0.055) / 1.055, 2.4)
        }
    }

    /**
     * Returns high contrast fallback ink color (black or white) based on background perceived luminance.
     */
    internal fun getFallbackContrastColor(backgroundColor: Int): Int {
        val r = Color.red(backgroundColor)
        val g = Color.green(backgroundColor)
        val b = Color.blue(backgroundColor)
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        return if (luminance < 128.0) Color.WHITE else Color.BLACK
    }
}
