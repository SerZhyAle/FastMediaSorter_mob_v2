package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface

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
        private const val CORNER_RADIUS = 28f

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

        // Channel values copied from HudCanvasRenderer so the two pages read as one surface.
        private const val BG_ALPHA = 220
        private const val BG_R = 15
        private const val BG_G = 15
        private const val BG_B = 25
        private const val ACCENT_R = 66
        private const val ACCENT_G = 165
        private const val ACCENT_B = 245
        private const val HEADER_GREY_RG = 230
        private const val HEADER_GREY_B = 250
        private const val FOOTER_R = 129
        private const val FOOTER_G = 199
        private const val FOOTER_B = 132

        // Ellipsize: keep at least this many chars, dropping this many per step before "..".
        private const val ELLIPSIZE_MIN_LEN = 4
        private const val ELLIPSIZE_DROP = 3

        private const val INPUT_BUDGET = ACTION_COLUMN_X - INPUT_COLUMN_X - COLUMN_GAP
        private const val ACTION_BUDGET = WIDTH - MARGIN - ACTION_COLUMN_X

        // Title and footer span the whole page rather than a column.
        private const val FULL_BUDGET = WIDTH - MARGIN - INPUT_COLUMN_X
    }

    var title: String = "Controls"
    var footer: String = ""
    var rows: List<LegendRow> = emptyList()

    private val bgPaint = Paint().apply {
        color = Color.argb(BG_ALPHA, BG_R, BG_G, BG_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val titlePaint = Paint().apply {
        color = Color.rgb(HEADER_GREY_RG, HEADER_GREY_RG, HEADER_GREY_B)
        textSize = TITLE_TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val inputPaint = Paint().apply {
        color = Color.rgb(ACCENT_R, ACCENT_G, ACCENT_B)
        textSize = ROW_TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val actionPaint = Paint().apply {
        color = Color.WHITE
        textSize = ROW_TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val footerPaint = Paint().apply {
        color = Color.rgb(FOOTER_R, FOOTER_G, FOOTER_B)
        textSize = FOOTER_TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    fun render(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawRoundRect(
            RectF(MARGIN, MARGIN, WIDTH - MARGIN, HEIGHT - MARGIN),
            CORNER_RADIUS,
            CORNER_RADIUS,
            bgPaint
        )

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

    /** Canvas has no TextUtils here, so trim manually until the string fits [maxWidth]. */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        var shown = text
        while (shown.length > ELLIPSIZE_MIN_LEN && paint.measureText(shown) > maxWidth) {
            shown = shown.dropLast(ELLIPSIZE_DROP) + ".."
        }
        return shown
    }
}
