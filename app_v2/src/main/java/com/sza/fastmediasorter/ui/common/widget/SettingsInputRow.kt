package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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

/**
 * Canonical reusable labelled input row for settings surfaces.
 *
 * Layout: title + inline helper, then a Material outlined text field. Replaces the manual
 * `TextInputLayout` + standalone help `ImageButton` pairs (S0567).
 *
 * The help-icon chrome is the shared [HelpRowDelegate]. Public XML attributes use the
 * `sir_` prefix (see `attrs.xml`).
 *
 * S2786: two opt-in extras. `sir_inline` puts the title and the field on one line, and `sir_entries`
 * offers presets beside a field that stays freely editable. Both default to off, so the row's other
 * call sites keep the stacked, picker-less form.
 *
 * S3235: an inline row is also a [LabelColumnRow], so it shares the label column of the
 * [SettingsValueRowGroup] it sits in instead of starting its caption on an offset of its own.
 */
class SettingsInputRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), LabelColumnRow, HelpableRow {

    private val binding = ViewSettingsInputRowBinding.inflate(LayoutInflater.from(context), this)

    private val titleView: TextView = binding.sirTitle
    private val helpIcon: ImageButton = binding.sirIconHelp
    private val help = HelpRowDelegate(helpIcon, "SettingsInputRow")
    private val inputLayout: TextInputLayout = binding.sirInputLayout
    private val editText: TextInputEditText = binding.sirInput
    private val titleLine: LinearLayout = binding.sirTitleLine
    private val titleLineSpacer: View = binding.sirTitleLineSpacer
    private val inlineTailSpacer: View = binding.sirInlineTailSpacer

    /**
     * `true` when the optional help icon is visible.
     */
    override val isHelpVisible: Boolean
        get() = help.isHelpVisible

    private var textChangedListener: ((CharSequence) -> Unit)? = null
    private var commitListener: ((CharSequence) -> Unit)? = null
    private var entries: List<CharSequence> = emptyList()
    private var optionsPopup: ListPopupWindow? = null

    // The value the commit listener last saw, so a second commit path for the same text is a no-op
    // instead of another identical DataStore write (S3234).
    private var lastCommittedText: String? = null

    // MATCH_PARENT keeps the stacked default; sir_fieldMaxWidth turns the inline field into a fixed column.
    private var fieldWidthPx: Int = LayoutParams.MATCH_PARENT

    // Only an inline row has a label column to share - a stacked one draws its caption on its own line.
    private var inlineLayout: Boolean = false

    /**
     * Current input text.
     */
    var text: CharSequence
        get() = editText.text?.toString().orEmpty()
        set(value) {
            // Text pushed in by a host comes from storage, so it counts as already committed.
            lastCommittedText = value.toString()
            editText.setText(value)
        }

