package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.res.use
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.checkbox.MaterialCheckBox
import com.sza.fastmediasorter.R

/**
 * Canonical reusable boolean option row for settings fragments and forms (contract BOOLEAN-OPTION 2.1).
 *
 * Layout: square checkbox at reading start, text group (title + helper inline, subtitle below),
 * optional trailing action slot for the rare row that needs a secondary action.
 *
 * The view owns visual state only - checked persistence stays outside the component.
 * A tap or click on the checkbox, the title or the leading glyph toggles the value once; the
 * subtitle (explanation), gaps, background and help never toggle. The row itself stays the single
 * focus stop: Space, Enter and D-pad centre toggle it, and accessibility services see it as one
 * named checkbox with the subtitle as its hint. The helper icon chrome is the shared
 * [HelpRowDelegate]; it is hidden without a payload, without breaking the layout.
 *
 * Public XML attributes use the `str_` prefix (see `attrs.xml`).
 *
 * @see docs/ARCHITECTURE.md § "UI Patterns - Trigger Row" (Pattern A).
 */
class SettingsToggleRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), HelpableRow {

    private val checkBox: MaterialCheckBox
    private val iconView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val help: HelpRowDelegate
    private val trailingSlot: FrameLayout
    private val textGroup: LinearLayout
    private val titleLineSpacer: View

    private var checkedChangeListener: ((Boolean) -> Unit)? = null

    /**
     * Current checked state of the embedded checkbox.
     */
    var isChecked: Boolean
        get() = checkBox.isChecked
        set(value) {
            checkBox.isChecked = value
        }

    /**
     * `true` when the optional help icon is visible.
     */
    override val isHelpVisible: Boolean
        get() = help.isHelpVisible

