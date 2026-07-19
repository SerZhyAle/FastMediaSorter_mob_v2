package com.sza.fastmediasorter.ui.xr

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.Keep
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.xr.VrLaunchDeliveryMode
import com.sza.fastmediasorter.core.xr.VrLaunchInput
import com.sza.fastmediasorter.core.xr.VrLaunchMode
import com.sza.fastmediasorter.core.xr.VrLaunchPayloadHolder
import com.sza.fastmediasorter.core.xr.VrLaunchResult
import com.sza.fastmediasorter.core.xr.VrLaunchUnavailableReason
import com.sza.fastmediasorter.core.xr.VrMediaType
import com.sza.fastmediasorter.core.xr.VrPanelReturnTarget
import com.sza.fastmediasorter.core.xr.assets.DiagnosticXrAssetProvider
import com.sza.fastmediasorter.core.xr.input.DiagnosticXrInputExitHandler
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrNativeResult
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.StereoDetector
import com.sza.fastmediasorter.ui.player.helpers.PrefetchLoadControlFactory
import com.sza.fastmediasorter.ui.xr.helpers.HudCanvasRenderer
import com.sza.fastmediasorter.ui.xr.helpers.HudHapticBridge
import com.sza.fastmediasorter.ui.xr.helpers.HudInteractionDispatcher
import com.sza.fastmediasorter.ui.xr.helpers.HudPlaybackController
import com.sza.fastmediasorter.ui.xr.helpers.HudTrackController
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

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

/** S0282: dedicated OpenXR session host Activity with dynamic playlist. */
@AndroidEntryPoint
class DiagnosticXrActivity : ComponentActivity(), SurfaceHolder.Callback {

    @Inject lateinit var runtime: DiagnosticXrRuntime
    @Inject lateinit var assetProvider: DiagnosticXrAssetProvider
    @Inject lateinit var exitHandler: DiagnosticXrInputExitHandler
    @Inject lateinit var payloadHolder: VrLaunchPayloadHolder

    // S0771: the immersive renderer must agree with the 2D panel on stereo layout. Reuse the panel
    // player's shared classifier instead of a second, divergent filename parser (see parseFilenameConfig).
    @Inject lateinit var stereoDetector: StereoDetector

    private var renderThread: DiagnosticXrRenderThread? = null
    private lateinit var surfaceView: SurfaceView

    // S0382 Phase 04: 2D loading indicator shown over the SurfaceView while the initial frame
    // decodes off the main thread; removed when the immersive session becomes ready (first frame).
    private var loadingOverlay: View? = null

    // Completed when the Activity gains window focus. Render thread awaits this before calling
    // nativeStartSession so that HzOS has registered the volumetric window by that time.
    // Created once here for the cold start and re-armed on teardown in shutdownRenderThreadSync
    // (NOT in maybeStartRenderThread): a same-Activity re-resume starts a fresh render thread, and
    // without re-arming it would await an already-completed deferred and skip the window-focus wait.
    private var windowFocusedDeferred = CompletableDeferred<Unit>()

    @Volatile private var reusableDirectBuffer: ByteBuffer? = null
    @Volatile private var reusableHudBuffer: ByteBuffer? = null

    @Synchronized
    private fun getReusableDirectBuffer(size: Int): ByteBuffer {
        val current = reusableDirectBuffer
        if (current != null && current.capacity() >= size) {
            current.clear()
            return current
        }
        return try {
            val newBuffer = ByteBuffer.allocateDirect(size)
            reusableDirectBuffer = newBuffer
            newBuffer
        } catch (oom: OutOfMemoryError) {
            Timber.w(oom, "getReusableDirectBuffer: OOM allocating direct buffer of size $size, trying GC...")
            System.gc()
            System.runFinalization()
            ByteBuffer.allocateDirect(size).also { reusableDirectBuffer = it }
        }
    }

    @Synchronized
    private fun getReusableHudBuffer(): ByteBuffer {
        val size = HUD_BANNER_WIDTH * HUD_BANNER_HEIGHT * 4
        val current = reusableHudBuffer
        if (current != null) {
            current.clear()
            return current
        }
        val newBuffer = ByteBuffer.allocateDirect(size)
        reusableHudBuffer = newBuffer
        return newBuffer
    }

    // S0964: panel-sized twin of getReusableHudBuffer (see reusablePanelHudBuffer field note).
    @Synchronized
    private fun getReusablePanelHudBuffer(): ByteBuffer {
        val size = HudCanvasRenderer.WIDTH * HudCanvasRenderer.HEIGHT * RGBA_BYTES_PER_PIXEL
        val current = reusablePanelHudBuffer
        if (current != null) {
            current.clear()
            return current
        }
        val newBuffer = ByteBuffer.allocateDirect(size)
        reusablePanelHudBuffer = newBuffer
        return newBuffer
    }

    /**
     * S0960: single OOM-guarded path for the direct-buffer -> on-heap RGBA copy shared by the
     * bundled, initial-file, and slide-change decodes. The fresh ByteArray below is the exact
     * allocation that crashed on Quest 3 (128 MB against a warm 512 MB heap), so it degrades to
     * null instead of throwing - callers reuse the existing DecoderFailed / skip-frame fallbacks.
     * The direct-buffer intermediate stays: heap ByteBuffer.wrap produced all-zero pixel output
     * on-device (see generateFilenameHudBytes).
     */
    private fun copyBitmapToRgbaBytes(bitmap: Bitmap): ByteArray? {
        val size = bitmap.width * bitmap.height * RGBA_BYTES_PER_PIXEL
        val buf = getReusableDirectBuffer(size)
        bitmap.copyPixelsToBuffer(buf)
        buf.rewind()
        return try {
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            Timber.d("S0960: RGBA copy ok, ${bytes.size} bytes (${bitmap.width}x${bitmap.height})")
            bytes
        } catch (oom: OutOfMemoryError) {
            Timber.e(oom, "copyBitmapToRgbaBytes: heap cannot fit $size bytes; degrading gracefully")
            null
        }
    }

    // Dynamic Playlist
    private var mediaPlaylist: List<File> = emptyList()
    private var currentPlaylistIndex: Int = -1

    // Video playback resources
    private var exoPlayer: ExoPlayer? = null
    @Volatile private var sessionReady: Boolean = false

    // S0382 Phase 04: gates render-thread start until the off-main-thread initial decode has
    // populated the texture buffer. The render thread takes the buffer by value at construction,
    // so starting before this is set could present an empty/black first frame.
    @Volatile private var initialDecodeComplete: Boolean = false

    // VR HUD Helpers
    private lateinit var hudRenderer: HudCanvasRenderer
    private lateinit var interactionDispatcher: HudInteractionDispatcher
    private lateinit var playbackController: HudPlaybackController
    private lateinit var hapticBridge: HudHapticBridge

    // S0964: track rows on the interactive panel (FILE_URI launches only).
    private lateinit var trackController: HudTrackController
    private var hudSubsOffLabel: String = ""
    private var hudNoTracksLabel: String = ""

    // S0964: panel repaints are debounced so a volume-slider drag (per ray-tick callbacks)
    // cannot turn into a per-frame queueHud storm (S0290 rule: state-driven uploads only).
    private val hudRepaintHandler = Handler(Looper.getMainLooper())
    private var hudRepaintScheduled = false

    // S0964: the panel copy buffer is deliberately separate from reusableDirectBuffer - image
    // decodes use that one from Dispatchers.IO while panel repaints run on main, and the buffer
    // CONTENT is written outside the @Synchronized getter, so sharing would race.
    @Volatile private var reusablePanelHudBuffer: ByteBuffer? = null

    // Dynamic HUD Canvas buffers (S0964: RGBA copies go through reusablePanelHudBuffer; the old
    // hudRgbaBytes field from the removed S0290 per-frame path was dead and is gone).
    private var hudBitmap: Bitmap? = null
    private var hudCanvas: Canvas? = null

