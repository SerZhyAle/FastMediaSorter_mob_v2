package com.sza.fastmediasorter.screencapture

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import timber.log.Timber

/**
 * S0672: Quick Settings tile fallback that triggers the standard MediaProjection screen capture.
 * One tap launches [ScreenCaptureConsentActivity] for the user-configured DOWN gesture action, with
 * no persistent overlay and no specialUse foreground service - the Play-safe contingency trigger used
 * if the edge-strip specialUse declaration is rejected. Per-direction / open-app / panel access stays
 * on the strip primary path.
 */
class ScreenshotGestureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_screenshot_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification_screen_capture)
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        Timber.d("S0672: QS tile clicked - launching capture (DOWN action)")
        // Plain TileService (no Hilt): a TileService lifecycle does not fit the standard Hilt scopes, and
        // all capture/MediaProjection work already lives in ScreenCaptureConsentActivity. The tile reuses
        // the user's configured DOWN-direction action - it is the single-tap equivalent of the down-swipe.
        val intent = Intent(this, ScreenCaptureConsentActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(ScreenCaptureConsentActivity.EXTRA_GESTURE_DIRECTION, ScreenshotGestureDirection.DOWN.name)
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
