package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeE2eKeyStore
import com.sza.fastmediasorter.data.repository.settings.ExchangeE2eTrustStore
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2ePeer
import com.sza.fastmediasorter.data.security.e2e.E2ePeerState
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eRecipients
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeSealState
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import javax.inject.Inject
import javax.inject.Singleton

/** Who a box of one entry goes to, and how each listed device stands (contract items DG and DR). */
class ExchangeSealing(
    /** This device as a sender and reader; null for an entry enrolled before 0.29, which seals nothing. */
    val self: E2eSelf?,
    val recipients: List<E2eRecipient>,
    val states: Map<String, ExchangeSealState>,
    private val trusted: Map<String, String>,
) {
    /** The opening check of item DH: [fromKey] is the key this device trusts for [from]. */
    fun trusts(from: String, fromKey: ByteArray): Boolean = E2eRecipients.trusts(trusted, from, fromKey)
}

/**
 * The phone's side of the trust rules: the entry's own key, the keys it trusts, and the recipients it seals to,
 * from the devices the directory lists. Learning a first-seen key happens here, so every caller - the producer's
 * control stream, the clipboard, the device list - sees the same trusted set.
 */
@Singleton
class ExchangeE2eRepository @Inject constructor(
    private val keys: DeviceExchangeE2eKeyStore,
    private val trust: ExchangeE2eTrustStore,
) {

    /**
     * [devices] is the account's whole device list - a `directory` or the state `changed` keeps - never a filtered
     * one: a kept key whose device is no longer listed was removed from the account (`revoke`), and is dropped so
     * a device added again under that id is first seen anew (item DG).
     */
    suspend fun sealing(entry: DeviceExchangeAccountEntry, devices: List<SftpRendezvousDevice>): ExchangeSealing {
        val peers = devices.map { it.toPeer() }
        trust.retain(entry.deviceId, peers.map { it.deviceId }.toSet())
        trust.learn(entry.deviceId, E2eRecipients.learned(entry.deviceId, peers, trust.trusted(entry.deviceId)))
        val trusted = trust.trusted(entry.deviceId)
        val self = keys.existing(entry.deviceId)?.let { E2eSelf(entry.deviceId, it) }
        val states = peers.associate { peer ->
            peer.deviceId to when (E2eRecipients.state(peer, trusted[peer.deviceId])) {
                E2ePeerState.TRUSTED -> ExchangeSealState.TRUSTED
                E2ePeerState.MISSING -> ExchangeSealState.E2E_MISSING
                E2ePeerState.KEY_CHANGED -> ExchangeSealState.KEY_CHANGED
            }
        }
        val recipients = if (self == null) emptyList() else E2eRecipients.recipients(entry.deviceId, peers, trusted)
        return ExchangeSealing(self, recipients, states, trusted)
    }

    suspend fun grantSenders(entry: DeviceExchangeAccountEntry): Map<String, String> = trust.grantSenders(entry.deviceId)

    suspend fun learnGrantSenders(entry: DeviceExchangeAccountEntry, firstSeen: Map<String, String>) =
        trust.learnGrantSenders(entry.deviceId, firstSeen)

    /** The keys this device trusts now, without a device list - for a box that arrives outside a directory. */
    suspend fun trustedSealing(entry: DeviceExchangeAccountEntry): ExchangeSealing =
        ExchangeSealing(self(entry), emptyList(), emptyMap(), trust.trusted(entry.deviceId))

    /** This device as a sender for [entry]; null for an entry enrolled before 0.29, which seals nothing. */
    suspend fun self(entry: DeviceExchangeAccountEntry): E2eSelf? =
        keys.existing(entry.deviceId)?.let { E2eSelf(entry.deviceId, it) }

    /** The watch's key from its hand-over answer, trusted over anything the directory said (item DG). */
    suspend fun trustHandedOver(entry: DeviceExchangeAccountEntry, deviceId: String, kem: String) {
        if (E2eDeviceKey.decodeKem(kem) != null) trust.pin(entry.deviceId, deviceId, kem)
    }

    private fun SftpRendezvousDevice.toPeer() = E2ePeer(
        deviceId = deviceId,
        kem = e2eKem,
        capable = SftpRendezvousDevice.CAPABILITY_E2E_V1 in capabilities,
        updatedAtMs = updatedAtMs,
    )
}
