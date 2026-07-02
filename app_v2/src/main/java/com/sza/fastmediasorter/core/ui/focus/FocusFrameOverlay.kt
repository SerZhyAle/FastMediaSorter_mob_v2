package com.sza.fastmediasorter.core.ui.focus

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R

/**
 * S0819: single animated rounded-rect accent border for the travelling D-pad/TV focus frame.
 *
 * This is a [Drawable] (not a View) so it can live in `window.decorView.overlay`, draw on top of any
 * layout, and never intercept touch. Styling resolves from the host theme: `focusFrameColor`
 * (accent per theme via `?attr/colorPrimary`, falling back to `@color/focus_frame_fallback`),
 * `focusFrameStrokeWidth`, `focusFrameCornerRadius`. The animation duration comes from
 * `@integer/focus_frame_anim_ms`.
 *
 * Draw-path allocation-free: one reusable [Paint], one reusable [RectF] for the drawn bounds, and a
 * single reusable [ValueAnimator] that lerps the bounds from the previous target to the new one.
 */
class FocusFrameOverlay(context: Context) : Drawable() {

    @ColorInt
    private val frameColor: Int = resolveFrameColor(context)
    private val strokeWidthPx: Float =
        resolveDimension(context, R.attr.focusFrameStrokeWidth)
    private val cornerRadiusPx: Float =
        resolveDimension(context, R.attr.focusFrameCornerRadius)
    private val animDurationMs: Long =
        context.resources.getInteger(R.integer.focus_frame_anim_ms).toLong()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = frameColor
        strokeWidth = strokeWidthPx
    }

    // Currently drawn frame edges. Empty (visible=false) means nothing is painted.
    private val drawnBounds = RectF()

    // Interpolation endpoints reused across every moveTo() so animation never allocates.
    private val fromBounds = RectF()
    private val toBounds = RectF()

    // Last requested destination. Guards against the per-frame pre-draw re-sync restarting the
    // animator every frame while focus is static, which would spin main-thread redraws forever.
    private val currentTarget = Rect()

    private var visible = false

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = animDurationMs
        addUpdateListener { anim ->
            val fraction = anim.animatedValue as Float
            drawnBounds.set(
                lerp(fromBounds.left, toBounds.left, fraction),
                lerp(fromBounds.top, toBounds.top, fraction),
                lerp(fromBounds.right, toBounds.right, fraction),
                lerp(fromBounds.bottom, toBounds.bottom, fraction),
            )
            invalidateSelf()
        }
    }

    /** Animate the frame from its current position to [target] (the focused view's screen bounds). */
    fun moveTo(target: Rect) {
        // Already at (or animating toward) this exact target: no-op, so the pre-draw re-sync does not
        // relaunch the animator every frame.
        if (visible && currentTarget == target) return
        currentTarget.set(target)
        animator.cancel()
        // First appearance jumps straight to the target so the frame does not fly in from an empty rect.
        if (!visible || drawnBounds.isEmpty) {
            drawnBounds.set(target)
            visible = true
            invalidateSelf()
            return
        }
        fromBounds.set(drawnBounds)
        toBounds.set(target)
        visible = true
        animator.start()
    }

    /** Stop drawing the frame (e.g. entering touch mode or losing focus). */
    fun clear() {
        animator.cancel()
        visible = false
        drawnBounds.setEmpty()
        currentTarget.setEmpty()
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (!visible || drawnBounds.isEmpty) return
        val inset = strokeWidthPx / 2f
        canvas.drawRoundRect(
            drawnBounds.left + inset,
            drawnBounds.top + inset,
            drawnBounds.right - inset,
            drawnBounds.bottom - inset,
            cornerRadiusPx,
            cornerRadiusPx,
            paint,
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION") // Drawable.getOpacity is deprecated (API 29) but still required.
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        fun lerp(start: Float, end: Float, fraction: Float): Float =
            start + (end - start) * fraction

        @ColorInt
        fun resolveFrameColor(context: Context): Int {
            val typed = context.theme.obtainStyledAttributes(intArrayOf(R.attr.focusFrameColor))
            try {
                val fallback = ContextCompat.getColor(context, R.color.focus_frame_fallback)
                return typed.getColor(0, fallback)
            } finally {
                typed.recycle()
            }
        }

        fun resolveDimension(context: Context, attr: Int): Float {
            val typed = context.theme.obtainStyledAttributes(intArrayOf(attr))
            try {
                return typed.getDimension(0, 0f)
            } finally {
                typed.recycle()
            }
        }
    }
}
