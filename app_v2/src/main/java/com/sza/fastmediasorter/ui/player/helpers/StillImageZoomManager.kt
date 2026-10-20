package com.sza.fastmediasorter.ui.player.helpers

import com.github.chrisbanes.photoview.PhotoView

/**
 * Key and wheel zoom surface for the still image (MEDIA-VIEWER rules 3.1-3.2): the 1.25 step, the
 * fitted scale and the actual-size target. PhotoView's minimumScale is the fitted size. The file's
 * original pixel size wins over the drawable's intrinsic size, because a sampled decode lies about
 * actual size.
 */
class StillImageZoomManager(
    private val photoView: PhotoView,
    private val originalSize: () -> Pair<Int, Int>?
) {
    fun zoomIn(): Boolean = stepBy(zoomIn = true)

    fun zoomOut(): Boolean = stepBy(zoomIn = false)

    fun zoomAt(factor: Float, focusX: Float, focusY: Float): Boolean {
        photoView.setScale(photoView.scale * factor, focusX, focusY, true)
        return true
    }

    fun fit(): Boolean {
        photoView.setScale(photoView.minimumScale, true)
        return true
    }

    fun actualSize(): Boolean {
        val size = originalSize() ?: photoView.drawable?.let { it.intrinsicWidth to it.intrinsicHeight }
        val dimensions = size?.takeIf { (width, height) -> width > 0 && height > 0 } ?: return false
        val relative = ViewerZoomMath.actualSizeScale(
            dimensions.first,
            dimensions.second,
            photoView.width.toFloat(),
            photoView.height.toFloat(),
        )
        val target = photoView.minimumScale * relative
        if (target > photoView.maximumScale) photoView.maximumScale = target
        photoView.setScale(target, true)
        return true
    }

    private fun stepBy(zoomIn: Boolean): Boolean {
        val target = if (zoomIn) {
            ViewerZoomMath.stepIn(photoView.scale, photoView.maximumScale)
        } else {
            ViewerZoomMath.stepOut(photoView.scale, photoView.minimumScale)
        }
        photoView.setScale(target, true)
        return true
    }
}
