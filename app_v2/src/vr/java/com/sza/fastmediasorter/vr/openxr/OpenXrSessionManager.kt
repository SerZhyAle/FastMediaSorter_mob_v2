package com.sza.fastmediasorter.vr.openxr

import android.app.Activity
import android.view.Surface
import timber.log.Timber

/**
 * Manages the OpenXR session lifecycle: instance → system → session → swapchain.
 *
 * The swapchain produces an android.view.Surface that is passed to
 * VrPlaybackEngine.prepare() so ExoPlayer renders into the XR compositor.
 *
 * Event loop (xrPollEvent) runs on Dispatchers.IO via a coroutine.
 *
 * NOTE: This is a scaffold — actual OpenXR JNI calls require the native
 * OpenXR loader and a Quest device for testing. The structure is ready
 * for native implementation.
 */
class OpenXrSessionManager {

    private var isSessionActive = false

    /**
     * Initialise OpenXR instance, system, and session.
     * Creates a swapchain and returns the Surface for video rendering.
     *
     * @param activity The host VrPlayerActivity (needed for XR context).
     * @return The Surface backed by the OpenXR swapchain.
     * @throws OpenXrInitException if XR runtime is not available.
     */
    suspend fun createSessionAndGetSurface(activity: Activity): Surface {
        Timber.d("OpenXrSessionManager: creating XR session..")

        // TODO: JNI calls to:
        //   1. xrCreateInstance() with Meta/Google XR extensions
        //   2. xrGetSystem() for HMD form factor
        //   3. xrCreateSession() with Android graphics binding
        //   4. xrCreateSwapchain() → extract Surface from swapchain images
        // For now, throw to signal the scaffold is not yet wired to native
        throw OpenXrInitException("OpenXR native session not yet implemented — scaffold only")
    }

    /**
     * Start the render/event loop. Polls xrPollEvent and submits frames.
     * Must be called after createSessionAndGetSurface succeeds.
     */
    suspend fun startEventLoop() {
        Timber.d("OpenXrSessionManager: starting event loop..")
        isSessionActive = true

        // TODO: xrPollEvent loop + xrBeginFrame / xrEndFrame cycle
        // Will run on Dispatchers.IO, yielding between frames
    }

    /**
     * Cleanly shut down the OpenXR session.
     * Must be called from VrPlayerActivity.onDestroy().
     */
    fun release() {
        Timber.d("OpenXrSessionManager: releasing XR session")
        isSessionActive = false

        // TODO: xrDestroySwapchain → xrDestroySession → xrDestroyInstance
    }
}

/**
 * Thrown when the OpenXR runtime is unavailable (e.g. running on a phone
 * or the Meta XR runtime is not installed on the headset).
 */
class OpenXrInitException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
