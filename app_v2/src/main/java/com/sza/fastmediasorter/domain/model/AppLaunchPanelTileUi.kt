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
    val isEmpty: Boolean
)
