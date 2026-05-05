package com.sza.fastmediasorter.data.network.lifecycle

import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.jcraft.jsch.ChannelSftp
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle adapter wrapping [SftpConnectionPool] under [NetworkConnectionGate]. S0067 Phase 03.
 *
 * **First iteration scope:** [closeFor] (delegates to existing `disconnectAll()`)
 * and [lastRecreateMs] (delegates to [SftpRecreateTracker]).
 * [acquire] / [release] / [withRetry] throw [UnsupportedOperationException] — SFTP consumers
 * continue calling `SftpClient` / `SftpConnectionPool` directly, per the strategic non-goal
 * "non-SMB consumers continue using their existing client until per-pool refactor lands".
 *
 * **Known limitation:** `SftpConnectionPool` has no per-consumer (UI vs WORKER) tagging today,
 * so [closeFor] closes **all** sessions including any active `BACKGROUND_WORKER` ones. A future
 * follow-up ticket should add tagging to `SftpConnectionPool` so worker sessions survive
 * `onStop`. Current behaviour matches pre-S0067 lifecycle (apps already disconnect SFTP on
 * background via separate paths).
 */
@Singleton
class SftpConnectionGate @Inject constructor(
    private val client: SftpClient,
    private val tracker: SftpRecreateTracker,
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
        // Never invoked while acquire throws — kept empty for forward compatibility.
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
        try {
            runBlocking { client.disconnectAllPool() }
            Timber.i("[scope=lifecycle protocol=SFTP action=close_ui] all SFTP sessions disconnected")
        } catch (e: Exception) {
            Timber.w(e, "SftpConnectionGate.closeFor: disconnectAll failed")
        }
    }

    override fun lastRecreateMs(resourceKey: String): Long? =
        tracker.lastRecreateMs(resourceKey)
}
