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
 * the Activity can `finish()` itself from the UI thread; this avoids the Activity having to
 * poll the runtime state.
 */
class DiagnosticXrRenderThread(
    private val activity: Activity,
    private val surface: Surface,
    private val runtime: DiagnosticXrRuntime,
    private val textureBytes: ByteArray,
    private val textureWidth: Int,
    private val textureHeight: Int,
    private val onExitDelivered: () -> Unit,
    private val onStartFailed: (DiagnosticXrNativeResult) -> Unit,
) : Thread("S0249.DiagXrRenderThread") {

    @Volatile private var exitSignalled: Boolean = false

    override fun run() {
        Timber.d("DiagnosticXrRenderThread: starting")
        try {
            val initResult = runBlocking { runtime.initSession(activity) }
            if (initResult != DiagnosticXrNativeResult.Ok) {
                Timber.w("initSession -> $initResult"); onStartFailed(initResult); return
            }
            val attachResult = runBlocking { runtime.attachSurface(surface) }
            if (attachResult != DiagnosticXrNativeResult.Ok) {
                Timber.w("attachSurface -> $attachResult"); onStartFailed(attachResult); return
            }
            val startResult = runBlocking { runtime.startSession() }
            if (startResult != DiagnosticXrNativeResult.Ok) {
                Timber.w("startSession -> $startResult"); onStartFailed(startResult); return
            }
            val uploadResult = runBlocking { runtime.uploadTexture(textureBytes, textureWidth, textureHeight) }
            if (uploadResult != DiagnosticXrNativeResult.Ok) {
                // Texture failure is non-fatal: the placeholder grey sphere still shows. Log loudly
                // because the user is staring at a featureless globe in this state.
                Timber.w("uploadTexture -> $uploadResult; proceeding with placeholder texture")
            }
            val loopResult = runtime.runFrameLoop()
            Timber.d("frame loop returned $loopResult")
        } catch (t: Throwable) {
            Timber.e(t, "DiagnosticXrRenderThread: unhandled exception")
        } finally {
            runCatching { runtime.shutdown() }
            onExitDelivered()
            Timber.d("DiagnosticXrRenderThread: terminated")
        }
    }

    /** Idempotent; safe to call from any thread. */
    fun requestExit() {
        if (exitSignalled) return
        exitSignalled = true
        runtime.requestExit()
    }
}
