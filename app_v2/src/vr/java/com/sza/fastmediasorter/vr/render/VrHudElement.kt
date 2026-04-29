package com.sza.fastmediasorter.vr.render

import android.graphics.RectF

/**
 * Source of truth for "what is at HUD pixel (x, y)" — one entry per interactive
 * element registered by [VrHudSceneComposer] during a draw pass.
 *
 * Element data is intentionally tiny and callback-free: callbacks live in
 * [VrHudElementRegistry] so per-frame re-registration stays GC-cheap.
 *
 * @property id     Stable element identifier (≥ 0; reserve 0 as "none").
 * @property bounds Pixel rectangle on the HUD canvas, origin top-left.
 * @property label  Debug-only short tag (≤ 32 chars).
 */
data class VrHudElement(
    val id: Int,
    val bounds: RectF,
    val label: String,
)
