package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import com.sza.fastmediasorter.R

/**
 * S1036: the explicit reset is the only path that clears a stored target - changing the action bound to
 * a slot must not, or switching away and back would silently drop the choice.
 *
 * S2256: shared by the edge-gesture slots and the launcher desktop swipes, so clearing a target is the
 * same icon, in the same place, with the same wording on both surfaces.
 */
object GestureTargetResetControl {

    // The size the edge slots have always used - kept so that surface looks untouched by the extraction.
    private const val ICON_SIZE_DP = 36

    fun create(context: Context, onClear: () -> Unit): ImageView {
        val size = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
        return ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            setImageResource(R.drawable.ic_clear)
            val borderless = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            background = context.obtainStyledAttributes(borderless).run { getDrawable(0).also { recycle() } }
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.gesture_slot_app_reset)
            setOnClickListener { onClear() }
        }
    }
}
