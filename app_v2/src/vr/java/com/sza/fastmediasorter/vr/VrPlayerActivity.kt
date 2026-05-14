package com.sza.fastmediasorter.vr

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.StereoDetector
import com.sza.fastmediasorter.ui.player.helpers.seekBackward
import com.sza.fastmediasorter.ui.player.helpers.seekForward
import com.sza.fastmediasorter.ui.player.entry.VrTaskTransition
import com.sza.fastmediasorter.vr.capture.VrStereoSnapshotManager
import com.sza.fastmediasorter.vr.helpers.VrCommandSource
import com.sza.fastmediasorter.vr.helpers.VrControllerInputManager
import com.sza.fastmediasorter.vr.helpers.VrPlayerCommandRouter
import com.sza.fastmediasorter.vr.helpers.VrRecentDestinationsPrefs
import com.sza.fastmediasorter.vr.helpers.VrRenderPipelineManager
import com.sza.fastmediasorter.vr.helpers.VrRouteDecisionHelper
import com.sza.fastmediasorter.vr.helpers.VrSessionLifecycleManager
import com.sza.fastmediasorter.vr.helpers.VrToggleButtonManager
import com.sza.fastmediasorter.vr.openxr.OpenXrSessionManager
import com.sza.fastmediasorter.vr.openxr.XrRenderCallback
import com.sza.fastmediasorter.vr.render.VrEye
import com.sza.fastmediasorter.vr.render.VrInteractivePanelDriver
import com.sza.fastmediasorter.vr.render.VrLayerFactory
import com.sza.fastmediasorter.vr.render.VrPhotoSphereRenderer
import com.sza.fastmediasorter.vr.render.VrRenderContext
import com.sza.fastmediasorter.vr.render.VrStereoRenderer
import com.sza.fastmediasorter.vr.render.VrVideoSurfaceTextureBridge
import com.sza.fastmediasorter.vr.ui.VrCheatsheetOverlayManager
import com.sza.fastmediasorter.vr.ui.VrControlOverlayManager
import com.sza.fastmediasorter.vr.ui.VrControllerRayManager
import com.sza.fastmediasorter.vr.ui.VrFileOpsOverlayManager
import com.sza.fastmediasorter.vr.ui.VrHandRayManager
import com.sza.fastmediasorter.vr.ui.VrHudIndicatorManager
import com.sza.fastmediasorter.vr.ui.VrZoomManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * VR player host — extends PlayerActivity to inherit all controls, file operations and
 * playback logic. Adds an OpenXR rendering layer via OpenXrSessionManager + VrStereoRenderer.
 *
 * 3D video pipeline:
 *   ExoPlayer (inherited) → videoSurfaceBridge.surface → SurfaceTexture → OES texture →
 *   VrStereoRenderer.renderEye() UV-crops per eye → OpenXR compositor.
 *
 * On a phone (no XR runtime), falls back to VrPhoneFallbackActivity. Per S0033 Phase 05,
 * render-pipeline / session-lifecycle / command-routing logic lives in helpers/Vr*Manager.kt.
 */
@AndroidEntryPoint
class VrPlayerActivity : PlayerActivity() {

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

    private var vrInputManager: VrControllerInputManager? = null
    private var vrZoomManager: VrZoomManager? = null
    private var vrHudFallback: VrHudIndicatorManager? = null
    private var vrCheatsheetManager: VrCheatsheetOverlayManager? = null
    private var vrFileOpsManager: VrFileOpsOverlayManager? = null
    private var vrHandRayManager: VrHandRayManager? = null
    private var vrControllerRayManager: VrControllerRayManager? = null
    private var vrToggleButtonManager: VrToggleButtonManager? = null
    private var audioManager: AudioManager? = null
    private var cheatsheetAutoShown = false

    private lateinit var vrRenderPipelineManager: VrRenderPipelineManager
    private lateinit var vrSessionLifecycleManager: VrSessionLifecycleManager
    private lateinit var vrPlayerCommandRouter: VrPlayerCommandRouter

    private var videoSurfaceBridge: VrVideoSurfaceTextureBridge? = null

