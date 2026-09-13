package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
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
import com.sza.fastmediasorter.databinding.ViewSettingsInputRowBinding
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
 *
 * S2786: two opt-in extras. `sir_inline` puts the title and the field on one line, and `sir_entries`
 * offers presets beside a field that stays freely editable. Both default to off, so the row's other
 * call sites keep the stacked, picker-less form.
 */
class SettingsInputRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewSettingsInputRowBinding.inflate(LayoutInflater.from(context), this)

    private val titleView: TextView = binding.sirTitle
    private val helpIcon: ImageButton = binding.sirIconHelp
    private val inputLayout: TextInputLayout = binding.sirInputLayout
    private val editText: TextInputEditText = binding.sirInput
    private val titleLine: LinearLayout = binding.sirTitleLine
    private val titleLineSpacer: View = binding.sirTitleLineSpacer
    private val inlineTailSpacer: View = binding.sirInlineTailSpacer

    private var helpTitleText: CharSequence? = null
    private var helpMessageText: CharSequence? = null
    private var textChangedListener: ((CharSequence) -> Unit)? = null
    private var commitListener: ((CharSequence) -> Unit)? = null
    private var entries: List<CharSequence> = emptyList()
    private var optionsPopup: ListPopupWindow? = null

    // MATCH_PARENT keeps the stacked default; sir_fieldMaxWidth turns the inline field into a fixed column.
    private var fieldWidthPx: Int = LayoutParams.MATCH_PARENT

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
     * Sets the presets offered beside the field. An empty list drops the picker and leaves a plain
     * input row; the field stays editable either way.
     */
    fun setEntries(items: List<CharSequence>) {
        entries = items
        dismissOptions()
        syncPickerAffordance()
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
        if (!enabled) dismissOptions()
    }

    override fun onDetachedFromWindow() {
        dismissOptions()
        super.onDetachedFromWindow()
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

    /**
     * Shows the picker affordance only while there is something to pick. The end icon is a focusable
     * button, so it is also how a D-pad reaches the list; the field itself answers only the D-pad
     * confirm key, never ENTER, which stays the keyboard user's commit.
     */
    private fun syncPickerAffordance() {
        if (entries.isEmpty()) {
            inputLayout.setEndIconOnClickListener(null)
            editText.setOnKeyListener(null)
            return
        }
        inputLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        inputLayout.setEndIconDrawable(R.drawable.ic_arrow_drop_down)
        inputLayout.endIconContentDescription = context.getText(R.string.select)
        inputLayout.setEndIconOnClickListener { showOptions() }
        editText.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP) showOptions()
            true
        }
    }

    /**
     * Opens the presets as a modal [ListPopupWindow]. Modality is the point: a modal popup owns window
     * focus, so the list receives D-pad keys and is reported to accessibility and uiautomator, neither
     * of which was true of the inline `ExposedDropdownMenu` this project already removed once (S1390).
     */
    private fun showOptions() {
        if (!isEnabled || entries.isEmpty() || optionsPopup != null) return
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, entries)
        val popup = ListPopupWindow(context).apply {
            anchorView = inputLayout
            isModal = true
            width = measurePopupContentWidth(context, adapter, minWidthPx = inputLayout.width)
            height = ListPopupWindow.WRAP_CONTENT
            setAdapter(adapter)
            setOnItemClickListener { _, _, position, _ ->
                dismiss()
                applyEntry(position)
            }
            setOnDismissListener { optionsPopup = null }
        }
        optionsPopup = popup
        popup.show()
    }

    private fun dismissOptions() {
        optionsPopup?.dismiss()
        optionsPopup = null
    }

    /**
     * A picked preset goes through the same commit path as a typed value, so the screen keeps exactly
     * one place where the value is validated and stored.
     */
    private fun applyEntry(position: Int) {
        val picked = entries.getOrNull(position) ?: return
        editText.setText(picked)
        editText.setSelection(editText.text?.length ?: 0)
        commitListener?.invoke(picked)
    }

    /**
     * Puts the title and the field on one line. The title hugs its text and the field follows it
     * immediately; the leftover width goes to a spacer at the END of the row, never between the two -
     * on a landscape settings card the value would otherwise sit a screen-width away from its caption
     * (owner ruling 2026-09-01).
     */
    private fun applyInlineLayout() {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        titleLineSpacer.visibility = View.GONE
        titleLine.updateLayoutParams<LayoutParams> {
            width = LayoutParams.WRAP_CONTENT
            weight = 0f
            marginEnd = resources.getDimensionPixelSize(R.dimen.margin_medium)
        }
        inputLayout.updateLayoutParams<LayoutParams> {
            width = if (fieldWidthPx > 0) fieldWidthPx else LayoutParams.WRAP_CONTENT
            weight = 0f
        }
        inlineTailSpacer.visibility = View.VISIBLE
        inlineTailSpacer.updateLayoutParams<LayoutParams> {
            width = 0
            weight = 1f
        }
    }

    /**
     * Sets the maximum character length for the input field.
     */
    fun setMaxLength(maxLength: Int) {
        if (maxLength > 0) {
            editText.filters = arrayOf(InputFilter.LengthFilter(maxLength))
        } else {
            editText.filters = emptyArray()
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
            val fieldMaxWidthPx = typedArray.getDimensionPixelSize(R.styleable.SettingsInputRow_sir_fieldMaxWidth, 0)
            if (fieldMaxWidthPx > 0) {
                inputLayout.maxWidth = fieldMaxWidthPx
                fieldWidthPx = fieldMaxWidthPx
            }
            val entriesRes = typedArray.getResourceId(R.styleable.SettingsInputRow_sir_entries, 0)
            if (entriesRes != 0) setEntries(resources.getTextArray(entriesRes).toList())
            val maxLength = typedArray.getInt(R.styleable.SettingsInputRow_sir_maxLength, 0)
            if (maxLength > 0) setMaxLength(maxLength)
            if (typedArray.getBoolean(R.styleable.SettingsInputRow_sir_inline, false)) applyInlineLayout()
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
