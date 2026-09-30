package com.sza.fastmediasorter.ui.player.letterbox

import android.graphics.Bitmap
import android.os.Build
import com.sza.fastmediasorter.core.letterbox.LetterboxFillMath
import com.sza.fastmediasorter.core.letterbox.LetterboxFillMath.Axis
import com.sza.fastmediasorter.core.letterbox.LetterboxFillMath.BarMode
import com.sza.fastmediasorter.core.letterbox.LetterboxFillMath.ImageRect
import com.sza.fastmediasorter.core.letterbox.LetterboxFillMath.Span

/** A surface-sized LETTERBOX-BARS frame and the geometry the halo is drawn against. */
class LetterboxBarsFrame(
    val bitmap: Bitmap,
    val axis: Axis,
    val rect: ImageRect,
    val background: Int,
) {
    val surfaceWidth: Int get() = bitmap.width
    val surfaceHeight: Int get() = bitmap.height
}

/**
 * Builds the LETTERBOX-BARS frame from a decoded image with [LetterboxFillMath]. Pure pixel work -
 * call it off the main thread.
 */
object LetterboxBarsFrameBuilder {

    private const val OPAQUE = 0xFF000000.toInt()
    private const val RGB_MASK = 0x00FFFFFF

    /**
     * Rule 9: null means "no bars" and the caller shows no frame at all. [background] is the surface
     * colour `B` as an opaque or packed RGB int.
     */
    fun build(source: Bitmap, surfaceW: Int, surfaceH: Int, background: Int): LetterboxBarsFrame? {
        val axis = LetterboxFillMath.barsAxis(source.width, source.height, surfaceW, surfaceH) ?: return null
        val rect = LetterboxFillMath.imageRect(source.width, source.height, surfaceW, surfaceH)
        val displayed = displayedImage(source, rect)
        try {
            val out = Bitmap.createBitmap(surfaceW, surfaceH, Bitmap.Config.ARGB_8888)
            val b = background or OPAQUE
            when (axis) {
                Axis.PILLARBOX -> paintPillarbox(out, displayed, rect, b)
                Axis.LETTERBOX -> paintLetterbox(out, displayed, rect, b)
            }
            return LetterboxBarsFrame(out, axis, rect, b)
        } finally {
            if (displayed !== source) displayed.recycle()
        }
    }

    /**
     * Rule 3: `D`, the image resampled to the fitted size. Android's bilinear filter clamps at the
     * edges, which - like the mirror the contract names - never pulls the outer pixels towards `B`.
     */
    private fun displayedImage(source: Bitmap, rect: ImageRect): Bitmap {
        val isHardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE
        // A HARDWARE bitmap has no CPU-readable pixels; getPixels on it throws.
        val readable = if (isHardware) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }
        if (readable.width == rect.width && readable.height == rect.height) return readable
        val scaled = Bitmap.createScaledBitmap(readable, rect.width, rect.height, true)
        if (readable !== source && readable !== scaled) readable.recycle()
        return scaled
    }

    /** One band's resolved fill: a single colour, or one smoothed colour per edge-list entry. */
    private class BandFill(val uniform: Int?, val perEntry: IntArray) {
        fun at(index: Int): Int = uniform ?: perEntry[index]
    }

    /** Rules 4-6. */
    private fun resolveBand(edge: IntArray): BandFill {
        val samples = LetterboxFillMath.steppedIndices(edge.size).map { edge[it] }.toIntArray()
        return if (LetterboxFillMath.barMode(samples) == BarMode.UNIFORM) {
            BandFill(LetterboxFillMath.trimmedMeanColor(samples), edge)
        } else {
            BandFill(null, LetterboxFillMath.smoothedColors(edge, LetterboxFillMath.smoothRadius(edge.size)))
        }
    }

    private fun paintPillarbox(out: Bitmap, displayed: Bitmap, rect: ImageRect, background: Int) {
        val surfaceW = out.width
        val surfaceH = out.height
        val nearEdge = edgeList(
            readColumn(displayed, LetterboxFillMath.edgeIndex(rect.width, false)),
            rect.top,
            surfaceH
        )
        val farEdge = edgeList(readColumn(displayed, LetterboxFillMath.edgeIndex(rect.width, true)), rect.top, surfaceH)
        val nearFill = resolveBand(nearEdge)
        val farFill = resolveBand(farEdge)
        val bands = LetterboxFillMath.barBands(surfaceW, rect.left, rect.width)
        val row = IntArray(surfaceW)
        for (y in 0 until surfaceH) {
            row.fill(background)
            fillSpan(row, bands.nearBar, nearFill.at(y))
            fillSpan(row, bands.farBar, farFill.at(y))
            fillSpan(row, bands.nearSeam, nearEdge[y])
            fillSpan(row, bands.farSeam, farEdge[y])
            out.setPixels(row, 0, surfaceW, 0, y, surfaceW, 1)
        }
    }

    private fun paintLetterbox(out: Bitmap, displayed: Bitmap, rect: ImageRect, background: Int) {
        val surfaceW = out.width
        val surfaceH = out.height
        val nearEdge = edgeList(
            readRow(displayed, LetterboxFillMath.edgeIndex(rect.height, false)),
            rect.left,
            surfaceW
        )
        val farEdge = edgeList(readRow(displayed, LetterboxFillMath.edgeIndex(rect.height, true)), rect.left, surfaceW)
        val nearFill = resolveBand(nearEdge)
        val farFill = resolveBand(farEdge)
        val bands = LetterboxFillMath.barBands(surfaceH, rect.top, rect.height)
        val row = IntArray(surfaceW)
        for (y in 0 until surfaceH) {
            val source: (Int) -> Int = when {
                // Painting order is bars then seams, far over near, so the later span wins here.
                y in bands.farSeam -> { x -> farEdge[x] }
                y in bands.nearSeam -> { x -> nearEdge[x] }
                y in bands.farBar -> farFill::at
                y in bands.nearBar -> nearFill::at
                else -> { _ -> background }
            }
            for (x in 0 until surfaceW) row[x] = source(x) or OPAQUE
            out.setPixels(row, 0, surfaceW, 0, y, surfaceW, 1)
        }
    }

    /** Rule 3: surface index -> clamped displayed index, so the list has exactly [surfaceSize] entries. */
    private fun edgeList(displayedEdge: IntArray, imageOffset: Int, surfaceSize: Int): IntArray =
        IntArray(surfaceSize) { i ->
            displayedEdge[LetterboxFillMath.edgeSourceIndex(i, imageOffset, displayedEdge.size)] and RGB_MASK
        }

    private fun readColumn(bitmap: Bitmap, x: Int): IntArray {
        val column = IntArray(bitmap.height)
        bitmap.getPixels(column, 0, 1, x, 0, 1, bitmap.height)
        return column
    }

    private fun readRow(bitmap: Bitmap, y: Int): IntArray {
        val row = IntArray(bitmap.width)
        bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        return row
    }

    private fun fillSpan(row: IntArray, span: Span, color: Int) {
        if (span.length <= 0) return
        row.fill(color or OPAQUE, span.start, span.start + span.length)
    }

    private operator fun Span.contains(index: Int): Boolean = length > 0 && index >= start && index < start + length
}
