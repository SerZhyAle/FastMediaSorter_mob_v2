package com.sza.fastmediasorter.core.xr.runtime

import android.app.Activity
import android.view.Surface

/**
 * Facade over the diagnostic OpenXR runtime (S0249).
 *
 * Implementations are VR-flavor only ([NativeDiagnosticXrRuntime] talks to JNI / OpenXR).
 * Phone flavors never see this type; the phone-only code path stops at
 * `XrEntryGateway.enterDiagnosticImage()` returning [com.sza.fastmediasorter.core.xr.XrEntryResult.UnavailableNoRuntime].
 *
 * Lifecycle (Phase 02 step 02.6 - Activity-driven; every call except [requestExit] runs on the
 * dedicated render thread the Activity owns):
 * 1. [initSession] - JavaVM + Activity jobject -> XrInstance + XrSystem + EGL context.
 * 2. [attachSurface] - Activity's SurfaceView Surface -> EGLSurface bound.
 * 3. [startSession] - XrSession + per-eye swapchains + reference space + GL assets + actions.
 * 4. [uploadTexture] - raw RGBA bytes (decoded on Kotlin side) populate the sphere texture.
 * 5. [runFrameLoop] - blocking; returns when [requestExit] is called or runtime sends EXITING.
 * 6. [shutdown] - tear everything down.
 *
 * [requestExit] is the only method safe to call from any thread; it sets an atomic flag the
 * render thread observes between frames.
 */
interface DiagnosticXrRuntime {

    /** Returns true if the native library loaded successfully (arm64 device + AAR present). */
    val isNativeAvailable: Boolean

    /** Returns true if a session is currently active (instance + session handle alive). */
    fun isRunning(): Boolean

    suspend fun initSession(activity: Activity): DiagnosticXrNativeResult

    suspend fun attachSurface(surface: Surface): DiagnosticXrNativeResult

    suspend fun startSession(): DiagnosticXrNativeResult

    suspend fun uploadTexture(rgba: ByteArray, width: Int, height: Int): DiagnosticXrNativeResult

    /**
     * Blocking frame-loop entry point. Drives `xrWaitFrame` -> render -> `xrEndFrame` until
     * [requestExit] is called or the OpenXR runtime sends `SESSION_STATE_EXITING`. Caller MUST
     * be the render thread.
     */
    fun runFrameLoop(): DiagnosticXrNativeResult

    /** Async, thread-safe: signal the render thread to leave the frame loop. */
    fun requestExit()

    fun shutdown()

    /** Thread-safe: queue a new frame for upload on the render thread during the next frame loop cycle. */
    fun queueFrame(rgba: ByteArray, width: Int, height: Int)

    /** Returns the native GL-backed video surface for ExoPlayer output, or null before session start. */
    fun getVideoSurface(): Surface?

    /** Switch media rendering between uploaded RGBA/image texture and the native external video texture. */
    fun setVideoSurfaceEnabled(enabled: Boolean)

    /** Thread-safe: set projection type (0=360, 1=180, 2=Flat) and stereo layout (0=Mono, 1=TB, 2=SBS) */
    fun setRenderConfig(projection: Int, layout: Int)

    /** Thread-safe: set stereo parallax shift for the diagnostic shader. */
    fun setParallaxShift(value: Float)

    /** Thread-safe: queue a filename/text HUD overlay frame to be rendered head-locked */
    fun queueHud(rgba: ByteArray, width: Int, height: Int)

    /** JNI Pathway: stream interaction data from C++ render loop up to JVM */
    fun onNativeRayInteraction(uvX: Float, uvY: Float, isHover: Boolean, isClick: Boolean)

    /** Apply haptic vibration to the specified controller (0 = left, 1 = right) */
    fun applyHaptic(hand: Int, durationSeconds: Float, frequency: Float, amplitude: Float)

    /**
     * Returns the current smoothed frame rate (Hz) measured by the native frame loop.
     * 0.0 before the second frame has been delivered. Thread-safe; reads an atomic native value.
     */
    fun getCurrentFps(): Float
}

/**
 * Result ordinals shared with `xr_session.cpp::NativeResult`. The two enums must be kept in
 * lock-step; any added value here needs a matching native ordinal and vice versa.
 */
enum class DiagnosticXrNativeResult(val nativeOrdinal: Int) {
    Ok(0),
    LoaderUnavailable(1),
    InstanceCreationFailed(2),
    SystemNotFound(3),
    SessionCreationFailed(4),
    SwapchainCreationFailed(5),
    FramePresentFailed(6),
    AlreadyRunning(7),
    NotRunning(8),
    UnexpectedRuntimeError(99);

    companion object {
        fun fromOrdinal(ordinal: Int): DiagnosticXrNativeResult =
            values().firstOrNull { it.nativeOrdinal == ordinal } ?: UnexpectedRuntimeError
    }
}
