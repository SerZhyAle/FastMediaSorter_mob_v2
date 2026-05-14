package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.sza.fastmediasorter.R
import timber.log.Timber


/**
 * Manages Draw Mode for the image player (S0107).
 *
 * Responsibilities:
 * - Draw Mode state machine (enter / exit).
 * - Transparent canvas overlay (DrawCanvasView) with brush, rectangle, eraser tools.
 * - ViewPager swipe blocking during Draw Mode.
 * - Screen orientation lock (ADR-4).
 * - Back-press interception.
 * - Save callback interface consumed by Phase 04.
 */
class ImageDrawOverlayManager(
    private val activity: Activity,
    private val imageContainer: ViewGroup,
    // S0162 ADR-4: restore rotation manager state on draw-mode exit (not unconditional UNSPECIFIED)
    private val screenRotationManager: ScreenRotationManager? = null,
    private val hasAccelerometer: Boolean = false
) {

    enum class DrawTool { BRUSH, RECTANGLE, ERASER }

    enum class DrawColor(val argb: Int) {
        WHITE(0xFFFFFFFF.toInt()),
        BLACK(0xFF000000.toInt()),
        GRAY(0xFF808080.toInt()),
        RED(0xFFE53935.toInt()),
        BLUE(0xFF1E88E5.toInt()),
        GREEN(0xFF43A047.toInt()),
        YELLOW(0xFFFDD835.toInt())
    }

    interface DrawOverlaySaveCallback {
        fun onSaveRequested(overlayBitmap: Bitmap, filename: String)
    }

    var isDrawModeActive: Boolean = false
        private set

    var selectedTool: DrawTool = DrawTool.BRUSH
    var selectedColor: DrawColor = DrawColor.BLACK
    var saveCallback: DrawOverlaySaveCallback? = null
    var editModeCallback: ((com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode) -> Unit)? = null

    private var drawCanvasView: DrawCanvasView? = null
    private var toolbarRoot: View? = null

    // Reference to current file for filename templating (set before enterDrawMode)
    var currentFile: com.sza.fastmediasorter.domain.model.MediaFile? = null

    // ── State machine ──────────────────────────────────────────────────────

    fun enterDrawMode() {
        if (isDrawModeActive) return
        isDrawModeActive = true
        editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.DRAW)

        // Inflate canvas and add to image container
        val canvas = DrawCanvasView(activity)
        drawCanvasView = canvas
        imageContainer.addView(canvas, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Block parent ViewPager from stealing touch events
        imageContainer.requestDisallowInterceptTouchEvent(true)

        // Lock screen orientation (ADR-4: no rotation inside Draw Mode)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        // Show toolbar if already bound
        toolbarRoot?.visibility = View.VISIBLE
    }

    fun exitDrawMode(save: Boolean) {
        if (!isDrawModeActive && save) return

        if (save) {
            val overlay = drawCanvasView?.getBitmap() ?: run {
                isDrawModeActive = false
                cleanupCanvas()
                return
            }
            handleSaveRequest(overlay)
            // Draw Mode stays active until the save dialog is dismissed or confirmed
            return
        }

        isDrawModeActive = false
        cleanupCanvas()
    }

    private fun handleSaveRequest(overlayBitmap: Bitmap) {
        val originalName = currentFile?.name ?: "image"
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val ext = if (dotIndex > 0) originalName.substring(dotIndex) else ""
        // ext contains a leading dot (e.g. ".jpg") — strip it before passing to buildName
        val extNoDot = ext.trimStart('.')
        val defaultFilename = ImageEditorFileNamer.buildName(baseName, extNoDot, ImageEditorFileNamer.DRAW)

        // Show filename dialog; on confirm invoke saveCallback
        val editText = EditText(activity).apply {
            setText(defaultFilename)
            hint = activity.getString(R.string.draw_overlay_filename_hint)
            setSingleLine(true)
        }
        AlertDialog.Builder(activity)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val confirmedFilename = editText.text?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: defaultFilename
                saveCallback?.onSaveRequested(overlayBitmap, confirmedFilename)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun cleanupCanvas() {
        drawCanvasView?.let { imageContainer.removeView(it) }
        drawCanvasView = null
        imageContainer.requestDisallowInterceptTouchEvent(false)
        // S0162 ADR-4: restore rotation manager state instead of unconditional UNSPECIFIED
        if (screenRotationManager != null) {
            screenRotationManager.reapply(activity, hasAccelerometer)
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        toolbarRoot?.visibility = View.GONE
        editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.NONE)
    }

    fun getOverlayBitmap(): Bitmap? = drawCanvasView?.getBitmap()

    // ── Back press ─────────────────────────────────────────────────────────

    /**
     * Returns true if Draw Mode was active and has been cancelled; false otherwise.
     * Caller must check this before its own back-navigation logic.
     */
    fun handleBackPress(): Boolean {
        if (!isDrawModeActive) return false
        exitDrawMode(save = false)
        return true
    }

    // ── Toolbar binding (Phase 03) ─────────────────────────────────────────

    fun bindToolbar(root: View) {
        toolbarRoot = root

        // Tool buttons
        root.findViewById<android.widget.ImageButton>(com.sza.fastmediasorter.R.id.btn_draw_tool_brush)
            ?.setOnClickListener { selectedTool = DrawTool.BRUSH; updateToolbarSelection(root) }
        root.findViewById<android.widget.ImageButton>(com.sza.fastmediasorter.R.id.btn_draw_tool_rect)
            ?.setOnClickListener { selectedTool = DrawTool.RECTANGLE; updateToolbarSelection(root) }
        root.findViewById<android.widget.ImageButton>(com.sza.fastmediasorter.R.id.btn_draw_tool_eraser)
            ?.setOnClickListener { selectedTool = DrawTool.ERASER; updateToolbarSelection(root) }

        // Color swatches
        val colorMap = mapOf(
            com.sza.fastmediasorter.R.id.color_white  to DrawColor.WHITE,
            com.sza.fastmediasorter.R.id.color_black  to DrawColor.BLACK,
            com.sza.fastmediasorter.R.id.color_gray   to DrawColor.GRAY,
            com.sza.fastmediasorter.R.id.color_red    to DrawColor.RED,
            com.sza.fastmediasorter.R.id.color_blue   to DrawColor.BLUE,
            com.sza.fastmediasorter.R.id.color_green  to DrawColor.GREEN,
            com.sza.fastmediasorter.R.id.color_yellow to DrawColor.YELLOW
        )
        colorMap.forEach { (id, color) ->
            root.findViewById<View>(id)?.setOnClickListener {
                selectedColor = color
                updateToolbarSelection(root)
            }
        }
        // Fill each swatch with its color (oval shape, white stroke) so it shows as a solid circle
        colorMap.forEach { (id, color) ->
            root.findViewById<View>(id)?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color.argb)
                setStroke(3, Color.WHITE)
            }
        }

        // Action buttons
        root.findViewById<android.widget.Button>(com.sza.fastmediasorter.R.id.btn_draw_save)
            ?.setOnClickListener { exitDrawMode(save = true) }
        root.findViewById<android.widget.Button>(com.sza.fastmediasorter.R.id.btn_draw_cancel)
            ?.setOnClickListener { exitDrawMode(save = false) }

        updateToolbarSelection(root)
    }

    private fun updateToolbarSelection(root: View) {
        // Dim inactive tool buttons; highlight active one with full alpha
        val toolIds = listOf(
            com.sza.fastmediasorter.R.id.btn_draw_tool_brush to DrawTool.BRUSH,
            com.sza.fastmediasorter.R.id.btn_draw_tool_rect to DrawTool.RECTANGLE,
            com.sza.fastmediasorter.R.id.btn_draw_tool_eraser to DrawTool.ERASER
        )
        toolIds.forEach { (id, tool) ->
            root.findViewById<View>(id)?.alpha = if (tool == selectedTool) 1.0f else 0.4f
        }
        // Scale active color swatch slightly to indicate selection
        val colorIds = listOf(
            com.sza.fastmediasorter.R.id.color_white  to DrawColor.WHITE,
            com.sza.fastmediasorter.R.id.color_black  to DrawColor.BLACK,
            com.sza.fastmediasorter.R.id.color_gray   to DrawColor.GRAY,
            com.sza.fastmediasorter.R.id.color_red    to DrawColor.RED,
            com.sza.fastmediasorter.R.id.color_blue   to DrawColor.BLUE,
            com.sza.fastmediasorter.R.id.color_green  to DrawColor.GREEN,
            com.sza.fastmediasorter.R.id.color_yellow to DrawColor.YELLOW
        )
        colorIds.forEach { (id, color) ->
            val scale = if (color == selectedColor) 1.2f else 1.0f
            root.findViewById<View>(id)?.let {
                it.scaleX = scale
                it.scaleY = scale
            }
        }
    }

    // ── Inner canvas view ──────────────────────────────────────────────────

    private inner class DrawCanvasView(context: android.content.Context) :
        View(context) {

        private var bitmap: Bitmap? = null
        private var bitmapCanvas: android.graphics.Canvas? = null

        // Tracking for rectangle preview
        private var startX: Float = 0f
        private var startY: Float = 0f
        private var currentX: Float = 0f
        private var currentY: Float = 0f

        private val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }

        private val previewPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmapCanvas = android.graphics.Canvas(bitmap!!)
            }
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

            // Draw rectangle preview (only during RECTANGLE drag)
            if (selectedTool == DrawTool.RECTANGLE && isPointerDown) {
                previewPaint.color = selectedColor.argb
                previewPaint.strokeWidth = 6f
                canvas.drawRect(startX, startY, currentX, currentY, previewPaint)
            }
        }

        private var isPointerDown = false

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            val x = event.x
            val y = event.y

            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isPointerDown = true
                    startX = x
                    startY = y
                    currentX = x
                    currentY = y
                    configurePaint()
                    if (selectedTool == DrawTool.BRUSH || selectedTool == DrawTool.ERASER) {
                        bitmapCanvas?.drawPoint(x, y, paint)
                        invalidate()
                    }
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    currentX = x
                    currentY = y
                    if (selectedTool == DrawTool.BRUSH || selectedTool == DrawTool.ERASER) {
                        bitmapCanvas?.drawLine(startX, startY, x, y, paint)
                        startX = x
                        startY = y
                    }
                    invalidate()
                }
                android.view.MotionEvent.ACTION_UP -> {
                    isPointerDown = false
                    currentX = x
                    currentY = y
                    when (selectedTool) {
                        DrawTool.RECTANGLE -> {
                            configurePaint()
                            bitmapCanvas?.drawRect(startX, startY, x, y, paint)
                        }
                        DrawTool.BRUSH, DrawTool.ERASER -> {
                            bitmapCanvas?.drawLine(startX, startY, x, y, paint)
                        }
                    }
                    invalidate()
                }
            }
            return true
        }

        private fun configurePaint() {
            when (selectedTool) {
                DrawTool.BRUSH -> {
                    paint.xfermode = null
                    paint.color = selectedColor.argb
                    paint.strokeWidth = 12f
                    paint.style = android.graphics.Paint.Style.STROKE
                }
                DrawTool.RECTANGLE -> {
                    paint.xfermode = null
                    paint.color = selectedColor.argb
                    paint.strokeWidth = 6f
                    paint.style = android.graphics.Paint.Style.STROKE
                }
                DrawTool.ERASER -> {
                    paint.xfermode = android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.CLEAR
                    )
                    paint.color = android.graphics.Color.TRANSPARENT
                    paint.strokeWidth = 48f
                    paint.style = android.graphics.Paint.Style.STROKE
                }
            }
        }

        fun clearCanvas() {
            bitmapCanvas?.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            invalidate()
        }

        fun getBitmap(): Bitmap = bitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
