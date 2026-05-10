package com.sza.fastmediasorter.vr.helpers

import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.vr.VrPlayerActivity
import com.sza.fastmediasorter.vr.openxr.OpenXrSessionManager
import com.sza.fastmediasorter.vr.render.VrEye
import com.sza.fastmediasorter.vr.render.VrHudRenderer
import com.sza.fastmediasorter.vr.render.VrHudSceneComposer
import com.sza.fastmediasorter.vr.render.VrHudSceneDriver
import com.sza.fastmediasorter.vr.render.VrInteractivePanelComposer
import com.sza.fastmediasorter.vr.render.VrInteractivePanelDriver
import com.sza.fastmediasorter.vr.render.VrInteractivePanelRenderer
import com.sza.fastmediasorter.vr.render.VrLayerDescriptor
import com.sza.fastmediasorter.vr.render.VrPhotoSphereRenderer
import com.sza.fastmediasorter.vr.render.VrRenderContext
import com.sza.fastmediasorter.vr.render.VrStereoRenderer
import com.sza.fastmediasorter.vr.render.VrVideoSurfaceTextureBridge
import com.sza.fastmediasorter.vr.ui.VrCheatsheetOverlayManager
import com.sza.fastmediasorter.vr.ui.VrControlOverlayManager
import com.sza.fastmediasorter.vr.ui.VrHudHitTester
import com.sza.fastmediasorter.vr.ui.VrHudIndicatorManager
import com.sza.fastmediasorter.vr.ui.VrHudInputDispatcher
import com.sza.fastmediasorter.vr.ui.VrHudSink
import com.sza.fastmediasorter.vr.ui.VrPanelHitZoneResolver
import com.sza.fastmediasorter.vr.ui.VrRayPanelHitTester
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the VR render pipeline: surface bridge, stereo + photo-sphere renderers, HUD scene
 * driver, interactive panel driver, FPS tracking, deferred surface flush. Decomposed from
 * VrPlayerActivity per S0033 Phase 05. Methods here run on either the main thread or the
 * XR render (GL) thread; per-method comments preserve the call-site contract.
 */
