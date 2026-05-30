package com.sza.fastmediasorter.core.xr

import android.content.Intent

/**
 * Facade for "enter VR session" requests.
 *
 * Stage 0 (S0245) shipped only the legacy [tryEnter] surface as a Boolean stub. Stage 1A
 * (S0249) extends the contract with the diagnostic-image entry, which returns a structured
 * [XrEntryResult] so the UI can react without inspecting implementation-level errors.
 *
 * Implementations:
 * - `src/vrStub/java/.../core/xr/NoOpXrEntryGateway` - phone-only flavors, returns
 *   "unavailable".
 * - `src/vr/java/.../core/xr/XrEntryGatewayImpl` - VR / noLegal flavors; wires the real
 *   OpenXR runtime starting in Phase 02 of S0249.
 */
interface XrEntryGateway {

    /**
     * Shared transport seam for UI callers that launch immersive playback through an
     * ActivityResultContract. Returning null means the launch is unavailable before dispatch.
     */
    fun createImmersiveIntent(input: VrLaunchInput): Intent?

    /**
     * Stage 0 legacy entry. Kept for binary-compatibility while no caller exists; will be
     * removed once a higher-fidelity entry replaces every Stage 0 call site.
     *
     * @return `true` once the VR session is active, `false` if VR is unavailable.
     */
    suspend fun tryEnter(): Boolean

    /**
     * Stage 1A (S0249) entry: open an OpenXR session that renders a single bundled,
     * stereoscopic top-bottom equirectangular diagnostic image. The session closes on any
     * controller input, keyboard key, or mouse click.
     *
     * @param launchTaskId optional flat-task id to restore when the immersive Activity exits.
     *
     * @return [XrEntryResult] describing what happened. Never throws.
     */
    suspend fun enterDiagnosticImage(): XrEntryResult
}
