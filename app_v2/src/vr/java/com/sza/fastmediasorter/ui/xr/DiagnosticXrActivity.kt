package com.sza.fastmediasorter.ui.xr

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import com.sza.fastmediasorter.R
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
import com.sza.fastmediasorter.ui.player.StereoDetector
import com.sza.fastmediasorter.ui.xr.helpers.HudCanvasRenderer
import com.sza.fastmediasorter.ui.xr.helpers.HudHapticBridge
import com.sza.fastmediasorter.ui.xr.helpers.HudInteractionDispatcher
import com.sza.fastmediasorter.ui.xr.helpers.HudPlaybackController
import com.sza.fastmediasorter.ui.xr.helpers.HudTrackController
import com.sza.fastmediasorter.ui.xr.helpers.ProjectionType
import com.sza.fastmediasorter.ui.xr.helpers.RenderConfig
import com.sza.fastmediasorter.ui.xr.helpers.StereoLayout
import com.sza.fastmediasorter.ui.xr.helpers.SubtitleCueController
import com.sza.fastmediasorter.ui.xr.helpers.VrDiagnosticPlaybackController
import com.sza.fastmediasorter.ui.xr.helpers.VrHudBannerRenderer
import com.sza.fastmediasorter.ui.xr.helpers.VrPanelReturnDispatcher
import com.sza.fastmediasorter.ui.xr.helpers.VrStereoConfigResolver
import com.sza.fastmediasorter.ui.xr.helpers.VrTextureDecoder
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject

/** S0282: dedicated OpenXR session host Activity with dynamic playlist. */
@AndroidEntryPoint
class DiagnosticXrActivity : ComponentActivity(), SurfaceHolder.Callback {

    @Inject lateinit var runtime: DiagnosticXrRuntime
    @Inject lateinit var assetProvider: DiagnosticXrAssetProvider
    @Inject lateinit var exitHandler: DiagnosticXrInputExitHandler
    @Inject lateinit var payloadHolder: VrLaunchPayloadHolder

    // S0771: the immersive renderer must agree with the 2D panel on stereo layout. Reuse the panel
    // player's shared classifier instead of a second, divergent filename parser (see VrStereoConfigResolver).
    @Inject lateinit var stereoDetector: StereoDetector

    // S0989: filename -> projection/layout resolution extracted to a dedicated helper.
    private val stereoConfigResolver by lazy { VrStereoConfigResolver(stereoDetector) }

    // S0989: always-on HUD banner rendering (filename/error) extracted to a dedicated helper.
    private val hudBanner by lazy { VrHudBannerRenderer(runtime) }

    // S0989: image texture decoding (budget sampling, Glide pool, RGBA copy) extracted to a helper.
    private val textureDecoder by lazy { VrTextureDecoder(applicationContext) }

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

    // S0964: panel-sized twin of the banner buffer (see reusablePanelHudBuffer field note).
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

    // Dynamic Playlist
    private var mediaPlaylist: List<File> = emptyList()
    private var currentPlaylistIndex: Int = -1

    // S0989: immersive ExoPlayer ownership + teardown extracted to a dedicated controller (not
    // HudPlaybackController, which is the transport-button wrapper). Lazy so the preflight-failure
    // path that never reaches proceedWithInitialization can still release() safely from onPause.
    private val playbackCtrl by lazy {
        VrDiagnosticPlaybackController(
            context = this,
            runtime = runtime,
            snapshotProvider = { launchInput.snapshot },
            runOnUiThread = { block -> runOnUiThread(block) },
            isHostActive = { !isFinishing && !isDestroyed },
            onSurfaceUnavailable = { hudRenderer.isPlaying = false },
            onError = { file, shortErr -> hudBanner.queueError(file.name, shortErr) },
            onTracksChanged = ::refreshTrackRowsAndRepaint,
            onCues = { cueGroup -> subtitleController?.submit(cueGroup) },
            onPlayerChanged = { p ->
                if (::playbackController.isInitialized) playbackController.updatePlayer(p)
                if (p != null) {
                    hudRenderer.isPlaying = p.playWhenReady
                    hudRenderer.volume = p.volume
                } else {
                    subtitleController?.submitText("")
                }
            },
        )
    }

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

    // S0986: renders selected subtitle-track cues onto the lower-third immersive quad.
    private var subtitleController: SubtitleCueController? = null
    private var hudSubsOffLabel: String = ""
    private var hudNoTracksLabel: String = ""

    // S0964: panel repaints are debounced so a volume-slider drag (per ray-tick callbacks)
    // cannot turn into a per-frame queueHud storm (S0290 rule: state-driven uploads only).
    private val hudRepaintHandler = Handler(Looper.getMainLooper())
    private var hudRepaintScheduled = false

    // S0964: the panel copy buffer is deliberately separate from VrTextureDecoder's direct buffer -
    // image decodes use that one from Dispatchers.IO while panel repaints run on main, and the buffer
    // CONTENT is written outside the @Synchronized getter, so sharing would race.
    @Volatile private var reusablePanelHudBuffer: ByteBuffer? = null

