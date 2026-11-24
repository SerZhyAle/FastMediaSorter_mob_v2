package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.ui.remotecontrol.ExternalControlActivity
import com.sza.fastmediasorter.ui.settings.RemoteControlSettingsResult
import com.sza.fastmediasorter.ui.settings.RemoteControlSettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber

class RemoteControlSettingsPanelManager(
    private val fragment: Fragment,
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: RemoteControlSettingsViewModel,
) {
    private var listening = false

    fun bind() {
        val row = binding.rowRemoteControlEnabled
        row.isEnabled = false
        row.setOnCheckedChangeListener { on ->
            row.setCheckedSilently(listening)
            viewModel.setListening(on)
        }
        binding.btnOpenExternalControl.setOnClickListener { open() }
        fragment.collectOnLifecycle(viewModel.listening) {
            listening = it
            row.setCheckedSilently(it)
            row.isEnabled = true
        }
        fragment.collectOnLifecycle(viewModel.results) { result ->
            when (result) {
                RemoteControlSettingsResult.OpenControllers -> open(openControllers = true)
                RemoteControlSettingsResult.SaveFailed ->
                    Toast.makeText(fragment.requireContext(), R.string.external_control_save_failed, Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    private fun open(openControllers: Boolean = false) {
        Timber.d("S4817: external control opened from settings at T3=%b", openControllers)
        fragment.startActivity(ExternalControlActivity.intent(fragment.requireContext(), openControllers))
    }
}
