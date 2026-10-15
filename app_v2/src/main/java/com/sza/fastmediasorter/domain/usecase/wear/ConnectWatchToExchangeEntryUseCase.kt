package com.sza.fastmediasorter.domain.usecase.wear

import com.google.gson.Gson
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeRefusedException
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.ExchangeE2eRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangePairingCode
import com.sza.fastmediasorter.domain.model.WatchExchangeHandoverOutcome
import com.sza.fastmediasorter.domain.model.WearExchangeHandoverAck
import com.sza.fastmediasorter.domain.model.WearExchangeHandoverCodec
import com.sza.fastmediasorter.domain.model.WearExchangeHandoverPayload
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import com.sza.fastmediasorter.service.WearSyncEvents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * S4158: the "Connect watch" action of one exchange account entry - asks the entry's server for a one-time
 * pairing code and hands it with the address, the trust decision and the login to the watch, which enrolls
 * into the same account with its own `deviceId` (contract DEVICE-EXCHANGE 5.3). The password never leaves the
 * phone, and nothing logged here names the code, the login or the address.
 *
 * One code enrolls one device, so it goes to the first connected watch that takes the message, never to all.
 */
class ConnectWatchToExchangeEntryUseCase @Inject constructor(
    private val accounts: DeviceExchangeAccountRepository,
    private val dataLayer: WearableDataLayerRepository,
    private val gson: Gson,
    private val e2e: ExchangeE2eRepository,
) {

    suspend operator fun invoke(entry: DeviceExchangeAccountEntry): WatchExchangeHandoverOutcome {
        // Checked before the server is asked: a code issued for no watch would only count against the account.
        val nodes = if (dataLayer.isCompanionEnabled()) dataLayer.getConnectedNodes() else null
        return when {
            nodes == null -> WatchExchangeHandoverOutcome.CompanionOff
            nodes.isEmpty() -> WatchExchangeHandoverOutcome.NoConnectedWatch
            else -> handOver(entry, nodes)
        }
    }

    private suspend fun handOver(
        entry: DeviceExchangeAccountEntry,
        nodes: List<WearNode>,
    ): WatchExchangeHandoverOutcome {
        val code = try {
            accounts.issuePairingCode(entry)
        } catch (e: IOException) {
            return issueFailure(e)
        }
        return deliver(entry, payload(entry, code), nodes)
    }

    private fun issueFailure(failure: IOException): WatchExchangeHandoverOutcome =
        if (failure is DeviceExchangeRefusedException) {
            Timber.w("Watch exchange hand-over: the server refused a pairing code (%s)", failure.reason)
            WatchExchangeHandoverOutcome.CodeRefused(failure.reason)
        } else {
            Timber.i(failure, "Watch exchange hand-over: no pairing code from the server")
            WatchExchangeHandoverOutcome.ServerUnreachable
        }

    private suspend fun deliver(
        entry: DeviceExchangeAccountEntry,
        payload: WearExchangeHandoverPayload,
        nodes: List<WearNode>,
    ): WatchExchangeHandoverOutcome {
        val bytes = WearExchangeHandoverCodec.serialize(payload, gson)
        // onSubscription, not a collect after the send: the answer flow keeps no replay, so a watch answering
        // before the collector is registered would be dropped and this phone would wait out the whole timeout.
        val ack = withTimeoutOrNull(ACK_TIMEOUT_MS) {
            WearSyncEvents.exchangeHandoverAckFlow
                .onSubscription { if (!sendToFirst(nodes, bytes)) emit(NOT_SENT) }
                .first { answer -> answer === NOT_SENT || answer.requestId == payload.requestId }
        }
        return when {
            ack === NOT_SENT -> WatchExchangeHandoverOutcome.NoConnectedWatch
            ack == null -> WatchExchangeHandoverOutcome.WatchDidNotAnswer
            ack.enrolled -> {
                trustWatchKey(entry, ack)
                WatchExchangeHandoverOutcome.Enrolled
            }
            else -> WatchExchangeHandoverOutcome.WatchRefused(ack.reason ?: REASON_UNKNOWN)
        }
    }

    // The key the watch answered with outranks the directory, where the server could have put another (item DG).
    private suspend fun trustWatchKey(entry: DeviceExchangeAccountEntry, ack: WearExchangeHandoverAck) {
        val deviceId = ack.deviceId
        val kem = ack.e2eKem
        if (deviceId != null && kem != null) e2e.trustHandedOver(entry, deviceId, kem)
    }

    /** True when a node took the message; the nodes after it are never offered the code. */
    private suspend fun sendToFirst(nodes: List<WearNode>, bytes: ByteArray): Boolean = nodes.any { node ->
        runCatching { dataLayer.sendMessage(node.id, WearDataLayerPaths.EXCHANGE_HANDOVER, bytes) }
            .onFailure { it.warnUnlessCancellation("Watch exchange hand-over: send to ${node.id} failed") }
            .isSuccess
    }

    private fun payload(entry: DeviceExchangeAccountEntry, code: DeviceExchangePairingCode) =
        WearExchangeHandoverPayload(
            requestId = UUID.randomUUID().toString(),
            host = entry.host,
            port = entry.port,
            login = entry.login,
            pinnedCertificate = entry.pinnedCertificate,
            pairingCode = code.code,
            expiresAt = code.expiresAt,
        )

    private companion object {
        /** Long enough for the watch's own TLS connect timeout and the server's `enroll` answer behind it. */
        const val ACK_TIMEOUT_MS = 45_000L

        const val REASON_UNKNOWN = "unknown"

        /** Compared by identity, never by value: no answer from the watch is ever this instance. */
        val NOT_SENT = WearExchangeHandoverAck(requestId = "", enrolled = false)
    }
}
