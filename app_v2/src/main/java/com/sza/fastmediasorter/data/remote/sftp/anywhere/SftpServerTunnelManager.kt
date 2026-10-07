package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.SftpExchangeConfig
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
import javax.inject.Inject
import javax.inject.Singleton

/** Where the tunnel manager reads the exchange configuration and records the certificate pin. */
interface SftpExchangeConfigSource {
    suspend fun snapshot(): SftpExchangeConfig

    suspend fun pinCertificate(fingerprint: String)
}

/**
 * The producer side of contract ANYWHERE-ACCESS sections 5.4 and 6.3: while the embedded SFTP server
 * runs, holds an outbound registration on the configured exchange server and answers every `open`
 * with a new data stream spliced to the server's own loopback port. The relay never interprets a byte,
 * so what crosses the splice is the client's SSH session, end to end.
 *
 * Nothing runs unless [start] is called with a usable configuration: with no exchange server set the
 * embedded server stays exactly the LAN server it was (contract section 8).
 */
@Singleton
class SftpServerTunnelManager @Inject constructor(
    private val connector: ExchangeConnector,
    private val configSource: SftpExchangeConfigSource,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val mutableState = MutableStateFlow<SftpTunnelState>(SftpTunnelState.Off)
    val state: StateFlow<SftpTunnelState> = mutableState.asStateFlow()

    private var job: Job? = null

    // Blocking socket reads ignore coroutine cancellation; closing the sockets is what ends them.
    private val openStreams: MutableSet<Closeable> = Collections.synchronizedSet(mutableSetOf())

    /** Starts (or restarts) the registration for a server listening on [localPort]. */
    @Synchronized
    fun start(localPort: Int, directEndpoints: List<String>) {
        Timber.d("S4094: tunnel manager start")
        stopLocked()
        job = appScope.launch(ioDispatcher) { run(localPort, directEndpoints) }
    }

    @Synchronized
    fun stop() = stopLocked()

    private fun stopLocked() {
        job?.cancel()
        job = null
        closeAll()
        mutableState.value = SftpTunnelState.Off
    }

    // Tunnels are children of this scope, not of one session: a control stream that drops and resumes
    // inside the keepalive window keeps its live tunnels (contract section 6.3, re-registration).
    private suspend fun run(localPort: Int, directEndpoints: List<String>) = coroutineScope {
        var backoffMs = INITIAL_BACKOFF_MS
        var resumeToken: String? = null
        var terminal: SftpTunnelState? = null
        while (currentCoroutineContext().isActive && terminal == null) {
            val config = configSource.snapshot()
            val outcome = if (config.isUsable) {
                session(this, config, localPort, directEndpoints, resumeToken)
            } else {
                SessionOutcome(resumeToken, terminalState = SftpTunnelState.Off)
            }
            resumeToken = outcome.resumeToken
            terminal = outcome.terminalState
            if (terminal == null) {
                backoffMs = if (outcome.wasRegistered) INITIAL_BACKOFF_MS else backoffMs
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        terminal?.let { mutableState.value = it }
    }

    private suspend fun session(
        tunnels: CoroutineScope,
        config: SftpExchangeConfig,
        localPort: Int,
        directEndpoints: List<String>,
        resumeToken: String?,
    ): SessionOutcome {
        mutableState.value = SftpTunnelState.Connecting
        var failure: IOException? = null
        val control = connectOrNull(config) { failure = it } ?: return connectFailure(failure, resumeToken)
        if (config.pinnedCertificate == null) configSource.pinCertificate(control.certificateFingerprint)
        val pinnedConfig = config.copy(pinnedCertificate = control.certificateFingerprint)
        return try {
            ExchangeFrameCodec.write(control.output, registerEnvelope(config, directEndpoints, resumeToken))
            val answer = ExchangeFrameCodec.read(control.input)
            when (answer?.type) {
                ExchangeEnvelope.TYPE_REGISTERED -> serve(tunnels, control, pinnedConfig, localPort, answer)
                ExchangeEnvelope.TYPE_REFUSED -> refusal(answer.reason, resumeToken)
                else -> SessionOutcome(resumeToken)
            }
        } catch (e: IOException) {
            Timber.i(e, "SftpServerTunnelManager: registration stream ended")
            SessionOutcome(resumeToken)
        } finally {
            close(control)
        }
    }

    private fun connectOrNull(config: SftpExchangeConfig, onFailure: (IOException) -> Unit): ExchangeConnection? =
        try {
            track(connector.connect(config.host, config.port, config.pinnedCertificate))
        } catch (e: IOException) {
            onFailure(e)
            null
        }

    private fun connectFailure(failure: IOException?, resumeToken: String?): SessionOutcome {
        if (failure is ExchangeCertificateChangedException) {
            Timber.w(failure, "SftpServerTunnelManager: exchange certificate changed")
            return SessionOutcome(resumeToken, terminalState = SftpTunnelState.CertificateChanged)
        }
        Timber.i(failure, "SftpServerTunnelManager: exchange server unreachable")
        mutableState.value = SftpTunnelState.Unreachable
        return SessionOutcome(resumeToken)
    }

    private fun refusal(reason: String?, resumeToken: String?): SessionOutcome {
        val refused = SftpTunnelState.Refused(reason ?: REASON_UNKNOWN)
        Timber.w("SftpServerTunnelManager: registration refused (%s)", refused.reason)
        mutableState.value = refused
        // A wrong password or a plain stream stays wrong on every retry; the other reasons are transient.
        val permanent = refused.reason in PERMANENT_REFUSALS
        return SessionOutcome(resumeToken, terminalState = refused.takeIf { permanent })
    }

    private suspend fun serve(
        tunnels: CoroutineScope,
        control: ExchangeConnection,
        config: SftpExchangeConfig,
        localPort: Int,
        registered: ExchangeEnvelope,
    ): SessionOutcome {
        mutableState.value = SftpTunnelState.Registered(registered.port?.takeIf { it > 0 })
        Timber.d("S4094: registered on the exchange server")
        val intervalMs = (registered.keepaliveSeconds ?: KEEPALIVE_SECONDS).toLong() * MILLIS_PER_SECOND
        val lastHeard = LastHeard()
        val keepalive = tunnels.launch(ioDispatcher) { keepalive(control, intervalMs, lastHeard) }
        try {
            while (true) {
                val envelope = ExchangeFrameCodec.read(control.input) ?: break
                lastHeard.touch()
                val tunnelId = envelope.tunnelId
                if (envelope.type == ExchangeEnvelope.TYPE_OPEN && tunnelId != null) {
                    tunnels.launch(ioDispatcher) { tunnel(config, tunnelId, localPort) }
                }
            }
        } catch (e: IOException) {
            Timber.i(e, "SftpServerTunnelManager: control stream lost")
        } finally {
            keepalive.cancel()
        }
        return SessionOutcome(registered.resumeToken, wasRegistered = true)
    }

    // The server answers every keepalive, so three silent intervals mean a dead stream that TCP alone
    // could take many minutes to notice (contract section 6.3).
    private suspend fun keepalive(control: ExchangeConnection, intervalMs: Long, lastHeard: LastHeard) {
        var alive = true
        while (alive) {
            delay(intervalMs)
            alive = lastHeard.silentFor() <= intervalMs * KEEPALIVE_WINDOW && sendKeepalive(control)
        }
        Timber.i("SftpServerTunnelManager: keepalive window missed or write failed")
        close(control)
    }

    private fun sendKeepalive(control: ExchangeConnection): Boolean = try {
        ExchangeFrameCodec.write(control.output, ExchangeEnvelope(ExchangeEnvelope.TYPE_KEEPALIVE))
        true
    } catch (e: IOException) {
        Timber.i(e, "SftpServerTunnelManager: keepalive write failed")
        false
    }

    private suspend fun tunnel(config: SftpExchangeConfig, tunnelId: String, localPort: Int) {
        Timber.d("S4094: tunnel opened by the exchange server")
        var data: ExchangeConnection? = null
        var local: Socket? = null
        try {
            val stream = track(connector.connect(config.host, config.port, config.pinnedCertificate))
            data = stream
            val attach = ExchangeEnvelope(ExchangeEnvelope.TYPE_ATTACH, shareId = config.shareId, tunnelId = tunnelId)
            ExchangeFrameCodec.write(stream.output, attach)
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

    private fun registerEnvelope(
        config: SftpExchangeConfig,
        directEndpoints: List<String>,
        resumeToken: String?,
    ): ExchangeEnvelope {
        val endpoints = JsonArray().apply { directEndpoints.forEach(::add) }
        return ExchangeEnvelope(
            type = ExchangeEnvelope.TYPE_REGISTER,
            password = config.password,
            shareId = config.shareId,
            keepaliveSeconds = KEEPALIVE_SECONDS,
            port = ANY_PUBLIC_PORT,
            resumeToken = resumeToken,
            claim = JsonObject().apply { add(CLAIM_ENDPOINTS, endpoints) },
        )
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
        val resumeToken: String?,
        val wasRegistered: Boolean = false,
        /** Non-null when retrying cannot help; the manager stops in this state. */
        val terminalState: SftpTunnelState? = null,
    )

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val KEEPALIVE_SECONDS = 30
        const val KEEPALIVE_WINDOW = 3
        const val ANY_PUBLIC_PORT = 0
        const val CLAIM_ENDPOINTS = "endpoints"
        const val REASON_UNKNOWN = "unknown"
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 120_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val BUFFER_SIZE = 32 * 1024
        val PERMANENT_REFUSALS = setOf(ExchangeEnvelope.REASON_BAD_PASSWORD, ExchangeEnvelope.REASON_TLS_REQUIRED)
    }
}
