package com.sza.fastmediasorter.ui.settings.helpers

import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle

/**
 * S0328: owns the "Color theme" spinner in Settings → General.
 *
 * Maps the spinner position to the raw [com.sza.fastmediasorter.domain.model.AppSettings.colorTheme]
 * value (0 = AUTO, 1 = LIGHT, 2 = DARK), keeps the spinner in sync with the current settings, and on a
 * real user change persists the value, updates the synchronous startup mirror, and prompts for restart
 * (same pattern as language / compact-elements).
 */
class GeneralSettingsColorThemeHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val getIsUpdatingSpinner: () -> Boolean,
    private val setIsUpdatingSpinner: (Boolean) -> Unit,
) {
    private fun positionToValue(position: Int): String = when (position) {
        1 -> "LIGHT"
        2 -> "DARK"
        3 -> "DARK_GREEN"
        4 -> "DARK_BLUE"
        5 -> "DARK_RED"
        6 -> "LIGHT_GREEN"
        7 -> "LIGHT_BLUE"
        8 -> "LIGHT_RED"
        else -> "AUTO"
    }

    private fun valueToPosition(value: String): Int = when (value) {
        "LIGHT" -> 1
        "DARK" -> 2
        "DARK_GREEN" -> 3
        "DARK_BLUE" -> 4
        "DARK_RED" -> 5
        "LIGHT_GREEN" -> 6
        "LIGHT_BLUE" -> 7
        "LIGHT_RED" -> 8
        else -> 0
    }

    // S0567: spinnerColorTheme migrated from raw Spinner to SettingsDropdownRow (ADR-1).
    // Entries come from app:sdr_entries="@array/color_theme_options" in the layout.
    fun setup() {
        setIsUpdatingSpinner(true)
        binding.spinnerColorTheme.setOnItemSelectedListener { position ->
            if (getIsUpdatingSpinner()) return@setOnItemSelectedListener
            val newValue = positionToValue(position)
            val current = viewModel.settings.value
            if (newValue == current.colorTheme) return@setOnItemSelectedListener
            showRestartDialog(current.colorTheme, newValue)
        }
        binding.spinnerColorTheme.post { setIsUpdatingSpinner(false) }

        fragment.viewLifecycleOwner.collectOnLifecycle(viewModel.settings) { settings ->
            val position = valueToPosition(settings.colorTheme)
            if (binding.spinnerColorTheme.getSelectedIndex() != position) {
                setIsUpdatingSpinner(true)
                binding.spinnerColorTheme.setSelection(position)
                binding.spinnerColorTheme.post { setIsUpdatingSpinner(false) }
            }
        }
    }

    private fun showRestartDialog(previousValue: String, newValue: String) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.restart_app_title)
            .setMessage(R.string.restart_app_color_theme_message)
            .setCancelable(false)
            .setPositiveButton(R.string.restart) { _, _ ->
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(colorTheme = newValue))
                ColorThemePrefs.setMode(fragment.requireContext(), newValue)
                ColorThemePrefs.applyMode(newValue)
                LocaleHelper.markReturnToSettings(fragment.requireContext())
                LocaleHelper.restartApp(fragment.requireActivity())
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                setIsUpdatingSpinner(true)
                binding.spinnerColorTheme.setSelection(valueToPosition(previousValue))
                binding.spinnerColorTheme.post { setIsUpdatingSpinner(false) }
                dialog.dismiss()
            }
            .show()
    }
}
