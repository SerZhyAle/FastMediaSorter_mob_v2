package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpRendezvousCodec
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource

/**
 * One `directory` answer of contract DEVICE-EXCHANGE 7.4: every device, resource and broadcast of the account
 * at [revision]. A record this build cannot read is dropped alone, never the whole answer.
 */
data class DeviceExchangeDirectory(
    val revision: Long,
    val devices: List<SftpRendezvousDevice>,
    val resources: List<SftpRendezvousResource>,
    val broadcasts: List<SftpRendezvousBroadcast>,
) {

    companion object {
        private const val KEY_REVISION = "revision"
        private const val KEY_DEVICES = "devices"
        private const val KEY_RESOURCES = "resources"
        private const val KEY_BROADCASTS = "broadcasts"

        fun from(envelope: DeviceExchangeEnvelope): DeviceExchangeDirectory = DeviceExchangeDirectory(
            revision = envelope.members.get(KEY_REVISION)
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L,
            devices = envelope.records(KEY_DEVICES, SftpRendezvousCodec.TYPE_DEVICE),
            resources = envelope.records(KEY_RESOURCES, SftpRendezvousCodec.TYPE_RESOURCE),
            broadcasts = envelope.records(KEY_BROADCASTS, SftpRendezvousCodec.TYPE_BROADCAST),
        )

        private inline fun <reified T> DeviceExchangeEnvelope.records(key: String, type: String): List<T> =
            (members.get(key) as? JsonArray).orEmpty().mapNotNull { element ->
                (element as? JsonObject)?.let { SftpRendezvousCodec.decodeBody(type, it) } as? T
            }

        private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray()
    }
}
