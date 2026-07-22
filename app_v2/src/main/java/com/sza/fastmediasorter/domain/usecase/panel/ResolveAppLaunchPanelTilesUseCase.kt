package com.sza.fastmediasorter.domain.usecase.panel

import android.content.Context
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.core.panel.ResourceTypeIconMap
import com.sza.fastmediasorter.domain.model.APP_LAUNCH_PANEL_SLOT_COUNT
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTile
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi
import com.sza.fastmediasorter.domain.model.panel.AppLaunchPanelRouteTarget
import com.sza.fastmediasorter.domain.repository.AppLaunchPanelRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.util.getApplicationInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Maps the stored tile list into a fixed 15-cell render list, resolving label/icon/availability on
 * IO. Unavailable targets (uninstalled app, deleted resource, unresolvable OS intent, feature absent
 * from the build) render as empty slots (soft degrade, strategic S0623 §6.3 / S0663 §5.1.B).
 */
class ResolveAppLaunchPanelTilesUseCase @Inject constructor(
    private val repository: AppLaunchPanelRepository,
    private val resourceRepository: ResourceRepository,
    private val resolveRouteAvailability: ResolvePanelRouteAvailabilityUseCase,
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(): Flow<List<AppLaunchPanelTileUi>> =
        repository.observeTiles()
            .map { tiles -> resolve(tiles) }
            .flowOn(Dispatchers.IO)

    private suspend fun resolve(tiles: List<AppLaunchPanelTile>): List<AppLaunchPanelTileUi> {
        val bySlot = tiles.associateBy { it.slotIndex }
        return (0 until APP_LAUNCH_PANEL_SLOT_COUNT).map { slot ->
            val tile = bySlot[slot]
            if (tile == null) emptySlot(slot) else resolveTile(tile) ?: emptySlot(slot)
        }
    }

    private suspend fun resolveTile(tile: AppLaunchPanelTile): AppLaunchPanelTileUi? {
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
                    isEmpty = false,
                    // App launcher icon is full-color; never tint it.
                    tintable = false,
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
                    isEmpty = false,
                    // External app launcher icon is full-color; never tint it.
                    tintable = false,
                )
            }
            AppLaunchPanelTileType.INTERNAL_ROUTE -> resolveInternalRoute(tile)
            // Empty-slot sentinel: rendered as an empty cell.
            AppLaunchPanelTileType.RESERVED -> null
        }
    }

    private suspend fun resolveInternalRoute(tile: AppLaunchPanelTile): AppLaunchPanelTileUi? {
        return when (val target = AppLaunchPanelRouteTarget.decode(tile.targetId)) {
            is AppLaunchPanelRouteTarget.Feature -> {
                val route = InternalRouteCatalog.byKey(target.routeKey) ?: return null
                // Degrade a feature that is not compiled into this build.
                if (!resolveRouteAvailability(target.routeKey).availableInBuild) return null
                // Feature glyphs are monochrome (ic_calculator, ic_cast, ..) - tint to stay legible.
                tileUi(tile, context.getString(route.labelRes), route.iconRes, tintable = true)
            }
            is AppLaunchPanelRouteTarget.OsShortcut -> {
                val osTarget = OsShortcutCatalog.byKey(target.targetKey) ?: return null
                if (!OsShortcutCatalog.isResolvable(context, target.targetKey)) return null
                // OS-shortcut glyphs are monochrome (ic_settings, ic_wifi, ..) - tint to stay legible.
                tileUi(tile, context.getString(osTarget.labelRes), osTarget.iconRes, tintable = true)
            }
            is AppLaunchPanelRouteTarget.Resource -> {
                val resource = resourceRepository.getResourceById(target.resourceId) ?: return null
                // Resource badges are full-color per source (green/blue/..); only the cast glyph is mono.
                val iconRes = ResourceTypeIconMap.iconFor(resource.type)
                tileUi(tile, resource.name, iconRes, tintable = ResourceTypeIconMap.isMonochrome(resource.type))
            }
            null -> null
        }
    }

    private fun tileUi(
        tile: AppLaunchPanelTile,
        label: String,
        iconRes: Int,
        tintable: Boolean,
    ): AppLaunchPanelTileUi =
        AppLaunchPanelTileUi(
            slotIndex = tile.slotIndex,
            type = AppLaunchPanelTileType.INTERNAL_ROUTE,
            targetId = tile.targetId,
            label = tile.labelOverride ?: label,
            icon = ContextCompat.getDrawable(context, iconRes),
            isEmpty = false,
            tintable = tintable,
        )

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
