package com.sza.fastmediasorter.data.remote.sftp.anywhere

import android.os.Build
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeRelayKeys
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerIdentityStore
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.ExchangeE2eRepository
import com.sza.fastmediasorter.data.repository.settings.DeviceExchangeAccountStore
import com.sza.fastmediasorter.data.repository.settings.ExchangeGrantRepository
import com.sza.fastmediasorter.data.repository.settings.SftpExchangeSettingsStore
import com.sza.fastmediasorter.data.repository.settings.SftpRendezvousIdentityStore
import com.sza.fastmediasorter.data.security.e2e.E2eBroadcastSeals
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeWelcome
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.model.SftpShareId
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the guest grants are sealed with on one server (27 item DJ): [audience] maps each `grantId` to the guest's
 * devices from the member's `directory`; null while that directory has not arrived, so no guest grant goes yet.
 */
class ExchangeGrantSealing(
    val entry: DeviceExchangeAccountEntry,
    val serverSeals: Boolean,
    val audience: Map<String, List<E2eRecipient>>?,
)

/** A share record as it goes through one server; [recipientsKey] names who its box was sealed to, if anyone. */
data class SealedShareRecord(val record: SftpRendezvousResource, val recipientsKey: String)

/** The account entry and share id the embedded SFTP share is published under. */
data class SftpExchangeProducer(val entry: DeviceExchangeAccountEntry, val shareId: String)

/** What the tunnel manager publishes, and through which account entry. */
interface SftpExchangeProducerSource {

    /** Null while publishing is off, no entry is the outgoing one or the share id is not usable. */
    suspend fun producer(): SftpExchangeProducer?

    /**
     * The entry the device publishes and broadcasts through (item AC), whether or not the share is published; a
     * broadcast needs it with publishing off.
     */
    suspend fun outgoingEntry(): DeviceExchangeAccountEntry? = producer()?.entry

    /**
     * The share's section 6.2 record as [producer] publishes it, with the direct endpoints of the server on
     * [port] at [hosts]; null while the server has no host key to describe.
     */
    suspend fun shareRecord(producer: SftpExchangeProducer, port: Int, hosts: List<String>): SftpRendezvousResource?

    /**
     * The `grant` envelopes of every grant this device made (items BD and BE), for the server on [port] at [hosts];
     * sent after `publish` on every welcomed stream. Empty when nothing is granted.
     */
    suspend fun grantEnvelopes(
        port: Int,
        hosts: List<String>,
        sealing: ExchangeGrantSealing,
    ): List<DeviceExchangeEnvelope> = emptyList()

    /**
     * Keeps what the control stream's `welcome` announced - above all whether the server carries `sealed`
     * (27 item DP) - which until now only an enrollment or a clipboard call refreshed.
     */
    suspend fun rememberWelcome(entry: DeviceExchangeAccountEntry, welcome: DeviceExchangeWelcome) = Unit

    /**
     * [record] as it may go through [entry]'s server (27 items DI and DP): `access` replaced by `sealedAccess` for
     * the account's [devices], or withheld while the server lacks `sealed`, the devices are not known yet or
     * nobody can open a box. The default keeps the record as it is.
     */
    suspend fun sealedRecord(
        entry: DeviceExchangeAccountEntry,
        record: SftpRendezvousResource,
        serverSeals: Boolean,
        devices: List<SftpRendezvousDevice>?,
    ): SealedShareRecord = SealedShareRecord(record, recipientsKey = "")

    /**
     * [record] with the box of 27 item DK for the account's [devices]: `relayKey`, and `tunnelPin` once the tunnel
     * runs TLS. Unchanged while the devices are not known yet or nobody can open a box.
     */
    suspend fun sealedBroadcast(
        entry: DeviceExchangeAccountEntry,
        record: SftpRendezvousBroadcast,
        devices: List<SftpRendezvousDevice>,
    ): SealedBroadcastRecord = SealedBroadcastRecord(record, recipientsKey = "")
}

/** A broadcast record with its box; [recipientsKey] names who the box was sealed to and with which own key. */
class SealedBroadcastRecord(val record: SftpRendezvousBroadcast, val recipientsKey: String)

/**
 * Builds the publication from the outgoing entry, the publish switch and the embedded server's identity. The record
 * keeps the `resourceId` the Drive channel uses for the same share, so one share is one resource in both
 * stores (contract DEVICE-EXCHANGE section 4).
 */
