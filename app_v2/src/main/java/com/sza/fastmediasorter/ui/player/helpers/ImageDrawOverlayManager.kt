package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


/** Manages image-player Draw Mode, including canvas overlay, toolbar binding, orientation lock, back handling, and save callbacks. */
class ImageDrawOverlayManager(
    private val activity: Activity,
    private val imageContainer: ViewGroup,
    // S0162 ADR-4: restore rotation manager state on draw-mode exit (not unconditional UNSPECIFIED)
    private val screenRotationManager: ScreenRotationManager? = null,
    private val hasAccelerometer: Boolean = false,
    // S0192 Phase 05 - Keep export dependency, used by the overflow menu.
    private val keepExportHelper: DrawKeepExportHelper
) {

    enum class DrawTool { BRUSH, RECTANGLE, OVAL, ERASER, TEXT }

    // S0192 Phase 05 - DrawColor enum dropped (old toolbar bindings removed).
    // Live state is the ARGB Int `selectedColorArgb`.

    /**
     * S0192 Phase 01 - per-action drawing primitives.
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

        // S0192 Phase 03 - Oval and Text variants.
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

    interface DrawOverlayActionCallback {
        fun onSaveRequested(overlayBitmap: Bitmap)
        fun onSaveAndCloseRequested(overlayBitmap: Bitmap)
        fun onSaveAndShareRequested(overlayBitmap: Bitmap)
        fun onShareRequested(overlayBitmap: Bitmap)
        fun onCancelRequested()
        fun onDeleteRequested()
    }

    /**
     * S0192 Phase 06 - in-place save callback. Fired by the `[Save]` button to
     * overwrite the current file. The activity-side implementation decides on
     * the read-only / non-local silent fallback per ADR-4.
     */
    interface DrawOverlayInPlaceSaveCallback {
        fun onInPlaceSaveRequested(overlayBitmap: Bitmap)
    }

    var isDrawModeActive: Boolean = false
        private set

    var selectedTool: DrawTool = DrawTool.BRUSH

    // S0192 Phase 02 - live state is an ARGB Int sourced from prefs at enterDrawMode.
    var selectedColorArgb: Int = 0xFFE53935.toInt() // Red default

    var saveCallback: DrawOverlaySaveCallback? = null
    var inPlaceSaveCallback: DrawOverlayInPlaceSaveCallback? = null
    var actionCallback: DrawOverlayActionCallback? = null
    var editModeCallback: ((com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode) -> Unit)? = null

    /**
     * S0192 Phase 05 - supplied by the host activity. Returns the bitmap of the
     * currently displayed image (used by the Keep export pipeline and, in
     * Phase 06, by the in-place save flow).
     */
    var baseBitmapProvider: () -> Bitmap? = { null }

    private var drawCanvasView: DrawCanvasView? = null
    private var toolbarRoot: View? = null
    private val cleanToolbarColor = 0xCC000000.toInt()
    private val dirtyToolbarColor = 0xCC6B2C00.toInt()

    // Reference to current file for filename templating (set before enterDrawMode)
    var currentFile: com.sza.fastmediasorter.domain.model.MediaFile? = null

    // ── State machine ──────────────────────────────────────────────────────

    fun enterDrawMode() {
        if (isDrawModeActive) return
        isDrawModeActive = true
        // S0192 Phase 02 - restore last used color from persistent prefs
        selectedColorArgb = DrawEditorPrefs.getLastColor(activity)
        editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.DRAW)

        val canvas = DrawCanvasView(activity)
        drawCanvasView = canvas
        imageContainer.addView(canvas, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        imageContainer.requestDisallowInterceptTouchEvent(true)

        // ADR-4: no rotation inside Draw Mode.
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        toolbarRoot?.visibility = View.VISIBLE
        canvas.markClean()
        updateToolbarDirtyState()
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
        val extNoDot = ext.trimStart('.')
        val defaultFilename = ImageEditorFileNamer.buildName(baseName, extNoDot, ImageEditorFileNamer.DRAW)

        // Show filename dialog; on confirm invoke saveCallback
        val editText = EditText(activity).apply {
            setText(defaultFilename)
            hint = activity.getString(R.string.draw_overlay_filename_hint)
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(activity)
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
        actionCallback?.onCancelRequested() ?: exitDrawMode(save = false)
        return true
    }

    // ── Toolbar binding (Phase 05) ─────────────────────────────────────────

    /**
     * S0192 Phase 05 - toolbar v2 wiring.
     *
     * Layout contract (portrait + landscape mirror each other):
     *   btn_draw_tool_selector - opens R.menu.menu_draw_tool_selector
     *   color_black / color_white / color_red / color_custom - 4 swatches
     *   btn_draw_save / btn_draw_save_close / btn_draw_close - direct session
     *       actions from S0191; "Save & send" and "Share" moved to the overflow
     *       menu so the strip stays narrow on phones (see menu_draw_overflow).
     *   btn_draw_overflow - opens R.menu.menu_draw_overflow (Save as.. / Undo last /
     *       Undo all / Settings / Send to Google Keep)
     */
    fun bindToolbar(root: View) {
        toolbarRoot = root

        // S0368: keep the draw toolbar inside the system-bar / display-cutout safe area.
        // Adds maxOf(systemBars, cutout) on top of the existing 8dp base padding; bottom
        // matters in portrait (nav bar), left/right in landscape (cutout / gesture rail).
        root.applySystemBarInsetPadding()

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

        root.findViewById<android.widget.ImageButton>(com.sza.fastmediasorter.R.id.btn_draw_save)
            ?.setOnClickListener {
                val overlay = drawCanvasView?.getBitmap() ?: return@setOnClickListener
                actionCallback?.onSaveRequested(overlay)
            }
        root.findViewById<android.widget.ImageButton>(com.sza.fastmediasorter.R.id.btn_draw_save_close)
            ?.setOnClickListener {
                val overlay = drawCanvasView?.getBitmap() ?: return@setOnClickListener
                actionCallback?.onSaveAndCloseRequested(overlay)
            }

        // ── Close button (X) ─────────────────────────────────────────────
        root.findViewById<android.widget.ImageButton>(com.sza.fastmediasorter.R.id.btn_draw_close)
            ?.setOnClickListener { actionCallback?.onCancelRequested() ?: exitDrawMode(save = false) }

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
                // S0360: "Delete file" only when a source file is open.
                menu.findItem(com.sza.fastmediasorter.R.id.draw_overflow_delete_file)
                    ?.isVisible = currentFile != null
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
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
                        com.sza.fastmediasorter.R.id.draw_overflow_send_to -> {
                            launchSendTo()
                            true
                        }
                        com.sza.fastmediasorter.R.id.draw_overflow_delete_file -> {
                            actionCallback?.onDeleteRequested()
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        updateToolbarSelection(root)
        updateToolbarDirtyState()
    }

    /**
     * S0192 Phase 05 - retained for binary compatibility with older PlayerDrawingSaveHelper
     * call sites. The Save & send / Share buttons no longer live on the toolbar (moved to
     * the overflow menu), so toggling visibility on the toolbar is a no-op now. Kept as a
     * no-op so removing the helper call can happen in a separate change.
     */
    @Suppress("UNUSED_PARAMETER")
    fun setShareActionsVisible(visible: Boolean) {
        // no-op: share actions live in R.menu.menu_draw_overflow now.
    }

    fun markCurrentStateSaved() {
        drawCanvasView?.markClean()
    }

    /**
     * S0459: route the composited drawing into the unified «Send to..» menu. Merges base + overlay
     * via [keepExportHelper], then hands the resulting image Uri to [SendToMenuManager], which gates
     * and dispatches the receivers (Keep/Lens/messengers/..). Failures show the save-failed toast.
     */
    private fun launchSendTo() {
        val baseBitmap = baseBitmapProvider() ?: run {
            android.widget.Toast.makeText(
                activity,
                R.string.draw_overlay_save_failed,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        val overlay = drawCanvasView?.getBitmap() ?: return
        val host = activity as? androidx.fragment.app.FragmentActivity ?: return
        val owner = activity as? LifecycleOwner ?: return
        owner.lifecycleScope.launch {
            val uri = keepExportHelper.prepareMergedImageUri(activity, baseBitmap, overlay)
                .getOrElse { error ->
                    Timber.e(error, "Draw send-to: merge failed")
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            activity,
                            R.string.draw_overlay_save_failed,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                activity.applicationContext, SendToEntryPoint::class.java
            )
            val settings = entryPoint.settingsRepository().getSettings().first()
            val content = com.sza.fastmediasorter.core.share.ShareableContent(
                uris = listOf(uri),
                mime = "image/jpeg",
                mediaType = com.sza.fastmediasorter.domain.model.MediaType.IMAGE,
                displayName = currentFile?.name ?: "drawing.jpg",
            )
            entryPoint.sendToMenuManager().show(host, content, settings)
        }
    }

    // S0459: app-scoped accessors for the unified send-to menu + settings, reached from this
    // manually-built (non-Hilt) manager via the application's SingletonComponent.
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    internal interface SendToEntryPoint {
        fun sendToMenuManager(): com.sza.fastmediasorter.ui.share.SendToMenuManager
        fun settingsRepository(): com.sza.fastmediasorter.domain.repository.SettingsRepository
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
     * S0192 Phase 02 - single entry point for any "user picked a color" event.
     * Persists the choice and refreshes the toolbar selection ring.
     */
    private fun setActiveColor(argb: Int) {
        selectedColorArgb = argb
        DrawEditorPrefs.setLastColor(activity, argb)
        toolbarRoot?.let { updateToolbarSelection(it) }
    }

    private fun updateToolbarDirtyState() {
        toolbarRoot?.setBackgroundColor(
            if (drawCanvasView?.hasUnsavedChanges() == true) dirtyToolbarColor else cleanToolbarColor
        )
    }

    // ── Inner canvas view ──────────────────────────────────────────────────

    private inner class DrawCanvasView(context: android.content.Context) :
        View(context) {

        // S0192 Phase 01 - action-list replay model. The single accumulator bitmap
        // is gone: every stroke/shape lives in `actions` and is replayed on each
        // onDraw. Off-screen rendering is needed only at export time (getBitmap).
        private val actions = mutableListOf<DrawAction>()
        private var revision: Int = 0
        private var cleanRevision: Int = 0

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

        init {
            // On a hardware-accelerated canvas PorterDuff.Mode.CLEAR paints opaque
            // black instead of clearing to transparent, so the eraser would "draw
            // black" on screen. A software layer gives the view a real alpha buffer
            // where CLEAR truly removes the stroke pixels; the transparent result
            // then composites over the base image, matching getBitmap() export.
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            // The view background is transparent and the view runs on a software
            // layer (see init), so PorterDuff.Mode.CLEAR strokes clear replayed
            // pixels to transparent rather than punching opaque black through the
            // base image beneath (Antigravity §9.1).
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
                            // S0192 Phase 02 - bake opacity into ARGB at action creation (ADR-6)
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
                        noteMutation()
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
                            noteMutation()
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
                            noteMutation()
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
            if (actions.removeLastOrNull() != null) {
                noteMutation()
                invalidate()
            }
        }

        fun undoAll() {
            if (actions.isNotEmpty()) {
                actions.clear()
                noteMutation()
                invalidate()
            }
        }

        fun hasActions(): Boolean = actions.isNotEmpty()

        fun hasUnsavedChanges(): Boolean = revision != cleanRevision

        fun markClean() {
            cleanRevision = revision
            this@ImageDrawOverlayManager.updateToolbarDirtyState()
        }

        /**
         * S0192 Phase 03 - Text tool entry point. Opens a MaterialAlertDialogBuilder dialog with a
         * single-line EditText (Antigravity §9.3 - multiline deferred). Empty
         * input is silently ignored; non-empty input becomes a TextEntry action
         * at the tap coordinates with current color + opacity-baked alpha + the
         * paint-ready text size from prefs.
         */
        private fun promptTextEntry(tapX: Float, tapY: Float) {
            val editText = android.widget.EditText(activity).apply {
                setSingleLine(true)
                hint = activity.getString(R.string.draw_text_input_hint)
            }
            MaterialAlertDialogBuilder(activity)
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
                    noteMutation()
                    invalidate()
                }
                .show()
        }

        private fun noteMutation() {
            revision += 1
            this@ImageDrawOverlayManager.updateToolbarDirtyState()
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
