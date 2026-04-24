package com.sza.fastmediasorter.ui.common.input

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.view.ViewCompat

/**
 * Applies a visible keyboard focus ring to arbitrary views.
 *
 * The ring is applied as a foreground drawable so it composites over
 * any existing selector background without replacing it. Views that
 * already set their own `foreground` are preserved: the helper only
 * installs its overlay when focused and restores the original on
 * unfocus.
 *
 * The colour is hard-coded to a high-contrast accent so it remains
 * visible across all theme surfaces until a per-theme override lands.
 */
object FocusRingHelper {

    private const val RING_COLOR = 0xFF2196F3.toInt()
    private const val RING_WIDTH_DP = 2
    private const val TAG_KEY = "focus_ring_original_foreground"

    /**
     * Apply the focus ring to [view] when [focused] is true; otherwise
     * remove it and restore the previous foreground.
     */
    fun setFocused(view: View, focused: Boolean) {
        view.isActivated = focused
        // Only pre-Q devices need foreground fallback; modern devices
        // rely on Activated state selectors. We always also apply the
        // explicit ring for consistency across themes.
        val tagId = TAG_KEY.hashCode()
        if (focused) {
            val originalTag = view.getTag(tagId)
            if (originalTag == null) {
                view.setTag(tagId, view.foreground ?: NO_FOREGROUND_MARKER)
            }
            view.foreground = buildRing(view)
            view.elevation = maxOf(view.elevation, 4f)
        } else {
            val restored = view.getTag(tagId)
            if (restored != null) {
                view.foreground = if (restored === NO_FOREGROUND_MARKER) null else restored as Drawable?
                view.setTag(tagId, null)
            } else {
                view.foreground = null
            }
        }
        ViewCompat.postInvalidateOnAnimation(view)
    }

    /**
     * Convenience: enable the focus ring on [view] as a Focus listener.
     * Use from Activities when the view does not flow through
     * [com.sza.fastmediasorter.ui.common.FocusManager].
     */
    fun attach(view: View) {
        val existing = view.onFocusChangeListener
        view.setOnFocusChangeListener { v, hasFocus ->
            setFocused(v, hasFocus)
            existing?.onFocusChange(v, hasFocus)
        }
    }

    private fun buildRing(view: View): Drawable {
        val widthPx = (RING_WIDTH_DP * view.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        return GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(widthPx, RING_COLOR)
            cornerRadius = 4f * view.resources.displayMetrics.density
        }
    }

    private val NO_FOREGROUND_MARKER = Any()
}
