package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Model + Canvas painter of the immersive HUD panel.
 *
 * S0964: the panel is the visible HUD on user launches (`FILE_URI`) - it is rendered into the
 * HUD quad via `queueHud` on state changes (never per frame, S0290 rule). The diagnostic
 * playlist keeps the 1024x128 filename banner and uses this class as interaction model only.
 * All captions are injected localized by the Activity - the renderer stays Context-free.
 *
 * S1228 (owner, 2026-07-27, in-headset review): the panel was a 1024x640 block on a 0.48x0.30 m
 * quad centred in view - the text measured ~0.015 m tall at 1.5 m ("шрифт как маленькие кубики")
 * and the block sat on top of the film. It is now a single horizontal strip: a wider texture on a
 * wider quad gives ~1.9x the glyph height, one row of controls leaves the centre of view clear,
 * and a close button collapses the strip to a small pill that restores it.
 */
class HudCanvasRenderer {

    companion object {
        // S1228: 1024x640 block -> 2560x360 strip (~7.1:1). Paired with a 1.40x0.197 m quad in
        // DiagnosticXrActivity, so glyph height goes 0.015 m -> 0.028 m at the 1.5 m watch distance.
        const val WIDTH = 2560
        const val HEIGHT = 360

        private const val MARGIN = 24f
        private const val CORNER_RADIUS = 28f
        private const val BUTTON_RADIUS = 18f
        private const val SLIDER_RADIUS = 12f

        // Header line: filename + status, spanning the strip above the control row.
        private const val HEADER_X = 32f
        private const val HEADER_BASELINE = 96f
        private const val HEADER_TEXT_SIZE = 52f
        private const val STATUS_TEXT_SIZE = 36f
        private const val STATUS_GAP = 32f

        // Single control row.
        private const val ROW_TOP = 176f
        private const val ROW_BOTTOM = 312f
        private const val ROW_CENTER = (ROW_TOP + ROW_BOTTOM) / 2f
        private const val CAPTION_BASELINE_SHIFT = 16f

        // Transport block (PREV / PLAY-PAUSE / NEXT).
        private const val TRANSPORT_LEFT = 32f
        private const val TRANSPORT_BTN_W = 196f
        private const val TRANSPORT_GAP = 20f

        // Track cycle blocks (AUDIO, SUBS): caption, `<`, value, `>`.
        private const val ARROW_W = 90f
        private const val AUDIO_CAPTION_X = 700f
        private const val AUDIO_PREV_X = 888f
        private const val AUDIO_NEXT_X = 1218f
        private const val SUBS_CAPTION_X = 1356f
        private const val SUBS_PREV_X = 1544f
        private const val SUBS_NEXT_X = 1874f

        // Slider columns (VOLUME, STEREO DEPTH): caption above a horizontal track.
        private const val SLIDER_CAPTION_BASELINE = 214f
        private const val SLIDER_TOP = 250f
        private const val SLIDER_H = 26f
        private const val SLIDER_KNOB_R = 22f
        private const val VOLUME_X = 2012f
        private const val VOLUME_RIGHT_X = 2252f
        private const val DEPTH_X = 2292f
        private const val DEPTH_RIGHT_X = 2532f

        // S1228: close button, top-right. Collapsing keeps the quad but paints only the pill.
        private const val CLOSE_W = 120f
        private const val CLOSE_TOP = 24f
        private const val CLOSE_H = 108f
        private const val CLOSE_LABEL = "X"

        // S1228: the collapsed affordance - centred so it is found without hunting.
        private const val PILL_HALF_W = 110f
        private const val PILL_TOP = 120f
        private const val PILL_H = 120f
        private const val PILL_LABEL = "HUD"

        private const val TEXT_SIZE = 48f
        private const val CAPTION_TEXT_SIZE = 40f
        private const val CURSOR_R = 16f

        // Ellipsize: keep at least this many chars, dropping this many per step before "..".
        private const val ELLIPSIZE_MIN_LEN = 4
        private const val ELLIPSIZE_DROP = 3

        // Disabled/dim styling components (bluish grey matching the panel background family).
        private const val DISABLED_ALPHA = 90
        private const val DISABLED_GREY_R = 120
        private const val DISABLED_GREY_G = 130
        private const val DISABLED_GREY_B = 150
        private const val DIM_TEXT_ALPHA = 140

        private const val BG_ALPHA = 220
        private const val BG_R = 15
        private const val BG_G = 15
        private const val BG_B = 25
        private const val ACCENT_R = 66
        private const val ACCENT_G = 165
        private const val ACCENT_B = 245
        private const val CLOSE_R = 198
        private const val CLOSE_G = 82
        private const val CLOSE_B = 92
        private const val TRACK_ALPHA = 100
        private const val TRACK_GREY = 100
        private const val TRACK_GREY_B = 120
        private const val HEADER_GREY_RG = 230
        private const val HEADER_GREY_B = 250
        private const val STATUS_R = 129
        private const val STATUS_G = 199
        private const val STATUS_B = 132
        private const val CURSOR_ALPHA = 160
        private const val CURSOR_R_CHANNEL = 255
        private const val CURSOR_G_CHANNEL = 64
        private const val CURSOR_B_CHANNEL = 129
    }

