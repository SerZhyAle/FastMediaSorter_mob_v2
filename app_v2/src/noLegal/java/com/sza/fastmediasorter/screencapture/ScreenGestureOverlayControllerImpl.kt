package com.sza.fastmediasorter.screencapture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * S0405. Two capture paths, chosen at runtime by API level:
 * - API 30+ : ScreenshotAccessibilityService hosts the strip and captures silently (no dialog).
 *   The backing permission is the enabled accessibility service.
 * - API 26..29 : OverlayHostService hosts the strip; the gesture triggers MediaProjection consent.
 *   The backing permission is draw-over-apps (SYSTEM_ALERT_WINDOW).
 */
class ScreenGestureOverlayControllerImpl @Inject constructor(
    @ApplicationContext context: Context
) : ScreenGestureOverlayController {

    private val appContext = context.applicationContext

    // API 30+ supports the dialog-free accessibility path; below that only MediaProjection exists.
    private val supportsAccessibilityPath: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    // The path used right now depends on which permission is granted: accessibility wins
    // (dialog-free); otherwise the overlay + MediaProjection fallback is used.
    private fun accessibilityPathActive(context: Context): Boolean =
        supportsAccessibilityPath && isAccessibilityServiceEnabled(context)

    override fun isOverlayPermissionGranted(context: Context): Boolean =
        accessibilityPathActive(context) || Settings.canDrawOverlays(context)

    override fun permissionSettingsIntent(context: Context): Intent =
        if (supportsAccessibilityPath) {
            accessibilitySettingsIntent(context)
        } else {
            overlaySettingsIntent(context)
        }

    override fun isFallbackCaptureAvailable(): Boolean = supportsAccessibilityPath

    override fun fallbackPermissionSettingsIntent(context: Context): Intent =
        overlaySettingsIntent(context)

    private fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )

    /**
     * Deep-links Settings to this app's accessibility service row. ACTION_ACCESSIBILITY_SETTINGS
     * alone only opens the generic list; the highlight/scroll extras (best-effort, honoured on
     * AOSP and Samsung) point Settings straight at our service so the user does not hunt for it.
     * Android forbids enabling an accessibility service programmatically, so landing on its row is
     * as direct as the routing can be.
     */
    private fun accessibilitySettingsIntent(context: Context): Intent {
        val component = ComponentName(context, ScreenshotAccessibilityService::class.java)
            .flattenToString()
        val showArgs = Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, component) }
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(EXTRA_FRAGMENT_ARG_KEY, component)
            putExtra(EXTRA_SHOW_FRAGMENT_ARGS, showArgs)
        }
    }

    override fun permissionRationaleResId(): Int =
        if (supportsAccessibilityPath) {
            R.string.screenshot_accessibility_permission_rationale
        } else {
            R.string.screenshot_overlay_permission_rationale
        }

    override fun setEnabled(enabled: Boolean, stripVisible: Boolean) {
        if (!enabled) {
            OverlayHostService.stop(appContext)
            ScreenshotAccessibilityServiceHolder.instance?.applyOverlayState(false)
            return
        }

        // Runtime priority: accessibility (dialog-free) wins when enabled, with the legacy overlay
        // host shut down; otherwise the MediaProjection fallback runs if draw-over-apps is granted.
        // Granting neither leaves the toggle effectively off. The accessibility service lifecycle is
        // system-owned; we only push strip visibility to the live instance (if connected).
        when {
            accessibilityPathActive(appContext) -> {
                OverlayHostService.stop(appContext)
                ScreenshotAccessibilityServiceHolder.instance?.applyOverlayState(true, stripVisible)
            }
            Settings.canDrawOverlays(appContext) -> {
                ScreenshotAccessibilityServiceHolder.instance?.applyOverlayState(false)
                OverlayHostService.start(appContext, stripVisible)
            }
        }
    }

    override fun setStripVisible(visible: Boolean, overlayEnabled: Boolean) {
        // S0724: recolour the live strip on whichever path currently hosts it; no-op while disabled.
        if (!overlayEnabled) return
        when {
            accessibilityPathActive(appContext) ->
                ScreenshotAccessibilityServiceHolder.instance?.applyStripVisible(visible)
            Settings.canDrawOverlays(appContext) ->
                OverlayHostService.start(appContext, visible)
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ScreenshotAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val parsed = ComponentName.unflattenFromString(splitter.next())
            if (parsed == expected) return true
        }
        return false
    }

    private companion object {
        // Semi-public Settings extras used to highlight/scroll to a specific preference row.
        const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    }
}
