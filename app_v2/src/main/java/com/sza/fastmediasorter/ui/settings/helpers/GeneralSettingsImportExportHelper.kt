package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

class GeneralSettingsImportExportHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val importSettingsFileLauncher: ActivityResultLauncher<String>,
) {
    private fun getImportFailureMessage(errorMessage: String?): String {
        // Keep the missing-backup guidance specific without surfacing raw exception text.
        return if (errorMessage?.contains("File not found", ignoreCase = true) == true) {
            fragment.getString(R.string.import_file_not_found)
        } else {
            fragment.getString(R.string.import_failed)
        }
    }

    fun showExportSettingsConfirmation() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.export_settings_confirm_title)
            .setMessage(R.string.export_settings_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> exportSettings() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showImportSettingsConfirmation() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.import_settings_confirm_title)
            .setMessage(R.string.import_settings_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> importSettings() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun importSettingsFromUri(uri: android.net.Uri) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                Timber.d("ImportSettings: Using SAF URI: $uri")
                val result = viewModel.importSettingsUseCase(uri.toString())
                result.onSuccess {
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.import_success), Toast.LENGTH_LONG).show()
                    showRestartAfterImportDialog()
                }.onFailure { error ->
                    com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog.show(
                        context = fragment.requireContext(),
                        title = fragment.getString(R.string.import_failed_title),
                        message = getImportFailureMessage(error.message),
                        showSave = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Import settings from URI failed")
                Toast.makeText(fragment.requireContext(), getImportFailureMessage(e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportSettings() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = viewModel.exportSettingsUseCase()
                result.onSuccess {
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.export_success), Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Timber.w(error, "Export settings returned a failure result")
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.export_failed), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Export settings failed")
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.export_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importSettings() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.import_method_title)
            .setItems(arrayOf(fragment.getString(R.string.import_auto), fragment.getString(R.string.import_browse))) { _, which ->
                when (which) {
                    0 -> importSettingsAuto()
                    1 -> {
                        try {
                            importSettingsFileLauncher.launch("*/*")
                        } catch (e: android.content.ActivityNotFoundException) {
                            Timber.w(e, "GeneralSettingsFragment: file picker not available")
                            Toast.makeText(fragment.requireContext(), R.string.save_logs_not_supported, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importSettingsAuto() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = viewModel.importSettingsUseCase()
                result.onSuccess {
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.import_success), Toast.LENGTH_LONG).show()
                    showRestartAfterImportDialog()
                }.onFailure { error ->
                    com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog.show(
                        context = fragment.requireContext(),
                        title = fragment.getString(R.string.import_failed_title),
                        message = getImportFailureMessage(error.message),
                        showSave = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Import settings failed")
                Toast.makeText(fragment.requireContext(), getImportFailureMessage(e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showRestartAfterImportDialog() {
        androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.restart_required_title)
            .setMessage(R.string.restart_required_message)
            .setPositiveButton(R.string.restart_now) { _, _ ->
                LocaleHelper.markReturnToSettings(fragment.requireContext())
                LocaleHelper.restartApp(fragment.requireActivity())
            }
            .setNegativeButton(R.string.restart_later) { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }
}
