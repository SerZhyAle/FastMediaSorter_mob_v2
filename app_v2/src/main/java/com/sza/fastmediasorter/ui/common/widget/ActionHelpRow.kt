package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.res.use
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R

/**
 * Compact reusable `button + help icon` row for dialogs and dense form sections.
 *
 * Replaces the hand-built strips that paired a `MaterialButton` with a standalone help
 * `ImageView` and manual `TooltipDialog` wiring (S0567 survey item 7, e.g. GIF editor).
 *
 * The button uses the project Button Taxonomy (default `Widget.FastMediaSorter.Button.Outlined`,
 * overridable via `ahr_buttonStyle`) - never a raw `Widget.Material3.Button.*`. The help icon
 * chrome is the shared [HelpRowDelegate]. Public XML attributes use the `ahr_` prefix.
 */
class ActionHelpRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), HelpableRow {

    private var button: MaterialButton
    private val help: HelpRowDelegate

    override val isHelpVisible: Boolean
        get() = help.isHelpVisible

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_action_help_row, this, true)

        button = findViewById(R.id.ahr_button)
        help = HelpRowDelegate(findViewById(R.id.ahr_iconHelp), "ActionHelpRow")

        applyAttributes(attrs, defStyleAttr)
        help.syncVisibility()
    }

    /**
     * Sets the action button label from a raw text value.
     */
    fun setButtonText(text: CharSequence?) {
        button.text = text
    }

    /**
     * Sets the action button label from a string resource.
     */
    fun setButtonText(@StringRes resId: Int) {
        button.setText(resId)
    }

    /**
     * Registers the action button click listener. Replaces any previous listener.
     */
    fun setOnButtonClickListener(listener: ((View) -> Unit)?) {
        if (listener == null) {
            button.setOnClickListener(null)
        } else {
            button.setOnClickListener { listener(it) }
        }
    }

    /**
     * Enables/disables only the action button; the help icon stays interactive.
     */
    fun setButtonEnabled(enabled: Boolean) {
        button.isEnabled = enabled
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
        button.isEnabled = enabled
        help.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.5f
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.ActionHelpRow, defStyleAttr, 0).use { typedArray ->
            val styleRes = typedArray.getResourceId(R.styleable.ActionHelpRow_ahr_buttonStyle, 0)
            if (styleRes != 0) recreateButtonWithStyle(styleRes)
            setButtonText(typedArray.getText(R.styleable.ActionHelpRow_ahr_buttonText))
            help.setPayload(
                typedArray.getText(R.styleable.ActionHelpRow_ahr_helpTitle),
                typedArray.getText(R.styleable.ActionHelpRow_ahr_helpMessage)
            )
            help.applyInitialVisibility(typedArray.getBoolean(R.styleable.ActionHelpRow_ahr_showHelp, false))
        }
    }

    /**
     * Swaps the default Outlined button for one built with the requested taxonomy style.
     * MaterialButton applies a full style only through its construction context, so the
     * button is rebuilt rather than restyled in place. Keeps the same id and layout slot.
     */
    private fun recreateButtonWithStyle(styleRes: Int) {
        val index = indexOfChild(button)
        val oldParams = button.layoutParams
        val text = button.text
        removeView(button)
        button = MaterialButton(ContextThemeWrapper(context, styleRes), null, 0).apply {
            id = R.id.ahr_button
            layoutParams = oldParams
            this.text = text
        }
        addView(button, index)
    }
}
