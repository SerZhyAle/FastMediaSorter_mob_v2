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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val hasAccelerometer: Boolean = false,
    // S0192 Phase 05 — Keep export dependency, used by the overflow menu.
    private val keepExportHelper: DrawKeepExportHelper
) {

    enum class DrawTool { BRUSH, RECTANGLE, OVAL, ERASER, TEXT }

    // S0192 Phase 05 — DrawColor enum dropped (old toolbar bindings removed).
    // Live state is the ARGB Int `selectedColorArgb`.

    /**
     * S0192 Phase 01 — per-action drawing primitives.
     *
     * Replay model: the canvas is rebuilt on every onDraw by iterating this list.
     * Eraser is encoded as a Stroke whose color == Color.TRANSPARENT; the replay
     * step recognises this and applies PorterDuff.Mode.CLEAR.
     *
     * cachedPath (Stroke only) is compiled lazily on first replay and reused to
     * avoid GC churn during long sessions (Antigravity §9.1). It is invalidated
     * (set to null) when the points list is mutated during ACTION_MOVE.
     */
    sealed class DrawAction {
        data class Stroke(
            val points: MutableList<android.graphics.PointF>,
            val color: Int,
            val width: Float,
            var cachedPath: android.graphics.Path? = null
        ) : DrawAction()

        data class ShapeRect(
            val left: Float,
            val top: Float,
            val right: Float,
            val bottom: Float,
            val color: Int,
            val width: Float
        ) : DrawAction()

        // S0192 Phase 03 — Oval and Text variants.
        data class ShapeOval(
            val left: Float,
            val top: Float,
            val right: Float,
            val bottom: Float,
            val color: Int,
            val width: Float
        ) : DrawAction()

        data class TextEntry(
            val x: Float,
            val y: Float,
            val text: String,
            val color: Int,
            val textSizePx: Float
        ) : DrawAction()
    }

    interface DrawOverlaySaveCallback {
        fun onSaveRequested(overlayBitmap: Bitmap, filename: String)
    }

    /**
     * S0192 Phase 06 — in-place save callback. Fired by the `[Save]` button to
     * overwrite the current file. The activity-side implementation decides on
     * the read-only / non-local silent fallback per ADR-4.
     */
    interface DrawOverlayInPlaceSaveCallback {
        fun onInPlaceSaveRequested(overlayBitmap: Bitmap)
    }

    var isDrawModeActive: Boolean = false
        private set

    var selectedTool: DrawTool = DrawTool.BRUSH

    // S0192 Phase 02 — live state is now an ARGB Int (sourced from prefs at
    // enterDrawMode). The DrawColor enum is preserved for the legacy toolbar
    // binding until Phase 05 replaces it.
    var selectedColorArgb: Int = 0xFFE53935.toInt() // Red default

    var saveCallback: DrawOverlaySaveCallback? = null
    var inPlaceSaveCallback: DrawOverlayInPlaceSaveCallback? = null
    var editModeCallback: ((com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode) -> Unit)? = null

    /**
     * S0192 Phase 05 — supplied by the host activity. Returns the bitmap of the
     * currently displayed image (used by the Keep export pipeline and, in
     * Phase 06, by the in-place save flow).
     */
    var baseBitmapProvider: () -> Bitmap? = { null }

    private var drawCanvasView: DrawCanvasView? = null
    private var toolbarRoot: View? = null

    // Reference to current file for filename templating (set before enterDrawMode)
    var currentFile: com.sza.fastmediasorter.domain.model.MediaFile? = null

    // ── State machine ──────────────────────────────────────────────────────

    fun enterDrawMode() {
        if (isDrawModeActive) return
        isDrawModeActive = true
        // S0192 Phase 02 — restore last used color from persistent prefs
        selectedColorArgb = DrawEditorPrefs.getLastColor(activity)
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

    // ── Toolbar binding (Phase 05) ─────────────────────────────────────────

    /**
     * S0192 Phase 05 — toolbar v2 wiring.
     *
     * Layout contract (portrait + landscape mirror each other):
     *   btn_draw_tool_selector — opens R.menu.menu_draw_tool_selector
     *   color_black / color_white / color_red / color_custom — 4 swatches
     *   btn_draw_overflow — opens R.menu.menu_draw_overflow (Save / Save as.. /
     *       Undo last / Undo all / Settings / Send to Google Keep)
     *   btn_draw_close — exit Draw Mode without saving
     *
     * Phase 06 revision: the standalone in-place save button was removed; both
     * save modes now live inside the overflow popup. This eliminates the visual
     * duplicate with "Save to new file" that the user reported on 2026-05-17 and
     * collapses the toolbar from two rows to one.
     */
    fun bindToolbar(root: View) {
        toolbarRoot = root

        // ── Tool selector ────────────────────────────────────────────────
        val selectorBtn = root.findViewById<android.widget.ImageButton>(
            com.sza.fastmediasorter.R.id.btn_draw_tool_selector
        )
        selectorBtn?.setImageResource(iconForTool(selectedTool))
        selectorBtn?.setOnClickListener { anchor ->
            android.widget.PopupMenu(activity, anchor).apply {
                menuInflater.inflate(com.sza.fastmediasorter.R.menu.menu_draw_tool_selector, menu)
                setOnMenuItemClickListener { item ->
                    val tool = when (item.itemId) {
                        com.sza.fastmediasorter.R.id.draw_tool_brush -> DrawTool.BRUSH
                        com.sza.fastmediasorter.R.id.draw_tool_rect -> DrawTool.RECTANGLE
                        com.sza.fastmediasorter.R.id.draw_tool_oval -> DrawTool.OVAL
                        com.sza.fastmediasorter.R.id.draw_tool_eraser -> DrawTool.ERASER
                        com.sza.fastmediasorter.R.id.draw_tool_text -> DrawTool.TEXT
                        else -> return@setOnMenuItemClickListener false
                    }
                    selectedTool = tool
                    selectorBtn.setImageResource(iconForTool(tool))
                    true
                }
                show()
            }
        }

        // ── 4 colour swatches ────────────────────────────────────────────
        val fixedSwatches = mapOf(
            com.sza.fastmediasorter.R.id.color_black to 0xFF000000.toInt(),
            com.sza.fastmediasorter.R.id.color_white to 0xFFFFFFFF.toInt(),
            com.sza.fastmediasorter.R.id.color_red to 0xFFE53935.toInt()
        )
        fixedSwatches.forEach { (id, argb) ->
            root.findViewById<View>(id)?.apply {
                backgroundTintList = android.content.res.ColorStateList.valueOf(argb)
                setOnClickListener { setActiveColor(argb) }
            }
        }
        val customSwatch = root.findViewById<View>(com.sza.fastmediasorter.R.id.color_custom)
        customSwatch?.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                DrawEditorPrefs.getCustomColor(activity)
            )
            setOnClickListener {
                DrawColorGridDialog(
                    activity = activity,
                    initialColor = DrawEditorPrefs.getCustomColor(activity)
                ) { picked ->
                    DrawEditorPrefs.setCustomColor(activity, picked)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(picked)
                    setActiveColor(picked)
                }.show()
            }
        }

        // ── Close button (X) ─────────────────────────────────────────────
        root.findViewById<android.widget.ImageButton>(com.sza.fastmediasorter.R.id.btn_draw_close)
            ?.setOnClickListener { exitDrawMode(save = false) }

        // ── Overflow menu (⋮) ────────────────────────────────────────────
        val overflowBtn = root.findViewById<android.widget.ImageButton>(
            com.sza.fastmediasorter.R.id.btn_draw_overflow
        )
        overflowBtn?.setOnClickListener { anchor ->
            android.widget.PopupMenu(activity, anchor).apply {
                menuInflater.inflate(com.sza.fastmediasorter.R.menu.menu_draw_overflow, menu)
                val hasActions = drawCanvasView?.hasActions() == true
                menu.findItem(com.sza.fastmediasorter.R.id.draw_overflow_undo_last)
                    ?.isEnabled = hasActions
                menu.findItem(com.sza.fastmediasorter.R.id.draw_overflow_undo_all)
                    ?.isEnabled = hasActions
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        com.sza.fastmediasorter.R.id.draw_overflow_save_inplace -> {
                            // Phase 06 — in-place overwrite. The activity-side
                            // callback writes back to the current file and shows
                            // a success/failure toast; no filename prompt under
                            // any circumstances.
                            val overlay = drawCanvasView?.getBitmap()
                                ?: return@setOnMenuItemClickListener true
                            inPlaceSaveCallback?.onInPlaceSaveRequested(overlay)
                            true
                        }
                        com.sza.fastmediasorter.R.id.draw_overflow_save_new -> {
                            val overlay = drawCanvasView?.getBitmap()
                                ?: return@setOnMenuItemClickListener true
                            handleSaveRequest(overlay)
                            true
                        }
                        com.sza.fastmediasorter.R.id.draw_overflow_undo_last -> {
                            drawCanvasView?.undoLast(); true
                        }
                        com.sza.fastmediasorter.R.id.draw_overflow_undo_all -> {
                            drawCanvasView?.undoAll(); true
                        }
                        com.sza.fastmediasorter.R.id.draw_overflow_settings -> {
                            DrawSettingsDialog(activity).show()
                            true
                        }
                        com.sza.fastmediasorter.R.id.draw_overflow_keep -> {
                            launchKeepExport()
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        updateToolbarSelection(root)
    }

    /**
     * S0192 Phase 05 — fires the Keep export pipeline. Needs the base bitmap
     * (the currently displayed photo) which the activity supplies via
     * [baseBitmapProvider]. Failures show the existing save-failed toast.
     */
    private fun launchKeepExport() {
        val baseBitmap = baseBitmapProvider() ?: run {
            android.widget.Toast.makeText(
                activity,
                R.string.draw_overlay_save_failed,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        val overlay = drawCanvasView?.getBitmap() ?: return
        val owner = activity as? LifecycleOwner ?: return
        owner.lifecycleScope.launch {
            val result = keepExportHelper.export(activity, baseBitmap, overlay)
            result.onFailure { error ->
                Timber.e(error, "S0192: Keep export failed")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        activity,
                        R.string.draw_overlay_save_failed,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun iconForTool(tool: DrawTool): Int = when (tool) {
        DrawTool.BRUSH -> com.sza.fastmediasorter.R.drawable.ic_draw_overlay
        DrawTool.RECTANGLE -> com.sza.fastmediasorter.R.drawable.ic_draw_rect
        DrawTool.OVAL -> com.sza.fastmediasorter.R.drawable.ic_draw_oval
        DrawTool.ERASER -> com.sza.fastmediasorter.R.drawable.ic_eraser
        DrawTool.TEXT -> com.sza.fastmediasorter.R.drawable.ic_draw_text
    }

    private fun updateToolbarSelection(root: View) {
        // Update tool selector icon to match active tool
        root.findViewById<android.widget.ImageButton>(com.sza.fastmediasorter.R.id.btn_draw_tool_selector)
            ?.setImageResource(iconForTool(selectedTool))

        // Mark active swatch via state_selected (the selector drawable renders the ring)
        val swatchPairs = listOf(
            com.sza.fastmediasorter.R.id.color_black to 0xFF000000.toInt(),
            com.sza.fastmediasorter.R.id.color_white to 0xFFFFFFFF.toInt(),
            com.sza.fastmediasorter.R.id.color_red to 0xFFE53935.toInt()
        )
        var matched = false
        swatchPairs.forEach { (id, argb) ->
            val isActive = (argb == selectedColorArgb)
            if (isActive) matched = true
            root.findViewById<View>(id)?.isSelected = isActive
        }
        // The custom swatch is selected when no fixed swatch matches the active colour
        root.findViewById<View>(com.sza.fastmediasorter.R.id.color_custom)?.isSelected = !matched
    }

    /**
     * S0192 Phase 02 — single entry point for any "user picked a color" event.
     * Persists the choice and refreshes the toolbar selection ring.
     */
    private fun setActiveColor(argb: Int) {
        selectedColorArgb = argb
        DrawEditorPrefs.setLastColor(activity, argb)
        toolbarRoot?.let { updateToolbarSelection(it) }
    }

    // ── Inner canvas view ──────────────────────────────────────────────────

    private inner class DrawCanvasView(context: android.content.Context) :
        View(context) {

        // S0192 Phase 01 — action-list replay model. The single accumulator bitmap
        // is gone: every stroke/shape lives in `actions` and is replayed on each
        // onDraw. Off-screen rendering is needed only at export time (getBitmap).
        private val actions = mutableListOf<DrawAction>()

        // Tracking for rectangle preview during drag
        private var startX: Float = 0f
        private var startY: Float = 0f
        private var currentX: Float = 0f
        private var currentY: Float = 0f

        private var isPointerDown = false

        // Reference to the currently in-progress brush/eraser stroke so MOVE
        // events can append points to the same DrawAction.Stroke entry.
        private var currentStroke: DrawAction.Stroke? = null

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

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            // The view background is transparent; PorterDuff.Mode.CLEAR strokes
            // therefore clear only previously replayed pixels in this frame and
            // never punch through the base image beneath (Antigravity §9.1).
            replay(canvas)

            // Live preview during RECTANGLE / OVAL drag (commit happens on ACTION_UP)
            if (selectedTool == DrawTool.RECTANGLE && isPointerDown) {
                previewPaint.color = selectedColorArgb
                previewPaint.strokeWidth = DrawEditorPrefs.getBrushSize(context).toFloat()
                canvas.drawRect(startX, startY, currentX, currentY, previewPaint)
            }
            if (selectedTool == DrawTool.OVAL && isPointerDown) {
                previewPaint.color = selectedColorArgb
                previewPaint.strokeWidth = DrawEditorPrefs.getBrushSize(context).toFloat()
                canvas.drawOval(
                    android.graphics.RectF(startX, startY, currentX, currentY),
                    previewPaint
                )
            }
        }

        /**
         * Iterates the action list in order and renders each entry onto [canvas].
         * Shared by onDraw (per-frame paint) and getBitmap (export rendering).
         */
        private fun replay(canvas: android.graphics.Canvas) {
            for (action in actions) {
                when (action) {
                    is DrawAction.Stroke -> {
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeCap = android.graphics.Paint.Cap.ROUND
                        paint.strokeJoin = android.graphics.Paint.Join.ROUND
                        paint.strokeWidth = action.width
                        if (action.color == android.graphics.Color.TRANSPARENT) {
                            paint.xfermode = android.graphics.PorterDuffXfermode(
                                android.graphics.PorterDuff.Mode.CLEAR
                            )
                            paint.color = android.graphics.Color.TRANSPARENT
                        } else {
                            paint.xfermode = null
                            paint.color = action.color
                        }
                        val path = action.cachedPath
                            ?: buildPath(action.points).also { action.cachedPath = it }
                        canvas.drawPath(path, paint)
                    }
                    is DrawAction.ShapeRect -> {
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.xfermode = null
                        paint.color = action.color
                        paint.strokeWidth = action.width
                        canvas.drawRect(action.left, action.top, action.right, action.bottom, paint)
                    }
                    is DrawAction.ShapeOval -> {
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.xfermode = null
                        paint.color = action.color
                        paint.strokeWidth = action.width
                        canvas.drawOval(
                            android.graphics.RectF(action.left, action.top, action.right, action.bottom),
                            paint
                        )
                    }
                    is DrawAction.TextEntry -> {
                        paint.style = android.graphics.Paint.Style.FILL
                        paint.xfermode = null
                        paint.color = action.color
                        paint.textSize = action.textSizePx
                        canvas.drawText(action.text, action.x, action.y, paint)
                        // Restore STROKE so subsequent stroke/shape entries replay correctly
                        paint.style = android.graphics.Paint.Style.STROKE
                    }
                }
            }
            // Restore paint to a clean state for any callers (preview/replay reuse)
            paint.xfermode = null
        }

        private fun buildPath(points: List<android.graphics.PointF>): android.graphics.Path {
            val path = android.graphics.Path()
            if (points.isEmpty()) return path
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
            return path
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            val x = event.x
            val y = event.y

            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isPointerDown = true
                    startX = x; startY = y
                    currentX = x; currentY = y
                    if (selectedTool == DrawTool.BRUSH || selectedTool == DrawTool.ERASER) {
                        val color = if (selectedTool == DrawTool.ERASER) {
                            android.graphics.Color.TRANSPARENT
                        } else {
                            // S0192 Phase 02 — bake opacity into ARGB at action creation (ADR-6)
                            (selectedColorArgb and 0x00FFFFFF) or (DrawEditorPrefs.opacityAlpha(context) shl 24)
                        }
                        val width = if (selectedTool == DrawTool.ERASER) {
                            DrawEditorPrefs.getBrushSize(context).toFloat() * 2f
                        } else {
                            DrawEditorPrefs.getBrushSize(context).toFloat()
                        }
                        val stroke = DrawAction.Stroke(
                            points = mutableListOf(android.graphics.PointF(x, y)),
                            color = color,
                            width = width
                        )
                        actions.add(stroke)
                        currentStroke = stroke
                    }
                    invalidate()
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    currentX = x; currentY = y
                    if (selectedTool == DrawTool.BRUSH || selectedTool == DrawTool.ERASER) {
                        currentStroke?.let {
                            it.points.add(android.graphics.PointF(x, y))
                            it.cachedPath = null
                        }
                    }
                    invalidate()
                }
                android.view.MotionEvent.ACTION_UP -> {
                    isPointerDown = false
                    currentX = x; currentY = y
                    when (selectedTool) {
                        DrawTool.RECTANGLE -> {
                            actions.add(
                                DrawAction.ShapeRect(
                                    left = minOf(startX, x),
                                    top = minOf(startY, y),
                                    right = maxOf(startX, x),
                                    bottom = maxOf(startY, y),
                                    color = (selectedColorArgb and 0x00FFFFFF) or
                                        (DrawEditorPrefs.opacityAlpha(context) shl 24),
                                    width = DrawEditorPrefs.getBrushSize(context).toFloat()
                                )
                            )
                        }
                        DrawTool.OVAL -> {
                            actions.add(
                                DrawAction.ShapeOval(
                                    left = minOf(startX, x),
                                    top = minOf(startY, y),
                                    right = maxOf(startX, x),
                                    bottom = maxOf(startY, y),
                                    color = (selectedColorArgb and 0x00FFFFFF) or
                                        (DrawEditorPrefs.opacityAlpha(context) shl 24),
                                    width = DrawEditorPrefs.getBrushSize(context).toFloat()
                                )
                            )
                        }
                        DrawTool.BRUSH, DrawTool.ERASER -> {
                            currentStroke?.let {
                                it.points.add(android.graphics.PointF(x, y))
                                it.cachedPath = null
                            }
                            currentStroke = null
                        }
                        DrawTool.TEXT -> {
                            promptTextEntry(x, y)
                        }
                    }
                    invalidate()
                }
            }
            return true
        }

        fun undoLast() {
            actions.removeLastOrNull()
            invalidate()
        }

        fun undoAll() {
            actions.clear()
            invalidate()
        }

        fun hasActions(): Boolean = actions.isNotEmpty()

        /**
         * S0192 Phase 03 — Text tool entry point. Opens an AlertDialog with a
         * single-line EditText (Antigravity §9.3 — multiline deferred). Empty
         * input is silently ignored; non-empty input becomes a TextEntry action
         * at the tap coordinates with current color + opacity-baked alpha + the
         * paint-ready text size from prefs.
         */
        private fun promptTextEntry(tapX: Float, tapY: Float) {
            val editText = android.widget.EditText(activity).apply {
                setSingleLine(true)
                hint = activity.getString(R.string.draw_text_input_hint)
            }
            AlertDialog.Builder(activity)
                .setView(editText)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val text = editText.text?.toString()?.trim().orEmpty()
                    if (text.isEmpty()) return@setPositiveButton
                    val argb = (selectedColorArgb and 0x00FFFFFF) or
                        (DrawEditorPrefs.opacityAlpha(context) shl 24)
                    val textSizePx = DrawEditorPrefs.textSizePx(context)
                    actions.add(
                        DrawAction.TextEntry(
                            x = tapX,
                            y = tapY,
                            text = text,
                            color = argb,
                            textSizePx = textSizePx
                        )
                    )
                    invalidate()
                }
                .show()
        }

        fun getBitmap(): Bitmap {
            if (width <= 0 || height <= 0) {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(android.graphics.Color.TRANSPARENT)
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // Ensure the off-screen surface starts transparent so PorterDuff.Mode.CLEAR
            // strokes do not punch through the base image during export (Antigravity §9.1).
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            val canvas = android.graphics.Canvas(bitmap)
            replay(canvas)
            return bitmap
        }
    }
}