    init {
        orientation = HORIZONTAL
        // Top, not centre: the checkbox aligns with the first heading line of a long explanation
        // (BOOLEAN-OPTION 2.1 section 2). Content height - the checkbox touch target is the floor.
        gravity = android.view.Gravity.TOP
        isClickable = true
        isFocusable = true
        if (background == null) {
            // Standard focus feedback so the focused row reads as one control.
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }
        LayoutInflater.from(context).inflate(R.layout.view_settings_toggle_row, this, true)

        checkBox = findViewById(R.id.str_checkbox)
        iconView = findViewById(R.id.str_icon)
        titleView = findViewById(R.id.str_title)
        subtitleView = findViewById(R.id.str_subtitle)
        help = HelpRowDelegate(findViewById(R.id.str_iconHelp), "SettingsToggleRow")
        trailingSlot = findViewById(R.id.str_trailingSlot)
        textGroup = findViewById(R.id.str_textGroup)
        titleLineSpacer = findViewById(R.id.str_titleLineSpacer)

        bindRowClicks()
        bindAccessibility()
        applyAttributes(attrs, defStyleAttr)
        help.syncVisibility()
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
     * Sets an optional leading feature icon. Passing `null` hides the icon slot (S0776).
     * S2390: clears the theme tint so full-color Drawables (e.g. launcher icons)
     * display in their natural colors rather than being converted into monochrome white silhouettes.
     */
    fun setIcon(icon: Drawable?) {
        iconView.imageTintList = null
        iconView.clearColorFilter()
        iconView.setImageDrawable(icon)
        iconView.visibility = if (icon == null) View.GONE else View.VISIBLE
    }

    /**
     * Sets an optional leading feature icon from a drawable resource (S0776).
     * S2390: applies the standard themed color for vector glyphs.
     */
    fun setIcon(@DrawableRes resId: Int) {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
        iconView.imageTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
        iconView.setImageResource(resId)
        iconView.visibility = View.VISIBLE
    }

    /**
     * Stores the help payload and makes the help icon available.
     * Call [setHelpVisible] (false) to hide without dropping the payload.
     */
    override fun setHelp(@StringRes titleRes: Int, @StringRes messageRes: Int) {
        help.setHelp(titleRes, messageRes)
    }

    /**
     * Shows or hides the help icon without dropping the stored help payload.
     * Hidden when no payload is configured regardless of the requested value.
     */
    override fun setHelpVisible(visible: Boolean) {
        help.setHelpVisible(visible)
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

    /**
     * Forwards enabled state to all interactive children so the row reads as one control.
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        checkBox.isEnabled = enabled
        iconView.isEnabled = enabled
        help.setEnabled(enabled)
        titleView.isEnabled = enabled
        subtitleView.isEnabled = enabled
        alpha = if (enabled) 1f else 0.5f
    }

    /**
     * Switches the row between the default "stretch-to-parent" layout and a compact layout
     * that hugs the title/subtitle so a trailing sibling (placed outside this row) sits
     * immediately next to the label rather than at the far right edge of the container.
     *
     * Default (`false`) keeps the canonical Trigger Row look: the text group has `weight=1`
     * and the title-line spacer pushes the helper icon next to the title. Setting `true`
     * collapses both weights, forces text to `wrap_content`, and lets the host LinearLayout
     * arrange this row plus a sibling button on a single tight line.
     *
     * Used by VR settings (S0249) where the "Test Immersive" button must sit next to the
     * master toggle row, not at the right edge in landscape.
     */
    fun setHugsTextContent(hug: Boolean) {
        val textGroupParams = textGroup.layoutParams as LayoutParams
        textGroupParams.width = if (hug) LayoutParams.WRAP_CONTENT else 0
        textGroupParams.weight = if (hug) 0f else 1f
        textGroup.layoutParams = textGroupParams

        val spacerParams = titleLineSpacer.layoutParams as LayoutParams
        spacerParams.weight = if (hug) 0f else 1f
        titleLineSpacer.layoutParams = spacerParams

        val subtitleParams = subtitleView.layoutParams as LayoutParams
        subtitleParams.width = if (hug) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT
        subtitleView.layoutParams = subtitleParams
    }

    /**
     * Injects or removes an optional trailing control hosted by this row.
     * Passing `null` clears the slot and hides it.
     */
    fun setTrailingControl(view: View?) {
        trailingSlot.removeAllViews()
        if (view == null) {
            trailingSlot.visibility = View.GONE
            return
        }
        (view.parent as? ViewGroup)?.removeView(view)
        trailingSlot.addView(view)
        trailingSlot.visibility = View.VISIBLE
    }

    /**
     * A touch no child consumed - the subtitle, the gaps, the background - must not toggle
     * (BOOLEAN-OPTION 2.1 section 3 rule 1). The row stays clickable only for the key and
     * accessibility-click paths, which reach [performClick] without a touch event.
     */
    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false

    private fun bindRowClicks() {
        val toggle = OnClickListener {
            if (isEnabled) checkBox.isChecked = !checkBox.isChecked
        }
        setOnClickListener(toggle)
        titleView.setOnClickListener(toggle)
        iconView.setOnClickListener(toggle)
        // The checkbox toggles itself natively on a tap, so it gets no second handler (one toggle per tap).
        // The native CheckedChangeListener fires for both programmatic and user-driven changes;
        // setCheckedSilently temporarily detaches it to suppress programmatic notifications.
        checkBox.setOnCheckedChangeListener { _, value ->
            timber.log.Timber.d("S4604: boolean option row toggled from checkbox, caption, glyph or key")
            checkedChangeListener?.invoke(value)
        }
    }

    private fun bindAccessibility() {
        ViewCompat.setAccessibilityDelegate(
            this,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = CHECKBOX_CLASS_NAME
                    info.isCheckable = true
                    info.isChecked = checkBox.isChecked
                    val explanation = subtitleView.text
                    if (subtitleView.visibility == View.VISIBLE && !explanation.isNullOrBlank()) {
                        info.hintText = explanation
                    }
                }
            },
        )
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.SettingsToggleRow, defStyleAttr, 0).use { typedArray ->
            val title = typedArray.getText(R.styleable.SettingsToggleRow_str_title) ?: ""
            setTitle(title)
            setSubtitle(typedArray.getText(R.styleable.SettingsToggleRow_str_subtitle))
            help.setPayload(
                typedArray.getText(R.styleable.SettingsToggleRow_str_helpTitle),
                typedArray.getText(R.styleable.SettingsToggleRow_str_helpMessage)
            )
            help.applyInitialVisibility(typedArray.getBoolean(R.styleable.SettingsToggleRow_str_showHelp, false))
            val initiallyChecked = typedArray.getBoolean(R.styleable.SettingsToggleRow_str_checked, false)
            checkBox.isChecked = initiallyChecked
            val iconRes = typedArray.getResourceId(R.styleable.SettingsToggleRow_str_icon, 0)
            if (iconRes != 0) setIcon(iconRes)
            applyTextSize(titleView, typedArray, R.styleable.SettingsToggleRow_str_titleTextSize)
            applyTextSize(subtitleView, typedArray, R.styleable.SettingsToggleRow_str_subtitleTextSize)
        }
    }

    private fun applyTextSize(view: TextView, typedArray: TypedArray, index: Int) {
        if (typedArray.hasValue(index)) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, typedArray.getDimension(index, view.textSize))
        }
    }

    private companion object {
        const val CHECKBOX_CLASS_NAME = "android.widget.CheckBox"
    }
}
