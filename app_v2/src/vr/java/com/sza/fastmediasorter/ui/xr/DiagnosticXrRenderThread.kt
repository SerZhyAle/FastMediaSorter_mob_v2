package com.sza.fastmediasorter.ui.xr

import android.app.Activity
import android.view.Surface
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrNativeResult
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
    /**
     * Completed by [DiagnosticXrActivity.onWindowFocusChanged] when the Activity gains window
     * focus. On re-entry sessions the XrInstance is reused and xrCreateSession is called
     * immediately, before HzOS registers the volumetric window (VolumetricWindowInfo is null).
     * Awaiting this deferred before [DiagnosticXrRuntime.startSession] ensures the window is
     * registered so the XR runtime sees "Activity is already in the ready state" instead of
     * deferring and never firing nativeOnActivityReady. Cold-start: deferred is already
     * completed by the time the render thread reaches this point (5-second XrInstance init
     * provides the natural window registration window), so await returns instantly.
     */
    private val windowFocused: Deferred<Unit>,
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
            // Wait for Activity window focus before starting the XR session. On re-entry the
            // XrInstance is reused so xrCreateSession fires ~0 ms after the Activity starts,
            // before HzOS has registered the volumetric window; the runtime then defers
            // nativeOnActivityReady indefinitely (session stays in IDLE, user sees black
            // screen). Window focus is the first reliable observable signal that HzOS is done
            // with window registration. 5-second timeout: proceed anyway if focus is late
            // (same outcome as before the fix; harmless for fresh cold starts where the
            // deferred is already complete from the 5-second XrInstance init window).
            val focusWaitMs = System.currentTimeMillis()
            val focused = runBlocking { withTimeoutOrNull(5_000L) { windowFocused.await() } }
            if (focused == null) {
                Timber.w("DiagnosticXrRenderThread: timed out waiting for window focus (${System.currentTimeMillis() - focusWaitMs} ms); proceeding anyway")
            } else {
                Timber.d("DiagnosticXrRenderThread: window focus received in ${System.currentTimeMillis() - focusWaitMs} ms")
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
