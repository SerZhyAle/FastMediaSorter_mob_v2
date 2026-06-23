package com.sza.fastmediasorter.domain.usecase.panel

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/** Launches the target behind a panel tile. Returns whether an activity was started. */
class LaunchAppLaunchPanelTileUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun launch(tile: AppLaunchPanelTileUi): Boolean {
        val packageName = when (tile.type) {
            AppLaunchPanelTileType.OWN_APP -> context.packageName
            AppLaunchPanelTileType.EXTERNAL_APP -> tile.targetId
            // v1 ships no internal routes.
            AppLaunchPanelTileType.INTERNAL_ROUTE,
            AppLaunchPanelTileType.RESERVED -> null
        } ?: return false

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Timber.i("App launch panel: no launch intent for %s", packageName)
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            Timber.w(it, "App launch panel: failed to launch %s", packageName)
            false
        }
    }
}
