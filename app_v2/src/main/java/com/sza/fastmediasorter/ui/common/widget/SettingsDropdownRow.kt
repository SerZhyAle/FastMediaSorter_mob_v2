package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.res.use
import androidx.core.view.updateLayoutParams
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ViewSettingsDropdownRowBinding
import kotlin.math.ceil

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
 * The help-icon chrome is the shared [HelpRowDelegate]. Public XML attributes use the
 * `sdr_` prefix (see `attrs.xml`).
 */
class SettingsDropdownRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), LabelColumnRow, HelpableRow {

    private val binding = ViewSettingsDropdownRowBinding.inflate(LayoutInflater.from(context), this)

    private val textGroup: LinearLayout = binding.sdrTextGroup
    private val titleView: TextView = binding.sdrTitle
    private val subtitleView: TextView = binding.sdrSubtitle
    private val helpIcon: ImageButton = binding.sdrIconHelp
    private val help = HelpRowDelegate(helpIcon, "SettingsDropdownRow")
    private val inputLayout: TextInputLayout = binding.sdrInputLayout
    private val valueView: TextInputEditText = binding.sdrValue
    private val valueTextView: TextView = binding.sdrValueText
    private val valueTextIcon: ImageView = binding.sdrValueTextIcon

    /**
     * `true` when the optional help icon is visible.
     */
    override val isHelpVisible: Boolean
        get() = help.isHelpVisible

    private var itemSelectedListener: ((Int) -> Unit)? = null
    private var entries: List<CharSequence> = emptyList()
    private var selectedIndex: Int = -1
    private var optionsPopup: ListPopupWindow? = null

    // -1 (MATCH_PARENT) keeps the legacy fill behaviour; a positive value caps the field to a fixed width.
    private var fieldWidthPx: Int = LayoutParams.MATCH_PARENT
    private var valueAsText: Boolean = false
    private var labelColumnWidthPx: Int = 0

    // null until value text mode picks a form; then true = hugging the content, false = weighted group.
    private var valueAsTextHugged: Boolean? = null

