package com.sza.fastmediasorter.data.network.lifecycle

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle adapter for cloud providers (Google Drive, OneDrive, Dropbox). S0067 Phase 05.
 *
 * Per strategic ADR-2: gate manages **token expiry only** — does **not** close OkHttp pool
 * on `onStop`. [closeFor] is intentionally a no-op for sockets; OkHttp's connection pool is
 * self-managed and forced closure would interrupt long-running uploads/downloads.
 *
 * Token-refresh integration with [com.sza.fastmediasorter.data.cloud.UnifiedCloudAuthManager]
 * is a future extension once that manager exposes a uniform `tokenFor(provider) / forceRefresh`
 * API. For now the gate exposes only [lastRecreateMs] tracking and lifecycle-uniform iteration.
 *
 * Available only on flavors with `BuildConfig.SUPPORT_CLOUD == true`.
 */
@Singleton
class CloudConnectionGate @Inject constructor(
    private val tracker: CloudRecreateTracker,
    @Suppress("unused") private val diagnostics: ConnectionDiagnostics
) : NetworkConnectionGate<CloudTokenHandle> {

    override val protocol: NetworkProtocol = NetworkProtocol.CLOUD

    override suspend fun acquire(consumer: ConsumerType, resourceKey: String): CloudTokenHandle {
        throw UnsupportedOperationException(
            "S0067 Phase 05: Cloud token lease via gate is a future extension. " +
                "Cloud REST clients continue managing their own access tokens."
        )
    }

    override fun release(connection: CloudTokenHandle, success: Boolean) {
        // Never invoked while acquire throws — kept empty for forward compatibility.
    }

    override suspend fun <R> withRetry(
        consumer: ConsumerType,
        resourceKey: String,
        op: suspend (CloudTokenHandle) -> R
    ): R {
        throw UnsupportedOperationException(
            "S0067 Phase 05: Cloud withRetry not yet routed through the gate."
        )
    }

    /** No-op for sockets per ADR-2. OkHttp pool self-manages; cloud lifecycle is token-bound. */
    override fun closeFor(consumer: ConsumerType) = Unit

    override fun lastRecreateMs(resourceKey: String): Long? =
        tracker.lastRecreateMs(resourceKey)
}

/** Token handle data carrier — used as the gate's connection type. S0067. */
data class CloudTokenHandle(
    val provider: String,
    val resourceKey: String,
    val accessToken: String,
    val expiresAtMs: Long
) {
    val isExpiringSoon: Boolean get() = System.currentTimeMillis() >= expiresAtMs - 60_000L
}
