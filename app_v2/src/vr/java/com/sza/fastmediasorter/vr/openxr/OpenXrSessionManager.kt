package com.sza.fastmediasorter.vr.openxr

import android.app.Activity
import android.os.SystemClock
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import com.sza.fastmediasorter.vr.render.VrLayerDescriptor
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the OpenXR session lifecycle on a dedicated render thread.
 *
 * Responsibilities:
 *  - Load native libs (`openxr_loader` + `openxr_native`).
 *  - Spin up a render thread with an EGL ES 3 context.
 *  - Call [OpenXrNative.nativeInitialize] inside that thread so the runtime can
 *    capture the live EGL context for its graphics binding.
 *  - Drive `nativeRunFrame()` in a loop until the runtime asks to exit or the
 *    Activity calls [release].
 *
 * On non-headset devices [initialize] returns false — [VrPlayerActivity] uses
 * that signal to route to the phone-fallback screen.
 */
class OpenXrSessionManager(
    private val callback: XrRenderCallback,
    /**
     * Optional hook called on the GL thread immediately after [OpenXrNative.nativeInitialize]
     * succeeds and before the render loop starts. Use it to initialise GL resources
     * (bridge.initialize(), renderer.initGl(), redirect ExoPlayer surface) that require
     * a live EGL context — exactly what [VrPlayerActivity.initializeVrRenderPipeline] does.
     * Called once per [initialize] invocation; safe to call on onPause/onResume cycles.
     */
    private val onSessionReady: (() -> Unit)? = null,
    /**
     * Optional hook called on the render thread after the loop exits and before EGL teardown.
     * Use it for GL cleanup because classes like [VrVideoSurfaceTextureBridge] and
     * [VrStereoRenderer] require release() on the thread that owns the current context.
     */
    private val onSessionStopped: (() -> Unit)? = null,
    /**
     * Optional callback that receives per-frame controller/stick/grip edge events from
     * the native OpenXR action system. Registered via [OpenXrNative.nativeSetInputCallback]
     * after a successful session init.
     */
    private val inputCallback: XrInputCallback? = null,
) {

    data class StereoSnapshotPixels(
        val width: Int,
        val height: Int,
        val leftEyePixels: IntArray,
        val rightEyePixels: IntArray,
    ) {
        // IntArray is not structurally compared by Kotlin data-class defaults;
        // override to maintain correct value semantics.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StereoSnapshotPixels) return false
            return width == other.width &&
                height == other.height &&
                leftEyePixels.contentEquals(other.leftEyePixels) &&
                rightEyePixels.contentEquals(other.rightEyePixels)
        }

        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            result = 31 * result + leftEyePixels.contentHashCode()
            result = 31 * result + rightEyePixels.contentHashCode()
            return result
        }
    }
    @Volatile
    private var currentLayerDescriptor = VrLayerDescriptor()

    private val running = AtomicBoolean(false)
    private val starting = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private var thread: Thread? = null

    /**
     * Start the render thread and initialize the XR session.
     *
     * @return true once native init succeeded; false if the runtime is unavailable
     *         or EGL setup failed. On failure, no thread is left running.
     */
    fun initialize(activity: Activity): Boolean {
        Timber.d("S0132: OpenXrSessionManager.initialize entry — swapchain format anchor + XR-init stage breakdown")
        synchronized(this) {
            if (running.get()) {
                Timber.w("OpenXrSessionManager: already running")
                return true
            }
            if (starting.get() || thread?.isAlive == true) {
                Timber.w("OpenXrSessionManager: initialize already in progress")
                return true
            }
            if (!OpenXrNative.ensureLoaderLoaded()) {
                Timber.w("OpenXrSessionManager: OpenXR libraries not available on this device")
                return false
            }
            starting.set(true)
            stopRequested.set(false)
        }

        // S0132 P05.3a: STAGE_XR_INIT_BEGIN marker — first XR-init action after the guard
        // synchronization block. Elapsed is measured from this point because the activity-side
        // clock is owned by VrPlayerActivity; this anchor pairs with STAGE_SETUP_VIEWS upstream.
        val xrInitBeginMs = SystemClock.uptimeMillis()
        Timber.d("VR_AUDIT/14: cold-start stage=STAGE_XR_INIT_BEGIN elapsed=0 tUptimeMs=%d", xrInitBeginMs)

        val initialized = AtomicBoolean(false)
        val initFinished = AtomicBoolean(false)
        val startLatch = Object()

        val renderThread = Thread({
            val threadName = Thread.currentThread().name
            val threadId   = Thread.currentThread().id
            Timber.i("OpenXrSessionManager: render thread started — name='%s' id=%d", threadName, threadId)

            val tInit = SystemClock.uptimeMillis()
            val egl = XrEglContext()
            Timber.d("OpenXrSessionManager: creating EGL context...")
            if (!egl.create()) {
                drainNativeLog()
                Timber.e("OpenXrSessionManager: EGL setup FAILED — no GL context, XR cannot start")
                starting.set(false)
                initFinished.set(true)
                synchronized(startLatch) { startLatch.notifyAll() }
                return@Thread
            }
            Timber.i("OpenXrSessionManager: EGL context created OK")
            Timber.i("VR_PERF: [xr-thread] egl_create=%dms", SystemClock.uptimeMillis() - tInit)
            // S0132 P05.3a: STAGE_EGL_CREATE marker — same grep family as the rest of the
            // stage-breakdown set; redundant with VR_PERF egl_create line above but uses the
            // stable `cold-start stage=STAGE_*` wording the data-collection script expects.
            Timber.d("VR_AUDIT/14: cold-start stage=STAGE_EGL_CREATE elapsed=%d", SystemClock.uptimeMillis() - xrInitBeginMs)

            val tNative = SystemClock.uptimeMillis()
            Timber.i("OpenXrSessionManager: calling nativeInitialize...")
            Timber.d("VR_AUDIT/14: cold-start phase=nativeInitialize-begin tUptimeMs=%d", tNative)
            val ok = try {
                val result = OpenXrNative.nativeInitialize(activity, callback)
                drainNativeLog()
                Timber.i("OpenXrSessionManager: nativeInitialize returned %b", result)
                Timber.i("VR_PERF: [xr-thread] native_init=%dms  cumulative=%dms", SystemClock.uptimeMillis() - tNative, SystemClock.uptimeMillis() - tInit)
                Timber.d("VR_AUDIT/14: cold-start phase=nativeInitialize-end result=%b nativeMs=%d eglToInitMs=%d",
                    result, SystemClock.uptimeMillis() - tNative, SystemClock.uptimeMillis() - tInit)
                // S0132 P05.3a: STAGE_NATIVE_INIT marker — pairs with STAGE_EGL_CREATE for
                // the native-init slice of the cold-start budget.
                Timber.d("VR_AUDIT/14: cold-start stage=STAGE_NATIVE_INIT elapsed=%d result=%b",
                    SystemClock.uptimeMillis() - xrInitBeginMs, result)
                result
            } catch (t: Throwable) {
                drainNativeLog()
                Timber.e(t, "OpenXrSessionManager: nativeInitialize THREW — see stack above")
                false
            }

            // S0132 P01.2a: swapchain format diagnostic for S0041 hypothesis E (sRGB
            // double-gamma). Format selection lives in native code; this Kotlin-side anchor
            // is logged when nativeInitialize succeeds so a single `grep VR_QUALITY_DEBUG`
            // captures the session-creation timepoint. Native log lines from
            // `OpenXrNative` (drainNativeLog) carry the actual numeric XR_*_FORMAT value.
            if (ok) {
                Timber.d(
                    "VR_QUALITY_DEBUG: swapchain format=native-selected (see OpenXrNative log lines around nativeInitialize-end for XR_*_FORMAT)"
                )
            }

            // Register the input callback as soon as the native session is up so that
            // controller events start flowing on the first rendered frame.
            if (ok) {
                val localInputCallback = inputCallback
                if (localInputCallback != null) {
                    try {
                        OpenXrNative.nativeSetInputCallback(localInputCallback)
                        Timber.i("OpenXrSessionManager: input callback registered")
                    } catch (t: Throwable) {
                        Timber.w(t, "OpenXrSessionManager: nativeSetInputCallback failed — controllers will be silent")
                    }
                } else {
                    Timber.d("OpenXrSessionManager: no inputCallback provided — controller input disabled")
                }
            }

            initialized.set(ok)
            initFinished.set(true)
            synchronized(startLatch) { startLatch.notifyAll() }

            if (!ok) {
                Timber.e("OpenXrSessionManager: nativeInitialize failed — tearing down EGL")
                starting.set(false)
                egl.destroy()
                return@Thread
            }

            if (stopRequested.get()) {
                Timber.i("OpenXrSessionManager: init completed after release request, skipping render loop")
                starting.set(false)
                OpenXrNative.nativeRelease()
                egl.destroy()
                synchronized(this@OpenXrSessionManager) {
                    if (thread === Thread.currentThread()) {
                        thread = null
                    }
                }
                return@Thread
            }

            running.set(true)
            starting.set(false)

            applyLayerDescriptor(currentLayerDescriptor, reason = "session-init")

            // Initialise GL bridge/renderer resources on the GL thread before the loop.
            // This must run after nativeInitialize so the EGL context + XR session exist.
            val tReady = SystemClock.uptimeMillis()
            Timber.i("OpenXrSessionManager: invoking onSessionReady callback")
            Timber.d("VR_AUDIT/14: cold-start phase=onSessionReady-begin sinceInitMs=%d", SystemClock.uptimeMillis() - tInit)
            try {
                onSessionReady?.invoke()
                Timber.i("OpenXrSessionManager: onSessionReady completed OK")
                Timber.i("VR_PERF: [xr-thread] session_ready_cb=%dms  cumulative=%dms", SystemClock.uptimeMillis() - tReady, SystemClock.uptimeMillis() - tInit)
                Timber.d("VR_AUDIT/14: cold-start phase=onSessionReady-end cbMs=%d sinceInitMs=%d",
                    SystemClock.uptimeMillis() - tReady, SystemClock.uptimeMillis() - tInit)
                // S0132 P05.3a: STAGE_SESSION_READY marker — boundary between native session
                // ready and the GL render pipeline starting; the first frame is tracked by
                // STAGE_FIRST_FRAME in VrRenderPipelineManager.
                Timber.d("VR_AUDIT/14: cold-start stage=STAGE_SESSION_READY elapsed=%d",
                    SystemClock.uptimeMillis() - xrInitBeginMs)
            } catch (t: Throwable) {
                Timber.e(t, "OpenXrSessionManager: onSessionReady THREW — continuing without pipeline")
            }

            // Frame counter for render loop health diagnostics.
            var frameCount = 0L
            Timber.i("OpenXrSessionManager: render loop starting")
            while (!stopRequested.get() && running.get() && OpenXrNative.nativeShouldContinue()) {
                OpenXrNative.nativeRunFrame()
                frameCount++
                if (frameCount % 300L == 1L) {
                    Timber.d("OpenXrSessionManager: render loop alive — frame #%d  stopReq=%b",
                        frameCount, stopRequested.get())
                }
            }
            Timber.i("OpenXrSessionManager: render loop exited after %d frames  stopReq=%b  running=%b  nativeShouldContinue=%b",
                frameCount, stopRequested.get(), running.get(), runCatching { OpenXrNative.nativeShouldContinue() }.getOrDefault(false))

            running.set(false)
            Timber.i("OpenXrSessionManager: invoking onSessionStopped for GL cleanup")
            try {
                onSessionStopped?.invoke()
                Timber.i("OpenXrSessionManager: onSessionStopped completed OK")
            } catch (t: Throwable) {
                Timber.e(t, "OpenXrSessionManager: onSessionStopped THREW during GL cleanup")
            }

            Timber.i("OpenXrSessionManager: calling nativeRelease")
            OpenXrNative.nativeRelease()
            drainNativeLog()
            Timber.i("OpenXrSessionManager: destroying EGL context")
            egl.destroy()

            synchronized(this@OpenXrSessionManager) {
                if (thread === Thread.currentThread()) {
                    thread = null
                }
            }
        }, "xr-render-thread")

        synchronized(this) {
            thread = renderThread
        }
        renderThread.isDaemon = true
        renderThread.start()

        val deadlineMs = System.currentTimeMillis() + INIT_TIMEOUT_MS
        synchronized(startLatch) {
            while (!initFinished.get() && renderThread.isAlive) {
                val remainingMs = deadlineMs - System.currentTimeMillis()
                if (remainingMs <= 0L) break
                try {
                    startLatch.wait(remainingMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        if (!initFinished.get()) {
            Timber.e("OpenXrSessionManager: init TIMED OUT after ${INIT_TIMEOUT_MS}ms — calling release")
            release()
            return false
        }

        val result = initialized.get()
        Timber.i("OpenXrSessionManager: initialize() returning %b", result)
        return result
    }

    /** Signal the render thread to exit and wait briefly for it to finish. */
    fun release() {
        Timber.i("OpenXrSessionManager: release() called")
        val renderThread = synchronized(this) {
            val activeThread = thread
            if (activeThread == null && !running.get() && !starting.get()) {
                Timber.d("OpenXrSessionManager: release() — nothing to stop (no active thread)")
                return
            }
            Timber.d("OpenXrSessionManager: release() — setting stopRequested, thread=%s",
                activeThread?.name ?: "null")
            stopRequested.set(true)
            running.set(false)
            starting.set(false)
            activeThread
        }

        try {
            OpenXrNative.nativeRequestExit()
            Timber.d("OpenXrSessionManager: nativeRequestExit sent")
        } catch (t: Throwable) {
            Timber.w(t, "OpenXrSessionManager: nativeRequestExit failed during release")
        }

        try {
            renderThread?.join(RELEASE_TIMEOUT_MS)
            if (renderThread?.isAlive == true) {
                Timber.w("OpenXrSessionManager: render thread still alive after %dms join timeout!", RELEASE_TIMEOUT_MS)
            } else {
                Timber.i("OpenXrSessionManager: render thread joined OK")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        synchronized(this) {
            if (thread === renderThread && renderThread?.isAlive != true) {
                thread = null
            }
        }
        drainNativeLog()
    }

    fun isActive(): Boolean = running.get()

    fun updateLayerDescriptor(descriptor: VrLayerDescriptor, reason: String = "external-update") {
        currentLayerDescriptor = descriptor
        if (running.get() || starting.get()) {
            applyLayerDescriptor(descriptor, reason)
        }
    }

    fun eyeWidth(eye: Int): Int = OpenXrNative.nativeGetEyeWidth(eye)
    fun eyeHeight(eye: Int): Int = OpenXrNative.nativeGetEyeHeight(eye)

    // ── HUD composition layer (spec_vr-immersive-hud-gl) ────────────────────
    // Pass-through to native. Native phase-01 implementation is a stub; phase 02
    // creates the real XrSwapchain and includes the layer in xrEndFrame.

    /** Allocate (or re-allocate) the HUD swapchain. No-op when session is not running. */
    fun createHudSwapchain(width: Int, height: Int): Boolean {
        if (!running.get()) return false
        val eyeW = OpenXrNative.nativeGetEyeWidth(0)
        val eyeH = OpenXrNative.nativeGetEyeHeight(0)
        Timber.d(
            "VR_AUDIT/9: HUD swapchain create requested size=%dx%d eyeBuf=%dx%d ratioW=%.3f ratioH=%.3f",
            width, height, eyeW, eyeH,
            if (eyeW > 0) width.toFloat() / eyeW else -1f,
            if (eyeH > 0) height.toFloat() / eyeH else -1f,
        )
        return OpenXrNative.nativeCreateHudSwapchain(width, height)
    }

    /** Release HUD swapchain resources. Safe even when the session has stopped. */
    fun destroyHudSwapchain() {
        if (running.get() || starting.get()) {
            OpenXrNative.nativeDestroyHudSwapchain()
        }
    }

    /** Toggle the HUD quad layer's inclusion in the next xrEndFrame. */
    fun setHudLayerVisible(visible: Boolean) {
        if (!running.get()) {
            Timber.d("OpenXrSessionManager: setHudLayerVisible(%s) ignored — session not running", visible)
            return
        }
        OpenXrNative.nativeSetHudLayerVisible(visible)
    }

    /** Upload a premultiplied ARGB_8888 [bitmap] to the HUD swapchain. */
    fun uploadHudBitmap(bitmap: android.graphics.Bitmap): Boolean {
        if (!running.get()) return false
        return OpenXrNative.nativeUploadHudBitmap(bitmap)
    }

    // ── Controller ray (spec_vr-immersive-controls-panel Phase 02) ───────────

    /** Toggle GL ray line rendering. NDC events are always emitted regardless. */
    fun setControllerRayEnabled(enabled: Boolean) {
        OpenXrNative.nativeSetControllerRayEnabled(enabled)
    }

    // ── Interactive panel swapchain (spec_vr-immersive-controls-panel Phase 03) ──

    /** Allocate (or re-allocate) the interactive panel swapchain. */
    fun createPanelSwapchain(width: Int, height: Int): Boolean {
        if (!running.get()) {
            Timber.w(
                "OpenXrSessionManager: createPanelSwapchain refused — session not running (size=%dx%d)",
                width, height,
            )
            return false
        }
        Timber.i("OpenXrSessionManager: createPanelSwapchain → native (size=%dx%d)", width, height)
        var ok = OpenXrNative.nativeCreatePanelSwapchain(width, height)
        if (!ok && running.get()) {
            // S0020 ADR-1: defensive single retry. After Phase 01 the native side checks
            // session-handle (loose) instead of session-running (strict), so this branch
            // should be unreachable — but if a future regression re-tightens the check,
            // a 50 ms grace gives the session-state event a chance to land.
            Timber.i("OpenXrSessionManager: panel retry after initial false (size=%dx%d)", width, height)
            Thread.sleep(50)
            ok = OpenXrNative.nativeCreatePanelSwapchain(width, height)
        }
        if (ok) {
            Timber.i("OpenXrSessionManager: panel ready (size=%dx%d)", width, height)
        } else {
            Timber.w(
                "OpenXrSessionManager: panel never came up (size=%dx%d) — see OpenXrNative log entries above",
                width, height,
            )
        }
        return ok
    }

    /** Release panel swapchain resources. */
    fun destroyPanelSwapchain() {
        if (running.get() || starting.get()) {
            OpenXrNative.nativeDestroyPanelSwapchain()
        }
    }

    /** Toggle the panel quad layer's inclusion in the next xrEndFrame. */
    fun setPanelLayerVisible(visible: Boolean) {
        if (!running.get()) return
        OpenXrNative.nativeSetPanelLayerVisible(visible)
    }

    /** Upload a premultiplied ARGB_8888 [bitmap] to the panel swapchain. */
    fun uploadPanelBitmap(bitmap: android.graphics.Bitmap): Boolean {
        if (!running.get()) return false
        return OpenXrNative.nativeUploadPanelBitmap(bitmap)
    }

    /**
     * Attach hit-test infrastructure to the input callback (Phase 04).
     * Delegates to [VrControllerInputManager.attachHitTester] if the registered
     * [inputCallback] is a [com.sza.fastmediasorter.vr.helpers.VrControllerInputManager].
     */
    fun attachHitTester(
        hitTester: com.sza.fastmediasorter.vr.ui.VrRayPanelHitTester,
        resolver: com.sza.fastmediasorter.vr.ui.VrPanelHitZoneResolver,
    ) {
        (inputCallback as? com.sza.fastmediasorter.vr.helpers.VrControllerInputManager)
            ?.attachHitTester(hitTester, resolver)
    }

    fun requestStereoSnapshot(): Boolean {
        Timber.d("OpenXrSessionManager: requestStereoSnapshot enter running=%s", running.get())
        if (!running.get()) {
            Timber.w("OpenXrSessionManager: requestStereoSnapshot refused — session not running")
            return false
        }
        return try {
            val accepted = OpenXrNative.nativeRequestStereoSnapshot()
            if (accepted) {
                Timber.i("OpenXrSessionManager: requestStereoSnapshot accepted")
            } else {
                Timber.w("OpenXrSessionManager: requestStereoSnapshot native returned false")
            }
            accepted
        } catch (t: Throwable) {
            Timber.w(t, "OpenXrSessionManager: failed to request stereo snapshot")
            false
        }
    }

    fun pollStereoSnapshot(): StereoSnapshotPixels? {
        if (!running.get()) return null
        return try {
            if (!OpenXrNative.nativeIsStereoSnapshotReady()) return null

            val width = eyeWidth(0)
            val height = eyeHeight(0)
            if (width <= 0 || height <= 0) {
                OpenXrNative.nativeReleaseStereoSnapshot()
                return null
            }

            val leftEyePixels = OpenXrNative.nativeConsumeStereoSnapshotPixels(0)
            val rightEyePixels = OpenXrNative.nativeConsumeStereoSnapshotPixels(1)
            if (leftEyePixels == null || rightEyePixels == null) {
                OpenXrNative.nativeReleaseStereoSnapshot()
                return null
            }

            val snapshot = StereoSnapshotPixels(
                width = width,
                height = height,
                leftEyePixels = leftEyePixels,
                rightEyePixels = rightEyePixels,
            )
            OpenXrNative.nativeReleaseStereoSnapshot()
            snapshot
        } catch (t: Throwable) {
            Timber.w(t, "OpenXrSessionManager: failed to consume stereo snapshot")
            try {
                OpenXrNative.nativeReleaseStereoSnapshot()
            } catch (_: Throwable) {
            }
            null
        }
    }

    private fun drainNativeLog() {
        val entries = try {
            OpenXrNative.nativeDrainLog()
        } catch (t: Throwable) {
            Timber.w(t, "OpenXrSessionManager: nativeDrainLog failed")
            return
        }
        for (raw in entries) {
            if (raw.isEmpty()) continue
            val pipeIdx = raw.indexOf('|')
            val prio = if (pipeIdx > 0) raw[0] else 'I'
            val msg = if (pipeIdx >= 0) raw.substring(pipeIdx + 1) else raw
            val tagged = Timber.tag("OpenXrNative")
            when (prio) {
                'E' -> tagged.e(msg)
                'W' -> tagged.w(msg)
                'I' -> tagged.i(msg)
                'D' -> tagged.d(msg)
                else -> tagged.v(msg)
            }
        }
    }

    private fun applyLayerDescriptor(descriptor: VrLayerDescriptor, reason: String) {
        Timber.d("OpenXrSessionManager: applyLayerDescriptor → %s (reason=%s)", descriptor.type, reason)
        // ADR-3 (S0027): single reference log line per layer application. Compare against
        // known-good snapshots in tests to catch geometry regressions without a headset.
        // orientation is always (0,0,0,1) — identity in appSpace (LOCAL reference space).
        Timber.i(
            "VideoLayerGeometry: type=%s orientation=(0,0,0,1) position=(0,0,0)" +
                " centralAngle=%.4f upper=%.4f lower=%.4f radius=%.4f (reason=%s)",
            descriptor.type,
            descriptor.centralHorizontalAngleRadians,
            descriptor.upperVerticalAngleRadians,
            descriptor.lowerVerticalAngleRadians,
            descriptor.radiusMeters,
            reason,
        )
        // S0041 cross-grep companion — same payload behind the VR_QUALITY_DEBUG marker so
        // a single `grep VR_QUALITY_DEBUG` catches geometry, track format, render UV, and
        // fisheye uniform lines together.
        Timber.d(
            "VR_QUALITY_DEBUG: layerGeom type=%s centralAngle=%.4f upper=%.4f lower=%.4f" +
                " radius=%.4f width=%.4f height=%.4f distance=%.4f (reason=%s)",
            descriptor.type,
            descriptor.centralHorizontalAngleRadians,
            descriptor.upperVerticalAngleRadians,
            descriptor.lowerVerticalAngleRadians,
            descriptor.radiusMeters,
            descriptor.widthMeters,
            descriptor.heightMeters,
            descriptor.distanceMeters,
            reason,
        )
        try {
            OpenXrNative.nativeConfigureLayer(
                layerType = descriptor.type.nativeValue,
                widthMeters = descriptor.widthMeters,
                heightMeters = descriptor.heightMeters,
                distanceMeters = descriptor.distanceMeters,
                radiusMeters = descriptor.radiusMeters,
                centralHorizontalAngleRadians = descriptor.centralHorizontalAngleRadians,
                upperVerticalAngleRadians = descriptor.upperVerticalAngleRadians,
                lowerVerticalAngleRadians = descriptor.lowerVerticalAngleRadians,
            )
        } catch (t: Throwable) {
            Timber.w(t, "OpenXrSessionManager: failed to apply layer descriptor %s (reason=%s)", descriptor.type, reason)
        }
    }

    companion object {
        // xrCreateInstance can take 5+ seconds on a cold process start (first launch after
        // reboot) while the Meta runtime loader initialises all plugins. 12 s covers the
        // observed worst case (~5.4 s) with a safe margin; subsequent warm launches finish
        // in ~130 ms so the extra headroom has no practical cost.
        private const val INIT_TIMEOUT_MS = 12_000L
        private const val RELEASE_TIMEOUT_MS = 2_000L
    }
}

/**
 * Thrown when the OpenXR runtime is unavailable (e.g. running on a phone
 * or the Meta XR runtime is not installed on the headset).
 */
class OpenXrInitException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Minimal EGL ES 3 context owned by the render thread. Pbuffer-backed because
 * the real rendering target is the XR swapchain — not an Android Surface.
 */
private class XrEglContext {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun create(): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return false
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false

        val cfgAttrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 24,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numCfg = IntArray(1)
        if (!EGL14.eglChooseConfig(display, cfgAttrs, 0, configs, 0, 1, numCfg, 0) ||
            numCfg[0] == 0 || configs[0] == null) {
            return false
        }
        val config = configs[0]!!

        val ctxAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttrs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) return false

        val pbufAttrs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        surface = EGL14.eglCreatePbufferSurface(display, config, pbufAttrs, 0)
        if (surface == EGL14.EGL_NO_SURFACE) return false

        return EGL14.eglMakeCurrent(display, surface, surface, context)
    }

    fun destroy() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        surface = EGL14.EGL_NO_SURFACE
    }

    companion object {
        // EGL_OPENGL_ES3_BIT isn't exposed as a constant in android.opengl.EGL14.
        private const val EGL_OPENGL_ES3_BIT = 0x0040
    }
}
