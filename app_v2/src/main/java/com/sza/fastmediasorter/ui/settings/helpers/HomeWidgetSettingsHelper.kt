package com.sza.fastmediasorter.ui.settings.helpers

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.ListSelectionAdapter
import com.sza.fastmediasorter.ui.dialog.ListSelectionConfig
import com.sza.fastmediasorter.ui.dialog.ListSelectionDialog
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
 * unsupported-launcher fallback toast. Binding-agnostic (takes the button view directly) so any
 * settings fragment can host the action; the fragment owns no business logic (Rule 3).
 */
class HomeWidgetSettingsHelper(
    private val button: MaterialButton,
    private val fragment: Fragment,
    private val catalog: HomeWidgetCatalog,
    private val pinner: HomeWidgetPinner,
) {

    fun setup() {
        button.setOnClickListener { onAddWidgetClicked() }
    }

    private fun onAddWidgetClicked() {
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
        ListSelectionDialog<HomeWidgetEntry>(
            context,
            ListSelectionConfig(
                title = context.getString(R.string.widget_picker_dialog_title),
                lifecycleOwner = fragment.viewLifecycleOwner,
                loader = { entries },
                formatter = object : ListSelectionAdapter.ItemFormatter<HomeWidgetEntry> {
                    override fun getDisplayName(item: HomeWidgetEntry): String =
                        context.getString(item.labelRes)

                    // S1165: show the same glyph the widget carries on the home screen, so the row
                    // is recognised by its picture rather than read.
                    override fun getIcon(item: HomeWidgetEntry): Drawable? =
                        ContextCompat.getDrawable(context, item.iconRes)
                },
                hasSelection = false,
                isSelected = { false },
                allowClear = false,
                emptyMessageRes = R.string.widget_pin_not_supported,
                errorMessageRes = R.string.widget_pin_not_supported,
                onSelected = { entry ->
                    entry?.let {
                        val requested = pinner.requestPin(it.component(context), null)
                        if (!requested) {
                            Toast.makeText(context, R.string.widget_pin_not_supported, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ),
        ).show()
    }
}
