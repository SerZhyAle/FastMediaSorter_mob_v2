package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.res.use
import com.google.android.material.materialswitch.MaterialSwitch
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import timber.log.Timber

/**
 * Canonical reusable switch/toggle row for settings fragments and forms.
 *
 * Layout: switch (leftmost), text group (title + helper inline, subtitle below),
 * optional trailing action slot for the rare row that needs a secondary action.
 *
 * The view owns visual state only - checked persistence stays outside the component.
 * Tapping anywhere on the row toggles the switch. The helper icon opens [TooltipDialog]
 * when a help payload is configured; it is hidden otherwise without breaking the layout.
 *
 * Public XML attributes use the `str_` prefix (see `attrs.xml`).
 *
 * @see docs/ARCHITECTURE.md § "UI Patterns - Trigger Row" (Pattern A).
 */
class SettingsToggleRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val switchView: MaterialSwitch
    private val iconView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val helpIcon: ImageButton
    private val trailingSlot: FrameLayout
    private val textGroup: LinearLayout
    private val titleLineSpacer: View

    private var helpTitleText: CharSequence? = null
    private var helpMessageText: CharSequence? = null
    private var checkedChangeListener: ((Boolean) -> Unit)? = null

    /**
     * Current checked state of the embedded switch.
     */
    var isChecked: Boolean
        get() = switchView.isChecked
        set(value) {
            switchView.isChecked = value
        }

    /**
     * `true` when the optional help icon is visible.
     */
    @get:JvmName("getHelpVisible")
    val isHelpVisible: Boolean
        get() = helpIcon.visibility == View.VISIBLE

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.button_height)
        isClickable = true
        isFocusable = true
        if (background == null) {
            // Provide the standard ripple feedback on row taps so the whole row reads as one control.
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }
        LayoutInflater.from(context).inflate(R.layout.view_settings_toggle_row, this, true)

        switchView = findViewById(R.id.str_switch)
        iconView = findViewById(R.id.str_icon)
        titleView = findViewById(R.id.str_title)
        subtitleView = findViewById(R.id.str_subtitle)
        helpIcon = findViewById(R.id.str_iconHelp)
        trailingSlot = findViewById(R.id.str_trailingSlot)
        textGroup = findViewById(R.id.str_textGroup)
        titleLineSpacer = findViewById(R.id.str_titleLineSpacer)

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
     * Sets an optional leading feature icon. Passing `null` hides the icon slot (S0776).
     */
    fun setIcon(icon: Drawable?) {
        iconView.setImageDrawable(icon)
        iconView.visibility = if (icon == null) View.GONE else View.VISIBLE
    }

    /**
     * Sets an optional leading feature icon from a drawable resource (S0776).
     */
    fun setIcon(@DrawableRes resId: Int) {
        iconView.setImageResource(resId)
        iconView.visibility = View.VISIBLE
    }

    /**
     * Stores the help payload and makes the help icon available.
     * Call [setHelpVisible] (false) to hide without dropping the payload.
     */
    fun setHelp(@StringRes titleRes: Int, @StringRes messageRes: Int) {
        helpTitleText = context.getText(titleRes)
        helpMessageText = context.getText(messageRes)
        setHelpVisible(true)
    }

    /**
     * Shows or hides the help icon without dropping the stored help payload.
     * Hidden when no payload is configured regardless of the requested value.
     */
    fun setHelpVisible(visible: Boolean) {
        helpIcon.visibility = if (visible && hasHelpPayload()) View.VISIBLE else View.GONE
        helpIcon.contentDescription = helpTitleText?.toString().orEmpty()
    }

    /**
     * Registers a listener invoked when the embedded switch changes state.
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
        switchView.isChecked = value
        checkedChangeListener = listener
    }

    /**
     * Forwards enabled state to all interactive children so the row reads as one control.
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        switchView.isEnabled = enabled
        helpIcon.isEnabled = enabled
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

    private fun bindRowClicks() {
        setOnClickListener {
            if (!isEnabled) return@setOnClickListener
            switchView.isChecked = !switchView.isChecked
        }
        // The native CheckedChangeListener fires for both programmatic and user-driven changes;
        // setCheckedSilently temporarily detaches it to suppress programmatic notifications.
        switchView.setOnCheckedChangeListener { _, value ->
            checkedChangeListener?.invoke(value)
        }
    }

    private fun bindHelpClick() {
        helpIcon.setOnClickListener {
            val title = helpTitleText
            val message = helpMessageText
            if (title.isNullOrEmpty() || message.isNullOrEmpty()) {
                Timber.w("SettingsToggleRow: help requested without payload")
                return@setOnClickListener
            }
            TooltipDialog.show(context, title.toString(), message.toString())
        }
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.SettingsToggleRow, defStyleAttr, 0).use { typedArray ->
            val title = typedArray.getText(R.styleable.SettingsToggleRow_str_title) ?: ""
            setTitle(title)
            setSubtitle(typedArray.getText(R.styleable.SettingsToggleRow_str_subtitle))
            helpTitleText = typedArray.getText(R.styleable.SettingsToggleRow_str_helpTitle)
            helpMessageText = typedArray.getText(R.styleable.SettingsToggleRow_str_helpMessage)
            val showHelp = typedArray.getBoolean(R.styleable.SettingsToggleRow_str_showHelp, false)
            helpIcon.visibility = if (showHelp && hasHelpPayload()) View.VISIBLE else View.GONE
            val initiallyChecked = typedArray.getBoolean(R.styleable.SettingsToggleRow_str_checked, false)
            switchView.isChecked = initiallyChecked
            val iconRes = typedArray.getResourceId(R.styleable.SettingsToggleRow_str_icon, 0)
            if (iconRes != 0) setIcon(iconRes)
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
