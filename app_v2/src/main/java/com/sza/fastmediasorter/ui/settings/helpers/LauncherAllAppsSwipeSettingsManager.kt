package com.sza.fastmediasorter.ui.settings.helpers

import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogLauncherSettingsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.LauncherAllAppsSwipeAction
import com.sza.fastmediasorter.domain.model.LauncherAllAppsSwipeDirection
import com.sza.fastmediasorter.domain.usecase.panel.QueryLaunchableAppsUseCase
import com.sza.fastmediasorter.ui.applaunchpanel.edit.AppPickerDialogFragment
import com.sza.fastmediasorter.ui.common.widget.SettingsSelectionRow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S2304: owns the All apps panel swipe row family while the dialog remains its lifecycle host.
 *
 * The target flow is folded in rather than split off as it is for the desktop family, because this
 * surface offers exactly one action that carries a target. The contracts it shares with the other
 * surfaces are the ones a divergence would be a defect in: [GestureTargetKind] decides whether a target
 * row exists at all, the reset control is the only path that clears a stored target, and the chooser is
 * the same app picker.
 */
class LauncherAllAppsSwipeSettingsManager(
    private val host: DialogFragment,
    private val binding: DialogLauncherSettingsBinding,
    private val currentSettings: () -> AppSettings,
    private val updateSettings: (AppSettings) -> Unit,
    private val picker: LauncherAllAppsSwipeActionPickerManager,
    private val queryLaunchableApps: QueryLaunchableAppsUseCase,
    /**
     * The pending direction lives in the host, not here: this manager is rebuilt on every re-inflate and
     * the host process can die while the child app picker is open, so only the fragment can carry it
     * across that gap.
     */
    private val pendingDirection: () -> LauncherAllAppsSwipeDirection?,
    private val setPendingDirection: (LauncherAllAppsSwipeDirection?) -> Unit,
) {

    // One lookup per dialog: the installed-app set cannot change while a modal dialog is up.
    private var appLabels: Map<String, String>? = null

    fun setupRows() {
        LauncherAllAppsSwipeDirection.entries.forEach { direction ->
            actionRow(direction).setOnRowClickListener { showPicker(direction) }
            val target = targetRow(direction)
            target.setOnRowClickListener {
                openTargetPicker(direction, direction.actionOf(currentSettings()))
            }
            target.setTrailingControl(
                GestureTargetResetControl.create(host.requireContext()) {
                    renderTargetRow(writePayload(direction, ""), direction)
                },
            )
        }
    }

    /**
     * Keyed on the host fragment rather than its view lifecycle, so the listener survives a re-inflate
     * and the pick is not dropped between opening the picker and returning from it.
     */
    fun registerAppPickerListener() {
        host.childFragmentManager.setFragmentResultListener(REQUEST_KEY, host) { _, bundle ->
            val packageName = bundle.getString(AppPickerDialogFragment.RESULT_PACKAGE).orEmpty()
            val direction = pendingDirection()
            setPendingDirection(null)
            if (packageName.isNotEmpty() && direction != null) {
                renderTargetRow(writePayload(direction, packageName), direction)
            }
        }
    }

    fun render(settings: AppSettings) {
        LauncherAllAppsSwipeDirection.entries.forEach { direction ->
            actionRow(direction).setValue(
                picker.labelFor(host.requireContext(), direction.actionOf(settings))
            )
            renderTargetRow(settings, direction)
        }
    }

    private fun showPicker(direction: LauncherAllAppsSwipeDirection) {
        val current = direction.actionOf(currentSettings())
        picker.showPicker(host.requireContext(), host.viewLifecycleOwner, current) { picked ->
            updateSettings(direction.withAction(currentSettings(), picked))
            // The target is asked for right after the action that needs one, as every other slot family
            // does; the row below stays the way back to it once the dialog is dismissed.
            openTargetPicker(direction, picked)
        }
    }

    private fun openTargetPicker(
        direction: LauncherAllAppsSwipeDirection,
        action: LauncherAllAppsSwipeAction,
    ) {
        val kind = targetKindOf(action)
        Timber.d("S2304: all apps swipe target flow %s kind=%s", direction, kind)
        when (kind) {
            GestureTargetKind.APP -> {
                setPendingDirection(direction)
                AppPickerDialogFragment.newInstance(REQUEST_KEY)
                    .show(host.childFragmentManager, AppPickerDialogFragment.TAG)
            }
            GestureTargetKind.URL -> GestureUrlTargetDialog.show(
                host = host,
                current = direction.payloadOf(currentSettings()),
                onSave = { renderTargetRow(writePayload(direction, it), direction) },
            )
            null -> Unit
        }
    }

    private fun targetKindOf(action: LauncherAllAppsSwipeAction): GestureTargetKind? =
        (action as? LauncherAllAppsSwipeAction.EdgeGestureAction)?.action?.let { GestureTargetKind.of(it) }

    /**
     * The row is present only while the assigned action carries a target, so a direction bound to the
     * panel's own routes does not show an empty target line under it.
     */
    private fun renderTargetRow(settings: AppSettings, direction: LauncherAllAppsSwipeDirection) {
        val row = targetRow(direction)
        val kind = targetKindOf(direction.actionOf(settings))
        row.isVisible = kind != null
        if (kind == null) return
        val payload = direction.payloadOf(settings)
        when (kind) {
            GestureTargetKind.APP -> {
                row.setTitle(host.getString(R.string.gesture_slot_app_label))
                row.setValue(host.getString(R.string.gesture_slot_app_none))
                if (payload.isNotEmpty()) resolveAppLabel(payload) { row.setValue(it) }
            }
            GestureTargetKind.URL -> {
                row.setTitle(host.getString(R.string.gesture_url_input_title))
                row.setValue(payload.ifEmpty { host.getString(R.string.gesture_slot_url_none) })
            }
        }
    }

    /**
     * Answers with the settings just written: the settings flow has not emitted them yet, so a caller
     * re-rendering from [currentSettings] would still read the value being replaced.
     */
    private fun writePayload(direction: LauncherAllAppsSwipeDirection, value: String): AppSettings {
        val updated = direction.withPayload(currentSettings(), value)
        updateSettings(updated)
        return updated
    }

    /** Falls back to the not-chosen wording when the chosen app has since been removed or disabled. */
    private fun resolveAppLabel(packageName: String, onResolved: (String) -> Unit) {
        val notChosen = host.getString(R.string.gesture_slot_app_none)
        appLabels?.let {
            onResolved(it[packageName] ?: notChosen)
            return
        }
        host.viewLifecycleOwner.lifecycleScope.launch {
            val labels = queryLaunchableApps().associate { it.packageName to it.label }
            appLabels = labels
            onResolved(labels[packageName] ?: notChosen)
        }
    }

    private fun actionRow(direction: LauncherAllAppsSwipeDirection): SettingsSelectionRow = when (direction) {
        LauncherAllAppsSwipeDirection.UP -> binding.rowLauncherAllAppsSwipeUp
        LauncherAllAppsSwipeDirection.DOWN -> binding.rowLauncherAllAppsSwipeDown
        LauncherAllAppsSwipeDirection.LEFT -> binding.rowLauncherAllAppsSwipeLeft
        LauncherAllAppsSwipeDirection.RIGHT -> binding.rowLauncherAllAppsSwipeRight
    }

    private fun targetRow(direction: LauncherAllAppsSwipeDirection): SettingsSelectionRow = when (direction) {
        LauncherAllAppsSwipeDirection.UP -> binding.rowLauncherAllAppsSwipeUpTarget
        LauncherAllAppsSwipeDirection.DOWN -> binding.rowLauncherAllAppsSwipeDownTarget
        LauncherAllAppsSwipeDirection.LEFT -> binding.rowLauncherAllAppsSwipeLeftTarget
        LauncherAllAppsSwipeDirection.RIGHT -> binding.rowLauncherAllAppsSwipeRightTarget
    }

    private companion object {
        const val REQUEST_KEY = "launcher_all_apps_swipe_app_picker"
    }
}
