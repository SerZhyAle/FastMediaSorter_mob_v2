package com.sza.fastmediasorter.vr

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.StereoDetector
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import com.sza.fastmediasorter.ui.player.helpers.seekForward
import com.sza.fastmediasorter.ui.player.helpers.seekBackward
import com.sza.fastmediasorter.ui.player.render.RenderPriority
import com.sza.fastmediasorter.ui.player.render.RenderTarget
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
    private val stereoDetector = StereoDetector()

    @Volatile
    private var currentVrImageKey: String? = null

    private val playbackPrefs by lazy {
        getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var currentLayerDescriptor = VrLayerDescriptor()

    private var currentStereoMode = StereoMode.MONO
    private var currentRenderingMode = VrRenderingMode.CINEMA

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
        // Check XR runtime BEFORE super.onCreate() to avoid PlayerActivity's
        // full initialisation (binding, ExoPlayer, file list) on non-headset devices.
        if (!isXrRuntimeAvailable()) {
            // Must call AppCompatActivity.onCreate at minimum for Activity lifecycle
            super.onCreate(savedInstanceState)
            Timber.w("VrPlayerActivity: no XR runtime, falling back to phone screen")
            startActivity(Intent(this, VrPhoneFallbackActivity::class.java))
            finish()
            return
        }

        super.onCreate(savedInstanceState)

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
        playbackPrefs.registerOnSharedPreferenceChangeListener(renderingModeListener)
        refreshLayerDescriptor("activity-created")

        // Propagate stereoMode ViewModel changes to VrStereoRenderer.
        // PlayerManagerInitializer already observes this to call VideoPlayerManager.applyStereoEffect()
        // (phone-screen path), but on VR the renderer needs its own observer so that when
        // the OpenXR render loop is active, VrStereoRenderer can UV-crop per eye correctly.
        val localRenderer = stereoRenderer
        if (localRenderer != null) {
            lifecycleScope.launch {
                viewModel.stereoMode.collectLatest { mode ->
                    val resolved = when (mode) {
                        StereoMode.AUTO, StereoMode.UNKNOWN -> StereoMode.MONO
                        else -> mode
                    }
                    currentStereoMode = resolved
                    Timber.d("VrPlayerActivity: stereoMode → $mode → renderer=$resolved")
                    localRenderer.setStereoMode(resolved)
                    refreshLayerDescriptor("stereo-mode")
                }
            }
        }

        val localPhotoRenderer = photoSphereRenderer
        if (localPhotoRenderer != null) {
            lifecycleScope.launch {
                viewModel.state.collectLatest { state ->
                    syncVrImageTarget(state.currentFile, localPhotoRenderer)
                }
            }
        }

        Timber.d("VrPlayerActivity: VR components initialised, XR session will start in onResume")
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
        val bridge = videoSurfaceBridge ?: return
        val renderer = stereoRenderer ?: return
        val photoRenderer = photoSphereRenderer

        // Release stale GL objects tied to any previous EGL context before re-init.
        // glDelete* calls on invalid IDs (from a destroyed context) produce GL_INVALID_VALUE
        // but do not crash — the old context's resources are freed by EGL on context destruction.
        bridge.release()
        renderer.release()
        photoRenderer?.release()

        // Re-create GL resources in the current (new) EGL context.
        bridge.initialize()
        renderer.initGl()
        photoRenderer?.initGl()

        // Redirect ExoPlayer video output from PlayerView to the VR bridge surface.
        // The inherited VideoPlayerManager exposes the ExoPlayer instance.
        val bridgeSurface = bridge.surface
        if (bridgeSurface != null) {
            videoPlayerManager.exoPlayer?.setVideoSurface(bridgeSurface)
            Timber.d("VrPlayerActivity: ExoPlayer video redirected to VR bridge surface")
        } else {
            Timber.w("VrPlayerActivity: bridge surface null after initialize — video will render to PlayerView")
        }

        // The native session is now alive, so push the current layer choice again on the render thread.
        refreshLayerDescriptor("session-ready")
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
        val bridge = videoSurfaceBridge ?: return
        val renderer = stereoRenderer ?: return
        val photoRenderer = photoSphereRenderer

        if (isVrStaticImageActive() && photoRenderer != null) {
            photoRenderer.renderEye(context, currentLayerDescriptor)
            return
        }

        if (!bridge.isReady()) return

        // Pull latest video frame into OES texture — once per frame, on the left eye.
        if (context.eye == VrEye.LEFT) bridge.updateFrame()

        renderer.renderEye(
            context = context,
            bridge.textureId,
            currentLayerDescriptor,
        )
    }

    private fun resolveSourceAspectRatio(): Float {
        val videoSize = videoPlayerManager.exoPlayer?.videoSize
        if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
            return videoSize.width.toFloat() / videoSize.height.toFloat()
        }

        val descriptorHeight = currentLayerDescriptor.heightMeters
        if (descriptorHeight > 0f) {
            return currentLayerDescriptor.widthMeters / descriptorHeight
        }

        return VrRenderContext.DEFAULT_SOURCE_ASPECT_RATIO
    }

    override fun onResume() {
        super.onResume()
        // initialize() blocks up to 5 s waiting for nativeInitialize — run off main thread.
        // Failure here means no XR runtime (shouldn't happen since we checked in onCreate,
        // but defensive in case of session loss mid-session).
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = xrSessionManager?.initialize(this@VrPlayerActivity) ?: false
            if (!ok && !isFinishing) {
                Timber.w("VrPlayerActivity: XR session could not start in onResume")
            }
        }
    }

    override fun onPause() {
        // Stop the XR render loop before super.onPause() pauses ExoPlayer,
        // so we don't render stale frames into the compositor while pausing.
        xrSessionManager?.release()
        super.onPause()
    }

    override fun onDestroy() {
        // GL resources are released from OpenXrSessionManager.onSessionStopped on the render thread.
        // Only clear object references here to avoid calling GLES APIs from the main thread.
        playbackPrefs.unregisterOnSharedPreferenceChangeListener(renderingModeListener)
        videoSurfaceBridge = null
        stereoRenderer = null
        photoSphereRenderer = null
        xrSessionManager?.release()
        xrSessionManager = null
        super.onDestroy()
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
            PlaybackCommand.Exit -> finish()
            else -> Timber.w("VrPlayerActivity: unhandled VR command $command")
        }
    }

    private fun refreshLayerDescriptor(reason: String) {
        val descriptor = vrLayerFactory.describe(currentStereoMode, currentRenderingMode)
        currentLayerDescriptor = descriptor
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
