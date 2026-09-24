package com.sza.fastmediasorter.core.xr

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import com.sza.fastmediasorter.ui.xr.DiagnosticXrActivity
import com.sza.fastmediasorter.ui.xr.ImmersiveBrowseActivity
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
 *
 * S3334: the runtime arrives as [Lazy] because its constructor calls `System.loadLibrary`, and this
 * gateway is reachable from the launcher's start-up graph (main-screen VR launch managers ->
 * `StartVrPlaybackUseCase` -> this class). Injecting it directly put an `.so` load - eleven
 * StrictMode DiskReadViolations - on the main thread of every cold start, including on phones that
 * never enter VR. Resolving it on the first immersive-intent request keeps the load off that path.
 */
@Singleton
class XrEntryGatewayImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val runtimeProvider: Lazy<DiagnosticXrRuntime>,
    private val payloadHolder: VrLaunchPayloadHolder,
) : XrEntryGateway {

    override fun createImmersiveIntent(input: VrLaunchInput): Intent? {
        if (!runtimeProvider.get().isNativeAvailable) {
            Timber.i("XrEntryGatewayImpl: createImmersiveIntent -> native runtime unavailable")
            return null
        }
        if (input.launchMode == VrLaunchMode.FILE_URI && input.fileUriString.isNullOrBlank()) {
            Timber.w("XrEntryGatewayImpl: createImmersiveIntent -> missing fileUriString")
            return null
        }
        if (input.launchMode == VrLaunchMode.RESOURCE_BROWSE && input.resourceId == null) {
            Timber.w("XrEntryGatewayImpl: createImmersiveIntent -> missing resourceId")
            return null
        }
        val target = if (input.launchMode == VrLaunchMode.RESOURCE_BROWSE) {
            ImmersiveBrowseActivity::class.java
        } else {
            DiagnosticXrActivity::class.java
        }
        return Intent(appContext, target).apply {
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
