package com.sza.fastmediasorter.ui.welcome.helpers

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import timber.log.Timber

/**
 * Drives the Welcome "enable gestures" toggle. Mirrors the gesture-overlay subset of
 * [com.sza.fastmediasorter.ui.settings.helpers.OperationsGesturesManager]: the capability gate, the
 * overlay-permission dialog, and re-applying state after the system permission screen returns. The
 * row is hidden entirely on flavors without the screen-gesture capability (empty controller set), so
 * Welcome and Settings share one persisted state. Per-direction action bindings are configured in
 * Settings only; onboarding offers the on/off opt-in, seeded with sensible defaults at first run.
 */
class WelcomeGesturesManager(
    private val row: SettingsToggleRow,
    private val screenGestureControllers: Set<ScreenGestureOverlayController>,
    private val overlayPermissionLauncher: ActivityResultLauncher<Intent>,
    private val activity: FragmentActivity,
    private val initialEnabled: Boolean,
    // S0727: persisted strip-visibility snapshot, passed in by the controller's owner so setEnabled
    // gets it without the controller reading settings on the Main thread.
    private val initialStripVisible: Boolean,
    private val persist: ((AppSettings) -> AppSettings) -> Unit,
) {
    fun setup() {
        val controller = screenGestureControllers.firstOrNull()
        if (controller == null) {
            row.isVisible = false
            return
        }
        row.isVisible = true
        row.setCheckedSilently(initialEnabled)
        row.setOnCheckedChangeListener { isChecked ->
            Timber.d("S0662: welcome gesture toggle -> $isChecked")
            if (isChecked) {
                if (!controller.isOverlayPermissionGranted(activity)) {
                    showGesturePermissionDialog(controller)
                    return@setOnCheckedChangeListener
                }
                controller.setEnabled(true, initialStripVisible)
                persist { it.copy(gestureOverlayEnabled = true) }
            } else {
                controller.setEnabled(false, initialStripVisible)
                persist { it.copy(gestureOverlayEnabled = false) }
            }
        }
    }

    /** Re-applies the overlay state after returning from the system permission screen. */
    fun onOverlayPermissionResult() {
        val controller = screenGestureControllers.firstOrNull() ?: return
        if (controller.isOverlayPermissionGranted(activity)) {
            controller.setEnabled(true, initialStripVisible)
            persist { it.copy(gestureOverlayEnabled = true) }
        } else {
            row.setCheckedSilently(false)
        }
    }

    private fun showGesturePermissionDialog(controller: ScreenGestureOverlayController) {
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.screenshot_gesture_permission_dialog_title)
            .setMessage(controller.permissionRationaleResId())
            .setPositiveButton(R.string.screenshot_gesture_open_settings) { _, _ ->
                launchPermissionIntent(controller.permissionSettingsIntent(activity))
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener {
                if (!controller.isOverlayPermissionGranted(activity)) {
                    row.setCheckedSilently(false)
                }
            }
        if (controller.isFallbackCaptureAvailable()) {
            builder.setNeutralButton(R.string.screenshot_gesture_use_old_method) { _, _ ->
                launchPermissionIntent(controller.fallbackPermissionSettingsIntent(activity))
            }
        }
        builder.show()
    }

    private fun launchPermissionIntent(intent: Intent) {
        try {
            overlayPermissionLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // ROM without the target settings screen: revert the row instead of crashing. The user can
            // still enable gestures later from Settings, which hosts the full fallback dialog.
            Timber.w(e, "Gesture permission settings intent unresolved from Welcome")
            row.setCheckedSilently(false)
        }
    }
}