    // Quest Home may redispatch the panel PendingIntent during immersive teardown. Keep the
    // flat-panel handoff idempotent so one exit cannot spawn multiple SettingsActivity instances.
    private val panelReturnDispatched = AtomicBoolean(false)
    private lateinit var launchInput: VrLaunchInput
    private lateinit var returnTarget: VrPanelReturnTarget
    private var exitResult: VrLaunchResult = VrLaunchResult.CancelledByUser

    // Decoded asset, owned by the Activity until handed off to the render thread.
    private var textureBytes: ByteArray = ByteArray(0)
    private var textureWidth: Int = 0
    private var textureHeight: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // S0295 Phase 02: shared transport parsing now lives in DiagnosticXrLaunchArgs so the
        // host stays focused on OpenXR session lifecycle rather than intent plumbing.
        when (val parsed = DiagnosticXrLaunchArgs.parse(
            intent,
            payloadHolder = payloadHolder,
            defaultReturnTarget = VrPanelReturnTarget.Settings(MEDIA_SETTINGS_TAB_INDEX),
        )) {
            is DiagnosticXrLaunchArgs.PreflightFailure -> {
                Timber.w("DiagnosticXrActivity: preflight failure ${parsed.unavailable.reason}")
                launchInput = VrLaunchInput(
                    launchMode = VrLaunchMode.DIAGNOSTIC_PLAYLIST,
                    mediaType = VrMediaType.IMAGE,
                )
                returnTarget = VrPanelReturnTarget.Settings(MEDIA_SETTINGS_TAB_INDEX)
                deliverReturnAndFinish(parsed.unavailable)
                return
            }
            is DiagnosticXrLaunchArgs.Parsed -> {
                launchInput = parsed.input
                returnTarget = parsed.returnTarget
            }
        }

