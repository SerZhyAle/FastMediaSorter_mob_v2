package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.view.View
import android.widget.Adapter
import android.widget.LinearLayout

private const val DEFAULT_MAX_WIDTH_FRACTION = 0.85f
private const val ROW_END_PADDING_DP = 8

/**
 * Pre-measures the widest row an [Adapter] would draw and returns a popup width that fits it,
 * never narrower than [minWidthPx] and capped at [maxWidthFraction] of the screen.
 *
 * S2185: `ListPopupWindow.WRAP_CONTENT` measures adapter rows against a zero-width constraint,
 * which collapses the popup to icon-only width and wraps a single-line label character-by-character
 * on a long translation (uk: "Поділитися" -> "Под/ілит/ися"). Measuring each row explicitly against
 * an UNSPECIFIED spec - the same trick `ListPopupWindow`'s own internal sizing uses - avoids that
 * collapse and lets the popup expand past a narrow anchor field instead of clipping its longest
 * entry mid-word.
 */
fun measurePopupContentWidth(
    context: Context,
    adapter: Adapter,
    minWidthPx: Int = 0,
    maxWidthFraction: Float = DEFAULT_MAX_WIDTH_FRACTION,
): Int {
    val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    val measureParent = LinearLayout(context)
    var convertView: View? = null
    var maxWidth = 0
    for (i in 0 until adapter.count) {
        convertView = adapter.getView(i, convertView, measureParent)
        convertView.measure(widthSpec, heightSpec)
        if (convertView.measuredWidth > maxWidth) maxWidth = convertView.measuredWidth
    }
    val paddingPx = (ROW_END_PADDING_DP * context.resources.displayMetrics.density).toInt()
    val screenWidth = context.resources.displayMetrics.widthPixels
    val cap = (screenWidth * maxWidthFraction).toInt()
    return (maxWidth + paddingPx).coerceAtLeast(minWidthPx).coerceAtMost(cap).coerceAtLeast(1)
}
