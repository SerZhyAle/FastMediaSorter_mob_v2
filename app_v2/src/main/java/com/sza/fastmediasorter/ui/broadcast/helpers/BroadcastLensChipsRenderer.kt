package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.View
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.chip.ChipGroup

/** Paints the lens choice of the broadcast entry screen as a single-selection chip row. */
class BroadcastLensChipsRenderer(
    private val label: TextView,
    private val group: ChipGroup,
    private val onLensSelected: (String) -> Unit,
) {

    fun render(lensUi: BroadcastEntryUi.LensUi) {
        val visibility = if (lensUi.visible) View.VISIBLE else View.GONE
        label.visibility = visibility
        group.visibility = visibility
        val shownIds = (0 until group.childCount).map { group.getChildAt(it).tag }
        if (shownIds != lensUi.options.map { it.id }) {
            group.removeAllViews()
            val context = group.context
            val labels = BroadcastLensLabelFormatter.labels(lensUi.options) { facing ->
                context.getString(BroadcastLensLabelFormatter.facingNameRes(facing))
            }
            lensUi.options.forEachIndexed { index, option -> group.addView(newChip(labels[index], option.id)) }
        }
        for (index in 0 until group.childCount) {
            val chip = group.getChildAt(index) as Chip
            chip.isChecked = chip.tag == lensUi.selectedLensId
        }
    }

    private fun newChip(text: String, lensId: String): Chip {
        val chip = Chip(group.context)
        // The Chip(Context) constructor resolves the theme's plain chip style, which is not checkable.
        chip.setChipDrawable(
            ChipDrawable.createFromAttributes(
                group.context,
                null,
                0,
                com.google.android.material.R.style.Widget_Material3_Chip_Filter,
            )
        )
        chip.text = text
        chip.tag = lensId
        chip.isCheckable = true
        chip.isCheckedIconVisible = true
        chip.isFocusable = true
        chip.isClickable = true
        chip.setOnClickListener { onLensSelected(lensId) }
        return chip
    }
}
