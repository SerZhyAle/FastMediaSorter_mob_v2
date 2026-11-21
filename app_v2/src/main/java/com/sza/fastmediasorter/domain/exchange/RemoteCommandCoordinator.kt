package com.sza.fastmediasorter.domain.exchange

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.ExchangeReceiverControlStreams
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager
import com.sza.fastmediasorter.data.repository.ExchangeE2eRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** A `command-request` as read, with the receive-only entry it came on; null [origin] is the outgoing tunnel. */
private class IncomingCommand(val origin: DeviceExchangeAccountEntry?, val envelope: DeviceExchangeEnvelope)

/**
 * The target half of REMOTE-CONTROL on the wire (DEVICE-EXCHANGE 1.18 item GC). Requests arrive on the outgoing
 * entry's tunnel and on every receive-only stream; each is copied at once into its own queue, so a consent prompt
 * holding one request for a minute never makes the two shared event flows drop a frame. The queue is worked in
 * arrival order and every answer goes back on the stream its request came on, only while that stream's `welcome`
 * lists `command` with `sealed`.
 */
@Singleton
class RemoteCommandCoordinator @Inject constructor(
    private val tunnel: SftpServerTunnelManager,
    private val receivers: ExchangeReceiverControlStreams,
    private val e2e: ExchangeE2eRepository,
    private val answers: RemoteCommandAnswerManager,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val started = AtomicBoolean(false)
    private val queue = Channel<IncomingCommand>(QUEUE_CAPACITY)

    /** Idempotent: a second call adds no second collector. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(ioDispatcher) {
            tunnel.controlEvents.collect { envelope -> enqueue(null, envelope) }
        }
        scope.launch(ioDispatcher) {
            receivers.events.collect { event -> enqueue(event.entry, event.envelope) }
        }
        scope.launch(ioDispatcher) {
            for (incoming in queue) serve(incoming)
        }
    }

    private fun enqueue(origin: DeviceExchangeAccountEntry?, envelope: DeviceExchangeEnvelope) {
        if (envelope.type != DeviceExchangeEnvelope.TYPE_COMMAND_REQUEST) return
        Timber.d("S4701: command-request arrived viaTunnel=%b", origin == null)
        // A full queue drops the newest: the controller's own time-out answers it, as for a box that does not open.
        if (queue.trySend(IncomingCommand(origin, envelope)).isFailure) {
            Timber.w("RemoteCommandCoordinator: request queue full, a command-request was dropped")
        }
    }

    private suspend fun serve(incoming: IncomingCommand) {
        val entry = incoming.origin ?: tunnel.liveEntry
        if (entry == null || !entry.mayProduce) {
            Timber.i("RemoteCommandCoordinator: command-request with no member entry to open it")
            return
        }
        val reply = answers.answer(incoming.envelope) { e2e.trustedSealing(entry) } ?: return
        val carries = incoming.origin?.let { receivers.carriesCommands(it.identity) } ?: tunnel.liveCarriesCommands
        val sent = carries && if (incoming.origin != null) {
            receivers.send(incoming.origin.identity, reply.envelope)
        } else {
            tunnel.sendControl(reply.envelope)
        }
        if (!sent) Timber.i("RemoteCommandCoordinator: command-result not sent, no command stream")
        reply.afterAnswer?.invoke()
    }

    private companion object {
        const val QUEUE_CAPACITY = 64
    }
}
