package com.sza.fastmediasorter.core.xr

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op entry gateway used by the phone-only flavors that mount `src/vrStub/java`.
 *
 * Every entry path returns "unavailable" because the device has no XR runtime. Paired with
 * the real `XrEntryGatewayImpl` in `src/vr/java/`.
 */
@Singleton
class NoOpXrEntryGateway @Inject constructor() : XrEntryGateway {

    override fun createImmersiveIntent(input: VrLaunchInput): Intent? = null

    override suspend fun tryEnter(): Boolean = false

    override suspend fun enterDiagnosticImage(): XrEntryResult = XrEntryResult.UnavailableNoRuntime
}
