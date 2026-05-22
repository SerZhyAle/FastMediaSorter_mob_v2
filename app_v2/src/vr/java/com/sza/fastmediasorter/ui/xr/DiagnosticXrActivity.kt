package com.sza.fastmediasorter.ui.xr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.Keep
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.core.xr.assets.DiagnosticXrAssetProvider
import com.sza.fastmediasorter.core.xr.input.DiagnosticXrInputExitHandler
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrNativeResult
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import com.sza.fastmediasorter.ui.xr.helpers.HudCanvasRenderer
import com.sza.fastmediasorter.ui.xr.helpers.HudInteractionDispatcher
import com.sza.fastmediasorter.ui.xr.helpers.HudPlaybackController
import com.sza.fastmediasorter.ui.xr.helpers.HudHapticBridge
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

enum class ProjectionType(val value: Int) {
    SPHERE_360(0),
    HEMISPHERE_180(1),
    FLAT(2)
}

enum class StereoLayout(val value: Int) {
    MONO(0),
    TOP_BOTTOM(1),
    SIDE_BY_SIDE(2)
}

data class RenderConfig(
    val projection: ProjectionType,
    val layout: StereoLayout
)

/**
 * S0282: dedicated OpenXR session host Activity with dynamic playlist.
 */
@AndroidEntryPoint
class DiagnosticXrActivity : ComponentActivity(), SurfaceHolder.Callback {

    @Inject lateinit var runtime: DiagnosticXrRuntime
    @Inject lateinit var assetProvider: DiagnosticXrAssetProvider
    @Inject lateinit var exitHandler: DiagnosticXrInputExitHandler

    private var renderThread: DiagnosticXrRenderThread? = null
    private lateinit var surfaceView: SurfaceView

    // Dynamic Playlist
    private var mediaPlaylist: List<File> = emptyList()
    private var currentPlaylistIndex: Int = -1

    // Video playback resources
    private var exoPlayer: ExoPlayer? = null
    @Volatile private var sessionReady: Boolean = false

    // VR HUD Helpers
    private lateinit var hudRenderer: HudCanvasRenderer
    private lateinit var interactionDispatcher: HudInteractionDispatcher
    private lateinit var playbackController: HudPlaybackController
    private lateinit var hapticBridge: HudHapticBridge

    // Dynamic HUD Canvas buffers
    private var hudBitmap: Bitmap? = null
    private var hudCanvas: Canvas? = null
    private var hudRgbaBytes: ByteArray = ByteArray(0)

