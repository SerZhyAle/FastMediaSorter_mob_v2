package com.sza.fastmediasorter.ui.main.helpers

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R

/**
 * Manages the main window dropdown menu display using a multi-column bounded popup.
 *
 * When the dropdown menu contains many items (> 8), renders them in a 2-column grid and caps the
 * height to the screen's safe bounds, ensuring all items fit without being cut off.
 */
class MainDropdownMenuPopupManager {

    private val gridPopup = BoundedGridPopupManager()

    fun show(
        activity: AppCompatActivity,
        anchor: View,
        items: List<MenuItem>,
        onItemClicked: (Int) -> Unit
    ) {
        dismiss()
        if (items.isEmpty()) return

        val inflater = LayoutInflater.from(activity)
        gridPopup.show(
            anchor = anchor,
            columnCount = if (items.size > MAX_ITEMS_SINGLE_COLUMN) 2 else 1,
            equalColumns = false,
        ) { grid, dismissPopup ->
            items.map { menuItem ->
                val button = inflater.inflate(
                    R.layout.item_main_dropdown_grid_entry,
                    grid,
                    false
                ) as MaterialButton

                button.id = View.generateViewId()
                button.text = menuItem.title
                button.contentDescription = menuItem.title
                button.icon = menuItem.icon
                menuItem.iconTintList?.let { button.iconTint = it }

                button.setOnClickListener {
                    dismissPopup()
                    onItemClicked(menuItem.itemId)
                }
                button
            }
        }
    }

    fun dismiss() {
        gridPopup.dismiss()
    }

    companion object {
        private const val MAX_ITEMS_SINGLE_COLUMN = 8
    }
}
