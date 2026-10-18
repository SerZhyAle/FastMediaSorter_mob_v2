package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eBroadcastSeals
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
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
        val broadcastId: String = "",
        /** S4240, 27 item DK: the group key (base64url) of a sealed relay, played only through the opening proxy. */
        val groupKey: String? = null,
    ) : WearBroadcastEndpoint {

        // 7.9: neither the server address nor the broadcast id reaches a log, and a group key opens the broadcast.
        override fun toString(): String =
            "Relay(pinned=${certFingerprint != null}, mode=$mode, sealed=${groupKey != null})"
    }
}

/** S4240: what an endpoint's `seal` (contract DEVICE-EXCHANGE 27 item DK) lets the watch do with it. */
sealed interface WearRelaySeal {
    data object Clear : WearRelaySeal

    class Keyed(val groupKey: String) : WearRelaySeal {
        override fun toString(): String = "Keyed(***)"
    }

    /** A known version whose key still rides the record's box: playable once the box opens, never before. */
    data object Pending : WearRelaySeal

    /** An unknown version or a malformed key: never played, and never as clear bytes. */
    data object Unplayable : WearRelaySeal
}

/**
 * Reads the endpoints of a directory record (contract DEVICE-EXCHANGE 6.3) into the ones the watch plays. `TUNNEL`
 * and `P2P` are dropped: the watch declares neither, and a consumer plays only what it implements (item CE).
 */
object WearBroadcastEndpoints {

    const val SUPPORTED_SCHEMA_VERSION = 1
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
    private const val KEY_SCHEMA_VERSION = "schemaVersion"
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

    /** The schemaVersion of [descriptor], or [SUPPORTED_SCHEMA_VERSION] if missing or not an integer. */
    fun schemaVersion(descriptor: JsonObject?): Int =
        descriptor?.get(KEY_SCHEMA_VERSION)?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asInt }.getOrNull() } ?: SUPPORTED_SCHEMA_VERSION

    /** Item M: a descriptor with an `auth` object demands the challenge be answered on every endpoint. */
    fun isProtected(descriptor: JsonObject?): Boolean =
        descriptor?.get(KEY_AUTH)?.takeIf(JsonElement::isJsonObject) != null

    /**
     * The playable endpoints of [descriptor] in the order given; [recordMode] is the fallback of an unmarked one.
     * [boxPending] lists a sealed relay whose key is still in the record's unopened box, for the list row alone:
     * such an endpoint carries no key and is never handed to a player.
     */
    fun parse(
        broadcastId: String,
        descriptor: JsonObject?,
        recordMode: String?,
        boxPending: Boolean = false,
    ): List<WearBroadcastEndpoint> {
        val schema = schemaVersion(descriptor)
        if (schema > SUPPORTED_SCHEMA_VERSION) {
            Timber.w(
                "WearBroadcastEndpoints: LIVE-BROADCAST descriptor schemaVersion %d is unsupported " +
                    "(format-unsupported)",
                schema,
            )
            return emptyList()
        }
        val fallbackMode = descriptor?.text(KEY_MODE) ?: recordMode
        val listed = descriptor?.get(KEY_ENDPOINTS)?.takeIf(JsonElement::isJsonArray)?.asJsonArray?.toList().orEmpty()
            .filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
        val sources = listed.ifEmpty { listOfNotNull(descriptor?.let(::legacyEndpoint)) }
        return sources.mapNotNull { endpoint(broadcastId, it, it.text(KEY_MODE) ?: fallbackMode, boxPending) }
    }

    // A descriptor without an endpoint list is read as one endpoint from its own `url`, like the producer's.
    private fun legacyEndpoint(descriptor: JsonObject): JsonObject? = descriptor.text(KEY_URL)?.let { url ->
        val rtsp = url.startsWith("$SCHEME_RTSP://", ignoreCase = true)
        JsonObject().apply {
            addProperty(KEY_URL, url)
            addProperty(KEY_TRANSPORT, if (rtsp) TRANSPORT_RTSP else TRANSPORT_HTTP)
        }
    }

    private fun endpoint(
        broadcastId: String,
        record: JsonObject,
        mode: String?,
        boxPending: Boolean,
    ): WearBroadcastEndpoint? {
        val uri = parseUri(record.text(KEY_URL))
        return when (record.text(KEY_TRANSPORT)?.uppercase()) {
            TRANSPORT_HTTP, TRANSPORT_RTSP -> uri?.let { lan(it, mode) }
            TRANSPORT_RELAY -> {
                val seal = relaySeal(record)
                val listed = seal is WearRelaySeal.Clear || seal is WearRelaySeal.Keyed ||
                    (boxPending && seal == WearRelaySeal.Pending)
                uri?.takeIf { listed }
                    ?.let { relay(it, broadcastId, record.text(KEY_CERT_FINGERPRINT), mode) }
                    ?.copy(groupKey = (seal as? WearRelaySeal.Keyed)?.groupKey)
            }
            else -> null
        }
    }

    /** The `seal` of [endpoint]: none, a version-1 seal with or without its 32-byte group key, or anything else. */
    fun relaySeal(endpoint: JsonObject): WearRelaySeal {
        val seal = endpoint.get(E2eBroadcastSeals.KEY_SEAL) ?: return WearRelaySeal.Clear
        val members = seal.takeIf(JsonElement::isJsonObject)?.asJsonObject
        val version = members?.get(E2eBroadcastSeals.KEY_V)?.takeIf(JsonElement::isJsonPrimitive)
            ?.let { runCatching { it.asInt }.getOrNull() }
        val text = members?.text(E2eBroadcastSeals.KEY_KEY)
        val key = text?.takeIf {
            runCatching { E2eBytes.fromB64url(it) }.getOrNull()?.size == E2eRelayRecords.GROUP_KEY_LENGTH
        }
        return when {
            version != E2eBroadcastSeals.VERSION -> WearRelaySeal.Unplayable
            key != null -> WearRelaySeal.Keyed(key)
            text == null -> WearRelaySeal.Pending
            else -> WearRelaySeal.Unplayable
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
            WearBroadcastEndpoint.Relay(
                host = uri.host,
                port = uri.port.takeIf { it > 0 } ?: HTTPS_PORT,
                path = uri.rawPath,
                certFingerprint = pin,
                mode = mode,
                broadcastId = broadcastId,
            )
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
