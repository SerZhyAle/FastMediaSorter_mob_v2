package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.res.use
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import timber.log.Timber

/**
 * Canonical reusable labelled input row for settings surfaces.
 *
 * Layout: title + inline helper, then a Material outlined text field. Replaces the manual
 * `TextInputLayout` + standalone help `ImageButton` pairs (S0567).
 *
 * The row owns the help icon -> [TooltipDialog] wiring. Public XML attributes use the
 * `sir_` prefix (see `attrs.xml`).
 */
class SettingsInputRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val helpIcon: ImageButton
    private val inputLayout: TextInputLayout
    private val editText: TextInputEditText

    private var helpTitleText: CharSequence? = null
    private var helpMessageText: CharSequence? = null
    private var textChangedListener: ((CharSequence) -> Unit)? = null
    private var commitListener: ((CharSequence) -> Unit)? = null

    /**
     * Current input text.
     */
    var text: CharSequence
        get() = editText.text?.toString().orEmpty()
        set(value) {
            editText.setText(value)
        }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_settings_input_row, this, true)

        titleView = findViewById(R.id.sir_title)
        helpIcon = findViewById(R.id.sir_iconHelp)
        inputLayout = findViewById(R.id.sir_inputLayout)
        editText = findViewById(R.id.sir_input)

        bindHelpClick()
        bindTextChange()
        bindCommit()
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
     * Sets the input field hint.
     */
    fun setHint(hint: CharSequence?) {
        inputLayout.hint = hint
    }

    /**
     * Registers the listener invoked when the input text changes. Replaces any previous listener.
     */
    fun setOnTextChangedListener(listener: ((CharSequence) -> Unit)?) {
        textChangedListener = listener
    }

    /**
     * Registers the listener invoked when the user commits the input - on focus loss or an IME
     * done/next action. Use this for numeric fields that must validate/clamp the final value
     * rather than react to every keystroke. Replaces any previous listener.
     */
    fun setOnCommitListener(listener: ((CharSequence) -> Unit)?) {
        commitListener = listener
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
        editText.isEnabled = enabled
        alpha = if (enabled) 1f else 0.5f
    }

    private fun bindTextChange() {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                textChangedListener?.invoke(s ?: "")
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun bindCommit() {
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commitListener?.invoke(text)
        }
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                commitListener?.invoke(text)
            }
            false
        }
    }

    private fun bindHelpClick() {
        helpIcon.setOnClickListener {
            val title = helpTitleText
            val message = helpMessageText
            if (title.isNullOrEmpty() || message.isNullOrEmpty()) {
                Timber.w("SettingsInputRow: help requested without payload")
                return@setOnClickListener
            }
            TooltipDialog.show(context, title.toString(), message.toString())
        }
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.SettingsInputRow, defStyleAttr, 0).use { typedArray ->
            setTitle(typedArray.getText(R.styleable.SettingsInputRow_sir_title) ?: "")
            setHint(typedArray.getText(R.styleable.SettingsInputRow_sir_hint))
            val inputType = typedArray.getInt(R.styleable.SettingsInputRow_sir_inputType, InputType.TYPE_CLASS_TEXT)
            editText.inputType = inputType
            inputLayout.endIconMode = when (typedArray.getInt(R.styleable.SettingsInputRow_sir_endIconMode, 0)) {
                1 -> TextInputLayout.END_ICON_CLEAR_TEXT
                2 -> TextInputLayout.END_ICON_PASSWORD_TOGGLE
                else -> TextInputLayout.END_ICON_NONE
            }
            helpTitleText = typedArray.getText(R.styleable.SettingsInputRow_sir_helpTitle)
            helpMessageText = typedArray.getText(R.styleable.SettingsInputRow_sir_helpMessage)
            val showHelp = typedArray.getBoolean(R.styleable.SettingsInputRow_sir_showHelp, false)
            helpIcon.visibility = if (showHelp && hasHelpPayload()) View.VISIBLE else View.GONE
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
