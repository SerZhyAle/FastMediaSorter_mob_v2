package com.sza.fastmediasorter.ui.exchange.helpers

import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.remote.exchange.ExchangeSettingsGetResult
import com.sza.fastmediasorter.data.remote.exchange.ExchangeSettingsSaveResult
import com.sza.fastmediasorter.databinding.DialogExchangeStandardSettingsBinding
import com.sza.fastmediasorter.domain.exchange.StandardSettingsSerializer
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.usecase.exchange.ApplyStandardSettingsUseCase
import com.sza.fastmediasorter.domain.usecase.exchange.SaveStandardSettingsUseCase
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages the dialog for selecting standard settings groups and performing Save as STANDARD and Read STANDARD.
 */
class ExchangeStandardSettingsDialogManager(
    private val activity: AppCompatActivity,
    private val saveUseCase: SaveStandardSettingsUseCase,
    private val applyUseCase: ApplyStandardSettingsUseCase,
    private val scope: CoroutineScope
) {

    private var dialog: AlertDialog? = null
    private var binding: DialogExchangeStandardSettingsBinding? = null

    fun show(entry: DeviceExchangeAccountEntry) {
        Timber.d("S4536: standard settings dialog opened")
        dismiss()
        val view = DialogExchangeStandardSettingsBinding.inflate(LayoutInflater.from(activity))
        binding = view

        val built = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.exchange_standard_settings_title)
            .setView(view.root)
            .setPositiveButton(R.string.exchange_standard_settings_save_btn, null)
            .setNeutralButton(R.string.exchange_standard_settings_read_btn, null)
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity) ?: return

        dialog = built

        built.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            performSave(entry, view, built)
        }
        built.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            performRead(entry, view, built)
        }
    }

    private fun getSelectedGroups(view: DialogExchangeStandardSettingsBinding): Set<String> {
        val selected = mutableSetOf<String>()
        if (view.cbGroupGeneral.isChecked) selected.add(StandardSettingsSerializer.GROUP_GENERAL)
        if (view.cbGroupAppearance.isChecked) selected.add(StandardSettingsSerializer.GROUP_APPEARANCE)
        if (view.cbGroupPlayback.isChecked) selected.add(StandardSettingsSerializer.GROUP_PLAYBACK)
        if (view.cbGroupShortcuts.isChecked) selected.add(StandardSettingsSerializer.GROUP_SHORTCUTS)
        if (view.cbGroupBehavior.isChecked) selected.add(StandardSettingsSerializer.GROUP_BEHAVIOR)
        return selected
    }

    private fun setBusy(view: DialogExchangeStandardSettingsBinding, dialog: AlertDialog, busy: Boolean) {
        view.progressStandardSettings.isVisible = busy
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !busy
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = !busy
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !busy
    }

    private fun performSave(
        entry: DeviceExchangeAccountEntry,
        view: DialogExchangeStandardSettingsBinding,
        dialog: AlertDialog
    ) {
        val groups = getSelectedGroups(view)
        setBusy(view, dialog, true)
        scope.launch {
            Timber.d("S4536: save standard groups=$groups")
            val result = saveUseCase(entry, groups)
            setBusy(view, dialog, false)
            when (result) {
                is ExchangeSettingsSaveResult.Success -> {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.exchange_standard_settings_save_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
                is ExchangeSettingsSaveResult.Refused -> {
                    view.textStandardSettingsStatus.isVisible = true
                    view.textStandardSettingsStatus.text = result.message ?: result.reason
                }
                is ExchangeSettingsSaveResult.Error -> {
                    view.textStandardSettingsStatus.isVisible = true
                    view.textStandardSettingsStatus.text = result.cause.message
                }
            }
        }
    }

    private fun performRead(
        entry: DeviceExchangeAccountEntry,
        view: DialogExchangeStandardSettingsBinding,
        dialog: AlertDialog
    ) {
        val groups = getSelectedGroups(view)
        setBusy(view, dialog, true)
        scope.launch {
            Timber.d("S4536: read standard groups=$groups")
            val result = applyUseCase(entry, groups)
            setBusy(view, dialog, false)
            when (result) {
                is ExchangeSettingsGetResult.Success -> {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.exchange_standard_settings_read_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
                is ExchangeSettingsGetResult.Refused -> {
                    view.textStandardSettingsStatus.isVisible = true
                    view.textStandardSettingsStatus.text = result.message ?: result.reason
                }
                is ExchangeSettingsGetResult.Error -> {
                    view.textStandardSettingsStatus.isVisible = true
                    view.textStandardSettingsStatus.text = result.cause.message
                }
            }
        }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        binding = null
    }
}
