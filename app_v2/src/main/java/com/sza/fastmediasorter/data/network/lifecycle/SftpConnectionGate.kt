package com.sza.fastmediasorter.data.network.lifecycle

import com.jcraft.jsch.ChannelSftp
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle adapter wrapping [SftpConnectionPool] under [NetworkConnectionGate]. S0067 Phase 03.
 *
 * **First iteration scope:** [closeFor] (retires pooled sessions with borrow-aware disconnect)
 * and [lastRecreateMs] (delegates to [SftpRecreateTracker]).
 * [acquire] / [release] / [withRetry] throw [UnsupportedOperationException] - SFTP consumers
 * continue calling `SftpClient` / `SftpConnectionPool` directly, per the strategic non-goal
 * "non-SMB consumers continue using their existing client until per-pool refactor lands".
 *
 * `SftpConnectionPool` has no per-consumer (UI vs WORKER) tagging. [closeFor] retires all
 * pooled sessions, but active FILE_OPS and PLAYBACK borrowers retain their session until their
 * final release. This prevents backgrounding from interrupting a non-replayable transfer.
 */
@Singleton
class SftpConnectionGate @Inject constructor(
    private val client: SftpClient,
    private val tracker: SftpRecreateTracker,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @Suppress("unused") private val diagnostics: ConnectionDiagnostics
) : NetworkConnectionGate<ChannelSftp> {

    override val protocol: NetworkProtocol = NetworkProtocol.SFTP

    override suspend fun acquire(consumer: ConsumerType, resourceKey: String): ChannelSftp {
        throw UnsupportedOperationException(
            "S0067 Phase 03: SFTP lease path uses SftpClient/SftpConnectionPool directly. " +
                "Gate-mediated acquire is a future extension."
        )
    }

    override fun release(connection: ChannelSftp, success: Boolean) {
        // Never invoked while acquire throws - kept empty for forward compatibility.
    }

    override suspend fun <R> withRetry(
        consumer: ConsumerType,
        resourceKey: String,
        op: suspend (ChannelSftp) -> R
    ): R {
        throw UnsupportedOperationException(
            "S0067 Phase 03: SFTP withRetry not yet routed through the gate."
        )
    }

    override fun closeFor(consumer: ConsumerType) {
        if (!consumer.isUi) return
        // closeFor is invoked from ProcessLifecycleOwner.ON_STOP (Main); a synchronous SSH teardown
        // here stalls the UI on every backgrounding. Fire-and-forget on the IO-backed application
        // scope, mirroring SftpConnectionPool.disconnectAllOnNetworkChange (S0727).
        applicationScope.launch {
            try {
                client.disconnectUiPool()
                Timber.i("[scope=lifecycle protocol=SFTP action=close_ui] SFTP retired; active borrows retained")
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.w(e, "SftpConnectionGate.closeFor: UI session retirement failed")
            }
        }
    }

    override fun lastRecreateMs(resourceKey: String): Long? =
        tracker.lastRecreateMs(resourceKey)
}
