package com.sza.fastmediasorter.ui.xr

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.Keep
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.xr.VrLaunchMode
import com.sza.fastmediasorter.core.xr.VrLaunchPayloadHolder
import com.sza.fastmediasorter.core.xr.VrMediaType
import com.sza.fastmediasorter.core.xr.VrPanelReturnTarget
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrNativeResult
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import com.sza.fastmediasorter.ui.xr.browse.ImmersiveBrowseCell
import com.sza.fastmediasorter.ui.xr.browse.ImmersiveBrowseContentLoader
import com.sza.fastmediasorter.ui.xr.browse.ImmersiveBrowseGridRenderer
import com.sza.fastmediasorter.ui.xr.browse.ImmersiveBrowseInteractionDispatcher
import com.sza.fastmediasorter.ui.xr.browse.ImmersiveBrowsePlaybackController
import com.sza.fastmediasorter.ui.xr.browse.ImmersiveThumbnailDecoder
import com.sza.fastmediasorter.ui.xr.helpers.HudHapticBridge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * S0963 (Pillar 2): immersive file browser. Reads a resource through the domain layer, renders a
 * thumbnail grid on the HUD quad (the ray-addressable surface), and on selection transitions
 * in-process to playback on the main media quad - no OpenXR session re-launch per pick (S0773 §8).
 * Shares the render thread / native runtime / haptics with the diagnostic host without editing it.
 */
@AndroidEntryPoint
class ImmersiveBrowseActivity : ComponentActivity(), SurfaceHolder.Callback {

    @Inject lateinit var runtime: DiagnosticXrRuntime

    @Inject lateinit var payloadHolder: VrLaunchPayloadHolder

    @Inject lateinit var contentLoader: ImmersiveBrowseContentLoader

    @Inject lateinit var thumbnailDecoder: ImmersiveThumbnailDecoder

    @Inject lateinit var playbackController: ImmersiveBrowsePlaybackController

    private enum class BrowseState { BROWSE, PLAYBACK }

    private var renderThread: DiagnosticXrRenderThread? = null
    private var surface: android.view.Surface? = null
    private lateinit var surfaceView: SurfaceView
    private var windowFocusedDeferred = CompletableDeferred<Unit>()
    private var renderThreadStarted = false

    private val gridRenderer = ImmersiveBrowseGridRenderer()
    private lateinit var dispatcher: ImmersiveBrowseInteractionDispatcher
    private lateinit var hapticBridge: HudHapticBridge
    private lateinit var hudBitmap: Bitmap
    private lateinit var hudCanvas: Canvas
    private var reusableHudBuffer: ByteBuffer? = null

    private var resourceId: Long = NO_RESOURCE
    private var state = BrowseState.BROWSE
    private var cells: List<ImmersiveBrowseCell> = emptyList()
    private var pageOffset = 0
    private val folderStack = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val parsed = DiagnosticXrLaunchArgs.parse(
            intent,
            payloadHolder = payloadHolder,
            defaultReturnTarget = VrPanelReturnTarget.Settings(0),
        )
        val input = (parsed as? DiagnosticXrLaunchArgs.Parsed)?.input
        if (input == null || input.launchMode != VrLaunchMode.RESOURCE_BROWSE || input.resourceId == null) {
            Timber.w("ImmersiveBrowseActivity: missing RESOURCE_BROWSE input, finishing")
            finish()
            return
        }
        resourceId = input.requireResourceId()

