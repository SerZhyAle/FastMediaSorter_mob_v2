package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2ePeer
import java.time.Instant

/** A device of the account as the directory lists it (contract DEVICE-EXCHANGE 6.1), reduced to what the list shows. */
data class WearDirectoryDevice(
    val deviceId: String,
    val name: String?,
    val product: String?,
    val isOnline: Boolean,
    /** S4240: `kem` of the device's `e2eKey` (27 item DF), null when it has none or one of an unknown version. */
    val kem: String? = null,
    /** The device declared `e2e-v1`. */
    val sealsCapable: Boolean = false,
    val updatedAtMs: Long = 0L,
) {
    /** The device as the shared trust rules see it (27 item DG). */
    fun peer(): E2ePeer = E2ePeer(deviceId, kem, sealsCapable, updatedAtMs)
}

/** The user and password of a protected broadcast's record (6.3); never printed, never persisted. */
class WearBroadcastCredentials(val user: String, val password: String) {

    override fun toString(): String = "WearBroadcastCredentials(user=***, password=***)"
}

/** A live broadcast record (6.3) with its descriptor kept as received, so the endpoint chooser reads it once. */
data class WearDirectoryBroadcast(
    val broadcastId: String,
    val deviceId: String,
    val title: String?,
    val mode: String?,
    val descriptor: JsonObject?,
    val credentials: WearBroadcastCredentials?,
    /** S4240: the record's box over the relay group key (27 item DK), opened only when the broadcast is played. */
    val sealed: JsonObject? = null,
) {

    // DEVICE-EXCHANGE 7.9: a broadcast id is a capability and a log names it by a hash only, so none prints it.
    override fun toString(): String = "WearDirectoryBroadcast(mode=$mode, protected=${credentials != null})"
}

/**
 * The directory a subscribed client holds (contract DEVICE-EXCHANGE 7.4): the last `directory` answer with every
 * `changed` push applied in revision order. A push that skips a revision cannot be applied safely, so it asks the
 * caller to send `list` again instead; the phone's `DeviceExchangeDirectoryState` makes the same rule and shares no
 * code with this one.
 */
