package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.res.use
import androidx.core.view.updateLayoutParams
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import timber.log.Timber

/**
 * Canonical reusable dropdown row for settings and dialog surfaces.
 *
 * Layout: title + inline helper, then a Material outlined field (TextInputLayout + read-only
 * TextInputEditText). Replaces raw [android.widget.Spinner] usage (S0567, ADR-1).
 *
 * S1390: the row opens its own modal [ListPopupWindow] instead of relying on the
 * `ExposedDropdownMenu` style. That style delegates to `AutoCompleteTextView.showDropDown()`, whose
 * popup is deliberately non-focusable - so it never received D-pad keys and never appeared in the
 * accessibility or uiautomator window walk. A modal popup takes window focus, which fixes both.
 *
 * The row owns the help icon -> [TooltipDialog] wiring. Public XML attributes use the
 * `sdr_` prefix (see `attrs.xml`).
 */
class SettingsDropdownRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val textGroup: LinearLayout
    private val titleView: TextView
    private val subtitleView: TextView
    private val helpIcon: ImageButton
    private val inputLayout: TextInputLayout
    private val valueView: TextInputEditText

    private var helpTitleText: CharSequence? = null
    private var helpMessageText: CharSequence? = null
    private var itemSelectedListener: ((Int) -> Unit)? = null
    private var entries: List<CharSequence> = emptyList()
    private var selectedIndex: Int = -1
    private var optionsPopup: ListPopupWindow? = null

    // -1 (MATCH_PARENT) keeps the legacy fill behaviour; a positive value caps the field to a fixed width.
    private var fieldWidthPx: Int = LayoutParams.MATCH_PARENT

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_settings_dropdown_row, this, true)

        textGroup = findViewById(R.id.sdr_textGroup)
        titleView = findViewById(R.id.sdr_title)
        subtitleView = findViewById(R.id.sdr_subtitle)
        helpIcon = findViewById(R.id.sdr_iconHelp)
        inputLayout = findViewById(R.id.sdr_inputLayout)
        valueView = findViewById(R.id.sdr_value)

        bindHelpClick()
        bindItemSelection()
        applyAttributes(attrs, defStyleAttr)
        syncHelpVisibility()
    }

    /**
     * Sets the row title from a raw text value.
     */
    fun setTitle(text: CharSequence) {
        titleView.text = text
    }

    /**
     * Sets the row title from a string resource.
     */
    fun setTitle(@StringRes resId: Int) {
        setTitle(context.getText(resId))
    }

    /**
     * Sets the subtitle text. Empty/null hides the subtitle without breaking row layout.
     */
    fun setSubtitle(text: CharSequence?) {
        if (text.isNullOrEmpty()) {
            subtitleView.text = ""
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.text = text
            subtitleView.visibility = View.VISIBLE
        }
    }

    /**
     * Sets the subtitle from a string resource.
     */
    fun setSubtitle(@StringRes resId: Int) {
        setSubtitle(context.getText(resId))
    }

    /**
     * Sets the option list shown by the row and refreshes the displayed value.
     */
    fun setEntries(items: List<CharSequence>) {
        entries = items
        dismissOptions()
        if (selectedIndex in items.indices) {
            valueView.setText(items[selectedIndex])
        }
    }

    /**
     * Selects the entry at [index] without invoking the selection listener.
     */
    fun setSelection(index: Int) {
        selectedIndex = index
        if (index in entries.indices) {
            valueView.setText(entries[index])
        }
    }

    /**
     * Currently selected entry index, or -1 when nothing is selected.
     */
    fun getSelectedIndex(): Int = selectedIndex

    /**
     * Registers the listener invoked when a dropdown entry is chosen. Replaces any previous listener.
     */
    fun setOnItemSelectedListener(listener: ((Int) -> Unit)?) {
        itemSelectedListener = listener
    }

    /**
     * Stores the help payload and makes the help icon available.
     */
    fun setHelp(@StringRes titleRes: Int, @StringRes messageRes: Int) {
        helpTitleText = context.getText(titleRes)
        helpMessageText = context.getText(messageRes)
        setHelpVisible(true)
    }

    /**
     * Shows or hides the help icon without dropping the stored help payload.
     */
    fun setHelpVisible(visible: Boolean) {
        helpIcon.visibility = if (visible && hasHelpPayload()) View.VISIBLE else View.GONE
        helpIcon.contentDescription = helpTitleText?.toString().orEmpty()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        titleView.isEnabled = enabled
        subtitleView.isEnabled = enabled
        helpIcon.isEnabled = enabled
        inputLayout.isEnabled = enabled
        valueView.isEnabled = enabled
        alpha = if (enabled) 1f else 0.5f
        if (!enabled) dismissOptions()
    }

    override fun onDetachedFromWindow() {
        dismissOptions()
        super.onDetachedFromWindow()
    }

    private fun bindItemSelection() {
        // The field only displays the chosen value, so a D-pad focus must never raise the keyboard.
        valueView.showSoftInputOnFocus = false
        valueView.setOnClickListener { showOptions() }
        inputLayout.setEndIconOnClickListener { showOptions() }
        // The field is read-only, so its own key handling would swallow the confirm keys without acting
        // on them - D-pad and keyboard users need an explicit open (CLAUDE.md Rule 16).
        valueView.setOnKeyListener { _, keyCode, event ->
            if (!isConfirmKey(keyCode)) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP) showOptions()
            true
        }
    }

    private fun isConfirmKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    /**
     * Opens the option list as a modal [ListPopupWindow]. Modality is the whole point: a modal popup
     * owns window focus, so the list receives D-pad keys and is reported to accessibility and
     * uiautomator, neither of which was true of the former inline dropdown (S1390).
     */
    private fun showOptions() {
        if (!isEnabled || entries.isEmpty() || optionsPopup != null) return
        val popup = ListPopupWindow(context).apply {
            anchorView = inputLayout
            isModal = true
            width = if (inputLayout.width > 0) inputLayout.width else ListPopupWindow.WRAP_CONTENT
            height = ListPopupWindow.WRAP_CONTENT
            setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, entries))
            setOnItemClickListener { _, _, position, _ ->
                dismiss()
                applySelection(position)
            }
            setOnDismissListener { optionsPopup = null }
        }
        optionsPopup = popup
        popup.show()
        // Start the D-pad walk on the current value instead of the first row.
        if (selectedIndex in entries.indices) popup.listView?.setSelection(selectedIndex)
    }

    private fun dismissOptions() {
        optionsPopup?.dismiss()
        optionsPopup = null
    }

    private fun applySelection(position: Int) {
        setSelection(position)
        itemSelectedListener?.invoke(position)
    }

    private fun bindHelpClick() {
        helpIcon.setOnClickListener {
            val title = helpTitleText
            val message = helpMessageText
            if (title.isNullOrEmpty() || message.isNullOrEmpty()) {
                Timber.w("SettingsDropdownRow: help requested without payload")
                return@setOnClickListener
            }
            TooltipDialog.show(context, title.toString(), message.toString())
        }
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.SettingsDropdownRow, defStyleAttr, 0).use { typedArray ->
            setTitle(typedArray.getText(R.styleable.SettingsDropdownRow_sdr_title) ?: "")
            setSubtitle(typedArray.getText(R.styleable.SettingsDropdownRow_sdr_subtitle))
            helpTitleText = typedArray.getText(R.styleable.SettingsDropdownRow_sdr_helpTitle)
            helpMessageText = typedArray.getText(R.styleable.SettingsDropdownRow_sdr_helpMessage)
            val showHelp = typedArray.getBoolean(R.styleable.SettingsDropdownRow_sdr_showHelp, false)
            helpIcon.visibility = if (showHelp && hasHelpPayload()) View.VISIBLE else View.GONE
            val entriesRes = typedArray.getResourceId(R.styleable.SettingsDropdownRow_sdr_entries, 0)
            if (entriesRes != 0) {
                setEntries(resources.getTextArray(entriesRes).toList())
            }
            fieldWidthPx = typedArray.getDimensionPixelSize(
                R.styleable.SettingsDropdownRow_sdr_fieldWidth,
                LayoutParams.MATCH_PARENT,
            )
            val fieldMaxWidthPx = typedArray.getDimensionPixelSize(R.styleable.SettingsDropdownRow_sdr_fieldMaxWidth, 0)
            if (fieldMaxWidthPx > 0) inputLayout.maxWidth = fieldMaxWidthPx
            if (typedArray.getBoolean(R.styleable.SettingsDropdownRow_sdr_inline, false)) {
                applyInlineLayout()
            } else if (fieldWidthPx != LayoutParams.MATCH_PARENT) {
                inputLayout.updateLayoutParams<LayoutParams> { width = fieldWidthPx }
            }
        }
    }

    /**
     * Switches the row to a single inline line - label left of the field - for dense landscape
     * settings layouts (S0618). The default (portrait/stacked) path is left untouched.
     */
    private fun applyInlineLayout() {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        textGroup.updateLayoutParams<LayoutParams> {
            width = 0
            weight = 1f
            marginEnd = resources.getDimensionPixelSize(R.dimen.settings_help_icon_margin)
        }
        findViewById<View>(R.id.sdr_titleLineSpacer).visibility = View.GONE
        inputLayout.updateLayoutParams<LayoutParams> {
            // A fixed field width opts out of weight-based stretching for short-value selectors.
            if (fieldWidthPx != LayoutParams.MATCH_PARENT) {
                width = fieldWidthPx
                weight = 0f
            } else {
                width = 0
                weight = 1f
            }
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
    }

    private fun syncHelpVisibility() {
        if (!hasHelpPayload()) {
            helpIcon.visibility = View.GONE
        }
        helpIcon.contentDescription = helpTitleText?.toString().orEmpty()
    }

    private fun hasHelpPayload(): Boolean {
        return !helpTitleText.isNullOrEmpty() && !helpMessageText.isNullOrEmpty()
    }
}
