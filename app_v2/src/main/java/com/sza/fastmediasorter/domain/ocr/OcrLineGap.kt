package com.sza.fastmediasorter.domain.ocr

import android.graphics.Rect
import kotlin.math.max

/**
 * The widest gap between two consecutive words of one recognised line, weighed against the line's type size.
 *
 * This is a measurement and deliberately carries no threshold: the rule that cuts a stitched line at a wide
 * gap may not enter code before a dated report brackets its constant on our own material
 * (`ocr-overlay-accuracy.md` section 15.4). The distribution this produces on a device is that report's input.
 */
object OcrLineGap {

    /** The three numbers the bracket is built from; [ratio] is [maxGapPx] over [medianWordHeightPx]. */
    data class Measurement(val maxGapPx: Int, val medianWordHeightPx: Int, val ratio: Float)

    /**
     * Horizontal distance between two word boxes, in whichever reading direction they are ordered.
     *
     * Measured between the boxes on both sides so a right-to-left line does not yield a negative gap on every
     * pair; overlapping boxes still come out negative, which is never a gap.
     */
    fun gapPx(prev: Rect, next: Rect): Int = max(next.left - prev.right, prev.left - next.right)

    /**
     * Measurement of [block], or null when the line has fewer than two words or no usable type size.
     *
     * The weight is the median word height from [OcrLineGeometry.typeSizePx], not the line box: the box is the
     * union of its words and one tall artifact sets it whole.
     */
    fun measure(block: OcrTextBlock): Measurement? {
        val words = block.words
        val median = OcrLineGeometry.typeSizePx(block)
        if (words == null || words.size < 2 || median <= 0) {
            return null
        }
        val maxGap = words.zipWithNext { prev, next -> gapPx(prev.boundingBox, next.boundingBox) }.max()
        return Measurement(maxGap, median, maxGap.toFloat() / median)
    }
}
