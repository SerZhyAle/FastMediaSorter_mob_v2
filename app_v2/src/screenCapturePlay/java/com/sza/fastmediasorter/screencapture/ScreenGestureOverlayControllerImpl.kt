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
import timber.log.Timber
import javax.inject.Inject

/**
 * S0418. Play-flavor (standard/photos) capture controller: a single MediaProjection path for the
 * whole minSdk 26+ range. Unlike the noLegal impl there is no accessibility branch - the silent
 * AccessibilityService.takeScreenshot path is deliberately excluded from Play builds (policy risk),
 * so capture always goes through the overlay strip + per-shot MediaProjection consent.
 */
class ScreenGestureOverlayControllerImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: Lazy<SettingsRepository>
) : ScreenGestureOverlayController {

    private val appContext = context.applicationContext

    override fun isOverlayPermissionGranted(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    override fun permissionSettingsIntent(context: Context): Intent =
        overlaySettingsIntent(context)

    override fun permissionRationaleResId(): Int =
        R.string.screenshot_overlay_permission_rationale

    // Only one capture path exists on Play flavors, so no "old method" choice is offered.
    override fun isFallbackCaptureAvailable(): Boolean = false

    override fun fallbackPermissionSettingsIntent(context: Context): Intent =
        overlaySettingsIntent(context)

    private fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )

    override fun setEnabled(enabled: Boolean) {
        Timber.d("S0418: Play capture controller setEnabled=%b (MediaProjection path, no a11y)", enabled)
        if (enabled && Settings.canDrawOverlays(appContext)) {
            OverlayHostService.start(appContext)
        } else {
            OverlayHostService.stop(appContext)
        }
    }

    override fun isEnabled(): Boolean = runBlocking {
        settingsRepository.get().getSettings().first().gestureOverlayEnabled
    }
}
