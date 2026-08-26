package com.sza.fastmediasorter.domain.ocr

/**
 * The scaled source box a translation replaces, in view pixels.
 *
 * Scaling and offsetting from OCR coordinates happen before this type is built; the geometry below
 * reasons only about the box as it already sits on screen.
 */
data class OverlaySourceBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * What the laid-out translation measured, in view pixels, plus the plate padding around it.
 *
 * Height is the laid-out text height, so a multi-line wrap is already accounted for by whoever
 * measured it - this type never re-measures text and holds no text engine.
 */
data class OverlayTranslationExtent(
    val width: Float,
    val height: Float,
    val padding: Float,
)

/** Final plate bounds in view pixels. */
data class OverlayPlateBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val height: Float get() = bottom - top
}

/**
 * S1716: the app's final plate rectangle, as pure arithmetic.
 *
 * It lives here rather than inside the overlay view so the production draw and the accuracy bench
 * score the same bounds. A bench that re-derived this calculation would be measuring its own
 * approximation of the plate rather than the plate (strategic §5.1 pillar 4), and a raster
 * comparison is not available as evidence on this host at all (§6.1).
 *
 * Deliberately free of every Android drawing, widget and text-layout type: everything the plate needs
 * to know arrives as numbers, which is also what makes the rectangle cases assertable without a
 * screen.
 */
object OverlayPlateGeometry {

    /**
     * Final plate bounds for one translated block.
     *
     * @param viewBottom the drawing surface's own bottom edge, the only limit on downward growth.
     */
    fun plateBounds(
        source: OverlaySourceBox,
        translation: OverlayTranslationExtent,
        viewBottom: Float,
    ): OverlayPlateBounds {
        val bothSides = translation.padding * 2
        // S0451: cover at least the original box width, never shrink below the source line.
        // S1713: the sideways cap is gone with the growth direction it belonged to - a plate that
        // widens covers the picture beside the line, which the line never occupied.
        val right = source.left + (translation.width + bothSides).coerceAtLeast(source.width)
        // S1713: downward is the direction a plate may grow, and it grows as far as the translation
        // needs. The surface's own bottom is the only limit, because a plate past it is drawn
        // nowhere. Coercing the room first keeps the range valid when the box starts below the edge.
        val roomBelow = (viewBottom - source.top).coerceAtLeast(source.height)
        val grown = (translation.height + bothSides).coerceIn(source.height, roomBelow)
        return OverlayPlateBounds(
            left = source.left,
            top = source.top,
            right = right,
            bottom = source.top + grown,
        )
    }
}
