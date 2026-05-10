package com.sza.fastmediasorter.ui.player.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import timber.log.Timber

/**
 * Full-screen canvas-based custom View that shows a draggable crop rectangle
 * with corner and edge handles over a semi-transparent dark scrim.
 *
 * The area inside the crop rect is cut out (xfermode CLEAR) to reveal the
 * image underneath. Handles are rendered with the theme's colorPrimary.
 *
 * Usage:
 *   1. Inflate via `player_crop_overlay_content.xml`.
 *   2. Read the result via `getCropRectNormalized()` on confirm.
 *   3. Optionally observe live changes via `cropChangedListener`.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    interface OnCropChangedListener {
        fun onCropChanged(normalised: RectF)
    }

    var cropChangedListener: OnCropChangedListener? = null

    var pinchPassthroughTarget: View? = null

    // ── Dimensions ──────────────────────────────────────────────────────────

    private val cornerHandleRadiusPx = dp(12f)
    private val edgeHandleHalfPx = dp(8f)
    private val minCropPx = dp(40f)

    // ── Paints ──────────────────────────────────────────────────────────────

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 0, 0, 0)
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // color is set lazily in onAttachedToWindow once theme is available
        color = Color.WHITE
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.WHITE
    }

    // ── Crop rect (view coordinates) ────────────────────────────────────────

    private val cropRect = RectF()
    private var initialised = false

    // ── Touch state ─────────────────────────────────────────────────────────

    private enum class DragTarget {
        NONE,
        MOVE,
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        TOP, BOTTOM, LEFT, RIGHT
    }

    private var dragTarget = DragTarget.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val tv = TypedValue()
        if (context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true)) {
            handlePaint.color = tv.data
        }
    }

    // ── Layout ──────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialised && w > 0 && h > 0) {
            val margin = 0.1f
            cropRect.set(w * margin, h * margin, w * (1f - margin), h * (1f - margin))
            initialised = true
        }
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // ── Drawing ─────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Scrim over the whole view
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        // Cut out the crop rect so the image shows through
        canvas.drawRect(cropRect, clearPaint)
        // Border around crop rect
        canvas.drawRect(cropRect, borderPaint)
        // Corner handles
        drawCornerHandle(canvas, cropRect.left, cropRect.top)
        drawCornerHandle(canvas, cropRect.right, cropRect.top)
        drawCornerHandle(canvas, cropRect.left, cropRect.bottom)
        drawCornerHandle(canvas, cropRect.right, cropRect.bottom)
        // Edge midpoint handles
        drawEdgeHandle(canvas, (cropRect.left + cropRect.right) / 2f, cropRect.top)
        drawEdgeHandle(canvas, (cropRect.left + cropRect.right) / 2f, cropRect.bottom)
        drawEdgeHandle(canvas, cropRect.left, (cropRect.top + cropRect.bottom) / 2f)
        drawEdgeHandle(canvas, cropRect.right, (cropRect.top + cropRect.bottom) / 2f)
    }

    private fun drawCornerHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, cornerHandleRadiusPx, handlePaint)
    }

    private fun drawEdgeHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawRect(
            cx - edgeHandleHalfPx, cy - edgeHandleHalfPx,
            cx + edgeHandleHalfPx, cy + edgeHandleHalfPx,
            handlePaint
        )
    }

    // ── Touch ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            dragTarget = DragTarget.NONE
            pinchPassthroughTarget?.dispatchTouchEvent(event)
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragTarget = hitTest(event.x, event.y)
                lastTouchX = event.x
                lastTouchY = event.y
                return dragTarget != DragTarget.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragTarget == DragTarget.NONE) return false
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                applyDrag(dx, dy)
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
                notifyListener()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragTarget = DragTarget.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitTest(x: Float, y: Float): DragTarget {
        val r = cornerHandleRadiusPx + dp(8f) // hit slop
        if (dist(x, y, cropRect.left, cropRect.top) <= r) return DragTarget.TOP_LEFT
        if (dist(x, y, cropRect.right, cropRect.top) <= r) return DragTarget.TOP_RIGHT
        if (dist(x, y, cropRect.left, cropRect.bottom) <= r) return DragTarget.BOTTOM_LEFT
        if (dist(x, y, cropRect.right, cropRect.bottom) <= r) return DragTarget.BOTTOM_RIGHT

        val mxH = (cropRect.left + cropRect.right) / 2f
        val myV = (cropRect.top + cropRect.bottom) / 2f
        val es = edgeHandleHalfPx + dp(8f)
        if (Math.abs(x - mxH) <= es && Math.abs(y - cropRect.top) <= es) return DragTarget.TOP
        if (Math.abs(x - mxH) <= es && Math.abs(y - cropRect.bottom) <= es) return DragTarget.BOTTOM
        if (Math.abs(x - cropRect.left) <= es && Math.abs(y - myV) <= es) return DragTarget.LEFT
        if (Math.abs(x - cropRect.right) <= es && Math.abs(y - myV) <= es) return DragTarget.RIGHT

        if (cropRect.contains(x, y)) return DragTarget.MOVE
        return DragTarget.NONE
    }

    private fun applyDrag(dx: Float, dy: Float) {
        val vw = width.toFloat()
        val vh = height.toFloat()
        when (dragTarget) {
            DragTarget.MOVE -> {
                val newL = (cropRect.left + dx).coerceIn(0f, vw - cropRect.width())
                val newT = (cropRect.top + dy).coerceIn(0f, vh - cropRect.height())
                cropRect.offsetTo(newL, newT)
            }
            DragTarget.TOP_LEFT -> {
                cropRect.left = (cropRect.left + dx).coerceIn(0f, cropRect.right - minCropPx)
                cropRect.top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minCropPx)
            }
            DragTarget.TOP_RIGHT -> {
                cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minCropPx, vw)
                cropRect.top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minCropPx)
            }
            DragTarget.BOTTOM_LEFT -> {
                cropRect.left = (cropRect.left + dx).coerceIn(0f, cropRect.right - minCropPx)
                cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropPx, vh)
            }
            DragTarget.BOTTOM_RIGHT -> {
                cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minCropPx, vw)
                cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropPx, vh)
            }
            DragTarget.TOP -> cropRect.top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minCropPx)
            DragTarget.BOTTOM -> cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropPx, vh)
            DragTarget.LEFT -> cropRect.left = (cropRect.left + dx).coerceIn(0f, cropRect.right - minCropPx)
            DragTarget.RIGHT -> cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minCropPx, vw)
            else -> {}
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns the crop rect in 0..1 normalised view coordinates.
     */
    fun getCropRectNormalized(): RectF {
        if (width == 0 || height == 0) return RectF(0.1f, 0.1f, 0.9f, 0.9f)
        return RectF(
            cropRect.left / width,
            cropRect.top / height,
            cropRect.right / width,
            cropRect.bottom / height
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun notifyListener() {
        cropChangedListener?.onCropChanged(getCropRectNormalized())
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

}
