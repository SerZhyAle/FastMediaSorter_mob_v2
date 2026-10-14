package com.sza.fastmediasorter.data.remote.sftp.anywhere

import android.os.Build
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerIdentityStore
import com.sza.fastmediasorter.data.repository.settings.DeviceExchangeAccountStore
import com.sza.fastmediasorter.data.repository.settings.ExchangeGrantRepository
import com.sza.fastmediasorter.data.repository.settings.SftpExchangeSettingsStore
import com.sza.fastmediasorter.data.repository.settings.SftpRendezvousIdentityStore
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.model.SftpShareId
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import javax.inject.Inject
import javax.inject.Singleton

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
    suspend fun grantEnvelopes(port: Int, hosts: List<String>): List<DeviceExchangeEnvelope> = emptyList()
}

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
) : SftpExchangeProducerSource {

    override suspend fun grantEnvelopes(port: Int, hosts: List<String>): List<DeviceExchangeEnvelope> {
        val all = grants.snapshot()
        val fingerprint = SshFingerprintNormalizer.canonical(identityStore.hostKeyFingerprint())
        if (all.isEmpty() || fingerprint == null) return emptyList()
        val share = ExchangeGrantEnvelopes.Share(
            hosts = hosts,
            port = port,
            hostKeyFingerprint = identityStore.clientCredentials().hostKeyFingerprint,
            resourceId = ids.resourceIdFor(fingerprint).resourceId,
        )
        // A grant whose password no longer decrypts (a restored copy) is not offered: nobody could log in with it.
        return all.mapNotNull { grant ->
            grants.password(grant.login)?.let { ExchangeGrantEnvelopes.grant(grant, it, share) }
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
    }
}
