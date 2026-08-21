package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * S1271: the shared visual language of the immersive HUD surfaces - media strip (S0964), controls
 * legend (S1223) and settings panel (S1271). One home for the palette, the paint recipes, text
 * trimming and the button/slider/background painters plus the row hit-rectangle helpers.
 *
 * The strip and the legend used to carry hand-copied colour constants ("copied from
 * HudCanvasRenderer so the two pages read as one surface"); the third surface is where the
 * strategic spec drew the line and moved them here (§4).
 *
 * Contract: Context-free and allocation-free. No Bitmap and no buffer is created in this class -
 * HUD buffers belong to the visible owner and are released on hide (S0290/S1232 rules). Surface
 * geometry (which rows exist, where they sit) stays with each renderer; only the presentation
 * rules live here.
 */
class HudRowPrimitives {

    companion object {
        // --- Palette (formerly duplicated across HudCanvasRenderer / HudLegendRenderer) ---
        const val BG_ALPHA = 220
        const val BG_R = 15
        const val BG_G = 15
        const val BG_B = 25
        const val ACCENT_R = 66
        const val ACCENT_G = 165
        const val ACCENT_B = 245
        const val EXIT_R = 198
        const val EXIT_G = 82
        const val EXIT_B = 92
        const val TRACK_ALPHA = 100
        const val TRACK_GREY = 100
        const val TRACK_GREY_B = 120
        const val HEADER_GREY_RG = 230
        const val HEADER_GREY_B = 250
        const val STATUS_R = 129
        const val STATUS_G = 199
        const val STATUS_B = 132
        const val CURSOR_ALPHA = 160
        const val CURSOR_R_CHANNEL = 255
        const val CURSOR_G_CHANNEL = 64
        const val CURSOR_B_CHANNEL = 129

        // Shared shape language.
        const val CORNER_RADIUS = 28f
        const val BUTTON_RADIUS = 18f
        const val SLIDER_RADIUS = 12f

        // Ellipsize: keep at least this many chars, dropping this many per step before "..".
        private const val ELLIPSIZE_MIN_LEN = 4
        private const val ELLIPSIZE_DROP = 3
    }

    // --- Fill paints (size-independent, shared as instances) ---

    val bgPaint = Paint().apply {
        color = Color.argb(BG_ALPHA, BG_R, BG_G, BG_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val accentPaint = Paint().apply {
        color = Color.rgb(ACCENT_R, ACCENT_G, ACCENT_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val exitPaint = Paint().apply {
        color = Color.rgb(EXIT_R, EXIT_G, EXIT_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val trackPaint = Paint().apply {
        color = Color.argb(TRACK_ALPHA, TRACK_GREY, TRACK_GREY, TRACK_GREY_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val cursorPaint = Paint().apply {
        color = Color.argb(CURSOR_ALPHA, CURSOR_R_CHANNEL, CURSOR_G_CHANNEL, CURSOR_B_CHANNEL)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // --- Text paint recipes (each surface picks its own sizes; colours and faces are shared) ---

    /** White row/value/button text - the strip's textPaint and the legend's actionPaint family. */
    fun whiteTextPaint(textSize: Float, bold: Boolean = true): Paint = Paint().apply {
        color = Color.WHITE
        this.textSize = textSize
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Accent-coloured text - the legend's input column. */
    fun accentTextPaint(textSize: Float, bold: Boolean = true): Paint = Paint().apply {
        color = Color.rgb(ACCENT_R, ACCENT_G, ACCENT_B)
        this.textSize = textSize
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Header-grey monospace - the strip's header line and the legend's title. */
    fun headerTextPaint(textSize: Float, bold: Boolean = true): Paint = Paint().apply {
        color = Color.rgb(HEADER_GREY_RG, HEADER_GREY_RG, HEADER_GREY_B)
        this.textSize = textSize
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Status-green monospace - the strip's FPS/time labels and the legend's footer. */
    fun statusTextPaint(textSize: Float): Paint = Paint().apply {
        color = Color.rgb(STATUS_R, STATUS_G, STATUS_B)
        this.textSize = textSize
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    // --- Painters ---

    /** Clears the canvas and draws the rounded translucent panel background every surface shares. */
    fun drawPanelBackground(canvas: Canvas, width: Int, height: Int, margin: Float) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawRoundRect(
            RectF(margin, margin, width - margin, height - margin),
            CORNER_RADIUS,
            CORNER_RADIUS,
            bgPaint
        )
    }

    /** Rounded button with centred text - transport, arrows and terminal buttons alike. */
    fun drawButton(canvas: Canvas, rect: RectF, text: String, fill: Paint, textPaint: Paint) {
        canvas.drawRoundRect(rect, BUTTON_RADIUS, BUTTON_RADIUS, fill)
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, rect.centerX() - textBounds.centerX(), rect.centerY() - textBounds.centerY(), textPaint)
    }

    /** Horizontal slider track with accent fill and a knob at the value position. */
    fun drawSliderTrack(canvas: Canvas, track: RectF, value: Float, knobRadius: Float, knobPaint: Paint) {
        canvas.drawRoundRect(track, SLIDER_RADIUS, SLIDER_RADIUS, trackPaint)
        val fillRight = track.left + track.width() * value.coerceIn(0f, 1f)
        canvas.drawRoundRect(
            RectF(track.left, track.top, fillRight, track.bottom),
            SLIDER_RADIUS,
            SLIDER_RADIUS,
            accentPaint
        )
        canvas.drawCircle(fillRight, track.centerY(), knobRadius, knobPaint)
    }

    /** Hover cursor dot, drawn last by interactive surfaces. */
    fun drawCursor(canvas: Canvas, x: Float, y: Float, radius: Float) {
        canvas.drawCircle(x, y, radius, cursorPaint)
    }

    /** Canvas has no TextUtils here, so trim manually until the string fits [maxWidth]. */
    fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        var shown = text
        while (shown.length > ELLIPSIZE_MIN_LEN && paint.measureText(shown) > maxWidth) {
            shown = shown.dropLast(ELLIPSIZE_DROP) + ".."
        }
        return shown
    }

    // --- Row hit-rectangle helpers (Canvas-space; surfaces keep their own anchors) ---

    /** A `<` / `>` arrow zone of a cycle row. */
    fun arrowRect(left: Float, top: Float, bottom: Float, width: Float) =
        RectF(left, top, left + width, top + (bottom - top))

    /** One button in an evenly spaced horizontal run (the strip's transport trio). */
    fun runButtonRect(runLeft: Float, index: Int, buttonWidth: Float, gap: Float, top: Float, bottom: Float): RectF {
        val left = runLeft + index * (buttonWidth + gap)
        return RectF(left, top, left + buttonWidth, bottom)
    }
}
