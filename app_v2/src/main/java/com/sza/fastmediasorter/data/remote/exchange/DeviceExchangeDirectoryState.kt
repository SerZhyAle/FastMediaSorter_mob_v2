package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpRendezvousCodec
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource

/**
 * One `changed` push of contract DEVICE-EXCHANGE 7.4: the records that appeared or changed at [revision] and the
 * ids that left. A record this build cannot read is dropped alone, as in [DeviceExchangeDirectory].
 */
data class DeviceExchangeChanged(
    val revision: Long,
    val devices: List<SftpRendezvousDevice>,
    val resources: List<SftpRendezvousResource>,
    val broadcasts: List<SftpRendezvousBroadcast>,
    val removedDeviceIds: Set<String>,
    val removedResourceIds: Set<String>,
    val removedBroadcastIds: Set<String>,
) {

    companion object {
        private const val KEY_REVISION = "revision"
        private const val KEY_UPSERTS = "upserts"
        private const val KEY_REMOVALS = "removals"
        private const val KEY_DEVICES = "devices"
        private const val KEY_RESOURCES = "resources"
        private const val KEY_BROADCASTS = "broadcasts"
        private const val KEY_DEVICE_IDS = "deviceIds"
        private const val KEY_RESOURCE_IDS = "resourceIds"
        private const val KEY_BROADCAST_IDS = "broadcastIds"

        /** A `changed` without a numeric `revision` reads as revision 0, which no held state accepts. */
        fun from(envelope: DeviceExchangeEnvelope): DeviceExchangeChanged {
            val upserts = envelope.obj(KEY_UPSERTS) ?: JsonObject()
            val removals = envelope.obj(KEY_REMOVALS) ?: JsonObject()
            return DeviceExchangeChanged(
                revision = envelope.members.get(KEY_REVISION)
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L,
                devices = upserts.records(KEY_DEVICES, SftpRendezvousCodec.TYPE_DEVICE),
                resources = upserts.records(KEY_RESOURCES, SftpRendezvousCodec.TYPE_RESOURCE),
                broadcasts = upserts.records(KEY_BROADCASTS, SftpRendezvousCodec.TYPE_BROADCAST),
                removedDeviceIds = removals.ids(KEY_DEVICE_IDS),
                removedResourceIds = removals.ids(KEY_RESOURCE_IDS),
                removedBroadcastIds = removals.ids(KEY_BROADCAST_IDS),
            )
        }

        private inline fun <reified T> JsonObject.records(key: String, type: String): List<T> =
            ((get(key) as? JsonArray) ?: JsonArray()).mapNotNull { element ->
                ExchangeBroadcastDiagnostics.decode(type, element) as? T
            }

        private fun JsonObject.ids(key: String): Set<String> =
            ((get(key) as? JsonArray) ?: JsonArray()).mapNotNull(::stringOrNull).toSet()

        private fun stringOrNull(element: JsonElement): String? =
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    }
}

/**
 * The directory a subscribed client holds (7.4): the last `directory` answer with every `changed` push applied in
 * revision order. A push that skips a revision cannot be applied safely - something in between was lost - so it
 * asks the caller to send `list` again instead.
 */
class DeviceExchangeDirectoryState private constructor(val directory: DeviceExchangeDirectory) {

    sealed interface Outcome {
        data class Applied(val state: DeviceExchangeDirectoryState) : Outcome

        /** The push is not newer than the held revision, e.g. one that crossed a fresh `directory` answer. */
        data object Stale : Outcome

        /** A revision was skipped: the caller sends `list` and replaces the state with its answer. */
        data object Gap : Outcome
    }

    val revision: Long get() = directory.revision

    fun apply(changed: DeviceExchangeChanged): Outcome = when {
        changed.revision <= revision -> Outcome.Stale
        changed.revision != revision + 1 -> Outcome.Gap
        else -> Outcome.Applied(DeviceExchangeDirectoryState(merged(changed)))
    }

    // A removed device takes its resources and broadcasts with it, whether or not the push names them.
    private fun merged(changed: DeviceExchangeChanged): DeviceExchangeDirectory {
        val goneDevices = changed.removedDeviceIds
        return DeviceExchangeDirectory(
            revision = changed.revision,
            devices = upsert(directory.devices, changed.devices, SftpRendezvousDevice::deviceId)
                .filterNot { it.deviceId in goneDevices },
            resources = upsert(directory.resources, changed.resources, SftpRendezvousResource::resourceId)
                .filterNot { it.resourceId in changed.removedResourceIds || it.deviceId in goneDevices },
            broadcasts = upsert(directory.broadcasts, changed.broadcasts, SftpRendezvousBroadcast::broadcastId)
                .filterNot { it.broadcastId in changed.removedBroadcastIds || it.deviceId in goneDevices },
        )
    }

    private fun <T> upsert(held: List<T>, upserts: List<T>, id: (T) -> String): List<T> {
        val replaced = upserts.associateBy(id)
        val kept = held.map { replaced[id(it)] ?: it }
        val heldIds = held.mapTo(HashSet(), id)
        return kept + upserts.filterNot { id(it) in heldIds }
    }

    companion object {
        fun of(directory: DeviceExchangeDirectory) = DeviceExchangeDirectoryState(directory)
    }
}
