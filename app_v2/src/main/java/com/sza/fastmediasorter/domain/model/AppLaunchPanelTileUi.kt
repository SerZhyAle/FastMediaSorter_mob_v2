package com.sza.fastmediasorter.domain.model

import android.graphics.drawable.Drawable

/** Number of fixed slots in the locked-view panel grid (3x5 portrait / 5x3 landscape). */
const val APP_LAUNCH_PANEL_SLOT_COUNT = 15

/**
 * Render model for one panel cell. A resolved app tile has [isEmpty] = false with a non-null
 * [icon]/[label]; an empty slot has [isEmpty] = true, a blank [label] (the UI supplies the
 * placeholder caption from resources) and a null [icon].
 */
data class AppLaunchPanelTileUi(
    val slotIndex: Int,
    val type: AppLaunchPanelTileType,
    val targetId: String?,
    val label: String,
    val icon: Drawable?,
    val isEmpty: Boolean,
    /**
     * S1124: true when [icon] is a monochrome glyph that must be tinted to a theme on-surface color to
     * stay legible on the light tile (feature / OS-shortcut routes, the shared stream cast glyph). False
     * for full-color assets that must keep their own colors: app launcher icons (own/external) and the
     * colored `ic_resource_*` source badges. Decided at resolve time where the icon source is known -
     * tile type alone is not enough (a Resource route can be either a colored badge or the cast glyph).
     */
    val tintable: Boolean = false,
)