    // Interactive button rects (transport block)
    val prevRect = transportButtonRect(0)
    val playPauseRect = transportButtonRect(1)
    val nextRect = transportButtonRect(2)

    // S0964: track rows - previous/next cycle arrows per track type.
    val audioPrevRect = arrowRect(AUDIO_PREV_X)
    val audioNextRect = arrowRect(AUDIO_NEXT_X)
    val subsPrevRect = arrowRect(SUBS_PREV_X)
    val subsNextRect = arrowRect(SUBS_NEXT_X)

    // Sliders
    val volumeTrackRect = RectF(VOLUME_X, SLIDER_TOP, VOLUME_RIGHT_X, SLIDER_TOP + SLIDER_H)
    val depthTrackRect = RectF(DEPTH_X, SLIDER_TOP, DEPTH_RIGHT_X, SLIDER_TOP + SLIDER_H)

    // S1228: close collapses the strip; the pill restores it. Both live on the same quad, so no
    // new controller binding is needed - the ray already addresses this texture.
    val closeRect = RectF(WIDTH - MARGIN - CLOSE_W, CLOSE_TOP, WIDTH - MARGIN, CLOSE_TOP + CLOSE_H)
    val expandRect = RectF(WIDTH / 2f - PILL_HALF_W, PILL_TOP, WIDTH / 2f + PILL_HALF_W, PILL_TOP + PILL_H)

    var isCollapsed = false

    var isPlaying = true
    var volume = 1.0f // 0.0 to 1.0
    var depth = 0.5f  // 0.0 to 1.0
    var currentFilename = "Loading.."
    var fps = 0.0f

    // S0964: captions/labels are set by the Activity (localized); EN defaults are fallbacks.
    var audioCaption = "AUDIO"
    var subsCaption = "SUBS"
    var audioTrackLabel = "-"
    var subtitleTrackLabel = "-"
    var audioRowEnabled = false
    var subsRowEnabled = false
    var prevLabel = "PREV"
    var playLabel = "PLAY"
    var pauseLabel = "PAUSE"
    var nextLabel = "NEXT"
    var volumeCaption = "VOLUME"
    var depthCaption = "STEREO DEPTH"

    // Cursor position for rendering feedback (drawn from UV in dispatch)
    var hoverX = -1f
    var hoverY = -1f
    var hasHover = false

