package com.sza.fastmediasorter.data.network.lifecycle

import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import org.apache.commons.net.ftp.FTPClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle adapter wrapping [FtpExoPlayerPool] under [NetworkConnectionGate]. S0067 Phase 04.
 *
 * **First iteration scope:** [closeFor] (delegates to `cleanupIdleFtpConnections()`)
 * and [lastRecreateMs] (delegates to [FtpRecreateTracker]).
 * [acquire] / [release] / [withRetry] throw [UnsupportedOperationException] — FTP consumers
 * continue calling `FtpClient` / `FtpExoPlayerPool` directly.
 *
 * **Known limitation:** [FtpExoPlayerPool] has no per-consumer (UI vs WORKER) tagging today.
 * `cleanupIdleFtpConnections()` only closes idle connections — active worker FTP transfers
 * are not interrupted by [closeFor], which is the desired (worker-safe) behaviour.
 */
@Singleton
class FtpConnectionGate @Inject constructor(
    private val client: FtpClient,
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
        // Never invoked while acquire throws — kept empty for forward compatibility.
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
        if (!consumer.isUi) return
        try {
            client.cleanupIdleFtpConnections()
            Timber.i("[scope=lifecycle protocol=FTP action=close_ui] idle FTP connections cleaned up")
        } catch (e: Exception) {
            Timber.w(e, "FtpConnectionGate.closeFor: cleanupIdleFtpConnections failed")
        }
    }

    override fun lastRecreateMs(resourceKey: String): Long? =
        tracker.lastRecreateMs(resourceKey)
}
