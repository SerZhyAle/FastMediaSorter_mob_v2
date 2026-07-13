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
                controller.setEnabled(true)
                viewModel.updateSettings(viewModel.settings.value.copy(gestureOverlayEnabled = true))
            } else {
                controller.setEnabled(false)
                viewModel.updateSettings(viewModel.settings.value.copy(gestureOverlayEnabled = false))
            }
        }
        // S1008: the per-zone strip-visibility toggles live inside each zone block, wired in setupZones().
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
        if (binding.rowCopyScreenshotToClipboard.isChecked != settings.copyScreenshotToClipboard) {
            binding.rowCopyScreenshotToClipboard.setCheckedSilently(settings.copyScreenshotToClipboard)
        }
        renderZone(
            settings,
            ScreenshotGestureZone.LEFT_TOP,
            ZoneViews(
                binding.rowZoneLeftTopEnabled,
                binding.containerZoneLeftTop,
                binding.rowZoneLeftTopStripVisible,
                binding.rowGestureLeftTopUp,
                binding.rowGestureLeftTopRight,
                binding.rowGestureLeftTopDown,
            ),
        )
        renderZone(
            settings,
            ScreenshotGestureZone.LEFT_BOTTOM,
            ZoneViews(
                binding.rowZoneLeftBottomEnabled,
                binding.containerZoneLeftBottom,
                binding.rowZoneLeftBottomStripVisible,
                binding.rowGestureLeftBottomUp,
                binding.rowGestureLeftBottomRight,
                binding.rowGestureLeftBottomDown,
            ),
        )
        renderZone(
            settings,
            ScreenshotGestureZone.RIGHT_TOP,
            ZoneViews(
                binding.rowZoneRightTopEnabled,
                binding.containerZoneRightTop,
                binding.rowZoneRightTopStripVisible,
                binding.rowGestureRightTopUp,
                binding.rowGestureRightTopRight,
                binding.rowGestureRightTopDown,
            ),
        )
        renderZone(
            settings,
            ScreenshotGestureZone.RIGHT_BOTTOM,
            ZoneViews(
                binding.rowZoneRightBottomEnabled,
                binding.containerZoneRightBottom,
                binding.rowZoneRightBottomStripVisible,
                binding.rowGestureRightBottomUp,
                binding.rowGestureRightBottomRight,
                binding.rowGestureRightBottomDown,
            ),
        )
        refreshLabel(
            settings.screenshotDestinationResourceId,
            R.string.setting_screenshot_destination_default
        ) { binding.tvScreenshotDestination.text = it }
    }

    // Bundles one zone's view refs so renderZone/bindZone stay under the LongParameterList threshold
    // instead of taking six individual view params each.
    private data class ZoneViews(
        val toggle: SettingsToggleRow,
        val container: View,
        val stripToggle: SettingsToggleRow,
        val upRow: SettingsSelectionRow,
        val rightRow: SettingsSelectionRow,
        val downRow: SettingsSelectionRow,
    )

    // S0847: wire the four edge-band groups. Each group persists its enable flag + rebuilds the live
    // overlay, and its three pickers write the zone-scoped action slots via the shared action catalog.
    // S1008: each group also owns a per-zone strip-visibility toggle inside its container.
    private fun setupZones() {
        bindLeftTopZone()
        bindLeftBottomZone()
        bindRightTopZone()
        bindRightBottomZone()
    }

    private fun bindLeftTopZone() = bindZone(
        ScreenshotGestureZone.LEFT_TOP,
        ZoneViews(
            binding.rowZoneLeftTopEnabled,
            binding.containerZoneLeftTop,
            binding.rowZoneLeftTopStripVisible,
            binding.rowGestureLeftTopUp,
            binding.rowGestureLeftTopRight,
            binding.rowGestureLeftTopDown,
        ),
        setEnabled = { s, e -> s.copy(screenshotGestureZoneLeftTopEnabled = e) },
        setStripVisible = { s, v -> s.copy(screenshotGestureZoneLeftTopStripVisible = v) },
        setAction = { s, d, a ->
            when (d) {
                ScreenshotGestureDirection.UP -> s.copy(screenshotGestureLeftTopUp = a)
                ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureLeftTopRight = a)
                ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureLeftTopDown = a)
            }
        },
    )

    private fun bindLeftBottomZone() = bindZone(
        ScreenshotGestureZone.LEFT_BOTTOM,
        ZoneViews(
            binding.rowZoneLeftBottomEnabled,
            binding.containerZoneLeftBottom,
            binding.rowZoneLeftBottomStripVisible,
            binding.rowGestureLeftBottomUp,
            binding.rowGestureLeftBottomRight,
            binding.rowGestureLeftBottomDown,
        ),
        setEnabled = { s, e -> s.copy(screenshotGestureZoneLeftBottomEnabled = e) },
        setStripVisible = { s, v -> s.copy(screenshotGestureZoneLeftBottomStripVisible = v) },
        setAction = { s, d, a ->
            when (d) {
                ScreenshotGestureDirection.UP -> s.copy(screenshotGestureLeftBottomUp = a)
                ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureLeftBottomRight = a)
                ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureLeftBottomDown = a)
            }
        },
    )

    private fun bindRightTopZone() = bindZone(
        ScreenshotGestureZone.RIGHT_TOP,
        ZoneViews(
            binding.rowZoneRightTopEnabled,
            binding.containerZoneRightTop,
            binding.rowZoneRightTopStripVisible,
            binding.rowGestureRightTopUp,
            binding.rowGestureRightTopRight,
            binding.rowGestureRightTopDown,
        ),
        setEnabled = { s, e -> s.copy(screenshotGestureZoneRightTopEnabled = e) },
        setStripVisible = { s, v -> s.copy(screenshotGestureZoneRightTopStripVisible = v) },
        setAction = { s, d, a ->
            when (d) {
                ScreenshotGestureDirection.UP -> s.copy(screenshotGestureRightTopUp = a)
                ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureRightTopRight = a)
                ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureRightTopDown = a)
            }
        },
    )

    private fun bindRightBottomZone() = bindZone(
        ScreenshotGestureZone.RIGHT_BOTTOM,
        ZoneViews(
            binding.rowZoneRightBottomEnabled,
            binding.containerZoneRightBottom,
            binding.rowZoneRightBottomStripVisible,
            binding.rowGestureRightBottomUp,
            binding.rowGestureRightBottomRight,
            binding.rowGestureRightBottomDown,
        ),
        setEnabled = { s, e -> s.copy(screenshotGestureZoneRightBottomEnabled = e) },
        setStripVisible = { s, v -> s.copy(screenshotGestureZoneRightBottomStripVisible = v) },
        setAction = { s, d, a ->
            when (d) {
                ScreenshotGestureDirection.UP -> s.copy(screenshotGestureRightBottomUp = a)
                ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureRightBottomRight = a)
                ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureRightBottomDown = a)
            }
        },
    )

    private fun bindZone(
        zone: ScreenshotGestureZone,
        views: ZoneViews,
        setEnabled: (AppSettings, Boolean) -> AppSettings,
        setStripVisible: (AppSettings, Boolean) -> AppSettings,
        setAction: (AppSettings, ScreenshotGestureDirection, ScreenshotGestureAction) -> AppSettings,
    ) {
        views.toggle.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(setEnabled(viewModel.settings.value, isChecked))
            views.container.isVisible = isChecked
            // Rebuild the live overlay so the band appears/disappears (no-op while the overlay master is off).
            val controller = screenGestureControllers.firstOrNull() ?: return@setOnCheckedChangeListener
            controller.setEnabled(viewModel.settings.value.gestureOverlayEnabled)
        }
        // S1008: per-zone strip visibility; refreshes the live strip colour (no-op while the overlay is off).
        views.stripToggle.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(setStripVisible(viewModel.settings.value, isChecked))
            val controller = screenGestureControllers.firstOrNull() ?: return@setOnCheckedChangeListener
            controller.setStripVisible(viewModel.settings.value.gestureOverlayEnabled)
        }
        bindPicker(views.upRow, zone, ScreenshotGestureDirection.UP, setAction)
        bindPicker(views.rightRow, zone, ScreenshotGestureDirection.RIGHT, setAction)
        bindPicker(views.downRow, zone, ScreenshotGestureDirection.DOWN, setAction)
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

    private fun renderZone(settings: AppSettings, zone: ScreenshotGestureZone, views: ZoneViews) {
        val enabled = settings.screenshotGestureZoneEnabled(zone)
        if (views.toggle.isChecked != enabled) views.toggle.setCheckedSilently(enabled)
        views.container.isVisible = enabled
        // S1008: reflect the per-zone strip-visibility toggle that lives inside the container.
        val stripVisible = settings.screenshotGestureZoneStripVisible(zone)
        if (views.stripToggle.isChecked != stripVisible) views.stripToggle.setCheckedSilently(stripVisible)
        val ctx = fragment.requireContext()
        fun label(direction: ScreenshotGestureDirection): CharSequence =
            gestureActionPickerManager.labelFor(ctx, settings.screenshotGestureAction(zone, direction))
        views.upRow.setValue(label(ScreenshotGestureDirection.UP))
        views.rightRow.setValue(label(ScreenshotGestureDirection.RIGHT))
        views.downRow.setValue(label(ScreenshotGestureDirection.DOWN))
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
