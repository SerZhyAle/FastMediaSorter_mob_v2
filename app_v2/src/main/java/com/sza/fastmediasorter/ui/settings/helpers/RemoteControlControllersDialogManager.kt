package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogRemoteControlControllersBinding
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentMode
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerChoice
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.ui.common.help.DialogHelpEntryManager
import com.sza.fastmediasorter.ui.settings.RemoteControlSettingsViewModel
import com.sza.fastmediasorter.ui.settings.RemoteControllersUiState
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle

/**
 * "Who can control this phone" (S4701): the account's devices that hold a key, each with a switch and, once
 * granted, one switch per risk class with "Ask me first" or "Always allow". The view model owns every decision;
 * this class shows its state and forwards each tap. Choosing "Always allow" for any class but `safe` first asks
 * once more, in words, what that lets the device do (owner wish, strategic 3.1 item 1).
 */
class RemoteControlControllersDialogManager(
    private val fragment: Fragment,
    private val viewModel: RemoteControlSettingsViewModel,
) {

    fun show() {
        val context = fragment.requireContext()
        val binding = DialogRemoteControlControllersBinding.inflate(LayoutInflater.from(context))
        val list = ControllerList(context, binding, viewModel, ::confirmAlways)
        viewModel.loadControllers()
        val job = fragment.viewLifecycleOwner.collectOnLifecycle(viewModel.controllers, block = list::render)
        val dialog = MaterialAlertDialogBuilder(context).setView(binding.root).create()
        dialog.setOnDismissListener { job.cancel() }
        binding.btnRemoteControlControllersDone.setOnClickListener { dialog.dismiss() }
        if (dialog.showBoundTo(fragment) == null) {
            job.cancel()
        } else {
            DialogHelpEntryManager.attach(dialog, SURFACE_ID)
        }
    }

    private fun confirmAlways(choice: RemoteControllerChoice, riskClass: RemoteRiskClass) {
        val context = fragment.requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.remote_control_always_warning_title)
            .setMessage(
                context.getString(
                    R.string.remote_control_always_warning_message,
                    choice.deviceName,
                    context.getString(riskClass.labelRes()),
                ),
            )
            .setPositiveButton(R.string.remote_control_mode_always) { _, _ ->
                viewModel.setMode(choice.deviceId, riskClass, RemoteConsentMode.ALWAYS)
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(fragment)
    }

    private companion object {
        const val SURFACE_ID = "dialog_remote_control_controllers"
    }
}

/** Keeps one row per device and updates it in place; rows are rebuilt only when the set of devices changes. */
private class ControllerList(
    private val context: Context,
    private val binding: DialogRemoteControlControllersBinding,
    private val viewModel: RemoteControlSettingsViewModel,
    private val confirmAlways: (RemoteControllerChoice, RemoteRiskClass) -> Unit,
) : RemoteControllerRowListener {

    private val rows = LinkedHashMap<String, RemoteControllerRowView>()
    private var choices: Map<String, RemoteControllerChoice> = emptyMap()

    // A bind sets the switches, which fire their listeners; those calls are not the person's taps.
    private var applyingState = false

    fun render(state: RemoteControllersUiState) {
        val message = when (state) {
            RemoteControllersUiState.Loading -> R.string.remote_control_controllers_loading
            RemoteControllersUiState.Failed -> R.string.remote_control_controllers_failed
            is RemoteControllersUiState.Loaded ->
                R.string.remote_control_controllers_empty.takeIf { state.choices.isEmpty() }
        }
        binding.textRemoteControlControllersState.isVisible = message != null
        message?.let(binding.textRemoteControlControllersState::setText)
        sync((state as? RemoteControllersUiState.Loaded)?.choices.orEmpty())
    }

    private fun sync(next: List<RemoteControllerChoice>) {
        choices = next.associateBy(RemoteControllerChoice::deviceId)
        if (rows.keys.toList() != next.map(RemoteControllerChoice::deviceId)) rebuild(next)
        applyingState = true
        next.forEach { rows.getValue(it.deviceId).bind(it) }
        applyingState = false
    }

    private fun rebuild(next: List<RemoteControllerChoice>) {
        binding.layoutRemoteControlControllers.removeAllViews()
        rows.clear()
        next.forEach { choice ->
            val row = RemoteControllerRowView(context, choice.deviceId, this)
            rows[choice.deviceId] = row
            binding.layoutRemoteControlControllers.addView(row.root)
        }
    }

    override fun onGranted(deviceId: String, granted: Boolean) {
        val choice = choices[deviceId]
        if (!applyingState && choice != null) viewModel.setGranted(choice, granted)
    }

    override fun onClassAllowed(deviceId: String, riskClass: RemoteRiskClass, allowed: Boolean) {
        if (!applyingState) viewModel.setClassAllowed(deviceId, riskClass, allowed)
    }

    override fun onModeTapped(deviceId: String, riskClass: RemoteRiskClass) {
        val choice = choices[deviceId] ?: return
        val next = when (choice.grant?.modeFor(riskClass)) {
            RemoteConsentMode.ASK -> RemoteConsentMode.ALWAYS
            else -> RemoteConsentMode.ASK
        }
        if (next == RemoteConsentMode.ALWAYS && riskClass != RemoteRiskClass.SAFE) {
            confirmAlways(choice, riskClass)
        } else {
            viewModel.setMode(deviceId, riskClass, next)
        }
    }
}
