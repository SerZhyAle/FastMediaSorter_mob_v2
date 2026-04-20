package com.sza.fastmediasorter.vr.openxr

import android.app.Activity
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

        val initialized = AtomicBoolean(false)
        val initFinished = AtomicBoolean(false)
        val startLatch = Object()

        val renderThread = Thread({
            val threadName = Thread.currentThread().name
            val threadId   = Thread.currentThread().id
            Timber.i("OpenXrSessionManager: render thread started — name='%s' id=%d", threadName, threadId)

            val egl = XrEglContext()
            Timber.d("OpenXrSessionManager: creating EGL context...")
            if (!egl.create()) {
                Timber.e("OpenXrSessionManager: EGL setup FAILED — no GL context, XR cannot start")
                starting.set(false)
                initFinished.set(true)
                synchronized(startLatch) { startLatch.notifyAll() }
                return@Thread
            }
            Timber.i("OpenXrSessionManager: EGL context created OK")

            Timber.i("OpenXrSessionManager: calling nativeInitialize...")
            val ok = try {
                val result = OpenXrNative.nativeInitialize(activity, callback)
                Timber.i("OpenXrSessionManager: nativeInitialize returned %b", result)
                result
            } catch (t: Throwable) {
                Timber.e(t, "OpenXrSessionManager: nativeInitialize THREW — see stack above")
                false
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

            Timber.i("OpenXrSessionManager: applying initial layer descriptor type=%s", currentLayerDescriptor.type)
            applyLayerDescriptor(currentLayerDescriptor)

            // Initialise GL bridge/renderer resources on the GL thread before the loop.
            // This must run after nativeInitialize so the EGL context + XR session exist.
            Timber.i("OpenXrSessionManager: invoking onSessionReady callback")
            try {
                onSessionReady?.invoke()
                Timber.i("OpenXrSessionManager: onSessionReady completed OK")
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
    }

    fun isActive(): Boolean = running.get()

    fun updateLayerDescriptor(descriptor: VrLayerDescriptor) {
        currentLayerDescriptor = descriptor
        if (running.get() || starting.get()) {
            applyLayerDescriptor(descriptor)
        }
    }

    fun eyeWidth(eye: Int): Int = OpenXrNative.nativeGetEyeWidth(eye)
    fun eyeHeight(eye: Int): Int = OpenXrNative.nativeGetEyeHeight(eye)

    fun requestStereoSnapshot(): Boolean {
        if (!running.get()) return false
        return try {
            OpenXrNative.nativeRequestStereoSnapshot()
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

    private fun applyLayerDescriptor(descriptor: VrLayerDescriptor) {
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
            Timber.w(t, "OpenXrSessionManager: failed to apply layer descriptor %s", descriptor.type)
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
