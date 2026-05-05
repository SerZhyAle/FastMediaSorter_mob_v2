package com.sza.fastmediasorter.data.network.lifecycle

import com.sza.fastmediasorter.data.network.PooledConnection
import com.sza.fastmediasorter.data.network.SmbConnectionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle-only adapter wrapping [SmbConnectionManager] under the
 * [NetworkConnectionGate] interface. S0067 Phase 02.
 *
 * **First iteration scope:** [closeFor] (delegates to existing `closeUiConnections()`)
 * and [lastRecreateMs] (delegates to [SmbRecreateTracker]). [acquire] / [release] /
 * [withRetry] throw [UnsupportedOperationException] — SMB consumers continue calling
 * `SmbConnectionManager.getConnectionForExoPlayer(..)` directly, per the strategic
 * non-goal "SMB consumers continue to use SmbConnectionManager directly".
 *
 * Gate-mediated per-share SMB lease is a deliberate future extension (separate ticket)
 * because the legacy manager's per-share `PooledConnection` lifecycle is well-tuned
 * by S0061 and not in S0067 scope.
 */
@Singleton
class SmbConnectionGate @Inject constructor(
    private val manager: SmbConnectionManager,
    private val tracker: SmbRecreateTracker,
    @Suppress("unused") private val diagnostics: ConnectionDiagnostics
) : NetworkConnectionGate<PooledConnection> {

    override val protocol: NetworkProtocol = NetworkProtocol.SMB

    override suspend fun acquire(consumer: ConsumerType, resourceKey: String): PooledConnection {
        throw UnsupportedOperationException(
            "S0067 Phase 02: SMB lease path uses SmbConnectionManager.getConnectionForExoPlayer directly. " +
                "Gate-mediated acquire is a future extension."
        )
    }

    override fun release(connection: PooledConnection, success: Boolean) {
        // Never invoked while acquire throws — kept empty for forward compatibility.
    }

    override suspend fun <R> withRetry(
        consumer: ConsumerType,
        resourceKey: String,
        op: suspend (PooledConnection) -> R
    ): R {
        throw UnsupportedOperationException(
            "S0067 Phase 02: SMB withRetry not yet routed through the gate — see SmbConnectionGate.acquire."
        )
    }

    override fun closeFor(consumer: ConsumerType) {
        if (consumer.isUi) {
            manager.closeUiConnections()
        }
        // BACKGROUND_WORKER: no-op — worker sessions survive backgrounding (S0061 contract).
    }

    override fun lastRecreateMs(resourceKey: String): Long? =
        tracker.lastRecreateMs(resourceKey)
}
