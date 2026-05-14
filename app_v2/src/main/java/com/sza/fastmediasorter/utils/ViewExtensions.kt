package com.sza.fastmediasorter.utils

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
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

/**
 * Returns the status bar height in pixels.
 *
 * Three-tier fallback for OEM Android 8.x (API 26/27) devices where
 * WindowInsetsCompat.Type.statusBars() may report 0 despite a visible status bar:
 * 1. Modern typed API (correct on API 30+ and well-behaved OEMs).
 * 2. Deprecated systemWindowInsetTop (broader OEM compatibility on API 20–29).
 * 3. System resource "status_bar_height" (always available, OEM-independent).
 */
@Suppress("DEPRECATION")
fun WindowInsetsCompat.getStatusBarHeightSafe(resources: Resources): Int {
    val fromType = getInsets(WindowInsetsCompat.Type.statusBars()).top
    if (fromType > 0) return fromType
    val fromSystemWindow = systemWindowInsetTop
    if (fromSystemWindow > 0) return fromSystemWindow
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
}
