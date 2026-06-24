package com.sza.fastmediasorter.ui.dialog

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R

/**
 * Thin reusable wrapper over [ListSelectionDialog] for the common "pick one string-keyed option
 * from a short label list" case in settings (S0646). Each option carries a stable [Option.key]
 * (the persisted value) and a localized [Option.label]. The result callback returns the chosen
 * key, or `null` when the optional Clear button is used.
 *
 * For domain-typed selectors (enums, `MediaResource`, widget entries) callers configure
 * [ListSelectionDialog] directly with a per-type formatter instead.
 */
class SimpleValueChoiceDialog(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    title: CharSequence,
    options: List<Option>,
    currentKey: String?,
    allowClear: Boolean = false,
    onSelected: (String?) -> Unit,
) : ListSelectionDialog<SimpleValueChoiceDialog.Option>(
    context,
    ListSelectionConfig(
        title = title,
        lifecycleOwner = lifecycleOwner,
        loader = { options },
        formatter = object : ListSelectionAdapter.ItemFormatter<Option> {
            override fun getDisplayName(item: Option): String = item.label
        },
        hasSelection = currentKey != null,
        isSelected = { it.key == currentKey },
        allowClear = allowClear,
        // The option list is built in code and never empty; these are defensive only.
        emptyMessageRes = R.string.error_unknown,
        errorMessageRes = R.string.error_unknown,
        onSelected = { onSelected(it?.key) },
    ),
) {

    /**
     * One selectable entry: [key] is the persisted value, [label] the localized display text.
     */
    data class Option(val key: String, val label: String)
}
