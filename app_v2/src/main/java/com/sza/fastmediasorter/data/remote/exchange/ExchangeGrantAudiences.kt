package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eRecipients
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox

/**
 * The guest devices each grant of a member account is sealed to (contract DEVICE-EXCHANGE 27 item DJ): `grants` of
 * the member's `directory`, each with `devices` - the guest account's devices and their public keys, which the
 * server already holds. A guest device without a valid key is no recipient.
 */
object ExchangeGrantAudiences {

    private const val KEY_GRANTS = "grants"
    private const val KEY_UPSERTS = "upserts"
    private const val KEY_REMOVALS = "removals"
    private const val KEY_GRANT_IDS = "grantIds"
    private const val KEY_GRANT_ID = "grantId"
    private const val KEY_DEVICES = "devices"
    private const val KEY_DEVICE_ID = "deviceId"

    /**
     * The audience a whole `directory` states. A member account that gave nothing carries no `grants` (item BD), and
     * that is an empty audience, not an unknown one: a grant missing from it gets the registering box of item DJ.
     */
    fun whole(members: JsonObject): Map<String, List<E2eRecipient>> = listed(members.get(KEY_GRANTS) as? JsonArray)

    /**
     * [held] after an applied `changed`, which carries only the grants it adds or changes and the ids it takes out
     * (7.4). Null while no `directory` has stated the whole audience yet.
     */
    fun changed(held: Map<String, List<E2eRecipient>>?, members: JsonObject): Map<String, List<E2eRecipient>>? {
        val upserts = (members.get(KEY_UPSERTS) as? JsonObject)?.get(KEY_GRANTS) as? JsonArray
        val removed = ((members.get(KEY_REMOVALS) as? JsonObject)?.get(KEY_GRANT_IDS) as? JsonArray).orEmpty()
            .mapNotNull { id -> id.takeIf { it.isJsonPrimitive }?.asString }
        return when {
            held == null || upserts == null && removed.isEmpty() -> held
            else -> held - removed.toSet() + listed(upserts)
        }
    }

    private fun listed(grants: JsonArray?): Map<String, List<E2eRecipient>> =
        grants.orEmpty().mapNotNull { element ->
            val grant = element as? JsonObject
            val grantId = grant?.get(KEY_GRANT_ID)?.takeIf { it.isJsonPrimitive }?.asString
            grantId?.let { it to devicesOf(grant.get(KEY_DEVICES) as? JsonArray) }
        }.toMap()

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
            grantId + "=" + E2eRecipients.keyOf(devices)
        }
}
