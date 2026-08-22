package com.sza.fastmediasorter.screencapture

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.screencapture.AccessibilityServiceControl
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Android Quick Settings Tile for noLegal flavor (API 24+).
 *
 * Appears in status bar pulldown shade. Tapping while active immediately invokes
 * [AccessibilityServiceControl.disableSelf] to unbind the Accessibility Service before
 * launching banking or security applications.
 */
@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class NoLegalAccessibilityTileService : TileService() {

    @Inject
    lateinit var accessibilityControl: AccessibilityServiceControl

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (accessibilityControl.isServiceActive()) {
            if (accessibilityControl.disableSelf()) {
                Toast.makeText(
                    this,
                    getString(R.string.accessibility_service_status_disabled),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivityAndCollapse(intent)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isActive = accessibilityControl.isServiceActive()
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.qs_tile_accessibility)
        tile.updateTile()
    }
}
