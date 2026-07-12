package com.sza.fastmediasorter.ui.streams.helpers

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R

/**
 * S0940: relocates the streams search/filter/sort control group between two host slots.
 *
 * Portrait keeps the group as a bar under the toolbar; landscape moves it into the in-header
 * host so the list/grid gains the freed vertical space. The streams window handles orientation
 * config changes without recreating (S0692, so a live stream is not torn down), therefore the
 * move is done programmatically here rather than via a layout-variant swap.
 *
 * In the header slot the group must not stretch: a full-width search field covers the toolbar
 * title, and its outlined box is unreadable on the primary-colored toolbar. Landscape therefore
 * pins the search field to a fixed width and fills its box with colorSurface; portrait restores
 * the original full-width transparent look. The filter/sort icons likewise swap to colorOnPrimary
 * in the header (light themes resolve colorControlNormal to a dark tint that vanishes on the
 * primary-colored toolbar) and back to their inflated tint in portrait.
 */
class StreamsControlsPlacementManager(
    private val controls: View,
    private val headerHost: ViewGroup,
    private val searchField: TextInputLayout,
) {
    // Captured before the first relocation, while the group still sits in its portrait slot.
    private val belowParent: ViewGroup? = controls.parent as? ViewGroup
    private val belowIndex: Int = belowParent?.indexOfChild(controls) ?: -1
    private val portraitSearchBoxColor: Int = searchField.boxBackgroundColor
    private val filterButton: ImageButton? = controls.findViewById(R.id.btnFilter)
    private val sortButton: ImageButton? = controls.findViewById(R.id.btnSort)
    private val portraitIconTint: ColorStateList? = filterButton?.imageTintList

    fun applyForOrientation(isLandscape: Boolean) {
        val targetParent: ViewGroup = if (isLandscape) headerHost else (belowParent ?: return)
        if (controls.parent !== targetParent) {
            (controls.parent as? ViewGroup)?.removeView(controls)
            if (isLandscape) {
                // Wrap so the toolbar title stays visible left of the group.
                headerHost.addView(
                    controls,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } else {
                val index = belowIndex.coerceIn(0, belowParent?.childCount ?: 0)
                belowParent?.addView(
                    controls,
                    index,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            applySearchFieldStyle(isLandscape)
            applyIconTint(isLandscape)
        }
        headerHost.isVisible = isLandscape
    }

    private fun applySearchFieldStyle(isLandscape: Boolean) {
        val lp = searchField.layoutParams as? LinearLayout.LayoutParams ?: return
        if (isLandscape) {
            lp.width = searchField.resources.getDimensionPixelSize(R.dimen.streams_header_search_width)
            lp.weight = 0f
            searchField.boxBackgroundColor =
                MaterialColors.getColor(searchField, com.google.android.material.R.attr.colorSurface)
        } else {
            lp.width = 0
            lp.weight = 1f
            searchField.boxBackgroundColor = portraitSearchBoxColor
        }
        searchField.layoutParams = lp
    }

    private fun applyIconTint(isLandscape: Boolean) {
        val tint = if (isLandscape) {
            ColorStateList.valueOf(
                MaterialColors.getColor(controls, com.google.android.material.R.attr.colorOnPrimary)
            )
        } else {
            portraitIconTint
        }
        filterButton?.imageTintList = tint
        sortButton?.imageTintList = tint
    }
}
