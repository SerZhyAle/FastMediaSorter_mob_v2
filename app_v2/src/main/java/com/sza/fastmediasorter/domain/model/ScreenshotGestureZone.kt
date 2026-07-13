package com.sza.fastmediasorter.domain.model

/** One of the four edge bands a screenshot gesture can start from (2 left, 2 right; 10-40% / 60-90% of safe height). */
enum class ScreenshotGestureZone {
    LEFT_TOP,
    LEFT_BOTTOM,
    RIGHT_TOP,
    RIGHT_BOTTOM;

    /** Right-edge bands drag inward leftward; the classifier mirrors the horizontal delta for these. */
    val isRightEdge: Boolean
        get() = this == RIGHT_TOP || this == RIGHT_BOTTOM

    /** Bottom bands anchor at 60% of safe height; top bands at 10%. */
    val isBottomBand: Boolean
        get() = this == LEFT_BOTTOM || this == RIGHT_BOTTOM
}
