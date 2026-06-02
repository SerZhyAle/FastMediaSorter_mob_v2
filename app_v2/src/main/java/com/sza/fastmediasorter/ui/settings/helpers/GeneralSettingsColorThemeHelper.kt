package com.sza.fastmediasorter.ui.settings.helpers

import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber

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
        else -> "AUTO"
    }

    private fun valueToPosition(value: String): Int = when (value) {
        "LIGHT" -> 1
        "DARK" -> 2
        else -> 0
    }

    fun setup() {
        val options = fragment.resources.getStringArray(R.array.color_theme_options)
        val adapter = ArrayAdapter(fragment.requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        setIsUpdatingSpinner(true)
        binding.spinnerColorTheme.adapter = adapter
        binding.spinnerColorTheme.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (getIsUpdatingSpinner()) return
                val newValue = positionToValue(position)
                val current = viewModel.settings.value
                if (newValue == current.colorTheme) return
                showRestartDialog(current.colorTheme, newValue)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        binding.spinnerColorTheme.post { setIsUpdatingSpinner(false) }

        fragment.viewLifecycleOwner.collectOnLifecycle(viewModel.settings) { settings ->
            val position = valueToPosition(settings.colorTheme)
            if (binding.spinnerColorTheme.selectedItemPosition != position) {
                setIsUpdatingSpinner(true)
                binding.spinnerColorTheme.setSelection(position, false)
                binding.spinnerColorTheme.post { setIsUpdatingSpinner(false) }
            }
        }
    }

    private fun showRestartDialog(previousValue: String, newValue: String) {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.restart_required_title)
            .setMessage(R.string.restart_required_message)
            .setCancelable(false)
            .setPositiveButton(R.string.restart_now) { _, _ ->
                Timber.d("S0328: color theme changed in settings -> $newValue, restarting")
                val current = viewModel.settings.value
                viewModel.updateSettings(current.copy(colorTheme = newValue))
                ColorThemePrefs.setMode(fragment.requireContext(), newValue)
                ColorThemePrefs.applyMode(newValue)
                LocaleHelper.markReturnToSettings(fragment.requireContext())
                LocaleHelper.restartApp(fragment.requireActivity())
            }
            .setNegativeButton(R.string.restart_later) { dialog, _ ->
                setIsUpdatingSpinner(true)
                binding.spinnerColorTheme.setSelection(valueToPosition(previousValue), false)
                binding.spinnerColorTheme.post { setIsUpdatingSpinner(false) }
                dialog.dismiss()
            }
            .show()
    }
}
