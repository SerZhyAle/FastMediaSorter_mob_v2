package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import timber.log.Timber
import java.net.URI
import java.net.URISyntaxException

/** One endpoint of a broadcast descriptor the watch can reach, in the producer's order (contract 7.7). */
sealed interface WearBroadcastEndpoint {

    val mode: String?

    /** A LAN `HTTP` or `RTSP` endpoint, played directly where the watch shares the producer's network. */
    data class Lan(val url: String, val host: String, val port: Int, override val mode: String?) : WearBroadcastEndpoint

    /** The server's relay: an `https` stream at `/v2/b/<broadcastId>/stream`, pinned by [certFingerprint]. */
    data class Relay(
        val host: String,
        val port: Int,
        val path: String,
        val certFingerprint: String?,
        override val mode: String?,
    ) : WearBroadcastEndpoint {

        // 7.9: neither the server address nor the broadcast id reaches a log.
        override fun toString(): String = "Relay(pinned=${certFingerprint != null}, mode=$mode)"
    }
}

/**
 * Reads the endpoints of a directory record (contract DEVICE-EXCHANGE 6.3) into the ones the watch plays. `TUNNEL`
 * and `P2P` are dropped: the watch declares neither, and a consumer plays only what it implements (item CE).
 */
object WearBroadcastEndpoints {

    const val MODE_AUDIO_ONLY = "AUDIO_ONLY"
    const val MODE_VIDEO_AUDIO = "VIDEO_AUDIO"
    const val MODE_VIDEO_ONLY = "VIDEO_ONLY"

    private const val TRANSPORT_HTTP = "HTTP"
    private const val TRANSPORT_RTSP = "RTSP"
    private const val TRANSPORT_RELAY = "RELAY"
    private const val SCHEME_HTTPS = "https"
    private const val SCHEME_RTSP = "rtsp"
    private const val HTTPS_PORT = 443
    private const val HTTP_PORT = 80
    private const val RTSP_PORT = 554
    private const val KEY_ENDPOINTS = "endpoints"
    private const val KEY_TRANSPORT = "transport"
    private const val KEY_MODE = "mode"
    private const val KEY_URL = "url"
    private const val KEY_AUTH = "auth"
    private const val KEY_CERT_FINGERPRINT = "certFingerprint"

    private val playableModes = setOf(MODE_AUDIO_ONLY, MODE_VIDEO_AUDIO, MODE_VIDEO_ONLY)

    /** A mode the watch decodes; anything else is shown as unplayable and never started (item CH). */
    fun isPlayableMode(mode: String?): Boolean = mode?.uppercase() in playableModes

    fun isVideo(mode: String?): Boolean = mode?.uppercase().let { it == MODE_VIDEO_AUDIO || it == MODE_VIDEO_ONLY }

    /** Item M: a descriptor with an `auth` object demands the challenge be answered on every endpoint. */
    fun isProtected(descriptor: JsonObject?): Boolean =
        descriptor?.get(KEY_AUTH)?.takeIf(JsonElement::isJsonObject) != null

    /** The playable endpoints of [descriptor] in the order given; [recordMode] is the fallback of an unmarked one. */
    fun parse(broadcastId: String, descriptor: JsonObject?, recordMode: String?): List<WearBroadcastEndpoint> {
        val fallbackMode = descriptor?.text(KEY_MODE) ?: recordMode
        val listed = descriptor?.get(KEY_ENDPOINTS)?.takeIf(JsonElement::isJsonArray)?.asJsonArray?.toList().orEmpty()
            .filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
        val sources = listed.ifEmpty { listOfNotNull(descriptor?.let(::legacyEndpoint)) }
        return sources.mapNotNull { endpoint(broadcastId, it, mode = it.text(KEY_MODE) ?: fallbackMode) }
    }

    // A descriptor without an endpoint list is read as one endpoint from its own `url`, like the producer's.
    private fun legacyEndpoint(descriptor: JsonObject): JsonObject? = descriptor.text(KEY_URL)?.let { url ->
        val rtsp = url.startsWith("$SCHEME_RTSP://", ignoreCase = true)
        JsonObject().apply {
            addProperty(KEY_URL, url)
            addProperty(KEY_TRANSPORT, if (rtsp) TRANSPORT_RTSP else TRANSPORT_HTTP)
        }
    }

    private fun endpoint(broadcastId: String, record: JsonObject, mode: String?): WearBroadcastEndpoint? {
        val uri = parseUri(record.text(KEY_URL))
        return when (record.text(KEY_TRANSPORT)?.uppercase()) {
            TRANSPORT_HTTP, TRANSPORT_RTSP -> uri?.let { lan(it, mode) }
            TRANSPORT_RELAY -> uri?.let { relay(it, broadcastId, record.text(KEY_CERT_FINGERPRINT), mode) }
            else -> null
        }
    }

    private fun lan(uri: URI, mode: String?): WearBroadcastEndpoint.Lan? {
        val host = uri.host?.takeIf(String::isNotEmpty)
        val port = uri.port.takeIf { it > 0 } ?: if (uri.scheme.equals(SCHEME_RTSP, true)) RTSP_PORT else HTTP_PORT
        return host?.let { WearBroadcastEndpoint.Lan(uri.toString(), it, port, mode) }
    }

    private fun relay(uri: URI, broadcastId: String, pin: String?, mode: String?): WearBroadcastEndpoint.Relay? {
        val valid = uri.scheme.equals(SCHEME_HTTPS, true) && !uri.host.isNullOrEmpty() &&
            uri.rawPath == "/v2/b/$broadcastId/stream"
        return if (valid) {
            WearBroadcastEndpoint.Relay(uri.host, uri.port.takeIf { it > 0 } ?: HTTPS_PORT, uri.rawPath, pin, mode)
        } else {
            null
        }
    }

    private fun parseUri(value: String?): URI? = try {
        value?.let(::URI)
    } catch (e: URISyntaxException) {
        Timber.w(e, "WearBroadcastEndpoints: an endpoint url does not parse and is skipped")
        null
    }

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf(String::isNotEmpty)
}
