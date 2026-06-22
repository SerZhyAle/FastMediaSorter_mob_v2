package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.res.use
import com.google.android.material.checkbox.MaterialCheckBox
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import timber.log.Timber

/**
 * Canonical reusable checkbox row for resource-entry forms.
 *
 * Layout: checkbox + title + inline helper on one line, optional subtitle indented below.
 * Replaces the repeated `checkbox + subtitle + optional help` structures duplicated across
 * Add Resource and Resource Editor scanning sections (S0567 survey item 5).
 *
 * Modelled on [SettingsToggleRow] (Pattern B): the whole row is a single focus stop, tapping
 * anywhere toggles the checkbox, and the row owns the help icon -> [TooltipDialog] wiring.
 * Public XML attributes use the `fcr_` prefix (see `attrs.xml`).
 */
class FormCheckboxRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val checkBox: MaterialCheckBox
    private val titleView: TextView
    private val subtitleView: TextView
    private val helpIcon: ImageButton

    private var helpTitleText: CharSequence? = null
    private var helpMessageText: CharSequence? = null
    private var checkedChangeListener: ((Boolean) -> Unit)? = null

    /**
     * Current checked state of the embedded checkbox.
     */
    var isChecked: Boolean
        get() = checkBox.isChecked
        set(value) {
            checkBox.isChecked = value
        }

    init {
        orientation = VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.button_height)
        isClickable = true
        isFocusable = true
        if (background == null) {
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }
        LayoutInflater.from(context).inflate(R.layout.view_form_checkbox_row, this, true)

        checkBox = findViewById(R.id.fcr_checkbox)
        titleView = findViewById(R.id.fcr_title)
        subtitleView = findViewById(R.id.fcr_subtitle)
        helpIcon = findViewById(R.id.fcr_iconHelp)

        bindRowClicks()
        bindHelpClick()
        applyAttributes(attrs, defStyleAttr)
        syncHelpVisibility()
    }

    /**
     * Sets the row title from a raw text value.
     */
    fun setTitle(text: CharSequence) {
        titleView.text = text
        contentDescription = text
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

    /**
     * Registers a listener invoked when the embedded checkbox changes state.
     * Replaces any previously registered listener.
     */
    fun setOnCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        checkedChangeListener = listener
    }

    /**
     * Sets the checked state without invoking the change listener.
     */
    fun setCheckedSilently(value: Boolean) {
        val listener = checkedChangeListener
        checkedChangeListener = null
        checkBox.isChecked = value
        checkedChangeListener = listener
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        checkBox.isEnabled = enabled
        helpIcon.isEnabled = enabled
        titleView.isEnabled = enabled
        subtitleView.isEnabled = enabled
        alpha = if (enabled) 1f else 0.5f
    }

    private fun bindRowClicks() {
        setOnClickListener {
            if (!isEnabled) return@setOnClickListener
            checkBox.isChecked = !checkBox.isChecked
        }
        // The native CheckedChangeListener fires for both programmatic and user-driven changes;
        // setCheckedSilently temporarily detaches it to suppress programmatic notifications.
        checkBox.setOnCheckedChangeListener { _, value ->
            checkedChangeListener?.invoke(value)
        }
    }

    private fun bindHelpClick() {
        helpIcon.setOnClickListener {
            val title = helpTitleText
            val message = helpMessageText
            if (title.isNullOrEmpty() || message.isNullOrEmpty()) {
                Timber.w("FormCheckboxRow: help requested without payload")
                return@setOnClickListener
            }
            TooltipDialog.show(context, title.toString(), message.toString())
        }
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.FormCheckboxRow, defStyleAttr, 0).use { typedArray ->
            setTitle(typedArray.getText(R.styleable.FormCheckboxRow_fcr_title) ?: "")
            setSubtitle(typedArray.getText(R.styleable.FormCheckboxRow_fcr_subtitle))
            helpTitleText = typedArray.getText(R.styleable.FormCheckboxRow_fcr_helpTitle)
            helpMessageText = typedArray.getText(R.styleable.FormCheckboxRow_fcr_helpMessage)
            val showHelp = typedArray.getBoolean(R.styleable.FormCheckboxRow_fcr_showHelp, false)
            helpIcon.visibility = if (showHelp && hasHelpPayload()) View.VISIBLE else View.GONE
            checkBox.isChecked = typedArray.getBoolean(R.styleable.FormCheckboxRow_fcr_checked, false)
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
