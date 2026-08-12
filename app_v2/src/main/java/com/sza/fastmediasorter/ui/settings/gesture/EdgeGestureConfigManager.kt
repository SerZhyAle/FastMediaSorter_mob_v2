package com.sza.fastmediasorter.ui.settings.gesture

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import com.sza.fastmediasorter.util.showBoundTo
import timber.log.Timber

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
    private val appSlotHost: AppSlotHost,
) {

    /**
     * S1036: the two app-slot services only the Fragment host can provide - the picker, whose result
     * arrives through the fragment result API a Fragment alone can register for, and the label lookup,
     * which needs the injected installed-apps source and answers off the main thread. Bundled into one
     * interface so this manager stays a view binder with no domain dependency, and its constructor
     * keeps a single parameter for the pair.
     */
    interface AppSlotHost {
        fun showAppPicker(zone: ScreenshotGestureZone, direction: ScreenshotGestureDirection)
        fun resolveAppLabel(packageName: String, onResolved: (String?) -> Unit)
    }

    // Zone order shared by the tabs and the block-visibility switch.
    private val tabZones = listOf(
        ScreenshotGestureZone.LEFT_TOP,
        ScreenshotGestureZone.LEFT_BOTTOM,
        ScreenshotGestureZone.RIGHT_TOP,
        ScreenshotGestureZone.RIGHT_BOTTOM,
    )

    // Held so teardown() can detach it symmetrically on the dialog's onDestroyView.
    private var tabSelectedListener: TabLayout.OnTabSelectedListener? = null

    // S1408: which zone the open tab edits. Screen state, not a setting - it is never persisted, and a
    // rotation restores it from the tab the dialog carries across the re-inflate.
    private var selectedZone: ScreenshotGestureZone? = null

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
        val slotRow = appRow(zone, direction)
        slotRow.setOnRowClickListener { appSlotHost.showAppPicker(zone, direction) }
        slotRow.setTrailingControl(resetControl(zone, direction))
    }

    /**
     * S1036: the explicit reset is the only path that clears a stored package - changing the direction's
     * action must not, or switching away and back would silently drop the choice. It writes an empty
     * payload for this one slot and leaves the action bound to it untouched.
     */
    private fun resetControl(zone: ScreenshotGestureZone, direction: ScreenshotGestureDirection): ImageView {
        val ctx = fragment.requireContext()
        val size = (RESET_ICON_SIZE_DP * ctx.resources.displayMetrics.density).toInt()
        return ImageView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            setImageResource(R.drawable.ic_clear)
            val borderless = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            background = ctx.obtainStyledAttributes(borderless).run { getDrawable(0).also { recycle() } }
            isClickable = true
            isFocusable = true
            contentDescription = ctx.getString(R.string.gesture_slot_app_reset)
            setOnClickListener {
                // Render from the copy just written: the settings flow has not emitted it yet, so
                // viewModel.settings.value would still carry the package being cleared.
                Timber.d("S1036: app choice cleared for slot %s/%s", zone, direction)
                val cleared = applyPayload(viewModel.settings.value, zone, direction, "")
                viewModel.updateSettings(cleared)
                renderAppRow(cleared, zone, direction)
            }
        }
    }

    private fun openActionPicker(zone: ScreenshotGestureZone, direction: ScreenshotGestureDirection) {
        gestureActionPickerManager.showPicker(
            fragment.requireContext(),
            fragment.viewLifecycleOwner,
            viewModel.settings.value.screenshotGestureAction(zone, direction)
        ) { picked ->
            viewModel.updateSettings(applyAction(viewModel.settings.value, zone, direction, picked))
            // S1038: OPEN_URL needs a target address; prompt for it right after the action is chosen and
            // store it in the per-slot payload. Cancelling leaves the payload empty (dispatch degrades).
            if (picked == ScreenshotGestureAction.OPEN_URL) promptUrl(zone, direction)
            // S1036: OPEN_APP needs a target package, so offer the app picker straight after the action is
            // chosen, mirroring OPEN_URL above. Cancelling leaves the payload empty and the gesture opens
            // FastMediaSorter, which is what it did before this action carried a payload at all.
            if (picked == ScreenshotGestureAction.OPEN_APP) appSlotHost.showAppPicker(zone, direction)
            // S1038: the brightness actions need WRITE_SETTINGS; request it now so the gesture actually
            // works. Without the grant the action is set but stays inactive (dispatch degrades with a log).
            if (picked == ScreenshotGestureAction.BRIGHTNESS_MAX ||
                picked == ScreenshotGestureAction.BRIGHTNESS_NORMAL
            ) {
                ensureWriteSettingsPermission()
            }
        }
    }

    // S1038: routes the user to the WRITE_SETTINGS grant screen when a brightness action is chosen and
    // the permission is missing. Declining leaves brightness inactive rather than failing silently.
    private fun ensureWriteSettingsPermission() {
        val ctx = fragment.requireContext()
        if (Settings.System.canWrite(ctx)) return
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.gesture_write_settings_title)
            .setMessage(R.string.gesture_write_settings_message)
            .setPositiveButton(R.string.screenshot_gesture_open_settings) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + ctx.packageName),
                )
                runCatching { fragment.startActivity(intent) }
                    .onFailure { Timber.w(it, "EdgeGestureConfigManager: cannot open WRITE_SETTINGS screen") }
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(fragment)
    }

    /**
     * S1036: stores the package the host's app picker returned into this slot's payload. It reuses the
     * same per-slot payload OPEN_URL writes, so no second mutator and no second storage key exist.
     */
    fun onAppPicked(
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection,
        packageName: String,
    ) {
        viewModel.updateSettings(applyPayload(viewModel.settings.value, zone, direction, packageName))
    }

    // S1038: per-slot URL entry for the OPEN_URL action, pre-filled with the current payload for editing.
    private fun promptUrl(zone: ScreenshotGestureZone, direction: ScreenshotGestureDirection) {
        val ctx = fragment.requireContext()
        val input = EditText(ctx).apply {
            setText(viewModel.settings.value.screenshotGesturePayload(zone, direction))
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setHint(R.string.gesture_url_input_hint)
        }
        val pad = (URL_DIALOG_PADDING_DP * ctx.resources.displayMetrics.density).toInt()
        val container = FrameLayout(ctx).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.gesture_url_input_title)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val url = input.text?.toString()?.trim().orEmpty()
                viewModel.updateSettings(applyPayload(viewModel.settings.value, zone, direction, url))
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(fragment)
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
        ScreenshotGestureDirection.entries.forEach { direction -> renderAppRow(settings, zone, direction) }
    }

    /**
     * S1036: the app row belongs to one direction and is on screen only while that direction launches an
     * app. A package that no longer resolves renders as not-chosen, which is what the dispatcher does
     * with it too - the dialog must not claim an app the gesture can no longer open.
     */
    private fun renderAppRow(
        settings: AppSettings,
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection,
    ) {
        val row = appRow(zone, direction)
        val launchesApp = settings.screenshotGestureAction(zone, direction) == ScreenshotGestureAction.OPEN_APP
        row.isVisible = launchesApp
        if (!launchesApp) return
        val notChosen = fragment.getString(R.string.gesture_slot_app_none)
        val packageName = settings.screenshotGesturePayload(zone, direction)
        row.setValue(notChosen)
        if (packageName.isEmpty()) return
        appSlotHost.resolveAppLabel(packageName) { label -> row.setValue(label ?: notChosen) }
    }

    // Mirrors applyPayload's slot mapping so a direction and its app row are read the same way.
    private fun appRow(
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection,
    ): SettingsSelectionRow = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> when (direction) {
            ScreenshotGestureDirection.UP -> binding.rowGestureLeftTopUpApp
            ScreenshotGestureDirection.RIGHT -> binding.rowGestureLeftTopRightApp
            ScreenshotGestureDirection.DOWN -> binding.rowGestureLeftTopDownApp
        }
        ScreenshotGestureZone.LEFT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.UP -> binding.rowGestureLeftBottomUpApp
            ScreenshotGestureDirection.RIGHT -> binding.rowGestureLeftBottomRightApp
            ScreenshotGestureDirection.DOWN -> binding.rowGestureLeftBottomDownApp
        }
        ScreenshotGestureZone.RIGHT_TOP -> when (direction) {
            ScreenshotGestureDirection.UP -> binding.rowGestureRightTopUpApp
            ScreenshotGestureDirection.RIGHT -> binding.rowGestureRightTopRightApp
            ScreenshotGestureDirection.DOWN -> binding.rowGestureRightTopDownApp
        }
        ScreenshotGestureZone.RIGHT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.UP -> binding.rowGestureRightBottomUpApp
            ScreenshotGestureDirection.RIGHT -> binding.rowGestureRightBottomRightApp
            ScreenshotGestureDirection.DOWN -> binding.rowGestureRightBottomDownApp
        }
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
        // S1408: both ways of choosing a zone land here - the tab itself, and the schema tap that
        // selects that tab - so the marker follows either without a second path into the view.
        selectedZone = tabZones.getOrNull(position)
        Timber.d("S1408: edge gesture zone selected -> ${selectedZone?.name}")
        binding.edgeGestureSchema.setState(buildSchemaState(viewModel.settings.value))
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
        return EdgeGestureSchemaView.SchemaState(zones, selectedZone)
    }

    private fun applyEnabled(s: AppSettings, zone: ScreenshotGestureZone, enabled: Boolean): AppSettings = when (zone) {
        ScreenshotGestureZone.LEFT_TOP ->
            s.copy(screenshotGesture = s.screenshotGesture.copy(zoneLeftTopEnabled = enabled))
        ScreenshotGestureZone.LEFT_BOTTOM ->
            s.copy(screenshotGesture = s.screenshotGesture.copy(zoneLeftBottomEnabled = enabled))
        ScreenshotGestureZone.RIGHT_TOP ->
            s.copy(screenshotGesture = s.screenshotGesture.copy(zoneRightTopEnabled = enabled))
        ScreenshotGestureZone.RIGHT_BOTTOM ->
            s.copy(screenshotGesture = s.screenshotGesture.copy(zoneRightBottomEnabled = enabled))
    }

    private fun applyStripVisible(
        s: AppSettings,
        zone: ScreenshotGestureZone,
        visible: Boolean,
    ): AppSettings = when (zone) {
        ScreenshotGestureZone.LEFT_TOP ->
            s.copy(screenshotGesture = s.screenshotGesture.copy(zoneLeftTopStripVisible = visible))
        ScreenshotGestureZone.LEFT_BOTTOM ->
            s.copy(screenshotGesture = s.screenshotGesture.copy(zoneLeftBottomStripVisible = visible))
        ScreenshotGestureZone.RIGHT_TOP ->
            s.copy(screenshotGesture = s.screenshotGesture.copy(zoneRightTopStripVisible = visible))
        ScreenshotGestureZone.RIGHT_BOTTOM ->
            s.copy(screenshotGesture = s.screenshotGesture.copy(zoneRightBottomStripVisible = visible))
    }

    private fun applyAction(
        s: AppSettings,
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection,
        action: ScreenshotGestureAction,
    ): AppSettings = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> when (direction) {
            ScreenshotGestureDirection.UP ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(leftTopUp = action))
            ScreenshotGestureDirection.RIGHT ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(leftTopRight = action))
            ScreenshotGestureDirection.DOWN ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(leftTopDown = action))
        }
        ScreenshotGestureZone.LEFT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.UP ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(leftBottomUp = action))
            ScreenshotGestureDirection.RIGHT ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(leftBottomRight = action))
            ScreenshotGestureDirection.DOWN ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(leftBottomDown = action))
        }
        ScreenshotGestureZone.RIGHT_TOP -> when (direction) {
            ScreenshotGestureDirection.UP ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(rightTopUp = action))
            ScreenshotGestureDirection.RIGHT ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(rightTopRight = action))
            ScreenshotGestureDirection.DOWN ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(rightTopDown = action))
        }
        ScreenshotGestureZone.RIGHT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.UP ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(rightBottomUp = action))
            ScreenshotGestureDirection.RIGHT ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(rightBottomRight = action))
            ScreenshotGestureDirection.DOWN ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(rightBottomDown = action))
        }
    }

    // S1038: writes the per-slot payload (the OPEN_URL address). Mirrors applyAction's slot mapping.
    private fun applyPayload(
        s: AppSettings,
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection,
        payload: String,
    ): AppSettings = when (zone) {
        ScreenshotGestureZone.LEFT_TOP -> when (direction) {
            ScreenshotGestureDirection.UP ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadLeftTopUp = payload))
            ScreenshotGestureDirection.RIGHT ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadLeftTopRight = payload))
            ScreenshotGestureDirection.DOWN ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadLeftTopDown = payload))
        }
        ScreenshotGestureZone.LEFT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.UP ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadLeftBottomUp = payload))
            ScreenshotGestureDirection.RIGHT ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadLeftBottomRight = payload))
            ScreenshotGestureDirection.DOWN ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadLeftBottomDown = payload))
        }
        ScreenshotGestureZone.RIGHT_TOP -> when (direction) {
            ScreenshotGestureDirection.UP ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadRightTopUp = payload))
            ScreenshotGestureDirection.RIGHT ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadRightTopRight = payload))
            ScreenshotGestureDirection.DOWN ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadRightTopDown = payload))
        }
        ScreenshotGestureZone.RIGHT_BOTTOM -> when (direction) {
            ScreenshotGestureDirection.UP ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadRightBottomUp = payload))
            ScreenshotGestureDirection.RIGHT ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadRightBottomRight = payload))
            ScreenshotGestureDirection.DOWN ->
                s.copy(screenshotGesture = s.screenshotGesture.copy(payloadRightBottomDown = payload))
        }
    }

    private companion object {
        private const val URL_DIALOG_PADDING_DP = 24
        private const val RESET_ICON_SIZE_DP = 36
    }
}
