package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.sza.fastmediasorter.R
import kotlin.math.abs
import kotlin.math.min

/**
 * The one draggable crop frame both crop surfaces render: the camera-OCR crop step and the player
 * file-crop overlay. `cfvScrimMode` picks the surface preset - [ScrimMode.BANDS] dims the four strips
 * around the frame and hugs the `fitCenter` content rect of a bound image, [ScrimMode.CUTOUT] scrims
 * the whole view and punches the frame out of it with handle chrome on top.
 *
 * Touch and D-pad behavior is shared: an edge or corner grab band resizes, anywhere inside moves,
 * D-pad center toggles resize mode and the arrows nudge by [KEY_STEP_DP].
 *
 * The grab band of an edge sitting at the view border is pushed inwards (see [grabBandCenter]), or
 * half of it lands off-screen where no touch can reach it (S1602).
 *
 * The frame is (re)computed lazily on the first draw with valid bounds as well as in [onSizeChanged],
 * so a re-shown crop step can never reuse an empty render node - the "frame never appears" bug.
 */
class CropFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Reports every live change of the frame; the player crop preview follows it. */
    interface OnCropChangedListener {
        fun onCropChanged(normalised: RectF)
    }

    private enum class ScrimMode { BANDS, CUTOUT }

    private enum class Handle {
        NONE, INSIDE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    var cropChangedListener: OnCropChangedListener? = null

    /** Receives multi-touch events so a pinch zooms the media underneath instead of moving the frame. */
    var pinchPassthroughTarget: View? = null

    private val scrimMode: ScrimMode = readScrimMode(attrs, defStyleAttr)

    private val density = resources.displayMetrics.density
    private val minSidePx = (if (scrimMode == ScrimMode.BANDS) MIN_SIDE_BANDS_DP else MIN_SIDE_CUTOUT_DP) * density
    private val handleSlopPx = HANDLE_SLOP_DP * density
    private val keyStepPx = KEY_STEP_DP * density
    private val defaultInsetPx = DEFAULT_INSET_DP * density
    private val cornerHandleRadiusPx = CORNER_HANDLE_RADIUS_DP * density
    private val edgeHandleHalfPx = EDGE_HANDLE_HALF_DP * density

    private val selection = RectF()

    /** Domain the frame lives in: the content rect of a bound image, or the whole view without one. */
    private val contentRect = RectF()
    private var imageWidth = 0
    private var imageHeight = 0

    private var initialized = false
    private var touched = false
    private var keyResizeMode = false

    private var activeHandle = Handle.NONE
    private var lastX = 0f
    private var lastY = 0f

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (scrimMode == ScrimMode.BANDS) {
            Color.argb(SCRIM_ALPHA_BANDS, 0, 0, 0)
        } else {
            Color.argb(SCRIM_ALPHA_CUTOUT, 0, 0, 0)
        }
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // Replaced with the theme colorPrimary once the view is attached and the theme is available.
        color = Color.WHITE
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        if (scrimMode == ScrimMode.BANDS) {
            strokeWidth = BORDER_STROKE_BANDS_DP * density
            color = ContextCompat.getColor(context, R.color.crop_frame_red)
            // 25% translucent so the thick red edge does not fully mask the text line it sits on.
            alpha = BORDER_ALPHA_BANDS
        } else {
            strokeWidth = BORDER_STROKE_CUTOUT_DP * density
            color = Color.WHITE
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * Binds the frame to a freshly shown image of [imageWidth] x [imageHeight] pixels and resets it to
     * the default near-full position. Without this call the frame spans the whole view.
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
        invalidate()
    }

    /** Selection normalized to the image content rect (left/top/right/bottom in [0f, 1f]). */
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

    /** Frame normalized to the view itself (left/top/right/bottom in [0f, 1f]). */
    fun getCropRectNormalized(): RectF {
        if (width == 0 || height == 0) {
            return RectF(CUTOUT_MARGIN, CUTOUT_MARGIN, 1f - CUTOUT_MARGIN, 1f - CUTOUT_MARGIN)
        }
        return RectF(
            selection.left / width,
            selection.top / height,
            selection.right / width,
            selection.bottom / height
        )
    }

    /** True once the user moved or resized the frame; false while it sits at the default. */
    fun isFrameTouched(): Boolean = touched

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val themeValue = TypedValue()
        if (context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, themeValue, true)) {
            handlePaint.color = themeValue.data
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeContentRect()
        if (!initialized && !contentRect.isEmpty) {
            applyDefaultFrame()
        }
        if (scrimMode == ScrimMode.CUTOUT) {
            // The CLEAR xfermode needs its own layer; without it the cutout paints black.
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!initialized) {
            recomputeContentRect()
            if (contentRect.isEmpty) return
            applyDefaultFrame()
        }
        if (scrimMode == ScrimMode.BANDS) {
            drawBands(canvas)
        } else {
            drawCutout(canvas)
        }
        canvas.drawRect(selection, borderPaint)
        if (scrimMode == ScrimMode.CUTOUT) {
            drawHandles(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2 || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            activeHandle = Handle.NONE
            pinchPassthroughTarget?.dispatchTouchEvent(event)
            return false
        }
        return if (!initialized) {
            false
        } else {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> grabHandle(event)
                MotionEvent.ACTION_MOVE -> dragHandle(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activeHandle = Handle.NONE
                    isPressed = false
                    true
                }
                else -> super.onTouchEvent(event)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handled = initialized && when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                keyResizeMode = !keyResizeMode
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                nudge(-keyStepPx, 0f)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                nudge(keyStepPx, 0f)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                nudge(0f, -keyStepPx)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                nudge(0f, keyStepPx)
                true
            }
            else -> false
        }
        return handled || super.onKeyDown(keyCode, event)
    }

    private fun grabHandle(event: MotionEvent): Boolean {
        activeHandle = handleAt(event.x, event.y)
        if (activeHandle == Handle.NONE) return false
        lastX = event.x
        lastY = event.y
        isPressed = true
        return true
    }

    private fun dragHandle(event: MotionEvent): Boolean {
        if (activeHandle == Handle.NONE) return false
        applyDrag(activeHandle, event.x - lastX, event.y - lastY)
        lastX = event.x
        lastY = event.y
        touched = true
        invalidate()
        notifyListener()
        return true
    }

    private fun readScrimMode(attrs: AttributeSet?, defStyleAttr: Int): ScrimMode {
        var raw = 0
        context.obtainStyledAttributes(attrs, R.styleable.CropFrameView, defStyleAttr, 0).use { typedArray ->
            raw = typedArray.getInt(R.styleable.CropFrameView_cfvScrimMode, 0)
        }
        return if (raw == 1) ScrimMode.CUTOUT else ScrimMode.BANDS
    }

    private fun drawBands(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), selection.top, scrimPaint)
        canvas.drawRect(0f, selection.bottom, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawRect(0f, selection.top, selection.left, selection.bottom, scrimPaint)
        canvas.drawRect(selection.right, selection.top, width.toFloat(), selection.bottom, scrimPaint)
    }

    private fun drawCutout(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawRect(selection, clearPaint)
    }

    private fun drawHandles(canvas: Canvas) {
        canvas.drawCircle(selection.left, selection.top, cornerHandleRadiusPx, handlePaint)
        canvas.drawCircle(selection.right, selection.top, cornerHandleRadiusPx, handlePaint)
        canvas.drawCircle(selection.left, selection.bottom, cornerHandleRadiusPx, handlePaint)
        canvas.drawCircle(selection.right, selection.bottom, cornerHandleRadiusPx, handlePaint)
        val midX = (selection.left + selection.right) / 2f
        val midY = (selection.top + selection.bottom) / 2f
        drawEdgeHandle(canvas, midX, selection.top)
        drawEdgeHandle(canvas, midX, selection.bottom)
        drawEdgeHandle(canvas, selection.left, midY)
        drawEdgeHandle(canvas, selection.right, midY)
    }

    private fun drawEdgeHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawRect(
            cx - edgeHandleHalfPx,
            cy - edgeHandleHalfPx,
            cx + edgeHandleHalfPx,
            cy + edgeHandleHalfPx,
            handlePaint
        )
    }

    private fun recomputeContentRect() {
        if (width <= 0 || height <= 0) {
            contentRect.setEmpty()
            return
        }
        if (imageWidth <= 0 || imageHeight <= 0) {
            contentRect.set(0f, 0f, width.toFloat(), height.toFloat())
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
        if (scrimMode == ScrimMode.BANDS) {
            selection.set(
                contentRect.left + defaultInsetPx,
                contentRect.top + defaultInsetPx,
                contentRect.right - defaultInsetPx,
                contentRect.bottom - defaultInsetPx
            )
        } else {
            val marginX = contentRect.width() * CUTOUT_MARGIN
            val marginY = contentRect.height() * CUTOUT_MARGIN
            selection.set(
                contentRect.left + marginX,
                contentRect.top + marginY,
                contentRect.right - marginX,
                contentRect.bottom - marginY
            )
        }
        // Tiny domain: the inset could invert the frame - fall back to the whole domain.
        if (selection.width() < minSidePx || selection.height() < minSidePx) {
            selection.set(contentRect)
        }
        initialized = true
    }

    private fun nudge(dx: Float, dy: Float) {
        // In resize mode arrows move the bottom-right corner; otherwise the whole frame moves.
        applyDrag(if (keyResizeMode) Handle.BOTTOM_RIGHT else Handle.INSIDE, dx, dy)
        touched = true
        invalidate()
        notifyListener()
    }

    private fun notifyListener() {
        cropChangedListener?.onCropChanged(getCropRectNormalized())
    }

    /**
     * Centre of an edge's +-[handleSlopPx] grab band, pushed inwards so the whole band stays within
     * `0..`[limit]. Without it an edge sitting at the view border keeps half of its band off-screen,
     * where no touch can land - the bottom edge lost that half under the command bar below (S1602).
     */
    private fun grabBandCenter(edge: Float, limit: Float): Float =
        edge.coerceIn(handleSlopPx, (limit - handleSlopPx).coerceAtLeast(handleSlopPx))

    private fun handleAt(x: Float, y: Float): Handle {
        val leftBand = grabBandCenter(selection.left, width.toFloat())
        val rightBand = grabBandCenter(selection.right, width.toFloat())
        val topBand = grabBandCenter(selection.top, height.toFloat())
        val bottomBand = grabBandCenter(selection.bottom, height.toFloat())
        val nearLeft = abs(x - leftBand) <= handleSlopPx
        val nearRight = abs(x - rightBand) <= handleSlopPx
        val nearTop = abs(y - topBand) <= handleSlopPx
        val nearBottom = abs(y - bottomBand) <= handleSlopPx
        val insideX = x in (leftBand - handleSlopPx)..(rightBand + handleSlopPx)
        val insideY = y in (topBand - handleSlopPx)..(bottomBand + handleSlopPx)
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

    private fun clampBottom(value: Float): Float {
        // Guarded so the lower bound never exceeds the upper on degenerate short content.
        val maxBottom = contentRect.bottom.coerceAtLeast(selection.top + minSidePx)
        return value.coerceIn(selection.top + minSidePx, maxBottom)
    }

    companion object {
        private const val DEFAULT_INSET_DP = 12f
        private const val MIN_SIDE_BANDS_DP = 48f
        private const val MIN_SIDE_CUTOUT_DP = 40f
        private const val HANDLE_SLOP_DP = 24f
        private const val KEY_STEP_DP = 16f
        private const val BORDER_STROKE_BANDS_DP = 6f
        private const val BORDER_STROKE_CUTOUT_DP = 2f
        private const val CORNER_HANDLE_RADIUS_DP = 12f
        private const val EDGE_HANDLE_HALF_DP = 8f
        private const val SCRIM_ALPHA_BANDS = 102 // ~40%
        private const val SCRIM_ALPHA_CUTOUT = 160 // ~63%
        private const val BORDER_ALPHA_BANDS = 191 // 75% opaque (25% transparent)
        private const val CUTOUT_MARGIN = 0.1f
    }
}
