package com.sza.fastmediasorter.vr

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Surface
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.StereoDetector
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import com.sza.fastmediasorter.ui.player.VrForcedFormatResolver
import com.sza.fastmediasorter.ui.player.helpers.seekForward
import com.sza.fastmediasorter.ui.player.helpers.seekBackward
import com.sza.fastmediasorter.ui.player.render.RenderPriority
import com.sza.fastmediasorter.ui.player.render.RenderTarget
import com.sza.fastmediasorter.vr.capture.VrStereoSnapshotManager
import com.sza.fastmediasorter.vr.helpers.VrRouteDecision
import com.sza.fastmediasorter.vr.helpers.VrRouteDecisionHelper
import com.sza.fastmediasorter.vr.openxr.OpenXrSessionManager
import com.sza.fastmediasorter.vr.openxr.XrRenderCallback
import com.sza.fastmediasorter.vr.render.VrLayerDescriptor
import com.sza.fastmediasorter.vr.render.VrLayerFactory
import com.sza.fastmediasorter.vr.render.VrPhotoSphereRenderer
import com.sza.fastmediasorter.vr.render.VrRenderContext
import com.sza.fastmediasorter.vr.render.VrRenderingMode
import com.sza.fastmediasorter.vr.render.VrEye
import com.sza.fastmediasorter.vr.playback.VrPlaybackEngine
import com.sza.fastmediasorter.vr.render.VrStereoRenderer
import com.sza.fastmediasorter.vr.render.VrVideoSurfaceTextureBridge
import com.sza.fastmediasorter.vr.ui.VrControlOverlayManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * VR player host — extends PlayerActivity to inherit all controls,
 * file operations and playback logic. Adds OpenXR rendering layer
 * via OpenXrSessionManager + VrStereoRenderer.
 *
 * 3D video pipeline:
 *   ExoPlayer (inherited) → [videoSurfaceBridge].surface → SurfaceTexture
 *   → OES texture → [stereoRenderer].renderEye() UV-crops per eye
 *   → OpenXR compositor (via [xrSessionManager])
 *
 * On a phone (no XR runtime), falls back to VrPhoneFallbackActivity.
 * This is a thin coordinator — no business logic directly in the Activity.
 */
@AndroidEntryPoint
class VrPlayerActivity : PlayerActivity() {

    @Inject
    lateinit var vrPlaybackEngine: VrPlaybackEngine

    @Inject
    lateinit var vrLayerFactory: VrLayerFactory

    private var xrSessionManager: OpenXrSessionManager? = null
    private var stereoRenderer: VrStereoRenderer? = null
    private var photoSphereRenderer: VrPhotoSphereRenderer? = null
    private var controlOverlay: VrControlOverlayManager? = null
    private var stereoSnapshotManager: VrStereoSnapshotManager? = null
    private val stereoDetector = StereoDetector()
    private val routeDecisionHelper = VrRouteDecisionHelper()

    @Volatile
    private var currentVrImageKey: String? = null