    // Decoded asset, owned by the Activity until handed off to the render thread.
    private var textureBytes: ByteArray = ByteArray(0)
    private var textureWidth: Int = 0
    private var textureHeight: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!runtime.isNativeAvailable) {
            Timber.w("DiagnosticXrActivity: native runtime unavailable, finishing")
            finish(); return
        }

        checkHandTrackingPermission()
    }

    private fun checkHandTrackingPermission() {
        val permission = "com.oculus.permission.HAND_TRACKING"
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                REQUEST_CODE_HAND_TRACKING
            )
        } else {
            proceedWithInitialization()
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_HAND_TRACKING) {
            if (grantResults.isEmpty() || grantResults[0] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Timber.w("DiagnosticXrActivity: com.oculus.permission.HAND_TRACKING denied; hand tracking may not work")
            }
            proceedWithInitialization()
        }
    }

    private fun proceedWithInitialization() {
        // 1. Initialize VR HUD Canvas buffers and helpers (Step 03.2)
        hudRenderer = HudCanvasRenderer()
        hapticBridge = HudHapticBridge(runtime)
        playbackController = HudPlaybackController(exoPlayer, ::navigateToNextMedia, ::navigateToPrevMedia)
        interactionDispatcher = HudInteractionDispatcher(hudRenderer, object : HudInteractionDispatcher.InteractionListener {
            override fun onPlayPauseClick() {
                hapticBridge.triggerClickFeedback()
                hudRenderer.isPlaying = !hudRenderer.isPlaying
                if (hudRenderer.isPlaying) playbackController.play() else playbackController.pause()
            }
            override fun onNextClick() {
                hapticBridge.triggerClickFeedback()
                playbackController.next()
            }
            override fun onPrevClick() {
                hapticBridge.triggerClickFeedback()
                playbackController.prev()
            }
            override fun onVolumeChanged(volume: Float) {
                playbackController.setVolume(volume)
            }
            override fun onDepthChanged(depth: Float) {
                // S0283 Parallax Stereo-depth wiring (0.0 to 1.0 translates to horizontal shift in GLES uniform)
                // Let's store this in a static/dynamic property. The rendering thread can read it or we set it.
                // We will set this on the renderThread directly.
                renderThread?.setParallaxShift(depth)
            }
            override fun onHoverStateChanged(isHovered: Boolean) {
                hapticBridge.triggerHoverFeedback()
            }
        })

        hudBitmap = Bitmap.createBitmap(HudCanvasRenderer.WIDTH, HudCanvasRenderer.HEIGHT, Bitmap.Config.ARGB_8888)
        hudCanvas = Canvas(hudBitmap!!)
        hudRgbaBytes = ByteArray(HudCanvasRenderer.WIDTH * HudCanvasRenderer.HEIGHT * 4)

        // Scan media files
        mediaPlaylist = scanMediaFiles()
        if (mediaPlaylist.isNotEmpty()) {
            currentPlaylistIndex = 0
            val firstFile = mediaPlaylist[0]
            hudRenderer.currentFilename = firstFile.name
            if (firstFile.extension.lowercase() in setOf("jpg", "jpeg", "png")) {
                if (!decodeImageToActivityBytes(firstFile)) {
                    Timber.w("Failed to decode initial image ${firstFile.name}, falling back to bundled")
                    decodeBundledAsset()
                }
            } else {
                // First is a video, use bundled placeholder initially
                decodeBundledAsset()
            }
        } else {
            decodeBundledAsset()
        }

        surfaceView = SurfaceView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            holder.addCallback(this@DiagnosticXrActivity)
        }
        setContentView(surfaceView)
        surfaceView.requestFocus()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.d("DiagnosticXrActivity: onBackPressed -> requesting exit")
                renderThread?.requestExit() ?: finish()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                exitHandler.exitRequested.collect { reason ->
                    Timber.d("exit signalled by $reason")
                    renderThread?.requestExit()
                }
            }
        }
    }

    private fun scanMediaFiles(): List<File> {
        val pictureDir = File("/sdcard/Pictures/FastMediaSorterVrTest")
        val movieDir = File("/sdcard/Movies/FastMediaSorterVrTest")

        val playlist = VR_TEST_MEDIA_ORDER.mapNotNull { filename ->
            val dir = if (isVideoFilename(filename)) movieDir else pictureDir
            File(dir, filename).takeIf { file -> file.isFile }
        }

        Timber.d("DiagnosticXrActivity: VR test playlist contains ${playlist.size} files: ${playlist.joinToString { it.name }}")
        return playlist
    }

    private fun isVideoFilename(filename: String): Boolean {
        val extension = filename.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in VIDEO_EXTENSIONS
    }

    private fun parseFilenameConfig(filename: String): RenderConfig {
        val name = filename.lowercase()
        val projection = when {
            name.contains("_360") || name.contains("360_") -> ProjectionType.SPHERE_360
            name.contains("_180") || name.contains("180_") -> ProjectionType.HEMISPHERE_180
            name.contains("_flat") || name.contains("flat_") -> ProjectionType.FLAT
            else -> {
                if (name.contains("panorama") || name.contains("panoramic") || name.contains("equirectangular")) {
                    ProjectionType.SPHERE_360
                } else {
                    ProjectionType.FLAT
                }
            }
        }
        val layout = when {
            name.contains("_tb") || name.contains("_topbottom") || name.contains("_stereo") || name.contains("stereo_tb") -> StereoLayout.TOP_BOTTOM
            name.contains("_sbs") || name.contains("_sidebyside") -> StereoLayout.SIDE_BY_SIDE
            name.contains("_mono") || name.contains("mono_") -> StereoLayout.MONO
            else -> {
                if (name.contains("stereo")) {
                    StereoLayout.TOP_BOTTOM
                } else {
                    StereoLayout.MONO
                }
            }
        }
        return RenderConfig(projection, layout)
    }

    private fun generateFilenameHudBytes(filename: String): ByteArray {
        val w = 1024
        val h = 128
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        
        val bgPaint = Paint().apply {
            color = Color.argb(160, 10, 10, 15)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val rect = RectF(40f, 20f, (w - 40).toFloat(), (h - 20).toFloat())
        canvas.drawRoundRect(rect, 16f, 16f, bgPaint)
        
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        
        val textBounds = Rect()
        textPaint.getTextBounds(filename, 0, filename.length, textBounds)
        val yOffset = (h / 2) - textBounds.exactCenterY()
        
        canvas.drawText(filename, (w / 2).toFloat(), yOffset, textPaint)
        
        val buf = ByteBuffer.allocateDirect(w * h * 4)
        bitmap.copyPixelsToBuffer(buf)
        buf.rewind()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        bitmap.recycle()
        return bytes
    }

    private fun decodeBundledAsset(): Boolean {
        runtime.setRenderConfig(ProjectionType.SPHERE_360.value, StereoLayout.MONO.value)
        val hudBytes = generateFilenameHudBytes("vr_diagnostic_360_mono.jpg")
        runtime.queueHud(hudBytes, 1024, 128)

        val asset = assetProvider.load() ?: return false
        val bitmap = decodeByteArrayWithOomFallback(asset.bytes) ?: return false
        try {
            val w = bitmap.width
            val h = bitmap.height
            val buf = ByteBuffer.allocateDirect(w * h * 4)
            bitmap.copyPixelsToBuffer(buf)
            buf.rewind()
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            textureBytes = bytes
            textureWidth = w
            textureHeight = h
            Timber.d("decoded bundled mono 360 asset to RGBA: ${w}x${h}, bytes=${bytes.size}")
            return true
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeImageToActivityBytes(file: File): Boolean {
        val config = parseFilenameConfig(file.name)
        runtime.setRenderConfig(config.projection.value, config.layout.value)
        val hudBytes = generateFilenameHudBytes(file.name)
        runtime.queueHud(hudBytes, 1024, 128)

        val bitmap = decodeFileWithOomFallback(file) ?: return false
        try {
            val w = bitmap.width
            val h = bitmap.height
            val buf = ByteBuffer.allocateDirect(w * h * 4)
            bitmap.copyPixelsToBuffer(buf)
            buf.rewind()
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            textureBytes = bytes
            textureWidth = w
            textureHeight = h
            Timber.d("decoded initial image ${file.name} to RGBA: ${w}x${h}")
            return true
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Decode a JPEG byte array as ARGB_8888 with progressive `inSampleSize` fallback on
     * [OutOfMemoryError]. Used for the bundled 8192x4096 equirectangular sphere image and any
     * comparably large playlist source — naive `BitmapFactory.decodeByteArray` would peak around
     * 128 MB heap on a fresh 8K decode and risk OOM on Quest 3 once ExoPlayer buffers are also
     * resident.
     */
    private fun decodeByteArrayWithOomFallback(bytes: ByteArray): Bitmap? {
        var sample = 1
        while (sample <= MAX_DECODE_SAMPLE) {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
                inSampleSize = sample
            }
            try {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (bmp != null) {
                    if (sample > 1) {
                        Timber.w("decodeByteArrayWithOomFallback: succeeded at inSampleSize=$sample")
                    }
                    return bmp
                }
                Timber.w("decodeByteArrayWithOomFallback: decode returned null at inSampleSize=$sample")
                return null
            } catch (oom: OutOfMemoryError) {
                Timber.w(oom, "decodeByteArrayWithOomFallback: OOM at inSampleSize=$sample; retrying with larger sample")
                sample *= 2
            }
        }
        Timber.e("decodeByteArrayWithOomFallback: exhausted sample steps, decode failed")
        return null
    }

    /**
     * File counterpart of [decodeByteArrayWithOomFallback]. User-pushed playlist items may be
     * arbitrarily large; we degrade resolution rather than crash.
     */
    private fun decodeFileWithOomFallback(file: File): Bitmap? {
        var sample = 1
        while (sample <= MAX_DECODE_SAMPLE) {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
                inSampleSize = sample
            }
            try {
                val bmp = BitmapFactory.decodeFile(file.absolutePath, options)
                if (bmp != null) {
                    if (sample > 1) {
                        Timber.w("decodeFileWithOomFallback: ${file.name} succeeded at inSampleSize=$sample")
                    }
                    return bmp
                }
                Timber.w("decodeFileWithOomFallback: ${file.name} decode returned null at inSampleSize=$sample")
                return null
            } catch (oom: OutOfMemoryError) {
                Timber.w(oom, "decodeFileWithOomFallback: OOM on ${file.name} at inSampleSize=$sample; retrying with larger sample")
                sample *= 2
            }
        }
        Timber.e("decodeFileWithOomFallback: exhausted sample steps for ${file.name}")
        return null
    }

    private fun startVideoPlayback(file: File) {
        releasePlaybackResources()
        val videoSurface = runtime.getVideoSurface()
        if (videoSurface == null) {
            Timber.w("DiagnosticXrActivity: native video surface is not ready for ${file.name}")
            hudRenderer.isPlaying = false
            return
        }

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setVideoSurface(videoSurface)
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            val mediaItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file))
            setMediaItem(mediaItem)
            prepare()
        }
        runtime.setVideoSurfaceEnabled(true)
        playbackController.updatePlayer(exoPlayer)
        Timber.d("Started video playback for: ${file.name}")
    }

    private fun releasePlaybackResources() {
        runtime.setVideoSurfaceEnabled(false)
        exoPlayer?.clearVideoSurface()
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        playbackController.updatePlayer(null)
    }

    private fun navigateToNextMedia() {
        if (mediaPlaylist.isEmpty()) return
        currentPlaylistIndex = (currentPlaylistIndex + 1) % mediaPlaylist.size
        loadCurrentMediaItem()
    }

    private fun navigateToPrevMedia() {
        if (mediaPlaylist.isEmpty()) return
        currentPlaylistIndex = if (currentPlaylistIndex - 1 < 0) {
            mediaPlaylist.size - 1
        } else {
            currentPlaylistIndex - 1
        }
        loadCurrentMediaItem()
    }

    private fun loadCurrentMediaItem() {
        val file = mediaPlaylist[currentPlaylistIndex]
        Timber.d("Loading media item at index $currentPlaylistIndex: ${file.name}")
        
        hudRenderer.currentFilename = file.name
        releasePlaybackResources()
        
        val config = parseFilenameConfig(file.name)
        runtime.setRenderConfig(config.projection.value, config.layout.value)
        val hudBytes = generateFilenameHudBytes(file.name)
        runtime.queueHud(hudBytes, 1024, 128)
        
        if (file.extension.lowercase() in setOf("jpg", "jpeg", "png")) {
            runtime.setVideoSurfaceEnabled(false)
            lifecycleScope.launch(Dispatchers.Default) {
                val bitmap = decodeFileWithOomFallback(file)
                if (bitmap != null) {
                    try {
                        val w = bitmap.width
                        val h = bitmap.height
                        val buf = ByteBuffer.allocateDirect(w * h * 4)
                        bitmap.copyPixelsToBuffer(buf)
                        buf.rewind()
                        val bytes = ByteArray(buf.remaining())
                        buf.get(bytes)
                        runtime.queueFrame(bytes, w, h)
                        Timber.d("Loaded and queued image: ${file.name} at ${w}x${h}")
                    } catch (t: Throwable) {
                        Timber.e(t, "Failed to copy bitmap buffer for ${file.name}")
                    } finally {
                        bitmap.recycle()
                    }
                } else {
                    Timber.w("Failed to decode image: ${file.name}")
                }
            }
        } else {
            if (sessionReady) {
                startVideoPlayback(file)
            } else {
                Timber.d("DiagnosticXrActivity: deferring video playback until native session is ready")
            }
        }
    }

    @Keep
    fun onNativeInputEvent(eventType: Int) {
        runOnUiThread {
            if (isFinishing || isDestroyed || mediaPlaylist.isEmpty()) return@runOnUiThread
            when (eventType) {
                1 -> navigateToNextMedia()
                2 -> navigateToPrevMedia()
            }
        }
    }

    @Keep
    fun onNativeRayInteraction(uvX: Float, uvY: Float, isHover: Boolean, isClick: Boolean) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            
            // Dispatch the interaction to virtual Canvas pixels (Step 03.2)
            interactionDispatcher.dispatch(uvX, uvY, isHover, isClick)

            // Calculate current real-time FPS from renderThread if running
            hudRenderer.fps = renderThread?.currentFps ?: 60.0f

            // Redraw HUD Canvas (Step 03.2)
            val bitmap = hudBitmap ?: return@runOnUiThread
            val canvas = hudCanvas ?: return@runOnUiThread
            hudRenderer.render(canvas)

            // Copy to raw RGBA bytes (Step 03.2)
            val buf = ByteBuffer.wrap(hudRgbaBytes)
            buf.rewind()
            bitmap.copyPixelsToBuffer(buf)

            // Upload head-locked texture to C++ (Step 03.2)
            runtime.queueHud(hudRgbaBytes, HudCanvasRenderer.WIDTH, HudCanvasRenderer.HEIGHT)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (renderThread != null) return
        Timber.d("surfaceCreated; starting render thread")
        renderThread = DiagnosticXrRenderThread(
            activity = this,
            surface = holder.surface,
            runtime = runtime,
            textureBytes = textureBytes,
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            onSessionReady = ::onRenderThreadSessionReady,
            onExitDelivered = ::onRenderThreadExit,
            onStartFailed = ::onRenderThreadStartFailed,
        ).also {
            it.start()
            exitHandler.markFirstFramePresented(SystemClock.elapsedRealtime())
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Timber.d("surfaceDestroyed; requesting exit")
        renderThread?.requestExit()
    }

    override fun onPause() {
        super.onPause()
        renderThread?.requestExit()
    }

    override fun onDestroy() {
        renderThread?.requestExit()
        renderThread?.join(SHUTDOWN_TIMEOUT_MS)
        renderThread = null
        releasePlaybackResources()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (exitHandler.onKeyEvent(event, SystemClock.elapsedRealtime())) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (exitHandler.onMotionEvent(event, SystemClock.elapsedRealtime())) return true
        return super.onTouchEvent(event)
    }

    private fun onRenderThreadExit() {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                Timber.d("render thread done; finishing")
                finish()
            }
        }
    }

    private fun onRenderThreadSessionReady() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            sessionReady = true
            if (mediaPlaylist.isEmpty() || currentPlaylistIndex !in mediaPlaylist.indices) return@runOnUiThread
            val file = mediaPlaylist[currentPlaylistIndex]
            if (isVideoFilename(file.name)) {
                val config = parseFilenameConfig(file.name)
                runtime.setRenderConfig(config.projection.value, config.layout.value)
                val hudBytes = generateFilenameHudBytes(file.name)
                runtime.queueHud(hudBytes, 1024, 128)
                startVideoPlayback(file)
            }
        }
    }

    private fun onRenderThreadStartFailed(result: DiagnosticXrNativeResult) {
        Timber.w("render thread start failed: $result")
        runOnUiThread { if (!isFinishing && !isDestroyed) finish() }
    }

    private companion object {
        const val SHUTDOWN_TIMEOUT_MS = 3_000L
        private const val REQUEST_CODE_HAND_TRACKING = 1001
        // Upper bound for inSampleSize escalation in decodeByteArrayWithOomFallback /
        // decodeFileWithOomFallback. 8 means we will not decode below 1/8 of the source side,
        // which for the bundled 8K asset is still 1024x512 — well above usable VR quality.
        private const val MAX_DECODE_SAMPLE = 8
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv")
        private val VR_TEST_MEDIA_ORDER = listOf(
            "diagnostic_360_mono.jpg",
            "diagnostic_360_stereo_tb.jpg",
            "moraine_lake_flat_mono.jpg",
            "colosseum_flat_mono.jpg",
            "video_360_mono.mp4",
            "video_360_stereo_tb.mp4",
            "video_180_stereo_tb.mp4",
            "big_buck_bunny_flat_mono.mp4"
        )
    }
}
