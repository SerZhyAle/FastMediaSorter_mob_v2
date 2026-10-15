package com.sza.fastmediasorter.data.security.e2e

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * The `seal` member of a broadcast descriptor's `RELAY` and `TUNNEL` endpoints (contract DEVICE-EXCHANGE 27 item
 * DK), shared by the phone and the watch. A record carries `seal: {v}` and the keys in its box; a descriptor handed
 * by hand carries them in the endpoints. Writing the opened keys into the endpoints makes both one form.
 */
object E2eBroadcastSeals {

    const val VERSION = 1
    const val KEY_SEAL = "seal"
    const val KEY_V = "v"
    const val KEY_KEY = "key"
    const val KEY_PIN = "pin"
    const val KEY_RELAY_KEY = "relayKey"
    const val KEY_TUNNEL_PIN = "tunnelPin"
    const val KEY_CREDENTIALS = "credentials"
    private const val KEY_ENDPOINTS = "endpoints"
    private const val KEY_TRANSPORT = "transport"
    private const val TRANSPORT_RELAY = "RELAY"
    private const val TRANSPORT_TUNNEL = "TUNNEL"

    /** Writes the opened box [payload]'s keys into [descriptor]'s sealed endpoints, in place; null writes none. */
    fun withKeys(descriptor: JsonObject, payload: JsonObject?) {
        val relayKey = payload?.get(KEY_RELAY_KEY)?.takeIf { it.isJsonPrimitive }?.asString
        val tunnelPin = payload?.get(KEY_TUNNEL_PIN)?.takeIf { it.isJsonPrimitive }?.asString
        endpoints(descriptor).forEach { endpoint ->
            val seal = endpoint.get(KEY_SEAL) as? JsonObject ?: return@forEach
            when (endpoint.get(KEY_TRANSPORT)?.takeIf { it.isJsonPrimitive }?.asString?.uppercase()) {
                TRANSPORT_RELAY -> relayKey?.let { seal.addProperty(KEY_KEY, it) }
                TRANSPORT_TUNNEL -> tunnelPin?.let { seal.addProperty(KEY_PIN, it) }
            }
        }
    }

    /** Removes every key from [descriptor]'s endpoints, in place - what a record put on the server may carry. */
    fun withoutKeys(descriptor: JsonObject) {
        endpoints(descriptor).forEach { endpoint ->
            (endpoint.get(KEY_SEAL) as? JsonObject)?.let { seal ->
                seal.remove(KEY_KEY)
                seal.remove(KEY_PIN)
            }
        }
    }

    private fun endpoints(descriptor: JsonObject): List<JsonObject> =
        (descriptor.get(KEY_ENDPOINTS) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}
