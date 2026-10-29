package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateChangedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateUntrustedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTunnelUnavailableException
import com.sza.fastmediasorter.data.security.e2e.E2eRelayHttp
import com.sza.fastmediasorter.domain.player.AfterEndAnswer
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject

/**
 * S4160: the cheap question asked of each endpoint before a player is handed it - does anything answer there.
 * Blocking; the caller runs it on an IO dispatcher.
 */
class BroadcastEndpointProbe @Inject constructor(
    private val connector: ExchangeConnector,
) {

    enum class ExchangeAnswer { TRUSTED, TRUST_REFUSED, UNREACHABLE }

    /** A TCP connect to the host and port of a LAN endpoint within [LAN_TIMEOUT_MS]. */
    fun lanAnswers(url: String): Boolean {
        // An unparseable address is an endpoint nothing answers on; the next one in the list is tried.
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host ?: return false
        val port = uri.port.takeIf { it > 0 } ?: defaultPort(uri.scheme)
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), LAN_TIMEOUT_MS) }
            true
        } catch (e: IOException) {
            Timber.d("BroadcastEndpointProbe: LAN endpoint silent - %s", e.javaClass.simpleName)
            false
        }
    }

    /**
     * A TLS handshake with [server], judged with [certFingerprint] or else the platform chain (item D). Nothing is
     * written on the stream, so the probe spends no tunnel and reveals no broadcast id.
     */
    fun exchangeAnswers(server: ExchangeServer, certFingerprint: String?): ExchangeAnswer = try {
        connector.connect(server.host, server.port, ExchangeTrust.Verified(certFingerprint)).close()
        ExchangeAnswer.TRUSTED
    } catch (e: ExchangeCertificateChangedException) {
        Timber.w("BroadcastEndpointProbe: %s presented %s, the descriptor pins another", server.label, e.presented)
        ExchangeAnswer.TRUST_REFUSED
    } catch (e: ExchangeCertificateUntrustedException) {
        Timber.w("BroadcastEndpointProbe: %s presented %s, untrusted and unpinned", server.label, e.presented)
        ExchangeAnswer.TRUST_REFUSED
    } catch (e: IOException) {
        Timber.d("BroadcastEndpointProbe: %s unreachable - %s", server.label, e.javaClass.simpleName)
        ExchangeAnswer.UNREACHABLE
    }

    /**
     * S4247, research item 3: the status the relay answers for [relay]'s stream, null when nothing answered. The
     * connection closes right after the status line, so a waiting receiver never holds a listener slot.
     */
    fun relayStatus(relay: BroadcastExchangeEndpoint.Relay): Int? = try {
        val path = URI(relay.url).rawPath ?: "/"
        connector.connect(relay.server.host, relay.server.port, ExchangeTrust.Verified(relay.certFingerprint))
            .use { connection ->
                E2eRelayHttp.get(path, relay.server.label, connection.output)
                E2eRelayHttp.line(connection.input).split(' ', limit = STATUS_PARTS).getOrNull(1)?.toIntOrNull()
            }
    } catch (e: ExchangeCertificateChangedException) {
        Timber.w(e, "BroadcastEndpointProbe: %s relay pins another certificate", relay.server.label)
        null
    } catch (e: ExchangeCertificateUntrustedException) {
        Timber.w(e, "BroadcastEndpointProbe: %s relay certificate untrusted and unpinned", relay.server.label)
        null
    } catch (e: IOException) {
        Timber.d("BroadcastEndpointProbe: %s relay silent - %s", relay.server.label, e.javaClass.simpleName)
        null
    } catch (e: URISyntaxException) {
        Timber.d("BroadcastEndpointProbe: %s relay url unreadable - %s", relay.server.label, e.javaClass.simpleName)
        null
    }

    /**
     * S4444, LIVE-BROADCAST item T: the one reconnect after a stop, asked of a LAN endpoint. A closed port is an end,
     * so is a `404` or `410` to the first request; any other status is a live broadcast; silence is a lost connection.
     * Only the status line is read, so the probe never holds the producer's listener slot.
     */
    fun lanAfterEnd(url: String): AfterEndAnswer {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host ?: return AfterEndAnswer.NO_ANSWER
        val port = uri.port.takeIf { it > 0 } ?: defaultPort(uri.scheme)
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), LAN_TIMEOUT_MS)
                socket.soTimeout = LAN_TIMEOUT_MS
                socket.getOutputStream().apply {
                    write(firstRequest(uri, url).toByteArray(Charsets.US_ASCII))
                    flush()
                }
                val line = socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                answerOf(line?.split(' ', limit = STATUS_PARTS)?.getOrNull(1)?.toIntOrNull(), line != null)
            }
        } catch (e: ConnectException) {
            Timber.d("BroadcastEndpointProbe: LAN endpoint refused after the end - %s", e.javaClass.simpleName)
            AfterEndAnswer.ENDED
        } catch (e: IOException) {
            Timber.d("BroadcastEndpointProbe: LAN endpoint silent after the end - %s", e.javaClass.simpleName)
            AfterEndAnswer.NO_ANSWER
        }
    }

    /** S4444: the relay's status for [relay] read as an after-end answer; `404` and `410` both name an ended id. */
    fun relayAfterEnd(relay: BroadcastExchangeEndpoint.Relay): AfterEndAnswer {
        val status = relayStatus(relay)
        return answerOf(status, status != null)
    }

    /**
     * S4444: one `connect` for [tunnel]'s broadcast, closed at once. Only a refusal `ended` is an end (DEVICE-EXCHANGE
     * 1.10 item DY): `unavailable` also stands for a broadcast at its tunnel limit, which passes, and is what a 1.9
     * server answers for an ended one - that viewer falls back to the directory and the bounded reconnect. Any other
     * refusal and a broken connection are silence.
     */
    fun tunnelAfterEnd(tunnel: BroadcastExchangeEndpoint.Tunnel): AfterEndAnswer = try {
        DeviceExchangeAccountClient.connectByBroadcastId(
            connector = connector,
            host = tunnel.server.host,
            port = tunnel.server.port,
            broadcastId = tunnel.broadcastId,
            scheme = tunnel.scheme,
            trust = ExchangeTrust.Verified(tunnel.certFingerprint),
        ).close()
        AfterEndAnswer.ALIVE
    } catch (e: ExchangeTunnelUnavailableException) {
        Timber.d("BroadcastEndpointProbe: %s tunnel refused after the end - %s", tunnel.server.label, e.message)
        Timber.d("S4448: tunnel after-end refusal reason=%s", e.reason)
        if (e.reason == DeviceExchangeEnvelope.REASON_ENDED) AfterEndAnswer.ENDED else AfterEndAnswer.NO_ANSWER
    } catch (e: IOException) {
        Timber.d(e, "BroadcastEndpointProbe: %s tunnel silent after the end", tunnel.server.label)
        AfterEndAnswer.NO_ANSWER
    }

    private fun answerOf(status: Int?, answered: Boolean): AfterEndAnswer = when {
        status == HTTP_NOT_FOUND || status == HTTP_GONE -> AfterEndAnswer.ENDED
        answered -> AfterEndAnswer.ALIVE
        else -> AfterEndAnswer.NO_ANSWER
    }

    private fun firstRequest(uri: URI, url: String): String =
        if (uri.scheme.equals("rtsp", ignoreCase = true)) {
            "DESCRIBE $url RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\n\r\n"
        } else {
            val path = uri.rawPath.orEmpty().ifEmpty { "/" } + uri.rawQuery?.let { "?$it" }.orEmpty()
            "GET $path HTTP/1.1\r\nHost: ${uri.rawAuthority}\r\nConnection: close\r\n\r\n"
        }

    private fun defaultPort(scheme: String?): Int = when (scheme?.lowercase()) {
        "rtsp" -> RTSP_DEFAULT_PORT
        "https" -> HTTPS_DEFAULT_PORT
        else -> HTTP_DEFAULT_PORT
    }

    private companion object {
        const val LAN_TIMEOUT_MS = 1_500
        const val HTTP_DEFAULT_PORT = 80
        const val HTTPS_DEFAULT_PORT = 443
        const val RTSP_DEFAULT_PORT = 554
        const val STATUS_PARTS = 3
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
    }
}
