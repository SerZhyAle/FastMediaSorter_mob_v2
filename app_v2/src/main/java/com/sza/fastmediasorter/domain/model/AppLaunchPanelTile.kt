package com.sza.fastmediasorter.domain.model

/**
 * One configured tile occupying a fixed grid slot in the app-launch panel.
 * [targetId] holds a package name for [AppLaunchPanelTileType.EXTERNAL_APP], a route key for
 * [AppLaunchPanelTileType.INTERNAL_ROUTE], and is null for [AppLaunchPanelTileType.OWN_APP].
 * [labelOverride] is a user-set caption; null means use the resolved label.
 */
data class AppLaunchPanelTile(
    val slotIndex: Int,
    val type: AppLaunchPanelTileType,
    val targetId: String?,
    val labelOverride: String?,
    val addedAt: Long
)
