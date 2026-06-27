package com.sza.fastmediasorter.core

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.applaunchpanel.AppLaunchPanelActivity
import timber.log.Timber

/**
 * S0683: Quick Settings tile that opens the Quick Launch Panel - a second entry point besides the
 * left-edge gesture, so the panel stays reachable in configurations where the edge-gesture overlay
 * is disabled (standard by default). Mirrors the launch path of
 * [com.sza.fastmediasorter.core.screencapture.ScreenshotGestureActionDispatcher] (start
 * [AppLaunchPanelActivity] with NEW_TASK; the activity is singleTask + excludeFromRecents and floats
 * over the foreground app).
 */
class AppLaunchPanelTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_launch_panel_title)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_view_grid)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        Timber.d("S0683: AppLaunchPanel QS tile clicked - launching panel")
        val intent = Intent(this, AppLaunchPanelActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
