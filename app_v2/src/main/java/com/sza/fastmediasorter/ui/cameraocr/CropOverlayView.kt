package com.sza.fastmediasorter.ui.cameraocr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import kotlin.math.abs
import kotlin.math.min

/**
 * Draws a bright-red, wide-stroke draggable rectangle over the captured photo for the Camera-OCR
 * crop step. The frame is constrained to the rectangle the photo actually occupies inside the view
 * (the `fitCenter` content rect), so it hugs the image rather than the screen edges and the
 * normalized selection it exposes maps 1:1 onto the source bitmap. The area outside the selection is
 * dimmed so the selection is distinguishable without relying on colour alone. Exposes the selection
 * as a normalized [RectF] via [getNormalizedRect] and whether the user moved it via [isFrameTouched]
 * - an untouched frame means "use the whole photo" (no crop). Supports touch drag/resize and D-pad /
 * keyboard adjustment (Strict Rule 17).
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val defaultInsetPx = DEFAULT_INSET_DP * density
    private val minSidePx = MIN_SIDE_DP * density
    private val handleSlopPx = HANDLE_SLOP_DP * density
    private val keyStepPx = KEY_STEP_DP * density

    private val selection = RectF()

    /** Rectangle the `fitCenter` photo occupies inside this view; the frame lives inside it. */
    private val contentRect = RectF()
    private var imageWidth = 0
    private var imageHeight = 0

    private var initialized = false
    private var touched = false
    private var keyResizeMode = false

    private var activeHandle = Handle.NONE
    private var lastX = 0f
    private var lastY = 0f

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_DP * density
        color = ContextCompat.getColor(context, R.color.crop_frame_red)
    }

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(SCRIM_ALPHA, 0, 0, 0)
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = context.getString(R.string.camera_ocr_crop_frame_desc)
    }

    /**
     * Binds the overlay to a freshly shown photo of [imageWidth] x [imageHeight] pixels and resets
     * the frame to the default near-full position. Call this whenever a new preview bitmap is set.
     * The content rect and frame are (re)computed immediately when the view already has bounds, and
     * otherwise lazily once [onSizeChanged] / [onDraw] sees a valid size.
     */
    fun setImageSize(imageWidth: Int, imageHeight: Int) {
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        initialized = false
        touched = false
        keyResizeMode = false
        recomputeContentRect()
        if (!contentRect.isEmpty) {
            applyDefaultFrame()
        }
        // Always invalidate: forces a redraw even when bounds are not ready yet, so a re-shown
        // crop step never reuses an empty hardware render node (the "frame never appears" bug).
        invalidate()
    }

    /** Selection normalized to the photo content rect (left/top/right/bottom in [0f, 1f]). */
    fun getNormalizedRect(): RectF {
        if (contentRect.isEmpty) return RectF(0f, 0f, 1f, 1f)
        val w = contentRect.width()
        val h = contentRect.height()
        return RectF(
            ((selection.left - contentRect.left) / w).coerceIn(0f, 1f),
            ((selection.top - contentRect.top) / h).coerceIn(0f, 1f),
            ((selection.right - contentRect.left) / w).coerceIn(0f, 1f),
            ((selection.bottom - contentRect.top) / h).coerceIn(0f, 1f)
        )
    }

    /** True once the user moved or resized the frame; false while it sits at the default. */
    fun isFrameTouched(): Boolean = touched

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeContentRect()
        if (!initialized && !contentRect.isEmpty) {
            applyDefaultFrame()
        }
        invalidate()
    }

    private fun recomputeContentRect() {
        if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) {
            contentRect.setEmpty()
            return
        }
        // fitCenter: scale the image down/up to fit, centred, preserving aspect ratio.
        val scale = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val drawnW = imageWidth * scale
        val drawnH = imageHeight * scale
        val left = (width - drawnW) / 2f
        val top = (height - drawnH) / 2f
        contentRect.set(left, top, left + drawnW, top + drawnH)
    }

    private fun applyDefaultFrame() {
        if (contentRect.isEmpty) return
        selection.set(
            contentRect.left + defaultInsetPx,
            contentRect.top + defaultInsetPx,
            contentRect.right - defaultInsetPx,
            contentRect.bottom - defaultInsetPx
        )
        // Tiny content rect: the inset could invert the frame - fall back to the full content rect.
        if (selection.right - selection.left < minSidePx || selection.bottom - selection.top < minSidePx) {
            selection.set(contentRect)
        }
        initialized = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Lazy init: reset()/setImageSize may run before layout (size 0); compute the frame the
        // first time the view is actually drawn with valid bounds so it can never stay invisible.
        if (!initialized) {
            recomputeContentRect()
            if (contentRect.isEmpty) return
            applyDefaultFrame()
        }

        // Dim everything outside the selection (four bands across the whole view).
        canvas.drawRect(0f, 0f, width.toFloat(), selection.top, scrimPaint)
        canvas.drawRect(0f, selection.bottom, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawRect(0f, selection.top, selection.left, selection.bottom, scrimPaint)
        canvas.drawRect(selection.right, selection.top, width.toFloat(), selection.bottom, scrimPaint)

        canvas.drawRect(selection, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!initialized) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = handleAt(event.x, event.y)
                if (activeHandle == Handle.NONE) return false
                lastX = event.x
                lastY = event.y
                isPressed = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle == Handle.NONE) return false
                val dx = event.x - lastX
                val dy = event.y - lastY
                applyDrag(activeHandle, dx, dy)
                lastX = event.x
                lastY = event.y
                markTouched()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeHandle = Handle.NONE
                isPressed = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!initialized) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                keyResizeMode = !keyResizeMode
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> { nudge(-keyStepPx, 0f); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { nudge(keyStepPx, 0f); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { nudge(0f, -keyStepPx); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { nudge(0f, keyStepPx); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun nudge(dx: Float, dy: Float) {
        // In resize mode arrows move the bottom-right corner; otherwise the whole frame moves.
        applyDrag(if (keyResizeMode) Handle.BOTTOM_RIGHT else Handle.INSIDE, dx, dy)
        markTouched()
        invalidate()
    }

    private fun markTouched() {
        touched = true
    }

    private fun handleAt(x: Float, y: Float): Handle {
        val nearLeft = abs(x - selection.left) <= handleSlopPx
        val nearRight = abs(x - selection.right) <= handleSlopPx
        val nearTop = abs(y - selection.top) <= handleSlopPx
        val nearBottom = abs(y - selection.bottom) <= handleSlopPx
        val insideX = x in (selection.left - handleSlopPx)..(selection.right + handleSlopPx)
        val insideY = y in (selection.top - handleSlopPx)..(selection.bottom + handleSlopPx)
        if (!insideX || !insideY) return Handle.NONE

        return when {
            nearLeft && nearTop -> Handle.TOP_LEFT
            nearRight && nearTop -> Handle.TOP_RIGHT
            nearLeft && nearBottom -> Handle.BOTTOM_LEFT
            nearRight && nearBottom -> Handle.BOTTOM_RIGHT
            nearLeft -> Handle.LEFT
            nearRight -> Handle.RIGHT
            nearTop -> Handle.TOP
            nearBottom -> Handle.BOTTOM
            selection.contains(x, y) -> Handle.INSIDE
            else -> Handle.NONE
        }
    }

    private fun applyDrag(handle: Handle, dx: Float, dy: Float) {
        when (handle) {
            Handle.INSIDE -> moveBy(dx, dy)
            Handle.LEFT -> selection.left = clampLeft(selection.left + dx)
            Handle.RIGHT -> selection.right = clampRight(selection.right + dx)
            Handle.TOP -> selection.top = clampTop(selection.top + dy)
            Handle.BOTTOM -> selection.bottom = clampBottom(selection.bottom + dy)
            Handle.TOP_LEFT -> {
                selection.left = clampLeft(selection.left + dx)
                selection.top = clampTop(selection.top + dy)
            }
            Handle.TOP_RIGHT -> {
                selection.right = clampRight(selection.right + dx)
                selection.top = clampTop(selection.top + dy)
            }
            Handle.BOTTOM_LEFT -> {
                selection.left = clampLeft(selection.left + dx)
                selection.bottom = clampBottom(selection.bottom + dy)
            }
            Handle.BOTTOM_RIGHT -> {
                selection.right = clampRight(selection.right + dx)
                selection.bottom = clampBottom(selection.bottom + dy)
            }
            Handle.NONE -> Unit
        }
    }

    private fun moveBy(dx: Float, dy: Float) {
        var nx = dx
        var ny = dy
        if (selection.left + nx < contentRect.left) nx = contentRect.left - selection.left
        if (selection.right + nx > contentRect.right) nx = contentRect.right - selection.right
        if (selection.top + ny < contentRect.top) ny = contentRect.top - selection.top
        if (selection.bottom + ny > contentRect.bottom) ny = contentRect.bottom - selection.bottom
        selection.offset(nx, ny)
    }

    private fun clampLeft(value: Float): Float =
        value.coerceIn(contentRect.left, selection.right - minSidePx)

    private fun clampRight(value: Float): Float =
        value.coerceIn(selection.left + minSidePx, contentRect.right)

    private fun clampTop(value: Float): Float =
        value.coerceIn(contentRect.top, selection.bottom - minSidePx)

    private fun clampBottom(value: Float): Float =
        value.coerceIn(selection.top + minSidePx, contentRect.bottom)

    private enum class Handle {
        NONE, INSIDE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    companion object {
        private const val DEFAULT_INSET_DP = 12f
        private const val MIN_SIDE_DP = 48f
        private const val HANDLE_SLOP_DP = 24f
        private const val KEY_STEP_DP = 16f
        private const val STROKE_DP = 6f
        private const val SCRIM_ALPHA = 102 // ~40%
    }
}
