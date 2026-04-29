package com.sza.fastmediasorter.vr.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Pure Canvas painter for the interactive GL control panel (spec_vr-immersive-controls-panel
 * Phase 03). No allocations inside [draw].
 *
 * Layout at 1024×512:
 * ```
 * ┌──────────────────────────────────────────────────────────┐
 * │  [◀◀ -10s]  [◀ Prev]  [⏯]  [Next ▶]  [+30s ▶▶]        │  Row 1: nav
 * │  ─────────────────────────────────── (seek slider)       │  Row 2: seek bar
 * │  [00:03 / 01:22:05]           (time labels)              │  Row 3: time
 * │  [Vol -] [▓░░] [Vol +]  [Bright -] [▓░] [Bright +]     │  Row 4: vol + bright
 * │  [Speed: 1.0x]  [Track: RUS]  [Format: 360° SBS] [2D] [Exit] │  Row 5: meta + exit
 * └──────────────────────────────────────────────────────────┘
 * ```
 *
 * Each interactive area is described by a [PanelZone]. [zoneAt] resolves UV coordinates
 * (origin = top-left of the panel, +X right, +Y down) to the zone under the cursor.
 */
class VrInteractivePanelComposer(
    @Suppress("unused") private val context: Context,
    val width: Int = VrInteractivePanelRenderer.DEFAULT_WIDTH,
    val height: Int = VrInteractivePanelRenderer.DEFAULT_HEIGHT,
) {

    data class PanelZone(val id: Int, val bounds: RectF, val label: String)

    // ── Paint objects (no allocations in draw) ───────────────────────────────

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 16, 16, 20)
    }
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 50, 50, 60)
    }
    private val zoneHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 80, 130, 200)
    }
    private val zoneBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 200, 200, 220)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        color = Color.argb(200, 200, 200, 200)
        textSize = 18f
    }
    private val seekBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 60, 60, 60)
    }
    private val seekFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 80, 170, 240)
    }
    private val seekDragPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 200, 60)
    }

    private val tmpRect = RectF()

    // ── Zone layout ──────────────────────────────────────────────────────────

    private val w = width.toFloat()
    private val h = height.toFloat()

    // Row Y bands (in canvas pixels, Y=0 top).
    private val row1Top = 12f;  private val row1Bot = h * 0.22f
    private val row2Top = h * 0.23f; private val row2Bot = h * 0.42f
    private val row3Top = h * 0.43f; private val row3Bot = h * 0.55f
    private val row4Top = h * 0.56f; private val row4Bot = h * 0.74f
    private val row5Top = h * 0.76f; private val row5Bot = h - 12f

    private fun zone(id: Int, l: Float, t: Float, r: Float, b: Float, label: String) =
        PanelZone(id, RectF(l, t, r, b), label)

    private val btnW1 = w / 5f           // 5 nav buttons in row 1
    private val btnW4 = w / 6f           // vol/bright buttons (3+3)
    private val btnW5 = w / 5f           // 5 meta buttons in row 5 (S0031 П3: added Exit-to-2D)

    private val zones: List<PanelZone> = buildList {
        // Row 1 — nav
        add(zone(ZONE_SEEK_BACK, btnW1 * 0f, row1Top, btnW1 * 1f, row1Bot, "◀◀"))
        add(zone(ZONE_PREV,      btnW1 * 1f, row1Top, btnW1 * 2f, row1Bot, "◀ Prev"))
        add(zone(ZONE_PLAY_PAUSE,btnW1 * 2f, row1Top, btnW1 * 3f, row1Bot, "⏯"))
        add(zone(ZONE_NEXT,      btnW1 * 3f, row1Top, btnW1 * 4f, row1Bot, "Next ▶"))
        add(zone(ZONE_SEEK_FWD,  btnW1 * 4f, row1Top, w,           row1Bot, "▶▶"))
        // Row 2 — seek slider (full width)
        add(zone(ZONE_SEEK_SLIDER, 0f, row2Top, w, row2Bot, ""))
        // Row 4 — vol + bright
        add(zone(ZONE_VOL_DOWN,   btnW4 * 0f, row4Top, btnW4 * 1f, row4Bot, "Vol −"))
        add(zone(ZONE_VOL_UP,     btnW4 * 2f, row4Top, btnW4 * 3f, row4Bot, "Vol +"))
        add(zone(ZONE_BRIGHT_DOWN,btnW4 * 3f, row4Top, btnW4 * 4f, row4Bot, "Bri −"))
        add(zone(ZONE_BRIGHT_UP,  btnW4 * 5f, row4Top, w,           row4Bot, "Bri +"))
        // Row 5 — meta + exit. S0031 П3: split old single Exit into "To 2D" (panel mode) + "Exit" (full VR exit).
        add(zone(ZONE_SPEED,      btnW5 * 0f, row5Top, btnW5 * 1f, row5Bot, "Speed"))
        add(zone(ZONE_TRACK,      btnW5 * 1f, row5Top, btnW5 * 2f, row5Bot, "Track"))
        add(zone(ZONE_FORMAT,     btnW5 * 2f, row5Top, btnW5 * 3f, row5Bot, "Format"))
        add(zone(ZONE_EXIT_TO_2D, btnW5 * 3f, row5Top, btnW5 * 4f, row5Bot, "To 2D"))
        add(zone(ZONE_EXIT,       btnW5 * 4f, row5Top, w,           row5Bot, "Exit"))
    }

    fun getAllZones(): List<PanelZone> = zones

    /**
     * Resolve a UV coordinate (u, v ∈ [0,1]) on the panel surface to the zone at that point.
     * Returns null when the point is between zones or outside the panel.
     */
    fun zoneAt(u: Float, v: Float): PanelZone? {
        val px = u * w
        val py = v * h
        return zones.firstOrNull { it.bounds.contains(px, py) }
    }

    fun draw(state: VrHudState, canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        // WHY: GL textures have Y=0 at the bottom; Y-flip corrects upside-down rendering.
        canvas.save()
        canvas.scale(1f, -1f, w / 2f, h / 2f)

        // Background pill
        tmpRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(tmpRect, 32f, 32f, bgPaint)

        // Draw non-seek zones as buttons
        for (zone in zones) {
            if (zone.id == ZONE_SEEK_SLIDER) continue
            val fill = if (state.hoveredZoneId == zone.id) zoneHoverPaint else zonePaint
            tmpRect.set(
                zone.bounds.left + 4f, zone.bounds.top + 4f,
                zone.bounds.right - 4f, zone.bounds.bottom - 4f,
            )
            canvas.drawRoundRect(tmpRect, 12f, 12f, fill)
            canvas.drawRoundRect(tmpRect, 12f, 12f, zoneBorderPaint)
            val label = resolveZoneLabel(zone.id, state)
            val cx = (zone.bounds.left + zone.bounds.right) / 2f
            val cy = (zone.bounds.top + zone.bounds.bottom) / 2f + textPaint.textSize / 3f
            canvas.drawText(label, cx, cy, textPaint)
        }

        // Seek slider
        val seekZone = zones.firstOrNull { it.id == ZONE_SEEK_SLIDER }
        if (seekZone != null) {
            val sliderPad = 16f
            val sl = seekZone.bounds.left + sliderPad
            val sr = seekZone.bounds.right - sliderPad
            val sy = (seekZone.bounds.top + seekZone.bounds.bottom) / 2f
            val barH = 10f
            tmpRect.set(sl, sy - barH / 2f, sr, sy + barH / 2f)
            canvas.drawRoundRect(tmpRect, 5f, 5f, seekBgPaint)

            val fraction = when {
                state.seekDragFraction >= 0f -> state.seekDragFraction
                state.positionMs >= 0L && state.totalMs > 0L ->
                    state.positionMs.toFloat() / state.totalMs.toFloat()
                else -> 0f
            }
            val fillPaint = if (state.seekDragFraction >= 0f) seekDragPaint else seekFillPaint
            val fillW = (sr - sl) * fraction.coerceIn(0f, 1f)
            if (fillW > 0f) {
                tmpRect.set(sl, sy - barH / 2f, sl + fillW, sy + barH / 2f)
                canvas.drawRoundRect(tmpRect, 5f, 5f, fillPaint)
            }

            // Time labels in row 3
            if (state.positionMs >= 0L && state.totalMs > 0L) {
                val posLabel = formatMs(state.positionMs)
                val totalLabel = formatMs(state.totalMs)
                val labelY = row3Top + (row3Bot - row3Top) * 0.7f
                canvas.drawText(posLabel, sl, labelY, smallTextPaint)
                val tw = smallTextPaint.measureText(totalLabel)
                canvas.drawText(totalLabel, sr - tw, labelY, smallTextPaint)
            }
        }

        canvas.restore()
    }

    private fun resolveZoneLabel(zoneId: Int, state: VrHudState): String = when (zoneId) {
        ZONE_SEEK_BACK  -> "◀◀ −10s"
        ZONE_PREV       -> "◀ Prev"
        ZONE_PLAY_PAUSE -> if (state.isPaused == true) "▶" else "⏸"
        ZONE_NEXT       -> "Next ▶"
        ZONE_SEEK_FWD   -> "+30s ▶▶"
        ZONE_VOL_DOWN   -> "Vol −"
        ZONE_VOL_UP     -> "Vol +"
        ZONE_BRIGHT_DOWN-> "Bri −"
        ZONE_BRIGHT_UP  -> "Bri +"
        ZONE_SPEED      -> "Speed: ${state.playbackSpeed?.let { "${it}x" } ?: "1.0x"}"
        ZONE_TRACK      -> "Track: ${state.audioTrackLabel ?: "—"}"
        ZONE_FORMAT     -> state.stereoModeLabel ?: "—"
        ZONE_EXIT_TO_2D -> "To 2D"
        ZONE_EXIT       -> "Exit"
        else -> ""
    }

    private fun formatMs(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L; val m = (total % 3600L) / 60L; val s = total % 60L
        return if (h > 0L) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    companion object {
        const val ZONE_SEEK_BACK   = 4
        const val ZONE_PREV        = 1
        const val ZONE_PLAY_PAUSE  = 3
        const val ZONE_NEXT        = 2
        const val ZONE_SEEK_FWD    = 5
        const val ZONE_SEEK_SLIDER = 6
        const val ZONE_VOL_DOWN    = 7
        const val ZONE_VOL_UP      = 8
        const val ZONE_BRIGHT_DOWN = 9
        const val ZONE_BRIGHT_UP   = 10
        const val ZONE_SPEED       = 11
        const val ZONE_TRACK       = 12
        const val ZONE_FORMAT      = 13
        const val ZONE_EXIT        = 14
        // S0031 П3: exit to VR panel (2D view within headset) without fully leaving VR.
        const val ZONE_EXIT_TO_2D  = 15
    }
}
