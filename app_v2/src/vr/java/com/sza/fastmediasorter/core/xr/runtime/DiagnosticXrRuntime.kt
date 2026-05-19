package com.sza.fastmediasorter.core.xr.runtime

import android.content.Context

/**
 * Facade over the diagnostic OpenXR runtime (S0249 Stage 1A).
 *
 * Implementations are VR-flavor only ([NativeDiagnosticXrRuntime] talks to JNI / OpenXR).
 * Phone flavors never see this type; the phone-only code path stops at
 * `XrEntryGateway.enterDiagnosticImage()` returning [com.sza.fastmediasorter.core.xr.XrEntryResult.UnavailableNoRuntime].
 *
 * The facade is intentionally narrow:
 * - [probeExtensions] runs once before [startSession] to record which optional features
 *   the device's OpenXR runtime exposes (notably `XR_KHR_composition_layer_equirect2`).
 * - [startSession] performs cold-start of OpenXR instance + session + swapchain. Returns a
 *   structured outcome; never throws.
 * - [presentStaticImage] uploads the bundled stereo top-bottom equirect bytes for one
 *   present cycle. Phase 03 wires the asset stream into this call.
 * - [requestExit] tears down the session in response to any input event from Phase 05.
 * - [isRunning] is read by `XrEntryGatewayImpl` to gate concurrent entry attempts.
 */
interface DiagnosticXrRuntime {

    suspend fun probeExtensions(): DiagnosticXrNativeResult

    fun hasEquirect2Layer(): Boolean

    suspend fun startSession(context: Context): DiagnosticXrNativeResult

    suspend fun presentStaticImage(imageBytes: ByteArray, width: Int, height: Int): DiagnosticXrNativeResult

    /**
     * Convenience wrapper that loads the bundled diagnostic asset (S0249 Phase 03) and forwards
     * it to [presentStaticImage]. The native side currently leaves real swapchain upload to
     * Phase 03 follow-on work — calling this method validates the JNI wiring and asset-provider
     * binding end-to-end without forcing the caller to handle asset I/O.
     */
    suspend fun presentBundledDiagnosticImage(): DiagnosticXrNativeResult

    fun requestExit()

    fun isRunning(): Boolean
}

/**
 * Result ordinals shared with `diagnostic_xr_runtime.cpp::NativeResult`. The two enums must
 * be kept in lock-step; any added value here needs a matching native ordinal and vice versa.
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
