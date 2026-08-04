package com.sza.fastmediasorter.utils

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
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

/**
 * Keep important content and action targets inside system-bar safe bounds.
 *
 * Android 15 forces edge-to-edge for targetSdk 35, so ordinary form screens must
 * apply these insets even when they do not explicitly opt into fullscreen UI.
 *
 * Insets are passed through (not consumed) so sibling views in the same window still
 * receive them. [onApplied] reports the per-edge inset deltas that were added on top of
 * the captured base padding (0 for any disabled edge); callers use it for diagnostics.
 *
 * S1087: [useStatusBarHeightFallback] exists for the one screen that hides the status bar on purpose.
 * The fallback in [getStatusBarHeightSafe] guesses the bar's height from the platform resource when the
 * inset reads 0, which protects screens that would otherwise slide under a bar the window has not
 * measured yet - but for a surface that just removed the bar it reserves a band for something that is
 * no longer there, so the removal looks like it did nothing. Pass false only when the caller controls
 * the bar's visibility itself.
 *
 * Call this ONCE per view: it captures the view's current padding as the base and adds insets on top,
 * so a second call would treat the already-inset padding as the base and compound it. To re-apply after
 * a bar changes visibility, request a fresh dispatch (`ViewCompat.requestApplyInsets`) instead.
 */
fun View.applySystemBarInsetPadding(
    applyLeft: Boolean = true,
    applyTop: Boolean = true,
    applyRight: Boolean = true,
    applyBottom: Boolean = true,
    useStatusBarHeightFallback: Boolean = true,
    onApplied: ((left: Int, top: Int, right: Int, bottom: Int) -> Unit)? = null,
) {
    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom

    fun apply(insets: WindowInsetsCompat) {
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val left = if (applyLeft) maxOf(systemBars.left, cutout.left) else 0
        val top = if (applyTop) {
            val statusBarTop = if (useStatusBarHeightFallback) {
                insets.getStatusBarHeightSafe(resources)
            } else {
                systemBars.top
            }
            maxOf(systemBars.top, cutout.top, statusBarTop)
        } else {
            0
        }
        val right = if (applyRight) maxOf(systemBars.right, cutout.right) else 0
        val bottom = if (applyBottom) maxOf(systemBars.bottom, cutout.bottom) else 0
        updatePadding(
            left = baseLeft + left,
            top = baseTop + top,
            right = baseRight + right,
            bottom = baseBottom + bottom,
        )
        onApplied?.invoke(left, top, right, bottom)
    }

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        apply(insets)
        insets
    }
    ViewCompat.getRootWindowInsets(this)?.let(::apply) ?: ViewCompat.requestApplyInsets(this)
}
