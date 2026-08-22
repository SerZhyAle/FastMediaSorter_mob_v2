package com.sza.fastmediasorter.ui.cameracapture.helpers

/**
 * S1920: the one place that says how much of the camera stream survives, so the viewfinder and the
 * saved file stop answering that question with two different formulas.
 *
 * The WYSIWYG contract the capture pipeline declares is only kept while both sides crop by the same
 * rule. They did not: the preview was scaled about the whole view box while the file was cropped by
 * `1/factor` on both axes, and those agree only when the stream fills the view. Under `FIT_CENTER`
 * it does not, and the two diverge by a constant equal to the view height over the content height.
 *
 * Pure arithmetic with no Android dependency, so the rule is provable on the JVM without a device.
 */
internal object CaptureCropGeometry {

    /** A rectangle in pixels, so this file needs no `android.graphics` import to state a crop. */
    data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * Height a `FIT_CENTER` stream of [streamLongOverShort] occupies inside a view of
     * [viewWidth] x [viewHeight]. A degenerate view answers with the view height, which the callers
     * read as "no letterbox" and therefore change nothing.
     */
    fun contentHeightIn(viewWidth: Int, viewHeight: Int, streamLongOverShort: Float): Int {
        if (viewWidth <= 0 || viewHeight <= 0 || streamLongOverShort <= 0f) return viewHeight
        // The stream is described by its long over short edge; inside a portrait view its short edge
        // is the width, so the drawn height is the width times that ratio - capped by the view, which
        // is the landscape-host case where the letterbox falls on the sides instead.
        val drawn = (viewWidth * streamLongOverShort).toInt()
        return if (drawn <= viewHeight) drawn else viewHeight
    }

    /**
     * Fraction of the stream content still visible after the preview is scaled by [zoomFactor] and
     * clipped to the view box, as horizontal to vertical.
     *
     * Horizontal content already spans the view, so it keeps `1/zoomFactor`. Vertical content is
     * letterboxed, so the clip bites later: it keeps `viewHeight / (zoomFactor * contentHeight)`,
     * capped at the whole content. The file crop uses `1/zoomFactor` on both axes, which is why the
     * two disagree by exactly `viewHeight / contentHeight` once the factor passes 1.
     */
    fun visibleKeepFractions(
        viewWidth: Int,
        viewHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
        zoomFactor: Float,
    ): Pair<Float, Float> {
        val degenerate = viewWidth <= 0 || viewHeight <= 0 || contentWidth <= 0 || contentHeight <= 0
        // Below a factor of 1 nothing is cropped on either side, so the whole content is kept and the
        // two rules cannot disagree - the divergence this object exists for starts strictly above 1.
        if (degenerate || zoomFactor <= 1f) return 1f to 1f
        val keepX = (viewWidth / (zoomFactor * contentWidth)).coerceIn(0f, 1f)
        val keepY = (viewHeight / (zoomFactor * contentHeight)).coerceIn(0f, 1f)
        return keepX to keepY
    }

    /**
     * Centre-crop of [width] x [height] keeping [keepX] of the width and [keepY] of the height. A
     * non-positive size or fraction returns the untouched frame, because a crop that cannot be
     * computed must keep the photo rather than lose it.
     */
    fun centreCropRect(width: Int, height: Int, keepX: Float, keepY: Float): CropRect {
        val sized = width > 0 && height > 0
        val wanted = keepX > 0f && keepY > 0f
        if (!sized || !wanted) return CropRect(0, 0, width, height)
        return centred(
            width,
            height,
            (width * keepX.coerceAtMost(1f)).toInt(),
            (height * keepY.coerceAtMost(1f)).toInt(),
        )
    }

    /**
     * Centre-crop of [width] x [height] keeping `1/[factor]` of each axis - the soft-zoom crop.
     *
     * Kept separate from [centreCropRect] rather than passing `1f / factor` into it, because dividing
     * and multiplying by the reciprocal are not the same in `Float`: across the plausible factor range
     * the two disagree on 38 values by one pixel, and this is the path that overwrites the user's photo.
     */
    fun centreCropByFactor(width: Int, height: Int, factor: Float): CropRect {
        if (width <= 0 || height <= 0 || factor <= 0f) return CropRect(0, 0, width, height)
        return centred(width, height, (width / factor).toInt(), (height / factor).toInt())
    }

    private fun centred(width: Int, height: Int, cropWidth: Int, cropHeight: Int): CropRect {
        val cropW = cropWidth.coerceIn(1, width)
        val cropH = cropHeight.coerceIn(1, height)
        val left = (width - cropW) / 2
        val top = (height - cropH) / 2
        return CropRect(left, top, left + cropW, top + cropH)
    }

    /**
     * Centre-crop of [width] x [height] narrowed to [targetLongOverShort], trimming the short edge
     * only. A frame already at or narrower than the target is returned untouched.
     */
    fun cropRectForRatio(width: Int, height: Int, targetLongOverShort: Float): CropRect {
        val longEdge = maxOf(width, height)
        val shortEdge = minOf(width, height)
        val targetShort = if (targetLongOverShort > 0f) {
            (longEdge / targetLongOverShort).toInt()
        } else {
            shortEdge
        }
        val untouched = width <= 0 || height <= 0 || targetShort >= shortEdge
        return when {
            untouched -> CropRect(0, 0, width, height)
            width >= height -> {
                val top = (height - targetShort) / 2
                CropRect(0, top, width, top + targetShort)
            }
            else -> {
                val left = (width - targetShort) / 2
                CropRect(left, 0, left + targetShort, height)
            }
        }
    }
}