@Singleton
class SftpExchangeProducerProvider @Inject constructor(
    private val settings: SftpExchangeSettingsStore,
    private val accounts: DeviceExchangeAccountStore,
    private val identityStore: SftpServerIdentityStore,
    private val ids: SftpRendezvousIdentityStore,
    private val grants: ExchangeGrantRepository,
    private val accountRepository: dagger.Lazy<DeviceExchangeAccountRepository>,
    private val e2e: ExchangeE2eRepository,
    private val relayKeys: ExchangeRelayKeys,
) : SftpExchangeProducerSource {

    override suspend fun sealedBroadcast(
        entry: DeviceExchangeAccountEntry,
        record: SftpRendezvousBroadcast,
        devices: List<SftpRendezvousDevice>,
    ): SealedBroadcastRecord {
        val sealing = e2e.sealing(entry, devices)
        val self = sealing.self
        // A device that left takes the own group key with it: the next box and the next epoch use a new one (DL).
        relayKeys.membersChanged(record.broadcastId, sealing.recipients.map { it.deviceId }.toSet())
        val relayKey = relayKeys.ownKey(record.broadcastId)
        val payload = JsonObject().apply {
            relayKey?.let { addProperty(E2eBroadcastSeals.KEY_RELAY_KEY, E2eBytes.b64url(it)) }
        }
        val key = sealing.recipients.map { it.deviceId }.sorted().joinToString(",") +
            "/" + relayKey?.let { E2eBytes.hex(E2eBytes.sha256(it)) }
        val sealed = if (self == null || sealing.recipients.isEmpty() || relayKey == null) {
            record.copy(sealed = null)
        } else {
            val context = E2eSealedBox.broadcastContext(record.broadcastId)
            record.copy(sealed = E2eSealedBox.seal(payload, context, self, sealing.recipients).toString())
        }
        return SealedBroadcastRecord(sealed, key)
    }

    override suspend fun sealedRecord(
        entry: DeviceExchangeAccountEntry,
        record: SftpRendezvousResource,
        serverSeals: Boolean,
        devices: List<SftpRendezvousDevice>?,
    ): SealedShareRecord {
        val withheld = record.copy(descriptor = null, root = null, sealedAccess = null)
        val sealing = devices?.takeIf { serverSeals }?.let { e2e.sealing(entry, it) }
        val self = sealing?.self
        val recipients = sealing?.recipients.orEmpty()
        val key = recipients.sortedBy { it.deviceId }
            .joinToString(",") { it.deviceId + ":" + E2eBytes.hex(it.publicKey) }
        val descriptor = record.descriptor
        return if (self == null || recipients.isEmpty() || descriptor == null) {
            SealedShareRecord(withheld, key)
        } else {
            val access = JsonObject().apply {
                addProperty(KEY_DESCRIPTOR, descriptor)
                record.root?.let { addProperty(KEY_ROOT, it) }
            }
            val box = E2eSealedBox.seal(access, E2eSealedBox.accessContext(record.resourceId), self, recipients)
            SealedShareRecord(withheld.copy(sealedAccess = box.toString()), key)
        }
    }

    override suspend fun rememberWelcome(entry: DeviceExchangeAccountEntry, welcome: DeviceExchangeWelcome) {
        accountRepository.get().rememberWelcome(entry, welcome)
    }

    override suspend fun grantEnvelopes(
        port: Int,
        hosts: List<String>,
        sealing: ExchangeGrantSealing,
    ): List<DeviceExchangeEnvelope> {
        val all = grants.snapshot()
        val fingerprint = SshFingerprintNormalizer.canonical(identityStore.hostKeyFingerprint())
        if (all.isEmpty() || fingerprint == null) return emptyList()
        val share = ExchangeGrantEnvelopes.Share(
            hosts = hosts,
            port = port,
            hostKeyFingerprint = identityStore.clientCredentials().hostKeyFingerprint,
            resourceId = ids.resourceIdFor(fingerprint).resourceId,
        )
        val self = e2e.self(sealing.entry)?.takeIf { sealing.serverSeals }
        // A grant whose password no longer decrypts (a restored copy) is not offered: nobody could log in with it.
        return all.mapNotNull { grant ->
            grants.password(grant.login)?.let { password ->
                ExchangeGrantEnvelopes.grant(grant, password, share) { payload ->
                    val to = sealing.audience?.get(grant.grantId).orEmpty()
                    self?.takeIf { to.isNotEmpty() }
                        ?.let { E2eSealedBox.seal(payload, E2eSealedBox.grantContext(grant.grantId), it, to) }
                }
            }
        }
    }

    override suspend fun outgoingEntry(): DeviceExchangeAccountEntry? = accounts.outgoing()

    override suspend fun producer(): SftpExchangeProducer? {
        val config = settings.snapshot()
        val entry = accounts.outgoing()
        return if (config.enabled && entry != null && SftpShareId.isValid(config.shareId)) {
            SftpExchangeProducer(entry, config.shareId)
        } else {
            null
        }
    }

    override suspend fun shareRecord(
        producer: SftpExchangeProducer,
        port: Int,
        hosts: List<String>,
    ): SftpRendezvousResource? {
        val fingerprint = SshFingerprintNormalizer.canonical(identityStore.hostKeyFingerprint()) ?: return null
        val credentials = identityStore.clientCredentials()
        val descriptor = SftpPairingPayload(
            hosts = hosts,
            port = port,
            username = credentials.username,
            password = credentials.password,
            hostKeyFingerprint = credentials.hostKeyFingerprint,
        ).encode()
        val now = System.currentTimeMillis()
        return SftpRendezvousResource(
            resourceId = ids.resourceIdFor(fingerprint).resourceId,
            deviceId = producer.entry.deviceId,
            kind = SftpRendezvousResource.KIND_SFTP_SHARE,
            name = Build.MODEL.orEmpty(),
            descriptor = descriptor,
            updatedAtMs = now,
            writtenAtMs = now,
            ttlSeconds = 0,
            shareId = producer.shareId,
            publicPort = ANY_PUBLIC_PORT,
            hostKeyFingerprint = fingerprint,
            identityRoot = WHOLE_SERVER_ROOT,
        )
    }

    private companion object {
        // A request for any free third-party port (contract ANYWHERE-ACCESS 5.5).
        const val ANY_PUBLIC_PORT = 0

        // The published resource is the whole server: every served folder sits under its root (item BA).
        const val WHOLE_SERVER_ROOT = "/"

        const val KEY_DESCRIPTOR = "descriptor"
        const val KEY_ROOT = "root"
    }
}
