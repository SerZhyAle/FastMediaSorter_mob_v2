package com.sza.fastmediasorter.domain.model

/**
 * S1188: which pair of opposite screen edges carries the edge gesture bands.
 *
 * The bands belong to the edges Android's own bars leave free, so the axis is derived from where the
 * system insets sit rather than from the display rotation: rotating a device whose bars stay top and
 * bottom must leave the bands exactly where they were.
 */
enum class EdgeGestureAxis {
    /** Bands on the left and right edges - the portrait default, with the system bars top and bottom. */
    VERTICAL,

    /** Bands on the top and bottom edges - the only free pair once both side edges are taken. */
    HORIZONTAL;

    companion object {
        /**
         * Takes plain inset widths so both the overlay service and a settings view can feed it from
         * their own inset source without this model depending on the Android framework.
         */
        fun forInsets(left: Int, right: Int): EdgeGestureAxis =
            if (left > 0 && right > 0) HORIZONTAL else VERTICAL
    }
}
