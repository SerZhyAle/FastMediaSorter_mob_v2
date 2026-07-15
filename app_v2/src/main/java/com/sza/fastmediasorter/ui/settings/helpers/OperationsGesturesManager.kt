package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.settings.SettingsViewModel

/**
 * S1035: after the edge-gesture detail UI moved to EdgeGestureConfigDialogFragment, this manager keeps
 * only the Operations-tab entry point - the master overlay toggle with its permission flow, and gating
 * the "Configure gestures" launcher (disabled while gestures are off). The whole card is hidden on
 * flavors without the capability (empty controller set). The overlay-permission launcher stays
 * registered in the fragment and is injected here.
 */
class OperationsGesturesManager(
    private val binding: FragmentSettingsDestinationsBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val screenGestureControllers: Set<ScreenGestureOverlayController>,
    private val overlayPermissionLauncher: ActivityResultLauncher<Intent>,
    private val isUpdatingFromSettings: () -> Boolean,
) {

    fun setup() {
        val controller = screenGestureControllers.firstOrNull()
        if (controller == null) {
            binding.groupScreenGestures.isVisible = false
            return
        }
        binding.rowGestureOverlayEnabled.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            if (isChecked) {
                if (!controller.isOverlayPermissionGranted(fragment.requireContext())) {
                    showGesturePermissionDialog(controller)
                    return@setOnCheckedChangeListener
                }
                controller.setEnabled(true)
                viewModel.updateSettings(viewModel.settings.value.copy(gestureOverlayEnabled = true))
            } else {
                controller.setEnabled(false)
                viewModel.updateSettings(viewModel.settings.value.copy(gestureOverlayEnabled = false))
            }
        }
    }

    /** Reflects the master overlay state and gates the launcher (S1035: disabled while gestures are off). */
    fun render(settings: AppSettings) {
        if (screenGestureControllers.isEmpty()) return
        if (binding.rowGestureOverlayEnabled.isChecked != settings.gestureOverlayEnabled) {
            binding.rowGestureOverlayEnabled.setCheckedSilently(settings.gestureOverlayEnabled)
        }
        binding.btnOpenEdgeGestureConfig.isEnabled = settings.gestureOverlayEnabled
    }

    /** Re-applies the overlay state after returning from the system permission screen. */
    fun onOverlayPermissionResult() {
        val controller = screenGestureControllers.firstOrNull() ?: return
        if (controller.isOverlayPermissionGranted(fragment.requireContext())) {
            controller.setEnabled(true)
            viewModel.updateSettings(viewModel.settings.value.copy(gestureOverlayEnabled = true))
        } else {
            binding.rowGestureOverlayEnabled.setCheckedSilently(false)
        }
    }

    /**
     * Instructional gate before sending the user to the permission screen. Sideloaded (noLegal) builds
     * cannot flip the accessibility toggle directly - the exact tap sequence is spelled out here, with
     * shortcuts to both the accessibility screen and App info. Any exit without the grant reverts the
     * row (handled in onDismiss so back-press / outside-tap are covered too).
     */
    private fun showGesturePermissionDialog(controller: ScreenGestureOverlayController) {
        val builder = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.screenshot_gesture_permission_dialog_title)
            .setMessage(controller.permissionRationaleResId())
            .setPositiveButton(R.string.screenshot_gesture_open_settings) { _, _ ->
                overlayPermissionLauncher.launch(controller.permissionSettingsIntent(fragment.requireContext()))
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener {
                if (!controller.isOverlayPermissionGranted(fragment.requireContext())) {
                    binding.rowGestureOverlayEnabled.setCheckedSilently(false)
                }
            }
        if (controller.isFallbackCaptureAvailable()) {
            builder.setNeutralButton(R.string.screenshot_gesture_use_old_method) { _, _ ->
                overlayPermissionLauncher.launch(controller.fallbackPermissionSettingsIntent(fragment.requireContext()))
            }
        }
        builder.show()
    }
}
