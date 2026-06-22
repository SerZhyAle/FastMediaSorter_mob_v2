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
 * before starting the Activity - otherwise the user would see a blank Activity flash before
 * the on-device runtime check rejects the session.
 */
@Singleton
class XrEntryGatewayImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val runtime: DiagnosticXrRuntime,
    private val payloadHolder: VrLaunchPayloadHolder,
) : XrEntryGateway {

    override fun createImmersiveIntent(input: VrLaunchInput): Intent? {
        if (!runtime.isNativeAvailable) {
            Timber.i("XrEntryGatewayImpl: createImmersiveIntent -> native runtime unavailable")
            return null
        }
        if (input.launchMode == VrLaunchMode.FILE_URI && input.fileUriString.isNullOrBlank()) {
            Timber.w("XrEntryGatewayImpl: createImmersiveIntent -> missing fileUriString")
            return null
        }
        return Intent(appContext, DiagnosticXrActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            if (input.deliveryMode == VrLaunchDeliveryMode.LEGACY_PANEL_RETURN) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            putExtra(VrLaunchInput.EXTRA_LAUNCH_INPUT_TOKEN, payloadHolder.put(input))
        }
    }

    override suspend fun tryEnter(): Boolean {
        Timber.d("XrEntryGatewayImpl: legacy tryEnter() called - no-op until full VR entry lands")
        return false
    }

    override suspend fun enterDiagnosticImage(): XrEntryResult {
        val intent = createImmersiveIntent(
            VrLaunchInput(
                launchMode = VrLaunchMode.DIAGNOSTIC_PLAYLIST,
                mediaType = VrMediaType.IMAGE,
                deliveryMode = VrLaunchDeliveryMode.LEGACY_PANEL_RETURN,
            )
        ) ?: return XrEntryResult.UnavailableNoRuntime
        return try {
            appContext.startActivity(intent)
            Timber.d("XrEntryGatewayImpl: DiagnosticXrActivity launched")
            XrEntryResult.Started
        } catch (t: Throwable) {
            Timber.e(t, "XrEntryGatewayImpl: startActivity(DiagnosticXrActivity) threw")
            XrEntryResult.InitializationFailed
        }
    }
}
