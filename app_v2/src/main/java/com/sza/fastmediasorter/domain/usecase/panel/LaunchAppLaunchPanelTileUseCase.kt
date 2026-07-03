package com.sza.fastmediasorter.domain.usecase.panel

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.panel.AppLaunchPanelRouteIntents
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi
import com.sza.fastmediasorter.domain.model.panel.AppLaunchPanelRouteTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/** Launches the target behind a panel tile. Returns whether an activity was started. */
class LaunchAppLaunchPanelTileUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolveRouteAvailability: ResolvePanelRouteAvailabilityUseCase,
) {
    suspend fun launch(tile: AppLaunchPanelTileUi): Boolean = when (tile.type) {
        AppLaunchPanelTileType.OWN_APP -> launchPackage(context.packageName)
        AppLaunchPanelTileType.EXTERNAL_APP -> tile.targetId?.let { launchPackage(it) } ?: false
        AppLaunchPanelTileType.INTERNAL_ROUTE -> launchInternalRoute(tile)
        AppLaunchPanelTileType.RESERVED -> false
    }

    private suspend fun launchInternalRoute(tile: AppLaunchPanelTileUi): Boolean {
        return when (val target = AppLaunchPanelRouteTarget.decode(tile.targetId)) {
            is AppLaunchPanelRouteTarget.Feature -> {
                val route = InternalRouteCatalog.byKey(target.routeKey) ?: return false
                val availability = resolveRouteAvailability(target.routeKey)
                when {
                    availability.isLaunchable -> startIntent(route.intent(context))
                    // Compiled-in but disabled by a setting: open the relevant setting, not a dead launch (§6.1).
                    availability.availableInBuild ->
                        route.settingsIntent?.let { startIntent(it(context)) } ?: false
                    else -> false
                }
            }
            is AppLaunchPanelRouteTarget.Resource ->
                startIntent(AppLaunchPanelRouteIntents.resource(context, target.resourceId))
            is AppLaunchPanelRouteTarget.OsShortcut -> {
                val osTarget = OsShortcutCatalog.byKey(target.targetKey) ?: return false
                if (!OsShortcutCatalog.isResolvable(context, target.targetKey)) return false
                startIntent(osTarget.intent(context))
            }
            null -> false
        }
    }

    private fun launchPackage(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Timber.i("App launch panel: no launch intent for %s", packageName)
            return false
        }
        return startIntent(intent)
    }

    private fun startIntent(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            Timber.w(it, "App launch panel: failed to launch %s", intent.component ?: intent.action)
            false
        }
    }
}
