package com.sza.fastmediasorter.ui.settings.gesture

import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.databinding.DialogEdgeGestureConfigBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone
import com.sza.fastmediasorter.ui.applaunchpanel.edit.EditAppLaunchPanelActivity
import com.sza.fastmediasorter.ui.common.widget.SettingsSelectionRow
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.ScreenshotGestureActionPickerManager

/**
 * S1035: hosts the edge-gesture detail UI inside [EdgeGestureConfigDialogFragment] - the four per-zone
 * blocks (enable + strip visibility + three direction pickers), the interactive schema, and the general
 * group (clipboard, screenshot destination, edit-app-panel). Extracted verbatim from
 * OperationsGesturesManager so persistence + the shared [ScreenshotGestureActionPickerManager] are reused,
 * not duplicated; the settings tab keeps only the master toggle + launcher. The dialog is reachable only
 * behind the same capability gate (non-empty controller set), so an empty set never renders here.
 */
class EdgeGestureConfigManager(
    private val binding: DialogEdgeGestureConfigBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val screenGestureControllers: Set<ScreenGestureOverlayController>,
    private val gestureActionPickerManager: ScreenshotGestureActionPickerManager,
    private val isUpdatingFromSettings: () -> Boolean,
    private val pickDestination: (Long?, (MediaResource?) -> Unit) -> Unit,
    private val refreshLabel: (String?, Int, (CharSequence) -> Unit) -> Unit,
) {

    // Zone order shared by the tabs and the block-visibility switch.
    private val tabZones = listOf(
        ScreenshotGestureZone.LEFT_TOP,
        ScreenshotGestureZone.LEFT_BOTTOM,
        ScreenshotGestureZone.RIGHT_TOP,
        ScreenshotGestureZone.RIGHT_BOTTOM,
    )

    // Held so teardown() can detach it symmetrically on the dialog's onDestroyView.
    private var tabSelectedListener: TabLayout.OnTabSelectedListener? = null

    fun setup() {
        // The dialog is reachable only behind the non-empty controller gate; guard defensively anyway.
        if (screenGestureControllers.isEmpty()) return
        // S0663: the quick-launch panel rides the same left-edge gesture, so its editor belongs here.
        binding.btnEditAppPanel.setOnClickListener {
            fragment.startActivity(Intent(fragment.requireContext(), EditAppLaunchPanelActivity::class.java))
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
        setupSchema()
        setupTabs()
        setupGeneralGroup()
    }

    // The header only owns its chevron state, so the container visibility must be driven here (S1035
    // follow-up: previously unwired, so collapsing/expanding the group did nothing). Starts collapsed.
    private fun setupGeneralGroup() {
        val container = binding.containerEdgeGestureGeneral
        val header = binding.headerEdgeGestureGeneral
        container.isVisible = header.isExpanded()
        header.setOnExpandedChangeListener { expanded -> container.isVisible = expanded }
    }

    /** Applies the latest settings to the dialog rows, the destination label, and the interactive schema. */
    fun render(settings: AppSettings) {
        if (screenGestureControllers.isEmpty()) return
        if (binding.rowCopyScreenshotToClipboard.isChecked != settings.copyScreenshotToClipboard) {
            binding.rowCopyScreenshotToClipboard.setCheckedSilently(settings.copyScreenshotToClipboard)
        }
        tabZones.forEach { zone -> renderZone(settings, zone) }
        refreshLabel(
            settings.screenshotDestinationResourceId,
            R.string.setting_screenshot_destination_default
        ) { binding.tvScreenshotDestination.text = it }
        binding.edgeGestureSchema.setState(buildSchemaState(settings))
    }

    /** Selects the tab for [zone] (driven by the schema's zone-tap), which reveals its block. */
    fun selectZone(zone: ScreenshotGestureZone) {
        val index = tabZones.indexOf(zone).takeIf { it >= 0 } ?: return
        binding.tabsEdgeGestureZones.getTabAt(index)?.select()
    }

    // Bundles one zone's view refs so renderZone/bindZone stay under the LongParameterList threshold.
    private data class ZoneViews(
        val toggle: SettingsToggleRow,
        val container: View,
        val stripToggle: SettingsToggleRow,
        val upRow: SettingsSelectionRow,
        val rightRow: SettingsSelectionRow,
        val downRow: SettingsSelectionRow,
    )

    private fun zoneViews(zone: ScreenshotGestureZone): ZoneViews = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> ZoneViews(
            binding.rowZoneLeftTopEnabled,
            binding.containerZoneLeftTop,
            binding.rowZoneLeftTopStripVisible,
            binding.rowGestureLeftTopUp,
            binding.rowGestureLeftTopRight,
            binding.rowGestureLeftTopDown,
        )
        ScreenshotGestureZone.LEFT_BOTTOM -> ZoneViews(
            binding.rowZoneLeftBottomEnabled,
            binding.containerZoneLeftBottom,
            binding.rowZoneLeftBottomStripVisible,
            binding.rowGestureLeftBottomUp,
            binding.rowGestureLeftBottomRight,
            binding.rowGestureLeftBottomDown,
        )
        ScreenshotGestureZone.RIGHT_TOP -> ZoneViews(
            binding.rowZoneRightTopEnabled,
            binding.containerZoneRightTop,
            binding.rowZoneRightTopStripVisible,
            binding.rowGestureRightTopUp,
            binding.rowGestureRightTopRight,
            binding.rowGestureRightTopDown,
        )
        ScreenshotGestureZone.RIGHT_BOTTOM -> ZoneViews(
            binding.rowZoneRightBottomEnabled,
            binding.containerZoneRightBottom,
            binding.rowZoneRightBottomStripVisible,
            binding.rowGestureRightBottomUp,
            binding.rowGestureRightBottomRight,
            binding.rowGestureRightBottomDown,
        )
    }

    // S0847: wire the four edge-band groups; each persists its enable flag + rebuilds the live overlay,
    // and its three pickers write the zone-scoped action slots. S1008: each also owns a strip toggle.
    private fun setupZones() {
        tabZones.forEach { zone -> bindZone(zone, zoneViews(zone)) }
    }

    private fun bindZone(zone: ScreenshotGestureZone, views: ZoneViews) {
        views.toggle.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(applyEnabled(viewModel.settings.value, zone, isChecked))
            views.container.isVisible = isChecked
            // Rebuild the live overlay so the band appears/disappears (no-op while the master is off).
            val controller = screenGestureControllers.firstOrNull() ?: return@setOnCheckedChangeListener
            controller.setEnabled(viewModel.settings.value.gestureOverlayEnabled)
        }
        // S1008: per-zone strip visibility; refreshes the live strip colour (no-op while the master is off).
        views.stripToggle.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            viewModel.updateSettings(applyStripVisible(viewModel.settings.value, zone, isChecked))
            val controller = screenGestureControllers.firstOrNull() ?: return@setOnCheckedChangeListener
            controller.setStripVisible(viewModel.settings.value.gestureOverlayEnabled)
        }
        bindPicker(views.upRow, zone, ScreenshotGestureDirection.UP)
        bindPicker(views.rightRow, zone, ScreenshotGestureDirection.RIGHT)
        bindPicker(views.downRow, zone, ScreenshotGestureDirection.DOWN)
    }

    private fun bindPicker(
        row: SettingsSelectionRow,
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection,
    ) {
        row.setOnRowClickListener { openActionPicker(zone, direction) }
    }

    private fun openActionPicker(zone: ScreenshotGestureZone, direction: ScreenshotGestureDirection) {
        gestureActionPickerManager.showPicker(
            fragment.requireContext(),
            fragment.viewLifecycleOwner,
            viewModel.settings.value.screenshotGestureAction(zone, direction)
        ) { picked ->
            viewModel.updateSettings(applyAction(viewModel.settings.value, zone, direction, picked))
        }
    }

    private fun renderZone(settings: AppSettings, zone: ScreenshotGestureZone) {
        val views = zoneViews(zone)
        val enabled = settings.screenshotGestureZoneEnabled(zone)
        if (views.toggle.isChecked != enabled) views.toggle.setCheckedSilently(enabled)
        views.container.isVisible = enabled
        val stripVisible = settings.screenshotGestureZoneStripVisible(zone)
        if (views.stripToggle.isChecked != stripVisible) views.stripToggle.setCheckedSilently(stripVisible)
        val ctx = fragment.requireContext()
        fun label(direction: ScreenshotGestureDirection): CharSequence =
            gestureActionPickerManager.labelFor(ctx, settings.screenshotGestureAction(zone, direction))
        views.upRow.setValue(label(ScreenshotGestureDirection.UP))
        views.rightRow.setValue(label(ScreenshotGestureDirection.RIGHT))
        views.downRow.setValue(label(ScreenshotGestureDirection.DOWN))
    }

    private fun setupSchema() {
        binding.edgeGestureSchema.setOnDirectionTapListener { zone, direction -> openActionPicker(zone, direction) }
        binding.edgeGestureSchema.setOnZoneTapListener { zone -> selectZone(zone) }
    }

    private fun setupTabs() {
        val tabs = binding.tabsEdgeGestureZones
        // Portrait stacks the two-word label onto two lines (one word per line) so all four fixed-width
        // tabs fit one row; landscape keeps the single-line scrollable tabs unchanged (S1035 follow-up).
        val portrait = fragment.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (tabs.tabCount == 0) {
            tabZones.forEach { zone ->
                val label = fragment.getString(tabTitleRes(zone))
                val text = if (portrait) label.replaceFirst(" ", "\n") else label
                tabs.addTab(tabs.newTab().setText(text))
            }
        }
        if (portrait) applyTabDividers(tabs)
        val listener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showZoneBlock(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        }
        tabSelectedListener = listener
        tabs.addOnTabSelectedListener(listener)
        showZoneBlock(tabs.selectedTabPosition.coerceAtLeast(0))
    }

    // Faint separator between the fixed-width portrait tabs; drawn by the tab strip, not measured in.
    private fun applyTabDividers(tabs: TabLayout) {
        val strip = tabs.getChildAt(0) as? LinearLayout ?: return
        strip.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        val ctx = fragment.requireContext()
        strip.dividerDrawable = ContextCompat.getDrawable(ctx, R.drawable.edge_gesture_tab_divider)
    }

    /** Symmetric teardown - the dialog calls this from onDestroyView to detach the tab listener. */
    fun teardown() {
        tabSelectedListener?.let { binding.tabsEdgeGestureZones.removeOnTabSelectedListener(it) }
        tabSelectedListener = null
    }

    private fun showZoneBlock(position: Int) {
        tabZones.forEachIndexed { index, zone -> blockFor(zone).isVisible = index == position }
    }

    private fun blockFor(zone: ScreenshotGestureZone): View = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> binding.blockZoneLeftTop
        ScreenshotGestureZone.LEFT_BOTTOM -> binding.blockZoneLeftBottom
        ScreenshotGestureZone.RIGHT_TOP -> binding.blockZoneRightTop
        ScreenshotGestureZone.RIGHT_BOTTOM -> binding.blockZoneRightBottom
    }

    private fun tabTitleRes(zone: ScreenshotGestureZone): Int = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> R.string.edge_gesture_tab_left_top
        ScreenshotGestureZone.LEFT_BOTTOM -> R.string.edge_gesture_tab_left_bottom
        ScreenshotGestureZone.RIGHT_TOP -> R.string.edge_gesture_tab_right_top
        ScreenshotGestureZone.RIGHT_BOTTOM -> R.string.edge_gesture_tab_right_bottom
    }

    private fun buildSchemaState(settings: AppSettings): EdgeGestureSchemaView.SchemaState {
        val zones = ScreenshotGestureZone.entries.associateWith { zone ->
            val assigned = ScreenshotGestureDirection.entries.filter { direction ->
                settings.screenshotGestureAction(zone, direction) != ScreenshotGestureAction.DO_NOT_USE
            }.toSet()
            EdgeGestureSchemaView.SchemaState.ZoneState(settings.screenshotGestureZoneEnabled(zone), assigned)
        }
        return EdgeGestureSchemaView.SchemaState(zones)
    }

    private fun applyEnabled(s: AppSettings, zone: ScreenshotGestureZone, enabled: Boolean): AppSettings = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> s.copy(screenshotGestureZoneLeftTopEnabled = enabled)
        ScreenshotGestureZone.LEFT_BOTTOM -> s.copy(screenshotGestureZoneLeftBottomEnabled = enabled)
        ScreenshotGestureZone.RIGHT_TOP -> s.copy(screenshotGestureZoneRightTopEnabled = enabled)
        ScreenshotGestureZone.RIGHT_BOTTOM -> s.copy(screenshotGestureZoneRightBottomEnabled = enabled)
    }

    private fun applyStripVisible(
        s: AppSettings,
        zone: ScreenshotGestureZone,
        visible: Boolean,
    ): AppSettings = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> s.copy(screenshotGestureZoneLeftTopStripVisible = visible)
        ScreenshotGestureZone.LEFT_BOTTOM -> s.copy(screenshotGestureZoneLeftBottomStripVisible = visible)
        ScreenshotGestureZone.RIGHT_TOP -> s.copy(screenshotGestureZoneRightTopStripVisible = visible)
        ScreenshotGestureZone.RIGHT_BOTTOM -> s.copy(screenshotGestureZoneRightBottomStripVisible = visible)
    }

    private fun applyAction(
        s: AppSettings,
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection,
        action: ScreenshotGestureAction,
    ): AppSettings = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> when (direction) {
            ScreenshotGestureDirection.UP -> s.copy(screenshotGestureLeftTopUp = action)
            ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureLeftTopRight = action)
            ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureLeftTopDown = action)
        }
        ScreenshotGestureZone.LEFT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.UP -> s.copy(screenshotGestureLeftBottomUp = action)
            ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureLeftBottomRight = action)
            ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureLeftBottomDown = action)
        }
        ScreenshotGestureZone.RIGHT_TOP -> when (direction) {
            ScreenshotGestureDirection.UP -> s.copy(screenshotGestureRightTopUp = action)
            ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureRightTopRight = action)
            ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureRightTopDown = action)
        }
        ScreenshotGestureZone.RIGHT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.UP -> s.copy(screenshotGestureRightBottomUp = action)
            ScreenshotGestureDirection.RIGHT -> s.copy(screenshotGestureRightBottomRight = action)
            ScreenshotGestureDirection.DOWN -> s.copy(screenshotGestureRightBottomDown = action)
        }
    }
}
