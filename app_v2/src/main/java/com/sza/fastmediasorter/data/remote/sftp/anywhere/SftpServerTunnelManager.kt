package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeNotEnrolledException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeRefusedException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeSession
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.model.SftpTunnelState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
 * The producer side of contract DEVICE-EXCHANGE sections 7.3-7.5 for the embedded SFTP share: while the
 * server runs, keeps one control stream of the producing account entry, publishes the share as an
 * `sftp-share` resource after every `welcome` (amendment H - the server keeps `access` and `shareId` in
 * memory only), and answers every `open` with a new authenticated data stream spliced to the server's own
 * loopback port. The relay never interprets a byte, so what crosses the splice is the client's SSH session.
 *
 * Nothing runs unless an account entry is enrolled and publishing is on: without one the embedded server
 * stays exactly the LAN server it was (contract section 9, rule 7).
 */
@Singleton
class SftpServerTunnelManager @Inject constructor(
    private val client: DeviceExchangeAccountClient,
    private val source: SftpExchangeProducerSource,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val mutableState = MutableStateFlow<SftpTunnelState>(SftpTunnelState.Off)
    val state: StateFlow<SftpTunnelState> = mutableState.asStateFlow()

    private var job: Job? = null

    /** The control stream whose resource is published right now; [stop] unpublishes it before closing. */
    private val published = AtomicReference<Publication?>(null)

    // Blocking socket reads ignore coroutine cancellation; closing the sockets is what ends them.
    private val openStreams: MutableSet<Closeable> = Collections.synchronizedSet(mutableSetOf())

    /** Starts (or restarts) publishing a server listening on [localPort] and reachable directly at [hosts]. */
    @Synchronized
    fun start(localPort: Int, hosts: List<String>) {
        Timber.d("S4094: tunnel manager start")
        // No unpublish on a restart: the new control stream replaces the old one on the server (7.3) and
        // publishes the same resource again, which a late unpublish on the old stream could undo.
        stopLocked(unpublish = false)
        job = appScope.launch(ioDispatcher) { run(localPort, hosts) }
    }

    @Synchronized
    fun stop() = stopLocked(unpublish = true)

    private fun stopLocked(unpublish: Boolean) {
        job?.cancel()
        job = null
        // The farewell needs its stream open, so it is taken out of the set closed below.
        val farewell = published.getAndSet(null)?.takeIf { unpublish }
        farewell?.let { openStreams.remove(it.session.connection) }
        closeAll()
        farewell?.let { appScope.launch(ioDispatcher) { unpublish(it) } }
        mutableState.value = SftpTunnelState.Off
    }

    // Tunnels are children of this scope, not of one session: a control stream that drops and comes back
    // keeps the live tunnels, which are separate data streams on the server too.
    private suspend fun run(localPort: Int, hosts: List<String>) = coroutineScope {
        var backoffMs = INITIAL_BACKOFF_MS
        var terminal: SftpTunnelState? = null
        while (currentCoroutineContext().isActive && terminal == null) {
            val producer = source.producer()
            val record = producer?.let { source.shareRecord(it, localPort, hosts) }
            val outcome = when {
                producer == null -> SessionOutcome(terminalState = SftpTunnelState.Off)
                record == null -> SessionOutcome()
                else -> session(this, producer.entry, record, localPort)
            }
            terminal = outcome.terminalState
            if (terminal == null) {
                backoffMs = if (outcome.wasWelcomed) INITIAL_BACKOFF_MS else backoffMs
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        terminal?.let { mutableState.value = it }
    }

    private suspend fun session(
        tunnels: CoroutineScope,
        entry: DeviceExchangeAccountEntry,
        record: SftpRendezvousResource,
        localPort: Int,
    ): SessionOutcome {
        mutableState.value = SftpTunnelState.Connecting
        var failure: IOException? = null
        val control = openOrNull(entry) { failure = it } ?: return openFailure(failure)
        track(control.connection)
        Timber.d("S4094: registered on the exchange server")
        val publication = Publication(control, record.resourceId)
        return try {
            serve(tunnels, publication, entry, localPort, record)
        } finally {
            published.compareAndSet(publication, null)
            close(control.connection)
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

    private fun openFailure(failure: IOException?): SessionOutcome = when (failure) {
        is DeviceExchangeNotEnrolledException -> SessionOutcome(terminalState = SftpTunnelState.Off)
        is DeviceExchangeRefusedException -> refusal(failure.reason)
        is ExchangeCertificateUntrustedException, is ExchangeCertificateChangedException -> {
            Timber.w(failure, "SftpServerTunnelManager: exchange certificate refused")
            SessionOutcome(terminalState = SftpTunnelState.CertificateChanged)
        }
        else -> {
            Timber.i(failure, "SftpServerTunnelManager: exchange server unreachable")
            mutableState.value = SftpTunnelState.Unreachable
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
        publication: Publication,
        entry: DeviceExchangeAccountEntry,
        localPort: Int,
        record: SftpRendezvousResource,
    ): SessionOutcome {
        val control = publication.session
        val intervalMs = (control.welcome.keepaliveSeconds ?: KEEPALIVE_SECONDS).toLong() * MILLIS_PER_SECOND
        val lastHeard = LastHeard()
        val keepalive = tunnels.launch(ioDispatcher) { keepalive(control, intervalMs, lastHeard) }
        var outcome = SessionOutcome(wasWelcomed = true)
        try {
            control.send(publishEnvelope(record))
            published.set(publication)
            var envelope = control.receive()
            while (envelope != null) {
                lastHeard.touch()
                val refused = handle(tunnels, envelope, entry, localPort)
                if (refused != null) outcome = refused.copy(wasWelcomed = true)
                envelope = if (refused == null) control.receive() else null
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
        localPort: Int,
    ): SessionOutcome? {
        when (envelope.type) {
            DeviceExchangeEnvelope.TYPE_PUBLISHED ->
                mutableState.value = SftpTunnelState.Registered(envelope.int(KEY_PUBLIC_PORT)?.takeIf { it > 0 })
            DeviceExchangeEnvelope.TYPE_REFUSED -> return refusal(envelope.string(DeviceExchangeEnvelope.KEY_REASON))
            DeviceExchangeEnvelope.TYPE_OPEN -> envelope.string(DeviceExchangeAccountClient.KEY_TUNNEL_ID)?.let { id ->
                tunnels.launch(ioDispatcher) { tunnel(entry, id, localPort) }
            }
        }
        return null
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

    // Going offline on purpose: the resource leaves the directory now instead of after a missed window.
    private fun unpublish(publication: Publication) {
        val removal = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_UNPUBLISH) {
            addProperty(DeviceExchangeAccountClient.KEY_RESOURCE_ID, publication.resourceId)
        }
        if (sendQuietly(publication.session, removal)) sendQuietly(publication.session, BYE)
        close(publication.session.connection)
    }

    private suspend fun tunnel(entry: DeviceExchangeAccountEntry, tunnelId: String, localPort: Int) {
        Timber.d("S4094: tunnel opened by the exchange server")
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

    // Identity matters, not content: compareAndSet must tell this session's publication from a newer one.
    private class Publication(val session: DeviceExchangeSession, val resourceId: String)

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
        const val KEY_PUBLIC_PORT = "publicPort"
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 120_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val BUFFER_SIZE = 32 * 1024
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
