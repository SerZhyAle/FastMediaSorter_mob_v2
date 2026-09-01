package com.sza.fastmediasorter.ui.settings.helpers

import android.text.InputType
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogLauncherSettingsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.util.showBoundTo

/** Owns the screen-timeout row and restores its stored value after an abandoned custom entry. */
class LauncherScreenTimeoutSettingsManager(
    private val host: DialogFragment,
    private val binding: DialogLauncherSettingsBinding,
    private val currentSettings: () -> AppSettings,
    private val isUpdating: () -> Boolean,
    private val updateSettings: (AppSettings) -> Unit,
) {
    fun setupRow() {
        binding.rowLauncherScreenTimeout.setOnItemSelectedListener { index ->
            if (isUpdating()) return@setOnItemSelectedListener
            val presets = AppSettings.LAUNCHER_SCREEN_TIMEOUT_PRESETS
            when {
                index in presets.indices -> updateTimeout(presets[index])
                index == presets.size -> showCustomTimeoutDialog()
            }
        }
    }

    fun render(settings: AppSettings) {
        val presets = AppSettings.LAUNCHER_SCREEN_TIMEOUT_PRESETS
        val seconds = settings.launcherScreenBlackoutTimeoutSeconds
        val customLabel = if (seconds !in presets && seconds > 0) {
            host.getString(R.string.launcher_settings_screen_timeout_custom_format, seconds)
        } else {
            host.getString(R.string.launcher_settings_screen_timeout_custom)
        }
        binding.rowLauncherScreenTimeout.setEntries(
            listOf(
                host.getText(R.string.launcher_settings_screen_timeout_off),
                host.getText(R.string.launcher_settings_screen_timeout_5s),
                host.getText(R.string.launcher_settings_screen_timeout_15s),
                host.getText(R.string.launcher_settings_screen_timeout_30s),
                host.getText(R.string.launcher_settings_screen_timeout_60s),
                host.getText(R.string.launcher_settings_screen_timeout_300s),
                customLabel,
            ),
        )
        binding.rowLauncherScreenTimeout.setSelection(
            presets.indexOf(seconds).takeIf { it >= 0 } ?: presets.size,
        )
    }

    private fun showCustomTimeoutDialog() {
        val context = host.requireContext()
        val current = currentSettings().launcherScreenBlackoutTimeoutSeconds
        val input = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.takeIf { it > 0 }?.toString().orEmpty())
            hint = host.getString(R.string.launcher_settings_screen_timeout_dialog_hint)
            setSingleLine()
            setSelection(text?.length ?: 0)
        }
        val container = FrameLayout(context).apply {
            val margin = resources.getDimensionPixelSize(R.dimen.margin_normal)
            setPadding(margin, margin / 2, margin, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.launcher_settings_screen_timeout_dialog_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                input.text?.toString()?.trim()?.toIntOrNull()?.takeIf { it > 0 }
                    ?.let(::updateTimeout)
                    ?: render(currentSettings())
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> render(currentSettings()) }
            .setOnCancelListener { render(currentSettings()) }
            .showBoundTo(host)
    }

    private fun updateTimeout(seconds: Int) {
        val current = currentSettings()
        updateSettings(current.withLauncher { copy(screenBlackoutTimeoutSeconds = seconds) })
    }
}
