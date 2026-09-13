package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.PopupMainDropdownGridBinding

/**
 * Manages the main window dropdown menu display using a multi-column bounded [PopupWindow].
 *
 * When the dropdown menu contains many items (> 8), renders them in a 2-column grid and caps the
 * height to the screen's safe bounds, ensuring all items fit without being cut off.
 */
class MainDropdownMenuPopupManager {

    private var activePopup: PopupWindow? = null

    fun show(
        activity: AppCompatActivity,
        anchor: View,
        items: List<MenuItem>,
        onItemClicked: (Int) -> Unit
    ) {
        dismiss()
        if (items.isEmpty()) return

        val binding = PopupMainDropdownGridBinding.inflate(activity.layoutInflater)
        val grid = binding.gridContainer

        grid.columnCount = if (items.size > MAX_ITEMS_SINGLE_COLUMN) 2 else 1

        val inflater = LayoutInflater.from(activity)
        items.forEach { menuItem ->
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
                dismiss()
                onItemClicked(menuItem.itemId)
            }

            grid.addView(button)
        }

        val popup = PopupWindow(
            binding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(resolvePopupBackground(activity))
            elevation = activity.resources.getDimension(R.dimen.card_elevation)
        }

        val rootInsets = ViewCompat.getRootWindowInsets(anchor)
        val sysBars = rootInsets?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val displayMetrics = activity.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val bottomInset = sysBars?.bottom ?: 0

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorBottom = location[1] + anchor.height

        val maxAvailableHeight = (screenHeight - anchorBottom - bottomInset - POPUP_BOTTOM_MARGIN_PX)
            .coerceAtLeast(MIN_POPUP_HEIGHT_PX)

        binding.scrollView.post {
            val measuredHeight = binding.root.measuredHeight
            if (measuredHeight > maxAvailableHeight) {
                val params = binding.scrollView.layoutParams
                params.height = maxAvailableHeight
                binding.scrollView.layoutParams = params
            }
        }

        activePopup = popup
        popup.showAsDropDown(anchor)
    }

    fun dismiss() {
        activePopup?.dismiss()
        activePopup = null
    }

    private fun resolvePopupBackground(context: Context): Drawable {
        val bgAttr = com.google.android.material.R.attr.popupMenuBackground
        val attrs = context.obtainStyledAttributes(intArrayOf(bgAttr))
        val drawable = attrs.getDrawable(0)
        attrs.recycle()
        if (drawable != null) return drawable
        val surface = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, surface, true)
        return ColorDrawable(surface.data)
    }

    companion object {
        private const val MAX_ITEMS_SINGLE_COLUMN = 8
        private const val POPUP_BOTTOM_MARGIN_PX = 16
        private const val MIN_POPUP_HEIGHT_PX = 300
    }
}