    private val playbackPrefs by lazy {
        getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var currentLayerDescriptor = VrLayerDescriptor()

    @Volatile
    private var cachedPlayerSourceAspectRatio = 0f

    @Volatile
    private var cachedDescriptorSourceAspectRatio = VrRenderContext.DEFAULT_SOURCE_ASPECT_RATIO

    private var currentStereoMode = StereoMode.MONO
    private var currentRenderingMode = VrRenderingMode.CINEMA
    private var playbackRouteJob: Job? = null
    private var observedVrPlayer: ExoPlayer? = null

    @Volatile
    private var xrInitializationRequested = false

    @Volatile
    private var standardPlayerFallbackLaunched = false

    /** Throttle counter for per-frame render debug logs — not persisted across sessions. */
    @Volatile
    private var dbgRenderFrameCount = 0L

    // WHY: the XR callback runs on xr-render-thread, but Media3 requires all ExoPlayer access
    // on the application's main looper. Keep render-time aspect data in a volatile cache instead.
    private val vrPlayerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            cachePlayerAspectRatio(videoSize, "player-video-size")
        }
    }

    private val renderingModeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == PlaybackControlPreferences.KEY_VR_RENDERING_MODE) {
            currentRenderingMode = VrRenderingMode.fromPreferenceValue(
                prefs.getString(PlaybackControlPreferences.KEY_VR_RENDERING_MODE, "CINEMA")
            )
            refreshLayerDescriptor("render-mode-pref")
        }
    }

    /**
     * Bridge between ExoPlayer video output and the VR GL pipeline.
     * Creates OES texture → SurfaceTexture → Surface chain.
     * The Surface is set on the inherited ExoPlayer so video frames
     * flow to VrStereoRenderer instead of the phone-screen PlayerView.
     */
    private var videoSurfaceBridge: VrVideoSurfaceTextureBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // High-priority diagnostic: use android.util.Log.e so this always appears in logcat
        // even when the logcat buffer is flooded by system (XR runtime) log spam.
        Log.e("VR_BOOT", "VrPlayerActivity.onCreate intent=${intent?.toUri(0)} savedState=$savedInstanceState")
        // Check XR runtime BEFORE super.onCreate() to avoid PlayerActivity's
        // full initialisation (binding, ExoPlayer, file list) on non-headset devices.
        Timber.i("VrPlayerActivity: onCreate ENTRY  intent=%s", intent?.toUri(0))
        val xrAvailable = isXrRuntimeAvailable()
        Timber.i("VrPlayerActivity: isXrRuntimeAvailable=%b", xrAvailable)
        if (!xrAvailable) {
            // Must call AppCompatActivity.onCreate at minimum for Activity lifecycle
            super.onCreate(savedInstanceState)
            Timber.w("VrPlayerActivity: no XR runtime — falling back to phone screen")
            startActivity(Intent(this, VrPhoneFallbackActivity::class.java))
            finish()
            return
        }

        super.onCreate(savedInstanceState)
        Timber.i("VrPlayerActivity: super.onCreate done  currentRenderingMode=%s  stereoMode=%s",
            currentRenderingMode, currentStereoMode)

        // Per-eye render callback: called by native once per eye per frame on the GL thread.
        // bridge.updateFrame() advances the SurfaceTexture — must be called only on the left eye
        // (eye == 0) to avoid consuming two frames per xrEndFrame cycle.
        val renderCallback = XrRenderCallback { eye, fbo, width, height ->
            renderVrFrame(
                VrRenderContext(
                    layerType = currentLayerDescriptor.type,
                    stereoMode = currentStereoMode,
                    eye = VrEye.fromNativeIndex(eye),
                    swapchainImageIndex = fbo,
                    renderingMode = currentRenderingMode,
                    targetWidthPx = width,
                    targetHeightPx = height,
                    sourceAspectRatio = resolveSourceAspectRatio(),
                )
            )
        }

        // Initialise VR-specific components
        // onSessionReady (= ::initializeVrRenderPipeline) runs on the GL thread after the XR
        // session is established so that bridge/renderer GL resources are created in the right context.
        xrSessionManager = OpenXrSessionManager(
            renderCallback,
            onSessionReady = ::initializeVrRenderPipeline,
            onSessionStopped = ::releaseVrRenderPipeline,
        )
        stereoRenderer = VrStereoRenderer()
        photoSphereRenderer = VrPhotoSphereRenderer(this)
        controlOverlay = VrControlOverlayManager { command ->
            handleVrCommand(command)
        }

        // Create the video → GL texture bridge.
        // initialize() requires a GL context which isn't available until the XR session
        // creates the EGL context. The bridge is created here but initialized lazily
        // in the render loop setup (OpenXrSessionManager.startEventLoop).
        videoSurfaceBridge = VrVideoSurfaceTextureBridge()
        currentRenderingMode = VrRenderingMode.fromPreferenceValue(
            playbackPrefs.getString(PlaybackControlPreferences.KEY_VR_RENDERING_MODE, "CINEMA")
        )
        stereoSnapshotManager = VrStereoSnapshotManager(
            activity = this,
            sessionProvider = { xrSessionManager },
        )
        playbackPrefs.registerOnSharedPreferenceChangeListener(renderingModeListener)
        refreshLayerDescriptor("activity-created")
        ensureVrPlayerListenerAttached()

        // Propagate stereoMode ViewModel changes to VrStereoRenderer.
        // PlayerManagerInitializer already observes this to call VideoPlayerManager.applyStereoEffect()
        // (phone-screen path), but on VR the renderer needs its own observer so that when
        // the OpenXR render loop is active, VrStereoRenderer can UV-crop per eye correctly.
        val localRenderer = stereoRenderer
        if (localRenderer != null) {
            lifecycleScope.launch {
                viewModel.stereoMode.collectLatest { mode ->
                    if (mode == StereoMode.AUTO || mode == StereoMode.UNKNOWN) {
                        // AUTO/UNKNOWN: let resolvePlaybackRoute derive the effective mode from
                        // filename/container detection. Directly resolving AUTO→MONO here would
                        // override the correct VR stereo mode that resolvePlaybackRoute already
                        // set (race: route resolves first, then this coroutine fires with AUTO).
                        Timber.d("VrPlayerActivity: stereoMode → $mode (deferring to route decision)")
                    } else {
                        // Explicit user-selected mode — apply to renderer immediately.
                        Timber.d("VrPlayerActivity: stereoMode → $mode → renderer=$mode")
                        applyStereoModeToVrRenderers(mode, "stereo-mode")
                    }
                    resolvePlaybackRoute("stereo-mode")
                }
            }
        }

        val localPhotoRenderer = photoSphereRenderer
        if (localPhotoRenderer != null) {
            lifecycleScope.launch {
                viewModel.state.collectLatest { state ->
                    syncVrImageTarget(state.currentFile, localPhotoRenderer)
                    resolvePlaybackRoute("player-state")
                }
            }
        }

        Timber.d("VrPlayerActivity: VR components initialised, XR session will start in onResume"
            + "  bridge=%s stereoRenderer=%s photoRenderer=%s renderingMode=%s",
            videoSurfaceBridge, stereoRenderer, photoSphereRenderer, currentRenderingMode)
    }

    /**
     * Called from the XR render loop (on GL thread) after OpenXR session is active.
     * Initialises the bridge and redirects ExoPlayer video output to the VR pipeline.
     *
     * Called by [OpenXrSessionManager] via its onSessionReady callback — once per XR session
     * lifecycle (i.e., also on onPause/onResume cycles). Bridge + renderer release their old
     * GL resources first so that new GL objects are created in the current EGL context.
     */
    fun initializeVrRenderPipeline() {
        Timber.i("VrPlayerActivity: initializeVrRenderPipeline START  bridge=%s renderer=%s",
            videoSurfaceBridge, stereoRenderer)
        val bridge = videoSurfaceBridge ?: run {
            Timber.e("VrPlayerActivity: initializeVrRenderPipeline — bridge is NULL, aborting")
            return
        }
        val renderer = stereoRenderer ?: run {
            Timber.e("VrPlayerActivity: initializeVrRenderPipeline — stereoRenderer is NULL, aborting")
            return
        }
        val photoRenderer = photoSphereRenderer

        // Release stale GL objects tied to any previous EGL context before re-init.
        // glDelete* calls on invalid IDs (from a destroyed context) produce GL_INVALID_VALUE
        // but do not crash — the old context's resources are freed by EGL on context destruction.
        Timber.d("VrPlayerActivity: releasing old GL resources (bridge textureId=%d)", bridge.textureId)
        bridge.release()
        renderer.release()
        photoRenderer?.release()

        // Re-create GL resources in the current (new) EGL context.
        Timber.d("VrPlayerActivity: initialising GL resources in new EGL context")
        bridge.initialize()
        Timber.i("VrPlayerActivity: bridge.initialize done — textureId=%d surface=%s isReady=%b",
            bridge.textureId, bridge.surface, bridge.isReady())
        renderer.initGl()
        Timber.d("VrPlayerActivity: renderer.initGl done")
        photoRenderer?.initGl()
        Timber.d("VrPlayerActivity: photoRenderer.initGl done (renderer=%s)", photoRenderer)

        val bridgeSurface = bridge.surface
        val isStaticImageSession = isCurrentVrStaticImageSession()
        syncVrPlayerBindingToBridgeSurface(
            bridgeSurface = bridgeSurface,
            textureId = bridge.textureId,
            isStaticImageSession = isStaticImageSession,
        )

        // The native session is now alive, so push the current layer choice again on the render thread.
        Timber.d("VrPlayerActivity: initializeVrRenderPipeline — refreshing layer descriptor")
        refreshLayerDescriptor("session-ready")
        Timber.i("VrPlayerActivity: initializeVrRenderPipeline COMPLETE")
    }

    /**
     * Release GL resources on the XR render thread.
     * Both bridge and renderer document that release() must run while their EGL context is current.
     */
    private fun releaseVrRenderPipeline() {
        videoSurfaceBridge?.release()
        stereoRenderer?.release()
        photoSphereRenderer?.release()
    }

    /**
     * Render a single XR eye using the current descriptor + frame context.
     *
     * The native layer has already bound the correct FBO, so the renderer only needs the
     * per-eye context to choose the UV crop and viewport policy for the active layer type.
     */
    private fun renderVrFrame(context: VrRenderContext) {
        val bridge = videoSurfaceBridge ?: run {
            val n = ++dbgRenderFrameCount
            if (n % 300L == 1L) Timber.w("VrPlayerActivity: renderVrFrame — bridge null (#%d)", n)
            return
        }
        val renderer = stereoRenderer ?: run {
            val n = ++dbgRenderFrameCount
            if (n % 300L == 1L) Timber.w("VrPlayerActivity: renderVrFrame — renderer null (#%d)", n)
            return
        }
        val photoRenderer = photoSphereRenderer

        if (isVrStaticImageActive() && photoRenderer != null) {
            val n = ++dbgRenderFrameCount
            if (n % 300L == 1L) {
                Timber.d("VrPlayerActivity: renderVrFrame [photo] #%d eye=%s layer=%s",
                    n, context.eye, context.layerType)
            }
            photoRenderer.renderEye(context, currentLayerDescriptor)
            return
        }

        val n = ++dbgRenderFrameCount
        if (!bridge.isReady()) {
            if (n % 300L == 1L) {
                Timber.w("VrPlayerActivity: renderVrFrame — bridge NOT ready  textureId=%d  (#%d)",
                    bridge.textureId, n)
            }
            return
        }

        // Throttled health log every 300 frames.
        if (n % 300L == 1L) {
            Timber.d("VrPlayerActivity: renderVrFrame #%d  eye=%s  layer=%s  stereo=%s  textureId=%d  w=%d h=%d",
                n, context.eye, context.layerType, context.stereoMode,
                bridge.textureId, context.targetWidthPx, context.targetHeightPx)
        }

        // Pull latest video frame into OES texture — once per frame, on the left eye.
        if (context.eye == VrEye.LEFT) bridge.updateFrame()

        renderer.renderEye(
            context = context,
            bridge.textureId,
            currentLayerDescriptor,
        )
    }

    private fun resolveSourceAspectRatio(): Float {
        val playerAspectRatio = cachedPlayerSourceAspectRatio
        if (playerAspectRatio > 0f) {
            return playerAspectRatio
        }

        val descriptorAspectRatio = cachedDescriptorSourceAspectRatio
        if (descriptorAspectRatio > 0f) {
            return descriptorAspectRatio
        }

        Timber.w("VrPlayerActivity: resolveSourceAspectRatio — no cached aspect ratio, using default")
        return VrRenderContext.DEFAULT_SOURCE_ASPECT_RATIO
    }

    private fun ensureVrPlayerListenerAttached() {
        val player = videoPlayerManager.exoPlayer
        if (player == null) {
            detachVrPlayerListener()
            return
        }

        if (observedVrPlayer === player) {
            cachePlayerAspectRatio(player.videoSize, "listener-refresh")
            return
        }

        observedVrPlayer?.let { previousPlayer ->
            try {
                previousPlayer.removeListener(vrPlayerListener)
            } catch (t: Throwable) {
                Timber.w(t, "VrPlayerActivity: failed to detach old VR player listener")
            }
        }

        observedVrPlayer = player
        player.addListener(vrPlayerListener)
        cachePlayerAspectRatio(player.videoSize, "listener-attached")
    }

    private fun detachVrPlayerListener() {
        observedVrPlayer?.let { player ->
            try {
                player.removeListener(vrPlayerListener)
            } catch (t: Throwable) {
                Timber.w(t, "VrPlayerActivity: failed to detach VR player listener")
            }
        }
        observedVrPlayer = null
        cachedPlayerSourceAspectRatio = 0f
    }

    private fun cachePlayerAspectRatio(videoSize: VideoSize?, reason: String) {
        val width = videoSize?.width ?: 0
        val height = videoSize?.height ?: 0
        if (width > 0 && height > 0) {
            val aspectRatio = width.toFloat() / height.toFloat()
            cachedPlayerSourceAspectRatio = aspectRatio
            Timber.d(
                "VrPlayerActivity: cached player aspect ratio=%.4f (reason=%s size=%dx%d)",
                aspectRatio,
                reason,
                width,
                height,
            )
            return
        }

        cachedPlayerSourceAspectRatio = 0f
        Timber.d("VrPlayerActivity: player aspect ratio unavailable (reason=%s size=%s)", reason, videoSize)
    }

    private fun syncVrPlayerBindingToBridgeSurface(
        bridgeSurface: Surface?,
        textureId: Int,
        isStaticImageSession: Boolean,
    ) {
        // WHY: both videoSize reads and setVideoSurface() must happen on the player's main thread.
        // Calling them from xr-render-thread floods logcat with IllegalStateException and starves VR rendering.
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread

            ensureVrPlayerListenerAttached()

            val exoPlayerInstance = videoPlayerManager.exoPlayer
            val videoSize = exoPlayerInstance?.videoSize
            cachePlayerAspectRatio(videoSize, "session-ready")

            Timber.i("VrPlayerActivity: ExoPlayer instance=%s  videoSize=%s  bridgeSurface=%s",
                exoPlayerInstance, videoSize, bridgeSurface)
            if (isStaticImageSession) {
                // Static VR images are rendered from a preloaded bitmap, so a missing ExoPlayer video
                // surface is expected and must not be treated as a black-screen failure.
                Timber.i("VrPlayerActivity: static-image immersive session — skipping ExoPlayer surface redirect")
            } else if (bridgeSurface != null && exoPlayerInstance != null) {
                exoPlayerInstance.setVideoSurface(bridgeSurface)
                Timber.i("VrPlayerActivity: ExoPlayer video redirected to VR bridge surface (textureId=%d)",
                    textureId)
            } else {
                // This is a critical error — video will render to the hidden PlayerView and VR shows black.
                // Possible causes: (a) exoPlayer not yet created (rare timing race on slow network sources),
                // (b) bridge.initialize() produced a null Surface.
                Timber.e("VrPlayerActivity: CANNOT redirect ExoPlayer to VR surface — exoPlayer=%s  bridgeSurface=%s  (VR will be BLACK!)",
                    exoPlayerInstance, bridgeSurface)
                Log.e("VR_BOOT", "VrPlayerActivity: surface redirect FAILED — exoPlayer=$exoPlayerInstance bridgeSurface=$bridgeSurface")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ensureVrPlayerListenerAttached()
        Timber.i("VrPlayerActivity: onResume — resolving whether this file needs XR or standard playback")
        resolvePlaybackRoute("onResume")
    }

    override fun onPause() {
        Timber.i("VrPlayerActivity: onPause — clearing video surface and releasing XR session")
        // Detach bridge surface from ExoPlayer BEFORE stopping the render loop so ExoPlayer
        // does not write frames to the bridge SurfaceTexture while it is being released.
        // setVideoSurface(null) re-routes output back to the hidden PlayerView, which
        // is safe and avoids IllegalStateException in the video decoder.
        val player = videoPlayerManager.exoPlayer
        if (player != null) {
            player.setVideoSurface(null)
            Timber.d("VrPlayerActivity: onPause — ExoPlayer surface cleared (player=%s)", player)
        } else {
            Timber.d("VrPlayerActivity: onPause — ExoPlayer not active, no surface to clear")
        }
        // Stop the XR render loop before super.onPause() pauses ExoPlayer,
        // so we don't render stale frames into the compositor while pausing.
        xrSessionManager?.release()
        super.onPause()
        Timber.i("VrPlayerActivity: onPause COMPLETE")
    }

    override fun onDestroy() {
        Timber.i("VrPlayerActivity: onDestroy  dbgRenderFrameCount=%d", dbgRenderFrameCount)
        // GL resources are released from OpenXrSessionManager.onSessionStopped on the render thread.
        // Only clear object references here to avoid calling GLES APIs from the main thread.
        playbackRouteJob?.cancel()
        playbackRouteJob = null
        playbackPrefs.unregisterOnSharedPreferenceChangeListener(renderingModeListener)
        detachVrPlayerListener()
        stereoSnapshotManager = null
        videoSurfaceBridge = null
        stereoRenderer = null
        photoSphereRenderer = null
        xrSessionManager?.release()
        xrSessionManager = null
        super.onDestroy()
        Timber.i("VrPlayerActivity: onDestroy COMPLETE")
    }

    /**
     * Called by the system when this singleTask activity is already running and a new
     * intent arrives (e.g. the user opens a second video from BrowseActivity while
     * VrPlayerActivity is the active task).
     *
     * Strategy: update the activity intent and recreate so the ViewModel reads the
     * new resourceId / initialIndex from SavedStateHandle. Recreation is the safest
     * approach because it gives us a clean ExoPlayer + XR session — avoiding the
     * complexity of reinitialising all managers mid-session.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.e("VR_BOOT", "VrPlayerActivity.onNewIntent new=${intent.toUri(0)}")
        Timber.i("VrPlayerActivity: onNewIntent — new video requested, recreating activity. intent=%s", intent.toUri(0))
        forceStopVrPlayback("new-intent-recreate")
        // setIntent() replaces the activity's current intent so that after recreate()
        // the new ViewModel's SavedStateHandle sees the new resourceId / initialIndex.
        setIntent(intent)
        recreate()
    }

    override fun saveCurrentFrame() {
        // VR snapshots must come from the XR eye swapchains, not the hidden PlayerView TextureView.
        stereoSnapshotManager?.captureCurrentFrame() ?: super.saveCurrentFrame()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_X -> {
                    Timber.i("VrPlayerActivity: X button (left controller) → exit immersive")
                    exitVrAndStopPlayback("controller-x-exit")
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_B -> {
                    // B button (right controller) = conventional "back/exit" on Quest controllers.
                    // Provides an always-reachable escape even when overlay is not visible.
                    Timber.i("VrPlayerActivity: B button (right controller) → exit immersive")
                    exitVrAndStopPlayback("controller-b-exit")
                    return true
                }
                KeyEvent.KEYCODE_MENU -> {
                    // Left controller ≡ button — open the playback controls dialog (3D, volume, brightness..).
                    Timber.i("VrPlayerActivity: Menu button (left controller ≡) → showPlaybackControlDialog")
                    dialogHelper.showPlaybackControlDialog()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    // BACK in VR immersive = always exit; do not pass to super.
                    // On Quest the ≡ left-controller button also sends BACK. We always exit here
                    // so the user is never trapped in immersive with no escape route.
                    Timber.i("VrPlayerActivity: Back/≡ button → exit immersive")
                    exitVrAndStopPlayback("back-button-exit")
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    internal fun captureStereoSnapshotFromCommand(): Boolean {
        val snapshotManager = stereoSnapshotManager ?: return false
        snapshotManager.captureCurrentFrame()
        return true
    }

    internal fun toggleVrControlOverlayFromCommand(): Boolean {
        val overlay = controlOverlay ?: return false
        // XR command input should toggle the headset overlay instead of mutating Android bars.
        overlay.toggle()
        return true
    }

    /**
     * Dispatch a command from the VR overlay to the inherited player.
     */
    private fun handleVrCommand(command: PlaybackCommand) {
        Timber.d("VrPlayerActivity: handling VR command $command")
        when (command) {
            PlaybackCommand.Play -> viewModel.togglePause()
            PlaybackCommand.Pause -> viewModel.togglePause()
            PlaybackCommand.SeekForward -> videoPlayerManager.seekForward(VR_SEEK_SECONDS)
            PlaybackCommand.SeekBackward -> videoPlayerManager.seekBackward(VR_SEEK_SECONDS)
            PlaybackCommand.NextFile -> viewModel.nextFile()
            PlaybackCommand.PreviousFile -> viewModel.previousFile()
            PlaybackCommand.OpenControls -> dialogHelper.showPlaybackControlDialog()
            PlaybackCommand.Exit -> exitVrAndStopPlayback("overlay-exit-command")
            else -> Timber.w("VrPlayerActivity: unhandled VR command $command")
        }
    }

    private fun exitVrAndStopPlayback(reason: String) {
        forceStopVrPlayback(reason)
        finish()
    }

    private fun launchVrFailureRecovery(
        userMessage: String,
        reason: String,
        shouldFinish: Boolean,
    ) {
        // XR init failure leaves the inherited player alive on the hidden PlayerView.
        // Force-stop playback before surfacing the error so the user never gets audio-only VR limbo.
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                Timber.w("VrPlayerActivity: skip VR failure recovery — activity is finishing/destroyed (%s)", reason)
                return@runOnUiThread
            }
            Timber.e("VrPlayerActivity: launchVrFailureRecovery reason=%s shouldFinish=%b", reason, shouldFinish)
            forceStopVrPlayback(reason)
            showError(userMessage)
            if (shouldFinish) {
                window.decorView.post { finish() }
            }
        }
    }

    private fun resolvePlaybackRoute(reason: String) {
        if (standardPlayerFallbackLaunched || isFinishing || isDestroyed) return

        val currentFile = viewModel.state.value.currentFile ?: run {
            Timber.d("VrPlayerActivity: resolvePlaybackRoute(%s) postponed — currentFile not ready", reason)
            return
        }

        playbackRouteJob?.cancel()
        playbackRouteJob = lifecycleScope.launch {
            val routeDecision = buildRouteDecision(currentFile, viewModel.stereoMode.value)
            if (standardPlayerFallbackLaunched || isFinishing || isDestroyed) return@launch

            applyStereoModeToVrRenderers(routeDecision.effectiveStereoMode, "route-decision")

            when (routeDecision.route) {
                VrLaunchRoute.STANDARD_PANEL_FALLBACK -> {
                    launchStandardPlayerFallback(currentFile, reason)
                }

                VrLaunchRoute.IMMERSIVE_VIDEO,
                VrLaunchRoute.IMMERSIVE_STATIC_IMAGE -> {
                    if (!xrInitializationRequested) {
                        startXrInitialization(reason, routeDecision)
                    }
                }

                VrLaunchRoute.UNSUPPORTED_IMMERSIVE_WITH_MESSAGE -> {
                    launchUnsupportedImmersiveFallback(currentFile, reason, routeDecision)
                }
            }
        }
    }

    private suspend fun buildRouteDecision(
        currentFile: MediaFile,
        requestedStereoMode: StereoMode,
    ): VrRouteDecision {
        val settings = viewModel.getSettings()
        val effectiveMode = resolveLaunchStereoMode(currentFile, requestedStereoMode, settings.vrAutoDetectFormat)
        val routeDecision = routeDecisionHelper.decide(currentFile, effectiveMode, settings)
        Timber.i(
            "VrPlayerActivity: route decision file=%s type=%s requested=%s effective=%s autoDetect=%b route=%s reason=%s",
            currentFile.path,
            currentFile.type,
            requestedStereoMode,
            routeDecision.effectiveStereoMode,
            settings.vrAutoDetectFormat,
            routeDecision.route,
            routeDecision.logReason,
        )
        return routeDecision
    }

    private suspend fun resolveLaunchStereoMode(
        currentFile: MediaFile,
        requestedStereoMode: StereoMode,
        autoDetectEnabled: Boolean,
    ): StereoMode {
        if (requestedStereoMode != StereoMode.AUTO && requestedStereoMode != StereoMode.UNKNOWN) {
            return requestedStereoMode
        }

        val detectedByFilename = stereoDetector.detectFromFilename(currentFile.path)
        val detectedMode = if (detectedByFilename != StereoMode.UNKNOWN) {
            detectedByFilename
        } else if (currentFile.width != null && currentFile.height != null) {
            stereoDetector.detectFromDimensions(currentFile.width, currentFile.height)
        } else {
            StereoMode.UNKNOWN
        }

        if (!autoDetectEnabled && !detectedMode.isSpherical()) {
            // When flat 3D auto-detection is disabled, ordinary videos should follow the
            // standard player path instead of being forced through the XR cinema host.
            return StereoMode.MONO
        }

        val settings = viewModel.getSettings()
        return VrForcedFormatResolver.resolve(
            detected = detectedMode,
            perFileOverride = null,
            forcedPlat = VrForcedFormatResolver.mapPlatSetting(settings.vrForcedPlatFormat),
            forcedSpherical = VrForcedFormatResolver.mapSphericalSetting(settings.vrForcedSphericalFormat),
        )
    }

    private fun launchStandardPlayerFallback(currentFile: MediaFile, reason: String) {
        if (standardPlayerFallbackLaunched || isFinishing || isDestroyed) return

        standardPlayerFallbackLaunched = true
        playbackRouteJob?.cancel()
        playbackRouteJob = null

        // VR flavor routes every launch into VrPlayerActivity, so non-VR media must be
        // re-targeted explicitly to PlayerActivity to preserve standard audio/2D behavior.
        Timber.i(
            "VrPlayerActivity: launching standard PlayerActivity fallback file=%s type=%s reason=%s",
            currentFile.path,
            currentFile.type,
            reason,
        )
        forceStopVrPlayback("standard-player-fallback:$reason")
        startActivity(Intent(intent).apply {
            setClass(this@VrPlayerActivity, PlayerActivity::class.java)
        })
        finish()
    }

    private fun launchUnsupportedImmersiveFallback(
        currentFile: MediaFile,
        reason: String,
        routeDecision: VrRouteDecision,
    ) {
        if (standardPlayerFallbackLaunched || isFinishing || isDestroyed) return

        standardPlayerFallbackLaunched = true
        playbackRouteJob?.cancel()
        playbackRouteJob = null

        Timber.w(
            "VrPlayerActivity: unsupported immersive route file=%s type=%s stereo=%s reason=%s",
            currentFile.path,
            currentFile.type,
            routeDecision.effectiveStereoMode,
            reason,
        )
        forceStopVrPlayback("unsupported-immersive:$reason")
        showError(getString(routeDecision.userMessageResId ?: R.string.vr_immersive_unsupported_media))

        // Give the shared error pipeline one UI frame to enqueue its toast/dialog before the
        // activity hands control to PlayerActivity. Without this delay the fallback is silent.
        window.decorView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            startActivity(Intent(intent).apply {
                setClass(this@VrPlayerActivity, PlayerActivity::class.java)
            })
            finish()
        }, VR_FALLBACK_ERROR_DELAY_MS)
    }

    private fun startXrInitialization(reason: String, routeDecision: VrRouteDecision) {
        xrInitializationRequested = true
        Timber.i("VrPlayerActivity: starting XR init (reason=%s route=%s)", reason, routeDecision.route)
        // initialize() blocks up to 5 s waiting for nativeInitialize — run off main thread.
        // The decision is delayed until media classification is available so ordinary video/audio
        // can stay on the standard player path inside the VR flavor.
        lifecycleScope.launch(Dispatchers.IO) {
            Timber.d("VrPlayerActivity: [IO] xrSessionManager.initialize starting")
            val ok = xrSessionManager?.initialize(this@VrPlayerActivity) ?: false
            Timber.i("VrPlayerActivity: [IO] xrSessionManager.initialize returned ok=%b  isFinishing=%b", ok, isFinishing)
            if (!ok && !isFinishing) {
                // XR session failed — fall back to flat cinema mode so the user can still see
                // the content as a 2D panel in the headset and fix stereo settings without
                // having to close and reopen the player.
                // initializeVrRenderPipeline() was never called at this point, so ExoPlayer
                // is already rendering to the regular PlayerView — no surface change needed.
                Timber.w("VrPlayerActivity: XR session could not start — falling back to flat cinema mode")
                Log.e("VR_BOOT", "VrPlayerActivity: XR session FAILED — falling back to flat cinema mode")
                fallbackToFlatCinemaMode("xr-session-init-failed")
            }
        }
    }

    /**
     * Called when the XR session fails to initialize.
     *
     * Instead of stopping playback and closing the activity, we leave ExoPlayer running
     * on the regular PlayerView — which the headset compositor shows as a flat 2D panel.
     * The user can see the content, open the settings menu to correct the stereo format,
     * and re-open the file when ready.
     *
     * Important: when XR init fails, [initializeVrRenderPipeline] was never called,
     * so ExoPlayer is still on the PlayerView surface — no surface redirect is needed here.
     */
    private fun fallbackToFlatCinemaMode(reason: String) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            Timber.w("VrPlayerActivity: fallbackToFlatCinemaMode (reason=%s) — XR unavailable, keeping flat playback", reason)
            // Allow a fresh XR attempt when the user changes stereo mode after a failed init.
            xrInitializationRequested = false
            // Release partial XR state to free resources (no active render loop to stop).
            try {
                xrSessionManager?.release()
            } catch (t: Throwable) {
                Timber.w(t, "VrPlayerActivity: fallbackToFlatCinemaMode — XR release failed, ignoring")
            }
            // Non-blocking info toast: user sees content in flat mode and can access
            // player settings to fix the stereo format, then re-open the file.
            showError(getString(R.string.vr_xr_fallback_flat_mode))
        }
    }

    private fun forceStopVrPlayback(reason: String) {
        Timber.w("VrPlayerActivity: forceStopVrPlayback reason=%s", reason)

        try {
            lifecycleManager.saveCurrentPlaybackPosition()
        } catch (t: Throwable) {
            Timber.w(t, "VrPlayerActivity: forceStopVrPlayback — saveCurrentPlaybackPosition failed")
        }

        try {
            videoPlayerManager.exoPlayer?.setVideoSurface(null)
        } catch (t: Throwable) {
            Timber.w(t, "VrPlayerActivity: forceStopVrPlayback — clearing video surface failed")
        }

        try {
            stopVideoPlayback()
        } catch (t: Throwable) {
            Timber.e(t, "VrPlayerActivity: forceStopVrPlayback — stopVideoPlayback failed")
        }

        try {
            xrSessionManager?.release()
        } catch (t: Throwable) {
            Timber.w(t, "VrPlayerActivity: forceStopVrPlayback — XR release failed")
        }
    }

    private fun applyStereoModeToVrRenderers(mode: StereoMode, reason: String) {
        if (currentStereoMode == mode) return

        currentStereoMode = mode
        stereoRenderer?.setStereoMode(mode)
        photoSphereRenderer?.setStereoMode(mode)
        refreshLayerDescriptor(reason)
    }

    private fun refreshLayerDescriptor(reason: String) {
        val descriptor = vrLayerFactory.describe(currentStereoMode, currentRenderingMode)
        currentLayerDescriptor = descriptor
        cachedDescriptorSourceAspectRatio = if (descriptor.heightMeters > 0f) {
            descriptor.widthMeters / descriptor.heightMeters
        } else {
            VrRenderContext.DEFAULT_SOURCE_ASPECT_RATIO
        }
        xrSessionManager?.updateLayerDescriptor(descriptor)
        Timber.d(
            "VrPlayerActivity: layer descriptor → %s (reason=%s, stereo=%s, renderMode=%s)",
            descriptor.type,
            reason,
            currentStereoMode,
            currentRenderingMode,
        )
    }

    private suspend fun syncVrImageTarget(
        currentFile: MediaFile?,
        renderer: VrPhotoSphereRenderer,
    ) {
        if (currentFile?.type != MediaType.IMAGE) {
            currentVrImageKey = null
            return
        }

        val detected = stereoDetector.detectForImage(
            path = currentFile.path,
            width = currentFile.width,
            height = currentFile.height,
        )
        viewModel.setAutoDetectedStereoMode(detected)

        val imageKey = buildVrImageKey(currentFile)
        if (currentVrImageKey == imageKey) return
        currentVrImageKey = imageKey

        // XR static images need a dedicated bitmap upload path because there is no video decoder
        // or SurfaceTexture to feed frames into the OpenXR render loop.
        renderer.render(
            RenderTarget(
                mediaFile = currentFile,
                path = currentFile.path,
                priority = RenderPriority.NEXT,
            )
        )
    }

    private fun isVrStaticImageActive(): Boolean {
        val currentFile = viewModel.state.value.currentFile ?: return false
        return currentFile.type == MediaType.IMAGE && photoSphereRenderer?.hasRenderableTexture() == true
    }

    private fun isCurrentVrStaticImageSession(): Boolean {
        val currentFile = viewModel.state.value.currentFile ?: return false
        return currentFile.type == MediaType.IMAGE &&
            (currentStereoMode.isSpherical() || currentStereoMode.isStereoscopic())
    }

    private fun buildVrImageKey(file: MediaFile): String = listOf(
        file.path,
        file.size,
        file.lastModified,
        file.width,
        file.height,
    ).joinToString("|")

    /**
     * Check if an OpenXR runtime is available on this device.
     * On phones this will return false; on Quest headsets it returns true.
     * Result is cached — System.loadLibrary is idempotent but the check only needs to run once.
     */
    private fun isXrRuntimeAvailable(): Boolean = xrAvailable

    companion object {
        /** Seek increment for VR controller seek commands (seconds). */
        private const val VR_SEEK_SECONDS = 15
        private const val VR_FALLBACK_ERROR_DELAY_MS = 350L

        /** Cached XR runtime probe — avoids repeated loadLibrary calls. */
        private val xrAvailable: Boolean by lazy {
            try {
                System.loadLibrary("openxr_loader")
                true
            } catch (_: UnsatisfiedLinkError) {
                Timber.d("VrPlayerActivity: OpenXR loader not available")
                false
            }
        }
    }
}
