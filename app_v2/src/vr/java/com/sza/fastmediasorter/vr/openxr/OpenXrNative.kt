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

    /**
     * Drain buffered native log messages (each `"X|message"` where `X` is the priority
     * char: V/D/I/W/E). The buffer is filled by every LOG* call inside the C++ bridge
     * in parallel with `__android_log_print`. Kotlin forwards each entry into Timber
     * so failed XR init produces a complete trace in the app's file log — not just
     * the bare `nativeInitialize returned false` line.
     */
    @JvmStatic external fun nativeDrainLog(): Array<String>

    /** Destroy swapchains, session, instance in reverse order. */
    @JvmStatic external fun nativeRelease()

    @JvmStatic external fun nativeGetEyeWidth(eye: Int): Int
    @JvmStatic external fun nativeGetEyeHeight(eye: Int): Int

    /**
     * Register (or clear with `null`) the input callback that receives per-frame
     * controller edge events and hand-tracking pointer/pinch events. The callback
     * runs on the xr-render-thread; implementations MUST hop to the main looper
     * before touching UI or ViewModel state.
     *
     * The native bridge caches method IDs for `onInputEvent(IIFI)V` and, optionally,
     * `onPointerMove(IFF)V`. A missing `onPointerMove` is non-fatal — the pointer
     * stream is silently dropped when the method is absent.
     */
    @JvmStatic external fun nativeSetInputCallback(callback: XrInputCallback?)

    /**
     * Trigger haptic feedback on [hand] (0 = left, 1 = right) for [durationNs]
     * nanoseconds with the given [amplitude] (0.0–1.0).
     * Returns true if the runtime accepted the request.
     */
    @JvmStatic external fun nativeTriggerHaptic(hand: Int, durationNs: Long, amplitude: Float): Boolean

    // ═══════════════════════════════════════════════════════════════════════
    // HUD composition layer (spec_vr-immersive-hud-gl).
    // Phase 01: stubs only. Phase 02 wires the real XrSwapchain + xrEndFrame.
    // Phase 03 turns nativeUploadHudBitmap into a glTexSubImage2D upload.
    // ═══════════════════════════════════════════════════════════════════════

    /** Reserve dimensions for the HUD quad swapchain. Returns true on success. */
    @JvmStatic external fun nativeCreateHudSwapchain(width: Int, height: Int): Boolean

    /** Release HUD swapchain resources and force the layer hidden. */
    @JvmStatic external fun nativeDestroyHudSwapchain()

    /** Toggle inclusion of the HUD quad layer in xrEndFrame composition. */
    @JvmStatic external fun nativeSetHudLayerVisible(visible: Boolean)

    /** Upload a premultiplied ARGB_8888 [bitmap] into the next HUD swapchain image. */
    @JvmStatic external fun nativeUploadHudBitmap(bitmap: android.graphics.Bitmap): Boolean
}
