package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.util.TypedValue
import timber.log.Timber

/**
 * Persistent settings for the draw editor (S0192 Phase 02).
 *
 * Synchronous mirror of the four user-facing draw editor preferences, kept in
 * its own SharedPreferences file so it can be queried from the View layer
 * without DataStore async overhead.
 *
 * Keys:
 * - `draw_editor_last_color` - ARGB Int of the last active color (default Red).
 * - `draw_brush_size` - Brush width 1..36 (default 12). Eraser is always 2× this.
 * - `draw_text_size` - Ordinal: 0=Small, 1=Medium, 2=Large (default Medium).
 * - `draw_opacity_pct` - One of 0/25/50/75/100 (default 100).
 *
 * Derived helpers:
 * - [textSizePx] - converts the text-size ordinal to a paint-ready px value.
 * - [opacityAlpha] - converts the opacity percentage to a 0..255 alpha byte.
 */
object DrawEditorPrefs {

    private const val FILE_NAME = "draw_editor"

    private const val KEY_LAST_COLOR = "draw_editor_last_color"
    private const val KEY_CUSTOM_COLOR = "draw_editor_custom_color"
    private const val KEY_BRUSH_SIZE = "draw_brush_size"
    private const val KEY_TEXT_SIZE = "draw_text_size"
    private const val KEY_OPACITY_PCT = "draw_opacity_pct"

    private const val DEFAULT_LAST_COLOR_ARGB = 0xFFE53935.toInt() // Red
    private const val DEFAULT_BRUSH_SIZE = 12
    private const val DEFAULT_TEXT_SIZE_ORDINAL = 1               // Medium
    private const val DEFAULT_OPACITY_PCT = 100

    // ── Last active color ─────────────────────────────────────────────────

    fun getLastColor(ctx: Context): Int = prefs(ctx).getInt(KEY_LAST_COLOR, DEFAULT_LAST_COLOR_ARGB)

    fun setLastColor(ctx: Context, argb: Int) {
        prefs(ctx).edit().putInt(KEY_LAST_COLOR, argb).apply()
    }

    // ── Custom swatch colour ──────────────────────────────────────────────
    //
    // Tracks the colour shown by the toolbar's "Custom" swatch. Falls back to
    // the user's last active colour on first run so the swatch is never empty.

    fun getCustomColor(ctx: Context): Int =
        prefs(ctx).getInt(KEY_CUSTOM_COLOR, getLastColor(ctx))

    fun setCustomColor(ctx: Context, argb: Int) {
        prefs(ctx).edit().putInt(KEY_CUSTOM_COLOR, argb).apply()
    }

    // ── Brush size (1..36) ────────────────────────────────────────────────

    fun getBrushSize(ctx: Context): Int =
        prefs(ctx).getInt(KEY_BRUSH_SIZE, DEFAULT_BRUSH_SIZE).coerceIn(1, 36)

    fun setBrushSize(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt(KEY_BRUSH_SIZE, value.coerceIn(1, 36)).apply()
    }

    // ── Text size (ordinal 0/1/2) ─────────────────────────────────────────

    fun getTextSizeOrdinal(ctx: Context): Int =
        prefs(ctx).getInt(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE_ORDINAL).coerceIn(0, 2)

    fun setTextSizeOrdinal(ctx: Context, ordinal: Int) {
        prefs(ctx).edit().putInt(KEY_TEXT_SIZE, ordinal.coerceIn(0, 2)).apply()
    }

    // ── Opacity (0/25/50/75/100) ──────────────────────────────────────────

    fun getOpacityPct(ctx: Context): Int =
        prefs(ctx).getInt(KEY_OPACITY_PCT, DEFAULT_OPACITY_PCT)

    fun setOpacityPct(ctx: Context, pct: Int) {
        // Snap to nearest allowed step to keep storage canonical
        val snapped = when {
            pct <= 12 -> 0
            pct <= 37 -> 25
            pct <= 62 -> 50
            pct <= 87 -> 75
            else -> 100
        }
        prefs(ctx).edit().putInt(KEY_OPACITY_PCT, snapped).apply()
    }

    // ── Derived ───────────────────────────────────────────────────────────

    /**
     * Returns the text size in px ready for `Paint.textSize`.
     * Mapping: Small=14sp, Medium=20sp, Large=30sp (strategic §2.5).
     */
    fun textSizePx(ctx: Context): Float {
        val sp = when (getTextSizeOrdinal(ctx)) {
            0 -> 14f
            1 -> 20f
            2 -> 30f
            else -> 20f
        }
        // S0218: replace deprecated DisplayMetrics.scaledDensity (deprecated since API 34)
        // with TypedValue.applyDimension(COMPLEX_UNIT_SP, sp, metrics) - same formula
        // sp * scaledDensity is computed internally, just without direct field access.
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            ctx.resources.displayMetrics
        )
    }

    /**
     * Returns the alpha byte (0..255) for the current opacity setting.
     * Mapping (strategic §2.5): 0%=0, 25%=64, 50%=128, 75%=191, 100%=255.
     */
    fun opacityAlpha(ctx: Context): Int = when (getOpacityPct(ctx)) {
        0 -> 0
        25 -> 64
        50 -> 128
        75 -> 191
        100 -> 255
        else -> 255
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
