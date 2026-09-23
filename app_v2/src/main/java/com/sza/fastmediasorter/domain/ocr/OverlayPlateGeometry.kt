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
     * The overflow rule OCR-OVERLAY rule 9 asks a product to write down, and this is where it lives:
     * - the plate grows downward from the source top while the view has room below it;
     * - once it would pass the view bottom, its bottom is pinned there and it grows upward instead,
     *   so the text always sits on the backing and the source line stays covered;
     * - it never grows above the view top, so a translation taller than the whole view gets a
     *   full-height plate and the caller shrinks the type ([OVERFLOW_FONT_STEP] down to
     *   [OVERFLOW_FONT_FLOOR]) before accepting that its tail runs past the edge;
     * - plates are painted in block order, so a plate lifted onto the one above it wins.
     *
     * @param viewBottom the drawing surface's own bottom edge; the view top is 0.
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
        val needed = (translation.height + bothSides).coerceAtLeast(source.height)
        // A box that already starts below the edge keeps its own extent: the plate still has to
        // cover the source line, and the part past the edge is drawn nowhere anyway.
        val lowestBottom = viewBottom.coerceAtLeast(source.top + source.height)
        val bottom = (source.top + needed).coerceAtMost(lowestBottom)
        // A box that starts above the view top is not pulled down to it: the anchor never moves down.
        val top = (bottom - needed).coerceIn(minOf(0f, source.top), source.top)
        return OverlayPlateBounds(
            left = source.left,
            top = top,
            right = right,
            bottom = bottom,
        )
    }

    /**
     * Type-size ladder for a translation taller than the whole view (OCR-OVERLAY rule 9).
     * Inherited, not derived here: `ocr-pipeline.md` §3.4 of the reference implementation steps the
     * font down 8% at a time with a floor at 50% of the base size.
     */
    const val OVERFLOW_FONT_STEP = 0.92f

    /** Floor of the ladder as a share of the starting type size; see [OVERFLOW_FONT_STEP]. */
    const val OVERFLOW_FONT_FLOOR = 0.5f
}
