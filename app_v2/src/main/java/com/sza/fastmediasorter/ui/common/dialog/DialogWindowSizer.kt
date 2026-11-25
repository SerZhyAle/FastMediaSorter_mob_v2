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
     *
     * [wide] raises the ceiling to `@dimen/dialog_wide_max_width` for a dialog whose content is a
     * grid of chips that wraps badly inside the shared phone ceiling (S4846). It never lowers the
     * shared ceiling, so a tablet that already allows more keeps it.
     */
    fun resolveWidthPx(resources: Resources, wide: Boolean = false): Int {
        val minWidth = resources.getDimensionPixelSize(R.dimen.dialog_min_width)
        val sharedMaxWidth = resources.getDimensionPixelSize(R.dimen.dialog_max_width)
        val maxWidth = if (wide) {
            maxOf(sharedMaxWidth, resources.getDimensionPixelSize(R.dimen.dialog_wide_max_width))
        } else {
            sharedMaxWidth
        }
        val screenWidth = resources.displayMetrics.widthPixels
        return screenWidth.coerceIn(minWidth, maxWidth)
    }

    /** Applies the shared width; callers may supply a height already bounded by system insets. */
    fun applyTo(dialog: Dialog, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT, wide: Boolean = false) {
        val window = dialog.window ?: return
        window.setLayout(resolveWidthPx(dialog.context.resources, wide), height)
    }
}
