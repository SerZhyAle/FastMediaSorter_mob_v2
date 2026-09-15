package com.sza.fastmediasorter.broadcast

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.broadcast.BroadcastEntryActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * S2818: Quick Settings tile for Live Broadcast. A conductor, not a toggle (strategic ADR-2): every
 * tap opens the confirmation screen, which owns the permission prompts and the start decision - a
 * service can neither request RECORD_AUDIO nor start a microphone session from the background.
 *
 * Registered only in src/broadcastSource/AndroidManifest.xml (standard/noLegal/legacy), so flavors
 * without broadcast never see the tile and the availability check lives on the screen itself.
 */
@AndroidEntryPoint
class BroadcastTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_broadcast_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_display)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, BroadcastEntryActivity::class.java).apply {
            action = BroadcastEntryActivity.ACTION_OPEN_BROADCAST_ENTRY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
