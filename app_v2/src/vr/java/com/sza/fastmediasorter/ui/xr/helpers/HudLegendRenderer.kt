package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.Canvas
import android.graphics.Paint

/**
 * S1223: Canvas painter of the one-time immersive controls legend.
 *
 * Same shape as [HudCanvasRenderer] - a Context-free model plus one [render] call - so the host
 * keeps owning localization. It paints onto the HUD texture channel, which is why the two can never
 * be on screen together: the legend is modal by nature and the strip returns the moment it closes.
 *
 * The 1600x1120 texture is paired with a 1.00x0.70 m quad in [HudLegendController]; the aspect must
 * stay matched, exactly as the strip's 2560x360 matches its 1.40x0.197 m quad. Glyphs land at
 * roughly the same physical height as the strip's header line, which is the size the owner accepted
 * in the S1228 in-headset review.
 */
class HudLegendRenderer {

    /** One legend line: the input on the left, what it does on the right. */
    data class LegendRow(val input: String, val action: String)

    companion object {
        const val WIDTH = 1600
        const val HEIGHT = 1120

        private const val MARGIN = 32f

        private const val TITLE_BASELINE = 120f
        private const val TITLE_TEXT_SIZE = 64f

        private const val ROWS_TOP = 250f
        private const val ROW_PITCH = 96f
        private const val ROW_TEXT_SIZE = 46f
        private const val INPUT_COLUMN_X = 96f
        private const val ACTION_COLUMN_X = 760f
        private const val COLUMN_GAP = 24f

        private const val FOOTER_BASELINE = 1044f
        private const val FOOTER_TEXT_SIZE = 44f

        // S1271: channel values live in HudRowPrimitives now - one palette for every HUD page.

        private const val INPUT_BUDGET = ACTION_COLUMN_X - INPUT_COLUMN_X - COLUMN_GAP
        private const val ACTION_BUDGET = WIDTH - MARGIN - ACTION_COLUMN_X

        // Title and footer span the whole page rather than a column.
        private const val FULL_BUDGET = WIDTH - MARGIN - INPUT_COLUMN_X
    }

    var title: String = "Controls"
    var footer: String = ""
    var rows: List<LegendRow> = emptyList()

    // S1271: paints come from the shared primitive so the legend cannot drift from the strip.
    private val primitives = HudRowPrimitives()
    private val titlePaint = primitives.headerTextPaint(TITLE_TEXT_SIZE)
    private val inputPaint = primitives.accentTextPaint(ROW_TEXT_SIZE)
    private val actionPaint = primitives.whiteTextPaint(ROW_TEXT_SIZE, bold = false)
    private val footerPaint = primitives.statusTextPaint(FOOTER_TEXT_SIZE)

    fun render(canvas: Canvas) {
        primitives.drawPanelBackground(canvas, WIDTH, HEIGHT, MARGIN)

        val shownTitle = ellipsize(title, titlePaint, FULL_BUDGET)
        canvas.drawText(shownTitle, INPUT_COLUMN_X, TITLE_BASELINE, titlePaint)

        var baseline = ROWS_TOP
        for (row in rows) {
            canvas.drawText(ellipsize(row.input, inputPaint, INPUT_BUDGET), INPUT_COLUMN_X, baseline, inputPaint)
            canvas.drawText(ellipsize(row.action, actionPaint, ACTION_BUDGET), ACTION_COLUMN_X, baseline, actionPaint)
            baseline += ROW_PITCH
        }

        val shownFooter = ellipsize(footer, footerPaint, FULL_BUDGET)
        canvas.drawText(shownFooter, INPUT_COLUMN_X, FOOTER_BASELINE, footerPaint)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String =
        primitives.ellipsize(text, paint, maxWidth)
}