class WearExchangeDirectoryState private constructor(
    val revision: Long,
    val devices: Map<String, WearDirectoryDevice>,
    val broadcasts: Map<String, WearDirectoryBroadcast>,
) {

    sealed interface Outcome {
        data class Applied(val state: WearExchangeDirectoryState) : Outcome

        /** The push is not newer than the held revision, e.g. one that crossed a fresh `directory` answer. */
        data object Stale : Outcome

        /** A revision was skipped: the caller sends `list` and replaces the state with its answer. */
        data object Gap : Outcome
    }

    fun apply(changed: WearExchangeEnvelope): Outcome {
        val incoming = changed.long(KEY_REVISION) ?: 0L
        return when {
            incoming <= revision -> Outcome.Stale
            incoming != revision + 1 -> Outcome.Gap
            else -> Outcome.Applied(merged(changed, incoming))
        }
    }

    // A removed device takes its broadcasts with it, whether or not the push names them (a broadcast ends with its
    // device, 7.3).
    private fun merged(changed: WearExchangeEnvelope, incoming: Long): WearExchangeDirectoryState {
        val upserts = changed.obj(KEY_UPSERTS) ?: JsonObject()
        val removals = changed.obj(KEY_REMOVALS) ?: JsonObject()
        val goneDevices = removals.ids(KEY_DEVICE_IDS)
        val goneBroadcasts = removals.ids(KEY_BROADCAST_IDS)
        val nextDevices = (devices + upserts.records(KEY_DEVICES).mapNotNull(::device).associateBy { it.deviceId })
            .filterKeys { it !in goneDevices }
        val nextBroadcasts = (
            broadcasts + upserts.records(KEY_BROADCASTS).mapNotNull(::broadcast).associateBy { it.broadcastId }
            ).filter { (id, record) -> id !in goneBroadcasts && record.deviceId !in goneDevices }
        return WearExchangeDirectoryState(incoming, nextDevices, nextBroadcasts)
    }

    companion object {
        val EMPTY = WearExchangeDirectoryState(0L, emptyMap(), emptyMap())

        private const val KEY_REVISION = "revision"
        private const val KEY_DEVICES = "devices"
        private const val KEY_BROADCASTS = "broadcasts"
        private const val KEY_UPSERTS = "upserts"
        private const val KEY_REMOVALS = "removals"
        private const val KEY_DEVICE_IDS = "deviceIds"
        private const val KEY_BROADCAST_IDS = "broadcastIds"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_DEVICE_NAME = "deviceName"
        private const val KEY_PRODUCT = "product"
        private const val KEY_PRESENCE = "presence"
        private const val KEY_CAPABILITIES = "capabilities"
        private const val KEY_UPDATED_AT = "updatedAt"
        private const val PRESENCE_ONLINE = "online"
        private const val KEY_BROADCAST_ID = "broadcastId"
        private const val KEY_TITLE = "title"
        private const val KEY_MODE = "mode"
        private const val KEY_DESCRIPTOR = "descriptor"
        private const val KEY_CREDENTIALS = "credentials"
        private const val KEY_SEALED = "sealed"
        private const val KEY_USER = "user"
        private const val KEY_PASSWORD = "password"

        /** Replaces the held state with a `directory` answer; a record that does not read is dropped alone. */
        fun of(directory: WearExchangeEnvelope): WearExchangeDirectoryState {
            val devices = directory.records(KEY_DEVICES).mapNotNull(::device).associateBy { it.deviceId }
            val broadcasts = directory.records(KEY_BROADCASTS).mapNotNull(::broadcast).associateBy { it.broadcastId }
            return WearExchangeDirectoryState(directory.long(KEY_REVISION) ?: 0L, devices, broadcasts)
        }

        private fun WearExchangeEnvelope.records(key: String): List<JsonObject> =
            array(key)?.toList().orEmpty().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)

        private fun JsonObject.records(key: String): List<JsonObject> =
            elements(key).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)

        private fun JsonObject.elements(key: String): List<JsonElement> =
            get(key)?.takeIf(JsonElement::isJsonArray)?.asJsonArray?.toList().orEmpty()

        private fun JsonObject.ids(key: String): Set<String> =
            elements(key).mapNotNull { it.takeIf(JsonElement::isJsonPrimitive)?.asString }.toSet()

        private fun JsonObject.text(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf(String::isNotEmpty)

        private fun device(record: JsonObject): WearDirectoryDevice? {
            val id = record.text(KEY_DEVICE_ID) ?: return null
            return WearDirectoryDevice(
                deviceId = id,
                name = record.text(KEY_DEVICE_NAME),
                product = record.text(KEY_PRODUCT),
                isOnline = record.text(KEY_PRESENCE) == PRESENCE_ONLINE,
                kem = E2eDeviceKey.kemOf(record.get(E2eDeviceKey.KEY_E2E_KEY)),
                sealsCapable = record.ids(KEY_CAPABILITIES).contains(E2eDeviceKey.CAPABILITY),
                updatedAtMs = record.text(KEY_UPDATED_AT)?.let(::instantMs) ?: 0L,
            )
        }

        private fun instantMs(text: String): Long? =
            runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()

        private fun broadcast(record: JsonObject): WearDirectoryBroadcast? {
            val id = record.text(KEY_BROADCAST_ID)
            val deviceId = record.text(KEY_DEVICE_ID)
            val descriptor = record.get(KEY_DESCRIPTOR)?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            return if (id == null || deviceId == null) {
                null
            } else {
                WearDirectoryBroadcast(
                    broadcastId = id,
                    deviceId = deviceId,
                    title = record.text(KEY_TITLE) ?: descriptor?.text(KEY_TITLE),
                    mode = record.text(KEY_MODE) ?: descriptor?.text(KEY_MODE),
                    descriptor = descriptor,
                    credentials = credentials(record),
                    sealed = record.get(KEY_SEALED)?.takeIf(JsonElement::isJsonObject)?.asJsonObject,
                )
            }
        }

        private fun credentials(record: JsonObject): WearBroadcastCredentials? {
            val pair = record.get(KEY_CREDENTIALS)?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            val user = pair?.text(KEY_USER)
            val password = pair?.text(KEY_PASSWORD)
            return if (user != null && password != null) WearBroadcastCredentials(user, password) else null
        }
    }
}