    // Dynamic HUD Canvas buffers (S0964: RGBA copies go through reusablePanelHudBuffer; the old
    // hudRgbaBytes field from the removed S0290 per-frame path was dead and is gone).
    private var hudBitmap: Bitmap? = null
    private var hudCanvas: Canvas? = null

    private lateinit var launchInput: VrLaunchInput
    private lateinit var returnTarget: VrPanelReturnTarget

    // S0989: exit/return playbook extracted to a dedicated dispatcher. Lazy + launchInput guarded so
    // the onCreate preflight-failure path (before launchInput is assigned) still delivers a return.
    private val returnDispatcher by lazy {
        VrPanelReturnDispatcher(
            activity = this,
            payloadHolder = payloadHolder,
            returnTargetProvider = { returnTarget },
            launchInputProvider = { if (::launchInput.isInitialized) launchInput else null },
            playerProvider = { playbackCtrl.player },
        )
    }

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
                returnDispatcher.deliverReturnAndFinish(parsed.unavailable)
                return
            }
            is DiagnosticXrLaunchArgs.Parsed -> {
                launchInput = parsed.input
                returnTarget = parsed.returnTarget
            }
        }

        if (!runtime.isNativeAvailable) {
            Timber.w("DiagnosticXrActivity: native runtime unavailable, finishing")
            returnDispatcher.deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NoRuntime))
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
        Timber.d("S0989: proceedWithInitialization - wiring stereo/banner/decoder/playback/return helpers")
        // 1. Initialize VR HUD Canvas buffers and helpers (Step 03.2)
        hudRenderer = HudCanvasRenderer()
        hapticBridge = HudHapticBridge(runtime)
        playbackController = HudPlaybackController(null, ::navigateToNextMedia, ::navigateToPrevMedia)
        // S0964: track rows mirror the 2D video dialog composition on top of the shared
        // VideoTrackSelectionManager primitives (epic S0773 ADR-3).
        trackController = HudTrackController { playbackCtrl.player }
        subtitleController = SubtitleCueController(runtime)
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
                renderThread?.requestExit() ?: returnDispatcher.deliverReturnAndFinish(VrLaunchResult.CancelledByUser)
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
                returnDispatcher.deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.DecoderFailed))
                return false
            }
            Timber.w("Failed to decode initial image ${firstFile.name}, falling back to bundled")
        }
        if (decodeBundledAsset()) return true
        Timber.w("Failed to decode bundled fallback asset; returning DecoderFailed")
        returnDispatcher.deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.DecoderFailed))
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
            returnDispatcher.deliverReturnAndFinish(
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
                    returnDispatcher.deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.InvalidUri))
                    return false
                }
                listOf(launchFile)
            }
            // S0963: RESOURCE_BROWSE is owned by ImmersiveBrowseActivity; the gateway never routes it
            // here. Reject defensively rather than scanning the diagnostic test folder.
            VrLaunchMode.RESOURCE_BROWSE -> {
                Timber.w("DiagnosticXrActivity: RESOURCE_BROWSE not handled by the diagnostic host")
                returnDispatcher.deliverReturnAndFinish(VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NotYetSupported))
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
            hudBanner.queueFilename("vr_diagnostic_360_mono.jpg (bundled)", ProjectionType.SPHERE_360, StereoLayout.MONO)
        }

        // S0382 Phase 04 / ADR-1: decode and the large RGBA copy run on Dispatchers.IO (VrTextureDecoder)
        // so the focus transition is never blocked on the main thread.
        val decoded = textureDecoder.decodeBundled() ?: return false
        textureBytes = decoded.bytes
        textureWidth = decoded.width
        textureHeight = decoded.height
        return true
    }

    private suspend fun decodeImageToActivityBytes(file: File): Boolean {
        val config = stereoConfigResolver.resolve(file.name)
        runtime.setRenderConfig(config.projection.value, config.layout.value)
        hudRenderer.currentFilename = file.name
        if (isPanelHudMode()) {
            renderPanelHud()
        } else {
            hudBanner.queueFilename(file.name, config.projection, config.layout)
        }

        // S0382 Phase 04 / ADR-1: decode + RGBA copy off the main thread (VrTextureDecoder).
        val decoded = textureDecoder.decodeFile(file) ?: return false
        textureBytes = decoded.bytes
        textureWidth = decoded.width
        textureHeight = decoded.height
        return true
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
        Timber.d("S0989: loadCurrentMediaItem index=$currentPlaylistIndex file=${file.name}")
        Timber.d("Loading media item at index $currentPlaylistIndex: ${file.name}")

        hudRenderer.currentFilename = file.name
        playbackCtrl.release()

        val config = stereoConfigResolver.resolve(file.name)
        runtime.setRenderConfig(config.projection.value, config.layout.value)
        if (isPanelHudMode()) {
            refreshTrackRowsAndRepaint()
        } else {
            hudBanner.queueFilename(file.name, config.projection, config.layout)
        }

        if (file.extension.lowercase() in setOf("jpg", "jpeg", "png")) {
            runtime.setVideoSurfaceEnabled(false)
            // S0290 Phase 11: decode off-main via the Glide pool (VrTextureDecoder). queueFrame stays on
            // Dispatchers.IO - it copies into the native pendingFrameData vector; the decoder returns the
            // bitmap to the pool internally so the main thread sees no GC pause per slide change.
            // S0960 graceful path: a null decode keeps the previous slide instead of crashing.
            lifecycleScope.launch(Dispatchers.IO) {
                val decoded = textureDecoder.decodeFile(file)
                if (decoded != null) {
                    runtime.queueFrame(decoded.bytes, decoded.width, decoded.height)
                    Timber.d("Loaded and queued image: ${file.name} at ${decoded.width}x${decoded.height}")
                } else {
                    Timber.w("Failed to decode image ${file.name}; keeping previous frame")
                }
            }
        } else {
            if (sessionReady) {
                if (!playbackCtrl.start(file)) {
                    hudBanner.queueError(file.name, "Playback Start Failed")
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
        playbackCtrl.release()
        shutdownRenderThreadSync(PAUSE_SHUTDOWN_TIMEOUT_MS)
    }

    override fun onDestroy() {
        // onPause normally already tore everything down (Phase 09); this is the belt-and-braces path for the rare process-death-after-pause case where onPause did not get to finish. Same ordering rule: ExoPlayer release before native shutdown.
        playbackCtrl.release()
        shutdownRenderThreadSync(SHUTDOWN_TIMEOUT_MS)
        hudRepaintHandler.removeCallbacksAndMessages(null)
        subtitleController?.release()
        subtitleController = null
        textureDecoder.releaseBuffer()
        hudBanner.releaseBuffers()
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

    private fun onRenderThreadExit() {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                // S0295 Phase 02 step 02.4: render-thread exit from an OpenXR session that ran to its end (user dismissed via input handler after viewing) is the natural "completed" path. CancelledByUser remains the result for the explicit back-press shortcut in onBackPressedDispatcher above.
                Timber.d("S0989: render-thread exit -> returnDispatcher")
                Timber.d("render thread done; returning to panel target")
                returnDispatcher.deliverReturnAndFinish(VrLaunchResult.CompletedNormally)
            }
        }
    }

    private fun onRenderThreadSessionReady() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            Timber.d("S0989: session ready - re-queue banner + (re)start playback")
            // S0382 Phase 04: immersive session is ready and the first frame renders now -
            // remove the 2D loading indicator.
            dismissInitialLoadingOverlay()
            sessionReady = true
            if (mediaPlaylist.isEmpty() || currentPlaylistIndex !in mediaPlaylist.indices) return@runOnUiThread
            val file = mediaPlaylist[currentPlaylistIndex]
            val config = stereoConfigResolver.resolve(file.name)
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
                hudBanner.queueFilename(file.name, config.projection, config.layout)
            }
            if (isVideoFilename(file.name)) {
                if (!playbackCtrl.start(file)) {
                    hudBanner.queueError(file.name, "Playback Start Failed")
                }
            }
            if (isPanelHudMode()) {
                // After playbackCtrl.start so the panel model already reflects snapshot state;
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
                returnDispatcher.deliverReturnAndFinish(VrLaunchResult.Crashed("native_start_failed:$result"))
            }
        }
    }

    private companion object {
        private const val MEDIA_SETTINGS_TAB_INDEX = 1
        const val SHUTDOWN_TIMEOUT_MS = 3_000L

        // S0290 Phase 09: onPause runs on the main thread; keep the join wait shorter than the standard ANR window so re-entry is responsive while still giving the render thread time to release EGL/OpenXR resources.
        const val PAUSE_SHUTDOWN_TIMEOUT_MS = 2_000L
        private const val REQUEST_CODE_HAND_TRACKING = 1001

        // S0960: bytes per ARGB_8888 / RGBA pixel for buffer sizing (panel HUD copy buffer).
        private const val RGBA_BYTES_PER_PIXEL = 4

        // S0964: world-space HUD quad sizes per mode. Banner values mirror the native defaults
        // (S0291 owner decision); the panel quad matches the 1024x640 texture aspect (1.6:1) at
        // -1.5 m so text stays legible without dominating the film. Owner review on device.
        private const val BANNER_QUAD_WIDTH_M = 0.3f
        private const val BANNER_QUAD_HEIGHT_M = 0.113f
        private const val PANEL_QUAD_WIDTH_M = 0.48f
        private const val PANEL_QUAD_HEIGHT_M = 0.30f

        // S0964: coalesce repaint bursts (slider drags) into one queueHud per window.
        private const val HUD_REPAINT_DEBOUNCE_MS = 100L

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
