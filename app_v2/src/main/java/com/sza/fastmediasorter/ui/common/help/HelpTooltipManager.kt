package com.sza.fastmediasorter.ui.common.help

import android.content.res.Configuration
import android.view.View
import androidx.appcompat.widget.TooltipCompat
import com.sza.fastmediasorter.R

/**
 * Names the help button's key only where the key exists: a device without a hardware keyboard has no
 * F1 to press, so naming it there would advertise a shortcut the user cannot reach.
 *
 * TooltipCompat, not the `android:tooltipText` attribute, because that attribute needs API 26 and the
 * legacy flavor runs on API 23.
 */
object HelpTooltipManager {

    fun apply(helpButton: View) {
        val hasKeyboard = helpButton.resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS
        val textRes = if (hasKeyboard) R.string.help_with_key else R.string.help
        TooltipCompat.setTooltipText(helpButton, helpButton.context.getString(textRes))
    }
}
