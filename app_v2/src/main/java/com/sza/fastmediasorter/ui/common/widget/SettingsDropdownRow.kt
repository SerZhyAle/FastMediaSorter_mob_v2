package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.res.use
import androidx.core.view.updateLayoutParams
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import timber.log.Timber

/**
 * Canonical reusable dropdown row for settings and dialog surfaces.
 *
 * Layout: title + inline helper, then a Material exposed dropdown (TextInputLayout +
 * AutoCompleteTextView). Replaces raw [android.widget.Spinner] usage (S0567, ADR-1).
 *
 * The row owns the help icon -> [TooltipDialog] wiring. Public XML attributes use the
 * `sdr_` prefix (see `attrs.xml`).
 */
class SettingsDropdownRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val helpIcon: ImageButton
    private val inputLayout: TextInputLayout
    private val autoComplete: AutoCompleteTextView

    private var helpTitleText: CharSequence? = null
    private var helpMessageText: CharSequence? = null
    private var itemSelectedListener: ((Int) -> Unit)? = null
    private var entries: List<CharSequence> = emptyList()
    private var selectedIndex: Int = -1

    // -1 (MATCH_PARENT) keeps the legacy fill behaviour; a positive value caps the field to a fixed width.
    private var fieldWidthPx: Int = LayoutParams.MATCH_PARENT

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_settings_dropdown_row, this, true)

        titleView = findViewById(R.id.sdr_title)
        helpIcon = findViewById(R.id.sdr_iconHelp)
        inputLayout = findViewById(R.id.sdr_inputLayout)
        autoComplete = findViewById(R.id.sdr_autocomplete)

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
     * Sets the dropdown entries and rebuilds the backing adapter.
     */
    fun setEntries(items: List<CharSequence>) {
        entries = items
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, items)
        autoComplete.setAdapter(adapter)
        if (selectedIndex in items.indices) {
            autoComplete.setText(items[selectedIndex], false)
        }
    }

    /**
     * Selects the entry at [index] without invoking the selection listener.
     */
    fun setSelection(index: Int) {
        selectedIndex = index
        if (index in entries.indices) {
            autoComplete.setText(entries[index], false)
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
        helpIcon.isEnabled = enabled
        inputLayout.isEnabled = enabled
        autoComplete.isEnabled = enabled
        alpha = if (enabled) 1f else 0.5f
    }

    private fun bindItemSelection() {
        autoComplete.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
            itemSelectedListener?.invoke(position)
        }
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
        (titleView.parent as View).updateLayoutParams<LayoutParams> {
            width = LayoutParams.WRAP_CONTENT
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
