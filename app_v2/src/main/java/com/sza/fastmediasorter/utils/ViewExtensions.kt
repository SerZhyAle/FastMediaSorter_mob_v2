package com.sza.fastmediasorter.utils

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R

/**
 * Extension functions for View interactions
 */

/**
 * Set badge text for a view that has a sibling TextView with id R.id.tvFilterBadge
 * Intended for use with ImageButtons wrapped in FrameLayout with a badge TextView
 */
fun View.setBadgeText(text: String?) {
    val parentGroup = parent as? ViewGroup ?: return
    // Specific logic for filter badge as per layout structure
    val badge = parentGroup.findViewById<TextView>(R.id.tvFilterBadge)
    if (badge != null) {
        if (text.isNullOrEmpty() || text == "0") {
            badge.isVisible = false
            badge.text = ""
        } else {
            badge.isVisible = true
            badge.text = text
        }
    }
}

/**
 * Clear badge for a view
 */
fun View.clearBadge() {
    setBadgeText(null)
}
