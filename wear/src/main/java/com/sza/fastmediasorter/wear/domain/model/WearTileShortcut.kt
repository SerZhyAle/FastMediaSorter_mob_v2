package com.sza.fastmediasorter.wear.domain.model

import androidx.annotation.DrawableRes

/**
 * S2511: one tappable cell of a shortcut-grid tile.
 *
 * [contentDescription] is resolved to text here rather than left as a resource id because it is the only
 * thing a screen reader can announce: the grid draws glyphs alone, so a cell that carried just an icon
 * would be unreachable without sight.
 */
data class WearTileShortcut(
    @DrawableRes val iconResId: Int,
    val contentDescription: String,
    val launchTarget: WearLaunchTarget
)
