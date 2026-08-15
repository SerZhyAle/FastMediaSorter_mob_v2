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
import android.text.format.DateUtils
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
import com.sza.fastmediasorter.core.xr.VrLegendPreferences
import com.sza.fastmediasorter.core.xr.VrMediaType
import com.sza.fastmediasorter.core.xr.VrPanelReturnTarget
import com.sza.fastmediasorter.core.xr.VrPlaylistEntry
import com.sza.fastmediasorter.core.xr.assets.DiagnosticXrAssetProvider
import com.sza.fastmediasorter.core.xr.input.DiagnosticXrInputExitHandler
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrNativeResult
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import com.sza.fastmediasorter.ui.player.StereoDetector
import com.sza.fastmediasorter.ui.xr.helpers.HudCanvasRenderer
import com.sza.fastmediasorter.ui.xr.helpers.HudHapticBridge
import com.sza.fastmediasorter.ui.xr.helpers.HudInteractionDispatcher
import com.sza.fastmediasorter.ui.xr.helpers.HudLegendController
import com.sza.fastmediasorter.ui.xr.helpers.HudLegendRenderer
import com.sza.fastmediasorter.ui.xr.helpers.HudPlaybackController
import com.sza.fastmediasorter.ui.xr.helpers.HudSeekProgressTicker
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
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** S0282: dedicated OpenXR session host Activity with dynamic playlist. */
@AndroidEntryPoint
class DiagnosticXrActivity : ComponentActivity(), SurfaceHolder.Callback {

    @Inject lateinit var runtime: DiagnosticXrRuntime
    @Inject lateinit var assetProvider: DiagnosticXrAssetProvider
    @Inject lateinit var exitHandler: DiagnosticXrInputExitHandler
    @Inject lateinit var payloadHolder: VrLaunchPayloadHolder

    // S1223: remembers whether the one-time controls legend has already been shown on this install.
    @Inject lateinit var legendPreferences: VrLegendPreferences

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
    // S1233: an item carries its media type rather than leaving the host to sniff the extension.
    // Three separate guesses used to disagree: the image branch accepted only jpg/jpeg/png, and
    // isVideoFilename knows only mp4/mkv, so webp/heic went to ExoPlayer and mov/webm never restarted
    // on session-ready. The caller reads the type off the domain model, so nothing has to guess.
    private data class PlaylistItem(val file: File, val mediaType: VrMediaType)

