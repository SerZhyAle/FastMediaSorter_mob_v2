package com.sza.fastmediasorter.screencapture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * S0621. Standard (Play) flavor controller for the edge-gesture screenshot. Single capture path:
 * the strip is hosted by [OverlayHostService] over the draw-over-apps permission, and each matched
 * gesture launches the MediaProjection consent dialog. There is no silent accessibility path on
 * standard - that stays a noLegal exclusive - so [isFallbackCaptureAvailable] is false, which also
 * hides the accessibility-shortcut settings rows.
 */
class ScreenGestureOverlayControllerImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: Lazy<SettingsRepository>
) : ScreenGestureOverlayController {

    private val appContext = context.applicationContext

    override fun isOverlayPermissionGranted(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    override fun permissionSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )

    override fun permissionRationaleResId(): Int =
        R.string.screenshot_overlay_permission_rationale

    override fun isFallbackCaptureAvailable(): Boolean = false

    override fun fallbackPermissionSettingsIntent(context: Context): Intent =
        permissionSettingsIntent(context)

    override fun setEnabled(enabled: Boolean) {
        if (enabled && Settings.canDrawOverlays(appContext)) {
            OverlayHostService.start(appContext, readStripVisible())
        } else {
            OverlayHostService.stop(appContext)
        }
    }

    override fun setStripVisible(visible: Boolean) {
        // Only meaningful while the host is running; re-issuing start refreshes the live strip colour.
        if (isEnabled() && Settings.canDrawOverlays(appContext)) {
            OverlayHostService.start(appContext, visible)
        }
    }

    override fun isEnabled(): Boolean = runBlocking {
        settingsRepository.get().getSettings().first().gestureOverlayEnabled
    }

    private fun readStripVisible(): Boolean = runBlocking {
        settingsRepository.get().getSettings().first().screenshotGestureStripVisible
    }
}
