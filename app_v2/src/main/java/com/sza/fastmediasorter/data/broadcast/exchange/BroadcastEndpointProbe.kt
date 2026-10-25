package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateChangedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateUntrustedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.data.security.e2e.E2eRelayHttp
import timber.log.Timber
import java.io.IOException
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
    }
}
