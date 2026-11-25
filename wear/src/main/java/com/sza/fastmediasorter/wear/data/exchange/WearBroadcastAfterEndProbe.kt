package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.wear.domain.playback.WearAfterEndAnswer
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DEVICE-EXCHANGE item DY on the watch: the one reconnect after a played broadcast's walk ran out, asked of a LAN or a
 * relay endpoint. Unlike the resolver's probes it tells a refusal from silence. Only the status line is read and the
 * connection closed, so the question never holds the producer's listener slot; neither the server address nor the
 * broadcast id reaches a log (7.9).
 */
@Singleton
class WearBroadcastAfterEndProbe @Inject constructor(
    private val connector: WearExchangeConnector,
) {

    fun lan(endpoint: WearBroadcastEndpoint.Lan): WearAfterEndAnswer = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = STATUS_TIMEOUT_MS
            socket.getOutputStream().apply {
                write(lanRequest(endpoint).toByteArray(Charsets.US_ASCII))
                flush()
            }
            answerOf(statusOf(statusLine(socket.getInputStream())), answered = true)
        }
    } catch (e: ConnectException) {
        // "Network is unreachable" is the watch's own lost link; only a refusal says the producer closed its port.
        val refused = e.message.orEmpty().contains(REFUSED, ignoreCase = true)
        Timber.v("WearBroadcastAfterEndProbe: LAN connect failed after the end, refused=%s", refused)
        if (refused) WearAfterEndAnswer.ENDED else WearAfterEndAnswer.NO_ANSWER
    } catch (e: IOException) {
        Timber.v("WearBroadcastAfterEndProbe: LAN endpoint silent after the end - %s", e.javaClass.simpleName)
        WearAfterEndAnswer.NO_ANSWER
    }

    /** [authorization] is the whole header line a protected broadcast plays with, or null. */
    fun relay(endpoint: WearBroadcastEndpoint.Relay, authorization: String?): WearAfterEndAnswer = try {
        connector.connect(endpoint.host, endpoint.port, endpoint.certFingerprint, readTimeoutMs = STATUS_TIMEOUT_MS)
            .use { connection ->
                connection.output.apply {
                    write(relayRequest(endpoint, authorization).toByteArray(Charsets.US_ASCII))
                    flush()
                }
                val status = statusOf(statusLine(connection.input))
                answerOf(status, answered = status != null)
            }
    } catch (e: IOException) {
        Timber.v("WearBroadcastAfterEndProbe: the relay did not answer after the end - %s", e.javaClass.simpleName)
        WearAfterEndAnswer.NO_ANSWER
    }

    private fun answerOf(status: Int?, answered: Boolean): WearAfterEndAnswer = when {
        status == HTTP_NOT_FOUND || status == HTTP_GONE -> WearAfterEndAnswer.ENDED
        answered -> WearAfterEndAnswer.ALIVE
        else -> WearAfterEndAnswer.NO_ANSWER
    }

    private fun lanRequest(endpoint: WearBroadcastEndpoint.Lan): String {
        val uri = runCatching { URI(endpoint.url) }.getOrNull()
        return if (uri?.scheme.equals(SCHEME_RTSP, ignoreCase = true)) {
            "DESCRIBE ${endpoint.url} RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\n\r\n"
        } else {
            val path = uri?.rawPath.orEmpty().ifEmpty { "/" } + uri?.rawQuery?.let { "?$it" }.orEmpty()
            "GET $path HTTP/1.1\r\nHost: ${uri?.rawAuthority ?: endpoint.host}\r\nConnection: close\r\n\r\n"
        }
    }

    private fun relayRequest(endpoint: WearBroadcastEndpoint.Relay, authorization: String?): String {
        val host = if (endpoint.port == HTTPS_PORT) endpoint.host else "${endpoint.host}:${endpoint.port}"
        val auth = authorization?.let { "$it\r\n" }.orEmpty()
        return "GET ${endpoint.path} HTTP/1.1\r\nHost: $host\r\n${auth}Connection: close\r\n\r\n"
    }

    // `HTTP/1.1 410 Gone` and `RTSP/1.0 404 Not Found` alike: the second token is the status.
    private fun statusOf(line: String): Int? = line.split(' ', limit = STATUS_PARTS).getOrNull(1)?.toIntOrNull()

    // Bounded byte by byte: a peer that starts streaming at once must not hold the probe past the status line.
    private fun statusLine(input: InputStream): String {
        val line = StringBuilder()
        while (line.length < MAX_STATUS_LINE) {
            val next = input.read()
            if (next < 0 || next == LINE_FEED) break
            line.append(next.toChar())
        }
        return line.toString().trimEnd('\r')
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 1_500
        const val STATUS_TIMEOUT_MS = 3_000
        const val MAX_STATUS_LINE = 64
        const val LINE_FEED = '\n'.code
        const val STATUS_PARTS = 3
        const val HTTPS_PORT = 443
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
        const val SCHEME_RTSP = "rtsp"
        const val REFUSED = "refused"
    }
}
