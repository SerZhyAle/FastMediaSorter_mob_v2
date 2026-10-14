package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpRendezvousCodec
import com.sza.fastmediasorter.domain.model.ExchangeCastOffer
import com.sza.fastmediasorter.domain.model.ExchangeCastResult
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast

/** The cast envelopes of contract DEVICE-EXCHANGE 7.8 on the control stream, both directions. */
object DeviceExchangeCastCodec {

    private const val KEY_BROADCAST_ID = "broadcastId"
    private const val KEY_RECEIVER_DEVICE_ID = "receiverDeviceId"
    private const val KEY_FROM_DEVICE_ID = "fromDeviceId"
    private const val KEY_BROADCAST = "broadcast"
    private const val KEY_ACCEPTED = "accepted"

    fun cast(broadcastId: String, receiverDeviceId: String) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST) {
            addProperty(KEY_BROADCAST_ID, broadcastId)
            addProperty(KEY_RECEIVER_DEVICE_ID, receiverDeviceId)
        }

    fun stop(broadcastId: String, receiverDeviceId: String) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_STOP) {
            addProperty(KEY_BROADCAST_ID, broadcastId)
            addProperty(KEY_RECEIVER_DEVICE_ID, receiverDeviceId)
        }

    fun answer(broadcastId: String, accepted: Boolean, reason: String?) =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CAST_ANSWER) {
            addProperty(KEY_BROADCAST_ID, broadcastId)
            addProperty(KEY_ACCEPTED, accepted)
            reason?.let { addProperty(DeviceExchangeEnvelope.KEY_REASON, it) }
        }

    /** A `cast-offer` whose broadcast record this build reads; null for anything else. */
    fun offer(envelope: DeviceExchangeEnvelope): ExchangeCastOffer? {
        val record = envelope.takeIf { it.type == DeviceExchangeEnvelope.TYPE_CAST_OFFER }
            ?.obj(KEY_BROADCAST)
            ?.let { SftpRendezvousCodec.decodeBody(SftpRendezvousCodec.TYPE_BROADCAST, it) } as? SftpRendezvousBroadcast
        return record?.let {
            ExchangeCastOffer(
                broadcastId = envelope.string(KEY_BROADCAST_ID) ?: it.broadcastId,
                fromDeviceId = envelope.string(KEY_FROM_DEVICE_ID),
                record = it,
            )
        }
    }

    fun result(envelope: DeviceExchangeEnvelope): ExchangeCastResult? {
        val id = envelope.string(KEY_BROADCAST_ID)
        val receiver = envelope.string(KEY_RECEIVER_DEVICE_ID)
        if (envelope.type != DeviceExchangeEnvelope.TYPE_CAST_RESULT || id == null || receiver == null) return null
        return ExchangeCastResult(
            broadcastId = id,
            receiverDeviceId = receiver,
            accepted = envelope.boolean(KEY_ACCEPTED) == true,
            reason = envelope.string(DeviceExchangeEnvelope.KEY_REASON),
        )
    }

    /** The broadcast a `cast-stop` withdraws. */
    fun stoppedBroadcastId(envelope: DeviceExchangeEnvelope): String? =
        envelope.takeIf { it.type == DeviceExchangeEnvelope.TYPE_CAST_STOP }?.string(KEY_BROADCAST_ID)
}
