package com.sza.fastmediasorter.ui.settings.helpers

import android.text.InputType
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogLauncherSettingsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.common.widget.SettingsDropdownRow
import com.sza.fastmediasorter.util.showBoundTo

/**
 * Owns the two screen-timeout rows and restores their stored values after an abandoned custom entry.
 *
 * S3284 added the second row: the same countdown, applied while the charger is connected. Both rows
 * share this class because they share every rule - the preset list, the custom-seconds dialog and the
 * restore-on-cancel behaviour - and a second copy would drift from the first at the next change.
 *
 * @param systemActionsAvailable whether this build can reach a real device lock. S2384: a row promises
 * two different things depending on it - a locked device, or an app-drawn black surface over a lit
 * screen - and a caption that named only the first would be read as a defect wherever the second happens.
 */
class LauncherScreenTimeoutSettingsManager(
    private val host: DialogFragment,
    private val binding: DialogLauncherSettingsBinding,
    private val currentSettings: () -> AppSettings,
    private val isUpdating: () -> Boolean,
    private val systemActionsAvailable: Boolean,
    private val updateSettings: (AppSettings) -> Unit,
) {
    fun setupRow() {
        binding.rowLauncherScreenTimeout.setOnItemSelectedListener { index ->
            onIndexSelected(index, onCharge = false)
        }
        binding.rowLauncherScreenTimeoutOnCharge.setOnItemSelectedListener { index ->
            onIndexSelected(index, onCharge = true)
        }
    }

    fun render(settings: AppSettings) {
        binding.rowLauncherScreenTimeout.setSubtitle(
            if (systemActionsAvailable) {
                R.string.launcher_settings_screen_timeout_subtitle
            } else {
                R.string.launcher_settings_screen_timeout_subtitle_no_lock
            },
        )
        // The on-charge row keeps its own no-lock caption rather than borrowing the row above's: the two
        // sit next to each other, and one shared sentence made them read as the same setting twice
        // (observed on the test phone, 2026-09-18, where no lock service is reachable).
        binding.rowLauncherScreenTimeoutOnCharge.setSubtitle(
            if (systemActionsAvailable) {
                R.string.launcher_settings_screen_timeout_on_charge_subtitle
            } else {
                R.string.launcher_settings_screen_timeout_on_charge_subtitle_no_lock
            },
        )
        renderRow(binding.rowLauncherScreenTimeout, settings.launcherScreenBlackoutTimeoutSeconds)
        renderRow(
            binding.rowLauncherScreenTimeoutOnCharge,
            settings.launcherScreenBlackoutTimeoutOnChargeSeconds,
        )
    }

    private fun onIndexSelected(index: Int, onCharge: Boolean) {
        if (isUpdating()) return
        val presets = AppSettings.LAUNCHER_SCREEN_TIMEOUT_PRESETS
        when {
            index in presets.indices -> updateTimeout(presets[index], onCharge)
            index == presets.size -> showCustomTimeoutDialog(onCharge)
        }
    }

    private fun renderRow(row: SettingsDropdownRow, seconds: Int) {
        val presets = AppSettings.LAUNCHER_SCREEN_TIMEOUT_PRESETS
        val customLabel = if (seconds !in presets && seconds > 0) {
            host.getString(R.string.launcher_settings_screen_timeout_custom_format, seconds)
        } else {
            host.getString(R.string.launcher_settings_screen_timeout_custom)
        }
        row.setEntries(
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
        row.setSelection(presets.indexOf(seconds).takeIf { it >= 0 } ?: presets.size)
    }

    private fun showCustomTimeoutDialog(onCharge: Boolean) {
        val context = host.requireContext()
        val current = storedSeconds(currentSettings(), onCharge)
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
            .setTitle(dialogTitleRes(onCharge))
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                input.text?.toString()?.trim()?.toIntOrNull()?.takeIf { it > 0 }
                    ?.let { seconds -> updateTimeout(seconds, onCharge) }
                    ?: render(currentSettings())
            }
            .setNegativeButton(R.string.cancel) { _, _ -> render(currentSettings()) }
            .setOnCancelListener { render(currentSettings()) }
            .showBoundTo(host)
    }

    @StringRes
    private fun dialogTitleRes(onCharge: Boolean): Int = if (onCharge) {
        R.string.launcher_settings_screen_timeout_on_charge_dialog_title
    } else {
        R.string.launcher_settings_screen_timeout_dialog_title
    }

    private fun storedSeconds(settings: AppSettings, onCharge: Boolean): Int = if (onCharge) {
        settings.launcherScreenBlackoutTimeoutOnChargeSeconds
    } else {
        settings.launcherScreenBlackoutTimeoutSeconds
    }

    private fun updateTimeout(seconds: Int, onCharge: Boolean) {
        val current = currentSettings()
        val updated = if (onCharge) {
            current.withLauncher { copy(screenBlackoutTimeoutOnChargeSeconds = seconds) }
        } else {
            current.withLauncher { copy(screenBlackoutTimeoutSeconds = seconds) }
        }
        updateSettings(updated)
    }
}
