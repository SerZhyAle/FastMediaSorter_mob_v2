package com.sza.fastmediasorter.wear.data.exchange

import java.io.Closeable
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What `welcome` tells the watch (contract DEVICE-EXCHANGE 7.2); a member the server left out stays null. */
data class WearExchangeWelcome(
    val publicEndpoint: String?,
    val keepaliveSeconds: Int?,
    val features: List<String>,
) {
    companion object {
        private const val KEY_PUBLIC_ENDPOINT = "publicEndpoint"
        private const val KEY_KEEPALIVE_SECONDS = "keepaliveSeconds"
        private const val KEY_FEATURES = "features"

        fun from(envelope: WearExchangeEnvelope): WearExchangeWelcome {
            val features = envelope.members.get(KEY_FEATURES)?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString }
                .orEmpty()
            return WearExchangeWelcome(
                publicEndpoint = envelope.string(KEY_PUBLIC_ENDPOINT),
                keepaliveSeconds = envelope.int(KEY_KEEPALIVE_SECONDS),
                features = features,
            )
        }
    }
}

/**
 * One authenticated stream: `hello` was answered with `welcome`, so both directions take the authenticated frame
 * limit. [send] is serialized because a control stream is written by the keepalive loop and by the announcer.
 */
class WearExchangeSession(
    val connection: WearExchangeConnection,
    val welcome: WearExchangeWelcome,
) : Closeable {

    @Synchronized
    fun send(envelope: WearExchangeEnvelope) {
        WearExchangeFrameCodec.write(connection.output, envelope, WearExchangeFrameCodec.AUTHENTICATED_MAX_FRAME_BODY)
    }

    /** The next envelope, or null when the server closed the stream cleanly. */
    fun receive(): WearExchangeEnvelope? =
        WearExchangeFrameCodec.read(connection.input, WearExchangeFrameCodec.AUTHENTICATED_MAX_FRAME_BODY)

    override fun close() = connection.close()
}

/** The entry holds no token: it was never enrolled, or its token was forgotten. */
class WearExchangeNotEnrolledException : IOException("exchange entry holds no device token")

/** The server answered `hello` with `refused`; [reason] is a contract 7.10 reason or `unknown`. */
class WearExchangeRefusedException(val reason: String) : IOException("exchange server refused: $reason")

/**
 * Opens an authenticated stream for an entry: the entry's own pin, its stored token, `hello`, and `welcome`.
 * Blocks on the network and belongs on an IO dispatcher.
 */
@Singleton
class WearExchangeSessionOpener @Inject constructor(
    private val connector: WearExchangeConnector,
    private val store: WearExchangeEntryStore,
    private val profile: WearExchangeDeviceProfile,
) {

    /**
     * [keepaliveSeconds] is proposed only by a control stream (7.3); a data stream leaves it out.
     *
     * @throws WearExchangeNotEnrolledException when the entry holds no token.
     * @throws WearExchangeRefusedException when the server refuses the token.
     * @throws IOException when the server cannot be reached or the answer is not `welcome`.
     */
    suspend fun open(entry: WearExchangeEntry, keepaliveSeconds: Int?, readTimeoutMs: Int): WearExchangeSession {
        val token = store.token(entry.identity) ?: throw WearExchangeNotEnrolledException()
        val connection = connector.connect(entry.host, entry.port, entry.pinnedCertificate, readTimeoutMs)
        try {
            WearExchangeFrameCodec.write(connection.output, hello(entry, token, keepaliveSeconds))
            return WearExchangeSession(connection, welcomeOf(WearExchangeFrameCodec.read(connection.input)))
        } catch (e: IOException) {
            connection.close()
            throw e
        }
    }

    private fun welcomeOf(answer: WearExchangeEnvelope?): WearExchangeWelcome = when (answer?.type) {
        WearExchangeEnvelope.TYPE_WELCOME -> WearExchangeWelcome.from(answer)
        WearExchangeEnvelope.TYPE_REFUSED -> throw WearExchangeRefusedException(
            answer.string(WearExchangeEnvelope.KEY_REASON) ?: WearExchangeEnrollResult.REASON_UNKNOWN
        )
        else -> throw WearExchangeWireException("hello was not answered with welcome")
    }

    private fun hello(entry: WearExchangeEntry, token: String, keepaliveSeconds: Int?) =
        WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_HELLO) {
            addProperty(KEY_DEVICE_ID, entry.deviceId)
            addProperty(KEY_DEVICE_TOKEN, token)
            addProperty(KEY_PRODUCT_VERSION, profile.productVersion)
            keepaliveSeconds?.let { addProperty(KEY_KEEPALIVE_SECONDS, it) }
        }

    private companion object {
        const val KEY_DEVICE_ID = "deviceId"
        const val KEY_DEVICE_TOKEN = "deviceToken"
        const val KEY_PRODUCT_VERSION = "productVersion"
        const val KEY_KEEPALIVE_SECONDS = "keepaliveSeconds"
    }
}
