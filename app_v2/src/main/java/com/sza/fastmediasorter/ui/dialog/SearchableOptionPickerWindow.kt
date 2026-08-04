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
}