        if (!runtime.isNativeAvailable) {
            Timber.w("DiagnosticXrActivity: native runtime unavailable, finishing")
            deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NoRuntime))
            return
        }

        if (!prepareLaunchMedia()) {
            return
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
        permissions: Array<String>,
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
        // S0964: track rows mirror the 2D video dialog composition on top of the shared
        // VideoTrackSelectionManager primitives (epic S0773 ADR-3).
        trackController = HudTrackController { exoPlayer }
        hudSubsOffLabel = getString(R.string.vr_hud_subs_off)
        hudNoTracksLabel = getString(R.string.vr_hud_no_tracks)
        hudRenderer.audioCaption = getString(R.string.vr_hud_audio_label)
        hudRenderer.subsCaption = getString(R.string.vr_hud_subs_label)
        hudRenderer.audioTrackLabel = hudNoTracksLabel
        hudRenderer.subtitleTrackLabel = hudNoTracksLabel
        hudRenderer.prevLabel = getString(R.string.vr_hud_prev)
        hudRenderer.playLabel = getString(R.string.vr_hud_play)
        hudRenderer.pauseLabel = getString(R.string.vr_hud_pause)
        hudRenderer.nextLabel = getString(R.string.vr_hud_next)
        hudRenderer.volumeCaption = getString(R.string.vr_hud_volume_label)
        hudRenderer.depthCaption = getString(R.string.vr_hud_depth_label)
        val hudInteractionListener = object : HudInteractionDispatcher.InteractionListener {
            override fun onPlayPauseClick() {
                hapticBridge.triggerClickFeedback()
                hudRenderer.isPlaying = !hudRenderer.isPlaying
                if (hudRenderer.isPlaying) playbackController.play() else playbackController.pause()
                scheduleHudPanelRepaint()
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
                scheduleHudPanelRepaint()
            }
            override fun onDepthChanged(depth: Float) {
                // S0283 Parallax Stereo-depth wiring (0.0 to 1.0 translates to horizontal shift in GLES uniform) Let's store this in a static/dynamic property. The rendering thread can read it or we set it. We will set this on the renderThread directly.
                renderThread?.setParallaxShift(depth)
                scheduleHudPanelRepaint()
            }
            override fun onHoverStateChanged(isHovered: Boolean) {
                hapticBridge.triggerHoverFeedback()
            }
            override fun onAudioTrackCycle(step: Int) {
                Timber.d("S0964: audio track cycle step=$step")
                hapticBridge.triggerClickFeedback()
                trackController.cycleAudio(step) { refreshTrackRowsAndRepaint() }
            }
            override fun onSubtitleTrackCycle(step: Int) {
                Timber.d("S0964: subtitle track cycle step=$step")
                hapticBridge.triggerClickFeedback()
                trackController.cycleSubtitle(step) { refreshTrackRowsAndRepaint() }
            }
        }
        interactionDispatcher = HudInteractionDispatcher(hudRenderer, hudInteractionListener)

        hudBitmap = Bitmap.createBitmap(HudCanvasRenderer.WIDTH, HudCanvasRenderer.HEIGHT, Bitmap.Config.ARGB_8888)
        hudCanvas = Canvas(hudBitmap!!)

        surfaceView = SurfaceView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            holder.addCallback(this@DiagnosticXrActivity)
        }
        val contentRoot = FrameLayout(this).apply {
            addView(
                surfaceView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        setContentView(contentRoot)
        surfaceView.requestFocus()
        showInitialLoadingOverlay(contentRoot)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.d("DiagnosticXrActivity: onBackPressed -> requesting exit")
                renderThread?.requestExit() ?: deliverReturnAndFinish(VrLaunchResult.CancelledByUser)
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

        // S0382 Phase 04: the initial frame is decoded off the main thread; the render thread is
        // gated on initialDecodeComplete so the first render never starts with an empty buffer.
        lifecycleScope.launch {
            if (!prepareInitialFrame()) return@launch
            initialDecodeComplete = true
            maybeStartRenderThread("initialDecodeComplete")
        }
    }

    /**
     * S0382 Phase 04: off-main-thread initial frame preparation. Tries to decode the first
     * playlist image; on failure falls back to the bundled asset, mirroring the prior synchronous
     * decision tree. Returns false only when a terminal failure path has already delivered a
     * return result and finished the Activity, so the caller must not start the render thread.
     */
    private suspend fun prepareInitialFrame(): Boolean {
        val firstFile = mediaPlaylist.firstOrNull()
        if (firstFile != null && firstFile.extension.lowercase() in setOf("jpg", "jpeg", "png")) {
            if (decodeImageToActivityBytes(firstFile)) return true
            if (launchInput.launchMode == VrLaunchMode.FILE_URI) {
                Timber.w("Failed to decode initial launch image ${firstFile.name}, returning DecoderFailed")
                deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.DecoderFailed))
                return false
            }
            Timber.w("Failed to decode initial image ${firstFile.name}, falling back to bundled")
        }
        if (decodeBundledAsset()) return true
        Timber.w("Failed to decode bundled fallback asset; returning DecoderFailed")
        deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.DecoderFailed))
        return false
    }

    /**
     * S0382 Phase 04 (§6.2, owner via /ui-clarify): show a 2D loading indicator over the
     * SurfaceView while the initial frame decodes off the main thread. Removed by
     * [dismissInitialLoadingOverlay] when the immersive session becomes ready (first frame).
     */
    private fun showInitialLoadingOverlay(root: FrameLayout) {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            setBackgroundColor(Color.argb(220, 8, 8, 16))
            addView(
                ProgressBar(this@DiagnosticXrActivity).apply { isIndeterminate = true },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                TextView(this@DiagnosticXrActivity).apply {
                    text = getString(R.string.vr_immersive_preparing)
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setPadding(0, 32, 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            // CLAUDE.md Rule 18: keep the indicator inside system-bar / cutout safe bounds.
            applySystemBarInsetPadding()
        }
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        loadingOverlay = overlay
    }

    private fun dismissInitialLoadingOverlay() {
        val overlay = loadingOverlay ?: return
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        loadingOverlay = null
    }

    private fun prepareLaunchMedia(): Boolean {
        // S0296 Phase 02 step 02.2: VIDEO is now supported in immerse; GIF remains out of scope and short-circuits.
        if (launchInput.launchMode == VrLaunchMode.FILE_URI &&
            launchInput.mediaType == VrMediaType.GIF) {
            Timber.w("DiagnosticXrActivity: mediaType=${launchInput.mediaType} not yet supported in immerse")
            deliverReturnAndFinish(
                VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NotYetSupported)
            )
            return false
        }
        mediaPlaylist = when (launchInput.launchMode) {
            VrLaunchMode.DIAGNOSTIC_PLAYLIST -> scanMediaFiles()
            VrLaunchMode.FILE_URI -> {
                val launchFile = resolveSingleLaunchFile(launchInput)
                if (launchFile == null) {
                    Timber.w("DiagnosticXrActivity: invalid launch fileUriString=%s", launchInput.fileUriString)
                    deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.InvalidUri))
                    return false
                }
                listOf(launchFile)
            }
            // S0963: RESOURCE_BROWSE is owned by ImmersiveBrowseActivity; the gateway never routes it
            // here. Reject defensively rather than scanning the diagnostic test folder.
            VrLaunchMode.RESOURCE_BROWSE -> {
                Timber.w("DiagnosticXrActivity: RESOURCE_BROWSE not handled by the diagnostic host")
                deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NotYetSupported))
                return false
            }
        }
        currentPlaylistIndex = if (mediaPlaylist.isNotEmpty()) 0 else -1
        return true
    }

    private fun resolveSingleLaunchFile(input: VrLaunchInput): File? {
        val uriString = runCatching { input.requireFileUriString() }.getOrNull() ?: return null
        val uri = Uri.parse(uriString)
        val file = when {
            uri.scheme == "file" -> uri.path?.let(::File)
            uri.scheme.isNullOrBlank() -> File(uriString)
            "content" == uri.scheme -> {
                if (input.mediaType == VrMediaType.IMAGE) {
                    resolveContentUriToCacheFile(uri)
                } else {
                    null
                }
            }
            else -> null
        }
        return file?.takeIf { it.isFile }
    }

    /** S0295 Phase 02 step 02.3: when the launch URI is a `content://` resource (the only realistic shape coming from the badge / overflow callers because they hand us a `MediaStore`-backed URI), drain it through `contentResolver.openInputStream` into a cache file the existing decode path can open as a [File]. Returns null on any IO error so the caller can surface a typed `Unavailable(InvalidUri)`. */
    private fun resolveContentUriToCacheFile(uri: Uri): File? {
        return runCatching {
            val cacheFile = File(cacheDir, "vr_immerse_launch_${SystemClock.elapsedRealtime()}.bin")
            contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null
            cacheFile
        }.onFailure {
            Timber.w(it, "DiagnosticXrActivity: failed to materialise content URI $uri to cache")
        }.getOrNull()
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

        // S0771: route through StereoDetector first so the immersive renderer and the 2D panel agree
        // on stereo layout. The prior local-only parser lacked the 3dh/3dv/tab/hou tokens and rendered
        // side-by-side films (e.g. *_180x180_3dh.mp4) as MONO. UNKNOWN falls through to the legacy
        // token scan below so no previously-recognised name regresses.
        val detected = stereoDetector.detectFromFilename(filename).toRenderConfigOrNull()
        val config = detected ?: RenderConfig(
            projection = when {
                name.contains("_360") || name.contains("360_") -> ProjectionType.SPHERE_360
                name.contains("_180") || name.contains("180_") -> ProjectionType.HEMISPHERE_180
                name.contains("_flat") || name.contains("flat_") -> ProjectionType.FLAT
                else -> if (
                    name.contains("panorama") || name.contains("panoramic") ||
                        name.contains("equirectangular")
                ) {
                    ProjectionType.SPHERE_360
                } else {
                    ProjectionType.FLAT
                }
            },
            // S0290 (owner feedback 2026-05-22): SPECIFIC markers (_sbs, _tb, _lr, _ou) MUST be
            // checked BEFORE the generic `_stereo` fallback. The old order matched `_stereo` first
            // and routed `video_360_stereo_sbs.mp4` to TOP_BOTTOM. Order: SBS family, then TB
            // family, then explicit MONO marker, then generic `_stereo` defaults to TB.
            layout = when {
                // Side-by-side family (specific markers, capture-oriented and renderer-oriented).
                name.contains("_sbs") || name.contains("_sidebyside") || name.contains("_hsbs") ||
                    name.contains("_fsbs") || name.contains("_lr") || name.contains("_rl") ->
                        StereoLayout.SIDE_BY_SIDE
                // Top-bottom family (specific markers).
                name.contains("_tb") || name.contains("_topbottom") || name.contains("_ou") ||
                    name.contains("_overunder") || name.contains("stereo_tb") ->
                        StereoLayout.TOP_BOTTOM
                // Explicit mono marker wins over generic `_stereo` fallback below.
                name.contains("_mono") || name.contains("mono_") -> StereoLayout.MONO
                // Generic `_stereo` with no specific layout marker defaults to TB (industry default).
                name.contains("_stereo") || name.contains("stereo") -> StereoLayout.TOP_BOTTOM
                else -> StereoLayout.MONO
            },
        )
        Timber.d(
            "parseFilenameConfig: $filename -> projection=${config.projection}, layout=${config.layout} " +
                "(source=${if (detected != null) "stereo-detector" else "legacy"})"
        )
        Timber.d(
            "S0771: immersive stereo resolved layout=${config.layout} " +
                "source=${if (detected != null) "stereo-detector" else "legacy"} file=$filename"
        )
        // S1112: verify TB (_stereo_tb) content resolves to TOP_BOTTOM, not SIDE_BY_SIDE.
        Timber.d("S1112: $filename -> proj=${config.projection} layout=${config.layout}")
        return config
    }

    /**
     * S0771: map the shared [StereoDetector] verdict onto the immersive renderer's projection/layout
     * pair. Returns null for [StereoMode.UNKNOWN] so the caller keeps the legacy filename scan. The XR
     * projection enum has no fisheye type, so [StereoMode.VR180_FISHEYE_SBS] uses [ProjectionType.HEMISPHERE_180]
     * (equirect hemisphere) - the same projection the legacy parser already chose for 180 content; only
     * the stereo layout is corrected here.
     */
    private fun StereoMode.toRenderConfigOrNull(): RenderConfig? = when (this) {
        StereoMode.VR180_FISHEYE_SBS,
        StereoMode.EQUIRECT_180_SBS -> RenderConfig(ProjectionType.HEMISPHERE_180, StereoLayout.SIDE_BY_SIDE)
        StereoMode.EQUIRECT_180_MONO,
        StereoMode.CYLINDER_180 -> RenderConfig(ProjectionType.HEMISPHERE_180, StereoLayout.MONO)
        StereoMode.EQUIRECT_360_SBS -> RenderConfig(ProjectionType.SPHERE_360, StereoLayout.SIDE_BY_SIDE)
        StereoMode.EQUIRECT_360_OU -> RenderConfig(ProjectionType.SPHERE_360, StereoLayout.TOP_BOTTOM)
        StereoMode.EQUIRECT_360_MONO -> RenderConfig(ProjectionType.SPHERE_360, StereoLayout.MONO)
        StereoMode.SBS_FULL,
        StereoMode.SBS_HALF -> RenderConfig(ProjectionType.FLAT, StereoLayout.SIDE_BY_SIDE)
        StereoMode.OU -> RenderConfig(ProjectionType.FLAT, StereoLayout.TOP_BOTTOM)
        StereoMode.MONO -> RenderConfig(ProjectionType.FLAT, StereoLayout.MONO)
        // AUTO/UNKNOWN are inconclusive - defer to the legacy filename scan in the caller.
        StereoMode.AUTO,
        StereoMode.UNKNOWN -> null
    }

    private fun queueErrorHud(filename: String, errorMsg: String) {
        val bytes = generateErrorHudBytes(filename, errorMsg)
        runtime.queueHud(bytes, HUD_BANNER_WIDTH, HUD_BANNER_HEIGHT)
    }

    private fun generateErrorHudBytes(filename: String, errorMsg: String): ByteArray {
        val w = HUD_BANNER_WIDTH
        val h = HUD_BANNER_HEIGHT
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark red opaque background for warning / error
        canvas.drawColor(Color.argb(255, 139, 0, 0))

        // Rounded panel inside the banner
        val bgPaint = Paint().apply {
            color = Color.argb(255, 60, 0, 0)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val rect = RectF(12f, 10f, (w - 12).toFloat(), (h - 10).toFloat())
        canvas.drawRoundRect(rect, 18f, 18f, bgPaint)

        // Filename on the left, monospace bold
        val namePaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        // Error msg on the right, bold yellow accent
        val errorPaint = Paint().apply {
            color = Color.YELLOW
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val truncated = if (filename.length > 32) filename.take(30) + ".." else filename
        val nameBounds = Rect()
        namePaint.getTextBounds(truncated, 0, truncated.length, nameBounds)
        val nameY = (h / 2f) - nameBounds.exactCenterY()
        canvas.drawText(truncated, 36f, nameY, namePaint)

        val errorLabel = "ERROR: $errorMsg"
        val errorBounds = Rect()
        errorPaint.getTextBounds(errorLabel, 0, errorLabel.length, errorBounds)
        val errorY = (h / 2f) - errorBounds.exactCenterY()
        canvas.drawText(errorLabel, (w - 36).toFloat(), errorY, errorPaint)

        val buf = getReusableHudBuffer()
        bitmap.copyPixelsToBuffer(buf)
        buf.rewind()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        bitmap.recycle()
        return bytes
    }

    /** S0290 (owner feedback round 3 2026-05-22): the previous attempt to render the full [HudCanvasRenderer] panel as the always-on HUD produced an invisible result on Quest 3 (user reported "I see no HUD"). The simple 1024x128 strip used pre-refactor was visible. This restores that working path and additionally includes the resolved projection and stereo layout next to the filename so the operator can see what the parser decided. */
    private fun queueFilenameHud(filename: String, projection: ProjectionType, layout: StereoLayout) {
        Timber.d("S0961: filename HUD queued file=$filename proj=$projection layout=$layout")
        val bytes = generateFilenameHudBytes(filename, projection, layout)
        runtime.queueHud(bytes, HUD_BANNER_WIDTH, HUD_BANNER_HEIGHT)
    }

    private fun generateFilenameHudBytes(filename: String, projection: ProjectionType, layout: StereoLayout): ByteArray {
        val w = HUD_BANNER_WIDTH
        val h = HUD_BANNER_HEIGHT
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark opaque background - solid black w/ slight alpha so any wrong sampling stays diagnosable in the headset. Full-banner fill (not just rounded rect) so the entire 1024x128 texture has known pixels.
        canvas.drawColor(Color.argb(220, 8, 8, 16))

        // Rounded panel inside the banner - gives the HUD a clean edge.
        val bgPaint = Paint().apply {
            color = Color.argb(255, 12, 16, 30)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val rect = RectF(12f, 10f, (w - 12).toFloat(), (h - 10).toFloat())
        canvas.drawRoundRect(rect, 18f, 18f, bgPaint)

        // Filename on the left, large white monospace bold.
        val namePaint = Paint().apply {
            color = Color.WHITE
            textSize = 46f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        // Projection/Layout config on the right, smaller cyan-ish accent.
        val configPaint = Paint().apply {
            color = Color.argb(255, 140, 210, 255)
            textSize = 38f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val truncated = if (filename.length > 32) filename.take(30) + ".." else filename
        val nameBounds = Rect()
        namePaint.getTextBounds(truncated, 0, truncated.length, nameBounds)
        val nameY = (h / 2f) - nameBounds.exactCenterY()
        canvas.drawText(truncated, 36f, nameY, namePaint)

        val configLabel = "${projectionLabel(projection)} ${layoutLabel(layout)}"
        val configBounds = Rect()
        configPaint.getTextBounds(configLabel, 0, configLabel.length, configBounds)
        val configY = (h / 2f) - configBounds.exactCenterY()
        canvas.drawText(configLabel, (w - 36).toFloat(), configY, configPaint)

        // Use a DIRECT ByteBuffer (allocateDirect) - Bitmap.copyPixelsToBuffer is reliable with direct buffers; the previous ByteBuffer.wrap(ByteArray) heap-buffer path produced all-zero output (confirmed in logcat 16:29 round 2).
        val buf = getReusableHudBuffer()
        bitmap.copyPixelsToBuffer(buf)
        buf.rewind()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        bitmap.recycle()
        Timber.d("HUD bytes ${bytes.size}; first pixel R=${bytes[0].toInt() and 0xFF} G=${bytes[1].toInt() and 0xFF} B=${bytes[2].toInt() and 0xFF} A=${bytes[3].toInt() and 0xFF}; filename=$filename layout=${projection.name}/${layout.name}")
        return bytes
    }

    private fun projectionLabel(p: ProjectionType): String = when (p) {
        ProjectionType.SPHERE_360 -> "360"
        ProjectionType.HEMISPHERE_180 -> "180"
        ProjectionType.FLAT -> "FLAT"
    }

    private fun layoutLabel(l: StereoLayout): String = when (l) {
        StereoLayout.MONO -> "MONO"
        StereoLayout.TOP_BOTTOM -> "TB"
        StereoLayout.SIDE_BY_SIDE -> "SBS"
    }

    /** S0964: true when this launch shows the interactive panel HUD instead of the banner. */
    private fun isPanelHudMode(): Boolean = launchInput.launchMode == VrLaunchMode.FILE_URI

    /**
     * S0964: paint the interactive panel (nav, volume, depth, track rows) into the HUD quad.
     * Called on state changes only - never per frame (S0290). Main thread.
     */
    private fun renderPanelHud() {
        if (isFinishing || isDestroyed) return
        val bitmap = hudBitmap
        val canvas = hudCanvas
        if (bitmap == null || canvas == null) return
        hudRenderer.render(canvas)
        val buf = getReusablePanelHudBuffer()
        bitmap.copyPixelsToBuffer(buf)
        buf.rewind()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        Timber.d("S0964: panel HUD queued file=${hudRenderer.currentFilename}")
        runtime.queueHud(bytes, HudCanvasRenderer.WIDTH, HudCanvasRenderer.HEIGHT)
    }

    /** S0964: pull current track labels/availability from the player into the panel model. */
    private fun refreshTrackRows() {
        hudRenderer.audioTrackLabel = trackController.audioLabel(hudNoTracksLabel)
        hudRenderer.subtitleTrackLabel = trackController.subtitleLabel(hudSubsOffLabel, hudNoTracksLabel)
        hudRenderer.audioRowEnabled = trackController.hasMultipleAudioTracks()
        hudRenderer.subsRowEnabled = trackController.hasSubtitleTracks()
    }

    private fun refreshTrackRowsAndRepaint() {
        refreshTrackRows()
        scheduleHudPanelRepaint()
    }

    /** S0964: debounced panel repaint; no-op on the banner (diagnostic) path. */
    private fun scheduleHudPanelRepaint() {
        if (!isPanelHudMode() || hudRepaintScheduled) return
        hudRepaintScheduled = true
        hudRepaintHandler.postDelayed({
            hudRepaintScheduled = false
            renderPanelHud()
        }, HUD_REPAINT_DEBOUNCE_MS)
    }

    private suspend fun decodeBundledAsset(): Boolean {
        runtime.setRenderConfig(ProjectionType.SPHERE_360.value, StereoLayout.MONO.value)
        hudRenderer.currentFilename = "vr_diagnostic_360_mono.jpg (bundled)"
        if (isPanelHudMode()) {
            renderPanelHud()
        } else {
            queueFilenameHud("vr_diagnostic_360_mono.jpg (bundled)", ProjectionType.SPHERE_360, StereoLayout.MONO)
        }

        // S0382 Phase 04 / ADR-1: decode and the large RGBA copy run on Dispatchers.IO so the
        // focus transition is never blocked on the main thread (reverses the prior S0290 Phase 11
        // assumption that this initial-load jank went unnoticed; Quest 3 logs disproved it).
        return withContext(Dispatchers.IO) {
            val bitmap = decodeBundledPooled() ?: return@withContext false
            try {
                val bytes = copyBitmapToRgbaBytes(bitmap)
                if (bytes == null) {
                    false
                } else {
                    textureBytes = bytes
                    textureWidth = bitmap.width
                    textureHeight = bitmap.height
                    Timber.d("decoded bundled mono 360 asset: ${bitmap.width}x${bitmap.height}")
                    true
                }
            } finally {
                // S0290 Phase 11: return bitmap to Glide pool so the second session reuses the
                // budget-sampled ARGB_8888 allocation instead of re-allocating. Pool drives recycle by LRU.
                returnToPool(bitmap)
            }
        }
    }

    private suspend fun decodeImageToActivityBytes(file: File): Boolean {
        val config = parseFilenameConfig(file.name)
        runtime.setRenderConfig(config.projection.value, config.layout.value)
        hudRenderer.currentFilename = file.name
        if (isPanelHudMode()) {
            renderPanelHud()
        } else {
            queueFilenameHud(file.name, config.projection, config.layout)
        }

        // S0382 Phase 04 / ADR-1: decode + RGBA copy off the main thread (see decodeBundledAsset).
        return withContext(Dispatchers.IO) {
            val bitmap = decodeFilePooled(file) ?: return@withContext false
            try {
                val bytes = copyBitmapToRgbaBytes(bitmap)
                if (bytes == null) {
                    false
                } else {
                    textureBytes = bytes
                    textureWidth = bitmap.width
                    textureHeight = bitmap.height
                    Timber.d("decoded initial image ${file.name}: ${bitmap.width}x${bitmap.height}")
                    true
                }
            } finally {
                returnToPool(bitmap)
            }
        }
    }

    /**
     * S0290 Phase 11 Step 11.1 / ADR-5 v2 + S0960: decode the bundled equirectangular JPEG via
     * Glide BitmapPool on Dispatchers.IO. The Glide pool is LRU, thread-safe, and already in the
     * project. S0960: the bundled asset now goes through the same [pickSampleSizeForBudget]
     * preflight as external files (8192x4096 exceeds the 96 MB budget -> inSampleSize=2 ->
     * 4096x2048 = 32 MB) - the previous full-size 128 MB decode plus its equally sized on-heap
     * RGBA copy OOMed a warm 512 MB heap on Quest 3. On OOM with `inBitmap` (pool entry too
     * small / GC pressure), retry with a doubled sample size.
     */
    private suspend fun decodeBundledPooled(): Bitmap? = withContext(Dispatchers.IO) {
        val sample = pickSampleSizeForBudget(BUNDLED_WIDTH, BUNDLED_HEIGHT)
        val sampledW = BUNDLED_WIDTH / sample
        val sampledH = BUNDLED_HEIGHT / sample
        Timber.d("S0960: bundled decode preflight sample=$sample -> ${sampledW}x${sampledH}")
        val pool = Glide.get(this@DiagnosticXrActivity).bitmapPool
        val reusable = pool.getDirty(sampledW, sampledH, Bitmap.Config.ARGB_8888)
        val opts = BitmapFactory.Options().apply {
            inBitmap = reusable
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sample
        }
        try {
            resources.openRawResource(R.drawable.vr_diagnostic_360_mono).use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (oom: OutOfMemoryError) {
            Timber.w(oom, "VR bundled asset decode OOM with inBitmap; retry with inSampleSize=${sample * 2}")
            opts.inBitmap = null
            opts.inSampleSize = sample * 2
            try {
                resources.openRawResource(R.drawable.vr_diagnostic_360_mono).use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            } catch (oom2: OutOfMemoryError) {
                Timber.e(oom2, "VR bundled asset decode OOM even with inSampleSize=${opts.inSampleSize}; giving up")
                null
            }
        }
    }

    /**
     * S0290 Phase 11 Step 11.1: external-file counterpart of [decodeBundledPooled]. Uses
     * `inJustDecodeBounds` preflight to discover dimensions, picks an [inSampleSize] that keeps
     * the ARGB_8888 footprint under [MAX_DECODE_BYTES], then asks the Glide pool for a matching
     * reusable bitmap. Bounds-driven preflight avoids the first OOM that the original
     * implementation took before falling back - observed on Quest 3 with
     * `moraine_lake_flat_mono.jpg` (7742x5327 = 165 MB) which crashed `BitmapFactory` before the
     * catch ran.
     */
    private suspend fun decodeFilePooled(file: File): Bitmap? = withContext(Dispatchers.IO) {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
            Timber.w("decodeFilePooled: bounds preflight failed for ${file.name}")
            return@withContext null
        }

        val preflightSample = pickSampleSizeForBudget(boundsOpts.outWidth, boundsOpts.outHeight)
        val sampledW = boundsOpts.outWidth / preflightSample
        val sampledH = boundsOpts.outHeight / preflightSample
        if (preflightSample > 1) {
            Timber.i(
                "decodeFilePooled: ${file.name} bounds ${boundsOpts.outWidth}x${boundsOpts.outHeight}" +
                    " exceeds ${MAX_DECODE_BYTES / (1024 * 1024)} MB budget;" +
                    " preflight inSampleSize=$preflightSample -> ${sampledW}x${sampledH}"
            )
        }

        val pool = Glide.get(this@DiagnosticXrActivity).bitmapPool
        val reusable = pool.getDirty(sampledW, sampledH, Bitmap.Config.ARGB_8888)
        val opts = BitmapFactory.Options().apply {
            inBitmap = reusable
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = preflightSample
        }
        try {
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (oom: OutOfMemoryError) {
            Timber.w(oom, "decodeFilePooled: ${file.name} OOM with inBitmap; retry with inSampleSize=${preflightSample * 2}")
            opts.inBitmap = null
            opts.inSampleSize = preflightSample * 2
            try {
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (oom2: OutOfMemoryError) {
                Timber.e(oom2, "decodeFilePooled: ${file.name} OOM even with inSampleSize=${opts.inSampleSize}; giving up")
                null
            }
        } catch (iae: IllegalArgumentException) {
            // BitmapFactory throws IllegalArgumentException ("Problem decoding into existing bitmap")
            // when the pool-supplied inBitmap is incompatible with the actual decoded dimensions or
            // config (e.g. moraine_lake_flat_mono.jpg 7742x5327 after inSampleSize may require a
            // different stride). Retry without inBitmap so the decoder allocates a fresh buffer.
            Timber.w(iae, "decodeFilePooled: ${file.name} inBitmap incompatible; retry without pool reuse")
            opts.inBitmap = null
            try {
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (oom3: OutOfMemoryError) {
                Timber.e(oom3, "decodeFilePooled: ${file.name} OOM on inBitmap-free retry; giving up")
                null
            }
        }
    }

    /**
     * Picks the smallest power-of-2 sample size such that the ARGB_8888 footprint of the
     * resulting bitmap is at most [MAX_DECODE_BYTES]. Capped at 8 - beyond that the picture is
     * below usable VR-quality anyway and we surface the failure.
     */
    private fun pickSampleSizeForBudget(width: Int, height: Int): Int {
        var sample = 1
        var bytes = width.toLong() * height.toLong() * 4L
        while (bytes > MAX_DECODE_BYTES && sample < 8) {
            sample *= 2
            bytes /= 4
        }
        return sample
    }

    /** S0290 Phase 11 Step 11.2: return a bitmap to the Glide pool so the next decode of matching dimensions/Config reuses this allocation. Do NOT call `bitmap.recycle()` - Glide handles LRU eviction internally. */
    private fun returnToPool(bitmap: Bitmap) {
        runCatching { Glide.get(this).bitmapPool.put(bitmap) }
            .onFailure { Timber.w(it, "returnToPool: bitmapPool.put threw; falling back to recycle") }
            .onFailure { bitmap.recycle() }
    }

    private fun startVideoPlayback(file: File): Boolean {
        releasePlaybackResources()
        val videoSurface = runtime.getVideoSurface()
        if (videoSurface == null) {
            Timber.w("DiagnosticXrActivity: native video surface is not ready for ${file.name}")
            hudRenderer.isPlaying = false
            return false
        }
        val snapshot = launchInput.snapshot
        // S1113: immersive playback was on the DEFAULT LoadControl (no S0772 heap cap), so a 7K
        // HEVC stream over-buffers on the 512 MB-heap headset and stalls (blank quad). Apply the
        // same heap-bounded LoadControl the flat player uses.
        val vrPlayer = ExoPlayer.Builder(this)
            .setLoadControl(PrefetchLoadControlFactory.build(plan = null, tag = "vr-diagnostic"))
            .build()
        exoPlayer = vrPlayer.apply {
            setVideoSurface(videoSurface)
            repeatMode = Player.REPEAT_MODE_ALL
            
            addListener(object : Player.Listener {
                // S1113: confirm the 7K immersive decoder produces an output size (and thus frames).
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    Timber.d("S1113: video size ${videoSize.width}x${videoSize.height} file=${file.name}")
                }

                override fun onPlayerError(error: PlaybackException) {
                    val cause = error.cause
                    val stage = when {
                        error.errorCode in 2000..2999 -> "source preparation"
                        error.errorCode in 4001..4002 || error.errorCode == 4004 -> "decoder initialization"
                        error.errorCode == 4003 -> "render"
                        else -> {
                            val exoEx = error as? ExoPlaybackException
                            when (exoEx?.type) {
                                ExoPlaybackException.TYPE_SOURCE -> "source preparation"
                                ExoPlaybackException.TYPE_RENDERER -> "decoder initialization / render"
                                else -> "unknown stage"
                            }
                        }
                    }

                    val decoderDetails = StringBuilder()
                    if (cause is androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException) {
                        decoderDetails.append("codec=${cause.codecInfo?.name ?: "null"}, ")
                        decoderDetails.append("mime=${cause.mimeType ?: "null"}, ")
                        decoderDetails.append("diag=${cause.diagnosticInfo ?: "null"}")
                    } else if (cause != null) {
                        decoderDetails.append("cause=${cause.javaClass.simpleName}: ${cause.message}")
                        cause.cause?.let { inner ->
                            decoderDetails.append(" -> ${inner.javaClass.simpleName}: ${inner.message}")
                        }
                    } else {
                        decoderDetails.append("no cause info")
                    }

                    Timber.e(error, "VR diagnostic playback failed! File: ${file.name}, Stage: $stage, Code: ${error.errorCode} (${error.errorCodeName}), Msg: ${error.message}, $decoderDetails")

                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            val shortErr = "${error.errorCodeName} ($stage)"
                            queueErrorHud(file.name, shortErr)

                            android.widget.Toast.makeText(
                                this@DiagnosticXrActivity,
                                "Playback Error: $shortErr",
                                android.widget.Toast.LENGTH_LONG
                            ).show()

                            releasePlaybackResources()
                        }
                    }
                }

                // S0964: tracks become known only after prepare(); refresh the panel rows when
                // they land or when a selection override is applied.
                override fun onTracksChanged(tracks: Tracks) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        refreshTrackRowsAndRepaint()
                    }
                }
            })

            if (snapshot != null) {
                seekTo(snapshot.videoPositionMs)
                playWhenReady = snapshot.videoIsPlaying
                playbackParameters = androidx.media3.common.PlaybackParameters(snapshot.videoPlaybackSpeed)
                volume = snapshot.videoVolume
            } else {
                playWhenReady = true
            }

            val mediaItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file))
            setMediaItem(mediaItem)
            prepare()
        }
        runtime.setVideoSurfaceEnabled(true)
        playbackController.updatePlayer(exoPlayer)
        // S0964: seed the panel model from the real player state (snapshot-aware) so the HUD does
        // not show stale defaults (volume 1.0 / playing) on warm entries from the flat player.
        exoPlayer?.let { player ->
            hudRenderer.isPlaying = player.playWhenReady
            hudRenderer.volume = player.volume
        }
        Timber.d("Started video playback for: ${file.name}")
        return true
    }

    private fun releasePlaybackResources() {
        runtime.setVideoSurfaceEnabled(false)
        exoPlayer?.clearVideoSurface()
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        if (::playbackController.isInitialized) {
            playbackController.updatePlayer(null)
        }
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
        if (isPanelHudMode()) {
            refreshTrackRowsAndRepaint()
        } else {
            queueFilenameHud(file.name, config.projection, config.layout)
        }

        if (file.extension.lowercase() in setOf("jpg", "jpeg", "png")) {
            runtime.setVideoSurfaceEnabled(false)
            // S0290 Phase 11: decode off-main via Glide pool. After native consumes RGBA bytes (queueFrame
            // copies into the native pendingFrameData vector), return the bitmap to the pool so the next
            // slide's decode reuses this allocation - main thread sees no GC pause from the budget-sized
            // bitmap being recreated each slide change.
            lifecycleScope.launch(Dispatchers.IO) {
                val bitmap = decodeFilePooled(file)
                if (bitmap != null) {
                    try {
                        val bytes = copyBitmapToRgbaBytes(bitmap)
                        if (bytes != null) {
                            runtime.queueFrame(bytes, bitmap.width, bitmap.height)
                            Timber.d("Loaded and queued image: ${file.name} at ${bitmap.width}x${bitmap.height}")
                        } else {
                            // S0960 graceful path: keep showing the previous slide instead of crashing.
                            Timber.w("RGBA copy failed for ${file.name}; keeping previous frame")
                        }
                    } catch (t: Throwable) {
                        Timber.e(t, "Failed to copy bitmap buffer for ${file.name}")
                    } finally {
                        returnToPool(bitmap)
                    }
                } else {
                    Timber.w("Failed to decode image: ${file.name}")
                }
            }
        } else {
            if (sessionReady) {
                if (!startVideoPlayback(file)) {
                    queueErrorHud(file.name, "Playback Start Failed")
                }
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
        // S0290 (owner round 3 - HUD bug fix 2026-05-22 16:30): the previous implementation
        // re-rendered the full HudCanvasRenderer panel here on every native-driven ray-tick
        // (~12 ms cadence) and pushed `hudRgbaBytes` to native via
        // `runtime.queueHud(hudRgbaBytes, 1024, 512)`. Two problems with that path:
        //   1. ByteBuffer.wrap(ByteArray) + Bitmap.copyPixelsToBuffer does not reliably fill
        //      a heap-backed buffer on Android - the resulting hudRgbaBytes stayed all-zero,
        //      which was visible in logcat as `xr_session_queue_hud STORED 1024x512 first
        //      pixel RGBA=0,0,0,0` every frame.
        //   2. Even if the buffer wrote correctly, blasting the HUD texture every frame
        //      OVERWROTE the filename banner queued by queueFilenameHud on slide change,
        //      so the user never saw the banner content (transparent zeros texture).
        // The hover dispatch stays so panel buttons keep getting click events. The HUD
        // visual is now owned solely by queueFilenameHud, which runs on slide load and on
        // session resume.
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            interactionDispatcher.dispatch(uvX, uvY, isHover, isClick)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Signal the render thread that HzOS has registered the volumetric window so
        // nativeStartSession will see "Activity is already in the ready state" instead of
        // deferring. CompletableDeferred.complete() is idempotent after the first call.
        if (hasFocus) windowFocusedDeferred.complete(Unit)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        maybeStartRenderThread("surfaceCreated")
    }

    override fun onResume() {
        super.onResume()
        if (::surfaceView.isInitialized && surfaceView.holder.surface.isValid) {
            maybeStartRenderThread("onResume")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun maybeStartRenderThread(reason: String) {
        if (renderThread != null || !::surfaceView.isInitialized) return
        // S0382 Phase 04: the render thread receives the texture buffer by value at construction,
        // so it must not start until the off-main-thread initial decode has populated it. The
        // decode-completion callback re-invokes this method once the buffer is ready.
        if (!initialDecodeComplete) return
        val surface = surfaceView.holder.surface
        if (!surface.isValid) {
            return
        }
        renderThread = DiagnosticXrRenderThread(
            activity = this,
            surface = surface,
            runtime = runtime,
            textureBytes = textureBytes,
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            windowFocused = windowFocusedDeferred,
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
        // S0290 Phase 09 + owner round 3 (2026-05-22): order matters. Release ExoPlayer FIRST so it lets go of the Surface it received from runtime.getVideoSurface(); only then tear down the native side which destroys the underlying SurfaceTexture / GL texture. The reverse order caused VideoFrameReleaseHelper to call Surface.setFrameRate on an already-released Surface (logcat: "Surface has already been released") and MediaCodec.flush on a Released codec at every immersive exit.
        releasePlaybackResources()
        shutdownRenderThreadSync(PAUSE_SHUTDOWN_TIMEOUT_MS)
    }

    override fun onDestroy() {
        // onPause normally already tore everything down (Phase 09); this is the belt-and-braces path for the rare process-death-after-pause case where onPause did not get to finish. Same ordering rule: ExoPlayer release before native shutdown.
        releasePlaybackResources()
        shutdownRenderThreadSync(SHUTDOWN_TIMEOUT_MS)
        hudRepaintHandler.removeCallbacksAndMessages(null)
        reusableDirectBuffer = null
        reusableHudBuffer = null
        reusablePanelHudBuffer = null
        super.onDestroy()
    }

    /** S0290 Phase 09: synchronously request exit, wait up to [timeoutMs] for the render thread to finish, and null the reference so the next [surfaceCreated] starts a fresh thread. */
    private fun shutdownRenderThreadSync(timeoutMs: Long) {
        val thread = renderThread ?: return
        thread.requestExit()
        thread.join(timeoutMs)
        if (thread.isAlive) {
            // Wedged native runFrameLoop: no safe synchronous kill (no Thread.stop). Abandoning it
            // retains this Activity plus the live native EGL/OpenXR session until it unwedges.
            Timber.e(
                "DiagnosticXrActivity: render thread did not exit within ${timeoutMs}ms; " +
                    "abandoning it still-alive - leaks Activity + native XR session"
            )
        }
        renderThread = null
        sessionReady = false
        // Re-arm the window-focus gate so the next render-thread start awaits a fresh window-focus
        // signal instead of the completed one from the session we just tore down (re-entry gate).
        windowFocusedDeferred = CompletableDeferred()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (exitHandler.onKeyEvent(event, SystemClock.elapsedRealtime())) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (exitHandler.onMotionEvent(event, SystemClock.elapsedRealtime())) return true
        return super.onTouchEvent(event)
    }

    /**
     * S0295 Phase 02 step 02.4: dispatch the final exit path through one helper that branches
     * on [VrLaunchInput.deliveryMode]:
     *
     * - [VrLaunchDeliveryMode.ACTIVITY_RESULT] - emit `setResult(RESULT_OK, EXTRA_LAUNCH_RESULT)`
     *   and `finish()`. The launcher infrastructure on the caller side (the new
     *   [com.sza.fastmediasorter.core.xr.VrPlaybackActivityContract]) collapses this back into
     *   a typed `VrLaunchResult` without restarting the panel host.
     * - [VrLaunchDeliveryMode.LEGACY_PANEL_RETURN] - keep the existing Home+PendingIntent
     *   handoff via [returnToSettingsTaskOrFinish] so the diagnostic `Test Immersive` button
     *   and any other direct-fire path still works during the migration.
     *
     * If [launchInput] has not been initialised yet (preflight failure path runs before the
     * lateinit assignment), we fall back to the legacy path so the user still ends up back on
     * the panel.
     */
    private fun deliverReturnAndFinish(result: VrLaunchResult) {
        exitResult = result
        val deliveryMode = runCatching { launchInput.deliveryMode }.getOrDefault(
            VrLaunchDeliveryMode.LEGACY_PANEL_RETURN
        )
        when (deliveryMode) {
            VrLaunchDeliveryMode.ACTIVITY_RESULT -> deliverViaActivityResult(result)
            VrLaunchDeliveryMode.LEGACY_PANEL_RETURN -> returnToSettingsTaskOrFinish(result)
        }
    }

    private fun deliverViaActivityResult(result: VrLaunchResult) {
        val data = Intent().apply { putExtra(VrLaunchInput.EXTRA_LAUNCH_RESULT_TOKEN, payloadHolder.put(result)) }
        setResult(android.app.Activity.RESULT_OK, data)
        Timber.d("DiagnosticXrActivity: deliverViaActivityResult result=$result")
        finish()
    }

    /** Return to Home and ask HorizonOS to foreground the canonical panel task. Recent Quest logs show HorizonOS restoring this app as a launcher-rooted panel task whose base activity is MainActivity even when SettingsActivity is on top. Relaunching SettingsActivity directly competes with that restore path and duplicates the panel surface. */
    private fun returnToSettingsTaskOrFinish(result: VrLaunchResult) {
        if (!panelReturnDispatched.compareAndSet(false, true)) {
            Timber.d("DiagnosticXrActivity: panel return already dispatched")
            return
        }
        val returnedToFlatTask = launchPanelHostInHome(result) || launchPanelHostFallback(result)
        Timber.d("DiagnosticXrActivity: returnToSettingsTaskOrFinish -> $returnedToFlatTask result=$result")
        if (!returnedToFlatTask) {
            panelReturnDispatched.set(false)
            finish()
            return
        }
        scheduleHostFinish()
    }

    private fun launchPanelHostInHome(result: VrLaunchResult): Boolean {
        val context = applicationContext
        return runCatching {
            val panelIntent = buildReturnIntent(context, result)
            val pendingPanelIntent = PendingIntent.getActivity(
                context,
                0,
                panelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val homeIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_LAUNCH_IN_HOME_PENDING_INTENT, pendingPanelIntent)
            startActivity(homeIntent)
            true
        }.getOrElse {
            Timber.w(it, "DiagnosticXrActivity: failed to return to launcher task through Home")
            false
        }
    }

    private fun launchPanelHostFallback(result: VrLaunchResult): Boolean {
        val intent = buildReturnIntent(this, result)
        return runCatching {
            startActivity(intent)
            true
        }.getOrElse {
            Timber.w(it, "DiagnosticXrActivity: failed to return to launcher task")
            false
        }
    }

    private fun buildPlayerReturnTarget(target: VrPanelReturnTarget.Player): VrPanelReturnTarget.Player {
        val player = exoPlayer
        val returnSnapshot = if (player != null) {
            target.snapshot.copy(
                videoPositionMs = player.currentPosition,
                videoPlaybackSpeed = player.playbackParameters.speed,
                videoIsPlaying = player.isPlaying,
                videoVolume = player.volume
            )
        } else {
            target.snapshot
        }
        return target.copy(snapshot = returnSnapshot)
    }

    private fun buildReturnIntent(context: android.content.Context, result: VrLaunchResult): Intent {
        return when (val target = returnTarget) {
            is VrPanelReturnTarget.Player -> {
                val updatedTarget = buildPlayerReturnTarget(target)
                val detectedStereoMode = updatedTarget.detectedStereoModeName
                    ?.let { modeName -> runCatching { com.sza.fastmediasorter.domain.model.StereoMode.valueOf(modeName) }.getOrNull() }
                PlayerActivity.createPanelIntent(
                    context = context,
                    resourceId = updatedTarget.resourceId,
                    initialIndex = updatedTarget.playlistIndex,
                    skipAvailabilityCheck = false,
                    initialFilePath = updatedTarget.sourceFilePath,
                    isPlaying = updatedTarget.snapshot.videoIsPlaying,
                    isSlideshowEnabled = updatedTarget.resumeSlideshowEnabled,
                    detectedStereoMode = detectedStereoMode,
                    windowId = updatedTarget.windowId,
                ).apply {
                    putExtra(VrLaunchInput.EXTRA_LAUNCH_RESULT_TOKEN, payloadHolder.put(result))
                    putExtra(VrLaunchInput.EXTRA_RETURN_TARGET_TOKEN, payloadHolder.put(updatedTarget))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            }
            is VrPanelReturnTarget.Settings -> MainActivity.createReturnToSettingsIntent(
                context,
                target.initialTab,
            ).apply {
                putExtra(VrLaunchInput.EXTRA_LAUNCH_RESULT_TOKEN, payloadHolder.put(result))
            }
        }
    }

    private fun scheduleHostFinish() {
        // Meta's handoff sample stops at HOME + PendingIntent. Force-removing the XR task here made HorizonOS spin up FocusPlaceholderActivity and re-dispatch the panel launch, which recreated Settings and prevented a clean second immersive entry.
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                finish()
            }
        }
    }

    private fun onRenderThreadExit() {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                // S0295 Phase 02 step 02.4: render-thread exit from an OpenXR session that ran to its end (user dismissed via input handler after viewing) is the natural "completed" path. CancelledByUser remains the result for the explicit back-press shortcut in onBackPressedDispatcher above.
                Timber.d("render thread done; returning to panel target")
                deliverReturnAndFinish(VrLaunchResult.CompletedNormally)
            }
        }
    }

    private fun onRenderThreadSessionReady() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            // S0382 Phase 04: immersive session is ready and the first frame renders now -
            // remove the 2D loading indicator.
            dismissInitialLoadingOverlay()
            sessionReady = true
            if (mediaPlaylist.isEmpty() || currentPlaylistIndex !in mediaPlaylist.indices) return@runOnUiThread
            val file = mediaPlaylist[currentPlaylistIndex]
            val config = parseFilenameConfig(file.name)
            // A native session that has just become ready owns a freshly created 1x1 placeholder
            // HUD texture, and the prior session's shutdown cleared any pending HUD bytes. Re-queue
            // the current item's filename banner here for EVERY media type so a fresh session always
            // repaints the banner instead of leaving the placeholder. Video already did this; image
            // items previously relied solely on the one-time onCreate decode path, which is not
            // re-run when a session is recreated, leaving the placeholder visible on re-entry.
            runtime.setRenderConfig(config.projection.value, config.layout.value)
            hudRenderer.currentFilename = file.name
            // S0964: the quad-size override persists in native state across sessions within one
            // process, so EVERY mode must (re)assert its own size here - a diagnostic launch after
            // a VR Cinema one would otherwise stretch the banner onto the panel-sized quad.
            if (isPanelHudMode()) {
                runtime.setHudQuadSize(PANEL_QUAD_WIDTH_M, PANEL_QUAD_HEIGHT_M)
            } else {
                runtime.setHudQuadSize(BANNER_QUAD_WIDTH_M, BANNER_QUAD_HEIGHT_M)
                queueFilenameHud(file.name, config.projection, config.layout)
            }
            if (isVideoFilename(file.name)) {
                if (!startVideoPlayback(file)) {
                    queueErrorHud(file.name, "Playback Start Failed")
                }
            }
            if (isPanelHudMode()) {
                // After startVideoPlayback so the panel model already reflects snapshot state;
                // track rows populate later via onTracksChanged.
                refreshTrackRows()
                renderPanelHud()
            }
        }
    }

    private fun onRenderThreadStartFailed(result: DiagnosticXrNativeResult) {
        Timber.w("render thread start failed: $result")
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                deliverReturnAndFinish(VrLaunchResult.Crashed("native_start_failed:$result"))
            }
        }
    }

    private companion object {
        private const val EXTRA_LAUNCH_IN_HOME_PENDING_INTENT = "extra_launch_in_home_pending_intent"
        private const val MEDIA_SETTINGS_TAB_INDEX = 1
        const val SHUTDOWN_TIMEOUT_MS = 3_000L

        // S0290 Phase 09: onPause runs on the main thread; keep the join wait shorter than the standard ANR window so re-entry is responsive while still giving the render thread time to release EGL/OpenXR resources.
        const val PAUSE_SHUTDOWN_TIMEOUT_MS = 2_000L
        private const val REQUEST_CODE_HAND_TRACKING = 1001

        // S0290 Phase 11: known source dimensions of the bundled equirectangular asset, pre-sampling.
        // S0960 runs them through pickSampleSizeForBudget, so the Glide BitmapPool key is the sampled
        // size (4096x2048 at the 96 MB budget) and the second + subsequent sessions reuse that
        // allocation instead of re-allocating (root cause of the 2nd-launch OOM observed 2026-05-22).
        private const val BUNDLED_WIDTH = 8192
        private const val BUNDLED_HEIGHT = 4096

        // S0960: bytes per ARGB_8888 / RGBA pixel for buffer sizing.
        private const val RGBA_BYTES_PER_PIXEL = 4

        // S0290 (owner round 3): always-on HUD banner dimensions. Wide banner with the filename on the left and the resolved projection/stereo layout on the right - visible from the moment a slide loads, no ray pointing required.
        private const val HUD_BANNER_WIDTH = 1024
        private const val HUD_BANNER_HEIGHT = 128

        // S0964: world-space HUD quad sizes per mode. Banner values mirror the native defaults
        // (S0291 owner decision); the panel quad matches the 1024x640 texture aspect (1.6:1) at
        // -1.5 m so text stays legible without dominating the film. Owner review on device.
        private const val BANNER_QUAD_WIDTH_M = 0.3f
        private const val BANNER_QUAD_HEIGHT_M = 0.113f
        private const val PANEL_QUAD_WIDTH_M = 0.48f
        private const val PANEL_QUAD_HEIGHT_M = 0.30f

        // S0964: coalesce repaint bursts (slider drags) into one queueHud per window.
        private const val HUD_REPAINT_DEBOUNCE_MS = 100L

        // S0290 Phase 11.1 + S0960: heap budget for ANY single bitmap decode - external files and the
        // bundled asset alike. Anything above this gets a preflight inSampleSize so we never even try
        // the OOM allocation. 96 MB at ARGB_8888 covers a ~4900x4900 source; the bundled 8192x4096
        // asset lands at inSampleSize=2 (4096x2048 = 32 MB), leaving headroom for ExoPlayer buffers
        // and OpenXR swapchain copies.
        private const val MAX_DECODE_BYTES = 96L * 1024L * 1024L
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv")

        // S0290 (owner round 3 2026-05-22): full coverage matrix - 3 projections × 3 stereo layouts × {image, video} = 18 entries plus the original FLAT mono (moraine lake). Stereo variants ship with diagnostic L/R overlays (see setup_test_vr.ps1) so the viewer can verify per-eye routing by closing one eye in the headset.
        // Test matrix: 360°/180°/flat × mono/TB-stereo/SBS-stereo × image+video. L/R overlays in setup_test_vr.ps1.
        private val VR_TEST_MEDIA_ORDER = listOf(
            "diagnostic_360_mono.jpg", "diagnostic_360_stereo_tb.jpg", "diagnostic_360_stereo_sbs.jpg",
            "diagnostic_180_mono.jpg", "diagnostic_180_stereo_tb.jpg", "diagnostic_180_stereo_sbs.jpg",
            "moraine_lake_flat_mono.jpg", "moraine_lake_flat_tb.jpg", "moraine_lake_flat_sbs.jpg",
            "lakeside_flat_mono.jpg",
            "video_360_mono.mp4", "video_360_stereo_tb.mp4", "video_360_stereo_sbs.mp4",
            "video_180_mono.mp4", "video_180_stereo_tb.mp4", "video_180_stereo_sbs.mp4",
            "big_buck_bunny_flat_mono.mp4", "big_buck_bunny_flat_tb.mp4", "big_buck_bunny_flat_sbs.mp4",
        )
    }
}
