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
import androidx.core.view.updateLayoutParams
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import timber.log.Timber

/**
 * Canonical reusable clickable selection row for settings fragments and forms.
 *
 * Layout: optional leading icon, text group (title + helper inline, subtitle below),
 * trailing value text, optional trailing control slot, and a trailing chevron.
 *
 * The whole row is one focus/click target. The helper icon opens [TooltipDialog] when a
 * help payload is configured; it is hidden otherwise without breaking the layout. Hosts
 * supply a row-click listener instead of wiring nested click handlers.
 *
 * Navigation mode (`ssr_navMode`) swaps the trailing chevron for a real forward arrow and
 * collapses the content to hug the left, for rows that open another screen/activity/dialog;
 * value rows keep the chevron.
 *
 * Public XML attributes use the `ssr_` prefix (see `attrs.xml`).
 *
 * @see docs/ARCHITECTURE.md § "UI Patterns - Trigger Row".
 */
class SettingsSelectionRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val iconView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val valueView: TextView
    private val helpIcon: ImageButton
    private val chevronView: ImageView
    private val trailingSlot: FrameLayout

    private var helpTitleText: CharSequence? = null
    private var helpMessageText: CharSequence? = null
    private var rowClickListener: ((View) -> Unit)? = null

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
        LayoutInflater.from(context).inflate(R.layout.view_settings_selection_row, this, true)

        iconView = findViewById(R.id.ssr_icon)
        titleView = findViewById(R.id.ssr_title)
        subtitleView = findViewById(R.id.ssr_subtitle)
        valueView = findViewById(R.id.ssr_value)
        helpIcon = findViewById(R.id.ssr_iconHelp)
        chevronView = findViewById(R.id.ssr_chevron)
        trailingSlot = findViewById(R.id.ssr_trailingSlot)

        bindRowClick()
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
     * Sets the trailing value text. Empty/null hides it without breaking row layout.
     */
    fun setValue(text: CharSequence?) {
        if (text.isNullOrEmpty()) {
            valueView.text = ""
            valueView.visibility = View.GONE
        } else {
            valueView.text = text
            valueView.visibility = View.VISIBLE
        }
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
     * Sets an optional leading icon. Passing `null` hides the icon slot.
     */
    fun setIcon(icon: Drawable?) {
        iconView.setImageDrawable(icon)
        iconView.visibility = if (icon == null) View.GONE else View.VISIBLE
    }

    /**
     * Sets an optional leading icon from a drawable resource.
     */
    fun setIcon(@DrawableRes resId: Int) {
        iconView.setImageResource(resId)
        iconView.visibility = View.VISIBLE
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
     * Hidden when no payload is configured regardless of the requested value.
     */
    fun setHelpVisible(visible: Boolean) {
        helpIcon.visibility = if (visible && hasHelpPayload()) View.VISIBLE else View.GONE
        helpIcon.contentDescription = helpTitleText?.toString().orEmpty()
    }

    /**
     * Shows or hides the trailing chevron.
     */
    fun setChevronVisible(visible: Boolean) {
        chevronView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Switches the trailing glyph between value mode (default chevron) and navigation mode
     * (real forward arrow). Navigation mode also collapses the content to the left so the arrow
     * sits right after the text; the row itself stays a full-width click target. Intended for
     * inflate-time configuration of rows that open another screen/activity/dialog.
     */
    fun setNavigationMode(enabled: Boolean) {
        if (enabled) {
            Timber.d("S0645: navigation mode applied - trailing arrow, no-stretch content")
            chevronView.setImageResource(R.drawable.ic_arrow_forward)
            chevronView.visibility = View.VISIBLE
            applyInlineLayout()
        } else {
            chevronView.setImageResource(R.drawable.ic_chevron_right)
        }
    }

    /**
     * Registers the listener invoked when the row is clicked. Replaces any previous listener.
     */
    fun setOnRowClickListener(listener: ((View) -> Unit)?) {
        rowClickListener = listener
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
     * Forwards enabled state to children so the row reads as one control.
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        helpIcon.isEnabled = enabled
        titleView.isEnabled = enabled
        subtitleView.isEnabled = enabled
        valueView.isEnabled = enabled
        alpha = if (enabled) 1f else 0.5f
    }

    private fun bindRowClick() {
        setOnClickListener { view ->
            if (!isEnabled) return@setOnClickListener
            rowClickListener?.invoke(view)
        }
    }

    private fun bindHelpClick() {
        helpIcon.setOnClickListener {
            val title = helpTitleText
            val message = helpMessageText
            if (title.isNullOrEmpty() || message.isNullOrEmpty()) {
                Timber.w("SettingsSelectionRow: help requested without payload")
                return@setOnClickListener
            }
            TooltipDialog.show(context, title.toString(), message.toString())
        }
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.SettingsSelectionRow, defStyleAttr, 0).use { typedArray ->
            setTitle(typedArray.getText(R.styleable.SettingsSelectionRow_ssr_title) ?: "")
            setValue(typedArray.getText(R.styleable.SettingsSelectionRow_ssr_value))
            setSubtitle(typedArray.getText(R.styleable.SettingsSelectionRow_ssr_subtitle))
            val iconRes = typedArray.getResourceId(R.styleable.SettingsSelectionRow_ssr_icon, 0)
            if (iconRes != 0) setIcon(iconRes)
            helpTitleText = typedArray.getText(R.styleable.SettingsSelectionRow_ssr_helpTitle)
            helpMessageText = typedArray.getText(R.styleable.SettingsSelectionRow_ssr_helpMessage)
            val showHelp = typedArray.getBoolean(R.styleable.SettingsSelectionRow_ssr_showHelp, false)
            helpIcon.visibility = if (showHelp && hasHelpPayload()) View.VISIBLE else View.GONE
            val showChevron = typedArray.getBoolean(R.styleable.SettingsSelectionRow_ssr_showChevron, true)
            chevronView.visibility = if (showChevron) View.VISIBLE else View.GONE
            if (typedArray.getBoolean(R.styleable.SettingsSelectionRow_ssr_inline, false)) {
                applyInlineLayout()
            }
            if (typedArray.getBoolean(R.styleable.SettingsSelectionRow_ssr_navMode, false)) {
                setNavigationMode(true)
            }
        }
    }

    /**
     * Collapses the row to hug its content: the text group stops stretching so the chevron sits
     * right after the value, and the tall touch-target band is dropped. Used for dense landscape
     * rows where the default full-width layout wastes vertical space (S0618 follow-up).
     */
    private fun applyInlineLayout() {
        minimumHeight = 0
        (findViewById<LinearLayout>(R.id.ssr_textGroup)).updateLayoutParams<LayoutParams> {
            width = LayoutParams.WRAP_CONTENT
            weight = 0f
        }
        findViewById<LinearLayout>(R.id.ssr_titleLine).updateLayoutParams<ViewGroup.LayoutParams> {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        findViewById<View>(R.id.ssr_titleLineSpacer).visibility = View.GONE
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