    init {
        orientation = VERTICAL

        bindTextChange()
        bindCommit()
        applyAttributes(attrs, defStyleAttr)
        help.syncVisibility()
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
     * Registers the listener invoked when the user commits the input - on focus loss, on an IME
     * done/next/go action or ENTER, and on [commitPending]. Use this for numeric fields that must
     * validate/clamp the final value rather than react to every keystroke. Replaces any previous
     * listener.
     */
    fun setOnCommitListener(listener: ((CharSequence) -> Unit)?) {
        commitListener = listener
    }

    /**
     * Commits the current text without waiting for focus to leave the field. Tapping a button on the
     * host screen does not move focus out of an [android.widget.EditText], so a screen that is about to
     * read the stored value calls this first; otherwise the action runs on the previous value while the
     * field shows the new one (S3234). A text equal to the last committed one commits nothing.
     */
    fun commitPending() {
        val current = text.toString()
        if (current == lastCommittedText) return
        lastCommittedText = current
        commitListener?.invoke(current)
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
    override fun setHelp(@StringRes titleRes: Int, @StringRes messageRes: Int) {
        help.setHelp(titleRes, messageRes)
    }

    /**
     * Shows or hides the help icon without dropping the stored help payload.
     */
    override fun setHelpVisible(visible: Boolean) {
        help.setHelpVisible(visible)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        titleView.isEnabled = enabled
        help.setEnabled(enabled)
        inputLayout.isEnabled = enabled
        editText.isEnabled = enabled
        alpha = if (enabled) 1f else 0.5f
        if (!enabled) dismissOptions()
    }

    override fun onDetachedFromWindow() {
        commitPending()
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
            if (!hasFocus) commitPending()
        }
        editText.setOnEditorActionListener { _, actionId, event ->
            if (!isCommitAction(actionId, event)) return@setOnEditorActionListener false
            commitPending()
            hideKeyboard()
            editText.clearFocus()
            // Consumed on purpose: an unhandled ENTER falls through to the window and the hosting
            // Activity closes instead of the value being committed (S3234).
            true
        }
    }

    /**
     * An ENTER from a hardware keyboard arrives as [EditorInfo.IME_NULL] carrying the key event, while
     * the on-screen keyboard sends its action id; both mean "the value is final".
     */
    private fun isCommitAction(actionId: Int, event: KeyEvent?): Boolean {
        if (actionId in COMMIT_ACTION_IDS) return true
        return actionId == EditorInfo.IME_NULL && event?.keyCode == KeyEvent.KEYCODE_ENTER
    }

    private fun hideKeyboard() {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(editText.windowToken, 0)
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
        inputLayout.endIconContentDescription = context.getText(R.string.cd_choose_from_list)
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
        commitPending()
    }

    /**
     * Puts the title and the field on one line. The title hugs its text and the field follows it
     * immediately; the leftover width goes to a spacer at the END of the row, never between the two -
     * on a landscape settings card the value would otherwise sit a screen-width away from its caption
     * (owner ruling 2026-09-01).
     */
    private fun applyInlineLayout() {
        inlineLayout = true
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
            // S3229: a bare View reports the whole AT_MOST spec back, so an unbounded height here grew
            // the row to the full height of the card it sits in and pushed the screen's actions out.
            height = 0
        }
    }

    /**
     * Natural width of the title plus its help icon, measured unconstrained so a column width the
     * group already applied is never fed back to it. A stacked row reports zero: it has no label
     * column, and a caption on its own line must not widen the one its inline siblings share.
     */
    override fun measureLabelNaturalWidth(): Int {
        if (!inlineLayout) return 0
        val unbounded = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        titleView.measure(unbounded, unbounded)
        var width = titleView.measuredWidth
        if (helpIcon.visibility == View.VISIBLE) {
            helpIcon.measure(unbounded, unbounded)
            width += helpIcon.measuredWidth + resources.getDimensionPixelSize(R.dimen.settings_help_icon_margin)
        }
        return width
    }

    /**
     * Width the field needs to the right of the label column, gap included. A field pinned by
     * `sir_fieldMaxWidth` reports that width directly - measuring it unconstrained would return the
     * text's own appetite rather than the column it was given.
     */
    override fun measureTrailingNaturalWidth(): Int {
        if (!inlineLayout) return 0
        val gap = resources.getDimensionPixelSize(R.dimen.margin_medium)
        val field = if (fieldWidthPx > 0) {
            fieldWidthPx
        } else {
            val unbounded = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            inputLayout.measure(unbounded, unbounded)
            inputLayout.measuredWidth
        }
        return gap + field
    }

    /**
     * Pins the title line to [widthPx] so the field starts where the siblings' values start. Zero
     * restores the hug-the-content form, which is what the group asks for when no column fits.
     */
    override fun applyLabelColumnWidth(widthPx: Int) {
        if (!inlineLayout) return
        val column = widthPx > 0
        titleView.maxLines = if (column) 1 else Int.MAX_VALUE
        titleView.ellipsize = if (column) TextUtils.TruncateAt.END else null
        titleLine.updateLayoutParams<LayoutParams> {
            width = if (column) widthPx else LayoutParams.WRAP_CONTENT
            weight = 0f
            marginEnd = resources.getDimensionPixelSize(R.dimen.margin_medium)
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
            help.setPayload(
                typedArray.getText(R.styleable.SettingsInputRow_sir_helpTitle),
                typedArray.getText(R.styleable.SettingsInputRow_sir_helpMessage),
            )
            val showHelp = typedArray.getBoolean(R.styleable.SettingsInputRow_sir_showHelp, false)
            help.applyInitialVisibility(showHelp)
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

    private companion object {
        val COMMIT_ACTION_IDS = setOf(
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_GO,
        )
    }
}
