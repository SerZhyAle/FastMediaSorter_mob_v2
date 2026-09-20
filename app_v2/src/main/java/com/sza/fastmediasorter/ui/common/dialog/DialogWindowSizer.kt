package com.sza.fastmediasorter.ui.common.dialog

import android.app.Dialog
import android.content.res.Resources
import android.view.ViewGroup
import com.sza.fastmediasorter.R

/**
 * Single source of truth for a floating dialog's window width, clamped between
 * `@dimen/dialog_min_width` and `@dimen/dialog_max_width` (S3242).
 *
 * Replaces per-dialog `window?.setLayout(..)` calls that each computed a screen-width
 * fraction independently and never respected a shared minimum/maximum.
 */
object DialogWindowSizer {

    /**
     * Resolves the dialog window width in pixels for the current screen width, clamped to the
     * shared min/max bounds so a phone in landscape does not stretch a dialog edge to edge and a
     * narrow screen does not shrink it below a usable minimum.
     */
    fun resolveWidthPx(resources: Resources): Int {
        val minWidth = resources.getDimensionPixelSize(R.dimen.dialog_min_width)
        val maxWidth = resources.getDimensionPixelSize(R.dimen.dialog_max_width)
        val screenWidth = resources.displayMetrics.widthPixels
        return screenWidth.coerceIn(minWidth, maxWidth)
    }

    /** Applies the resolved width and a wrap-content height to [dialog]'s window. */
    fun applyTo(dialog: Dialog) {
        val window = dialog.window ?: return
        window.setLayout(resolveWidthPx(dialog.context.resources), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
