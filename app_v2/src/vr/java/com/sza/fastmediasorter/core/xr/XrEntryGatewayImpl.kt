package com.sza.fastmediasorter.core.xr

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import com.sza.fastmediasorter.ui.xr.DiagnosticXrActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Real VR entry gateway for `vr` / `noLegal` flavors.
 *
 * S0249 Phase 02 step 02.6: the diagnostic-image entry now launches a dedicated
 * [DiagnosticXrActivity] via Intent. HorizonOS picks up the `com.oculus.intent.category.VR`
 * intent-filter declared in `src/vr/AndroidManifest.xml` and launches the Activity in headset
 * mode. The Activity owns the OpenXR session, frame loop, and input handling end-to-end;
 * this gateway only signals user intent and stays decoupled from native lifecycle.
 *
 * Runtime probe: if the native library is unavailable on the current device (non-arm64 ABI
 * where the OpenXR slice is absent), short-circuit to [XrEntryResult.UnavailableNoRuntime]
 * before starting the Activity — otherwise the user would see a blank Activity flash before
 * the on-device runtime check rejects the session.
 */
@Singleton
class XrEntryGatewayImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val runtime: DiagnosticXrRuntime,
) : XrEntryGateway {

    override suspend fun tryEnter(): Boolean {
        Timber.d("XrEntryGatewayImpl: legacy tryEnter() called - no-op until full VR entry lands")
        return false
    }

    override suspend fun enterDiagnosticImage(): XrEntryResult {
        Timber.d("S0249: XrEntryGatewayImpl.enterDiagnosticImage - dispatching to DiagnosticXrActivity")
        if (!runtime.isNativeAvailable) {
            Timber.i("XrEntryGatewayImpl: native runtime unavailable on this ABI - returning UnavailableNoRuntime")
            return XrEntryResult.UnavailableNoRuntime
        }
        if (runtime.isRunning()) {
            Timber.w("XrEntryGatewayImpl: diagnostic session already active - InitializationFailed")
            return XrEntryResult.InitializationFailed
        }
        return try {
            val intent = Intent(appContext, DiagnosticXrActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            Timber.d("XrEntryGatewayImpl: DiagnosticXrActivity launched")
            XrEntryResult.Started
        } catch (t: Throwable) {
            Timber.e(t, "XrEntryGatewayImpl: startActivity(DiagnosticXrActivity) threw")
            XrEntryResult.InitializationFailed
        }
    }
}
