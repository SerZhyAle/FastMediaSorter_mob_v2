package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * S0623: a configured app-launch-panel tile. [slotIndex] is the stable 0..14 grid position
 * (locked view); absence of a row for an index = empty slot. [type] is stored as the
 * AppLaunchPanelTileType.name string so new tile kinds never force a schema migration.
 */
@Entity(tableName = "app_launch_panel_tiles")
data class AppLaunchPanelTileEntity(
    @PrimaryKey
    val slotIndex: Int,
    val type: String,
    val targetId: String?,
    val labelOverride: String?,
    val addedAt: Long
)
