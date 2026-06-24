package com.sza.fastmediasorter.ui.settings.fragments

import android.text.Editable
import android.text.TextWatcher
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.ui.common.widget.SettingsDropdownRow
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow

abstract class BaseSettingsFragment : Fragment() {

    protected var isUpdatingFromSettings: Boolean = false

    protected inline fun withSettingsUpdate(block: () -> Unit) {
        isUpdatingFromSettings = true
        try {
            block()
        } finally {
            isUpdatingFromSettings = false
        }
    }

    protected fun bindSwitch(
        switch: CompoundButton,
        onUserChanged: (Boolean) -> Unit
    ) {
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromSettings) {
                onUserChanged(isChecked)
            }
        }
    }

    protected fun setSwitchChecked(
        switch: CompoundButton,
        checked: Boolean
    ) {
        if (switch.isChecked != checked) {
            switch.isChecked = checked
        }
    }

    /**
     * Row-level overload for [SettingsToggleRow]. Notifies [onUserChanged] only for
     * user-initiated changes (suppressed while [withSettingsUpdate] is active).
     */
    protected fun bindSwitch(row: SettingsToggleRow, onUserChanged: (Boolean) -> Unit) {
        row.setOnCheckedChangeListener { isChecked ->
            if (!isUpdatingFromSettings) {
                onUserChanged(isChecked)
            }
        }
    }

    /**
     * Row-level overload for [SettingsToggleRow]. Avoids a redundant assignment and
     * does not invoke the change listener.
     */
    protected fun setSwitchChecked(row: SettingsToggleRow, checked: Boolean) {
        if (row.isChecked != checked) {
            row.setCheckedSilently(checked)
        }
    }

    protected fun bindSpinner(
        spinner: AdapterView<*>,
        onUserSelected: (position: Int) -> Unit
    ) {
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                if (!isUpdatingFromSettings) {
                    onUserSelected(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    protected fun setSpinnerSelection(
        spinner: AdapterView<*>,
        position: Int
    ) {
        if (spinner.selectedItemPosition != position) {
            if (spinner is android.widget.Spinner) {
                spinner.setSelection(position, false)
            } else {
                spinner.setSelection(position)
            }
        }
    }

    /**
     * [SettingsDropdownRow] analogue of [bindSpinner]. Forwards only user-initiated selections
     * (suppressed while [withSettingsUpdate] is active) so reflecting the current settings value back
     * into the row never loops back as a fake user change.
     */
    protected fun bindDropdown(row: SettingsDropdownRow, onUserSelected: (position: Int) -> Unit) {
        row.setOnItemSelectedListener { position ->
            if (!isUpdatingFromSettings) {
                onUserSelected(position)
            }
        }
    }

    /**
     * [SettingsDropdownRow] analogue of [setSpinnerSelection]. Re-selects only on an actual change;
     * [SettingsDropdownRow.setSelection] already suppresses the listener, so no extra guard is needed.
     */
    protected fun setDropdownSelection(row: SettingsDropdownRow, index: Int) {
        if (row.getSelectedIndex() != index) {
            row.setSelection(index)
        }
    }

    protected fun bindInputField(
        input: EditText,
        onUserChanged: (String) -> Unit
    ) {
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isUpdatingFromSettings) {
                    onUserChanged(s?.toString().orEmpty())
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    protected fun setInputText(
        input: EditText,
        value: String
    ) {
        if (input.text?.toString() != value) {
            input.setText(value)
        }
    }
}
