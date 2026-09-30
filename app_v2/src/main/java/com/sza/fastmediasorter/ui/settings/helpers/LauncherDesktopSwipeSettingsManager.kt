package com.sza.fastmediasorter.ui.settings.helpers

import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogLauncherSettingsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeDirection
import com.sza.fastmediasorter.domain.usecase.panel.QueryLaunchableAppsUseCase
import com.sza.fastmediasorter.ui.common.widget.SettingsSelectionRow
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Owns the desktop-swipe row family while the dialog remains its lifecycle host. */
class LauncherDesktopSwipeSettingsManager(
    private val host: DialogFragment,
    private val binding: DialogLauncherSettingsBinding,
    private val currentSettings: () -> AppSettings,
    private val updateSettings: ((AppSettings) -> AppSettings) -> Unit,
    private val picker: LauncherDesktopSwipeActionPickerManager,
    private val payloadPicker: LauncherSwipePayloadPickerManager,
    private val queryLaunchableApps: QueryLaunchableAppsUseCase,
) {

    // S2256: one lookup per dialog. The installed-app set cannot change while a modal dialog is up, and
    // resolving a package label per render would repeat the same query on every settings emission. Held as
    // the in-flight query, so the rows of the first render share one lookup instead of starting one each.
    private var appLabels: Deferred<Map<String, String>>? = null

    // The last render of a row wins: a label still pending from an earlier render must not land after it.
    private val labelJobs = mutableMapOf<LauncherDesktopSwipeDirection, Job>()

    fun setupRows() {
        LauncherDesktopSwipeDirection.entries.forEach { direction ->
            actionRow(direction).setOnRowClickListener { showPicker(direction) }
            val target = targetRow(direction)
            target.setOnRowClickListener {
                payloadPicker.openTargetPicker(direction, direction.actionOf(currentSettings()))
            }
            // The reset is the only path that clears a stored target: re-picking the same action must
            // not drop it, which is the contract the edge slots already carry.
            target.setTrailingControl(
                GestureTargetResetControl.create(host.requireContext()) {
                    renderTargetRow(payloadPicker.clearTarget(direction), direction)
                },
            )
        }
    }

    fun registerAppPickerListener() {
        payloadPicker.registerAppPickerListener()
    }

    fun render(settings: AppSettings) {
        LauncherDesktopSwipeDirection.entries.forEach { direction ->
            actionRow(direction).setValue(
                picker.labelFor(host.requireContext(), direction.actionOf(settings))
            )
            renderTargetRow(settings, direction)
        }
    }

    private fun showPicker(direction: LauncherDesktopSwipeDirection) {
        val current = direction.actionOf(currentSettings())
        picker.showPicker(host.requireContext(), host.viewLifecycleOwner, current) { picked ->
            updateSettings { direction.withAction(it, picked) }
            // The target is asked for right after the action that needs one, as the edge slots do; the
            // row below stays the way back to it once the dialog is dismissed.
            payloadPicker.openTargetPicker(direction, picked)
        }
    }

    /**
     * The row is present only while the assigned action carries a target, so a direction bound to, say,
     * the flashlight does not show an empty "App to launch" line under it.
     */
    private fun renderTargetRow(settings: AppSettings, direction: LauncherDesktopSwipeDirection) {
        labelJobs.remove(direction)?.cancel()
        val row = targetRow(direction)
        val kind = payloadPicker.targetKindOf(direction.actionOf(settings))
        row.isVisible = kind != null
        if (kind == null) return
        val payload = direction.payloadOf(settings)
        when (kind) {
            GestureTargetKind.APP -> {
                row.setTitle(host.getString(R.string.gesture_slot_app_label))
                row.setValue(host.getString(R.string.gesture_slot_app_none))
                if (payload.isNotEmpty()) resolveAppLabel(direction, payload) { row.setValue(it) }
            }
            GestureTargetKind.URL -> {
                row.setTitle(host.getString(R.string.gesture_url_input_title))
                row.setValue(payload.ifEmpty { host.getString(R.string.gesture_slot_url_none) })
            }
        }
    }

    /** Falls back to the "not chosen" wording when the chosen app has since been removed or disabled. */
    private fun resolveAppLabel(
        direction: LauncherDesktopSwipeDirection,
        packageName: String,
        onResolved: (String) -> Unit,
    ) {
        val notChosen = host.getString(R.string.gesture_slot_app_none)
        val scope = host.viewLifecycleOwner.lifecycleScope
        val labels = appLabels ?: scope.async {
            queryLaunchableApps().associate { it.packageName to it.label }
        }.also { appLabels = it }
        labelJobs[direction] = scope.launch { onResolved(labels.await()[packageName] ?: notChosen) }
    }

    private fun actionRow(direction: LauncherDesktopSwipeDirection): SettingsSelectionRow = when (direction) {
        LauncherDesktopSwipeDirection.UP -> binding.rowLauncherDesktopSwipeUp
        LauncherDesktopSwipeDirection.DOWN -> binding.rowLauncherDesktopSwipeDown
        LauncherDesktopSwipeDirection.LEFT -> binding.rowLauncherDesktopSwipeLeft
        LauncherDesktopSwipeDirection.RIGHT -> binding.rowLauncherDesktopSwipeRight
    }

    private fun targetRow(direction: LauncherDesktopSwipeDirection): SettingsSelectionRow = when (direction) {
        LauncherDesktopSwipeDirection.UP -> binding.rowLauncherDesktopSwipeUpTarget
        LauncherDesktopSwipeDirection.DOWN -> binding.rowLauncherDesktopSwipeDownTarget
        LauncherDesktopSwipeDirection.LEFT -> binding.rowLauncherDesktopSwipeLeftTarget
        LauncherDesktopSwipeDirection.RIGHT -> binding.rowLauncherDesktopSwipeRightTarget
    }
}
