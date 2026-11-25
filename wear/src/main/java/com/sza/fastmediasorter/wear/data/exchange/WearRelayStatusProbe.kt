package com.sza.fastmediasorter.wear.data.exchange

import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** S4692: the HTTP status the relay answers for a broadcast, or null when the server did not answer. */
interface WearRelayStatusProbe {

    fun status(endpoint: WearBroadcastEndpoint.Relay): Int?
}

/**
 * LIVE-BROADCAST item S: a `GET` of the relay URL over the pinned exchange connector that reads the status line and
 * closes at once, so waiting for a pending broadcast spends no listener slot of the producer (consumer rule 5).
 */
@Singleton
class PinnedWearRelayStatusProbe @Inject constructor(
    private val connector: WearExchangeConnector,
) : WearRelayStatusProbe {

    override fun status(endpoint: WearBroadcastEndpoint.Relay): Int? = try {
        connector.connect(endpoint.host, endpoint.port, endpoint.certFingerprint, readTimeoutMs = READ_TIMEOUT_MS)
            .use { connection ->
                connection.output.apply {
                    write(request(endpoint).toByteArray(Charsets.US_ASCII))
                    flush()
                }
                statusLine(connection.input).split(' ', limit = STATUS_PARTS).getOrNull(1)?.toIntOrNull()
            }
    } catch (e: IOException) {
        // DEVICE-EXCHANGE 7.9: neither the server address nor the broadcast id reaches a log.
        Timber.v("PinnedWearRelayStatusProbe: the relay did not answer - %s", e.javaClass.simpleName)
        null
    }

    private fun request(endpoint: WearBroadcastEndpoint.Relay): String {
        val host = if (endpoint.port == HTTPS_PORT) endpoint.host else "${endpoint.host}:${endpoint.port}"
        return "GET ${endpoint.path} HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n"
    }

    // Bounded byte by byte: a relay that starts streaming at once must not hold the probe past the status line.
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
        const val READ_TIMEOUT_MS = 5_000
        const val HTTPS_PORT = 443
        const val MAX_STATUS_LINE = 64
        const val LINE_FEED = '\n'.code
        const val STATUS_PARTS = 3
    }
}
