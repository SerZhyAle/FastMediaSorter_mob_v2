package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.res.use
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import timber.log.Timber

/**
 * Compact reusable `button + help icon` row for dialogs and dense form sections.
 *
 * Replaces the hand-built strips that paired a `MaterialButton` with a standalone help
 * `ImageView` and manual `TooltipDialog` wiring (S0567 survey item 7, e.g. GIF editor).
 *
 * The button uses the project Button Taxonomy (default `Widget.FastMediaSorter.Button.Outlined`,
 * overridable via `ahr_buttonStyle`) - never a raw `Widget.Material3.Button.*`. The row owns
 * the help icon -> [TooltipDialog] wiring. Public XML attributes use the `ahr_` prefix.
 */
class ActionHelpRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var button: MaterialButton
    private val helpIcon: ImageButton

    private var helpTitleText: CharSequence? = null
    private var helpMessageText: CharSequence? = null

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_action_help_row, this, true)

        button = findViewById(R.id.ahr_button)
        helpIcon = findViewById(R.id.ahr_iconHelp)

        bindHelpClick()
        applyAttributes(attrs, defStyleAttr)
        syncHelpVisibility()
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
        button.isEnabled = enabled
        helpIcon.isEnabled = enabled
        alpha = if (enabled) 1f else 0.5f
    }

    private fun bindHelpClick() {
        helpIcon.setOnClickListener {
            val title = helpTitleText
            val message = helpMessageText
            if (title.isNullOrEmpty() || message.isNullOrEmpty()) {
                Timber.w("ActionHelpRow: help requested without payload")
                return@setOnClickListener
            }
            TooltipDialog.show(context, title.toString(), message.toString())
        }
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.ActionHelpRow, defStyleAttr, 0).use { typedArray ->
            val styleRes = typedArray.getResourceId(R.styleable.ActionHelpRow_ahr_buttonStyle, 0)
            if (styleRes != 0) recreateButtonWithStyle(styleRes)
            setButtonText(typedArray.getText(R.styleable.ActionHelpRow_ahr_buttonText))
            helpTitleText = typedArray.getText(R.styleable.ActionHelpRow_ahr_helpTitle)
            helpMessageText = typedArray.getText(R.styleable.ActionHelpRow_ahr_helpMessage)
            val showHelp = typedArray.getBoolean(R.styleable.ActionHelpRow_ahr_showHelp, false)
            helpIcon.visibility = if (showHelp && hasHelpPayload()) View.VISIBLE else View.GONE
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