    private val bgPaint = Paint().apply {
        color = Color.argb(BG_ALPHA, BG_R, BG_G, BG_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val accentPaint = Paint().apply {
        color = Color.rgb(ACCENT_R, ACCENT_G, ACCENT_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val closePaint = Paint().apply {
        color = Color.rgb(CLOSE_R, CLOSE_G, CLOSE_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val disabledPaint = Paint().apply {
        color = Color.argb(DISABLED_ALPHA, DISABLED_GREY_R, DISABLED_GREY_G, DISABLED_GREY_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val trackPaint = Paint().apply {
        color = Color.argb(TRACK_ALPHA, TRACK_GREY, TRACK_GREY, TRACK_GREY_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val captionPaint = Paint().apply {
        color = Color.WHITE
        textSize = CAPTION_TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val dimTextPaint = Paint().apply {
        color = Color.WHITE
        alpha = DIM_TEXT_ALPHA
        textSize = TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val headerPaint = Paint().apply {
        color = Color.rgb(HEADER_GREY_RG, HEADER_GREY_RG, HEADER_GREY_B)
        textSize = HEADER_TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val statusPaint = Paint().apply {
        color = Color.rgb(STATUS_R, STATUS_G, STATUS_B)
        textSize = STATUS_TEXT_SIZE
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    private val cursorPaint = Paint().apply {
        color = Color.argb(CURSOR_ALPHA, CURSOR_R_CHANNEL, CURSOR_G_CHANNEL, CURSOR_B_CHANNEL)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun render(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        if (isCollapsed) {
            renderCollapsed(canvas)
            return
        }

        canvas.drawRoundRect(
            RectF(MARGIN, MARGIN, WIDTH - MARGIN, HEIGHT - MARGIN),
            CORNER_RADIUS,
            CORNER_RADIUS,
            bgPaint
        )

        drawHeaderLine(canvas)

        drawButton(canvas, prevRect, prevLabel, accentPaint)
        drawButton(canvas, playPauseRect, if (isPlaying) pauseLabel else playLabel, accentPaint)
        drawButton(canvas, nextRect, nextLabel, accentPaint)

        drawTrackBlock(
            canvas,
            AUDIO_CAPTION_X,
            audioCaption,
            audioTrackLabel,
            audioPrevRect,
            audioNextRect,
            audioRowEnabled
        )
        drawTrackBlock(
            canvas,
            SUBS_CAPTION_X,
            subsCaption,
            subtitleTrackLabel,
            subsPrevRect,
            subsNextRect,
            subsRowEnabled
        )

        drawSlider(canvas, volumeCaption, volumeTrackRect, volume)
        drawSlider(canvas, depthCaption, depthTrackRect, depth)

        drawButton(canvas, closeRect, CLOSE_LABEL, closePaint)

        drawCursor(canvas)
    }

    /** S1228: collapsed state paints only the restore pill - the rest of the quad stays clear. */
    private fun renderCollapsed(canvas: Canvas) {
        drawButton(canvas, expandRect, PILL_LABEL, accentPaint)
        drawCursor(canvas)
    }

    private fun drawHeaderLine(canvas: Canvas) {
        val status = "FPS %.1f".format(fps)
        val statusWidth = statusPaint.measureText(status)
        val nameBudget = closeRect.left - HEADER_X - statusWidth - STATUS_GAP - MARGIN
        val name = ellipsize("FILE: $currentFilename", headerPaint, nameBudget)
        canvas.drawText(name, HEADER_X, HEADER_BASELINE, headerPaint)
        canvas.drawText(status, closeRect.left - STATUS_GAP - statusWidth, HEADER_BASELINE, statusPaint)
    }

    /** S0964: one cycle block - caption, `<` arrow, current value, `>` arrow, laid out inline. */
    private fun drawTrackBlock(
        canvas: Canvas,
        captionX: Float,
        caption: String,
        value: String,
        prevArrow: RectF,
        nextArrow: RectF,
        enabled: Boolean
    ) {
        val buttonPaint = if (enabled) accentPaint else disabledPaint
        val valuePaint = if (enabled) textPaint else dimTextPaint
        val baseline = ROW_CENTER + CAPTION_BASELINE_SHIFT
        val captionBudget = prevArrow.left - captionX - MARGIN
        canvas.drawText(ellipsize(caption, captionPaint, captionBudget), captionX, baseline, captionPaint)
        drawButton(canvas, prevArrow, "<", buttonPaint)
        drawButton(canvas, nextArrow, ">", buttonPaint)
        val shown = ellipsize(value, valuePaint, nextArrow.left - prevArrow.right - MARGIN)
        val valueX = (prevArrow.right + nextArrow.left) / 2f - valuePaint.measureText(shown) / 2f
        canvas.drawText(shown, valueX, baseline, valuePaint)
    }

    private fun drawSlider(canvas: Canvas, caption: String, track: RectF, value: Float) {
        val shown = ellipsize(caption, captionPaint, track.width())
        canvas.drawText(shown, track.left, SLIDER_CAPTION_BASELINE, captionPaint)
        canvas.drawRoundRect(track, SLIDER_RADIUS, SLIDER_RADIUS, trackPaint)
        val fillRight = track.left + track.width() * value
        canvas.drawRoundRect(
            RectF(track.left, track.top, fillRight, track.bottom),
            SLIDER_RADIUS,
            SLIDER_RADIUS,
            accentPaint
        )
        canvas.drawCircle(fillRight, track.centerY(), SLIDER_KNOB_R, textPaint)
    }

    private fun drawCursor(canvas: Canvas) {
        if (!hasHover) return
        canvas.drawCircle(hoverX, hoverY, CURSOR_R, cursorPaint)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, text: String, paint: Paint) {
        canvas.drawRoundRect(rect, BUTTON_RADIUS, BUTTON_RADIUS, paint)
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, rect.centerX() - textBounds.centerX(), rect.centerY() - textBounds.centerY(), textPaint)
    }

    /** Canvas has no TextUtils here, so trim manually until the string fits [maxWidth]. */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        var shown = text
        while (shown.length > ELLIPSIZE_MIN_LEN && paint.measureText(shown) > maxWidth) {
            shown = shown.dropLast(ELLIPSIZE_DROP) + ".."
        }
        return shown
    }

    private fun transportButtonRect(index: Int): RectF {
        val left = TRANSPORT_LEFT + index * (TRANSPORT_BTN_W + TRANSPORT_GAP)
        return RectF(left, ROW_TOP, left + TRANSPORT_BTN_W, ROW_BOTTOM)
    }

    private fun arrowRect(left: Float) = RectF(left, ROW_TOP, left + ARROW_W, ROW_BOTTOM)
}
