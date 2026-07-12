package com.sza.fastmediasorter.ui.settings.helpers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone
import com.sza.fastmediasorter.ui.applaunchpanel.edit.EditAppLaunchPanelActivity
import com.sza.fastmediasorter.ui.common.widget.SettingsSelectionRow
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import timber.log.Timber

/**
 * Owns the screen-gesture overlay subgroup of the Operations tab: the overlay enable toggle and its
 * permission dialog, the clipboard toggle, the screenshot destination, and the three gesture-action
 * pickers. The whole card is hidden on flavors without the capability (empty controller set), so this
 * manager is a no-op there. The overlay-permission launcher stays registered in the fragment and is
 * injected here.
 */
class OperationsGesturesManager(
    private val binding: FragmentSettingsDestinationsBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val screenGestureControllers: Set<ScreenGestureOverlayController>,
    private val gestureActionPickerManager: ScreenshotGestureActionPickerManager,
    private val overlayPermissionLauncher: ActivityResultLauncher<Intent>,
    private val isUpdatingFromSettings: () -> Boolean,
    private val pickDestination: (Long?, (MediaResource?) -> Unit) -> Unit,
    private val refreshLabel: (String?, Int, (CharSequence) -> Unit) -> Unit,
) {

    fun setup() {
        val controller = screenGestureControllers.firstOrNull()
        if (controller == null) {
            binding.groupScreenGestures.isVisible = false
            return
        }
        // S0621: on standard the controller exposes only the MediaProjection consent path
        // (isFallbackCaptureAvailable() == false), so the accessibility-shortcut rows are hidden;
        // noLegal (API 30+) keeps them as the silent-capture opt-in.
        val supportsA11ySilent = controller.isFallbackCaptureAvailable()
        binding.tvAccessibilityShortcutHint.isVisible = supportsA11ySilent
        binding.btnOpenAccessibilitySettings.isVisible = supportsA11ySilent
        // S0663: edit the gesture-bound quick-launch panel; the panel rides the same left-edge gesture,
        // so it belongs in this gesture group and inherits its capability visibility.
        binding.btnEditAppPanel.setOnClickListener {
            fragment.startActivity(Intent(fragment.requireContext(), EditAppLaunchPanelActivity::class.java))
        }
        binding.rowGestureOverlayEnabled.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            if (isChecked) {
                if (!controller.isOverlayPermissionGranted(fragment.requireContext())) {
                    showGesturePermissionDialog(controller)
                    return@setOnCheckedChangeListener
                }
                controller.setEnabled(true, viewModel.settings.value.screenshotGestureStripVisible)
                viewModel.updateSettings(viewModel.settings.value.copy(gestureOverlayEnabled = true))
            } else {
                controller.setEnabled(false, viewModel.settings.value.screenshotGestureStripVisible)
                viewModel.updateSettings(viewModel.settings.value.copy(gestureOverlayEnabled = false))
            }
        }
        binding.rowGestureStripVisible.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(screenshotGestureStripVisible = isChecked))
            // Push the explicit value so a live strip recolours immediately (no-op while overlay is off).
            controller.setStripVisible(isChecked, viewModel.settings.value.gestureOverlayEnabled)
        }
        binding.rowCopyScreenshotToClipboard.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(viewModel.settings.value.copy(copyScreenshotToClipboard = isChecked))
        }
        // S0842: icon-only "select resource" button; tooltip backports the label (S0810 pattern).
        val destBtn = binding.btnSelectScreenshotDestination
        TooltipCompat.setTooltipText(destBtn, destBtn.contentDescription)
        binding.btnSelectScreenshotDestination.setOnClickListener {
            pickDestination(
                viewModel.settings.value.screenshotDestinationResourceId?.toLongOrNull()
            ) { resource ->
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(screenshotDestinationResourceId = resource?.id?.toString()))
            }
        }
        setupZones()
        binding.btnOpenAccessibilitySettings.setOnClickListener {
            try {
                overlayPermissionLauncher.launch(controller.permissionSettingsIntent(fragment.requireContext()))
            } catch (e: ActivityNotFoundException) {
                // Accessibility settings screen unreachable on this ROM: fall back to the
                // educational dialog, which routes to alternative entry points (S0449 ADR-1).
                Timber.w(e, "Accessibility settings intent unresolved; showing fallback dialog")
                showGesturePermissionDialog(controller)
            }
        }
    }

    /** Applies the latest settings to the gesture rows (overlay toggle, clipboard, action labels, destination). */
    fun render(settings: AppSettings) {
        if (screenGestureControllers.isEmpty()) return
        if (binding.rowGestureOverlayEnabled.isChecked != settings.gestureOverlayEnabled) {
            binding.rowGestureOverlayEnabled.setCheckedSilently(settings.gestureOverlayEnabled)
        }
        if (binding.rowGestureStripVisible.isChecked != settings.screenshotGestureStripVisible) {
            binding.rowGestureStripVisible.setCheckedSilently(settings.screenshotGestureStripVisible)
        }
        if (binding.rowCopyScreenshotToClipboard.isChecked != settings.copyScreenshotToClipboard) {
            binding.rowCopyScreenshotToClipboard.setCheckedSilently(settings.copyScreenshotToClipboard)
        }
        renderZone(
            settings, ScreenshotGestureZone.LEFT_TOP, binding.rowZoneLeftTopEnabled, binding.containerZoneLeftTop,
            binding.rowGestureLeftTopUp, binding.rowGestureLeftTopRight, binding.rowGestureLeftTopDown
        )
        renderZone(
            settings, ScreenshotGestureZone.LEFT_BOTTOM, binding.rowZoneLeftBottomEnabled,
            binding.containerZoneLeftBottom, binding.rowGestureLeftBottomUp,
            binding.rowGestureLeftBottomRight, binding.rowGestureLeftBottomDown
        )
        renderZone(
            settings, ScreenshotGestureZone.RIGHT_TOP, binding.rowZoneRightTopEnabled, binding.containerZoneRightTop,
            binding.rowGestureRightTopUp, binding.rowGestureRightTopRight, binding.rowGestureRightTopDown
        )
        renderZone(
            settings, ScreenshotGestureZone.RIGHT_BOTTOM, binding.rowZoneRightBottomEnabled,
            binding.containerZoneRightBottom, binding.rowGestureRightBottomUp,
            binding.rowGestureRightBottomRight, binding.rowGestureRightBottomDown
        )
        refreshLabel(
            settings.screenshotDestinationResourceId,
            R.string.setting_screenshot_destination_default
        ) { binding.tvScreenshotDestination.text = it }
    }

    // S0847: wire the four edge-band groups. Each group persists its enable flag + rebuilds the live
    // overlay, and its three pickers write the zone-scoped action slots via the shared action catalog.
    private fun setupZones() {
        bindZone(
            ScreenshotGestureZone.LEFT_TOP, binding.rowZoneLeftTopEnabled, binding.containerZoneLeftTop,
            binding.rowGestureLeftTopUp, binding.rowGestureLeftTopRight, binding.rowGestureLeftTopDown,
            setEnabled = { s, e -> s.copy(screenshotGestureZoneLeftTopEnabled = e) },
            setAction = { s, d, a ->
                when (d) {
                    ScreenshotGestureDirection.UP -> s.copy(screenshotGestureLeftTopUp = a)
                    ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureLeftTopRight = a)
                    ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureLeftTopDown = a)
                }
            },
        )
        bindZone(
            ScreenshotGestureZone.LEFT_BOTTOM, binding.rowZoneLeftBottomEnabled, binding.containerZoneLeftBottom,
            binding.rowGestureLeftBottomUp, binding.rowGestureLeftBottomRight, binding.rowGestureLeftBottomDown,
            setEnabled = { s, e -> s.copy(screenshotGestureZoneLeftBottomEnabled = e) },
            setAction = { s, d, a ->
                when (d) {
                    ScreenshotGestureDirection.UP -> s.copy(screenshotGestureLeftBottomUp = a)
                    ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureLeftBottomRight = a)
                    ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureLeftBottomDown = a)
                }
            },
        )
        bindZone(
            ScreenshotGestureZone.RIGHT_TOP, binding.rowZoneRightTopEnabled, binding.containerZoneRightTop,
            binding.rowGestureRightTopUp, binding.rowGestureRightTopRight, binding.rowGestureRightTopDown,
            setEnabled = { s, e -> s.copy(screenshotGestureZoneRightTopEnabled = e) },
            setAction = { s, d, a ->
                when (d) {
                    ScreenshotGestureDirection.UP -> s.copy(screenshotGestureRightTopUp = a)
                    ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureRightTopRight = a)
                    ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureRightTopDown = a)
                }
            },
        )
        bindZone(
            ScreenshotGestureZone.RIGHT_BOTTOM, binding.rowZoneRightBottomEnabled, binding.containerZoneRightBottom,
            binding.rowGestureRightBottomUp, binding.rowGestureRightBottomRight, binding.rowGestureRightBottomDown,
            setEnabled = { s, e -> s.copy(screenshotGestureZoneRightBottomEnabled = e) },
            setAction = { s, d, a ->
                when (d) {
                    ScreenshotGestureDirection.UP -> s.copy(screenshotGestureRightBottomUp = a)
                    ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureRightBottomRight = a)
                    ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureRightBottomDown = a)
                }
            },
        )
    }

    private fun bindZone(
        zone: ScreenshotGestureZone,
        toggle: SettingsToggleRow,
        container: View,
        upRow: SettingsSelectionRow,
        rightRow: SettingsSelectionRow,
        downRow: SettingsSelectionRow,
        setEnabled: (AppSettings, Boolean) -> AppSettings,
        setAction: (AppSettings, ScreenshotGestureDirection, ScreenshotGestureAction) -> AppSettings,
    ) {
        toggle.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(setEnabled(viewModel.settings.value, isChecked))
            container.isVisible = isChecked
            // Rebuild the live overlay so the band appears/disappears (no-op while the overlay master is off).
            val controller = screenGestureControllers.firstOrNull() ?: return@setOnCheckedChangeListener
            val settings = viewModel.settings.value
            controller.setEnabled(settings.gestureOverlayEnabled, settings.screenshotGestureStripVisible)
        }
        bindPicker(upRow, zone, ScreenshotGestureDirection.UP, setAction)
        bindPicker(rightRow, zone, ScreenshotGestureDirection.RIGHT, setAction)
        bindPicker(downRow, zone, ScreenshotGestureDirection.DOWN, setAction)
    }

    private fun bindPicker(
        row: SettingsSelectionRow,
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection,
        setAction: (AppSettings, ScreenshotGestureDirection, ScreenshotGestureAction) -> AppSettings,
    ) {
        row.setOnRowClickListener {
            gestureActionPickerManager.showPicker(
                fragment.requireContext(),
                fragment.viewLifecycleOwner,
                viewModel.settings.value.screenshotGestureAction(zone, direction)
            ) { picked ->
                viewModel.updateSettings(setAction(viewModel.settings.value, direction, picked))
            }
        }
    }

    private fun renderZone(
        settings: AppSettings,
        zone: ScreenshotGestureZone,
        toggle: SettingsToggleRow,
        container: View,
        upRow: SettingsSelectionRow,
        rightRow: SettingsSelectionRow,
        downRow: SettingsSelectionRow,
    ) {
        val enabled = settings.screenshotGestureZoneEnabled(zone)
        if (toggle.isChecked != enabled) toggle.setCheckedSilently(enabled)
        container.isVisible = enabled
        val ctx = fragment.requireContext()
        fun label(direction: ScreenshotGestureDirection): CharSequence =
            gestureActionPickerManager.labelFor(ctx, settings.screenshotGestureAction(zone, direction))
        upRow.setValue(label(ScreenshotGestureDirection.UP))
        rightRow.setValue(label(ScreenshotGestureDirection.RIGHT))
        downRow.setValue(label(ScreenshotGestureDirection.DOWN))
    }

    /** Re-applies the overlay state after returning from the system permission screen. */
    fun onOverlayPermissionResult() {
        val controller = screenGestureControllers.firstOrNull() ?: return
        if (controller.isOverlayPermissionGranted(fragment.requireContext())) {
            controller.setEnabled(true, viewModel.settings.value.screenshotGestureStripVisible)
            viewModel.updateSettings(viewModel.settings.value.copy(gestureOverlayEnabled = true))
        } else {
            binding.rowGestureOverlayEnabled.setCheckedSilently(false)
        }
    }

    /**
     * Instructional gate before sending the user to the permission screen. Sideloaded (noLegal)
     * builds cannot flip the accessibility toggle directly - the exact tap sequence is spelled out
     * here, with shortcuts to both the accessibility screen and App info. Any exit without the
     * grant reverts the row (handled in onDismiss so back-press / outside-tap are covered too).
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
