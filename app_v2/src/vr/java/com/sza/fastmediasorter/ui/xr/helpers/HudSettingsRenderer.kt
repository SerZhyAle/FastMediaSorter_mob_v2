package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * S1271: Canvas painter of the immersive settings panel - the second interactive HUD surface.
 *
 * Same shape as [HudCanvasRenderer]: a Context-free model plus one [render] call, captions and
 * value labels injected localized by the host. Painted only on state change (S0290 rule); buffers
 * belong to [HudSettingsController], never to this class.
 *
 * Five rows, the owner's quiz set (strategic §3.3): stereo layout override, projection, the panel
 * distance/size pair (one row, two sliders), subtitle presentation, resume-from-last-position.
 * Built on [HudRowPrimitives] - the third surface is exactly the point where the shared primitive
 * had to exist instead of a third hand copy (strategic §4).
 *
 * The 1600x1120 texture pairs with the legend's 1.00x0.70 m quad: the panel is modal over the
 * strip on the single HUD channel, and reusing the proven quad keeps glyphs at the size the owner
 * accepted in the S1228 in-headset review.
 */
class HudSettingsRenderer {

    companion object {
        const val WIDTH = 1600
        const val HEIGHT = 1120

        private const val MARGIN = 32f

        private const val TITLE_BASELINE = 120f
        private const val TITLE_TEXT_SIZE = 64f

        // Five rows between title and footer.
        private const val ROWS_TOP = 200f
        private const val ROW_PITCH = 164f
        private const val ROW_H = 120f
        private const val CAPTION_TEXT_SIZE = 44f
        private const val VALUE_TEXT_SIZE = 48f
        private const val CAPTION_BASELINE_SHIFT = 16f

        // Cycle rows: caption on the left, `<` value `>` block right-aligned.
        private const val CAPTION_X = 96f
        private const val ARROW_W = 110f
        private const val VALUE_ZONE_W = 460f
        private const val CYCLE_RIGHT = WIDTH - MARGIN - 64f

        // Slider row: two half-width sliders (distance, size) sharing row 3.
        private const val SLIDER_W = 520f
        private const val SLIDER_H = 26f
        private const val SLIDER_KNOB_R = 22f
        private const val SLIDER_GAP = 96f
        private const val SLIDER_CAPTION_GAP = 46f

        private const val FOOTER_BASELINE = 1044f
        private const val FOOTER_TEXT_SIZE = 40f

        private const val CURSOR_R = 16f

        /** Row indexes in paint order - one home for the vertical map. */
        private const val ROW_LAYOUT = 0
        private const val ROW_PROJECTION = 1
        private const val ROW_PANEL = 2
        private const val ROW_SUBTITLES = 3
        private const val ROW_RESUME = 4
    }

    private val primitives = HudRowPrimitives()
    private val titlePaint = primitives.headerTextPaint(TITLE_TEXT_SIZE)
    private val captionPaint = primitives.whiteTextPaint(CAPTION_TEXT_SIZE)
    private val valuePaint = primitives.whiteTextPaint(VALUE_TEXT_SIZE)
    private val footerPaint = primitives.statusTextPaint(FOOTER_TEXT_SIZE)

    // --- Hit regions (Canvas space; the dispatcher hit-tests these instances) ---

    // Settings row 1: stereo layout override (mono / SBS / over-under).
    val layoutPrevRect = cycleArrowRect(ROW_LAYOUT, first = true)
    val layoutNextRect = cycleArrowRect(ROW_LAYOUT, first = false)

    // Settings row 2: projection (flat / 180 / 360).
    val projectionPrevRect = cycleArrowRect(ROW_PROJECTION, first = true)
    val projectionNextRect = cycleArrowRect(ROW_PROJECTION, first = false)

    // Settings row 3: panel distance and panel size sliders.
    val distanceTrackRect = sliderTrackRect(slot = 0)
    val sizeTrackRect = sliderTrackRect(slot = 1)

    // Settings row 4: subtitle presentation.
    val subtitlesPrevRect = cycleArrowRect(ROW_SUBTITLES, first = true)
    val subtitlesNextRect = cycleArrowRect(ROW_SUBTITLES, first = false)

    // Settings row 5: resume from last position.
    val resumePrevRect = cycleArrowRect(ROW_RESUME, first = true)
    val resumeNextRect = cycleArrowRect(ROW_RESUME, first = false)

    // --- Injected localized captions/labels (EN defaults are fallbacks, as in the strip) ---