class VrRenderPipelineManager(
    private val activity: VrPlayerActivity,
    val videoSurfaceBridge: VrVideoSurfaceTextureBridge,
    val stereoRenderer: VrStereoRenderer,
    val photoSphereRenderer: VrPhotoSphereRenderer,
    private val vrHudFallback: VrHudIndicatorManager,
    private val xrSessionManagerProvider: () -> OpenXrSessionManager?,
) {

    @Volatile
    var pendingVrBridgeSurface: Surface? = null
    @Volatile
    var pendingVrBridgeTextureId: Int = -1

    @Volatile
    var vrRenderingActive: Boolean = false
        private set

    @Volatile
    var currentLayerDescriptor: VrLayerDescriptor = VrLayerDescriptor()

    @Volatile
    private var cachedPlayerSourceAspectRatio: Float = 0f

    @Volatile
    var cachedDescriptorSourceAspectRatio: Float = VrRenderContext.DEFAULT_SOURCE_ASPECT_RATIO

    @Volatile
    private var dbgRenderFrameCount: Long = 0L

    private var vrFpsFrameCount: Int = 0
    private var vrFpsLastUpdateTime: Long = 0L
    private var vrFpsLastValid: Int = 0

    @Volatile
    private var vrFirstFrameLoggedMs: Long = 0L

    private var vrHudProgressJob: Job? = null

    var vrHudRenderer: VrHudRenderer? = null
        private set
    var vrHudSceneDriver: VrHudSceneDriver? = null
        private set
    var vrInteractivePanelDriver: VrInteractivePanelDriver? = null
        private set

    var vrHudManager: VrHudSink? = vrHudFallback

    var lastHoveredZoneId: Int = -1
    var lastSeekDragFraction: Float = -1f

    /**
     * Last HUD element id resolved by the controller aim-ray hit-test (S0024 Phase 02).
     * `0` means "no hover" or HUD layer not visible. Phase 03 reads this to drive the
     * hover-redraw path; Phase 04 reads this on trigger to dispatch the registered click
     * callback. Updated on the xr-render-thread via the `hudHoverSink`.
     */
    @Volatile
    var currentHudHoverId: Int = 0

    private var observedVrPlayer: ExoPlayer? = null

    private val vrPlayerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            cachePlayerAspectRatio(videoSize, "player-video-size")
        }
    }

    /**
     * Called from the XR render loop (on GL thread) after OpenXR session is active.
     * Initialises the bridge and redirects ExoPlayer video output to the VR pipeline.
     *
     * Bridge + renderer release their old GL resources first so that new GL objects are created
     * in the current EGL context.
     */
    fun initializeVrRenderPipeline() {
        val tPipeline0 = SystemClock.uptimeMillis()
        Timber.i("VrPlayerActivity: initializeVrRenderPipeline START  bridge=%s renderer=%s",
            videoSurfaceBridge, stereoRenderer)
        val bridge = videoSurfaceBridge
        val renderer = stereoRenderer
        val photoRenderer = photoSphereRenderer

        Timber.d("VrPlayerActivity: releasing old GL resources (bridge textureId=%d)", bridge.textureId)
        bridge.release()
        renderer.release()
        photoRenderer.release()

        Timber.d("VrPlayerActivity: initialising GL resources in new EGL context")
        bridge.initialize()
        Timber.i("VrPlayerActivity: bridge.initialize done — textureId=%d surface=%s isReady=%b",
            bridge.textureId, bridge.surface, bridge.isReady())
        Timber.i("VR_PERF: [gl-thread] bridge_init=%dms  abs=%dms",
            SystemClock.uptimeMillis() - tPipeline0,
            SystemClock.uptimeMillis() - activity.xrInitStartedAtMsInternal)
        renderer.initGl()
        Timber.d("VrPlayerActivity: renderer.initGl done")
        photoRenderer.initGl()
        Timber.d("VrPlayerActivity: photoRenderer.initGl done (renderer=%s)", photoRenderer)
        Timber.i("VR_PERF: [gl-thread] renderers_init=%dms  abs=%dms",
            SystemClock.uptimeMillis() - tPipeline0,
            SystemClock.uptimeMillis() - activity.xrInitStartedAtMsInternal)

        val bridgeSurface = bridge.surface
        val isStaticImageSession = activity.isCurrentVrStaticImageSessionInternal()
        syncVrPlayerBindingToBridgeSurface(
            bridgeSurface = bridgeSurface,
            textureId = bridge.textureId,
            isStaticImageSession = isStaticImageSession,
        )

        Timber.d("VrPlayerActivity: initializeVrRenderPipeline — refreshing layer descriptor")
        activity.refreshLayerDescriptorInternal("session-ready")

        val sessionMgr = xrSessionManagerProvider()
        if (sessionMgr != null) {
            val newRenderer = VrHudRenderer(sessionMgr)
            if (newRenderer.ensureSwapchainCreated()) {
                vrHudRenderer = newRenderer
                // S0024 Phase 04 smoke wire: seek-bar callback. Real seek belongs to S0019.
                val composer = VrHudSceneComposer(
                    context = activity,
                    width = newRenderer.width,
                    height = newRenderer.height,
                    onSeekBarClick = { Timber.d("HUD click: seek-bar") },
                )
                val driver = VrHudSceneDriver(newRenderer, composer)
                vrHudSceneDriver = driver
                vrHudManager = driver
                Timber.i("VrPlayerActivity: HUD scene driver active (immersive)")
                startVrHudProgressTicker()

                // S0024 Phase 02 + 03 + 04: wire HUD ray-input. Math gated by HUD layer
                // visibility (strategic §11 criterion 4 — no work when the layer is not
                // submitted). Hover changes flip `currentHudHoverId` and request a redraw
                // via the driver only when the id actually changes. The dispatcher sees
                // every controller-trigger and hand-pinch event and routes them to the
                // registered HUD callback when an element is currently hovered.
                val localInputManager = activity.vrInputManagerInternal
                if (localInputManager != null) {
                    localInputManager.attachHudHitTester(VrHudHitTester())
                    localInputManager.hudRegistryProvider = { vrHudSceneDriver?.registry }
                    localInputManager.hudVisibleProvider = { vrHudSceneDriver?.isLayerVisible == true }
                    localInputManager.hudHoverSink = { _, hudElementId ->
                        if (currentHudHoverId != hudElementId) {
                            Timber.d("VR_AUDIT/1: HUD hit-test transition prev=%d -> next=%d", currentHudHoverId, hudElementId)
                        }
                        currentHudHoverId = hudElementId
                        val driverRef = vrHudSceneDriver
                        if (driverRef != null && driverRef.hoverState.setCurrent(hudElementId)) {
                            driverRef.onHoverIdChanged()
                        }
                    }
                    // S0024 Phase 05: accessibility audio cue (strategic §3.2). System
                    // FX_KEY_CLICK matches the choice already made for hand-pinch UI clicks
                    // in `handlePointerClick` (S0007 §3.5) — no new asset shipped.
                    val audioMgr = activity.audioManagerInternal
                    val dispatcher = VrHudInputDispatcher(
                        hoverState = driver.hoverState,
                        registryProvider = { vrHudSceneDriver?.registry },
                        onClickAudioCue = {
                            audioMgr?.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK)
                        },
                    )
                    localInputManager.hudInputDispatcher = dispatcher
                    localInputManager.hudHoverIdProvider = { driver.hoverState.current() }
                }
            } else {
                Timber.w("VrPlayerActivity: HUD swapchain unavailable — keeping Android-view fallback")
            }
        }
        Timber.i("VR_PERF: [gl-thread] hud_swapchain=%dms  abs=%dms",
            SystemClock.uptimeMillis() - tPipeline0,
            SystemClock.uptimeMillis() - activity.xrInitStartedAtMsInternal)

        if (sessionMgr != null) {
            val panelRenderer = VrInteractivePanelRenderer(sessionMgr)
            if (panelRenderer.ensureSwapchainCreated()) {
                val panelComposer = VrInteractivePanelComposer(activity)
                val panelDriver = VrInteractivePanelDriver(panelRenderer, panelComposer)
                vrInteractivePanelDriver = panelDriver
                activity.controlOverlayInternal = VrControlOverlayManager(
                    activity = activity,
                    onCommand = { command ->
                        activity.handleVrCommandInternal(command, VrCommandSource.UI)
                    },
                    panelDriver = panelDriver,
                )
                Timber.i("VrPlayerActivity: interactive panel driver active")

                val hitTester = VrRayPanelHitTester()
                val hitResolver = VrPanelHitZoneResolver(panelComposer)
                sessionMgr.attachHitTester(hitTester, hitResolver)
                val localInputManager = activity.vrInputManagerInternal
                if (localInputManager != null) {
                    localInputManager.panelVisibleProvider = { panelDriver.isPanelVisible() }
                    localInputManager.panelHoverSink = { _, zoneId, seekFrac ->
                        lastHoveredZoneId = zoneId
                        panelDriver.updateHoverZone(zoneId)
                        if (seekFrac >= 0f) {
                            panelDriver.updateSeekDrag(seekFrac)
                            activity.scheduleSeekDragInternal(seekFrac, panelDriver)
                        } else if (lastSeekDragFraction >= 0f) {
                            activity.commitSeekDragInternal(lastSeekDragFraction, panelDriver)
                        }
                        lastSeekDragFraction = seekFrac
                    }
                    localInputManager.panelClickSink = {
                        activity.dispatchPanelZoneClickInternal(lastHoveredZoneId)
                    }
                }
            } else {
                Timber.w("VrPlayerActivity: panel swapchain unavailable — keeping 2D overlay fallback")
            }
        }
        Timber.i("VR_PERF: [gl-thread] panel_swapchain=%dms  abs=%dms",
            SystemClock.uptimeMillis() - tPipeline0,
            SystemClock.uptimeMillis() - activity.xrInitStartedAtMsInternal)

        Timber.i("VrPlayerActivity: initializeVrRenderPipeline COMPLETE")
        Timber.i("VR_PERF: [gl-thread] pipeline_total=%dms  abs_from_init=%dms",
            SystemClock.uptimeMillis() - tPipeline0,
            SystemClock.uptimeMillis() - activity.xrInitStartedAtMsInternal)
    }

    /**
     * 2 Hz ticker that pulls position / buffered / duration from the inherited
     * ExoPlayer and feeds the HUD scene driver. Cancelled in [releaseVrRenderPipeline].
     */
    private fun startVrHudProgressTicker() {
        vrHudProgressJob?.cancel()
        vrHudProgressJob = activity.lifecycleScope.launch {
            while (isActive) {
                val snapshot = activity.currentPlaybackProgressSnapshotInternal()
                vrHudSceneDriver?.updateProgress(snapshot.positionMs, snapshot.bufferedPositionMs, snapshot.durationMs)
                vrInteractivePanelDriver?.updateProgress(snapshot.positionMs, snapshot.bufferedPositionMs, snapshot.durationMs)
                delay(500L)
            }
        }
    }

    /**
     * Release GL resources on the XR render thread. Both bridge and renderer document that
     * release() must run while their EGL context is current.
     */
    fun releaseVrRenderPipeline() {
        videoSurfaceBridge.release()
        stereoRenderer.release()
        photoSphereRenderer.release()
        vrHudProgressJob?.cancel()
        vrHudProgressJob = null
        vrHudSceneDriver?.hideAll()
        vrHudSceneDriver = null
        vrHudRenderer?.release()
        vrHudRenderer = null
        vrHudManager = vrHudFallback
        vrInteractivePanelDriver?.release()
        vrInteractivePanelDriver = null
        activity.vrInputManagerInternal?.panelVisibleProvider = null
        activity.vrInputManagerInternal?.panelHoverSink = null
        activity.vrInputManagerInternal?.panelClickSink = null
    }

    /**
     * Render a single XR eye using the current descriptor + frame context.
     *
     * The native layer has already bound the correct FBO, so the renderer only needs the
     * per-eye context to choose the UV crop and viewport policy for the active layer type.
     */
    fun renderVrFrame(context: VrRenderContext) {
        val now = android.os.SystemClock.elapsedRealtime()
        val showFps = activity.viewModelInternal.settings.value.vrShowFps
        if (showFps || vrFpsLastValid > 0) {
            vrFpsFrameCount++
            if (now - vrFpsLastUpdateTime >= 500) {
                val previousValid = vrFpsLastValid
                if (showFps && vrFpsFrameCount >= 5 && (now - vrFpsLastUpdateTime) <= 1500) {
                    vrFpsLastValid = (vrFpsFrameCount * 1000f / (now - vrFpsLastUpdateTime)).toInt()
                }
                if (showFps && vrFpsLastValid > 0) {
                    if (previousValid == 0) {
                        Timber.d("VR_AUDIT/13: VR HUD FPS first sample fps=%d", vrFpsLastValid)
                    }
                    vrHudManager?.updateFps(vrFpsLastValid)
                } else if (!showFps && vrFpsLastValid > 0) {
                    Timber.d("VR_FPS: cleared HUD label after vrShowFps→false")
                    Timber.d("VR_AUDIT/13: VR HUD FPS toggle=OFF (cleared)")
                    vrHudManager?.clearFps()
                    vrFpsLastValid = 0
                }
                vrFpsFrameCount = 0
                vrFpsLastUpdateTime = now
            }
        }

        val bridge = videoSurfaceBridge
        val renderer = stereoRenderer
        val photoRenderer = photoSphereRenderer

        if (activity.isVrStaticImageActiveInternal()) {
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

        if (vrFirstFrameLoggedMs == 0L && activity.xrInitStartedAtMsInternal > 0L) {
            vrFirstFrameLoggedMs = SystemClock.uptimeMillis()
            Timber.i("VR_PERF: [gl-thread] first_frame_ready  abs_from_init=%dms",
                vrFirstFrameLoggedMs - activity.xrInitStartedAtMsInternal)
            Timber.d("VR_AUDIT/14: cold-start phase=first-frame-submission absFromInitMs=%d",
                vrFirstFrameLoggedMs - activity.xrInitStartedAtMsInternal)
        }

        if (n % 300L == 1L) {
            Timber.d("VrPlayerActivity: renderVrFrame #%d  eye=%s  layer=%s  stereo=%s  textureId=%d  w=%d h=%d",
                n, context.eye, context.layerType, context.stereoMode,
                bridge.textureId, context.targetWidthPx, context.targetHeightPx)
        }

        if (context.eye == VrEye.LEFT) bridge.updateFrame()

        renderer.renderEye(
            context = context,
            bridge.textureId,
            currentLayerDescriptor,
        )
    }

    fun resolveSourceAspectRatio(): Float {
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

    fun ensureVrPlayerListenerAttached() {
        val player = activity.currentPlaybackPlayerInternal()
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

    fun detachVrPlayerListener() {
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

    fun cachePlayerAspectRatio(videoSize: VideoSize?, reason: String) {
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
            val format = activity.currentPlaybackPlayerInternal()?.videoFormat
            Timber.d(
                "VR_AUDIT/6: ExoPlayer track size=%dx%d codec=%s bitrate=%d ar=%.4f reason=%s",
                width, height,
                format?.sampleMimeType ?: format?.codecs ?: "<unknown>",
                format?.bitrate ?: -1,
                aspectRatio,
                reason,
            )
            return
        }

        cachedPlayerSourceAspectRatio = 0f
        Timber.d("VrPlayerActivity: player aspect ratio unavailable (reason=%s size=%s)",
            reason, videoSize)
    }

    fun syncVrPlayerBindingToBridgeSurface(
        bridgeSurface: Surface?,
        textureId: Int,
        isStaticImageSession: Boolean,
    ) {
        // WHY: both videoSize reads and setVideoSurface() must happen on the player's main thread.
        // Calling them from xr-render-thread floods logcat with IllegalStateException and starves VR rendering.
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

            ensureVrPlayerListenerAttached()

            val exoPlayerInstance = activity.currentPlaybackPlayerInternal()
            val videoSize = activity.currentPlaybackVideoSizeInternal()
            cachePlayerAspectRatio(videoSize, "session-ready")

            Timber.i("VrPlayerActivity: ExoPlayer instance=%s  videoSize=%s  bridgeSurface=%s",
                exoPlayerInstance, videoSize, bridgeSurface)
            if (isStaticImageSession) {
                Timber.i("VrPlayerActivity: static-image immersive session — skipping ExoPlayer surface redirect")
                pendingVrBridgeSurface = null
                pendingVrBridgeTextureId = -1
                setVrRenderingActive(true, "static-image-session-ready")
            } else if (bridgeSurface != null && exoPlayerInstance != null) {
                activity.attachPlaybackVideoSurfaceInternal(bridgeSurface)
                Timber.i("VrPlayerActivity: ExoPlayer video redirected to VR bridge surface (textureId=%d)",
                    textureId)
                pendingVrBridgeSurface = null
                pendingVrBridgeTextureId = -1
                setVrRenderingActive(true, "surface-attached-sync")
            } else if (bridgeSurface != null && exoPlayerInstance == null) {
                pendingVrBridgeSurface = bridgeSurface
                pendingVrBridgeTextureId = textureId
                Timber.w("VrPlayerActivity: XR ready before ExoPlayer — deferring VR surface attach (textureId=%d)",
                    textureId)
                Log.w("VR_BOOT", "VrPlayerActivity: VR surface deferred — waiting for ExoPlayer creation (textureId=$textureId)")
            } else {
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
    fun flushPendingVrSurfaceIfReady() {
        val surface = pendingVrBridgeSurface ?: return
        val textureId = pendingVrBridgeTextureId
        val player = activity.currentPlaybackPlayerInternal() ?: return
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            try {
                player.setVideoSurface(surface)
                Timber.w("VrPlayerActivity: attached DEFERRED VR surface to late-arriving ExoPlayer (textureId=%d)",
                    textureId)
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
    fun setVrRenderingActive(active: Boolean, reason: String) {
        if (vrRenderingActive == active) return
        vrRenderingActive = active
        Timber.i("VrPlayerActivity: vrRenderingActive=%b (reason=%s)", active, reason)
        activity.setVrImmersivePlaybackActiveInternal(active)
        activity.runOnUiThread { activity.updatePlayerFpsOverlay() }
        activity.runOnUiThread {
            activity.vrToggleButtonManagerInternal?.updateState(active)
            if (active && !activity.cheatsheetAutoShownInternal) {
                activity.cheatsheetAutoShownInternal = true
                activity.vrCheatsheetManagerInternal?.showIfFirstTime()
            }
        }
    }
}
