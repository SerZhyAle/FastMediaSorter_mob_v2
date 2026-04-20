package com.sza.fastmediasorter.vr.openxr

import android.app.Activity

/**
 * Thin JNI binding for the native OpenXR bridge (`libopenxr_native.so`).
 *
 * Loader contract:
 *  - `openxr_loader` (Khronos AAR) is loaded via [ensureLoaderLoaded] first.
 *  - `openxr_native` (this module's native lib) depends on `openxr_loader` and is loaded second.
 *
 * All methods are called through [OpenXrSessionManager]; no other code should touch this object.
 */
internal object OpenXrNative {

    @Volatile
    private var loaded = false

    /**
     * Load the OpenXR loader + native bridge libraries.
     * Safe to call multiple times; subsequent calls are no-ops.
     * @return true on success, false if any library is missing (non-headset device).
     */
    fun ensureLoaderLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("openxr_loader")
            System.loadLibrary("openxr_native")
            loaded = true
            true
        } catch (t: UnsatisfiedLinkError) {
            loaded = false
            false
        }
    }

    /**
     * Create the XR instance, system, session and swapchains.
     * Must be called from a thread with a valid EGL ES 3 context.
     *
     * @param activity Hosting Activity — the runtime needs Application Context + VM.
     * @param callback Render callback; its `onRenderEye(eye, fbo, width, height)`
     *                 method is invoked per eye per frame while the session is visible.
     * @return true if initialization completed; false means the XR runtime refused
     *         (phone, missing Meta XR runtime, or driver mismatch) — caller should
     *         fall back to the non-XR screen.
     */
    @JvmStatic external fun nativeInitialize(activity: Activity, callback: XrRenderCallback): Boolean

    /** Poll events + render one frame. Called in a tight loop from the render thread. */
    @JvmStatic external fun nativeRunFrame()

    /** Update the active OpenXR layer config used by subsequent xrEndFrame submissions. */
    @JvmStatic
    external fun nativeConfigureLayer(
        layerType: Int,
        widthMeters: Float,
        heightMeters: Float,
        distanceMeters: Float,
        radiusMeters: Float,
        centralHorizontalAngleRadians: Float,
        upperVerticalAngleRadians: Float,
        lowerVerticalAngleRadians: Float,
    )

    /** @return false when the session state requested exit (STOPPING/EXITING). */
    @JvmStatic external fun nativeShouldContinue(): Boolean

    /** Request a one-shot stereo snapshot from the active XR render loop. */
    @JvmStatic external fun nativeRequestStereoSnapshot(): Boolean

    /** Returns true once the requested stereo snapshot pixels are ready for consumption. */
    @JvmStatic external fun nativeIsStereoSnapshotReady(): Boolean

    /** Consume one eye of the pending stereo snapshot as ARGB pixels. */
    @JvmStatic external fun nativeConsumeStereoSnapshotPixels(eye: Int): IntArray?

    /** Release any native buffers held for the pending/ready stereo snapshot. */
    @JvmStatic external fun nativeReleaseStereoSnapshot()

    /** Cooperatively signal the native loop to exit (e.g. on Activity finish). */
    @JvmStatic external fun nativeRequestExit()

    /** Destroy swapchains, session, instance in reverse order. */
    @JvmStatic external fun nativeRelease()

    @JvmStatic external fun nativeGetEyeWidth(eye: Int): Int
    @JvmStatic external fun nativeGetEyeHeight(eye: Int): Int
}
