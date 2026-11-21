package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandLogEntry
import com.sza.fastmediasorter.ui.settings.RemoteControlSettingsViewModel
import com.sza.fastmediasorter.ui.settings.RemoteControlUiState
import com.sza.fastmediasorter.utils.collectOnLifecycle

/**
 * Binds the "Accept commands from my devices" rows of the "Access from anywhere" card (S4701): the switch, the
 * "Who can control" button and the latest log lines. Every decision is the view model's; this class only shows
 * its state. Without a held exchange entry that carries commands the rows are disabled and one reason line
 * says why - a switch already on stays usable, so the person can always turn listening off.
 */
class RemoteControlSettingsPanelManager(
    private val fragment: Fragment,
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: RemoteControlSettingsViewModel,
    private val controllersDialog: RemoteControlControllersDialogManager,
) {

    private val context get() = fragment.requireContext()

    fun bind() {
        binding.rowRemoteControlEnabled.setOnCheckedChangeListener { on -> viewModel.setListening(on) }
        binding.btnRemoteControlControllers.setOnClickListener { controllersDialog.show() }
        fragment.collectOnLifecycle(viewModel.state, block = ::render)
    }

    private fun render(state: RemoteControlUiState) {
        val usable = state.canListen || state.listening
        binding.rowRemoteControlEnabled.setCheckedSilently(state.listening)
        binding.rowRemoteControlEnabled.isEnabled = usable
        binding.btnRemoteControlControllers.isEnabled = usable
        binding.textRemoteControlDisabledReason.isVisible = !state.canListen
        renderLog(state.log)
    }

    private fun renderLog(entries: List<RemoteCommandLogEntry>) {
        val container = binding.layoutRemoteControlLog
        container.removeAllViews()
        val lines = if (entries.isEmpty()) {
            listOf(context.getString(R.string.remote_control_log_empty))
        } else {
            entries.takeLast(LOG_LINES).asReversed().map { RemoteCommandLogText.line(context, it) }
        }
        lines.forEach { text -> container.addView(TextView(context).apply { this.text = text }) }
    }

    private companion object {
        const val LOG_LINES = 5
    }
}
