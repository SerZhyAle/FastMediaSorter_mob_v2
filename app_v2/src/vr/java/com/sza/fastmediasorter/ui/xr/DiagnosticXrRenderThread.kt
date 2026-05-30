package com.sza.fastmediasorter.ui.xr

import android.app.Activity
import android.view.Surface
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrNativeResult
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * S0249 Phase 02 step 02.6: dedicated render thread that drives the OpenXR frame loop.
 *
 * Owns the full pipeline (init -> attach surface -> start session -> upload texture -> frame
 * loop -> shutdown) and stays alive until the loop returns. The Activity instantiates one
 * instance, starts it after the SurfaceView's Surface is ready, and calls [requestExit] from
 * `onPause` / input handler / `onDestroy` to wake the loop.
 *
 * The thread blocks inside [DiagnosticXrRuntime.runFrameLoop] for most of its life; nothing
 * else should be posted to it (there is no Handler). Cleanup (`shutdown`) happens on the same
 * thread immediately after the loop exits, so all GL/OpenXR objects are torn down on the
 * thread that created them, satisfying both EGL and OpenXR threading rules.
 *
 * The render thread is also responsible for invoking [onExitDelivered] back to the Activity so
 * the Activity can leave foreground from the UI thread without polling runtime state.
 */
class DiagnosticXrRenderThread(
    private val activity: Activity,
    private val surface: Surface,
    private val runtime: DiagnosticXrRuntime,
    private val textureBytes: ByteArray,
    private val textureWidth: Int,
    private val textureHeight: Int,
    private val onSessionReady: () -> Unit,
    private val onExitDelivered: () -> Unit,
    private val onStartFailed: (DiagnosticXrNativeResult) -> Unit,
) : Thread("S0249.DiagXrRenderThread") {

    @Volatile private var exitSignalled: Boolean = false

    /** S0283 Phase 03: current stereo parallax shift in normalized units (0.0 .. 1.0). */
    @Volatile private var pendingParallaxShift: Float = 0.0f

    /**
     * S0283 §11.8 / §2.6: live OpenXR frame rate sampled in the native frame loop and exposed
     * through JNI. Reads an atomic value on each access, so the HUD can call it once per UI tick
     * without coordinating with the render thread.
     */
    val currentFps: Float get() = runtime.getCurrentFps()

    fun setParallaxShift(value: Float) {
        pendingParallaxShift = value.coerceIn(0.0f, 1.0f)
        runtime.setParallaxShift(pendingParallaxShift)
    }

    override fun run() {
        try {
            val initResult = runBlocking { runtime.initSession(activity) }
            if (initResult != DiagnosticXrNativeResult.Ok) {
                Timber.w("DiagnosticXrRenderThread: initSession failed -> $initResult")
                onStartFailed(initResult); return
            }
            val attachResult = runBlocking { runtime.attachSurface(surface) }
            if (attachResult != DiagnosticXrNativeResult.Ok) {
                Timber.w("DiagnosticXrRenderThread: attachSurface failed -> $attachResult")
                onStartFailed(attachResult); return
            }
            val startResult = runBlocking { runtime.startSession() }
            if (startResult != DiagnosticXrNativeResult.Ok) {
                Timber.w("DiagnosticXrRenderThread: startSession failed -> $startResult")
                onStartFailed(startResult); return
            }
            val uploadResult = runBlocking { runtime.uploadTexture(textureBytes, textureWidth, textureHeight) }
            if (uploadResult != DiagnosticXrNativeResult.Ok) {
                // Texture failure is non-fatal: the placeholder grey sphere still shows. Log loudly
                // because the user is staring at a featureless globe in this state.
                Timber.w("DiagnosticXrRenderThread: uploadTexture -> $uploadResult; proceeding with placeholder texture")
            }
            onSessionReady()
            runtime.runFrameLoop()
        } catch (t: Throwable) {
            Timber.e(t, "DiagnosticXrRenderThread: unhandled exception in render loop")
        } finally {
            runCatching { runtime.shutdown() }
            onExitDelivered()
        }
    }

    /** Idempotent; safe to call from any thread. */
    fun requestExit() {
        if (exitSignalled) return
        exitSignalled = true
        runtime.requestExit()
    }
}
