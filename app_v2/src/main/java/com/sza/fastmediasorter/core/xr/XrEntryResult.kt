package com.sza.fastmediasorter.core.xr

/**
 * Structured outcome of a request to enter an immersive VR session.
 *
 * Returned from [XrEntryGateway.enterDiagnosticImage] (S0249 Stage 1A) and intended to drive
 * UI feedback without leaking implementation-level detail.
 *
 * Caller mapping:
 * - [Started] - session is opening; UI usually only logs and waits.
 * - [UnavailableNoRuntime] - device has no XR runtime; UI surfaces nothing (the entry control
 *   is already hidden in this state).
 * - [UnavailableDisabledByUser] - master toggle is OFF; UI usually surfaces nothing for the
 *   same reason.
 * - [InitializationFailed] - runtime exists and toggle is ON, but the OpenXR pipeline could
 *   not start; UI surfaces a short toast.
 */
sealed class XrEntryResult {

    /** Session is opening or has opened. The caller can release UI focus. */
    data object Started : XrEntryResult()

    /** Device does not expose an XR runtime; the entry should not have been reachable. */
    data object UnavailableNoRuntime : XrEntryResult()

    /** XR runtime exists but the user has the master toggle OFF. */
    data object UnavailableDisabledByUser : XrEntryResult()

    /**
     * Runtime initialization failed mid-flight. Causes (loader missing, swapchain rejected,
     * permission denied at runtime) are intentionally collapsed into one user-visible case;
     * specifics are logged via Timber and surfaced through diagnostics, not UI strings.
     */
    data object InitializationFailed : XrEntryResult()
}
