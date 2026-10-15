package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox

/**
 * The guest devices each grant of a member account is sealed to (contract DEVICE-EXCHANGE 27 item DJ): `grants` of
 * the member's `directory`, each with `devices` - the guest account's devices and their public keys, which the
 * server already holds. A guest device without a valid key is no recipient.
 */
object ExchangeGrantAudiences {

    private const val KEY_GRANTS = "grants"
    private const val KEY_UPSERTS = "upserts"
    private const val KEY_GRANT_ID = "grantId"
    private const val KEY_DEVICES = "devices"
    private const val KEY_DEVICE_ID = "deviceId"

    /**
     * The audience of every grant [members] lists - a `directory`, or a `changed` that carries the grants again,
     * top-level or under `upserts`. Null when the envelope says nothing about grants, so the held audience stays.
     */
    fun of(members: JsonObject): Map<String, List<E2eRecipient>>? {
        val grants = members.get(KEY_GRANTS) as? JsonArray
            ?: (members.get(KEY_UPSERTS) as? JsonObject)?.get(KEY_GRANTS) as? JsonArray
        return grants?.mapNotNull { element ->
            val grant = element as? JsonObject
            val grantId = grant?.get(KEY_GRANT_ID)?.takeIf { it.isJsonPrimitive }?.asString
            grantId?.let { it to devicesOf(grant.get(KEY_DEVICES) as? JsonArray) }
        }?.toMap()
    }

    private fun devicesOf(devices: JsonArray?): List<E2eRecipient> =
        devices.orEmpty().mapNotNull { element ->
            val device = element as? JsonObject
            val deviceId = device?.get(KEY_DEVICE_ID)?.takeIf { it.isJsonPrimitive }?.asString
            val key = E2eDeviceKey.kemOf(device?.get(E2eDeviceKey.KEY_E2E_KEY))?.let(E2eDeviceKey::decodeKem)
            if (deviceId == null || key == null) null else E2eRecipient(deviceId, key)
        }.take(E2eSealedBox.MAX_RECIPIENTS)

    private fun JsonArray?.orEmpty(): List<JsonElement> = this?.toList() ?: emptyList()

    /** A stable name of who each grant's box goes to, so a change of audience is noticed and nothing else. */
    fun keyOf(audience: Map<String, List<E2eRecipient>>): String =
        audience.toSortedMap().entries.joinToString(";") { (grantId, devices) ->
            grantId + "=" + devices.sortedBy { it.deviceId }.joinToString(",") { it.deviceId }
        }
}