    var title = "Settings"
    var footer = ""
    var layoutCaption = "LAYOUT"
    var layoutValue = "-"
    var projectionCaption = "PROJECTION"
    var projectionValue = "-"
    var distanceCaption = "DISTANCE"
    var sizeCaption = "SIZE"
    var subtitlesCaption = "SUBTITLES"
    var subtitlesValue = "-"
    var resumeCaption = "RESUME"
    var resumeValue = "-"

    /** 0..1 positions of the two sliders. */
    var distanceValue = 0.5f
    var sizeValue = 0.5f

    // Hover cursor, mirrored from the dispatcher like the strip's.
    var hoverX = -1f
    var hoverY = -1f
    var hasHover = false

    fun render(canvas: Canvas) {
        primitives.drawPanelBackground(canvas, WIDTH, HEIGHT, MARGIN)

        val fullBudget = WIDTH - 2 * CAPTION_X
        canvas.drawText(primitives.ellipsize(title, titlePaint, fullBudget), CAPTION_X, TITLE_BASELINE, titlePaint)

        drawCycleRow(canvas, ROW_LAYOUT, layoutCaption, layoutValue, layoutPrevRect, layoutNextRect)
        drawCycleRow(canvas, ROW_PROJECTION, projectionCaption, projectionValue, projectionPrevRect, projectionNextRect)
        drawSliderPairRow(canvas)
        drawCycleRow(canvas, ROW_SUBTITLES, subtitlesCaption, subtitlesValue, subtitlesPrevRect, subtitlesNextRect)
        drawCycleRow(canvas, ROW_RESUME, resumeCaption, resumeValue, resumePrevRect, resumeNextRect)

        canvas.drawText(primitives.ellipsize(footer, footerPaint, fullBudget), CAPTION_X, FOOTER_BASELINE, footerPaint)

        if (hasHover) {
            primitives.drawCursor(canvas, hoverX, hoverY, CURSOR_R)
        }
    }

    private fun drawCycleRow(
        canvas: Canvas,
        row: Int,
        caption: String,
        value: String,
        prevArrow: RectF,
        nextArrow: RectF
    ) {
        val baseline = rowCenter(row) + CAPTION_BASELINE_SHIFT
        val captionBudget = prevArrow.left - CAPTION_X - MARGIN
        canvas.drawText(primitives.ellipsize(caption, captionPaint, captionBudget), CAPTION_X, baseline, captionPaint)
        primitives.drawButton(canvas, prevArrow, "<", primitives.accentPaint, valuePaint)
        primitives.drawButton(canvas, nextArrow, ">", primitives.accentPaint, valuePaint)
        val shown = primitives.ellipsize(value, valuePaint, nextArrow.left - prevArrow.right - MARGIN)
        val valueX = (prevArrow.right + nextArrow.left) / 2f - valuePaint.measureText(shown) / 2f
        canvas.drawText(shown, valueX, baseline, valuePaint)
    }

    private fun drawSliderPairRow(canvas: Canvas) {
        drawCaptionedSlider(canvas, distanceCaption, distanceTrackRect, distanceValue)
        drawCaptionedSlider(canvas, sizeCaption, sizeTrackRect, sizeValue)
    }

    private fun drawCaptionedSlider(canvas: Canvas, caption: String, track: RectF, value: Float) {
        val shown = primitives.ellipsize(caption, captionPaint, track.width())
        canvas.drawText(shown, track.left, track.top - SLIDER_CAPTION_GAP + CAPTION_BASELINE_SHIFT, captionPaint)
        primitives.drawSliderTrack(canvas, track, value, SLIDER_KNOB_R, valuePaint)
    }

    private fun rowTop(row: Int) = ROWS_TOP + row * ROW_PITCH
    private fun rowCenter(row: Int) = rowTop(row) + ROW_H / 2f

    private fun cycleArrowRect(row: Int, first: Boolean): RectF {
        val nextLeft = CYCLE_RIGHT - ARROW_W
        val prevLeft = nextLeft - VALUE_ZONE_W - ARROW_W
        val left = if (first) prevLeft else nextLeft
        return RectF(left, rowTop(row), left + ARROW_W, rowTop(row) + ROW_H)
    }

    private fun sliderTrackRect(slot: Int): RectF {
        val rowCenterY = rowCenter(ROW_PANEL)
        val left = CAPTION_X + slot * (SLIDER_W + SLIDER_GAP)
        return RectF(left, rowCenterY, left + SLIDER_W, rowCenterY + SLIDER_H)
    }
}
