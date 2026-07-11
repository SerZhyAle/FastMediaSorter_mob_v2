package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.*

/**
 * Model + Canvas painter of the immersive HUD panel.
 *
 * S0964: the panel is the visible HUD on user launches (`FILE_URI`) - it is rendered into the
 * HUD quad via `queueHud` on state changes (never per frame, S0290 rule). The diagnostic
 * playlist keeps the 1024x128 filename banner and uses this class as interaction model only.
 * All captions are injected localized by the Activity - the renderer stays Context-free.
 */
class HudCanvasRenderer {

    companion object {
        const val WIDTH = 1024

        // S0964: 512 -> 640 to fit the two track rows between the status line and transport row.
        const val HEIGHT = 640

        // Panel layout grid (canvas px): header/status on top, AUDIO + SUBS cycle rows, then the
        // transport row on the left with the slider column on the right.
        private const val CAPTION_X = 80f
        private const val ROW_ARROW_LEFT_X = 300f
        private const val ROW_ARROW_RIGHT_X = 880f
        private const val ROW_ARROW_W = 80f
        private const val ROW_H = 70f
        private const val AUDIO_ROW_TOP = 200f
        private const val SUBS_ROW_TOP = 290f
        private const val TRANSPORT_LEFT = 100f
        private const val TRANSPORT_BTN_W = 160f
        private const val TRANSPORT_GAP = 40f
        private const val TRANSPORT_TOP = 400f
        private const val TRANSPORT_BOTTOM = 500f
        private const val SLIDER_COL_X = 700f
        private const val SLIDER_RIGHT_X = 950f
        private const val SLIDER_H = 20f
        private const val VOLUME_LABEL_Y = 390f
        private const val VOLUME_SLIDER_TOP = 420f
        private const val DEPTH_LABEL_Y = 490f
        private const val DEPTH_SLIDER_TOP = 520f
        private const val CAPTION_BASELINE_SHIFT = 12f
        private const val VALUE_GAP = 40f

        // Ellipsize: keep at least this many chars, dropping this many per step before "..".
        private const val ELLIPSIZE_MIN_LEN = 4
        private const val ELLIPSIZE_DROP = 3

        // Disabled/dim styling components (bluish grey matching the panel background family).
        private const val DISABLED_ALPHA = 90
        private const val DISABLED_GREY_R = 120
        private const val DISABLED_GREY_G = 130
        private const val DISABLED_GREY_B = 150
        private const val DIM_TEXT_ALPHA = 140
    }

    // Interactive button rects (transport row)
    val prevRect = transportButtonRect(0)
    val playPauseRect = transportButtonRect(1)
    val nextRect = transportButtonRect(2)

    // S0964: track rows - previous/next cycle arrows per track type.
    val audioPrevRect = arrowRect(ROW_ARROW_LEFT_X, AUDIO_ROW_TOP)
    val audioNextRect = arrowRect(ROW_ARROW_RIGHT_X, AUDIO_ROW_TOP)
    val subsPrevRect = arrowRect(ROW_ARROW_LEFT_X, SUBS_ROW_TOP)
    val subsNextRect = arrowRect(ROW_ARROW_RIGHT_X, SUBS_ROW_TOP)

