package com.sza.fastmediasorter.ui.common.help

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.common.input.UiSurface
import kotlin.math.roundToInt

/** Owns the help entry of a headerless surface without intercepting its surrounding gestures. */
class HelpOverlayEntryManager(
    activity: FragmentActivity,
    private val container: ViewGroup,
    surfaceId: String,
    uiSurface: UiSurface?,
    private val follow: View? = null,
    private val reserveSpaceFor: View? = null,
) {
    private val overlay = FrameLayout(activity)
    private val button = AppCompatImageButton(activity)
    private val location = IntArray(2)
    private val size = dp(TOUCH_TARGET_DP)
    private val margin = dp(MARGIN_DP)
    private val originalTopPadding = reserveSpaceFor?.paddingTop ?: 0
    private var lastOpenAt = 0L
    private var released = false
    private val followListener = ViewTreeObserver.OnPreDrawListener {
        button.alpha = follow?.alpha ?: FULL_ALPHA
        button.visibility = if (follow?.isShown != false && button.alpha > 0f) View.VISIBLE else View.INVISIBLE
        true
    }

    // Insets may arrive before the overlay has a window position, and rotation changes that position.
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        ViewCompat.requestApplyInsets(overlay)
    }

    init {
        if (HelpOpenManager.hasHelp(activity, surfaceId)) {
            button.apply {
                id = View.generateViewId()
                setImageResource(R.drawable.ic_help_outline)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                contentDescription = activity.getString(R.string.help)
                tooltipText = contentDescription
                isFocusable = true
                isClickable = true
                setPadding(dp(ICON_PADDING_DP), dp(ICON_PADDING_DP), dp(ICON_PADDING_DP), dp(ICON_PADDING_DP))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(BACKGROUND_ALPHA, 0, 0, 0))
                }
                setOnClickListener {
                    val now = SystemClock.uptimeMillis()
                    if (lastOpenAt == 0L || now - lastOpenAt >= DOUBLE_TAP_WINDOW_MS) {
                        lastOpenAt = now
                        HelpOpenManager.open(activity, surfaceId, uiSurface)
                    }
                }
            }
            overlay.addView(button, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END))
            container.addView(
                overlay,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            val next = container.getFocusables(View.FOCUS_FORWARD)
                .firstOrNull { it !== button && it.id != View.NO_ID && it.isClickable }
            button.nextFocusForwardId = next?.id ?: button.id
            button.nextFocusDownId = button.nextFocusForwardId
            button.nextFocusLeftId = button.nextFocusForwardId
            button.nextFocusRightId = button.nextFocusForwardId
            reserveSpaceFor?.setPadding(
                reserveSpaceFor.paddingLeft,
                originalTopPadding + size + margin,
                reserveSpaceFor.paddingRight,
                reserveSpaceFor.paddingBottom,
            )
            ViewCompat.setOnApplyWindowInsetsListener(overlay) { _, insets ->
                val safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                overlay.getLocationInWindow(location)
                val rightSpace = overlay.rootView.width - location[0] - overlay.width
                val params = button.layoutParams as FrameLayout.LayoutParams
                params.topMargin = margin + (safe.top - location[1]).coerceAtLeast(0)
                params.leftMargin = margin + (safe.left - location[0]).coerceAtLeast(0)
                params.rightMargin = margin + (safe.right - rightSpace).coerceAtLeast(0)
                button.layoutParams = params
                insets
            }
            overlay.addOnLayoutChangeListener(layoutListener)
            if (follow != null) overlay.viewTreeObserver.addOnPreDrawListener(followListener)
            ViewCompat.requestApplyInsets(overlay)
        }
    }

    fun release() {
        if (released) return
        released = true
        if (overlay.viewTreeObserver.isAlive) {
            overlay.viewTreeObserver.removeOnPreDrawListener(followListener)
        }
        overlay.removeOnLayoutChangeListener(layoutListener)
        ViewCompat.setOnApplyWindowInsetsListener(overlay, null)
        button.setOnClickListener(null)
        container.removeView(overlay)
        reserveSpaceFor?.setPadding(
            reserveSpaceFor.paddingLeft,
            originalTopPadding,
            reserveSpaceFor.paddingRight,
            reserveSpaceFor.paddingBottom,
        )
    }

    private fun dp(value: Int): Int = (value * container.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TOUCH_TARGET_DP = 48
        const val MARGIN_DP = 8
        const val ICON_PADDING_DP = 12
        const val BACKGROUND_ALPHA = 176
        const val FULL_ALPHA = 1f
        const val DOUBLE_TAP_WINDOW_MS = 600L
    }
}