        if (!runtime.isNativeAvailable) {
            Timber.w("ImmersiveBrowseActivity: native runtime unavailable, finishing")
            finish()
            return
        }
        checkHandTrackingPermission()
    }

    private fun checkHandTrackingPermission() {
        val granted = ContextCompat.checkSelfPermission(this, HAND_TRACKING_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            proceedWithInitialization()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(HAND_TRACKING_PERMISSION), REQUEST_HAND_TRACKING)
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_HAND_TRACKING) {
            proceedWithInitialization()
        }
    }

    private fun proceedWithInitialization() {
        hapticBridge = HudHapticBridge(runtime)
        dispatcher = ImmersiveBrowseInteractionDispatcher(gridRenderer).apply {
            onCellSelected = { cell -> onCellSelected(cell) }
            onPageScroll = { direction -> onPageScroll(direction) }
        }
        hudBitmap = Bitmap.createBitmap(
            ImmersiveBrowseGridRenderer.PANEL_WIDTH,
            ImmersiveBrowseGridRenderer.PANEL_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        hudCanvas = Canvas(hudBitmap)

        surfaceView = SurfaceView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            holder.addCallback(this@ImmersiveBrowseActivity)
        }
        setContentView(FrameLayout(this).apply { addView(surfaceView) })
        surfaceView.requestFocus()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = onBackRequested()
            }
        )

        loadContent(currentPath = null)
    }

    // ---- content ----

    private fun loadContent(currentPath: String?) {
        lifecycleScope.launch {
            cells = contentLoader.load(resourceId, currentPath)
            pageOffset = 0
            drawAndPushGrid()
            decodeVisibleThumbnails()
        }
    }

    private fun decodeVisibleThumbnails() {
        val pageSize = gridRenderer.pageSize
        val visible = cells.drop(pageOffset).take(pageSize).filter { !it.isFolder && it.thumbnail == null }
        for (cell in visible) {
            val model = cell.filePath ?: continue
            val isVideo = cell.mediaType == VrMediaType.VIDEO
            lifecycleScope.launch(Dispatchers.Main.immediate) {
                val bitmap = thumbnailDecoder.decode(model, isVideo, CELL_THUMB_PX, CELL_THUMB_PX)
                if (bitmap != null && state == BrowseState.BROWSE) {
                    cell.thumbnail = bitmap
                    drawAndPushGrid()
                }
            }
        }
    }

    private fun drawAndPushGrid() {
        val breadcrumb = folderStack.lastOrNull()?.let { File(it).name } ?: ""
        gridRenderer.layout(cells, pageOffset)
        gridRenderer.draw(hudCanvas, cells, pageOffset, dispatcher.hoveredIndex, breadcrumb)
        val bytes = copyHudToRgba() ?: return
        runtime.queueHud(bytes, ImmersiveBrowseGridRenderer.PANEL_WIDTH, ImmersiveBrowseGridRenderer.PANEL_HEIGHT)
    }

    private fun copyHudToRgba(): ByteArray? {
        val size = ImmersiveBrowseGridRenderer.PANEL_WIDTH *
            ImmersiveBrowseGridRenderer.PANEL_HEIGHT * RGBA_BYTES_PER_PIXEL
        val buffer = reusableHudBuffer?.apply { clear() } ?: ByteBuffer.allocateDirect(size).also {
            reusableHudBuffer = it
        }
        hudBitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
        return runCatching {
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            bytes
        }.getOrElse {
            Timber.e(it, "ImmersiveBrowseActivity: HUD RGBA copy failed")
            null
        }
    }

    // ---- interaction ----

    @Keep
    fun onNativeRayInteraction(uvX: Float, uvY: Float, isHover: Boolean, isClick: Boolean) {
        runOnUiThread {
            if (isFinishing || isDestroyed || state != BrowseState.BROWSE) return@runOnUiThread
            val previousHover = dispatcher.hoveredIndex
            val resolved = dispatcher.dispatch(uvX, uvY, isHover, isClick, cells, pageOffset)
            if (resolved != previousHover) {
                if (resolved != -1) hapticBridge.triggerHoverFeedback()
                drawAndPushGrid()
            }
        }
    }

    private fun onCellSelected(cell: ImmersiveBrowseCell) {
        Timber.d("S1132: browser cell selected index=${cell.index} folder=${cell.isFolder}")
        hapticBridge.triggerClickFeedback()
        if (cell.isFolder) {
            cell.folderPath?.let { path ->
                folderStack.addLast(path)
                loadContent(path)
            }
            return
        }
        selectMedia(cell)
    }

    private fun onPageScroll(direction: Int) {
        val pageSize = gridRenderer.pageSize
        val next = pageOffset + direction * pageSize
        if (next < 0 || next >= cells.size) return
        hapticBridge.triggerClickFeedback()
        pageOffset = next
        drawAndPushGrid()
        decodeVisibleThumbnails()
    }

    private fun selectMedia(cell: ImmersiveBrowseCell) {
        val path = cell.filePath ?: return
        state = BrowseState.PLAYBACK
        blankGrid()
        if (cell.mediaType == VrMediaType.VIDEO) {
            playbackController.playVideo(toUri(path), cell.label)
        } else {
            lifecycleScope.launch { playbackController.playImage(toLocalFile(path), cell.label) }
        }
    }

    private fun blankGrid() {
        hudCanvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        copyHudToRgba()?.let {
            runtime.queueHud(it, ImmersiveBrowseGridRenderer.PANEL_WIDTH, ImmersiveBrowseGridRenderer.PANEL_HEIGHT)
        }
    }

    private fun onBackRequested() {
        when {
            state == BrowseState.PLAYBACK -> returnToBrowse()
            folderStack.isNotEmpty() -> {
                folderStack.removeLast()
                loadContent(folderStack.lastOrNull())
            }
            else -> renderThread?.requestExit() ?: finish()
        }
    }

    private fun returnToBrowse() {
        playbackController.stop()
        state = BrowseState.BROWSE
        // Re-assert the browse quad: playback used the media quad, and the runtime is process-wide.
        runtime.setHudQuadSize(BROWSE_QUAD_WIDTH_M, BROWSE_QUAD_HEIGHT_M, BROWSE_QUAD_OFFSET_Y_M)
        drawAndPushGrid()
    }

    private fun toUri(path: String): Uri =
        if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)

    private fun toLocalFile(path: String): File =
        if (path.startsWith("file://")) File(Uri.parse(path).path ?: path) else File(path)

    // ---- OpenXR session lifecycle ----

    override fun surfaceCreated(holder: SurfaceHolder) {
        surface = holder.surface
        maybeStartRenderThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surface = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !windowFocusedDeferred.isCompleted) {
            windowFocusedDeferred.complete(Unit)
        }
    }

    private fun maybeStartRenderThread() {
        val activeSurface = surface ?: return
        if (renderThreadStarted) return
        renderThreadStarted = true
        renderThread = DiagnosticXrRenderThread(
            activity = this,
            surface = activeSurface,
            runtime = runtime,
            textureBytes = neutralTexture(),
            textureWidth = INIT_TEXTURE_DIM,
            textureHeight = INIT_TEXTURE_DIM,
            windowFocused = windowFocusedDeferred,
            onSessionReady = { runOnUiThread { onSessionReady() } },
            onExitDelivered = { runOnUiThread { if (!isFinishing) finish() } },
            onStartFailed = { result -> runOnUiThread { onStartFailed(result) } },
        ).also { it.start() }
    }

    private fun onSessionReady() {
        // The XR runtime is a process singleton, so the browser must assert its own HUD quad size or
        // it inherits whatever the previous mode left (the tiny banner) - the "micro-browser" (S1116).
        runtime.setHudQuadSize(BROWSE_QUAD_WIDTH_M, BROWSE_QUAD_HEIGHT_M, BROWSE_QUAD_OFFSET_Y_M)
        Timber.d("S1116: browse HUD quad ${BROWSE_QUAD_WIDTH_M}x${BROWSE_QUAD_HEIGHT_M}m")
        if (state == BrowseState.BROWSE) drawAndPushGrid()
    }

    private fun onStartFailed(result: DiagnosticXrNativeResult) {
        Timber.w("ImmersiveBrowseActivity: render start failed -> %s", result)
        finish()
    }

    private fun neutralTexture(): ByteArray {
        val bytes = ByteArray(INIT_TEXTURE_DIM * INIT_TEXTURE_DIM * RGBA_BYTES_PER_PIXEL)
        for (i in bytes.indices step RGBA_BYTES_PER_PIXEL) {
            bytes[i] = BACKDROP_RED
            bytes[i + 1] = BACKDROP_GREEN
            bytes[i + 2] = BACKDROP_BLUE
            bytes[i + ALPHA_OFFSET] = BACKDROP_ALPHA
        }
        return bytes
    }

    // ---- lifecycle teardown ----

    override fun onDestroy() {
        playbackController.stop()
        thumbnailDecoder.release()
        renderThread?.requestExit()
        super.onDestroy()
    }

    private companion object {
        const val HAND_TRACKING_PERMISSION = "com.oculus.permission.HAND_TRACKING"
        const val REQUEST_HAND_TRACKING = 0x963
        const val RGBA_BYTES_PER_PIXEL = 4
        const val ALPHA_OFFSET = 3
        const val CELL_THUMB_PX = 384

        // S1116: browse HUD quad in metres (2:1). The banner default is 0.30x0.113 m and the player
        // panel is a wide strip since S1228 - the browser needs its own large readable surface, so
        // it asserts this size on session-ready.
        const val BROWSE_QUAD_WIDTH_M = 0.80f
        const val BROWSE_QUAD_HEIGHT_M = 0.40f

        // S1228: the browser is the content being read, not an overlay on it, so it stays centred
        // in view. Only the player strip takes a negative offset.
        const val BROWSE_QUAD_OFFSET_Y_M = 0.0f
        const val INIT_TEXTURE_DIM = 64
        const val NO_RESOURCE = -1L
        const val BACKDROP_RED: Byte = 12
        const val BACKDROP_GREEN: Byte = 12
        const val BACKDROP_BLUE: Byte = 20
        const val BACKDROP_ALPHA: Byte = -1 // 0xFF opaque
    }
}
