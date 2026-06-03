package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Matrix
import android.graphics.PointF
import com.github.chrisbanes.photoview.PhotoView
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager.TranslatedTextBlock

/**
 * Pure coordinate/text mapping for PDF long-press word pre-selection (S0323 Phase 02).
 *
 * No Android lifecycle, no DI. Converts a PhotoView touch point into a character range inside
 * the extracted page text so the text-selection overlay can pre-select the tapped word. Mapping
 * is approximate (OCR word boxes); the user adjusts with the native selection handles.
 */
object PdfSelectionCoordinateMapper {

    /**
     * Convert a touch point in [photoView] view coordinates to bitmap-pixel coordinates.
     * Returns null when the current image matrix is not invertible.
     */
    fun viewToBitmap(photoView: PhotoView, x: Float, y: Float): PointF? {
        val inverse = Matrix()
        if (!photoView.imageMatrix.invert(inverse)) return null
        val pts = floatArrayOf(x, y)
        inverse.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    /**
     * Find the word whose OCR box contains [point] (bitmap coordinates), or the nearest word by
     * box-center distance, then locate that word's first occurrence in [fullText]. Returns the
     * character range to select, or null when no word matches or the text is not found.
     */
    fun charRangeForPoint(
        point: PointF,
        words: List<TranslatedTextBlock>,
        fullText: String
    ): IntRange? {
        if (words.isEmpty() || fullText.isEmpty()) return null
        val px = point.x.toInt()
        val py = point.y.toInt()
        val containing = words.firstOrNull { it.boundingBox.contains(px, py) }
        val target = containing ?: words.minByOrNull { w ->
            val dx = w.boundingBox.exactCenterX() - point.x
            val dy = w.boundingBox.exactCenterY() - point.y
            dx * dx + dy * dy
        }
        val word = target?.originalText?.trim().orEmpty()
        if (word.isEmpty()) return null
        val start = fullText.indexOf(word)
        if (start < 0) return null
        return start until (start + word.length)
    }
}
