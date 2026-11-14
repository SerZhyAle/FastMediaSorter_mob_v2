package com.sza.fastmediasorter.ui.common.widget

import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import kotlin.math.ceil

/**
 * BUTTONS section 3 rule 3: a button whose caption flips between states reserves the widest known state,
 * so changing Start to Stop never moves the neighbouring targets of a content-fit row.
 */
object ButtonWidthReserver {

    /** Raises [button]'s minimum width to fit its widest caption in [captionIds]; never lowers an existing one. */
    fun reserveForStrings(button: MaterialButton, @StringRes vararg captionIds: Int) {
        reserve(button, captionIds.map { button.context.getString(it) })
    }

    fun reserve(button: MaterialButton, captions: List<CharSequence>) {
        val paint = button.paint
        val textWidth = captions.maxOfOrNull { paint.measureText(it, 0, it.length) } ?: return
        val iconWidth = button.icon?.let { icon ->
            (if (button.iconSize > 0) button.iconSize else icon.intrinsicWidth) + button.iconPadding
        } ?: 0
        val required = ceil(textWidth).toInt() + iconWidth + button.paddingStart + button.paddingEnd
        if (required > button.minWidth) button.minWidth = required
    }
}
