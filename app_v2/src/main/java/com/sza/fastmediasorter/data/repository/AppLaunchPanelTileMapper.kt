package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.AppLaunchPanelTileEntity
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTile
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType

/** Unknown persisted type strings degrade to [AppLaunchPanelTileType.RESERVED] (forward-compatible). */
fun AppLaunchPanelTileEntity.toDomain(): AppLaunchPanelTile =
    AppLaunchPanelTile(
        slotIndex = slotIndex,
        type = AppLaunchPanelTileType.fromName(type, AppLaunchPanelTileType.RESERVED),
        targetId = targetId,
        labelOverride = labelOverride,
        addedAt = addedAt
    )

fun AppLaunchPanelTile.toEntity(): AppLaunchPanelTileEntity =
    AppLaunchPanelTileEntity(
        slotIndex = slotIndex,
        type = type.name,
        targetId = targetId,
        labelOverride = labelOverride,
        addedAt = addedAt
    )
