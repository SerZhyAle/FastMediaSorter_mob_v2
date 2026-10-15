package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import timber.log.Timber
import java.net.URI
import java.net.URISyntaxException

/**
 * The one exchange endpoint the watch plays (contract DEVICE-EXCHANGE 7.7): `RELAY`, `AUDIO_ONLY`, an `https` URL
 * with the path `/v2/b/<broadcastId>/stream`. [certFingerprint] pins a self-signed deployment; without it the
 * platform chain decides, as for any descriptor holder (item D).
 */
data class WearExchangeRelayEndpoint(
    val broadcastId: String,
    val host: String,
    val port: Int,
    val path: String,
    val certFingerprint: String?,
    val title: String?,
    /** S4240, 27 item DK: the group key (base64url) of a sealed relay, played only through the opening proxy. */
    val groupKey: String? = null,
) {

    /** What the player is given: the same path on the pinning proxy, which carries it inside TLS unchanged. */
    fun loopbackUrl(proxyPort: Int): String = "http://$LOOPBACK_HOST:$proxyPort$path"

    // DIAGNOSTIC-REPORT section 10 and 7.9: neither the server address nor the broadcast id reaches a log, and a
    // group key opens the broadcast.
    override fun toString(): String =
        "WearExchangeRelayEndpoint(pinned=${certFingerprint != null}, sealed=${groupKey != null})"

    companion object {
        const val LOOPBACK_HOST = "127.0.0.1"

        private const val HTTPS = "https"
        private const val HTTPS_PORT = 443
        private const val KEY_BROADCAST = "broadcast"
        private const val KEY_BROADCAST_ID = "broadcastId"
        private const val KEY_DESCRIPTOR = "descriptor"
        private const val KEY_SEALED = "sealed"
        private const val KEY_ENDPOINTS = "endpoints"
        private const val KEY_TRANSPORT = "transport"
        private const val KEY_MODE = "mode"
        private const val KEY_URL = "url"
        private const val KEY_TITLE = "title"
        private const val KEY_CERT_FINGERPRINT = "certFingerprint"
        private const val MODE_AUDIO_ONLY = "AUDIO_ONLY"

        /** The broadcast id a `cast-offer` names, from the envelope or its record. */
        fun broadcastIdOf(offer: WearExchangeEnvelope): String? =
            offer.string(KEY_BROADCAST_ID) ?: offer.obj(KEY_BROADCAST)?.text(KEY_BROADCAST_ID)

        /** The record's descriptor of a `cast-offer` as sent, its sealed endpoints keyless. */
        fun descriptorOf(offer: WearExchangeEnvelope): JsonObject? =
            offer.obj(KEY_BROADCAST)?.get(KEY_DESCRIPTOR)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

        /** The record's box of a `cast-offer` (27 item DK), or null for a clear one. */
        fun boxOf(offer: WearExchangeEnvelope): JsonObject? =
            offer.obj(KEY_BROADCAST)?.get(KEY_SEALED)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

        /**
         * The playable endpoint of a `cast-offer`, or null when it carries none the watch can play. [descriptor] is
         * the record's descriptor with the box's keys written in; a sealed relay without its key is not playable.
         */
        fun fromOffer(
            offer: WearExchangeEnvelope,
            descriptor: JsonObject? = descriptorOf(offer),
        ): WearExchangeRelayEndpoint? {
            val record = offer.obj(KEY_BROADCAST)
            val broadcastId = broadcastIdOf(offer)
            val endpoint = descriptor?.relayEndpoint()
            val title = record?.text(KEY_TITLE) ?: descriptor?.text(KEY_TITLE)
            val seal = endpoint?.let(WearBroadcastEndpoints::relaySeal)
            val playable = seal is WearRelaySeal.Clear || seal is WearRelaySeal.Keyed
            return if (broadcastId == null || endpoint == null || !playable) {
                null
            } else {
                parse(broadcastId, endpoint, title)?.copy(groupKey = (seal as? WearRelaySeal.Keyed)?.groupKey)
            }
        }

        private fun JsonObject.relayEndpoint(): JsonObject? {
            val fallbackMode = text(KEY_MODE) ?: MODE_AUDIO_ONLY
            val endpoints = get(KEY_ENDPOINTS)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: JsonArray()
            return endpoints.asSequence()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .firstOrNull { endpoint ->
                    endpoint.text(KEY_TRANSPORT).equals(WearExchangeBroadcastRecords.TRANSPORT_RELAY, true) &&
                        (endpoint.text(KEY_MODE) ?: fallbackMode).equals(MODE_AUDIO_ONLY, true)
                }
        }

        private fun parse(broadcastId: String, endpoint: JsonObject, title: String?): WearExchangeRelayEndpoint? {
            val uri = try {
                endpoint.text(KEY_URL)?.let(::URI)
            } catch (e: URISyntaxException) {
                Timber.w(e, "WearExchangeRelayEndpoint: the RELAY url does not parse, the offer is unplayable")
                null
            }
            return uri?.takeIf { isRelayStream(it, broadcastId) }?.let { relay ->
                WearExchangeRelayEndpoint(
                    broadcastId = broadcastId,
                    host = relay.host,
                    port = relay.port.takeIf { it > 0 } ?: HTTPS_PORT,
                    path = relay.rawPath,
                    certFingerprint = endpoint.text(KEY_CERT_FINGERPRINT),
                    title = title,
                )
            }
        }

        private fun isRelayStream(uri: URI, broadcastId: String): Boolean =
            uri.scheme.equals(HTTPS, true) && !uri.host.isNullOrEmpty() && uri.rawPath == "/v2/b/$broadcastId/stream"

        private fun JsonObject.text(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    }
}
