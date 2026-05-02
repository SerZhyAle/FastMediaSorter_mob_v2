package com.sza.fastmediasorter.vr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.view.isVisible
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.StereoDetector
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import com.sza.fastmediasorter.ui.player.entry.VrTaskTransition
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import com.sza.fastmediasorter.ui.player.VrForcedFormatResolver
import com.sza.fastmediasorter.ui.player.helpers.seekForward
import com.sza.fastmediasorter.ui.player.helpers.seekBackward
import com.sza.fastmediasorter.ui.player.render.RenderPriority
import com.sza.fastmediasorter.ui.player.render.RenderTarget
import com.sza.fastmediasorter.vr.capture.VrStereoSnapshotManager
import com.sza.fastmediasorter.vr.helpers.VrControllerInputManager
import com.sza.fastmediasorter.vr.helpers.VrCommandSource
import com.sza.fastmediasorter.vr.helpers.VrRecentDestinationsPrefs
import com.sza.fastmediasorter.vr.helpers.VrRouteDecision
import com.sza.fastmediasorter.vr.helpers.VrRouteDecisionHelper
import com.sza.fastmediasorter.vr.helpers.VrToggleButtonManager
import com.sza.fastmediasorter.vr.openxr.OpenXrNative
import com.sza.fastmediasorter.vr.openxr.OpenXrSessionManager
import com.sza.fastmediasorter.vr.openxr.XrHand
import com.sza.fastmediasorter.vr.openxr.XrRenderCallback
import com.sza.fastmediasorter.vr.render.VrHudRenderer
import com.sza.fastmediasorter.vr.render.VrHudSceneComposer
import com.sza.fastmediasorter.vr.render.VrHudSceneDriver
import com.sza.fastmediasorter.vr.render.VrInteractivePanelComposer
import com.sza.fastmediasorter.vr.render.VrInteractivePanelDriver
import com.sza.fastmediasorter.vr.render.VrInteractivePanelRenderer
import com.sza.fastmediasorter.vr.render.VrLayerDescriptor
import com.sza.fastmediasorter.vr.render.VrLayerFactory
import com.sza.fastmediasorter.vr.render.VrPhotoSphereRenderer
import com.sza.fastmediasorter.vr.render.VrRenderContext
import com.sza.fastmediasorter.vr.render.VrRenderingMode
import com.sza.fastmediasorter.vr.render.VrEye
import com.sza.fastmediasorter.vr.playback.VrPlaybackEngine
import com.sza.fastmediasorter.vr.render.VrStereoRenderer
import com.sza.fastmediasorter.vr.render.VrVideoSurfaceTextureBridge
import com.sza.fastmediasorter.vr.ui.VrCheatsheetOverlayManager
import com.sza.fastmediasorter.vr.ui.VrControlOverlayManager
import com.sza.fastmediasorter.vr.ui.VrFileOpsOverlayManager
import com.sza.fastmediasorter.vr.ui.VrControllerRayManager
import com.sza.fastmediasorter.vr.ui.VrHandRayManager
import com.sza.fastmediasorter.vr.ui.VrPanelHitZoneResolver
import com.sza.fastmediasorter.vr.ui.VrRayPanelHitTester
import com.sza.fastmediasorter.vr.ui.VrHudIndicatorManager
import com.sza.fastmediasorter.vr.ui.VrHudSink
import com.sza.fastmediasorter.vr.ui.VrZoomManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    @Inject
    lateinit var vrRecentDestinationsPrefs: VrRecentDestinationsPrefs

    private var xrSessionManager: OpenXrSessionManager? = null
    private var stereoRenderer: VrStereoRenderer? = null
    private var photoSphereRenderer: VrPhotoSphereRenderer? = null
    private var controlOverlay: VrControlOverlayManager? = null
    private var stereoSnapshotManager: VrStereoSnapshotManager? = null
    private val stereoDetector = StereoDetector()
    private val routeDecisionHelper = VrRouteDecisionHelper()

    // S0026: stereo mode hint from BrowseEventHandler. When present and resolvable, primes
    // resolveLaunchStereoMode so the inner route decision sees the actual file format.
    // Single-use — consumed on the first route resolution and cleared.
    private var initialDetectedStereoModeHint: StereoMode? = null

    // ── VR immersive controls (spec_vr-immersive-controls-tech) ──────────────
    private var vrInputManager: VrControllerInputManager? = null
    private var vrZoomManager: VrZoomManager? = null
    // Active HUD backend. Phone fallback uses vrHudFallback (decorView Android view);
    // immersive XR sessions swap in vrHudSceneDriver (OpenXR composition layer)
    // for the lifetime of the session.
    internal var vrHudManager: VrHudSink? = null
    private var vrHudFallback: VrHudIndicatorManager? = null
    private var vrHudRenderer: VrHudRenderer? = null
    private var vrHudSceneDriver: VrHudSceneDriver? = null
    private var vrInteractivePanelDriver: VrInteractivePanelDriver? = null
    private var lastHoveredZoneId = -1
    private var currentPanelSpeed = 1.0f
    private var lastSeekDragFraction = -1f
    private var vrHudProgressJob: Job? = null
    private var vrCheatsheetManager: VrCheatsheetOverlayManager? = null
    private var vrFileOpsManager: VrFileOpsOverlayManager? = null
    private var vrHandRayManager: VrHandRayManager? = null
    private var vrControllerRayManager: VrControllerRayManager? = null
    private var audioManager: AudioManager? = null
    private var cheatsheetAutoShown = false

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
    private var xrInitStartedAtMs = 0L

    @Volatile
    private var vrFirstFrameLoggedMs = 0L

    @Volatile
    private var standardPlayerFallbackLaunched = false

    // True when the user explicitly tapped "3DVR" from panel mode — bypasses stereo-detection gate.
    private var forceImmersiveThisLaunch = false
    // True when the user explicitly tapped "3DVR" from immersive mode — forces panel route
    // even for spherical/stereoscopic content that would otherwise auto-enter immersive.
    private var forcePanelThisLaunch = false
    // Throttle for onGenericMotionEvent → reportActivity() to avoid flooding HUD state machine.
    private var lastActivityReportMs = 0L
    private var vrToggleButtonManager: VrToggleButtonManager? = null

    // Pending VR surface redirect: captured when the XR session becomes ready before ExoPlayer
    // is created, and flushed as soon as VideoPlayerManager reports a fresh player (see
    // onPlayerCreated hook). Without this, early XR readiness results in a silent black screen.
    @Volatile
    private var pendingVrBridgeSurface: Surface? = null
    @Volatile
    private var pendingVrBridgeTextureId: Int = -1

    // True once the VR surface has been attached to ExoPlayer — drives the toggle button state
    // so it only flips to "Exit 3D" after immersive actually renders.
    @Volatile
    private var vrRenderingActive: Boolean = false

    /** Throttle counter for per-frame render debug logs — not persisted across sessions. */
    @Volatile
    private var dbgRenderFrameCount = 0L
    private var vrFpsFrameCount = 0
    private var vrFpsLastUpdateTime = 0L
    private var vrFpsLastValid: Int = 0

    private val mainHandler = Handler(Looper.getMainLooper())

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

        forceImmersiveThisLaunch = intent.getBooleanExtra(EXTRA_FORCE_IMMERSIVE, false)
        Timber.i("VrPlayerActivity: forceImmersiveThisLaunch=%b", forceImmersiveThisLaunch)
        forcePanelThisLaunch = intent.getBooleanExtra(EXTRA_FORCE_PANEL, false)

        initialDetectedStereoModeHint = intent.getStringExtra(PlayerActivity.EXTRA_DETECTED_STEREO_MODE)
            ?.let { name -> runCatching { StereoMode.valueOf(name) }.getOrNull() }
        Timber.i("VrPlayerActivity: initial detected stereo hint=%s", initialDetectedStereoModeHint)

        // Wire the immersive toggle button (VR flavor only — button is always in the layout but hidden in other flavors).
        vrToggleButtonManager = VrToggleButtonManager(
            button = safeViews.btn3dVrCmd,
            onSwitchToPanelRequested = { switchToPanelPreservingPosition() },
            onSwitchToImmersiveRequested = { switchToImmersivePreservingPosition() },
        )
        // Initial button state: panel mode. Only flip to immersive once the VR pipeline
        // is actually rendering (see flushPendingVrSurfaceIfReady).
        vrToggleButtonManager?.updateState(false)

        // Hook into ExoPlayer creation so we can flush a pending VR surface as soon as the
        // player becomes available (fixes the black-screen race between XR session ready
        // and async ExoPlayer creation).
        videoPlayerManager.onPlayerCreated = { player ->
            Timber.i("VrPlayerActivity: onPlayerCreated — attempting pending VR surface flush")
            flushPendingVrSurfaceIfReady(player)
        }

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

        // Build the VR immersive controls pipeline BEFORE OpenXrSessionManager so we can
        // pass the input callback straight into the session constructor.
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        // Layer E: hand-tracking ray manager. Constructed early so it can be wired
        // into VrControllerInputManager before the input callback reaches the XR
        // runtime. release() is called alongside the other managers in onDestroy.
        val handRay = VrHandRayManager(this, audioManager)
        vrHandRayManager = handRay
        val inputManager = VrControllerInputManager(
            mainHandler = mainHandler,
            keyBindingManager = keyBindingManager,
            onCommand = { command, source -> handleVrCommand(command, source) },
            onVolumeStep = ::onVolumeStep,
            onZoomGripDelta = ::onZoomGripDelta,
            audioManager = audioManager,
            onPointerEvent = { hand, down -> handRay.onPointerClick(hand, down) },
        )
        // Route per-frame pointer positions (Layer E) from the input manager into the
        // ray manager; the sink is volatile so the render-thread doesn't need a lock.
        inputManager.pointerMoveSink = { hand, ndcX, ndcY ->
            handRay.onPointerMove(hand, ndcX, ndcY)
        }
        // Layer A controller aim-ray (spec_vr-immersive-controls-panel Phase 02).
        // VrControllerRayManager handles throttle + MotionEvent dispatch internally.
        val controllerRay = VrControllerRayManager(this)
        vrControllerRayManager = controllerRay
        inputManager.controllerPointerMoveSink = { hand, ndcX, ndcY ->
            controllerRay.onControllerPointerMove(hand, ndcX, ndcY)
        }
        vrInputManager = inputManager
        vrHudFallback = VrHudIndicatorManager(this)
        vrHudManager = vrHudFallback
        vrCheatsheetManager = VrCheatsheetOverlayManager(this)
        vrZoomManager = VrZoomManager { descriptor ->
            xrSessionManager?.updateLayerDescriptor(descriptor, reason = "zoom-rebase")
            currentLayerDescriptor = descriptor
        }
        vrFileOpsManager = VrFileOpsOverlayManager(
            activity = this,
            recentDestinations = vrRecentDestinationsPrefs,
            callbacks = buildFileOpsCallbacks(),
        )

        // Initialise VR-specific components
        // onSessionReady (= ::initializeVrRenderPipeline) runs on the GL thread after the XR
        // session is established so that bridge/renderer GL resources are created in the right context.
        xrSessionManager = OpenXrSessionManager(
            renderCallback,
            onSessionReady = ::initializeVrRenderPipeline,
            onSessionStopped = ::releaseVrRenderPipeline,
            inputCallback = inputManager,
        )
        stereoRenderer = VrStereoRenderer()
        photoSphereRenderer = VrPhotoSphereRenderer(this)
        controlOverlay = VrControlOverlayManager(
            activity = this,
            onCommand = { command -> handleVrCommand(command, VrCommandSource.UI) },
        )

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
        val tPipeline0 = SystemClock.uptimeMillis()
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
        Timber.i("VR_PERF: [gl-thread] bridge_init=%dms  abs=%dms", SystemClock.uptimeMillis() - tPipeline0, SystemClock.uptimeMillis() - xrInitStartedAtMs)
        renderer.initGl()
        Timber.d("VrPlayerActivity: renderer.initGl done")
        photoRenderer?.initGl()
        Timber.d("VrPlayerActivity: photoRenderer.initGl done (renderer=%s)", photoRenderer)
        Timber.i("VR_PERF: [gl-thread] renderers_init=%dms  abs=%dms", SystemClock.uptimeMillis() - tPipeline0, SystemClock.uptimeMillis() - xrInitStartedAtMs)

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

        // Stand up the HUD composition pipeline (spec_vr-immersive-hud-gl). The renderer
        // owns the HUD swapchain + reusable Bitmap; the scene driver becomes the active
        // VrHudSink so all existing show*() callsites land in the OpenXR layer.
        val sessionMgr = xrSessionManager
        if (sessionMgr != null) {
            val newRenderer = VrHudRenderer(sessionMgr)
            if (newRenderer.ensureSwapchainCreated()) {
                vrHudRenderer = newRenderer
                val composer = VrHudSceneComposer(this)
                val driver = VrHudSceneDriver(newRenderer, composer)
                vrHudSceneDriver = driver
                vrHudManager = driver
                Timber.i("VrPlayerActivity: HUD scene driver active (immersive)")
                startVrHudProgressTicker()
            } else {
                Timber.w("VrPlayerActivity: HUD swapchain unavailable — keeping Android-view fallback")
            }
        }
        Timber.i("VR_PERF: [gl-thread] hud_swapchain=%dms  abs=%dms", SystemClock.uptimeMillis() - tPipeline0, SystemClock.uptimeMillis() - xrInitStartedAtMs)

        // Stand up the interactive GL panel pipeline (spec_vr-immersive-controls-panel Phase 03).
        if (sessionMgr != null) {
            val panelRenderer = VrInteractivePanelRenderer(sessionMgr)
            if (panelRenderer.ensureSwapchainCreated()) {
                val panelComposer = VrInteractivePanelComposer(this)
                val panelDriver = VrInteractivePanelDriver(panelRenderer, panelComposer)
                vrInteractivePanelDriver = panelDriver
                controlOverlay = VrControlOverlayManager(
                    activity = this,
                    onCommand = { command -> handleVrCommand(command, VrCommandSource.UI) },
                    panelDriver = panelDriver,
                )
                Timber.i("VrPlayerActivity: interactive panel driver active")

                // Phase 04: wire hit-test → hover zone → panel driver.
                val hitTester = VrRayPanelHitTester()
                val hitResolver = VrPanelHitZoneResolver(panelComposer)
                sessionMgr.attachHitTester(hitTester, hitResolver)
                val localInputManager = vrInputManager
                if (localInputManager != null) {
                    localInputManager.panelVisibleProvider = { panelDriver.isPanelVisible() }
                    localInputManager.panelHoverSink = { _, zoneId, seekFrac ->
                        lastHoveredZoneId = zoneId
                        panelDriver.updateHoverZone(zoneId)
                        if (seekFrac >= 0f) {
                            panelDriver.updateSeekDrag(seekFrac)
                            scheduleSeekDrag(seekFrac, panelDriver)
                        } else if (lastSeekDragFraction >= 0f) {
                            commitSeekDrag(lastSeekDragFraction, panelDriver)
                        }
                        lastSeekDragFraction = seekFrac
                    }
                    localInputManager.panelClickSink = {
                        dispatchPanelZoneClick(lastHoveredZoneId)
                    }
                }
            } else {
                Timber.w("VrPlayerActivity: panel swapchain unavailable — keeping 2D overlay fallback")
            }
        }
        Timber.i("VR_PERF: [gl-thread] panel_swapchain=%dms  abs=%dms", SystemClock.uptimeMillis() - tPipeline0, SystemClock.uptimeMillis() - xrInitStartedAtMs)

        Timber.i("VrPlayerActivity: initializeVrRenderPipeline COMPLETE")
        Timber.i("VR_PERF: [gl-thread] pipeline_total=%dms  abs_from_init=%dms", SystemClock.uptimeMillis() - tPipeline0, SystemClock.uptimeMillis() - xrInitStartedAtMs)
    }

    /**
     * 2 Hz ticker that pulls position / buffered / duration from the inherited
     * ExoPlayer and feeds the HUD scene driver. Runs only while the OpenXR
     * session is active; cancelled in [releaseVrRenderPipeline].
     */
    private fun startVrHudProgressTicker() {
        vrHudProgressJob?.cancel()
        vrHudProgressJob = lifecycleScope.launch {
            while (isActive) {
                val player = videoPlayerManager.exoPlayer
                val pos = player?.currentPosition ?: -1L
                val buf = player?.bufferedPosition ?: -1L
                val dur = player?.duration ?: -1L
                vrHudSceneDriver?.updateProgress(pos, buf, dur)
                vrInteractivePanelDriver?.updateProgress(pos, buf, dur)
                delay(500L)
            }
        }
    }

    /**
     * Release GL resources on the XR render thread.
     * Both bridge and renderer document that release() must run while their EGL context is current.
     */
    private fun releaseVrRenderPipeline() {
        videoSurfaceBridge?.release()
        stereoRenderer?.release()
        photoSphereRenderer?.release()
        // Tear down the HUD composition pipeline and revert to Android-view fallback
        // so phone-layout (or a subsequent re-bring-up of the XR session) keeps a sink.
        vrHudProgressJob?.cancel()
        vrHudProgressJob = null
        vrHudSceneDriver?.hideAll()
        vrHudSceneDriver = null
        vrHudRenderer?.release()
        vrHudRenderer = null
        vrHudManager = vrHudFallback
        vrInteractivePanelDriver?.release()
        vrInteractivePanelDriver = null
        vrInputManager?.panelVisibleProvider = null
        vrInputManager?.panelHoverSink = null
        vrInputManager?.panelClickSink = null
    }

    /**
     * Render a single XR eye using the current descriptor + frame context.
     *
     * The native layer has already bound the correct FBO, so the renderer only needs the
     * per-eye context to choose the UV crop and viewport policy for the active layer type.
     */
    private fun renderVrFrame(context: VrRenderContext) {
        val now = android.os.SystemClock.elapsedRealtime()
        val showFps = viewModel.settings.value.vrShowFps
        if (showFps || vrFpsLastValid > 0) {
            vrFpsFrameCount++
            if (now - vrFpsLastUpdateTime >= 500) {
                if (showFps && vrFpsFrameCount >= 5 && (now - vrFpsLastUpdateTime) <= 1500) {
                    vrFpsLastValid = (vrFpsFrameCount * 1000f / (now - vrFpsLastUpdateTime)).toInt()
                }
                if (showFps && vrFpsLastValid > 0) {
                    vrHudManager?.updateFps(vrFpsLastValid)
                } else if (!showFps && vrFpsLastValid > 0) {
                    Timber.d("VR_FPS: cleared HUD label after vrShowFps→false")
                    vrHudManager?.clearFps()
                    vrFpsLastValid = 0
                }
                vrFpsFrameCount = 0
                vrFpsLastUpdateTime = now
            }
        }

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

        if (vrFirstFrameLoggedMs == 0L && xrInitStartedAtMs > 0L) {
            vrFirstFrameLoggedMs = SystemClock.uptimeMillis()
            Timber.i("VR_PERF: [gl-thread] first_frame_ready  abs_from_init=%dms", vrFirstFrameLoggedMs - xrInitStartedAtMs)
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
                pendingVrBridgeSurface = null
                pendingVrBridgeTextureId = -1
                setVrRenderingActive(true, "static-image-session-ready")
            } else if (bridgeSurface != null && exoPlayerInstance != null) {
                exoPlayerInstance.setVideoSurface(bridgeSurface)
                Timber.i("VrPlayerActivity: ExoPlayer video redirected to VR bridge surface (textureId=%d)",
                    textureId)
                pendingVrBridgeSurface = null
                pendingVrBridgeTextureId = -1
                setVrRenderingActive(true, "surface-attached-sync")
            } else if (bridgeSurface != null && exoPlayerInstance == null) {
                // Race: XR session ready before ExoPlayer created. Hold surface until createPlayer fires.
                pendingVrBridgeSurface = bridgeSurface
                pendingVrBridgeTextureId = textureId
                Timber.w("VrPlayerActivity: XR ready before ExoPlayer — deferring VR surface attach (textureId=%d)", textureId)
                Log.w("VR_BOOT", "VrPlayerActivity: VR surface deferred — waiting for ExoPlayer creation (textureId=$textureId)")
            } else {
                // Genuine error: bridge surface is null (bridge.initialize failed).
                Timber.e("VrPlayerActivity: CANNOT redirect ExoPlayer to VR surface — exoPlayer=%s  bridgeSurface=%s  (VR will be BLACK!)",
                    exoPlayerInstance, bridgeSurface)
                Log.e("VR_BOOT", "VrPlayerActivity: surface redirect FAILED — exoPlayer=$exoPlayerInstance bridgeSurface=$bridgeSurface")
            }
        }
    }

    /**
     * Try to attach a deferred VR bridge surface to a newly-created ExoPlayer.
     * Called from the [VideoPlayerManager.onPlayerCreated] callback. No-op if
     * there is nothing pending or the XR session is not ready yet.
     */
    private fun flushPendingVrSurfaceIfReady(player: ExoPlayer) {
        val surface = pendingVrBridgeSurface ?: return
        val textureId = pendingVrBridgeTextureId
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            try {
                player.setVideoSurface(surface)
                Timber.w("VrPlayerActivity: attached DEFERRED VR surface to late-arriving ExoPlayer (textureId=%d)", textureId)
                Log.w("VR_BOOT", "VrPlayerActivity: deferred VR surface attached (textureId=$textureId)")
            } catch (t: Throwable) {
                Timber.e(t, "VrPlayerActivity: failed to attach deferred VR surface")
                return@runOnUiThread
            }
            pendingVrBridgeSurface = null
            pendingVrBridgeTextureId = -1
            setVrRenderingActive(true, "deferred-surface-attached")
        }
    }

    /** Single entry point for toggling the "VR is actually rendering" state + UI. */
    private fun setVrRenderingActive(active: Boolean, reason: String) {
        if (vrRenderingActive == active) return
        vrRenderingActive = active
        Timber.i("VrPlayerActivity: vrRenderingActive=%b (reason=%s)", active, reason)
        // Immersive renderer owns per-eye crop; suppress panel single-eye crop while immersive is active.
        videoPlayerManager.setVrImmersiveActive(active)
        // S0021: while immersive renders, the existing VR-HUD-FPS handles diagnostics —
        // suppress the panel-mode FPS overlay and re-evaluate when leaving immersive.
        runOnUiThread { updatePlayerFpsOverlay() }
        runOnUiThread {
            vrToggleButtonManager?.updateState(active)
            // First time the immersive render pipeline goes live, auto-show the
            // controls cheatsheet per spec §5.3. SharedPreferences-gated so only
            // the first-ever entry after install triggers it.
            if (active && !cheatsheetAutoShown) {
                cheatsheetAutoShown = true
                vrCheatsheetManager?.showIfFirstTime()
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
        // Clear the onPlayerCreated hook so future PlayerActivity instances
        // don't inherit a stale VrPlayerActivity reference.
        videoPlayerManager.onPlayerCreated = null
        pendingVrBridgeSurface = null
        pendingVrBridgeTextureId = -1
        stereoSnapshotManager = null
        videoSurfaceBridge = null
        stereoRenderer = null
        photoSphereRenderer = null
        // Release VR immersive controls managers.
        controlOverlay?.release()
        controlOverlay = null
        vrHudProgressJob?.cancel()
        vrHudProgressJob = null
        vrHudSceneDriver?.hideAll()
        vrHudSceneDriver = null
        vrHudRenderer?.release()
        vrHudRenderer = null
        vrHudFallback?.release()
        vrHudFallback = null
        vrHudManager = null
        vrCheatsheetManager?.release()
        vrCheatsheetManager = null
        vrFileOpsManager?.release()
        vrFileOpsManager = null
        vrZoomManager = null
        // Detach the pointer streams BEFORE releasing ray managers — otherwise
        // a queued main-thread Runnable from the xr-render-thread could resurrect the
        // decor overlay after release() has already torn it down.
        vrInputManager?.pointerMoveSink = null
        vrInputManager?.controllerPointerMoveSink = null
        vrHandRayManager?.release()
        vrHandRayManager = null
        vrControllerRayManager?.release()
        vrControllerRayManager = null
        vrInputManager = null
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
        // WHY: S0038 — Log.e is prohibited (Timber only). Downgraded to Timber.d.
        Timber.d("VrPlayerActivity: onNewIntent raw uri=%s", intent.toUri(0))
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

    override fun exitPlayerWithAudioCheck(withTransition: Boolean) {
        // In immersive VR the base finish() drops the user at the system shell because
        // the panel task was destroyed on entry. Route through VrTaskTransition instead.
        exitVrAndStopPlayback("gamepad-or-keyboard-exit")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // VR-specific buttons handled here (MENU, BACK, thumbstick click) because they
        // are Quest-controller signals, not standard gamepad buttons covered by
        // GamepadInputManager. KEYCODE_BUTTON_X/A/B/Y routing goes through super →
        // PlayerActivity.dispatchKeyEvent → GamepadInputManager so VR and phone players
        // share identical gamepad behaviour.
        if (event.action == KeyEvent.ACTION_UP) {
            // WHY: any button press means the user is actively holding the controller;
            // notify the HUD so it stays visible for the full 15 s idle-hide window.
            vrHudManager?.reportActivity()
            when (event.keyCode) {
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
                KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                    // Left thumbstick click — toggle 3DVR mode (panel ↔ immersive).
                    Timber.i("VrPlayerActivity: thumbstick-L → 3DVR toggle")
                    vrToggleButtonManager?.onToggleRequested()
                    return true
                }
            }
        }
        val inputManager = vrInputManager
        if (inputManager != null) {
            val isKeyboardSource = (event.source and InputDevice.SOURCE_KEYBOARD) != 0
            // WHY: PlayerActivity owns shared keyboard/media shortcuts on ACTION_DOWN.
            // Route only VR-exclusive keyboard commands through the VR layer; all non-keyboard
            // sources (OpenXR button KeyEvents, HID side buttons) still use the full VR mapper.
            val shouldRouteToVr = if (isKeyboardSource) {
                inputManager.shouldInterceptKeyboardShortcut(event)
            } else {
                true
            }
            if (shouldRouteToVr && inputManager.onKeyEvent(event) == true) return true
        }
        // Standard gamepad buttons (A/B/X/Y/L1/R1/START/SELECT) are routed by
        // PlayerActivity.dispatchKeyEvent → GamepadInputManager. B (Exit) dispatches
        // exitPlayerWithAudioCheck, which for VrPlayerActivity exits immersive mode.
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // WHY: thumbstick / trigger axis motion means the user is actively using the controller;
        // throttle to 1 Hz to avoid flooding the HUD state machine with redundant updates.
        val nowMotion = System.currentTimeMillis()
        if (nowMotion - lastActivityReportMs > 1_000L) {
            lastActivityReportMs = nowMotion
            vrHudManager?.reportActivity()
        }
        // Route BT mouse wheel / cursor motion through the VR input manager.
        if (vrInputManager?.onMotionEvent(event) == true) return true
        return super.onGenericMotionEvent(event)
    }

    override fun handle3dVrToggleClicked() {
        vrToggleButtonManager?.onToggleRequested()
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
     * True while immersive rendering is active AND the spec_vr-ui-composition-layer
     * (spec B) feature flag is still off. In that state, opening Android-view-backed
     * panels (file ops, control dialog, cheatsheet) would create invisible overlays
     * that pause playback without any way to dismiss them. The handlers route those
     * commands to a HUD banner instead — see [handleVrCommand].
     *
     * When spec B lands and the BuildConfig flag flips to `true`, this helper returns
     * `false` even in immersive — the panels then have a real composition target.
     */
    internal fun isImmersiveUiLocked(): Boolean =
        vrRenderingActive && !BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED

    /**
     * S0021 override: in VR-flavor, suppress the panel-mode FPS overlay while
     * immersive rendering is active — the existing VR-HUD-FPS counter handles
     * diagnostics there. When immersive leaves, fall through to the base rule.
     */
    override fun updatePlayerFpsOverlay() {
        if (vrRenderingActive) {
            binding.tvPlayerFpsOverlay.isVisible = false
            playerFpsMeter.stop()
            return
        }
        super.updatePlayerFpsOverlay()
    }

    /**
     * Dispatch a command from the VR overlay or controller input manager to the
     * inherited player. All UI feedback (HUD, haptic) is triggered here so every
     * input source (OpenXR / keyboard / mouse) produces identical feedback.
     */
    /**
     * Dispatch a panel zone click to the appropriate [PlaybackCommand].
     * Called on the main thread when the trigger fires while the GL panel is visible.
     * Phase 05 will expand the full dispatch table; only ZONE_EXIT is fully wired here.
     */
    private fun dispatchPanelZoneClick(zoneId: Int) {
        val cmd = when (zoneId) {
            VrInteractivePanelComposer.ZONE_PREV        -> PlaybackCommand.PreviousFile
            VrInteractivePanelComposer.ZONE_NEXT        -> PlaybackCommand.NextFile
            VrInteractivePanelComposer.ZONE_PLAY_PAUSE  -> PlaybackCommand.TogglePausePlay
            VrInteractivePanelComposer.ZONE_SEEK_BACK   -> PlaybackCommand.SeekBackward
            VrInteractivePanelComposer.ZONE_SEEK_FWD    -> PlaybackCommand.SeekForward
            VrInteractivePanelComposer.ZONE_SEEK_SLIDER -> null // handled via drag
            VrInteractivePanelComposer.ZONE_VOL_DOWN    -> PlaybackCommand.VolumeDown
            VrInteractivePanelComposer.ZONE_VOL_UP      -> PlaybackCommand.VolumeUp
            VrInteractivePanelComposer.ZONE_BRIGHT_DOWN -> PlaybackCommand.BrightnessDown
            VrInteractivePanelComposer.ZONE_BRIGHT_UP   -> PlaybackCommand.BrightnessUp
            VrInteractivePanelComposer.ZONE_SPEED       ->
                PlaybackCommand.SetPlaybackSpeed(nextPanelSpeed())
            VrInteractivePanelComposer.ZONE_TRACK       -> PlaybackCommand.CycleAudioTrack
            VrInteractivePanelComposer.ZONE_FORMAT      -> PlaybackCommand.CycleStereoFormat
            VrInteractivePanelComposer.ZONE_EXIT_TO_2D  -> PlaybackCommand.ExitTo2D
            VrInteractivePanelComposer.ZONE_EXIT        -> PlaybackCommand.Exit
            -1 -> null
            else -> null.also {
                Timber.w("VrPlayerActivity: unknown zone %d — ignored", zoneId)
            }
        }
        if (cmd != null) {
            handleVrCommand(cmd, VrCommandSource.CONTROLLER)
            vrInteractivePanelDriver?.scheduleAutoHide()
        }
    }

    private fun scheduleSeekDrag(fraction: Float, panelDriver: VrInteractivePanelDriver) {
        mainHandler.removeCallbacks(seekDragRunnable)
        seekDragTargetFraction = fraction
        mainHandler.postDelayed(seekDragRunnable, SEEK_DEBOUNCE_MS)
    }

    private fun commitSeekDrag(fraction: Float, panelDriver: VrInteractivePanelDriver) {
        mainHandler.removeCallbacks(seekDragRunnable)
        val totalMs = videoPlayerManager.exoPlayer?.duration ?: return
        if (totalMs <= 0L) return
        val positionMs = (fraction * totalMs).toLong().coerceIn(0L, totalMs)
        handleVrCommand(PlaybackCommand.SeekTo(positionMs), VrCommandSource.CONTROLLER)
        panelDriver.updateSeekDrag(-1f)
    }

    private var seekDragTargetFraction = -1f
    private val seekDragRunnable = Runnable {
        val pd = vrInteractivePanelDriver ?: return@Runnable
        commitSeekDrag(seekDragTargetFraction, pd)
    }

    private fun nextPanelSpeed(): Float {
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val idx = speeds.indexOfFirst { it == currentPanelSpeed }
        currentPanelSpeed = speeds[(idx + 1) % speeds.size]
        vrInteractivePanelDriver?.updateSpeed(currentPanelSpeed)
        return currentPanelSpeed
    }

    /**
     * Snapshot the relevant immersive subsystems before processing an explicit VR command
     * (`OpenControls`, `OpenFileOps`, ..) so the log differentiates "command suppressed by lock"
     * from "command silently dropped". One log line per command — not on the per-frame path.
     */
    private fun traceImmersiveCommand(command: PlaybackCommand, source: VrCommandSource) {
        Timber.d(
            "VrPlayerActivity: cmd=%s source=%s locked=%s hudVisible=%s descriptor=%s",
            command,
            source,
            isImmersiveUiLocked(),
            vrHudSceneDriver?.let { "scene-driver" } ?: "fallback",
            currentLayerDescriptor.type,
        )
    }

    private fun handleVrCommand(command: PlaybackCommand, source: VrCommandSource = VrCommandSource.UI) {
        when (command) {
            PlaybackCommand.OpenControls,
            PlaybackCommand.OpenFileOps -> traceImmersiveCommand(command, source)
            else -> Timber.d("VrPlayerActivity: handling VR command $command")
        }
        // WHY: any mapped controller command means the user is actively using the controller;
        // wake the HUD and reset the 15 s idle-hide timer.
        vrHudManager?.reportActivity()
        when (command) {
            PlaybackCommand.Play -> {
                viewModel.setPaused(false)
                vrHudManager?.showPauseIndicator(paused = false)
            }
            PlaybackCommand.Pause -> {
                viewModel.setPaused(true)
                vrHudManager?.showPauseIndicator(paused = true)
                // S0019: passive only — interactivity in S0024
                vrHudManager?.showBannerText(getString(R.string.vr_hud_prev_next_hint))
            }
            PlaybackCommand.TogglePausePlay -> {
                val nowPaused = !viewModel.state.value.isPaused
                viewModel.togglePause()
                vrHudManager?.showPauseIndicator(paused = nowPaused)
                if (nowPaused) {
                    // S0019: passive only — interactivity in S0024
                    vrHudManager?.showBannerText(getString(R.string.vr_hud_prev_next_hint))
                }
                maybeTriggerHaptic(source, XrHand.RIGHT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.SeekForward -> {
                videoPlayerManager.seekForward(VR_SEEK_SECONDS)
                showSeekFeedback(+VR_SEEK_SECONDS)
                maybeTriggerHaptic(source, XrHand.LEFT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.SeekBackward -> {
                videoPlayerManager.seekBackward(VR_SEEK_SECONDS)
                showSeekFeedback(-VR_SEEK_SECONDS)
                maybeTriggerHaptic(source, XrHand.LEFT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            is PlaybackCommand.SeekMicro -> {
                val d = if (command.forward) VR_SEEK_MICRO else -VR_SEEK_MICRO
                if (command.forward) videoPlayerManager.seekForward(VR_SEEK_MICRO)
                else videoPlayerManager.seekBackward(VR_SEEK_MICRO)
                showSeekFeedback(d)
            }
            is PlaybackCommand.SeekMacro -> {
                val d = if (command.forward) VR_SEEK_MACRO else -VR_SEEK_MACRO
                if (command.forward) videoPlayerManager.seekForward(VR_SEEK_MACRO)
                else videoPlayerManager.seekBackward(VR_SEEK_MACRO)
                showSeekFeedback(d)
            }
            PlaybackCommand.NextFile -> {
                // S0019: immersive-safe — does not recreate XR session.
                // viewModel.nextFile() updates state.currentIndex; the active
                // ExoPlayer / image surface is rebound by existing observers,
                // and the swapchain + HUD remain alive. User stays in the headset.
                Timber.i("VrPlayerActivity: immersive prev/next dir=NEXT xrSession=alive")
                viewModel.nextFile()
                showFileFeedback()
                maybeTriggerHaptic(source, XrHand.RIGHT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.PreviousFile -> {
                // S0019: immersive-safe — does not recreate XR session.
                Timber.i("VrPlayerActivity: immersive prev/next dir=PREV xrSession=alive")
                viewModel.previousFile()
                showFileFeedback()
                maybeTriggerHaptic(source, XrHand.RIGHT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.OpenControls -> {
                if (isImmersiveUiLocked()) {
                    Timber.i("VrPlayerActivity: OpenControls no-op — reason=immersive-ui-locked")
                    vrHudManager?.showBannerText(getString(R.string.vr_hud_guard_controls))
                } else {
                    vrHudManager?.setVisible(true, reason = "explicit-open-controls")
                    dialogHelper.showPlaybackControlDialog()
                }
            }
            PlaybackCommand.Exit -> exitVrAndStopPlayback("overlay-exit-command")
            // S0031 П3: switch to VR panel (2D view in headset) preserving file position.
            PlaybackCommand.ExitTo2D -> {
                Timber.i("VrPlayerActivity: ExitTo2D — switching to VR panel mode")
                switchToPanelPreservingPosition()
            }
            PlaybackCommand.OpenFileOps -> {
                if (isImmersiveUiLocked()) {
                    Timber.i("VrPlayerActivity: OpenFileOps no-op — reason=immersive-ui-locked")
                    vrHudManager?.showBannerText(getString(R.string.vr_hud_guard_file_ops))
                } else {
                    vrFileOpsManager?.show()
                }
            }
            PlaybackCommand.CopyFile -> {
                if (isImmersiveUiLocked()) {
                    vrHudManager?.showBannerText(getString(R.string.vr_hud_guard_file_ops))
                } else {
                    vrFileOpsManager?.showAndCopy()
                }
            }
            PlaybackCommand.MoveFile -> {
                if (isImmersiveUiLocked()) {
                    vrHudManager?.showBannerText(getString(R.string.vr_hud_guard_file_ops))
                } else {
                    vrFileOpsManager?.showAndMove()
                }
            }
            PlaybackCommand.DeleteFile -> {
                if (isImmersiveUiLocked()) {
                    vrHudManager?.showBannerText(getString(R.string.vr_hud_guard_file_ops))
                } else {
                    vrFileOpsManager?.showAndDelete()
                }
            }
            PlaybackCommand.RenameFile -> {
                if (isImmersiveUiLocked()) {
                    vrHudManager?.showBannerText(getString(R.string.vr_hud_guard_file_ops))
                } else {
                    vrFileOpsManager?.showAndRename()
                }
            }
            is PlaybackCommand.VolumeStep -> {
                showVolumeFeedback()
            }
            is PlaybackCommand.ZoomStep -> {
                vrZoomManager?.onDiscreteStep(command.increase)
                vrHudManager?.showZoomIndicator(vrZoomManager?.zoom() ?: 1f)
            }
            PlaybackCommand.ZoomReset -> {
                vrZoomManager?.reset()
                vrHudManager?.showZoomIndicator(1f)
                maybeTriggerHaptic(source, XrHand.LEFT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.Recenter -> {
                // Recentering requires a tracker reset at the native layer; for this iteration
                // we show the visual cue only. Native API is queued in the follow-up spec.
                vrHudManager?.showRecenterFlash()
                maybeTriggerHaptic(source, XrHand.RIGHT, HAPTIC_SHORT_NS, HAPTIC_AMPL)
            }
            PlaybackCommand.ToggleImmersiveMode -> {
                vrToggleButtonManager?.onToggleRequested()
                vrHudManager?.showImmersiveModeChanged(immersive = !vrRenderingActive)
            }
            PlaybackCommand.ShowCheatsheet -> {
                if (isImmersiveUiLocked()) {
                    vrHudManager?.showBannerText(getString(R.string.vr_hud_first_run_cheat))
                } else {
                    vrCheatsheetManager?.toggleManual()
                }
            }
            PlaybackCommand.ToggleOverlay  -> controlOverlay?.toggle()
            PlaybackCommand.Mute -> toggleMute()
            // Panel commands (spec_vr-immersive-controls-panel) — minimal wiring; full UX in that spec.
            PlaybackCommand.VolumeUp -> onVolumeStep(+1)
            PlaybackCommand.VolumeDown -> onVolumeStep(-1)
            PlaybackCommand.BrightnessUp, PlaybackCommand.BrightnessDown -> Unit
            is PlaybackCommand.SetPlaybackSpeed -> videoPlayerManager.setPlaybackSpeed(command.speed)
            PlaybackCommand.CycleAudioTrack -> cycleAudioTrackAndUpdatePanel()
            PlaybackCommand.CycleStereoFormat -> Unit
            is PlaybackCommand.SeekTo -> vrPlaybackEngine.seekTo(command.positionMs)
        }
    }

    // ── VR immersive input helper methods (spec_vr-immersive-controls-tech) ──

    private fun onVolumeStep(delta: Int) {
        val am = audioManager ?: return
        val dir = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
        } catch (t: Throwable) {
            Timber.w(t, "VrPlayerActivity: AudioManager.adjustStreamVolume failed")
        }
        showVolumeFeedback()
    }

    private fun cycleAudioTrackAndUpdatePanel() {
        val tracks = videoPlayerManager.getAvailableAudioTracks()
        if (tracks.size < 2) return
        val currentIdx = tracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        val next = tracks[(currentIdx + 1) % tracks.size]
        videoPlayerManager.selectAudioTrack(next.groupIndex, next.trackIndex)
        vrInteractivePanelDriver?.updatePanelTrackLabel(next.label)
    }

    private fun showVolumeFeedback() {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val percent = (current * 100 / max).coerceIn(0, 100)
        vrHudManager?.showVolumeIndicator(percent)
        vrInteractivePanelDriver?.updatePanelVolume(percent)
    }

    private fun onZoomGripDelta(delta: Float) {
        vrZoomManager?.onGripDelta(delta)
        vrHudManager?.showZoomIndicator(vrZoomManager?.zoom() ?: 1f)
    }

    private fun showSeekFeedback(deltaSeconds: Int) {
        val p = videoPlayerManager.exoPlayer
        val pos = p?.currentPosition ?: 0L
        val total = p?.duration ?: 0L
        vrHudManager?.showSeekIndicator(deltaSeconds, pos, total)
    }

    private fun showFileFeedback() {
        val state = viewModel.state.value
        val file = state.currentFile ?: return
        val index = state.currentIndex.coerceAtLeast(0) + 1
        val total = state.files.size.coerceAtLeast(1)
        vrHudManager?.showFileIndicator(file.name, index, total)
    }

    private fun toggleMute() {
        val am = audioManager ?: return
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            if (current > 0) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            } else {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            }
        } catch (t: Throwable) {
            Timber.w(t, "VrPlayerActivity: toggleMute failed")
        }
        showVolumeFeedback()
    }

    private fun triggerHaptic(hand: Int, durationNs: Long, amplitude: Float) {
        try {
            OpenXrNative.nativeTriggerHaptic(hand, durationNs, amplitude)
        } catch (t: Throwable) {
            // Native may not be loaded on fallback path — silently skip.
            Timber.v(t, "VrPlayerActivity: haptic trigger failed (hand=%d)", hand)
        }
    }

    private fun maybeTriggerHaptic(source: VrCommandSource, hand: Int, durationNs: Long, amplitude: Float) {
        // Only physical XR controllers can provide meaningful haptics. Hand tracking,
        // keyboard, mouse and Android-view UI paths must stay silent here; hand input
        // already gets audio confirmation in VrControllerInputManager.
        if (source != VrCommandSource.CONTROLLER) return
        triggerHaptic(hand, durationNs, amplitude)
    }

    private fun buildFileOpsCallbacks() = object : VrFileOpsOverlayManager.Callbacks {
        override fun currentFile(): MediaFile? = viewModel.state.value.currentFile
        override fun isPlaying(): Boolean = !viewModel.state.value.isPaused
        override fun pausePlayback() { viewModel.setPaused(true) }
        override fun resumePlayback() { viewModel.setPaused(false) }
        override fun copyFileTo(file: MediaFile, destinationPath: String) {
            Toast.makeText(this@VrPlayerActivity,
                getString(R.string.vr_fops_not_implemented_copy), Toast.LENGTH_SHORT).show()
            Timber.w("VrFileOps: copyFileTo path=%s — direct-path copy not wired in this iteration", destinationPath)
        }
        override fun moveFileTo(file: MediaFile, destinationPath: String) {
            Toast.makeText(this@VrPlayerActivity,
                getString(R.string.vr_fops_not_implemented_move), Toast.LENGTH_SHORT).show()
            Timber.w("VrFileOps: moveFileTo path=%s — direct-path move not wired in this iteration", destinationPath)
        }
        override fun deleteFile(file: MediaFile) {
            viewModel.deleteCurrentFile()
        }
        override fun renameFile(file: MediaFile, newName: String) {
            Toast.makeText(this@VrPlayerActivity,
                getString(R.string.vr_fops_not_implemented_rename), Toast.LENGTH_SHORT).show()
            Timber.w("VrFileOps: renameFile name=%s — rename flow not wired in this iteration", newName)
        }
        override fun openFolderBrowserForDestination(file: MediaFile, move: Boolean) {
            // Route back to the 2D panel so the user can pick a folder with the full tree UI.
            // S0019 recovery: not user exit — file-ops «pick destination» needs the full
            // browser tree; landing on MainActivity is the intended behaviour.
            Timber.i("VrFileOps: exiting immersive to pick destination (move=%b)", move)
            VrTaskTransition.exitImmersiveToPanel(this@VrPlayerActivity)
        }
        override fun buildFileInfoText(file: MediaFile): CharSequence {
            val parts = mutableListOf<String>()
            parts += getString(R.string.vr_info_name) + " " + file.name
            parts += getString(R.string.vr_info_path) + " " + file.path
            if (file.size > 0) parts += getString(R.string.vr_info_size) + " " + humanReadableSize(file.size)
            val w = file.width ?: 0; val h = file.height ?: 0
            if (w > 0 && h > 0) parts += getString(R.string.vr_info_resolution, w, h)
            file.duration?.let { parts += getString(R.string.vr_info_duration) + " " + formatDurationMs(it) }
            file.videoCodec?.let { parts += getString(R.string.vr_info_video) + " " + it }
            file.videoBitrate?.let { parts += getString(R.string.vr_info_bitrate, it / 1000) }
            return parts.joinToString("\n")
        }
    }

    private fun humanReadableSize(bytes: Long): String {
        val kb = bytes / 1024.0; val mb = kb / 1024.0; val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatDurationMs(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }

    private fun exitVrAndStopPlayback(reason: String) {
        forceStopVrPlayback(reason)
        // Hybrid-app exit: the panel task was destroyed on entry, so finish() alone would
        // drop the user at the system shell. Route back to a fresh PlayerActivity panel.
        val state = viewModel.state.value
        val playerIntent = PlayerActivity.createIntent(
            context = this,
            resourceId = state.resourceId,
            skipAvailabilityCheck = true,
            initialFilePath = state.currentFile?.path,
            isPlaying = false,
            isSlideshowEnabled = state.isSlideshowEnabled
        )
        // WHY: S0038 — use canonical EXTRA_FORCE_PANEL key so VrRouteDecisionHelper correctly
        // reads forcePanelThisLaunch=true and stays in panel mode without re-entering immersive.
        // The old key "extra_user_forced_panel" was silently ignored (wrong string).
        playerIntent.putExtra(EXTRA_FORCE_PANEL, true)
        // S0019: explicit user-driven exit-to-flat-player path. Carries playback context
        // (resource/file/position) so the user lands on the same file in the 2D player
        // rather than on the file-browser root.
        VrTaskTransition.exitImmersiveToFlatPlayer(this, playerIntent)
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
                // Hybrid-app exit: the panel task was destroyed on entry; route back to a
                // fresh MainActivity panel so the user is not stranded on a dead surface.
                // S0019 recovery: not user exit — XR-init failure cannot reasonably resume on
                // a flat-player surface; falling back to MainActivity is the intended behaviour.
                window.decorView.post { VrTaskTransition.exitImmersiveToPanel(this) }
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

            // S0018 defensive guard: panel-only reasons MUST yield STANDARD_PANEL_FALLBACK.
            // If the helper produced a contradictory pair, force panel fallback rather than
            // entering immersive — matches the unit-test invariant in VrRouteDecisionHelperTest.
            val panelOnlyReasons = setOf(
                "auto-immersive-disabled",
                "user-forced-panel",
                "disable-3d-vr",
                "plain-2d-content",
            )
            if (routeDecision.logReason in panelOnlyReasons &&
                routeDecision.route != VrLaunchRoute.STANDARD_PANEL_FALLBACK) {
                // recovery: not a route decision emission
                Timber.e(
                    "VrPlayerActivity: (route, reason) invariant violated — route=%s reason=%s; forcing panel fallback",
                    routeDecision.route,
                    routeDecision.logReason,
                )
                launchStandardPlayerFallback(currentFile, "invariant-violation:${routeDecision.logReason}")
                return@launch
            }

            applyStereoModeToVrRenderers(routeDecision.effectiveStereoMode, "route-decision")

            when (routeDecision.route) {
                VrLaunchRoute.STANDARD_PANEL_FALLBACK -> {
                    launchStandardPlayerFallback(currentFile, reason)
                }

                VrLaunchRoute.CINEMA_IMMERSIVE,
                VrLaunchRoute.IMMERSIVE_VIDEO,
                VrLaunchRoute.IMMERSIVE_STATIC_IMAGE -> {
                    if (!xrInitializationRequested) {
                        startXrInitialization(reason, routeDecision)
                    }
                    // applyStereoModeToVrRenderers already called above with MONO (CINEMA_IMMERSIVE)
                    // or the file's effective stereo mode — descriptor update is automatic.
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
        val routeDecision = routeDecisionHelper.decide(currentFile, effectiveMode, settings, forceImmersiveThisLaunch, forcePanelThisLaunch)
        routeDecision.logTo(currentFile, requestedStereoMode, settings.vrAutoDetectFormat)
        return routeDecision
    }

    private suspend fun resolveLaunchStereoMode(
        currentFile: MediaFile,
        requestedStereoMode: StereoMode,
        autoDetectEnabled: Boolean,
    ): StereoMode {
        // S0026: when Browse provided a detected mode hint, honor it over the coordinator's
        // current value if the coordinator is still at the MONO/UNKNOWN/AUTO default. The hint
        // is single-use; subsequent file changes within the same VrPlayerActivity instance
        // re-detect normally via the stereoDetector block below.
        val hintToUse = initialDetectedStereoModeHint
        initialDetectedStereoModeHint = null

        if (hintToUse != null && hintToUse != StereoMode.UNKNOWN && hintToUse != StereoMode.MONO &&
            (requestedStereoMode == StereoMode.MONO ||
                requestedStereoMode == StereoMode.AUTO ||
                requestedStereoMode == StereoMode.UNKNOWN)
        ) {
            Timber.i(
                "VrPlayerActivity: resolveLaunchStereoMode using browse hint=%s (was requested=%s)",
                hintToUse,
                requestedStereoMode,
            )
            return hintToUse
        }

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
        // Intentionally NOT using VrTaskTransition here: this is post-entry recovery for
        // non-immersive media (MONO/audio). The task is already the dedicated VR task
        // created by the hybrid-app handoff; re-targeting PlayerActivity into the same
        // task is the standard within-flavor fallback and does not participate in the
        // FOCUSED-entry problem this handoff solves.
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
            // Intentionally NOT using VrTaskTransition: unsupported-media recovery stays
            // inside the current (VR) task, same rationale as launchStandardPlayerFallback.
            startActivity(Intent(intent).apply {
                setClass(this@VrPlayerActivity, PlayerActivity::class.java)
            })
            finish()
        }, VR_FALLBACK_ERROR_DELAY_MS)
    }

    private fun startXrInitialization(reason: String, routeDecision: VrRouteDecision) {
        xrInitializationRequested = true
        xrInitStartedAtMs = SystemClock.uptimeMillis()
        Timber.i("VrPlayerActivity: starting XR init (reason=%s route=%s)", reason, routeDecision.route)
        Timber.i("VR_PERF: [main] xr_init_requested  t=%d", xrInitStartedAtMs)
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
     * Staying in VrPlayerActivity after a failed XR init leaves the user stuck: the
     * activity is sized to the headset panel resolution (~4128x2208) and command
     * panels collapse to a few physical millimetres, making every button unreachable
     * with the Quest controller. We therefore hand the session off to the regular
     * PlayerActivity — the Quest compositor renders it as an ordinary 2D window panel
     * where the UI is full-size and usable, and ResumeStateRepository restores the
     * playback position so the user re-enters at the same frame.
     */
    private fun fallbackToFlatCinemaMode(reason: String) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            Timber.w("VrPlayerActivity: fallbackToFlatCinemaMode (reason=%s) — XR unavailable, routing to standard PlayerActivity", reason)
            xrInitializationRequested = false
            try {
                xrSessionManager?.release()
            } catch (t: Throwable) {
                Timber.w(t, "VrPlayerActivity: fallbackToFlatCinemaMode — XR release failed, ignoring")
            }
            showError(getString(R.string.vr_xr_fallback_flat_mode))

            val currentFile = viewModel.state.value.currentFile
            if (currentFile == null) {
                Timber.w("VrPlayerActivity: fallbackToFlatCinemaMode — currentFile is null, finishing without fallback target")
                finish()
                return@runOnUiThread
            }
            // Delay matches launchUnsupportedImmersiveFallback: give the toast one UI frame
            // to enqueue before the activity hands off, otherwise the message is silent.
            window.decorView.postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                launchStandardPlayerFallback(currentFile, "xr-init-failed:$reason")
            }, VR_FALLBACK_ERROR_DELAY_MS)
        }
    }

    private fun forceStopVrPlayback(reason: String) {
        Timber.w("VrPlayerActivity: forceStopVrPlayback reason=%s", reason)

        // VR is no longer rendering — drop pending surface and flip button back to "Enter 3D".
        pendingVrBridgeSurface = null
        pendingVrBridgeTextureId = -1
        setVrRenderingActive(false, "force-stop:$reason")

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
        // Update the stereo-mode badge in the HUD so the user knows what is active.
        val label = mode.toHudLabel()
        vrHudManager?.showStereoModeLabel(label)
        // S0019: passive only — interactivity in S0024
        if (label != null && reason == "route-decision") {
            vrHudManager?.showBannerText(getString(R.string.vr_hud_applied_format, label))
        }
    }

    /** Maps a [StereoMode] to a short display label for the HUD badge, or null to hide it. */
    private fun StereoMode.toHudLabel(): String? = when (this) {
        StereoMode.MONO             -> "2D"
        StereoMode.SBS_FULL         -> "3D SBS"
        StereoMode.SBS_HALF         -> "3D SBS½"
        StereoMode.OU               -> "3D O/U"
        StereoMode.EQUIRECT_360_MONO -> "360° Mono"
        StereoMode.EQUIRECT_360_SBS  -> "360° SBS"
        StereoMode.EQUIRECT_360_OU   -> "360° O/U"
        StereoMode.EQUIRECT_180_MONO -> "180° Mono"
        StereoMode.EQUIRECT_180_SBS  -> "180° SBS"
        StereoMode.VR180_FISHEYE_SBS -> "VR180°"
        StereoMode.CYLINDER_180      -> "Cylinder"
        StereoMode.AUTO, StereoMode.UNKNOWN -> null  // transient states, no badge needed
    }

    private fun refreshLayerDescriptor(reason: String) {
        val descriptor = vrLayerFactory.describe(currentStereoMode, currentRenderingMode)
        currentLayerDescriptor = descriptor
        cachedDescriptorSourceAspectRatio = if (descriptor.heightMeters > 0f) {
            descriptor.widthMeters / descriptor.heightMeters
        } else {
            VrRenderContext.DEFAULT_SOURCE_ASPECT_RATIO
        }
        // When the zoom manager is wired, route through it so the user's current zoom
        // factor is re-applied on top of the new base descriptor. Otherwise submit directly.
        val zoom = vrZoomManager
        if (zoom != null) {
            zoom.setBaseDescriptor(descriptor)
        } else {
            xrSessionManager?.updateLayerDescriptor(descriptor, reason)
        }
        Timber.d(
            "VrPlayerActivity: layer descriptor → %s (reason=%s, stereo=%s, renderMode=%s)",
            descriptor.type,
            reason,
            currentStereoMode,
            currentRenderingMode,
        )
        assertStereoCoherence(reason)
    }

    /**
     * Coherence guard: compare the activity's [currentStereoMode] (used by [vrLayerFactory.describe])
     * against [viewModel].stereoMode (the coordinator's effective value). On divergence emit a
     * Timber.w line with the active descriptor, the activity field and the coordinator value;
     * on match emit a debug confirmation. Never on the per-frame path — only on descriptor refresh.
     */
    private fun assertStereoCoherence(reason: String) {
        val coordinatorEffective = viewModel.stereoMode.value
        if (coordinatorEffective != currentStereoMode) {
            Timber.w(
                "VrPlayerActivity: stereo coherence MISMATCH coordinator=%s activity=%s descriptor=%s reason=%s",
                coordinatorEffective,
                currentStereoMode,
                currentLayerDescriptor.type,
                reason,
            )
        } else {
            Timber.d(
                "VrPlayerActivity: stereo coherence OK %s reason=%s",
                coordinatorEffective,
                reason,
            )
        }
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

    /**
     * Relaunch current file in panel mode (no forced immersive).
     * [forceStopVrPlayback] saves the position to DB — PlayerActivity restores it on load.
     */
    private fun switchToPanelPreservingPosition() {
        if (isFinishing || isDestroyed) return
        Timber.i("VrPlayerActivity: switchToPanelPreservingPosition")
        forceStopVrPlayback("toggle-to-panel")
        startActivity(Intent(intent).apply {
            setClass(this@VrPlayerActivity, VrPlayerActivity::class.java)
            putExtra(EXTRA_FORCE_IMMERSIVE, false)
            // WHY: EXTRA_FORCE_PANEL=true tells VrRouteDecisionHelper to return STANDARD_PANEL_FALLBACK
            // unconditionally, even for spherical/SBS content that would otherwise be re-routed
            // back to immersive. Without this flag the toggle had no effect on VR180/360 files.
            putExtra(EXTRA_FORCE_PANEL, true)
        })
        finish()
    }

    /**
     * Relaunch current file in immersive mode (forced).
     * [forceStopVrPlayback] saves the position to DB — VrPlayerActivity restores it on load.
     */
    private fun switchToImmersivePreservingPosition() {
        if (isFinishing || isDestroyed) return
        Timber.i("VrPlayerActivity: switchToImmersivePreservingPosition")
        forceStopVrPlayback("toggle-to-immersive")
        startActivity(Intent(intent).apply {
            putExtra(EXTRA_FORCE_IMMERSIVE, true)
        })
        finish()
    }

    companion object {
        /** Seek increment for VR controller seek commands (seconds). */
        private const val VR_SEEK_SECONDS = 10
        /** Shift+arrow / `Shift+wheel` micro-seek increment (seconds). */
        private const val VR_SEEK_MICRO = 3
        /** Ctrl+arrow macro-seek increment (seconds). */
        private const val VR_SEEK_MACRO = 60
        private const val VR_FALLBACK_ERROR_DELAY_MS = 350L

        /** Debounce window for seek-drag SeekTo dispatch (ms). 300 ms guards against SMB latency. */
        private const val SEEK_DEBOUNCE_MS = 300L

        /** Short haptic pulse (~20 ms). */
        private const val HAPTIC_SHORT_NS = 20_000_000L
        /** Long haptic pulse (~150 ms) for warning / boundary. */
        private const val HAPTIC_LONG_NS  = 150_000_000L
        /** Standard haptic amplitude (0..1). */
        private const val HAPTIC_AMPL = 0.5f

        /** When true, bypass stereo-detection gate and force immersive XR route for video. */
        const val EXTRA_FORCE_IMMERSIVE = "com.sza.fastmediasorter.EXTRA_FORCE_IMMERSIVE"
        /** When true, force panel fallback route even for spherical/stereoscopic content. */
        const val EXTRA_FORCE_PANEL = "com.sza.fastmediasorter.EXTRA_FORCE_PANEL"

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
