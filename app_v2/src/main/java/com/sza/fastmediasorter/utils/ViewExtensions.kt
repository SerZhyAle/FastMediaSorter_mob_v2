package com.sza.fastmediasorter.utils

import android.content.res.Resources
import android.graphics.Rect
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.sza.fastmediasorter.R
import java.text.BreakIterator

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
 * 2. Deprecated systemWindowInsetTop (broader OEM compatibility on API 20-29).
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
 * The base padding is captured on the FIRST call for a given view and reused by every later one, so
 * re-applying with a different edge mask - a surface that hands the status area back and forth, say -
 * recomputes from the bare view instead of stacking one inset on top of the one it added last time
 * (S1766 regression: the launcher desktop lost a navigation bar's height on every foreground return).
 * Re-applying only to pick up a changed inset still needs no call at all: the registered listener does
 * it, and `ViewCompat.requestApplyInsets` asks for a fresh dispatch.
 *
 * S2667: [suspendWhile] lets a caller sit out an inset episode it starts and ends itself. A padding
 * change requests a layout pass over the whole subtree, and on the launcher desktop that single pass
 * was measured at 1.53 s of a 1.555 s frame - so hiding the system bars for a full-screen black overlay
 * cost a visible freeze on the way in and another on the way out. Skipping the recompute is safe only
 * for an episode that restores the insets it changed, because nothing re-applies afterwards: the
 * padding left standing is the one the view already had.
 */
fun View.applySystemBarInsetPadding(
    applyLeft: Boolean = true,
    applyTop: Boolean = true,
    applyRight: Boolean = true,
    applyBottom: Boolean = true,
    useStatusBarHeightFallback: Boolean = true,
    suspendWhile: (() -> Boolean)? = null,
    onApplied: ((left: Int, top: Int, right: Int, bottom: Int) -> Unit)? = null,
) {
    val base = systemBarInsetBasePadding()
    val baseLeft = base.left
    val baseTop = base.top
    val baseRight = base.right
    val baseBottom = base.bottom

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
        if (suspendWhile?.invoke() != true) apply(insets)
        insets
    }
    ViewCompat.getRootWindowInsets(this)?.let(::apply) ?: ViewCompat.requestApplyInsets(this)
}

/**
 * Shrinks the text until its widest unbreakable run fits on one line, never below [minTextSizePx].
 *
 * A size chosen without the view's width lets the line breaker split a word in the middle once that
 * word is wider than the line ("Download" / "s"); `maxLines` caps the line count but does not prevent
 * the split. The runs are the line breaker's own break opportunities, so "Pictures/Holiday" may still
 * wrap after the slash.
 *
 * Install once per view: the check re-runs on every layout, because the first layout is not the last -
 * a dialog window is laid out at one width and then resized, so a one-shot check fitted the text to a
 * width the button no longer had. The shrink itself waits for pre-draw: a size change made during a
 * RecyclerView layout pass has its re-measure swallowed, which left a one-line name in a two-line box.
 * It only ever shrinks; a caller that resets the size on rebind gets a fresh fit from that size.
 * The listener compares by value, so a repeated call replaces the previous one instead of stacking.
 */
fun TextView.keepLongestWordOnOneLine(minTextSizePx: Float) {
    val fitter = LongestWordFitter(minTextSizePx)
    removeOnLayoutChangeListener(fitter)
    addOnLayoutChangeListener(fitter)
}

private data class LongestWordFitter(val minTextSizePx: Float) : View.OnLayoutChangeListener {
    override fun onLayoutChange(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int,
    ) {
        view.doOnPreDraw { (view as? TextView)?.shrinkLongestWordToWidth(minTextSizePx) }
    }
}

private fun TextView.shrinkLongestWordToWidth(minTextSizePx: Float) {
    val available = width - totalPaddingLeft - totalPaddingRight
    if (available <= 0 || textSize <= minTextSizePx) return
    val probe = TextPaint(paint)
    var size = textSize
    while (size > minTextSizePx && probe.widestUnbreakableRun(text) > available) {
        size = (size - 1f).coerceAtLeast(minTextSizePx)
        probe.textSize = size
    }
    if (size < textSize) setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
}

private fun TextPaint.widestUnbreakableRun(text: CharSequence): Float {
    val source = text.toString()
    val breaker = BreakIterator.getLineInstance().apply { setText(source) }
    var widest = 0f
    var start = breaker.first()
    var end = breaker.next()
    while (end != BreakIterator.DONE) {
        widest = maxOf(widest, measureText(source.substring(start, end).trimEnd()))
        start = end
        end = breaker.next()
    }
    return widest
}

/**
 * The view's padding as it stood before any inset was added to it, remembered on the view itself.
 *
 * Read on every [applySystemBarInsetPadding] call and written only by the first one: the padding the
 * later calls see is the previous call's own output, and measuring from that is what turns a repeated
 * application into a compounding one.
 */
private fun View.systemBarInsetBasePadding(): Rect {
    val remembered = getTag(R.id.systemBarInsetBasePadding) as? Rect
    if (remembered != null) {
        return remembered
    }
    return Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)
        .also { setTag(R.id.systemBarInsetBasePadding, it) }
}
