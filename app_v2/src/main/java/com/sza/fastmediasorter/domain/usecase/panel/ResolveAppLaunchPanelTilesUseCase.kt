package com.sza.fastmediasorter.domain.usecase.panel

import android.content.Context
import com.sza.fastmediasorter.domain.model.APP_LAUNCH_PANEL_SLOT_COUNT
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTile
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi
import com.sza.fastmediasorter.domain.repository.AppLaunchPanelRepository
import com.sza.fastmediasorter.util.getApplicationInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Maps the stored tile list into a fixed 15-cell render list, resolving label/icon/availability on
 * IO. Unavailable external packages render as empty slots (soft degrade, strategic S0623 §6.3).
 */
class ResolveAppLaunchPanelTilesUseCase @Inject constructor(
    private val repository: AppLaunchPanelRepository,
    @ApplicationContext private val context: Context
) {
    operator fun invoke(): Flow<List<AppLaunchPanelTileUi>> =
        repository.observeTiles()
            .map { tiles -> resolve(tiles) }
            .flowOn(Dispatchers.IO)

    private fun resolve(tiles: List<AppLaunchPanelTile>): List<AppLaunchPanelTileUi> {
        val bySlot = tiles.associateBy { it.slotIndex }
        return (0 until APP_LAUNCH_PANEL_SLOT_COUNT).map { slot ->
            val tile = bySlot[slot]
            if (tile == null) emptySlot(slot) else resolveTile(tile) ?: emptySlot(slot)
        }
    }

    private fun resolveTile(tile: AppLaunchPanelTile): AppLaunchPanelTileUi? {
        val pm = context.packageManager
        return when (tile.type) {
            AppLaunchPanelTileType.OWN_APP -> {
                val appInfo = pm.getApplicationInfoCompat(context.packageName, 0)
                AppLaunchPanelTileUi(
                    slotIndex = tile.slotIndex,
                    type = tile.type,
                    targetId = null,
                    label = tile.labelOverride ?: pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo),
                    isEmpty = false
                )
            }
            AppLaunchPanelTileType.EXTERNAL_APP -> {
                val packageName = tile.targetId ?: return null
                val appInfo = runCatching { pm.getApplicationInfoCompat(packageName, 0) }.getOrNull()
                    ?: return null
                // Hidden when not launchable (uninstalled / no launcher entry): soft degrade.
                if (pm.getLaunchIntentForPackage(packageName) == null) return null
                AppLaunchPanelTileUi(
                    slotIndex = tile.slotIndex,
                    type = tile.type,
                    targetId = packageName,
                    label = tile.labelOverride ?: pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo),
                    isEmpty = false
                )
            }
            // v1 ships no internal routes; modelled-but-unused types render as empty slots.
            AppLaunchPanelTileType.INTERNAL_ROUTE,
            AppLaunchPanelTileType.RESERVED -> null
        }
    }

    private fun emptySlot(slot: Int): AppLaunchPanelTileUi =
        AppLaunchPanelTileUi(
            slotIndex = slot,
            type = AppLaunchPanelTileType.RESERVED,
            targetId = null,
            label = "",
            icon = null,
            isEmpty = true
        )
}