    private var mediaPlaylist: List<PlaylistItem> = emptyList()
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
                    // S1239: one hook covers both playback entry points - the fresh start and the
                    // deferred restart once the native session is ready. Duration is still unset
                    // this early, so the band appears on the first tick that finds it.
                    refreshSeekPosition()
                    seekTicker.start()
                } else {
                    subtitleController?.submitText("")
                    seekTicker.stop()
                    // S1239: release() also runs on the preflight-failure path, which returns from
                    // onCreate before proceedWithInitialization assigns the HUD - the same reason
                    // updatePlayer above is guarded.
                    if (::hudRenderer.isInitialized) hudRenderer.seekRowVisible = false
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

    // S1223: Context-free like HudCanvasRenderer, so its captions are injected below.
    private val legendRenderer = HudLegendRenderer()

    // S1223: created in proceedWithInitialization rather than lazily - the preflight-failure path
    // returns from onCreate before that point and must not allocate a legend only to release it.
    private var legendController: HudLegendController? = null

    // S0986: renders selected subtitle-track cues onto the lower-third immersive quad.
    private var subtitleController: SubtitleCueController? = null
    private var hudSubsOffLabel: String = ""
    private var hudNoTracksLabel: String = ""

    // S0964: panel repaints are debounced so a volume-slider drag (per ray-tick callbacks)
    // cannot turn into a per-frame queueHud storm (S0290 rule: state-driven uploads only).
    private val hudRepaintHandler = Handler(Looper.getMainLooper())
    private var hudRepaintScheduled = false

    // S1239: `runtime.setHudVisible` is write-only on the native side, so the seek ticker cannot
    // ask whether the strip is on screen - the host has to remember.
    private var hudVisible = true

    // S1239: raised for the length of a ray drag on the seek band. While it is set the ticker
    // stands down, which is what stops the classic seek-bar race where the periodic position
    // update yanks the handle back out from under the user's hand.
    private var isScrubbingSeek = false

    // S1239: the strip's only periodic repaint. Lazy because its predicate reads hudRenderer,
    // which proceedWithInitialization assigns.
    private val seekTicker by lazy {
        HudSeekProgressTicker(
            handler = hudRepaintHandler,
            shouldRun = { isPanelHudMode() && hudVisible && hudRenderer.isPlaying && !isScrubbingSeek },
            onTick = ::refreshSeekPosition,
        )
    }

    // S0964: the panel copy buffer is deliberately separate from VrTextureDecoder's direct buffer -
    // image decodes use that one from Dispatchers.IO while panel repaints run on main, and the buffer
    // CONTENT is written outside the @Synchronized getter, so sharing would race.
    @Volatile private var reusablePanelHudBuffer: ByteBuffer? = null

    // Dynamic HUD Canvas buffers (S0964: RGBA copies go through reusablePanelHudBuffer; the old
    // hudRgbaBytes field from the removed S0290 per-frame path was dead and is gone).
    private var hudBitmap: Bitmap? = null
    private var hudCanvas: Canvas? = null

    // S1239: queueHud takes a ByteArray, so every repaint used to allocate a fresh 3.7 MB one.
    // Harmless while repaints were state-driven; the seek bar's 1 Hz tick would turn it into
    // 3.7 MB/s of garbage on a 512 MB-heap headset, so the array is reused like the buffer above.
    private var reusablePanelHudBytes: ByteArray? = null

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
        hudRenderer.hideLabel = getString(R.string.vr_hud_hide)
        hudRenderer.exitLabel = getString(R.string.vr_hud_exit)
        hudRenderer.helpLabel = getString(R.string.vr_hud_help)
        legendRenderer.title = getString(R.string.vr_legend_title)
        legendRenderer.footer = getString(R.string.vr_legend_footer)
        legendRenderer.rows = buildLegendRows()
        legendController = HudLegendController(runtime, legendRenderer, ::restoreStripAfterLegend)
        val hudInteractionListener = object : HudInteractionDispatcher.InteractionListener {
            override fun onPlayPauseClick() {
                hapticBridge.triggerClickFeedback()
                hudRenderer.isPlaying = !hudRenderer.isPlaying
                if (hudRenderer.isPlaying) playbackController.play() else playbackController.pause()
                // S1239: a paused film has nothing to advance, so the tick stands down until play.
                if (hudRenderer.isPlaying) seekTicker.start() else seekTicker.stop()
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
                hapticBridge.triggerClickFeedback()
                trackController.cycleAudio(step) { refreshTrackRowsAndRepaint() }
            }
            override fun onSubtitleTrackCycle(step: Int) {
                hapticBridge.triggerClickFeedback()
                trackController.cycleSubtitle(step) { refreshTrackRowsAndRepaint() }
            }
            override fun onSeekPreview(fraction: Float) {
                // S1239: preview only - the label follows the handle, the player does not move yet.
                isScrubbingSeek = true
                val position = playbackController.positionOrNull() ?: return
                hudRenderer.timeLabel =
                    seekTimeLabel((position.durationMs * fraction).toLong(), position.durationMs)
                scheduleHudPanelRepaint()
            }

            override fun onSeekCommit(fraction: Float) {
                isScrubbingSeek = false
                hapticBridge.triggerClickFeedback()
                playbackController.seekToFraction(fraction)
                seekTicker.start()
            }

            override fun onHideClick() {
                hapticBridge.triggerClickFeedback()
                // S1232: the quad stops drawing and stops reacting natively. Nothing is repainted -
                // there is no pill to leave behind, and the trigger is what brings it back.
                runtime.setHudVisible(false)
                // S1239: an invisible strip has no bar to advance; repainting it would be pure cost.
                hudVisible = false
                seekTicker.stop()
            }
            override fun onHelpClick() {
                hapticBridge.triggerClickFeedback()
                showLegend()
            }
            override fun onExitClick() {
                hapticBridge.triggerClickFeedback()
                // S1232: must go through the render thread, not straight to the return dispatcher.
                // Finishing the Activity while the native session still runs skips the EGL/OpenXR
                // teardown and the next entry fails as AlreadyRunning. requestExit unwinds the
                // frame loop; onRenderThreadExit then delivers the return.
                renderThread?.requestExit()
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

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Timber.d("DiagnosticXrActivity: onBackPressed -> requesting exit")
                    renderThread?.requestExit()
                        ?: returnDispatcher.deliverReturnAndFinish(VrLaunchResult.CancelledByUser)
                }
            }
        )

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
        // S1233: the launched item is the one at currentPlaylistIndex, not the head of the list -
        // entering immersive on the tenth file of a folder used to decode the first one instead.
        // The image/video decision now comes from the carried type rather than an extension whitelist
        // that rejected webp and heic.
        val firstItem = mediaPlaylist.getOrNull(currentPlaylistIndex)
        if (firstItem != null && firstItem.mediaType == VrMediaType.IMAGE) {
            val firstFile = firstItem.file
            if (decodeImageToActivityBytes(firstFile)) return true
            if (launchInput.launchMode == VrLaunchMode.FILE_URI) {
                Timber.w("Failed to decode initial launch image ${firstFile.name}, returning DecoderFailed")
                returnDispatcher.deliverReturnAndFinish(
                    VrLaunchResult.Unavailable(VrLaunchUnavailableReason.DecoderFailed)
                )
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
        // S1233: the launched item is no longer always index 0, so the list and the starting index
        // are decided together rather than the index being assumed afterwards.
        val prepared: Pair<List<PlaylistItem>, Int> = when (launchInput.launchMode) {
            VrLaunchMode.DIAGNOSTIC_PLAYLIST -> scanMediaFiles() to 0
            VrLaunchMode.FILE_URI -> {
                val launchFile = resolveSingleLaunchFile(launchInput)
                if (launchFile == null) {
                    Timber.w("DiagnosticXrActivity: invalid launch fileUriString=%s", launchInput.fileUriString)
                    returnDispatcher.deliverReturnAndFinish(
                        VrLaunchResult.Unavailable(VrLaunchUnavailableReason.InvalidUri)
                    )
                    return false
                }
                // S1233: the caller may hand over the surrounding resource list so PREV/NEXT have
                // somewhere to go. Absent or unusable, this stays the single-element list it was.
                resolveCarriedPlaylist(launchInput)
                    ?: (listOf(PlaylistItem(launchFile, launchInput.mediaType)) to 0)
            }
            // S0963: RESOURCE_BROWSE is owned by ImmersiveBrowseActivity; the gateway never routes it
            // here. Reject defensively rather than scanning the diagnostic test folder.
            VrLaunchMode.RESOURCE_BROWSE -> {
                Timber.w("DiagnosticXrActivity: RESOURCE_BROWSE not handled by the diagnostic host")
                returnDispatcher.deliverReturnAndFinish(
                    VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NotYetSupported)
                )
                return false
            }
        }
        mediaPlaylist = prepared.first
        currentPlaylistIndex = if (mediaPlaylist.isEmpty()) -1 else prepared.second
        return true
    }

    /**
     * S1233: turn the caller's ordered entries into openable items, paired with the launched item's
     * index inside the surviving list. Null means "no usable list" - the caller either sent none, sent
     * one too short to navigate, or the launched entry itself does not resolve here.
     *
     * Resolution is deliberately narrower than [resolveSingleLaunchFile]: it never materialises a
     * `content://` entry, because doing that for a whole list would copy the entire folder into the
     * cache at launch. [PlayerVrLaunchManager] only hands over local paths, so nothing is lost.
     */
    private fun resolveCarriedPlaylist(input: VrLaunchInput): Pair<List<PlaylistItem>, Int>? {
        if (input.playlist.size < MIN_NAVIGABLE_PLAYLIST_SIZE) return null
        val items = mutableListOf<PlaylistItem>()
        var launchedIndex = -1
        input.playlist.forEachIndexed { index, entry ->
            val file = resolvePlaylistEntryFile(entry) ?: return@forEachIndexed
            if (index == input.playlistIndex) launchedIndex = items.size
            items += PlaylistItem(file, entry.mediaType)
        }
        // A list whose launched entry vanished (deleted between launch and here) would start playback
        // on an unrelated file, which is worse than not navigating at all.
        val usable = launchedIndex >= 0 && items.size >= MIN_NAVIGABLE_PLAYLIST_SIZE
        if (!usable) {
            Timber.w("Carried playlist unusable (resolved=${items.size}, launchedIndex=$launchedIndex)")
        }
        return if (usable) items to launchedIndex else null
    }

    private fun resolvePlaylistEntryFile(entry: VrPlaylistEntry): File? {
        val uri = Uri.parse(entry.fileUriString)
        val file = when {
            uri.scheme == "file" -> uri.path?.let(::File)
            uri.scheme.isNullOrBlank() -> File(entry.fileUriString)
            else -> null
        }
        return file?.takeIf { it.isFile }
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

    private fun scanMediaFiles(): List<PlaylistItem> {
        val pictureDir = File("/sdcard/Pictures/FastMediaSorterVrTest")
        val movieDir = File("/sdcard/Movies/FastMediaSorterVrTest")

        // S1233: the scan keeps deriving the type from the filename - a filesystem walk of the test
        // folder has no typed source to carry, unlike a launch coming from the player.
        val playlist = VR_TEST_MEDIA_ORDER.mapNotNull { filename ->
            val isVideo = isVideoFilename(filename)
            val dir = if (isVideo) movieDir else pictureDir
            File(dir, filename).takeIf { file -> file.isFile }?.let { file ->
                PlaylistItem(file, if (isVideo) VrMediaType.VIDEO else VrMediaType.IMAGE)
            }
        }

        val names = playlist.joinToString { it.file.name }
        Timber.d("DiagnosticXrActivity: VR test playlist contains ${playlist.size} files: $names")
        return playlist
    }

    private fun isVideoFilename(filename: String): Boolean {
        val extension = filename.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in VIDEO_EXTENSIONS
    }

    /** S0964: true when this launch shows the interactive panel HUD instead of the banner. */
    private fun isPanelHudMode(): Boolean = launchInput.launchMode == VrLaunchMode.FILE_URI

    /**
     * S1223: the legend lists what S1232 and S1240 actually wired, in the order a newcomer meets
     * them. The menu-button settings row from S1240's table is deliberately absent - it belongs to
     * S1271 and is not in this build, and a legend row for an unwired binding is worse than none.
     */
    private fun buildLegendRows(): List<HudLegendRenderer.LegendRow> = listOf(
        row(R.string.vr_legend_input_trigger, R.string.vr_legend_action_click),
        row(R.string.vr_legend_input_trigger_hidden, R.string.vr_legend_action_summon),
        row(R.string.vr_legend_input_stick_x, R.string.vr_legend_action_seek),
        row(R.string.vr_legend_input_grip_stick_x, R.string.vr_legend_action_file_step),
        row(R.string.vr_legend_input_stick_y, R.string.vr_legend_action_zoom),
        row(R.string.vr_legend_input_grip, R.string.vr_legend_action_move_panel),
        row(R.string.vr_legend_input_ax, R.string.vr_legend_action_exit),
        row(R.string.vr_legend_input_panel_buttons, R.string.vr_legend_action_panel_buttons),
    )

    private fun row(inputRes: Int, actionRes: Int) =
        HudLegendRenderer.LegendRow(getString(inputRes), getString(actionRes))

    /**
     * S1223: the single restore point after the legend hands the HUD channel back. The repaint runs
     * directly rather than through [scheduleHudPanelRepaint] because the debounce would leave the
     * legend's texture stretched across the strip-sized quad for the length of the window.
     */
    /**
     * S1223: the seek tick stands down for the length of the legend. The bar cannot be seen while
     * the legend owns the channel, so a tick would only queue work that [renderPanelHud] drops.
     */
    private fun showLegend() {
        seekTicker.stop()
        legendController?.show()
    }

    private fun restoreStripAfterLegend() {
        runtime.setHudQuadSize(PANEL_QUAD_WIDTH_M, PANEL_QUAD_HEIGHT_M, PANEL_QUAD_OFFSET_Y_M)
        hudVisible = true
        renderPanelHud()
        refreshSeekPosition()
        seekTicker.start()
    }

    /**
     * S0964: paint the interactive panel (nav, volume, depth, track rows) into the HUD quad.
     * Called on state changes only - never per frame (S0290). Main thread.
     */
    private fun renderPanelHud() {
        // S1223: the legend borrows this same texture channel. Uploading the strip while it is up
        // would stretch a 2560x360 texture across the legend's quad - one guard at the only queueHud
        // call site covers every repaint trigger (ticker, track change, slider drag) at once.
        if (isFinishing || isDestroyed || legendController?.isVisible == true) return
        val bitmap = hudBitmap
        val canvas = hudCanvas
        if (bitmap == null || canvas == null) return
        hudRenderer.render(canvas)
        val buf = getReusablePanelHudBuffer()
        bitmap.copyPixelsToBuffer(buf)
        buf.rewind()
        val bytes = reusablePanelHudBytes?.takeIf { it.size == buf.remaining() }
            ?: ByteArray(buf.remaining()).also { reusablePanelHudBytes = it }
        buf.get(bytes)
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

    /**
     * S1239: pull the player's position into the seek band and repaint. Main thread - the ticker
     * posts on the same handler the debounced repaint uses.
     *
     * A null position hides the band rather than freezing it: that is an image, or a source whose
     * duration is unset, and a handle nailed to zero would invite a drag that cannot go anywhere.
     */
    private fun refreshSeekPosition() {
        val position = playbackController.positionOrNull()
        val visible = position != null
        val label = position?.let { seekTimeLabel(it.positionMs, it.durationMs) }.orEmpty()
        // Repaint only when something a viewer could see moved. The label has one-second
        // granularity, so this also spares a live source - whose duration never resolves - a
        // 3.7 MB texture upload every second for a band that stays hidden.
        if (visible == hudRenderer.seekRowVisible && label == hudRenderer.timeLabel) return
        hudRenderer.seekRowVisible = visible
        hudRenderer.timeLabel = label
        hudRenderer.seekProgress =
            position?.let { it.positionMs.toFloat() / it.durationMs.toFloat() } ?: 0f
        scheduleHudPanelRepaint()
    }

    /**
     * S1239: `DateUtils` already renders M:SS and H:MM:SS, so the immersive HUD does not become the
     * seventh place in this project carrying its own duration formatter.
     */
    private fun seekTimeLabel(positionMs: Long, durationMs: Long): String {
        val elapsed = DateUtils.formatElapsedTime(TimeUnit.MILLISECONDS.toSeconds(positionMs))
        val total = DateUtils.formatElapsedTime(TimeUnit.MILLISECONDS.toSeconds(durationMs))
        return "$elapsed / $total"
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
        hudRenderer.depthRowVisible = false
        hudRenderer.currentFilename = "vr_diagnostic_360_mono.jpg (bundled)"
        if (isPanelHudMode()) {
            renderPanelHud()
        } else {
            hudBanner.queueFilename(
                "vr_diagnostic_360_mono.jpg (bundled)",
                ProjectionType.SPHERE_360,
                StereoLayout.MONO
            )
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
        hudRenderer.depthRowVisible = config.layout != StereoLayout.MONO
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

    /**
     * S1217: a VR file downloaded from a site keeps its descriptive name and loses the layout token,
     * so the filename scan calls it flat and mono and the film plays that way for its whole run. The
     * container often still carries `st3d`/`sv3d` boxes. Read them off the main thread and correct
     * the render config; arriving a beat late is plainly better than never arriving.
     *
     * Deliberately one-directional - it only ever turns MONO into stereo. A metadata verdict that
     * says mono is not allowed to overrule a filename that said 360 or SBS, because the diagnostic
     * test media is named correctly and would regress on files whose boxes are less complete than
     * their names.
     */
    private fun correctStereoFromMetadata(file: File, current: RenderConfig) {
        if (current.layout != StereoLayout.MONO) return
        lifecycleScope.launch(Dispatchers.IO) {
            val fromBoxes = stereoConfigResolver.resolveFromMetadata(file.absolutePath)
            if (fromBoxes == null || fromBoxes.layout == StereoLayout.MONO) return@launch
            withContext(Dispatchers.Main) { applyStereoFromMetadata(fromBoxes, file.name) }
        }
    }

    private fun applyStereoFromMetadata(config: RenderConfig, filename: String) {
        if (isFinishing || isDestroyed) return
        Timber.d(
            "VrStereo: container metadata corrected layout to %s projection=%s file=%s",
            config.layout,
            config.projection,
            filename,
        )
        runtime.setRenderConfig(config.projection.value, config.layout.value)
        hudRenderer.depthRowVisible = true
        if (isPanelHudMode()) scheduleHudPanelRepaint()
    }

    private fun loadCurrentMediaItem() {
        val item = mediaPlaylist[currentPlaylistIndex]
        val file = item.file
        Timber.d("Loading media item at index $currentPlaylistIndex: ${file.name}")

        hudRenderer.currentFilename = file.name
        playbackCtrl.release()

        val config = stereoConfigResolver.resolve(file.name)
        runtime.setRenderConfig(config.projection.value, config.layout.value)
        hudRenderer.depthRowVisible = config.layout != StereoLayout.MONO
        if (isPanelHudMode()) {
            refreshTrackRowsAndRepaint()
        } else {
            hudBanner.queueFilename(file.name, config.projection, config.layout)
        }
        if (item.mediaType == VrMediaType.VIDEO) {
            correctStereoFromMetadata(file, config)
        }

        // S1233: the carried type decides, not the extension - see the PlaylistItem note.
        if (item.mediaType == VrMediaType.IMAGE) {
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
                    // S1221: the banner, the resolved layout and the playlist index have all already
                    // advanced to this file, so simply returning left the PREVIOUS slide on the quad
                    // wearing the new file's label. That is what got reported as broken stereo
                    // detection and cost a session's diagnosis. The banner must never describe a
                    // frame that is not there.
                    Timber.w("Failed to decode image ${file.name}; surfacing the failure")
                    if (isPanelHudMode()) {
                        withContext(Dispatchers.Main) {
                            hudRenderer.currentFilename = "$DECODE_FAILED_LABEL ${file.name}"
                            refreshTrackRowsAndRepaint()
                        }
                    } else {
                        hudBanner.queueError(file.name, DECODE_FAILED_LABEL)
                    }
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
            if (isFinishing || isDestroyed) return@runOnUiThread
            // S1223: the press that closes the legend does nothing else. A deflection that both
            // dismissed and seeked would teach the user that the binding they just read about does
            // something unrelated to what it said.
            if (legendController?.dismiss() == true) return@runOnUiThread
            // S1232: summon is checked before the playlist guard below. Bringing the panel back is
            // valid with an empty playlist - navigation is not - and gating it on media would make
            // a hidden HUD unrecoverable in exactly the state where the user needs to reach exit.
            if (eventType == INPUT_EVENT_HUD_SUMMON) {
                runtime.setHudVisible(true)
                hudVisible = true
                // S1239: the bar is stale by however long the strip was hidden - refresh before
                // resuming the tick so the handle is never seen in its pre-hide position.
                refreshSeekPosition()
                seekTicker.start()
                return@runOnUiThread
            }
            // S1240: seeking is about the item already playing, so it is not gated on the playlist
            // the way stepping between files is.
            when (eventType) {
                INPUT_EVENT_SEEK_FORWARD -> {
                    playbackController.seekBy(SEEK_STEP_MS)
                    return@runOnUiThread
                }
                INPUT_EVENT_SEEK_BACK -> {
                    playbackController.seekBy(-SEEK_STEP_MS)
                    return@runOnUiThread
                }
            }
            if (mediaPlaylist.isEmpty()) return@runOnUiThread
            when (eventType) {
                INPUT_EVENT_NEXT -> navigateToNextMedia()
                INPUT_EVENT_PREV -> navigateToPrevMedia()
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
            // S1223: only a real press closes the legend. Dismissing on hover would take it away
            // the moment the ray crossed it, which is before anyone has read it.
            if (isClick && legendController?.dismiss() == true) return@runOnUiThread
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
        // S1239: stop the tick before the player goes away, so no repaint outlives it.
        seekTicker.stop()
        playbackCtrl.release()
        shutdownRenderThreadSync(PAUSE_SHUTDOWN_TIMEOUT_MS)
    }

    override fun onDestroy() {
        // onPause normally already tore everything down (Phase 09); this is the belt-and-braces path for the rare process-death-after-pause case where onPause did not get to finish. Same ordering rule: ExoPlayer release before native shutdown.
        seekTicker.stop()
        playbackCtrl.release()
        shutdownRenderThreadSync(SHUTDOWN_TIMEOUT_MS)
        hudRepaintHandler.removeCallbacksAndMessages(null)
        subtitleController?.release()
        subtitleController = null
        textureDecoder.releaseBuffer()
        hudBanner.releaseBuffers()
        legendController?.release()
        legendController = null
        reusablePanelHudBuffer = null
        reusablePanelHudBytes = null
        // S1640: unsubscribe at the terminal boundary, never in surfaceDestroyed - the surface is
        // recreated while the activity lives, and removing there would leave it without callbacks.
        if (::surfaceView.isInitialized) {
            surfaceView.holder.removeCallback(this)
        }
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
                Timber.d("render thread done; returning to panel target")
                returnDispatcher.deliverReturnAndFinish(VrLaunchResult.CompletedNormally)
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
            val item = mediaPlaylist[currentPlaylistIndex]
            val file = item.file
            val config = stereoConfigResolver.resolve(file.name)
            // A native session that has just become ready owns a freshly created 1x1 placeholder
            // HUD texture, and the prior session's shutdown cleared any pending HUD bytes. Re-queue
            // the current item's filename banner here for EVERY media type so a fresh session always
            // repaints the banner instead of leaving the placeholder. Video already did this; image
            // items previously relied solely on the one-time onCreate decode path, which is not
            // re-run when a session is recreated, leaving the placeholder visible on re-entry.
            runtime.setRenderConfig(config.projection.value, config.layout.value)
            hudRenderer.depthRowVisible = config.layout != StereoLayout.MONO
            hudRenderer.currentFilename = file.name
            // S0964: the quad-size override persists in native state across sessions within one
            // process, so EVERY mode must (re)assert its own size here - a diagnostic launch after
            // a VR Cinema one would otherwise stretch the banner onto the panel-sized quad.
            if (isPanelHudMode()) {
                runtime.setHudQuadSize(PANEL_QUAD_WIDTH_M, PANEL_QUAD_HEIGHT_M, PANEL_QUAD_OFFSET_Y_M)
            } else {
                runtime.setHudQuadSize(BANNER_QUAD_WIDTH_M, BANNER_QUAD_HEIGHT_M, BANNER_QUAD_OFFSET_Y_M)
                hudBanner.queueFilename(file.name, config.projection, config.layout)
            }
            // S1233: was isVideoFilename, which knows only mp4/mkv - a .webm or .mov item silently
            // never restarted when a recreated session became ready.
            if (item.mediaType == VrMediaType.VIDEO) {
                if (!playbackCtrl.start(file)) {
                    hudBanner.queueError(file.name, "Playback Start Failed")
                }
            }
            if (isPanelHudMode()) {
                // After playbackCtrl.start so the panel model already reflects snapshot state;
                // track rows populate later via onTracksChanged.
                refreshTrackRows()
                renderPanelHud()
                maybeShowLegendOnFirstEntry()
            }
        }
    }

    /**
     * S1223: shown once per install. The flag is written after the legend is on screen rather than
     * before, so a process death between the two does not silently burn the single showing.
     */
    private fun maybeShowLegendOnFirstEntry() {
        lifecycleScope.launch {
            if (legendPreferences.isShown()) return@launch
            showLegend()
            legendPreferences.markShown()
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
        // Event codes raised by triggerJniInputCallback in xr_session.cpp - keep both sides in step.
        private const val INPUT_EVENT_NEXT = 1
        private const val INPUT_EVENT_PREV = 2
        private const val INPUT_EVENT_HUD_SUMMON = 3
        private const val INPUT_EVENT_SEEK_FORWARD = 4
        private const val INPUT_EVENT_SEEK_BACK = 5

        // S1240: one thumbstick deflection = one 10 s step. The number is not invented - it is what
        // docs/VR_CONTROLS.md has documented as the intended seek granularity since before the
        // binding existed.
        private const val SEEK_STEP_MS = 10_000L

        private const val MEDIA_SETTINGS_TAB_INDEX = 1
        const val SHUTDOWN_TIMEOUT_MS = 3_000L

        // S0290 Phase 09: onPause runs on the main thread; keep the join wait shorter than the standard ANR window so re-entry is responsive while still giving the render thread time to release EGL/OpenXR resources.
        const val PAUSE_SHUTDOWN_TIMEOUT_MS = 2_000L
        private const val REQUEST_CODE_HAND_TRACKING = 1001

        // S1221: shown instead of leaving the previous slide on the quad under the new file's name.
        // English literal like the neighbouring "Playback Start Failed" - the immersive HUD is a
        // diagnostic surface and is not localized.
        private const val DECODE_FAILED_LABEL = "Decode Failed"

        // S0960: bytes per ARGB_8888 / RGBA pixel for buffer sizing (panel HUD copy buffer).
        private const val RGBA_BYTES_PER_PIXEL = 4

        // S0964: world-space HUD quad sizes per mode. Banner values mirror the native defaults
        // (S0291 owner decision). The panel quad matched the 1024x640 texture aspect until S1228
        // reshaped it - see below.
        private const val BANNER_QUAD_WIDTH_M = 0.3f
        private const val BANNER_QUAD_HEIGHT_M = 0.113f

        // S1228 (owner, in-headset 2026-07-27): 0.48x0.30 m block -> 1.40x0.197 m strip, matching
        // the 2560x360 canvas. The text was ~0.015 m tall at the 1.5 m watch distance and unreadable;
        // the wider quad puts it at ~0.028 m while the flatter shape stops covering the film.
        private const val PANEL_QUAD_WIDTH_M = 1.40f
        private const val PANEL_QUAD_HEIGHT_M = 0.197f

        // S1228: drop the strip below the gaze ray so it stops covering the film. It clears the
        // S0986 subtitle quad, which spans -0.5625..-0.4375 m: the strip spans -0.3985..-0.2015 m.
        private const val PANEL_QUAD_OFFSET_Y_M = -0.30f

        // The diagnostic banner keeps the S0290 round-3 centring - it is meant to land on the
        // owner's screenshots without a head tilt.
        private const val BANNER_QUAD_OFFSET_Y_M = 0.0f

        // S0964: coalesce repaint bursts (slider drags) into one queueHud per window.
        private const val HUD_REPAINT_DEBOUNCE_MS = 100L

        // S1233: a carried list shorter than this has nothing to navigate, so the host stays on its
        // single-file path rather than pretending one file is a playlist.
        private const val MIN_NAVIGABLE_PLAYLIST_SIZE = 2

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
