package com.sza.fastmediasorter.data.network.lifecycle

import org.apache.commons.net.ftp.FTPClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle adapter under [NetworkConnectionGate]. S0067 Phase 04.
 *
 * **First iteration scope:** only [lastRecreateMs] (delegates to [FtpRecreateTracker]) is live.
 * [acquire] / [release] / [withRetry] throw [UnsupportedOperationException] - FTP consumers
 * continue calling `FtpClient` / `FtpExoPlayerPool` directly. [closeFor] is a no-op: ExoPlayer FTP
 * connections are created per-acquire and disconnected on release, so there is no idle pool to sweep
 * (the previous `cleanupIdleFtpConnections()` delegate was a permanent no-op - removed in S0904).
 */
@Singleton
class FtpConnectionGate @Inject constructor(
    private val tracker: FtpRecreateTracker,
    @Suppress("unused") private val diagnostics: ConnectionDiagnostics
) : NetworkConnectionGate<FTPClient> {

    override val protocol: NetworkProtocol = NetworkProtocol.FTP

    override suspend fun acquire(consumer: ConsumerType, resourceKey: String): FTPClient {
        throw UnsupportedOperationException(
            "S0067 Phase 04: FTP lease path uses FtpClient/FtpExoPlayerPool directly. " +
                "Gate-mediated acquire is a future extension."
        )
    }

    override fun release(connection: FTPClient, success: Boolean) {
        // Never invoked while acquire throws - kept empty for forward compatibility.
    }

    override suspend fun <R> withRetry(
        consumer: ConsumerType,
        resourceKey: String,
        op: suspend (FTPClient) -> R
    ): R {
        throw UnsupportedOperationException(
            "S0067 Phase 04: FTP withRetry not yet routed through the gate."
        )
    }

    override fun closeFor(consumer: ConsumerType) {
        // No-op: ExoPlayer FTP connections are not pooled (created per-acquire, disconnected on
        // release), so there is no idle pool to sweep on UI close (S0904).
    }

    override fun lastRecreateMs(resourceKey: String): Long? =
        tracker.lastRecreateMs(resourceKey)
}
