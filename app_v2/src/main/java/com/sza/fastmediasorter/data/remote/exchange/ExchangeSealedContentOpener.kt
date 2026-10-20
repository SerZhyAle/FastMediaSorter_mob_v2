package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.repository.ExchangeE2eRepository
import com.sza.fastmediasorter.data.security.e2e.E2eBroadcastSeals
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eOpenFailure
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeSealIssue
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens what an entry's server delivered sealed (contract DEVICE-EXCHANGE section 27), so every later step - the
 * listing, the attach, the tunnel - works on the opened access exactly as it did on a clear one. Content the
 * server delivered in clear is dropped: a conforming client has no clear fallback through the server (item DP).
 * The Drive channel never passes here (item DO).
 */
@Singleton
class ExchangeSealedContentOpener @Inject constructor(
    private val e2e: ExchangeE2eRepository,
) {

    suspend fun open(entry: DeviceExchangeAccountEntry, directory: DeviceExchangeDirectory): DeviceExchangeDirectory {
        val sealing = e2e.sealing(entry, directory.devices)
        val grants = GrantTrust(e2e.grantSenders(entry))
        // Item DJ: a granted record whose box is the registering one is not listed until its replacing box arrives;
        // a device with no key of its own still lists it, so it can show why nothing will open.
        val registering = { grantId: String?, box: String? ->
            sealing.self != null && grantId != null &&
                E2eSealedBox.addressesNobody(box?.let { runCatching { JsonParser.parseString(it) }.getOrNull() })
        }
        val opened = directory.copy(
            resources = directory.resources.filterNot { registering(it.grantId, it.sealedAccess) }
                .map { openAccess(sealing, it, grants) },
            broadcasts = directory.broadcasts.filterNot { registering(it.grantId, it.sealed) }
                .map { openBroadcast(sealing, it, grants) },
        )
        e2e.learnGrantSenders(entry, grants.learned)
        return opened
    }

    /** A broadcast offered by `cast` (7.8), opened with the keys this device already trusts. */
    suspend fun openCast(entry: DeviceExchangeAccountEntry, record: SftpRendezvousBroadcast): SftpRendezvousBroadcast =
        openBroadcast(e2e.trustedSealing(entry), record)

    /**
     * Item DK: the record's box is opened and its keys are written into the endpoints' `seal`, so the descriptor
     * from here on is the one a person would have handed over - every later step reads the keys from there. A box
     * that does not open leaves the sealed endpoints keyless, and a keyless sealed endpoint is never played.
     */
    internal fun openBroadcast(
        sealing: ExchangeSealing,
        record: SftpRendezvousBroadcast,
        grants: GrantTrust = GrantTrust(emptyMap()),
    ): SftpRendezvousBroadcast {
        val box = record.sealed?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
        val self = sealing.self
        val grantId = record.grantId
        // Item DJ: a granted broadcast's box is the grant's, opened with the first sender key of that grant.
        val result = when {
            box == null || self == null -> null
            grantId != null -> E2eSealedBox.open(box, E2eSealedBox.grantContext(grantId), self) { _, fromKey ->
                grants.knows(grantId, fromKey) != false
            }
            else -> E2eSealedBox.open(box, E2eSealedBox.broadcastContext(record.broadcastId), self, sealing::trusts)
        }
        val opened = result as? E2eOpenResult.Opened
        if (grantId != null) opened?.let { grants.learn(grantId, it.fromKey) }
        val issue = if (grantId == null || opened != null) null else grantIssue(result)
        val descriptor = runCatching { JsonParser.parseString(record.descriptorJson).asJsonObject }.getOrNull()
        return if (descriptor == null) {
            record.copy(sealed = null, sealIssue = issue)
        } else {
            E2eBroadcastSeals.withKeys(descriptor, opened?.payload)
            // Item BF: the pair rides on the opened descriptor, in memory only, so the broadcast asks nothing - the
            // own pair for an own broadcast, the grant's pair for a guest (BD).
            opened?.payload?.get(E2eBroadcastSeals.KEY_CREDENTIALS)?.takeIf { it.isJsonObject }
                ?.let { descriptor.add(E2eBroadcastSeals.KEY_CREDENTIALS, it) }
            record.copy(descriptorJson = descriptor.toString(), sealed = null, sealIssue = issue)
        }
    }

    private fun grantIssue(result: E2eOpenResult?): ExchangeSealIssue =
        if (result == E2eOpenResult.Failed(E2eOpenFailure.UNTRUSTED_SENDER)) {
            ExchangeSealIssue.KEY_CHANGED
        } else {
            ExchangeSealIssue.SEALED_FAILED
        }

    /**
     * The first sender key of each grant a guest has seen (27 item DJ). [learned] collects the keys of boxes that
     * opened for the first time, kept only after the whole directory was read.
     */
    internal class GrantTrust(private val held: Map<String, String>) {
        val learned = mutableMapOf<String, String>()

        fun knows(grantId: String, fromKey: ByteArray): Boolean? =
            (held[grantId] ?: learned[grantId])?.let { it == E2eBytes.b64url(fromKey) }

        fun learn(grantId: String, fromKey: ByteArray) {
            if (held[grantId] == null) learned.putIfAbsent(grantId, E2eBytes.b64url(fromKey))
        }
    }

    internal fun openAccess(
        sealing: ExchangeSealing,
        resource: SftpRendezvousResource,
        grants: GrantTrust = GrantTrust(emptyMap()),
    ): SftpRendezvousResource {
        val box = resource.sealedAccess
        val self = sealing.self
        val grantId = resource.grantId
        return when {
            box != null && self != null && grantId != null -> openedGrant(self, resource, box, grantId, grants)
            box != null && self != null -> opened(sealing, resource, box)
            box != null -> resource.unusable(ExchangeSealIssue.E2E_MISSING)
            resource.descriptor != null -> resource.unusable(ExchangeSealIssue.E2E_MISSING)
            else -> resource
        }
    }

    // Item DJ: the guest trusts the first sender key of a grant; the payload is `{access}`.
    private fun openedGrant(
        self: E2eSelf,
        resource: SftpRendezvousResource,
        box: String,
        grantId: String,
        grants: GrantTrust,
    ): SftpRendezvousResource {
        val result = E2eSealedBox.open(
            runCatching { JsonParser.parseString(box) }.getOrNull(),
            E2eSealedBox.grantContext(grantId),
            self,
        ) { _, fromKey -> grants.knows(grantId, fromKey) != false }
        val opened = result as? E2eOpenResult.Opened
        opened?.let { grants.learn(grantId, it.fromKey) }
        val access = opened?.payload?.get(KEY_ACCESS)?.takeIf { it.isJsonObject }?.asJsonObject
        return when {
            access != null -> withAccess(resource, access)
            result == E2eOpenResult.Failed(E2eOpenFailure.UNTRUSTED_SENDER) ->
                resource.unusable(ExchangeSealIssue.KEY_CHANGED)
            else -> resource.unusable(ExchangeSealIssue.SEALED_FAILED)
        }
    }

    private fun opened(
        sealing: ExchangeSealing,
        resource: SftpRendezvousResource,
        box: String,
    ): SftpRendezvousResource {
        val result = E2eSealedBox.open(
            runCatching { JsonParser.parseString(box) }.getOrNull(),
            E2eSealedBox.accessContext(resource.resourceId),
            requireNotNull(sealing.self),
            sealing::trusts,
        )
        val payload = (result as? E2eOpenResult.Opened)?.payload
        return if (payload == null) {
            Timber.w("Exchange sealed access did not open (%s)", (result as? E2eOpenResult.Failed)?.reason)
            resource.unusable(ExchangeSealIssue.SEALED_FAILED)
        } else {
            withAccess(resource, payload)
        }
    }

    private fun withAccess(resource: SftpRendezvousResource, access: JsonObject): SftpRendezvousResource {
        val descriptor = access.get(KEY_DESCRIPTOR)?.takeIf { it.isJsonPrimitive }?.asString
        val root = access.get(KEY_ROOT)?.takeIf { it.isJsonPrimitive }?.asString
        return when {
            descriptor == null -> {
                Timber.w("Exchange sealed access opened without a descriptor; not used")
                resource.unusable(ExchangeSealIssue.SEALED_FAILED)
            }
            !sameHostKey(resource, descriptor) -> {
                Timber.w("Exchange sealed access names another host key than its record; not used")
                resource.unusable(ExchangeSealIssue.SEALED_FAILED)
            }
            else -> resource.copy(descriptor = descriptor, root = root, sealedAccess = null)
        }
    }

    // Item DI: the record's open fingerprint is the resource's identity for merging; a record whose opened
    // descriptor names another key is neither merged nor attached.
    private fun sameHostKey(resource: SftpRendezvousResource, descriptor: String): Boolean {
        val opened = SshFingerprintNormalizer.canonical(SftpPairingPayload.decode(descriptor)?.hostKeyFingerprint)
        val listed = resource.hostKeyFingerprint?.let(SshFingerprintNormalizer::canonical)
        return opened == null || listed == null || listed == opened
    }

    private fun SftpRendezvousResource.unusable(issue: ExchangeSealIssue) =
        copy(descriptor = null, root = null, sealedAccess = null, sealIssue = issue)

    private companion object {
        const val KEY_ACCESS = "access"
        const val KEY_DESCRIPTOR = "descriptor"
        const val KEY_ROOT = "root"
    }
}
