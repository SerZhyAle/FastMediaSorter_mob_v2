package com.sza.fastmediasorter.vr.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Pure Canvas painter for [VrHudState]. No allocations inside [draw].
 *
 * Layout (canvas pixels at default 1024×256):
 *   ┌────────────────────────────────────────────────────────┐
 *   │ banner / immersive badge (centre)              {file}  │
 *   │ action pulse (centre top)                      {repeat}│
 *   │           pause icon (centre)        volume bar (right)│
 *   │           zoom label (under pause)                     │
 *   │ seek overlay (above progress)                          │
 *   │ progress bar (full width, bottom)                      │
 *   └────────────────────────────────────────────────────────┘
 *
 * Default Typeface covers Latin and Cyrillic via the system fallback chain.
 */
class VrHudSceneComposer(
    @Suppress("unused") private val context: Context,
    private val width: Int = VrHudRenderer.DEFAULT_WIDTH,
    private val height: Int = VrHudRenderer.DEFAULT_HEIGHT,
    /**
     * S0024 Phase 04 smoke wire: invoked when the dispatcher resolves a click
     * on the seek-bar element. Real seek behaviour belongs to S0019 — this
     * lambda only confirms the end-to-end ray-input path. Default no-op so
     * non-VR consumers and unit tests stay unaffected.
     */
    private val onSeekBarClick: () -> Unit = {},
) {

    val registry: VrHudElementRegistry = VrHudElementRegistry(width, height)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        color = Color.WHITE
        textSize = 28f
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        color = Color.WHITE
        textSize = 22f
    }
    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
        textSize = 64f
        textAlign = Paint.Align.CENTER
    }
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 0, 0)
    }
    private val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 60, 60, 60)
    }
    private val progressBufferedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 130, 130, 130)
    }
    private val progressPositionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 80, 170, 240)
    }
    private val volumeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 50, 50, 50)
    }
    private val volumeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 200, 200, 60)
    }
    private val flashPaint = Paint().apply {
        color = Color.argb(160, 255, 255, 255)
    }

    // WHY: button affordance paints — S0040. Two bg variants encode active/inactive
    // state without per-frame Paint mutation. Border is a thin STROKE on top.
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 255, 255) // inactive / paused state
    }
    private val btnBgActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255) // active / playing state — more opaque
    }
    private val btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(128, 255, 255, 255) // #80FFFFFF border
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val tmpRect = RectF()

    // S0024 Phase 03: subtle 2-px stroked rounded-rect drawn around the hovered
    // element bounds. Subdued ARGB per strategic §3.1 #2 (must not distract).
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 120, 200, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    fun draw(state: VrHudState, canvas: Canvas, hoverId: Int = 0) {
        registry.beginFrame()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        // WHY: GL textures have Y=0 at the bottom (opposite to Android Canvas Y=0 at top).
        // glTexSubImage2D maps bitmap row 0 to the bottom of the GL texture, so without this
        // flip all content appears upside-down in the headset. Flipping around the horizontal
        // centre here corrects the orientation; all draw coordinates remain unchanged.
        canvas.save()
        canvas.scale(1f, -1f, width / 2f, height / 2f)
        val now = System.currentTimeMillis()

        drawProgressBar(state, canvas)
        drawPauseIcon(state, canvas)
        drawSeekOverlay(state, canvas)
        drawVolumeBar(state, canvas)
        drawZoomLabel(state, canvas)
        drawFileBadge(state, canvas)
        drawRepeatIcon(state, canvas)
        drawStereoModeLabel(state, canvas)
        drawFpsLabel(state, canvas)
        drawActionBadge(state, canvas, now)
        drawImmersiveBadge(state, canvas, now)
        drawBanner(state, canvas, now)
        drawRecenterFlash(state, canvas, now)
        // Hover highlight painted last so it overlays the element it belongs to.
        // Skipped when hoverId == 0 or the element is not registered this frame.
        if (hoverId != 0) {
            registry.boundsOf(hoverId)?.let { bounds ->
                canvas.drawRoundRect(bounds, 6f, 6f, hoverPaint)
            }
        }
        canvas.restore()
    }

    private fun drawProgressBar(state: VrHudState, canvas: Canvas) {
        if (state.positionMs < 0L || state.totalMs <= 0L) return
        val total = state.totalMs.coerceAtLeast(1L)
        val pos = state.positionMs.coerceIn(0L, total)
        val buf = state.bufferedMs.coerceIn(pos, total).takeIf { state.bufferedMs >= 0L } ?: pos

        val marginX = 48f
        val barH = 8f
        val barY = height - 32f
        val barW = width - marginX * 2f

        // Background
        tmpRect.set(marginX, barY, marginX + barW, barY + barH)
        canvas.drawRoundRect(tmpRect, 4f, 4f, progressBgPaint)
        registry.register(HUD_ELEMENT_SEEK_BAR, tmpRect, "seek", onSeekBarClick)
        // Buffered
        val bufW = barW * buf.toFloat() / total.toFloat()
        if (bufW > 0f) {
            tmpRect.set(marginX, barY, marginX + bufW, barY + barH)
            canvas.drawRoundRect(tmpRect, 4f, 4f, progressBufferedPaint)
        }
        // Position
        val posW = barW * pos.toFloat() / total.toFloat()
        if (posW > 0f) {
            tmpRect.set(marginX, barY, marginX + posW, barY + barH)
            canvas.drawRoundRect(tmpRect, 4f, 4f, progressPositionPaint)
        }
        // Time labels
        val posLabel = formatMs(pos)
        val totalLabel = formatMs(total)
        val labelY = barY - 8f
        canvas.drawText(posLabel, marginX, labelY, smallTextPaint)
        val totalWidth = smallTextPaint.measureText(totalLabel)
        canvas.drawText(totalLabel, marginX + barW - totalWidth, labelY, smallTextPaint)
    }

    private fun drawPauseIcon(state: VrHudState, canvas: Canvas) {
        val paused = state.isPaused ?: return
        val glyph = if (paused) "❚❚" else "▶"
        val cx = width / 2f
        val cy = height / 2f - 20f
        // WHY: glyph width varies between pause and play glyphs; measure each frame
        // to avoid a fixed rect that clips "❚❚" or leaves too much space for "▶".
        // Paint.measureText does not allocate — safe inside draw (see KDoc contract).
        val hw = iconPaint.measureText(glyph) / 2f + 20f
        tmpRect.set(cx - hw, cy - 70f, cx + hw, cy + 20f)
        // Active (playing) state → stronger bg opacity; inactive (paused) → dimmer.
        // Two separate Paint instances avoid mutating color on the hot path.
        val bg = if (paused) btnBgPaint else btnBgActivePaint
        canvas.drawRoundRect(tmpRect, 6f, 6f, bg)
        canvas.drawRoundRect(tmpRect, 6f, 6f, btnBorderPaint)
        // Register bounds for S0024 ray-input hit-testing.
        registry.register(HUD_ELEMENT_PLAY_PAUSE, tmpRect, "play_pause") {}
        canvas.drawText(glyph, cx, cy, iconPaint)
    }

    private fun drawSeekOverlay(state: VrHudState, canvas: Canvas) {
        val delta = state.seekDeltaSec ?: return
        val arrow = if (delta >= 0) "▶▶" else "◀◀"
        val sign = if (delta >= 0) "+" else ""
        val pos = if (state.positionMs >= 0L) formatMs(state.positionMs) else "--:--"
        val tot = if (state.totalMs > 0L) formatMs(state.totalMs) else "--:--"
        val text = "[$pos / $tot]  $sign${delta}s $arrow"
        val cx = width / 2f
        val y = height - 80f
        canvas.drawText(text, cx, y, centerTextPaint)
    }

    private fun drawVolumeBar(state: VrHudState, canvas: Canvas) {
        val percent = state.volumePercent ?: return
        val clamped = percent.coerceIn(0, 100)
        val barW = 16f
        val barH = 160f
        val x = width - 48f - barW
        val y = (height - barH) / 2f - 10f
        // Background
        tmpRect.set(x, y, x + barW, y + barH)
        canvas.drawRoundRect(tmpRect, 6f, 6f, volumeBgPaint)
        // Fill (bottom-up)
        val fillH = barH * clamped / 100f
        tmpRect.set(x, y + barH - fillH, x + barW, y + barH)
        canvas.drawRoundRect(tmpRect, 6f, 6f, volumeFillPaint)
        // Numeric label above
        val label = "$clamped%"
        val labelW = smallTextPaint.measureText(label)
        canvas.drawText(label, x + (barW - labelW) / 2f, y - 8f, smallTextPaint)
    }

    private fun drawZoomLabel(state: VrHudState, canvas: Canvas) {
        val factor = state.zoomFactor ?: return
        val text = String.format("×%.1f", factor)
        val cx = width / 2f
        val y = height / 2f + 36f
        canvas.drawText(text, cx, y, centerTextPaint)
    }

    private fun drawFileBadge(state: VrHudState, canvas: Canvas) {
        val name = state.fileLabel ?: return
        val idx = state.fileIndex ?: return
        val total = state.fileTotal ?: return
        val truncated = if (name.length > 40) name.take(37) + ".." else name
        val text = "$truncated  $idx / $total"
        val tw = textPaint.measureText(text)
        val pad = 16f
        val x = width - 24f - tw - pad
        val y = 12f
        // Pill bg
        tmpRect.set(x - pad, y, x + tw + pad, y + 36f)
        canvas.drawRoundRect(tmpRect, 18f, 18f, pillBgPaint)
        canvas.drawText(text, x, y + 26f, textPaint)
    }

    private fun drawRepeatIcon(state: VrHudState, canvas: Canvas) {
        val mode = state.repeatMode ?: return
        val glyph = when (mode) {
            RepeatMode.OFF -> "↻ OFF"
            RepeatMode.ONE -> "↻ 1"
            RepeatMode.ALL -> "↻ ALL"
        }
        val tw = smallTextPaint.measureText(glyph)
        val x = width - 24f - tw
        val y = 64f
        canvas.drawText(glyph, x, y, smallTextPaint)
    }

    private fun drawStereoModeLabel(state: VrHudState, canvas: Canvas) {
        val label = state.stereoModeLabel ?: return
        // Draw at the top-left so it doesn't overlap the file badge (top-right).
        canvas.drawText(label, 24f, 32f, smallTextPaint)
    }

    /** Top-right corner; drops below the file badge when present (S0006). */
    private fun drawFpsLabel(state: VrHudState, canvas: Canvas) {
        val fps = state.fps ?: return
        if (fps <= 0) return
        val text = "$fps FPS"
        val tw = smallTextPaint.measureText(text)
        val x = width - 24f - tw
        val y = if (state.fileLabel == null) 32f else 64f
        canvas.drawText(text, x, y, smallTextPaint)
    }

    private fun drawActionBadge(state: VrHudState, canvas: Canvas, now: Long) {
        val badge = state.actionBadge ?: return
        if (state.actionBadgeUntilMs <= now) return
        val glyph = when (badge) {
            ActionBadge.PREV_FILE -> "⏭​ prev"
            ActionBadge.NEXT_FILE -> "⏭ next"
            ActionBadge.REWIND -> "⏪ -10s"
            ActionBadge.FORWARD -> "⏩ +30s"
            ActionBadge.OPEN_CONTROLS -> "☰ controls"
            ActionBadge.REPEAT_TOGGLE -> "↻ repeat"
        }
        val cx = width / 2f
        val y = 48f
        canvas.drawText(glyph, cx, y, centerTextPaint)
    }

    private fun drawImmersiveBadge(state: VrHudState, canvas: Canvas, now: Long) {
        if (state.immersiveBadgeUntilMs <= now) return
        val cx = width / 2f
        val y = 84f
        canvas.drawText("Immersive", cx, y, centerTextPaint)
    }

    private fun drawBanner(state: VrHudState, canvas: Canvas, now: Long) {
        val text = state.bannerText ?: return
        if (state.bannerUntilMs <= now) return
        // Two-line wrap at 60 glyphs.
        val (line1, line2) = if (text.length > 60) {
            val cut = text.lastIndexOf(' ', 60).takeIf { it > 30 } ?: 60
            text.substring(0, cut) to text.substring(cut).trim()
        } else {
            text to ""
        }
        val cx = width / 2f
        val cy = height / 2f - 10f
        // Pill bg
        val w1 = centerTextPaint.measureText(line1)
        val w2 = centerTextPaint.measureText(line2)
        val pillW = maxOf(w1, w2) + 48f
        val pillH = if (line2.isEmpty()) 56f else 96f
        tmpRect.set(cx - pillW / 2f, cy - 36f, cx + pillW / 2f, cy - 36f + pillH)
        canvas.drawRoundRect(tmpRect, 18f, 18f, pillBgPaint)
        canvas.drawText(line1, cx, cy, centerTextPaint)
        if (line2.isNotEmpty()) canvas.drawText(line2, cx, cy + 38f, centerTextPaint)
    }

    private fun drawRecenterFlash(state: VrHudState, canvas: Canvas, now: Long) {
        if (state.recenterFlashUntilMs <= now) return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
    }

    private fun formatMs(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0L) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%d:%02d", m, s)
        }
    }

    companion object {
        const val HUD_ELEMENT_SEEK_BAR: Int = 1
        const val HUD_ELEMENT_PLAY_PAUSE: Int = 2
    }
}
