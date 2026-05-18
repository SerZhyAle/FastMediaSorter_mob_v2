package com.sza.fastmediasorter.core.xr

import android.content.Context
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrNativeResult
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Real VR entry gateway for `vr` / `noLegal` flavors.
 *
 * S0249 Phase 02: delegates to [DiagnosticXrRuntime] for the diagnostic-image entry. The
 * runtime is currently scaffold-only — it probes extensions and creates an OpenXR instance
 * but does not present the asset yet (lands in Phase 03). Any runtime status that is not
 * [DiagnosticXrNativeResult.Ok] is collapsed to [XrEntryResult.InitializationFailed]; the
 * specific native ordinal is logged via Timber so on-device diagnostics can trace it back.
 */
@Singleton
class XrEntryGatewayImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val runtime: DiagnosticXrRuntime
) : XrEntryGateway {

    override suspend fun tryEnter(): Boolean {
        Timber.d("XrEntryGatewayImpl: legacy tryEnter() called - no-op until full VR entry lands")
        return false
    }

    override suspend fun enterDiagnosticImage(): XrEntryResult {
        if (runtime.isRunning()) {
            Timber.w("XrEntryGatewayImpl: enterDiagnosticImage rejected - runtime already running")
            return XrEntryResult.InitializationFailed
        }
        val probe = runtime.probeExtensions()
        if (probe != DiagnosticXrNativeResult.Ok) {
            Timber.w("XrEntryGatewayImpl: probeExtensions returned $probe")
            return XrEntryResult.InitializationFailed
        }
        val startResult = runtime.startSession(appContext)
        return when (startResult) {
            DiagnosticXrNativeResult.Ok -> XrEntryResult.Started
            DiagnosticXrNativeResult.LoaderUnavailable -> XrEntryResult.UnavailableNoRuntime
            DiagnosticXrNativeResult.SystemNotFound -> XrEntryResult.UnavailableNoRuntime
            else -> {
                Timber.w("XrEntryGatewayImpl: startSession returned $startResult")
                XrEntryResult.InitializationFailed
            }
        }
    }
}
