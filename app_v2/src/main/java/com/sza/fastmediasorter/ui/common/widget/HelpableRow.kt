package com.sza.fastmediasorter.ui.common.widget

import android.view.View
import android.widget.ImageButton
import androidx.annotation.StringRes
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import timber.log.Timber

/**
 * The help-icon contract every row widget that carries one exposes: a payload, a visibility switch
 * that never drops it, and the current visibility.
 */
interface HelpableRow {

    /** `true` when the help icon is visible. */
    val isHelpVisible: Boolean

    /** Stores the help payload and makes the help icon available. */
    fun setHelp(@StringRes titleRes: Int, @StringRes messageRes: Int)

    /** Shows or hides the help icon without dropping the stored help payload. */
    fun setHelpVisible(visible: Boolean)
}

/**
 * The help-icon chrome [ActionHelpRow], [FormCheckboxRow] and [SettingsToggleRow] used to carry three
 * times each: payload storage, the [TooltipDialog] click wiring, the payload-gated visibility rule and
 * the icon content description.
 *
 * [logTag] is the owning row's simple name so the "help requested without payload" warning keeps
 * naming the row the user actually tapped.
 */
class HelpRowDelegate(
    private val helpIcon: ImageButton,
    private val logTag: String,
) {

    private var helpTitleText: CharSequence? = null
    private var helpMessageText: CharSequence? = null

    val isHelpVisible: Boolean
        get() = helpIcon.visibility == View.VISIBLE

    init {
        helpIcon.setOnClickListener {
            val title = helpTitleText
            val message = helpMessageText
            if (title.isNullOrEmpty() || message.isNullOrEmpty()) {
                Timber.w("$logTag: help requested without payload")
                return@setOnClickListener
            }
            TooltipDialog.show(helpIcon.context, title.toString(), message.toString())
        }
    }

    /** Stores the help payload read from XML attributes without touching the icon visibility. */
    fun setPayload(title: CharSequence?, message: CharSequence?) {
        helpTitleText = title
        helpMessageText = message
    }

    fun setHelp(@StringRes titleRes: Int, @StringRes messageRes: Int) {
        val context = helpIcon.context
        helpTitleText = context.getText(titleRes)
        helpMessageText = context.getText(messageRes)
        setHelpVisible(true)
    }

    fun setHelpVisible(visible: Boolean) {
        helpIcon.visibility = if (visible && hasPayload()) View.VISIBLE else View.GONE
        helpIcon.contentDescription = helpTitleText?.toString().orEmpty()
    }

    /**
     * Applies the initial visibility an XML `showHelp` attribute asked for; a row with no payload
     * keeps the icon hidden whatever the attribute says.
     */
    fun applyInitialVisibility(showHelp: Boolean) {
        helpIcon.visibility = if (showHelp && hasPayload()) View.VISIBLE else View.GONE
    }

    /** Hides an icon left visible without a payload and syncs its content description. */
    fun syncVisibility() {
        if (!hasPayload()) {
            helpIcon.visibility = View.GONE
        }
        helpIcon.contentDescription = helpTitleText?.toString().orEmpty()
    }

    fun setEnabled(enabled: Boolean) {
        helpIcon.isEnabled = enabled
    }

    private fun hasPayload(): Boolean =
        !helpTitleText.isNullOrEmpty() && !helpMessageText.isNullOrEmpty()
}