    private var lastActivityReportMs = 0L
    private var forceImmersiveThisLaunch = false
    private var forcePanelThisLaunch = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val playbackPrefs by lazy {
        getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val renderingModeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PlaybackControlPreferences.KEY_VR_RENDERING_MODE) {
            val sessionMgr = vrSessionLifecycleManager ?: return@OnSharedPreferenceChangeListener
            sessionMgr.applyRenderingModeFromPrefs()
            sessionMgr.refreshLayerDescriptor("render-mode-pref")
        }
    }

    internal data class VrPlaybackProgressSnapshot(
        val positionMs: Long,
        val bufferedPositionMs: Long,
        val durationMs: Long,
    )

    // ── Internal accessors used by helpers/Vr*Manager.kt (S0033 Phase 05) ───────────────

    internal val viewModelInternal get() = viewModel
    internal val lifecycleManagerInternal get() = lifecycleManager
    internal val dialogHelperInternal get() = dialogHelper

    internal val vrInputManagerInternal get() = vrInputManager
    internal val vrToggleButtonManagerInternal get() = vrToggleButtonManager
    internal val vrCheatsheetManagerInternal get() = vrCheatsheetManager
    internal val vrZoomManagerInternal get() = vrZoomManager
    internal val vrFileOpsManagerInternal get() = vrFileOpsManager
    internal val audioManagerInternal get() = audioManager
    internal var controlOverlayInternal: VrControlOverlayManager?
        get() = controlOverlay
        set(value) { controlOverlay = value }
    internal var cheatsheetAutoShownInternal: Boolean
        get() = cheatsheetAutoShown
        set(value) { cheatsheetAutoShown = value }
    internal val forceImmersiveThisLaunchInternal get() = forceImmersiveThisLaunch
    internal val forcePanelThisLaunchInternal get() = forcePanelThisLaunch
    internal val vrRenderPipelineManagerInternal: VrRenderPipelineManager?
        get() = if (::vrRenderPipelineManager.isInitialized) vrRenderPipelineManager else null
    /** Backward-compat: VrCheatsheetOverlayManager etc. read this directly. Delegates to the pipeline. */
    internal val vrHudManager get() = vrRenderPipelineManagerInternal?.vrHudManager
    internal val vrSessionLifecycleManagerInternal: VrSessionLifecycleManager?
        get() = if (::vrSessionLifecycleManager.isInitialized) vrSessionLifecycleManager else null
    internal val xrInitStartedAtMsInternal get() = vrSessionLifecycleManagerInternal?.xrInitStartedAtMs ?: 0L

    // WHY: VR helpers should depend on explicit playback operations, not on raw manager/player fields.
    internal fun currentPlaybackPlayerInternal(): androidx.media3.exoplayer.ExoPlayer? =
        videoPlayerManager.exoPlayer
    internal fun currentPlaybackDurationInternal(): Long = videoPlayerManager.exoPlayer?.duration ?: -1L
    internal fun currentPlaybackVideoSizeInternal(): androidx.media3.common.VideoSize? =
        videoPlayerManager.exoPlayer?.videoSize
    internal fun currentPlaybackProgressSnapshotInternal(): VrPlaybackProgressSnapshot {
        val player = videoPlayerManager.exoPlayer
        return VrPlaybackProgressSnapshot(
            positionMs = player?.currentPosition ?: -1L,
            bufferedPositionMs = player?.bufferedPosition ?: -1L,
            durationMs = player?.duration ?: -1L,
        )
    }
    internal fun seekPlaybackToInternal(positionMs: Long) {
        videoPlayerManager.exoPlayer?.seekTo(positionMs)
    }
    internal fun seekPlaybackForwardInternal(seconds: Int) {
        videoPlayerManager.seekForward(seconds)
    }
    internal fun seekPlaybackBackwardInternal(seconds: Int) {
        videoPlayerManager.seekBackward(seconds)
    }
    internal fun setPlaybackSpeedInternal(speed: Float) {
        videoPlayerManager.setPlaybackSpeed(speed)
    }
    internal fun availableAudioTracksInternal() = videoPlayerManager.getAvailableAudioTracks()
    internal fun selectAudioTrackInternal(groupIndex: Int, trackIndex: Int) {
        videoPlayerManager.selectAudioTrack(groupIndex, trackIndex)
    }
    internal fun attachPlaybackVideoSurfaceInternal(surface: android.view.Surface) {
        videoPlayerManager.exoPlayer?.setVideoSurface(surface)
    }
    internal fun clearPlaybackVideoSurfaceInternal() {
        videoPlayerManager.exoPlayer?.setVideoSurface(null)
    }
    internal fun setVrImmersivePlaybackActiveInternal(active: Boolean) {
        videoPlayerManager.setVrImmersiveActive(active)
    }

    internal fun stopVideoPlaybackInternal() = stopVideoPlayback()
    internal fun showErrorInternal(msg: String) = showError(msg)
    internal fun isVrStaticImageActiveInternal(): Boolean =
        vrSessionLifecycleManagerInternal?.isVrStaticImageActive() ?: false
    internal fun isCurrentVrStaticImageSessionInternal(): Boolean =
        vrSessionLifecycleManagerInternal?.isCurrentVrStaticImageSession() ?: false
    internal fun refreshLayerDescriptorInternal(reason: String) {
        vrSessionLifecycleManagerInternal?.refreshLayerDescriptor(reason)
    }

    /** Forwarder retained per S0033 Phase 05 — input routes call `handleVrCommand`. Delegates to router. */
    private fun handleVrCommand(
        command: com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand,
        source: VrCommandSource = VrCommandSource.UI,
    ) {
        if (::vrPlayerCommandRouter.isInitialized) vrPlayerCommandRouter.handleVrCommand(command, source)
    }
    internal fun handleVrCommandInternal(
        command: com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand,
        source: VrCommandSource = VrCommandSource.UI,
    ) = handleVrCommand(command, source)
    internal fun scheduleSeekDragInternal(fraction: Float, panelDriver: VrInteractivePanelDriver) {
        if (::vrPlayerCommandRouter.isInitialized) vrPlayerCommandRouter.scheduleSeekDrag(fraction, panelDriver)
    }
    internal fun commitSeekDragInternal(fraction: Float, panelDriver: VrInteractivePanelDriver) {
        if (::vrPlayerCommandRouter.isInitialized) vrPlayerCommandRouter.commitSeekDrag(fraction, panelDriver)
    }
    internal fun dispatchPanelZoneClickInternal(zoneId: Int) {
        if (::vrPlayerCommandRouter.isInitialized) vrPlayerCommandRouter.dispatchPanelZoneClick(zoneId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("S0132: VrPlayerActivity.onCreate — cold-start stage breakdown + swapchain + fisheye logging path")
        // High-priority diagnostic: android.util.Log.e so this always appears in logcat
        // even when the buffer is flooded by system (XR runtime) log spam.
        Log.e("VR_BOOT", "VrPlayerActivity.onCreate intent=${intent?.toUri(0)} savedState=$savedInstanceState")
        Timber.i("VrPlayerActivity: onCreate ENTRY  intent=%s", intent?.toUri(0))
        val xrColdStartNanos = System.nanoTime()
        Timber.d("VR_AUDIT/14: cold-start phase=onCreate-begin tNanos=%d", xrColdStartNanos)
        val xrAvailable = isXrRuntimeAvailable()
        Timber.i("VrPlayerActivity: isXrRuntimeAvailable=%b", xrAvailable)
        if (!xrAvailable) {
            super.onCreate(savedInstanceState)
            Timber.w("VrPlayerActivity: no XR runtime — falling back to phone screen")
            startActivity(Intent(this, VrPhoneFallbackActivity::class.java))
            finish()
            return
        }

        super.onCreate(savedInstanceState)
        Timber.i("VrPlayerActivity: super.onCreate done")
        Timber.d("VR_AUDIT/14: cold-start phase=super.onCreate-done deltaMs=%d", (System.nanoTime() - xrColdStartNanos) / 1_000_000)
        // S0132 P05.3a: STAGE_SETUP_VIEWS marker — BaseActivity.setupViews completes inside
        // super.onCreate, so this anchor sits at the boundary needed for the stage-breakdown
        // grep pattern `cold-start stage=STAGE_*`.
        Timber.d("VR_AUDIT/14: cold-start stage=STAGE_SETUP_VIEWS elapsed=%d", (System.nanoTime() - xrColdStartNanos) / 1_000_000)

        forceImmersiveThisLaunch = intent.getBooleanExtra(EXTRA_FORCE_IMMERSIVE, false)
        Timber.i("VrPlayerActivity: forceImmersiveThisLaunch=%b", forceImmersiveThisLaunch)
        forcePanelThisLaunch = intent.getBooleanExtra(EXTRA_FORCE_PANEL, false)

        val initialDetectedStereoModeHint =
            intent.getStringExtra(PlayerActivity.EXTRA_DETECTED_STEREO_MODE)
                ?.let { name -> runCatching { StereoMode.valueOf(name) }.getOrNull() }
        Timber.i("VrPlayerActivity: initial detected stereo hint=%s", initialDetectedStereoModeHint)
        Timber.d(
            "VR_AUDIT/5: VrPlayerActivity.onCreate EXTRA_DETECTED_STEREO_MODE=%s hintParsed=%s forceImmersive=%b forcePanel=%b",
            intent.getStringExtra(PlayerActivity.EXTRA_DETECTED_STEREO_MODE),
            initialDetectedStereoModeHint,
            forceImmersiveThisLaunch,
            forcePanelThisLaunch,
        )

        vrToggleButtonManager = VrToggleButtonManager(
            button = safeViews.btn3dVrCmd,
            onSwitchToPanelRequested = { vrSessionLifecycleManager.switchToPanelPreservingPosition() },
            onSwitchToImmersiveRequested = { vrSessionLifecycleManager.switchToImmersivePreservingPosition() },
        )
        vrToggleButtonManager?.updateState(false)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val handRay = VrHandRayManager(this, audioManager)
        vrHandRayManager = handRay
        val inputManager = VrControllerInputManager(
            mainHandler = mainHandler,
            keyBindingManager = keyBindingManager,
            onCommand = { command, source -> vrPlayerCommandRouter.handleVrCommand(command, source) },
            onVolumeStep = { delta -> vrPlayerCommandRouter.onVolumeStep(delta) },
            onZoomGripDelta = { delta -> vrPlayerCommandRouter.onZoomGripDelta(delta) },
            audioManager = audioManager,
            onPointerEvent = { hand, down -> handRay.onPointerClick(hand, down) },
        )
        inputManager.pointerMoveSink = { hand, ndcX, ndcY ->
            handRay.onPointerMove(hand, ndcX, ndcY)
        }
        val controllerRay = VrControllerRayManager(this)
        vrControllerRayManager = controllerRay
        inputManager.controllerPointerMoveSink = { hand, ndcX, ndcY ->
            controllerRay.onControllerPointerMove(hand, ndcX, ndcY)
        }
        vrInputManager = inputManager

        val hudFallback = VrHudIndicatorManager(this)
        vrHudFallback = hudFallback
        vrCheatsheetManager = VrCheatsheetOverlayManager(this)
        vrZoomManager = VrZoomManager { descriptor ->
            xrSessionManager?.updateLayerDescriptor(descriptor, reason = "zoom-rebase")
            vrRenderPipelineManagerInternal?.currentLayerDescriptor = descriptor
        }
        vrFileOpsManager = VrFileOpsOverlayManager(
            activity = this,
            recentDestinations = vrRecentDestinationsPrefs,
            callbacks = buildFileOpsCallbacks(),
        )

        val newStereoRenderer = VrStereoRenderer()
        stereoRenderer = newStereoRenderer
        val newPhotoRenderer = VrPhotoSphereRenderer(this)
        photoSphereRenderer = newPhotoRenderer
        val newBridge = VrVideoSurfaceTextureBridge()
        videoSurfaceBridge = newBridge

        val sessionMgr = VrSessionLifecycleManager(
            activity = this,
            xrSessionManagerProvider = { xrSessionManager },
            stereoDetector = stereoDetector,
            routeDecisionHelper = routeDecisionHelper,
            playbackPrefs = playbackPrefs,
            vrLayerFactory = vrLayerFactory,
            onCriticalLifecycleEvent = { /* reserved */ },
        )
        sessionMgr.initialDetectedStereoModeHint = initialDetectedStereoModeHint
        sessionMgr.applyRenderingModeFromPrefs()
        vrSessionLifecycleManager = sessionMgr

        val routerMgr = VrPlayerCommandRouter(activity = this, mainHandler = mainHandler)
        vrPlayerCommandRouter = routerMgr

        val pipelineMgr = VrRenderPipelineManager(
            activity = this,
            videoSurfaceBridge = newBridge,
            stereoRenderer = newStereoRenderer,
            photoSphereRenderer = newPhotoRenderer,
            vrHudFallback = hudFallback,
            xrSessionManagerProvider = { xrSessionManager },
        )
        vrRenderPipelineManager = pipelineMgr

        videoPlayerManager.onPlayerCreated = {
            Timber.i("VrPlayerActivity: onPlayerCreated — attempting pending VR surface flush")
            vrRenderPipelineManagerInternal?.flushPendingVrSurfaceIfReady()
        }

        // Per-eye render callback — runs on xr-render-thread.
        val renderCallback = XrRenderCallback { eye, fbo, width, height ->
            val pipeline = vrRenderPipelineManager ?: return@XrRenderCallback
            val session = vrSessionLifecycleManager ?: return@XrRenderCallback
            pipeline.renderVrFrame(
                VrRenderContext(
                    layerType = pipeline.currentLayerDescriptor.type,
                    stereoMode = session.currentStereoMode,
                    eye = VrEye.fromNativeIndex(eye),
                    swapchainImageIndex = fbo,
                    renderingMode = session.currentRenderingMode,
                    targetWidthPx = width,
                    targetHeightPx = height,
                    sourceAspectRatio = pipeline.resolveSourceAspectRatio(),
                )
            )
        }

        xrSessionManager = OpenXrSessionManager(
            renderCallback,
            onSessionReady = { pipelineMgr.initializeVrRenderPipeline() },
            onSessionStopped = { pipelineMgr.releaseVrRenderPipeline() },
            inputCallback = inputManager,
        )

        controlOverlay = VrControlOverlayManager(
            activity = this,
            onCommand = { command -> routerMgr.handleVrCommand(command, VrCommandSource.UI) },
        )

        stereoSnapshotManager = VrStereoSnapshotManager(
            activity = this,
            sessionProvider = { xrSessionManager },
        )
        playbackPrefs.registerOnSharedPreferenceChangeListener(renderingModeListener)
        sessionMgr.refreshLayerDescriptor("activity-created")
        pipelineMgr.ensureVrPlayerListenerAttached()

        lifecycleScope.launch {
            viewModel.stereoMode.collectLatest { mode ->
                if (mode == StereoMode.AUTO || mode == StereoMode.UNKNOWN) {
                    Timber.d("VrPlayerActivity: stereoMode → $mode (deferring to route decision)")
                } else {
                    Timber.d("VrPlayerActivity: stereoMode → $mode → renderer=$mode")
                    sessionMgr.applyStereoModeToVrRenderers(mode, "stereo-mode")
                }
                sessionMgr.resolvePlaybackRoute("stereo-mode")
            }
        }

        lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                sessionMgr.syncVrImageTarget(state.currentFile, newPhotoRenderer)
                sessionMgr.resolvePlaybackRoute("player-state")
            }
        }

        Timber.d("VrPlayerActivity: VR components initialised, XR session will start in onResume")
        Timber.d("VR_AUDIT/14: cold-start phase=onCreate-end deltaMs=%d", (System.nanoTime() - xrColdStartNanos) / 1_000_000)
    }

    override fun onResume() {
        super.onResume()
        vrRenderPipelineManagerInternal?.ensureVrPlayerListenerAttached()
        Timber.i("VrPlayerActivity: onResume — resolving whether this file needs XR or standard playback")
        vrSessionLifecycleManager.resolvePlaybackRoute("onResume")
    }

    override fun onPause() {
        Timber.i("VrPlayerActivity: onPause — clearing video surface and releasing XR session")
        // Detach bridge surface from ExoPlayer BEFORE stopping the render loop so ExoPlayer
        // does not write frames to the bridge SurfaceTexture while it is being released.
        val player = videoPlayerManager.exoPlayer
        if (player != null) {
            player.setVideoSurface(null)
            Timber.d("VrPlayerActivity: onPause — ExoPlayer surface cleared (player=%s)", player)
        } else {
            Timber.d("VrPlayerActivity: onPause — ExoPlayer not active, no surface to clear")
        }
        xrSessionManager?.release()
        super.onPause()
        Timber.i("VrPlayerActivity: onPause COMPLETE")
    }

    override fun onDestroy() {
        Timber.i("VrPlayerActivity: onDestroy")
        Timber.d("VR_AUDIT/7: VrPlayerActivity.onDestroy isFinishing=%b isTaskRoot=%b — finishAndRemoveTask path completion check",
            isFinishing, isTaskRoot)
        if (::vrSessionLifecycleManager.isInitialized) vrSessionLifecycleManager.cancelRouteJob()
        playbackPrefs.unregisterOnSharedPreferenceChangeListener(renderingModeListener)
        vrRenderPipelineManagerInternal?.detachVrPlayerListener()
        videoPlayerManager.onPlayerCreated = null
        stereoSnapshotManager = null
        videoSurfaceBridge = null
        stereoRenderer = null
        photoSphereRenderer = null
        controlOverlay?.release()
        controlOverlay = null
        // vrRenderPipelineManager is lateinit; Activity instance is being torn down — references
        // become unreachable when super.onDestroy() returns and GC reclaims this instance.
        vrHudFallback?.release()
        vrHudFallback = null
        vrCheatsheetManager?.release()
        vrCheatsheetManager = null
        vrFileOpsManager?.release()
        vrFileOpsManager = null
        vrZoomManager = null
        // Detach pointer streams BEFORE releasing ray managers — otherwise a queued main-thread
        // Runnable from xr-render-thread could resurrect the decor overlay after release().
        vrInputManager?.pointerMoveSink = null
        vrInputManager?.controllerPointerMoveSink = null
        vrHandRayManager?.release()
        vrHandRayManager = null
        vrControllerRayManager?.release()
        vrControllerRayManager = null
        vrInputManager = null
        xrSessionManager?.release()
        xrSessionManager = null
        // Manager refs are lateinit; references reclaimed by GC after super.onDestroy().
        super.onDestroy()
        Timber.i("VrPlayerActivity: onDestroy COMPLETE")
    }

    /**
     * Called by the system when this singleTask activity is already running and a new
     * intent arrives. Strategy: update the activity intent and recreate so the ViewModel
     * reads the new resourceId / initialIndex from SavedStateHandle.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.d("VrPlayerActivity: onNewIntent raw uri=%s", intent.toUri(0))
        Timber.i("VrPlayerActivity: onNewIntent — new video requested, recreating activity. intent=%s", intent.toUri(0))
        val brought = (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0
        Timber.d("VR_AUDIT/7: onNewIntent flags=0x%x FLAG_ACTIVITY_BROUGHT_TO_FRONT=%b", intent.flags, brought)
        vrSessionLifecycleManager.forceStopVrPlayback("new-intent-recreate")
        setIntent(intent)
        recreate()
    }

    override fun saveCurrentFrame() {
        // VR snapshots come from the XR eye swapchains, not the hidden PlayerView TextureView.
        stereoSnapshotManager?.captureCurrentFrame() ?: super.saveCurrentFrame()
    }

    override fun exitPlayerWithAudioCheck(withTransition: Boolean) {
        // Base finish() drops the user at the system shell because the panel task was destroyed
        // on entry. Route through VrTaskTransition instead.
        vrSessionLifecycleManager.exitVrAndStopPlayback("gamepad-or-keyboard-exit")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            // Any button press means the user is actively holding the controller — notify the
            // HUD so it stays visible for the full 15 s idle-hide window.
            vrRenderPipelineManagerInternal?.vrHudManager?.reportActivity()
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    Timber.i("VrPlayerActivity: Menu button (left controller ≡) → showPlaybackControlDialog")
                    dialogHelper.showPlaybackControlDialog()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    Timber.i("VrPlayerActivity: Back/≡ button → exit immersive")
                    vrSessionLifecycleManager.exitVrAndStopPlayback("back-button-exit")
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                    Timber.i("VrPlayerActivity: thumbstick-L → 3DVR toggle")
                    vrToggleButtonManager?.onToggleRequested()
                    return true
                }
            }
        }
        val inputManager = vrInputManager
        if (inputManager != null) {
            val isKeyboardSource = (event.source and InputDevice.SOURCE_KEYBOARD) != 0
            // PlayerActivity owns shared keyboard/media shortcuts on ACTION_DOWN. Route only
            // VR-exclusive keyboard commands through the VR layer; non-keyboard sources still
            // use the full VR mapper.
            val shouldRouteToVr = if (isKeyboardSource) {
                inputManager.shouldInterceptKeyboardShortcut(event)
            } else {
                true
            }
            if (shouldRouteToVr && inputManager.onKeyEvent(event) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Throttle to 1 Hz; sub-deadzone Quest 3 controller noise must NOT keep the HUD alive.
        if (isDeliberateControllerMotion(event)) {
            val nowMotion = System.currentTimeMillis()
            if (nowMotion - lastActivityReportMs > 1_000L) {
                lastActivityReportMs = nowMotion
                vrRenderPipelineManagerInternal?.vrHudManager?.reportActivity()
            }
        }
        if (vrInputManager?.onMotionEvent(event) == true) return true
        return super.onGenericMotionEvent(event)
    }

    // Idle Quest 3 controllers emit sub-deadzone axis jitter every frame. Forwarding those
    // events would slide the HUD's 15 s idle-hide window forever and defeat auto-hide.
    private fun isDeliberateControllerMotion(event: MotionEvent): Boolean {
        if (Math.abs(event.getAxisValue(MotionEvent.AXIS_X)) >= VR_AXIS_ACTIVITY_DEADZONE) return true
        if (Math.abs(event.getAxisValue(MotionEvent.AXIS_Y)) >= VR_AXIS_ACTIVITY_DEADZONE) return true
        if (Math.abs(event.getAxisValue(MotionEvent.AXIS_Z)) >= VR_AXIS_ACTIVITY_DEADZONE) return true
        if (Math.abs(event.getAxisValue(MotionEvent.AXIS_RZ)) >= VR_AXIS_ACTIVITY_DEADZONE) return true
        if (Math.abs(event.getAxisValue(MotionEvent.AXIS_HAT_X)) >= VR_AXIS_ACTIVITY_DEADZONE) return true
        if (Math.abs(event.getAxisValue(MotionEvent.AXIS_HAT_Y)) >= VR_AXIS_ACTIVITY_DEADZONE) return true
        if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) >= VR_AXIS_ACTIVITY_DEADZONE) return true
        if (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) >= VR_AXIS_ACTIVITY_DEADZONE) return true
        if (event.getAxisValue(MotionEvent.AXIS_BRAKE) >= VR_AXIS_ACTIVITY_DEADZONE) return true
        if (event.getAxisValue(MotionEvent.AXIS_GAS) >= VR_AXIS_ACTIVITY_DEADZONE) return true
        return false
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
        overlay.toggle()
        return true
    }

    /**
     * True while immersive rendering is active AND the spec_vr-ui-composition-layer flag is off.
     * In that state, opening Android-view-backed panels would create invisible overlays that pause
     * playback without any way to dismiss them. Handlers route those commands to a HUD banner.
     */
    internal fun isImmersiveUiLocked(): Boolean =
        (vrRenderPipelineManagerInternal?.vrRenderingActive == true) && !BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED

    /**
     * S0021: in VR-flavor, suppress the panel-mode FPS overlay while immersive rendering is
     * active — the existing VR-HUD-FPS counter handles diagnostics there. When immersive
     * leaves, fall through to the base rule.
     */
    override fun updatePlayerFpsOverlay() {
        if (vrRenderPipelineManagerInternal?.vrRenderingActive == true) {
            binding.tvPlayerFpsOverlay.isVisible = false
            playerFpsMeter.stop()
            return
        }
        super.updatePlayerFpsOverlay()
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

    private fun isXrRuntimeAvailable(): Boolean = xrAvailable

    companion object {
        private const val VR_AXIS_ACTIVITY_DEADZONE = 0.20f

        const val EXTRA_FORCE_IMMERSIVE = "com.sza.fastmediasorter.EXTRA_FORCE_IMMERSIVE"
        const val EXTRA_FORCE_PANEL = "com.sza.fastmediasorter.EXTRA_FORCE_PANEL"

        internal const val VR_FALLBACK_ERROR_DELAY_MS = 350L

        private val xrAvailable: Boolean by lazy {
            // S0156 ADR-8: noLegal APK ships openxr_native.so only for arm64-v8a
            // (cmake.abiFilters = arm64-v8a). On other ABIs or devices without the
            // Meta XR runtime, either library will throw UnsatisfiedLinkError — we
            // catch it here so VrPlayerActivity falls back gracefully to the phone screen.
            try {
                System.loadLibrary("openxr_loader")
                System.loadLibrary("openxr_native")
                true
            } catch (_: UnsatisfiedLinkError) {
                Timber.w("VrPlayerActivity: OpenXR native bridge unavailable (non-Quest or unsupported ABI)")
                false
            }
        }
    }
}