    init {
        orientation = VERTICAL

        bindItemSelection()
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
        // A subtitle may arrive long after inflation - the power saving row names its active cause from
        // a battery broadcast - and it decides which value text form the row takes (S2780).
        if (valueAsText) syncValueAsTextTextGroup()
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
            showValue(items[selectedIndex])
        }
    }

    /**
     * Selects the entry at [index] without invoking the selection listener.
     */
    fun setSelection(index: Int) {
        selectedIndex = index
        if (index in entries.indices) {
            showValue(entries[index])
        }
    }

    private fun showValue(text: CharSequence) {
        valueView.setText(text)
        valueTextView.text = text
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
        subtitleView.isEnabled = enabled
        help.setEnabled(enabled)
        inputLayout.isEnabled = enabled
        valueView.isEnabled = enabled
        valueTextView.isEnabled = enabled
        valueTextIcon.isEnabled = enabled
        alpha = if (enabled) 1f else 0.5f
        if (!enabled) dismissOptions()
    }

    override fun onDetachedFromWindow() {
        dismissOptions()
        super.onDetachedFromWindow()
    }

    /**
     * Caps the value text so the trailing chevron always keeps its slot.
     *
     * The row hugs its content, because the glyph belongs beside the value and not at the row's far
     * edge (S0644). Hugging means the value is measured before the glyph and may take the whole row,
     * which is what left the one Streams entry long enough to wrap without a chevron (S2783).
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (valueAsText && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val gap = resources.getDimensionPixelSize(R.dimen.settings_help_icon_margin)
            val label = if (labelColumnWidthPx > 0) {
                labelColumnWidthPx + resources.getDimensionPixelSize(R.dimen.margin_medium)
            } else {
                measureLabelNaturalWidth()
            }
            val glyph = resources.getDimensionPixelSize(R.dimen.settings_help_icon_size) + gap
            val room = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd - label - glyph - gap
            val cap = room.coerceAtLeast(0)
            if (valueTextView.maxWidth != cap) {
                valueTextView.maxWidth = cap
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
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
        // S2185: never narrower than the field it is anchored to, but expands past a compact
        // field (e.g. sdr_fieldMaxWidth) to fit the widest entry - see measurePopupContentWidth.
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, entries)
        val popup = ListPopupWindow(context).apply {
            anchorView = if (valueAsText) this@SettingsDropdownRow else inputLayout
            isModal = true
            width = measurePopupContentWidth(context, adapter, minWidthPx = if (valueAsText) 0 else inputLayout.width)
            height = ListPopupWindow.WRAP_CONTENT
            setAdapter(adapter)
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

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.SettingsDropdownRow, defStyleAttr, 0).use { typedArray ->
            setTitle(typedArray.getText(R.styleable.SettingsDropdownRow_sdr_title) ?: "")
            setSubtitle(typedArray.getText(R.styleable.SettingsDropdownRow_sdr_subtitle))
            help.setPayload(
                typedArray.getText(R.styleable.SettingsDropdownRow_sdr_helpTitle),
                typedArray.getText(R.styleable.SettingsDropdownRow_sdr_helpMessage),
            )
            val showHelp = typedArray.getBoolean(R.styleable.SettingsDropdownRow_sdr_showHelp, false)
            help.applyInitialVisibility(showHelp)
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
            valueAsText = typedArray.getBoolean(R.styleable.SettingsDropdownRow_sdr_valueAsText, false)
            if (valueAsText) {
                applyValueAsTextLayout()
            } else if (typedArray.getBoolean(R.styleable.SettingsDropdownRow_sdr_inline, false)) {
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
        binding.sdrTitleLineSpacer.visibility = View.GONE
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

    /**
     * Value text mode: the outlined field is dropped and the chosen entry is drawn on the title line,
     * so this row reads identically to the SettingsSelectionRow rows it sits beside - same value style,
     * same trailing chevron. The whole row becomes the trigger, since there is no field left to click.
     */
    private fun applyValueAsTextLayout() {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        syncValueAsTextTextGroup()
        inputLayout.visibility = View.GONE
        valueTextView.visibility = View.VISIBLE
        valueTextIcon.visibility = View.VISIBLE
        isClickable = true
        isFocusable = true
        setOnClickListener { showOptions() }
        setOnKeyListener { _, keyCode, event ->
            if (!isConfirmKey(keyCode)) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP) showOptions()
            true
        }
        val background = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true)
        if (background.resourceId != 0) setBackgroundResource(background.resourceId)
    }

    /**
     * Picks the text group's form for value text mode from whether the row shows a subtitle.
     *
     * Without one the group hugs its content, so the chevron sits right after the value. With one the
     * group keeps its weight: the subtitle spans the full row width, and a hugged group would squeeze
     * it into a column as narrow as the title line above it (S2780). Either way the value stays on the
     * title line immediately after the caption, which is the point of the mode. Same split as
     * [SettingsSelectionRow], which reached it first (S0644).
     */
    private fun syncValueAsTextTextGroup() {
        val hug = subtitleView.visibility == View.GONE
        // The subtitle is rewritten on every settings emission, and updateLayoutParams always requests
        // a layout pass - so re-apply only when the form actually changes.
        if (hug == valueAsTextHugged) return
        valueAsTextHugged = hug
        textGroup.updateLayoutParams<LayoutParams> {
            width = if (hug) LayoutParams.WRAP_CONTENT else 0
            weight = if (hug) 0f else 1f
        }
        binding.sdrTitleLine.updateLayoutParams<ViewGroup.LayoutParams> {
            width = if (hug) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.sdrTitleLineSpacer.visibility = if (hug) View.GONE else View.VISIBLE
    }

    /**
     * Natural width of the title plus its help icon, measured unconstrained so an already applied
     * column width is never fed back to [SettingsValueRowGroup].
     */
    override fun measureLabelNaturalWidth(): Int {
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
     * Width of the value plus the trailing chevron and their gaps - what the row needs to the right
     * of its label column.
     */
    override fun measureTrailingNaturalWidth(): Int {
        var width = resources.getDimensionPixelSize(R.dimen.margin_medium)
        if (valueTextView.visibility != View.GONE) {
            // Read off the paint rather than by measuring the view, which already carries the cap
            // onMeasure put on it - feeding that cap back would let the group size a column that only
            // fits because the value was truncated to make it fit.
            val text = valueTextView.text ?: ""
            width += ceil(valueTextView.paint.measureText(text, 0, text.length)).toInt() +
                valueTextView.paddingStart + valueTextView.paddingEnd
        }
        if (valueTextIcon.visibility != View.GONE) {
            width += resources.getDimensionPixelSize(R.dimen.settings_help_icon_size) +
                resources.getDimensionPixelSize(R.dimen.settings_help_icon_margin)
        }
        return width
    }

    /**
     * Pins the label column so the value starts where its siblings' values start. The value still
     * follows the label immediately - the column is sized by the longest caption, not by the row -
     * and carries a readable gap, because on the longest row the column ends exactly where the
     * caption does and the two texts would otherwise touch.
     * Zero restores the row's own hug layout, for when the group decided a column does not fit.
     */
    override fun applyLabelColumnWidth(widthPx: Int) {
        val column = widthPx > 0
        labelColumnWidthPx = widthPx
        titleView.maxLines = if (column) 1 else Int.MAX_VALUE
        titleView.ellipsize = if (column) TextUtils.TruncateAt.END else null
        binding.sdrTitleCluster.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (column) widthPx else LayoutParams.WRAP_CONTENT
            weight = 0f
            marginEnd = if (column) resources.getDimensionPixelSize(R.dimen.margin_medium) else 0
        }
    }
}