    // Sliders
    val volumeTrackRect = RectF(SLIDER_COL_X, VOLUME_SLIDER_TOP, SLIDER_RIGHT_X, VOLUME_SLIDER_TOP + SLIDER_H)
    val depthTrackRect = RectF(SLIDER_COL_X, DEPTH_SLIDER_TOP, SLIDER_RIGHT_X, DEPTH_SLIDER_TOP + SLIDER_H)

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
        color = Color.argb(220, 15, 15, 25)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val accentPaint = Paint().apply {
        color = Color.argb(255, 66, 165, 245) // Beautiful material blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val disabledPaint = Paint().apply {
        color = Color.argb(DISABLED_ALPHA, DISABLED_GREY_R, DISABLED_GREY_G, DISABLED_GREY_B)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val trackPaint = Paint().apply {
        color = Color.argb(100, 100, 100, 120)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val dimTextPaint = Paint().apply {
        color = Color.WHITE
        alpha = DIM_TEXT_ALPHA
        textSize = 32f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val headerPaint = Paint().apply {
        color = Color.argb(255, 230, 230, 250)
        textSize = 40f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val statusPaint = Paint().apply {
        color = Color.argb(255, 129, 199, 132) // Soft green
        textSize = 28f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    fun render(canvas: Canvas) {
        // Clear canvas
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Draw panel background
        val panelRect = RectF(40f, 40f, (WIDTH - 40).toFloat(), (HEIGHT - 40).toFloat())
        canvas.drawRoundRect(panelRect, 24f, 24f, bgPaint)

        // Draw File Header
        canvas.drawText("FILE: $currentFilename", CAPTION_X, 100f, headerPaint)

        // Draw Status / FPS
        canvas.drawText("SYSTEM: IMMERSIVE ACTIVE | FPS: %.1f".format(fps), CAPTION_X, 160f, statusPaint)

        // S0964: track rows (audio, subtitles)
        drawTrackRow(canvas, audioCaption, audioTrackLabel, audioPrevRect, audioNextRect, audioRowEnabled)
        drawTrackRow(canvas, subsCaption, subtitleTrackLabel, subsPrevRect, subsNextRect, subsRowEnabled)

        // Draw transport row
        drawButton(canvas, prevRect, prevLabel, accentPaint)
        drawButton(canvas, playPauseRect, if (isPlaying) pauseLabel else playLabel, accentPaint)
        drawButton(canvas, nextRect, nextLabel, accentPaint)

        // Draw Volume Slider
        canvas.drawText(volumeCaption, SLIDER_COL_X, VOLUME_LABEL_Y, textPaint)
        canvas.drawRoundRect(volumeTrackRect, 10f, 10f, trackPaint)
        val volFillWidth = volumeTrackRect.left + (volumeTrackRect.width() * volume)
        canvas.drawRoundRect(RectF(volumeTrackRect.left, volumeTrackRect.top, volFillWidth, volumeTrackRect.bottom), 10f, 10f, accentPaint)
        canvas.drawCircle(volFillWidth, volumeTrackRect.centerY(), 18f, textPaint)

        // Draw Depth Slider
        canvas.drawText(depthCaption, SLIDER_COL_X, DEPTH_LABEL_Y, textPaint)
        canvas.drawRoundRect(depthTrackRect, 10f, 10f, trackPaint)
        val depthFillWidth = depthTrackRect.left + (depthTrackRect.width() * depth)
        canvas.drawRoundRect(RectF(depthTrackRect.left, depthTrackRect.top, depthFillWidth, depthTrackRect.bottom), 10f, 10f, accentPaint)
        canvas.drawCircle(depthFillWidth, depthTrackRect.centerY(), 18f, textPaint)

        // Draw Virtual Pointer Cursor Feedback (low-latency feedback on Canvas)
        if (hasHover) {
            val cursorPaint = Paint().apply {
                color = Color.argb(160, 255, 64, 129) // Glowing magenta/pink cursor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(hoverX, hoverY, 14f, cursorPaint)
        }
    }

    /** S0964: one cycle row - caption, `<` arrow, current value, `>` arrow. */
    private fun drawTrackRow(
        canvas: Canvas,
        caption: String,
        value: String,
        prevArrow: RectF,
        nextArrow: RectF,
        enabled: Boolean
    ) {
        val buttonPaint = if (enabled) accentPaint else disabledPaint
        val valuePaint = if (enabled) textPaint else dimTextPaint
        val captionBaseline = prevArrow.centerY() + CAPTION_BASELINE_SHIFT
        canvas.drawText(caption, CAPTION_X, captionBaseline, valuePaint)
        drawButton(canvas, prevArrow, "<", buttonPaint)
        drawButton(canvas, nextArrow, ">", buttonPaint)
        // Value centered between the arrows; ellipsize manually - Canvas has no TextUtils here.
        val maxWidth = nextArrow.left - prevArrow.right - VALUE_GAP
        var shown = value
        while (shown.length > ELLIPSIZE_MIN_LEN && valuePaint.measureText(shown) > maxWidth) {
            shown = shown.dropLast(ELLIPSIZE_DROP) + ".."
        }
        val valueX = (prevArrow.right + nextArrow.left) / 2f - valuePaint.measureText(shown) / 2f
        canvas.drawText(shown, valueX, captionBaseline, valuePaint)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, text: String, paint: Paint) {
        canvas.drawRoundRect(rect, 16f, 16f, paint)
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textX = rect.centerX() - textBounds.centerX()
        val textY = rect.centerY() - textBounds.centerY()
        canvas.drawText(text, textX, textY, textPaint)
    }

    private fun transportButtonRect(index: Int): RectF {
        val left = TRANSPORT_LEFT + index * (TRANSPORT_BTN_W + TRANSPORT_GAP)
        return RectF(left, TRANSPORT_TOP, left + TRANSPORT_BTN_W, TRANSPORT_BOTTOM)
    }

    private fun arrowRect(left: Float, top: Float) = RectF(left, top, left + ROW_ARROW_W, top + ROW_H)
}
