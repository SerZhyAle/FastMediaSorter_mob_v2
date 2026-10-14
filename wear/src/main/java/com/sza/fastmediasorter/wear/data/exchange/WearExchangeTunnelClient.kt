package com.sza.fastmediasorter.wear.data.exchange

import android.net.Network
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4228: the exchange server has no live path to the resource (unknown, offline or not shared with this device -
 * contract DEVICE-EXCHANGE 7.5 answers them alike), or it closed the stream before answering. Kept apart from
 * transport, authentication and host-key failures, so the folder reads as unavailable rather than broken.
 */
class WearExchangeTunnelUnavailableException(val reason: String?) :
    IOException("exchange tunnel unavailable: ${reason ?: "no answer"}")

/**
 * S4228: a tunnel to a resource of the watch's own account (contract DEVICE-EXCHANGE 7.5, 23 item CG): `hello` on
 * the entry, `connect {resourceId}`, and the same stream as raw bytes once the server says `connected`. Everything
 * carried over it - the host-key pin, authentication, the root proof - is the SFTP session's, unchanged.
 */
@Singleton
class WearExchangeTunnelClient @Inject constructor(
    private val opener: WearExchangeSessionOpener,
) {

    /**
     * Blocks on the network and belongs on an IO dispatcher. [network] binds the stream to the watch's own Wi-Fi or
     * LTE; null keeps the platform route.
     *
     * @throws WearExchangeTunnelUnavailableException when the server refuses or answers anything but `connected`.
     * @throws IOException when the entry cannot open an authenticated stream.
     */
    suspend fun open(entry: WearExchangeEntry, resourceId: String, network: Network? = null): WearExchangeConnection {
        val session = opener.open(entry, keepaliveSeconds = null, readTimeoutMs = ANSWER_TIMEOUT_MS, network = network)
        val answer = try {
            val request = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CONNECT) {
                addProperty(KEY_RESOURCE_ID, resourceId)
            }
            session.send(request)
            awaitAnswer(session)
        } catch (e: IOException) {
            session.close()
            throw e
        }
        if (answer?.type != WearExchangeEnvelope.TYPE_CONNECTED) {
            session.close()
            throw WearExchangeTunnelUnavailableException(answer?.string(WearExchangeEnvelope.KEY_REASON))
        }
        // From here the stream is SSH; its reads wait as long as the session above decides.
        session.connection.socket.soTimeout = WearExchangeConnector.NO_READ_TIMEOUT
        return session.connection
    }

    // A push meant for another stream of this device may arrive first; only the answer to `connect` counts.
    private fun awaitAnswer(session: WearExchangeSession): WearExchangeEnvelope? {
        var answer = session.receive()
        var skipped = 0
        while (answer != null && answer.type !in ANSWERS && skipped < MAX_UNRELATED_ENVELOPES) {
            answer = session.receive()
            skipped++
        }
        return answer
    }

    companion object {
        /** The tunnel window of contract 20 item BB. */
        const val ANSWER_TIMEOUT_MS = 10_000

        private const val KEY_RESOURCE_ID = "resourceId"
        private const val MAX_UNRELATED_ENVELOPES = 8
        private val ANSWERS = setOf(WearExchangeEnvelope.TYPE_CONNECTED, WearExchangeEnvelope.TYPE_REFUSED)
    }
}
