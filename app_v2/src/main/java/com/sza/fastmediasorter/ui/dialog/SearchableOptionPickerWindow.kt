package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.ViewGroup
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding

/**
 * S1286: the window and surface treatment shared by every FragmentResult-hosted picker built on
 * [DialogSearchableOptionPickerBinding]. Those hosts draw the layout themselves instead of wrapping it
 * in a MaterialAlertDialog, so the dialog window is transparent and only the option rows carry a
 * background of their own - without the card applied here the title and the search field would sit
 * directly on the dimmed host activity and read as barely visible ghosts.
 *
 * Height is a cap, not a fixed size: a short option list shrinks to content while a long one stops at
 * [HEIGHT_FRACTION] of the window and scrolls, so the dialog follows the window instead of the former
 * hardcoded 300dp list.
 */
object SearchableOptionPickerWindow {

    private const val WIDTH_FRACTION = 0.92f
    private const val MAX_WIDTH_DP = 560f
    private const val HEIGHT_FRACTION = 0.8f

    // S1095's values, kept exactly: a cell narrower than this ellipsizes an ordinary app or resource
    // name, and two columns is what makes a phone-width dialog worth gridding at all.
    private const val MIN_CELL_DP = 160f
    private const val MIN_COLUMNS = 2

    /** Full treatment for a self-drawn host: opaque card, capped height, centered sized window. */
    fun apply(dialog: Dialog?, binding: DialogSearchableOptionPickerBinding) {
        binding.root.setBackgroundResource(R.drawable.bg_option_picker_surface)
        applyHeightCap(binding)
        val width = widthPx(binding.root.resources.displayMetrics)
        dialog?.window?.apply {
            // The rounded card above is the visible surface, so the window itself must not paint square
            // corners behind it.
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
    }

    /**
     * Height cap only - for the MaterialAlertDialog-hosted picker, which already owns a Material surface
     * and must not gain a second one.
     */
    fun applyHeightCap(binding: DialogSearchableOptionPickerBinding) {
        val metrics = binding.root.resources.displayMetrics
        binding.root.maxHeightPx = (metrics.heightPixels * HEIGHT_FRACTION).toInt()
    }

    /**
     * The width this treatment gives the dialog. Exposed because a grid host derives its column count
     * from the dialog width, and that must be the same number the window is actually sized to.
     */
    fun widthPx(metrics: DisplayMetrics): Int = minOf(
        (metrics.widthPixels * WIDTH_FRACTION).toInt(),
        (metrics.density * MAX_WIDTH_DP).toInt(),
    )

    /**
     * S1413: how many columns a grid host should ask the controller for - as many [minCellDp]-wide cells
     * as [widthPx] allows, never fewer than [minColumns].
     *
     * It lives here rather than in the host or the controller because the answer is only correct against
     * the width this object actually gives the window. S1095 kept a private copy inside the app picker,
     * and the next host that wanted a grid had no way to reuse it - which is why the resource picker
     * stayed a single column until this ticket.
     */
    fun columnsFor(
        metrics: DisplayMetrics,
        minCellDp: Float = MIN_CELL_DP,
        minColumns: Int = MIN_COLUMNS,
    ): Int {
        val widthDp = widthPx(metrics) / metrics.density
        return (widthDp / minCellDp).toInt().coerceAtLeast(minColumns)
    }
}
