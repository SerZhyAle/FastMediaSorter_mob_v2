package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class GeneralSettingsResetHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
) {
    fun showRememberFileListHelpDialog() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.remember_file_list_help_title)
            .setMessage(R.string.remember_file_list_help_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun showResetSettingsConfirmation() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.reset_settings_title)
            .setMessage(R.string.reset_settings_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> resetSettingsToDefaults() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showResetGeneralSectionConfirmation() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.reset_general_section_title)
            .setMessage(R.string.reset_general_section_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> resetGeneralSection() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun resetSmbConnections() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.reset_smb_connections_title)
            .setMessage(R.string.reset_smb_connections_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                binding.btnResetSmbConnections.isEnabled = false
                binding.btnResetSmbConnections.text = fragment.getString(R.string.please_wait)
                fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        viewModel.resetSmbConnectionsUseCase()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(fragment.requireContext(), R.string.reset_smb_success, Toast.LENGTH_SHORT).show()
                            binding.btnResetSmbConnections.isEnabled = true
                            binding.btnResetSmbConnections.text = fragment.getString(R.string.reset_smb_connections)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to reset SMB connections")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_reset_smb_failed), Toast.LENGTH_LONG).show()
                            binding.btnResetSmbConnections.isEnabled = true
                            binding.btnResetSmbConnections.text = fragment.getString(R.string.reset_smb_connections)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resetGeneralSection() {
        fragment.lifecycleScope.launch {
            try {
                viewModel.resetGeneralSection()
                Toast.makeText(fragment.requireContext(), R.string.reset_general_section_success, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset general section")
                Toast.makeText(
                    fragment.requireContext(),
                    fragment.getString(R.string.reset_general_section_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun resetSettingsToDefaults() {
        fragment.lifecycleScope.launch {
            try {
                viewModel.resetToDefaults()
                Toast.makeText(fragment.requireContext(), R.string.reset_settings_success, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset settings")
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_reset_settings_failed), Toast.LENGTH_LONG).show()
            }
        }
    }
}
