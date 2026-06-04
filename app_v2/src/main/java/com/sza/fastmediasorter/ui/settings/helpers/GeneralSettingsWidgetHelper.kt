package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.widget.registry.HomeWidgetCatalog
import com.sza.fastmediasorter.widget.registry.HomeWidgetEntry
import com.sza.fastmediasorter.widget.registry.HomeWidgetPinner
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Settings manager for the "Add widget to home screen" action.
 *
 * Opens an availability-filtered picker of pinnable widgets ([HomeWidgetCatalog.availableEntries])
 * and pins the chosen one through the system flow ([HomeWidgetPinner]), with an explicit
 * unsupported-launcher fallback toast. The fragment owns no business logic (Rule 3).
 */
class GeneralSettingsWidgetHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
    private val catalog: HomeWidgetCatalog,
    private val pinner: HomeWidgetPinner,
) {

    fun setup() {
        binding.buttonAddHomeWidget.setOnClickListener { onAddWidgetClicked() }
    }

    private fun onAddWidgetClicked() {
        Timber.d("S0348: settings add-widget picker opened")
        val context = fragment.requireContext()
        if (!pinner.isSupported()) {
            Toast.makeText(context, R.string.widget_pin_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val entries = catalog.availableEntries()
            if (!fragment.isAdded || fragment.view == null) return@launch
            // Calculator has no flavor or setting gate, so the list is never empty in practice;
            // the guard is purely defensive against an unexpected empty result.
            if (entries.isEmpty()) return@launch
            showPickerDialog(entries)
        }
    }

    private fun showPickerDialog(entries: List<HomeWidgetEntry>) {
        val context = fragment.requireContext()
        val labels = entries.map { context.getString(it.labelRes) }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.widget_picker_dialog_title)
            .setItems(labels) { _, which ->
                val entry = entries[which]
                Timber.d("S0348: widget pin requested from settings picker: ${entry.providerClass.simpleName}")
                val requested = pinner.requestPin(entry.component(context), null)
                if (!requested) {
                    Toast.makeText(context, R.string.widget_pin_not_supported, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
