package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.*

class HudCanvasRenderer {

    companion object {
        const val WIDTH = 1024
        const val HEIGHT = 512
    }

    // Interactive button rects
    val prevRect = RectF(100f, 320f, 260f, 420f)
    val playPauseRect = RectF(300f, 320f, 460f, 420f)
    val nextRect = RectF(500f, 320f, 660f, 420f)
    
    // Sliders
    val volumeTrackRect = RectF(700f, 200f, 950f, 220f)
    val depthTrackRect = RectF(700f, 350f, 950f, 370f)

    var isPlaying = true
    var volume = 1.0f // 0.0 to 1.0
    var depth = 0.5f  // 0.0 to 1.0
    var currentFilename = "Loading.."
    var fps = 0.0f

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
        canvas.drawText("FILE: $currentFilename", 80f, 100f, headerPaint)

        // Draw Status / FPS
        canvas.drawText("SYSTEM: IMMERSIVE ACTIVE | FPS: %.1f".format(fps), 80f, 160f, statusPaint)

        // Draw Prev Button
        drawButton(canvas, prevRect, "PREV", accentPaint)

        // Draw Play/Pause Button
        val playText = if (isPlaying) "PAUSE" else "PLAY"
        drawButton(canvas, playPauseRect, playText, accentPaint)

        // Draw Next Button
        drawButton(canvas, nextRect, "NEXT", accentPaint)

        // Draw Volume Slider
        canvas.drawText("VOLUME", 700f, 160f, textPaint)
        canvas.drawRoundRect(volumeTrackRect, 10f, 10f, trackPaint)
        val volFillWidth = volumeTrackRect.left + (volumeTrackRect.width() * volume)
        canvas.drawRoundRect(RectF(volumeTrackRect.left, volumeTrackRect.top, volFillWidth, volumeTrackRect.bottom), 10f, 10f, accentPaint)
        canvas.drawCircle(volFillWidth, volumeTrackRect.centerY(), 18f, textPaint)

        // Draw Depth Slider
        canvas.drawText("STEREO DEPTH", 700f, 310f, textPaint)
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

    private fun drawButton(canvas: Canvas, rect: RectF, text: String, paint: Paint) {
        canvas.drawRoundRect(rect, 16f, 16f, paint)
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textX = rect.centerX() - textBounds.centerX()
        val textY = rect.centerY() - textBounds.centerY()
        canvas.drawText(text, textX, textY, textPaint)
    }
}
