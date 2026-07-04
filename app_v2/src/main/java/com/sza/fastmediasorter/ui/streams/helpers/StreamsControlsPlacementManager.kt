package com.sza.fastmediasorter.ui.streams.helpers

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible

/**
 * S0940: relocates the streams search/filter/sort control group between two host slots.
 *
 * Portrait keeps the group as a bar under the toolbar; landscape moves it into the in-header
 * host so the list/grid gains the freed vertical space. The streams window handles orientation
 * config changes without recreating (S0692, so a live stream is not torn down), therefore the
 * move is done programmatically here rather than via a layout-variant swap.
 */
class StreamsControlsPlacementManager(
    private val controls: View,
    private val headerHost: ViewGroup,
) {
    // Captured before the first relocation, while the group still sits in its portrait slot.
    private val belowParent: ViewGroup? = controls.parent as? ViewGroup
    private val belowIndex: Int = belowParent?.indexOfChild(controls) ?: -1

    fun applyForOrientation(isLandscape: Boolean) {
        val targetParent: ViewGroup = if (isLandscape) headerHost else (belowParent ?: return)
        if (controls.parent !== targetParent) {
            (controls.parent as? ViewGroup)?.removeView(controls)
            if (isLandscape) {
                headerHost.addView(controls)
            } else {
                val index = belowIndex.coerceIn(0, belowParent?.childCount ?: 0)
                belowParent?.addView(controls, index)
            }
        }
        headerHost.isVisible = isLandscape
    }
}
