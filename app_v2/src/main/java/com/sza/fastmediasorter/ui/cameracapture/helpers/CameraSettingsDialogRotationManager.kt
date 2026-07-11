package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.app.Dialog
import android.content.Context
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sza.fastmediasorter.R

/**
 * S0924: visually rotates the camera settings dialog content with physical device rotation while the
 * host [com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity] stays portrait-locked (S0754).
 *
 * The camera Activity never changes `Configuration`, so a `layout-land/` resource can never resolve.
 * Instead this manager owns a rotate-and-swap-measure container: it applies `View.rotation` to the
 * dialog content root (pivoting at centre) and, for the two landscape buckets, swaps the child's
 * measure axes so the perpendicular content measures correctly, then reshapes the floating dialog
 * window via [Dialog.getWindow] `setLayout(w, h)`. All rotation math lives here, not in the Fragment
 * (Rule 3/6).
 *
 * Known limitation (strategic §11.4): dropdown suggestion popups (`AutoCompleteTextView`) and
 * `TooltipDialog` render as separate top-level windows and do NOT inherit this rotation.
 */
class CameraSettingsDialogRotationManager(context: Context) {

    private val container = RotatingContentContainer(context)
    private var dialog: Dialog? = null
    private var appliedBucket = -1

    /** Wrap [contentRoot] in the rotate-and-swap-measure container; pass the result to `setView`. */
    fun wrap(contentRoot: View): View {
        (contentRoot.parent as? ViewGroup)?.removeView(contentRoot)
        container.removeAllViews()
        container.addView(
            contentRoot,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        return container
    }

    /** Bind the created dialog's window; call once from `onCreateDialog` after `create()`. */
    fun attach(dialog: Dialog) {
        this.dialog = dialog
    }

    /**
     * Rotate the content to keep it upright for the given `Surface.ROTATION_*` bucket and reshape the
     * dialog window to the post-rotation bounds. Idempotent: re-applying the same bucket is a no-op.
     */
    fun applyRotation(bucket: Int) {
        if (bucket == appliedBucket) return
        appliedBucket = bucket
        val degrees = when (bucket) {
            Surface.ROTATION_90 -> ROTATE_90
            Surface.ROTATION_180 -> ROTATE_180
            Surface.ROTATION_270 -> ROTATE_270
            else -> ROTATE_0
        }
        container.setContentRotation(degrees)
        reshapeWindow(landscape = bucket == Surface.ROTATION_90 || bucket == Surface.ROTATION_270)
    }

    /**
     * Size the floating window to the rotated content. Portrait: match-width, wrap-height (default
     * AlertDialog behaviour). Landscape: wide-and-short, capped by [R.dimen.dialog_landscape_max_height]
     * on the post-rotation vertical axis and inset from system bars / display cutout.
     */
    private fun reshapeWindow(landscape: Boolean) {
        val window = dialog?.window ?: return
        val res = container.resources
        if (!landscape) {
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
            container.requestLayout()
            return
        }
        val metrics = res.displayMetrics
        val safe = safeInsets(window.decorView)
        val availableWidth = metrics.widthPixels - safe.first
        val maxHeight = res.getDimensionPixelSize(R.dimen.dialog_landscape_max_height)
        val availableHeight = (metrics.heightPixels - safe.second).coerceAtMost(maxHeight)
        window.setLayout(availableWidth, availableHeight)
        container.requestLayout()
    }

    /** Total horizontal / vertical inset (both edges) of system bars + display cutout, in px. */
    private fun safeInsets(decor: View): Pair<Int, Int> {
        val insets = ViewCompat.getRootWindowInsets(decor)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            ?: return WINDOW_EDGE_MARGIN_PX * 2 to WINDOW_EDGE_MARGIN_PX * 2
        return (insets.left + insets.right + WINDOW_EDGE_MARGIN_PX * 2) to
            (insets.top + insets.bottom + WINDOW_EDGE_MARGIN_PX * 2)
    }

    /**
     * Single-child container that reports swapped measured dimensions for the two landscape buckets so
     * the perpendicular (rotated) child measures against the opposite axis, then centres and pivots it.
     */
    private class RotatingContentContainer(context: Context) : ViewGroup(context) {

        private var angle = ROTATE_0

        fun setContentRotation(degrees: Float) {
            if (degrees == angle) return
            angle = degrees
            getChildAt(0)?.rotation = degrees
            requestLayout()
        }

        private fun isQuarterTurn(): Boolean = angle == ROTATE_90 || angle == ROTATE_270

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val child = getChildAt(0)
            if (child == null) {
                setMeasuredDimension(0, 0)
                return
            }
            if (isQuarterTurn()) {
                // Rotated 90/270: the child (portrait) measures against the swapped axes so its natural
                // width becomes the window's vertical extent and vice versa.
                measureChild(child, heightMeasureSpec, widthMeasureSpec)
                setMeasuredDimension(child.measuredHeight, child.measuredWidth)
            } else {
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
                setMeasuredDimension(child.measuredWidth, child.measuredHeight)
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val child = getChildAt(0) ?: return
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight
            val left = (measuredWidth - childWidth) / 2
            val top = (measuredHeight - childHeight) / 2
            child.layout(left, top, left + childWidth, top + childHeight)
            child.pivotX = childWidth / 2f
            child.pivotY = childHeight / 2f
            child.rotation = angle
        }
    }

    private companion object {
        const val ROTATE_0 = 0f
        const val ROTATE_90 = 90f
        const val ROTATE_180 = 180f

        // ROTATION_270 keeps content upright with a -90 deg turn (mirrors the overlay icon compensation).
        const val ROTATE_270 = -90f

        // Extra breathing room beyond system insets so the rotated window never touches the screen edge.
        const val WINDOW_EDGE_MARGIN_PX = 24
    }
}
