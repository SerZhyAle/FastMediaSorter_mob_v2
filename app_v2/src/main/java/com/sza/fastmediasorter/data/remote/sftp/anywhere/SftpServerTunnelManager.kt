package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeNotEnrolledException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeRefusedException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeSession
import com.sza.fastmediasorter.data.remote.exchange.ExchangeBroadcastPublication
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.model.SftpTunnelState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The producer side of contract DEVICE-EXCHANGE sections 7.3-7.5 through the outgoing account entry: keeps the
 * device's one control stream (7.3) while the embedded SFTP share is published or a broadcast is live, and sends
 * both after every `welcome` (amendment H - the server keeps `access`, `shareId` and the broadcast in memory only).
 * Every `open` gets a new authenticated data stream spliced to a loopback port: the SFTP server's for the share,
 * the broadcast's HTTP or RTSP server for a `broadcastId` (7.5). The relay never interprets a byte.
 *
 * One owner for both is what the contract demands: a second control stream from the same device would replace
 * this one, so a broadcast and a published share always travel on the same stream.
 *
 * Nothing runs unless an account entry is the outgoing one: without it the embedded server and the broadcast stay
 * exactly the LAN services they were (contract section 9, rule 7).
 */
@Singleton
class SftpServerTunnelManager @Inject constructor(
    private val client: DeviceExchangeAccountClient,
    private val source: SftpExchangeProducerSource,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val mutableState = MutableStateFlow<SftpTunnelState>(SftpTunnelState.Off)

    /** The share's registration; a broadcast-only control stream leaves it [SftpTunnelState.Off]. */
    val state: StateFlow<SftpTunnelState> = mutableState.asStateFlow()

    private var job: Job? = null

    @Volatile
    private var share: ShareTarget? = null

    @Volatile
    private var broadcast: ExchangeBroadcastPublication? = null

    /** The device takes casts (7.8), which arrive only on its control stream, so the stream is held for them too. */
    @Volatile
    private var receiving = false

    private val mutableControlEvents = MutableSharedFlow<DeviceExchangeEnvelope>(extraBufferCapacity = EVENT_BUFFER)

    /** The control-stream envelopes other owners act on: directory and the cast messages of 7.8. */
    val controlEvents: SharedFlow<DeviceExchangeEnvelope> = mutableControlEvents.asSharedFlow()

    /** The welcomed control stream, with what was sent on it; a farewell undoes exactly that. */
    private val live = AtomicReference<LiveControl?>(null)

    // Blocking socket reads ignore coroutine cancellation; closing the sockets is what ends them.
    private val openStreams: MutableSet<Closeable> = Collections.synchronizedSet(mutableSetOf())

    /** Starts (or restarts) publishing a server listening on [localPort] and reachable directly at [hosts]. */
    @Synchronized
    fun start(localPort: Int, hosts: List<String>) {
        share = ShareTarget(localPort, hosts)
        // No farewell on a restart: the new control stream replaces the old one on the server (7.3) and sends
        // the same records again, which a late unpublish on the old stream could undo.
        detachLocked(farewell = false)
        job = appScope.launch(ioDispatcher) { run() }
    }

    /**
     * Holds the control stream while [on] even with nothing published, so a cast reaches this device; turning it
     * off with nothing published lets the stream go.
     */
    @Synchronized
    fun setReceiving(on: Boolean) {
        if (receiving == on) return
        receiving = on
        when {
            on && job?.isActive != true -> job = appScope.launch(ioDispatcher) { run() }
            !on && share == null && broadcast == null ->
                detachLocked(farewell = true)?.let { control -> appScope.launch(ioDispatcher) { farewell(control) } }
        }
    }

    /** Sends [envelope] on the live control stream; false when none is welcomed or the write failed. Blocking. */
    fun sendControl(envelope: DeviceExchangeEnvelope): Boolean =
        live.get()?.let { sendQuietly(it.session, envelope) } ?: false

    /** Stops publishing the share; a live broadcast keeps the control stream and only the share leaves it. */
    @Synchronized
    fun stop() {
        share = null
        if (broadcast == null && !receiving) {
            detachLocked(farewell = true)?.let { control -> appScope.launch(ioDispatcher) { farewell(control) } }
            return
        }
        mutableState.value = SftpTunnelState.Off
        live.get()?.let { control -> appScope.launch(ioDispatcher) { unpublish(control) } }
    }

    /**
     * Leaves as a producer (contract DEVICE-EXCHANGE item AC): stops publishing, ends the broadcast on the exchange
     * and sends `bye` on the live control stream. True when every message was written; false when nothing was live
     * or a write failed. Nothing is retried, so a false answer means the leave ended on this device only.
     */
    suspend fun withdraw(): Boolean {
        val control = synchronized(this) {
            share = null
            broadcast = null
            detachLocked(farewell = true)
        } ?: return false
        return withContext(ioDispatcher) { farewell(control) }
    }

    /**
     * Announces [publication] - `broadcast-start` the first time on a stream, `broadcast-update` after (7.4) - and
     * keeps announcing it after every `welcome` until [endBroadcast]. Opens the control stream when none runs.
     */
    @Synchronized
    fun announceBroadcast(publication: ExchangeBroadcastPublication) {
        broadcast = publication
        val control = live.get()
        when {
            control != null -> appScope.launch(ioDispatcher) { announce(control, publication) }
            job?.isActive != true -> job = appScope.launch(ioDispatcher) { run() }
        }
    }

    /** Ends the announced broadcast (`broadcast-end`); with no share published the control stream goes as well. */
    @Synchronized
    fun endBroadcast() {
        broadcast ?: return
        broadcast = null
        if (share == null && !receiving) {
            detachLocked(farewell = true)?.let { control -> appScope.launch(ioDispatcher) { farewell(control) } }
            return
        }
        live.get()?.let { control -> appScope.launch(ioDispatcher) { endAnnounced(control) } }
    }

    /** Ends the control loop and closes every stream; returns the live control stream for a farewell, if asked. */
    private fun detachLocked(farewell: Boolean): LiveControl? {
        job?.cancel()
        job = null
        // The farewell needs its stream open, so it is taken out of the set closed below.
        val control = live.getAndSet(null)?.takeIf { farewell }
        control?.let { openStreams.remove(it.session.connection) }
        closeAll()
        mutableState.value = SftpTunnelState.Off
        return control
    }

    // Tunnels are children of this scope, not of one session: a control stream that drops and comes back
    // keeps the live tunnels, which are separate data streams on the server too.
    private suspend fun run() = coroutineScope {
        var backoffMs = INITIAL_BACKOFF_MS
        var terminal: SftpTunnelState? = null
        while (currentCoroutineContext().isActive && terminal == null) {
            val outcome = attempt(this)
            terminal = outcome.terminalState
            if (terminal == null) {
                backoffMs = if (outcome.wasWelcomed) INITIAL_BACKOFF_MS else backoffMs
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        terminal?.let { mutableState.value = it }
    }

    private suspend fun attempt(tunnels: CoroutineScope): SessionOutcome {
        val target = share
        val producer = target?.let { source.producer() }
        val record = producer?.let { source.shareRecord(it, target.localPort, target.hosts) }
        val entry = producer?.entry ?: source.outgoingEntry()
        val nothingToSend = record == null && broadcast == null && !receiving
        return when {
            entry == null -> SessionOutcome(terminalState = SftpTunnelState.Off)
            // Publishing off with no broadcast is final; a share whose host key is not ready yet is retried.
            nothingToSend && producer == null -> SessionOutcome(terminalState = SftpTunnelState.Off)
            nothingToSend -> SessionOutcome()
            else -> session(tunnels, entry, record, target?.localPort)
        }
    }

    private suspend fun session(
        tunnels: CoroutineScope,
        entry: DeviceExchangeAccountEntry,
        record: SftpRendezvousResource?,
        sharePort: Int?,
    ): SessionOutcome {
        if (record != null) mutableState.value = SftpTunnelState.Connecting
        var failure: IOException? = null
        val session = openOrNull(entry) { failure = it } ?: return openFailure(failure, record != null)
        track(session.connection)
        val control = LiveControl(session)
        return try {
            serve(tunnels, control, entry, record, sharePort)
        } finally {
            live.compareAndSet(control, null)
            close(session.connection)
        }
    }

    private suspend fun openOrNull(
        entry: DeviceExchangeAccountEntry,
        onFailure: (IOException) -> Unit,
    ): DeviceExchangeSession? = try {
        client.openAuthenticated(entry, KEEPALIVE_SECONDS)
    } catch (e: IOException) {
        onFailure(e)
        null
    }

    private fun openFailure(failure: IOException?, sharing: Boolean): SessionOutcome = when (failure) {
        is DeviceExchangeNotEnrolledException -> SessionOutcome(terminalState = SftpTunnelState.Off)
        is DeviceExchangeRefusedException -> refusal(failure.reason)
        is ExchangeCertificateUntrustedException, is ExchangeCertificateChangedException -> {
            Timber.w(failure, "SftpServerTunnelManager: exchange certificate refused")
            SessionOutcome(terminalState = SftpTunnelState.CertificateChanged)
        }
        else -> {
            Timber.i(failure, "SftpServerTunnelManager: exchange server unreachable")
            if (sharing) mutableState.value = SftpTunnelState.Unreachable
            SessionOutcome()
        }
    }

    private fun refusal(reason: String?): SessionOutcome {
        val refused = SftpTunnelState.Refused(reason ?: DeviceExchangeAccountClient.REASON_UNKNOWN)
        Timber.w("SftpServerTunnelManager: exchange server refused (%s)", refused.reason)
        mutableState.value = refused
        // A dead token or a foreign id stays wrong on every retry; the other reasons are transient.
        return SessionOutcome(terminalState = refused.takeIf { it.reason in PERMANENT_REFUSALS })
    }

    private suspend fun serve(
        tunnels: CoroutineScope,
        control: LiveControl,
        entry: DeviceExchangeAccountEntry,
        record: SftpRendezvousResource?,
        sharePort: Int?,
    ): SessionOutcome {
        val session = control.session
        val intervalMs = (session.welcome.keepaliveSeconds ?: KEEPALIVE_SECONDS).toLong() * MILLIS_PER_SECOND
        val lastHeard = LastHeard()
        val keepalive = tunnels.launch(ioDispatcher) { keepalive(session, intervalMs, lastHeard) }
        var outcome = SessionOutcome(wasWelcomed = true)
        try {
            record?.let {
                session.send(publishEnvelope(it))
                control.resourceId = it.resourceId
            }
            live.set(control)
            broadcast?.let { announce(control, it) }
            var envelope = session.receive()
            while (envelope != null) {
                lastHeard.touch()
                val refused = handle(tunnels, envelope, entry, sharePort)
                if (refused != null) outcome = refused.copy(wasWelcomed = true)
                envelope = if (refused == null) session.receive() else null
            }
        } catch (e: IOException) {
            Timber.i(e, "SftpServerTunnelManager: control stream lost")
        } finally {
            keepalive.cancel()
        }
        return outcome
    }

    /** Acts on one control-stream envelope; a non-null outcome ends the session. */
    private fun handle(
        tunnels: CoroutineScope,
        envelope: DeviceExchangeEnvelope,
        entry: DeviceExchangeAccountEntry,
        sharePort: Int?,
    ): SessionOutcome? {
        when (envelope.type) {
            DeviceExchangeEnvelope.TYPE_PUBLISHED ->
                mutableState.value = SftpTunnelState.Registered(envelope.int(KEY_PUBLIC_PORT)?.takeIf { it > 0 })
            DeviceExchangeEnvelope.TYPE_REFUSED -> return refusal(envelope.string(DeviceExchangeEnvelope.KEY_REASON))
            in FORWARDED_TYPES -> mutableControlEvents.tryEmit(envelope)
            DeviceExchangeEnvelope.TYPE_OPEN -> envelope.string(DeviceExchangeAccountClient.KEY_TUNNEL_ID)?.let { id ->
                val port = tunnelPort(envelope, sharePort)
                if (port == null) {
                    Timber.i("SftpServerTunnelManager: open for nothing this device serves now")
                } else {
                    tunnels.launch(ioDispatcher) { tunnel(entry, id, port) }
                }
            }
        }
        return null
    }

    // An open names a broadcast by its id and scheme (7.5); without one it is for the share.
    private fun tunnelPort(open: DeviceExchangeEnvelope, sharePort: Int?): Int? {
        val broadcastId = open.string(DeviceExchangeAccountClient.KEY_BROADCAST_ID) ?: return sharePort
        val scheme = open.string(DeviceExchangeAccountClient.KEY_SCHEME)
        return broadcast?.takeIf { it.record.broadcastId == broadcastId }?.localPorts?.get(scheme)
    }

    // The server answers every keepalive, so three silent intervals mean a dead stream that TCP alone
    // could take many minutes to notice (contract section 7.3).
    private suspend fun keepalive(control: DeviceExchangeSession, intervalMs: Long, lastHeard: LastHeard) {
        var alive = true
        while (alive) {
            delay(intervalMs)
            alive = lastHeard.silentFor() <= intervalMs * KEEPALIVE_WINDOW && sendQuietly(control, KEEPALIVE)
        }
        Timber.i("SftpServerTunnelManager: keepalive window missed or write failed")
        close(control.connection)
    }

    private fun sendQuietly(control: DeviceExchangeSession, envelope: DeviceExchangeEnvelope): Boolean = try {
        control.send(envelope)
        true
    } catch (e: IOException) {
        Timber.i(e, "SftpServerTunnelManager: %s write failed", envelope.type)
        false
    }

    // Serialized per stream so a start can never arrive after the update meant to follow it.
    private fun announce(control: LiveControl, publication: ExchangeBroadcastPublication) = synchronized(control) {
        val id = publication.record.broadcastId
        val type = if (control.broadcastId == id) {
            DeviceExchangeEnvelope.TYPE_BROADCAST_UPDATE
        } else {
            DeviceExchangeEnvelope.TYPE_BROADCAST_START
        }
        val envelope = DeviceExchangeEnvelope.of(type) {
            add(KEY_BROADCAST, SftpRendezvousCodec.body(publication.record))
            if (type == DeviceExchangeEnvelope.TYPE_BROADCAST_START) {
                addProperty(KEY_MAX_LISTENERS, publication.maxListeners)
            }
        }
        if (sendQuietly(control.session, envelope)) {
            Timber.i("SftpServerTunnelManager: broadcast announced (%s)", type)
            control.broadcastId = id
        }
    }

    private fun endAnnounced(control: LiveControl): Boolean = synchronized(control) {
        val id = control.broadcastId ?: return true
        control.broadcastId = null
        sendQuietly(
            control.session,
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BROADCAST_END) {
                addProperty(DeviceExchangeAccountClient.KEY_BROADCAST_ID, id)
            }
        )
    }

    private fun unpublish(control: LiveControl): Boolean = synchronized(control) {
        val id = control.resourceId ?: return true
        control.resourceId = null
        sendQuietly(
            control.session,
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_UNPUBLISH) {
                addProperty(DeviceExchangeAccountClient.KEY_RESOURCE_ID, id)
            }
        )
    }

    // Going offline on purpose: the records leave the directory now instead of after a missed window.
    private fun farewell(control: LiveControl): Boolean {
        val unpublished = unpublish(control)
        val ended = endAnnounced(control)
        val sent = unpublished && ended && sendQuietly(control.session, BYE)
        close(control.session.connection)
        return sent
    }

    private suspend fun tunnel(entry: DeviceExchangeAccountEntry, tunnelId: String, localPort: Int) {
        var data: ExchangeConnection? = null
        var local: Socket? = null
        try {
            val stream = track(client.attach(entry, tunnelId))
            data = stream
            val server = track(Socket(LOOPBACK, localPort))
            local = server
            coroutineScope {
                launch(ioDispatcher) { pump(stream.input, server.getOutputStream(), stream, server) }
                launch(ioDispatcher) { pump(server.getInputStream(), stream.output, stream, server) }
            }
        } catch (e: IOException) {
            Timber.i(e, "SftpServerTunnelManager: tunnel could not be opened")
        } finally {
            data?.let(::close)
            local?.let(::close)
        }
    }

    // Either direction ending ends the tunnel: closing both sockets unblocks the opposite pump.
    private fun pump(from: InputStream, to: OutputStream, first: Closeable, second: Closeable) {
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            var read = from.read(buffer)
            while (read >= 0) {
                to.write(buffer, 0, read)
                to.flush()
                read = from.read(buffer)
            }
        } catch (e: IOException) {
            Timber.d(e, "SftpServerTunnelManager: tunnel stream closed")
        } finally {
            close(first)
            close(second)
        }
    }

    private fun <T : Closeable> track(stream: T): T = stream.also(openStreams::add)

    private fun close(stream: Closeable) {
        openStreams.remove(stream)
        try {
            stream.close()
        } catch (e: IOException) {
            Timber.d(e, "SftpServerTunnelManager: close failed")
        }
    }

    private fun closeAll() {
        val streams = synchronized(openStreams) { openStreams.toList() }
        streams.forEach(::close)
    }

    private fun publishEnvelope(record: SftpRendezvousResource) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_PUBLISH) {
            add(KEY_RESOURCE, SftpRendezvousCodec.body(record))
        }

    private data class ShareTarget(val localPort: Int, val hosts: List<String>)

    // Identity matters, not content: compareAndSet must tell this stream from a newer one.
    private class LiveControl(val session: DeviceExchangeSession) {
        @Volatile
        var resourceId: String? = null

        @Volatile
        var broadcastId: String? = null
    }

    private class LastHeard {
        @Volatile
        private var at = System.nanoTime()

        fun touch() {
            at = System.nanoTime()
        }

        fun silentFor(): Long = (System.nanoTime() - at) / NANOS_PER_MILLI
    }

    private data class SessionOutcome(
        /** A `welcome` was received, so the next attempt starts from the shortest backoff again. */
        val wasWelcomed: Boolean = false,
        /** Non-null when retrying cannot help; the manager stops in this state. */
        val terminalState: SftpTunnelState? = null,
    )

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val KEEPALIVE_SECONDS = 30
        const val KEEPALIVE_WINDOW = 3
        const val KEY_RESOURCE = "resource"
        const val KEY_BROADCAST = "broadcast"
        const val KEY_MAX_LISTENERS = "maxListeners"
        const val KEY_PUBLIC_PORT = "publicPort"
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 120_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val BUFFER_SIZE = 32 * 1024
        const val EVENT_BUFFER = 16
        val FORWARDED_TYPES = setOf(
            DeviceExchangeEnvelope.TYPE_DIRECTORY,
            DeviceExchangeEnvelope.TYPE_CAST_OFFER,
            DeviceExchangeEnvelope.TYPE_CAST_RESULT,
            DeviceExchangeEnvelope.TYPE_CAST_STOP,
        )
        val KEEPALIVE = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_KEEPALIVE)
        val BYE = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BYE)
        val PERMANENT_REFUSALS = setOf(
            DeviceExchangeEnvelope.REASON_BAD_CREDENTIALS,
            DeviceExchangeEnvelope.REASON_DEVICE_REVOKED,
            DeviceExchangeEnvelope.REASON_TLS_REQUIRED,
            DeviceExchangeEnvelope.REASON_VERSION_UNSUPPORTED,
            DeviceExchangeEnvelope.REASON_ID_COLLISION,
        )
    }
}
