package com.sza.fastmediasorter.domain.usecase

import android.os.Build
import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.repository.WearResourceSelectionRepositoryImpl
import com.sza.fastmediasorter.data.repository.wear.WearDeliveredResourceStore
import com.sza.fastmediasorter.data.repository.wear.WearResourceStampStore
import com.sza.fastmediasorter.data.repository.wear.WearResourceTombstoneStore
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearEndpointPayload
import com.sza.fastmediasorter.domain.model.WearNetworkSourcePayload
import com.sza.fastmediasorter.domain.model.WearSourceTombstonePayload
import com.sza.fastmediasorter.domain.model.WearSyncPayload
import com.sza.fastmediasorter.domain.networkmonitor.ReachableEndpointProvider
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import timber.log.Timber
import javax.inject.Inject

private const val DATA_LAYER_PATH = "/fms/network_sources/push"
private const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L

/**
 * @param deselected S2882: how many registered resources this batch declared as unwanted on the
 *   watch. Declared last with a default so the older two-value call sites still build. A caller reads
 *   it to tell a batch that withdrew something from one that did nothing at all - the two used to be
 *   the same value, and the companion reported both as an empty selection.
 * @param dispatched S2926: whether this batch reached the Data Layer. The three counters above
 *   describe what was inside the batch and cannot answer it: a batch carrying only tombstones is
 *   written to the wire with every one of them at zero, and both callers that inferred "nothing
 *   left the phone" from `sent` and `deselected` therefore reported it as an empty selection while
 *   the watch was already acknowledging it. No default, so a new call site has to state the fact
 *   rather than inherit it.
 */
data class SendResult(
    val sent: Int,
    val skipped: Int,
    val deselected: Int = 0,
    val dispatched: Boolean
)

class SendResourcesToWatchUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val wearableRepository: WearableDataLayerRepository,
    private val selectionRepository: WearResourceSelectionRepositoryImpl,
    private val reachableEndpointProvider: ReachableEndpointProvider,
    // S2502: the edit times the watch ranks the incoming records by.
    private val wearResourceStampStore: WearResourceStampStore,
    // S2507: the deletions this phone has made, which the watch ranks the same way.
    private val wearResourceTombstoneStore: WearResourceTombstoneStore,
    // S2909: what this phone has already put on the watch, which is what a withdrawal can name.
    private val wearDeliveredResourceStore: WearDeliveredResourceStore
) {
    private val gson = Gson()
    suspend operator fun invoke(forceDispatch: Boolean = false): Result<SendResult> = runCatching {
        val nodes = wearableRepository.getConnectedNodes()
        if (nodes.isEmpty()) error("No watch connected")

        val selectedIds = selectionRepository.getSelectedIds()
        // S2502: read once for the whole batch - the map is small and every record consults it.
        val editStamps = wearResourceStampStore.readStamps()
        val allResources = resourceRepository.getAllResourcesSync()
        // S1009: never push hidden resources to the watch (defense-in-depth; hidden resources are LOCAL today).
        val networkResources = allResources.filter {
            it.id in selectedIds && !it.isHidden &&
                it.type in ResourceType.WATCH_TRANSFERABLE
        }
        val collected = collectPayloads(networkResources, editStamps)
        val delivered = wearDeliveredResourceStore.read()
        // S2882: the registered resources this phone declares unwanted on the watch, narrowed by
        // S2909 to the ones the watch was actually given - see withdrawalsFor.
        val deselectedIds = withdrawalsFor(allResources, selectedIds, delivered)
        // S2507: every tombstone travels, whatever the selection holds. Selection says which
        // resources the watch should carry; it never says which deletions it may hear about, and
        // dropping a deselected resource's tombstone is what would let the watch resurrect it.
        val tombstones = wearResourceTombstoneStore.read()

        // S1781: an empty selection with no tombstones and no active deselections sends nothing.
        // S2909: tombstones that travelled in a prior batch are retired after the Data Layer accepted
        // the bytes, so a phone that once deleted a resource does not re-send its tombstone on every
        // subsequent push - without that, this branch is unreachable whenever the tombstone store is
        // non-empty, which is always once a deletion has been recorded.
        // S3046: forceDispatch allows explicit watch sync requests to answer even when empty.
        if (!forceDispatch && collected.payloads.isEmpty() && deselectedIds.isEmpty() && tombstones.orEmpty().isEmpty()) {
            return@runCatching SendResult(
                sent = 0,
                skipped = collected.skipped,
                deselected = 0,
                dispatched = false
            )
        }

        val syncPayload = WearSyncPayload(
            sentAt = System.currentTimeMillis(),
            phoneName = Build.MODEL.orEmpty(),
            sources = collected.payloads,
            tombstones = tombstones,
            // Null rather than an empty list, so a batch that withdraws nothing stays byte-identical
            // to one a build without this field would have produced.
            deselectedIds = deselectedIds.ifEmpty { null }
        )
        val syncJson = gson.toJson(syncPayload)
        val bytes = syncJson.toByteArray(Charsets.UTF_8)
        wearableRepository.putDataItem(DATA_LAYER_PATH, bytes)
        rememberDelivered(delivered, collected.payloads, deselectedIds, tombstones)
        retireTombstones(tombstones)
        Timber.i(
            "Sent ${collected.payloads.size} resources to watch " +
                "(${collected.skipped} skipped, ${deselectedIds.size} withdrawn)"
        )
        SendResult(
            sent = collected.payloads.size,
            skipped = collected.skipped,
            deselected = deselectedIds.size,
            dispatched = true
        )
    }

    /**
     * S2909: the ids this batch declares unwanted on the watch.
     *
     * S2882's set was the whole registry minus the selection, which is never empty while the registry
     * is not - so a selection holding nothing re-declared every resource withdrawn on every push, and
     * the "nothing to send" branch above could not be reached at all. Only a resource this phone
     * actually delivered can be withdrawn from the watch, and the watch drops an id it does not hold
     * anyway (`ImportNetworkSourcesUseCase.applyDeselections`).
     *
     * [delivered] being null means no push has completed since the store existed, so that one batch
     * keeps the older, wider set: guessing "nothing was delivered" there would strand whatever the
     * watch is already holding, which is the loss S2882 exists to prevent. The next push writes the
     * precise set and the wide one is never used again.
     */
    private fun withdrawalsFor(
        allResources: List<MediaResource>,
        selectedIds: Set<Long>,
        delivered: Set<String>?
    ): List<String> {
        if (delivered == null) {
            return allResources.filterNot { it.id in selectedIds }.map { it.id.toString() }
        }
        val selected = selectedIds.mapTo(mutableSetOf()) { it.toString() }
        // Sorted so one selection state produces one batch of bytes, whatever order the store kept.
        return delivered.filterNot { it in selected }.sorted()
    }

    /**
     * S2909: what the watch holds once this batch lands - what it already had, plus what travelled
     * now, minus what this batch withdrew or deleted.
     *
     * Written after the Data Layer accepted the bytes and never before, so a push that failed does not
     * leave the phone believing a resource arrived - the phone would then never withdraw it.
     */
    private suspend fun rememberDelivered(
        deliveredBefore: Set<String>?,
        payloads: List<WearNetworkSourcePayload>,
        withdrawn: List<String>,
        tombstones: List<WearSourceTombstonePayload>
    ) {
        val gone = withdrawn.toSet() + tombstones.map { it.id }
        wearDeliveredResourceStore.write(deliveredBefore.orEmpty() + payloads.map { it.id } - gone)
    }

    /**
     * S2909: retires the tombstones that travelled in this batch once the Data Layer accepted the bytes.
     *
     * A tombstone exists to tell the watch "delete this id if you hold it" and to prevent a later push
     * from resurrecting it. Once the batch landed, the watch has either applied the deletion and recorded
     * its own tombstone, rejected it because a local edit is newer, or recorded it for an id it never
     * held - in every branch the phone's copy is redundant. The GMS Data Layer guarantees delivery after
     * `putDataItem` returns, so forgetting at this point does not risk a tombstone that never reaches the
     * watch.
     *
     * Only the tombstones that were read for this batch are forgotten, so a deletion recorded by another
     * coroutine between [read][WearResourceTombstoneStore.read] and the `putDataItem` call survives and
     * travels on the next push.
     */
    private suspend fun retireTombstones(tombstones: List<WearSourceTombstonePayload>?) {
        tombstones.orEmpty().forEach { wearResourceTombstoneStore.forget(it.id) }
    }

    /** What the per-resource loop produced: the records that travel, and how many could not. */
    private class CollectedPayloads(
        val payloads: List<WearNetworkSourcePayload>,
        val skipped: Int
    )

    /**
     * Extracted from `invoke`, which reached detekt's length ceiling when S2882 added the withdrawn
     * set beside the records - the loop is the one part of the batch that is per-resource.
     */
    private suspend fun collectPayloads(
        networkResources: List<MediaResource>,
        editStamps: Map<String, Long>
    ): CollectedPayloads {
        var skipped = 0
        val payloads = mutableListOf<WearNetworkSourcePayload>()
        for (resource in networkResources) {
            val credId = resource.credentialsId
            if (credId == null) {
                Timber.w("Resource ${resource.name} has no credentialsId - skipping")
                skipped++
                continue
            }
            val creds = credentialsRepository.getByCredentialId(credId)
            if (creds == null) {
                Timber.w("Credentials not found for ${resource.name} ($credId) - skipping")
                skipped++
                continue
            }
            val password = creds.password
            if (password.isEmpty() && creds.encryptedPassword.isNotEmpty()) {
                Timber.e("Password decryption failed for ${resource.name} - skipping")
                skipped++
                continue
            }
            payloads.add(
                toPayload(resource, creds, password, endpointsFor(resource, creds), editStamps[resource.id.toString()])
            )
        }
        return CollectedPayloads(payloads, skipped)
    }

    /**
     * S2488: for SFTP, send the address that answers right now instead of the one recorded at import -
     * the companion's listening endpoint moves, and the watch has no way to notice.
     */
    private suspend fun endpointsFor(
        resource: MediaResource,
        creds: NetworkCredentialsEntity
    ): List<HostPort>? = if (resource.type == ResourceType.SFTP) {
        runCatching { reachableEndpointProvider.orderedEndpoints(creds.server, creds.port) }
            .onFailure {
                Timber.w(it, "Endpoint resolution failed for ${resource.name} - sending stored address")
            }
            .getOrNull()
    } else {
        null
    }

    // Extracted from invoke, which reached detekt's length ceiling when S2502 added the edit stamp.
    private fun toPayload(
        resource: MediaResource,
        creds: NetworkCredentialsEntity,
        password: String,
        endpoints: List<HostPort>?,
        lastEditedAt: Long?
    ): WearNetworkSourcePayload {
        val primary = endpoints?.firstOrNull()
        return WearNetworkSourcePayload(
            id = resource.id.toString(),
            type = resource.type.name,
            name = resource.name,
            server = primary?.host ?: creds.server,
            port = primary?.port ?: creds.port,
            username = creds.username,
            password = password,
            shareName = creds.shareName,
            basePath = resource.path,
            domain = creds.domain,
            sshPrivateKey = creds.decryptedSshPrivateKey,
            // S1555: canonicalised here, never on the watch - an unparseable value becomes
            // null and stays permissive, exactly as the phone's own SFTP path treats it.
            hostKeyFingerprint = SshFingerprintNormalizer.canonical(resource.hostKeyFingerprint),
            // S2129: sent verbatim. The watch owns the mirrored icon set and resolves the id
            // there, so validating it here would only reject ids a newer watch does carry.
            iconId = resource.iconId,
            // S2487: mirror phone resource allowed media types and allFiles mode
            supportedMediaTypes = resource.supportedMediaTypes.map { it.name },
            allFiles = resource.allFiles,
            // S2488: the whole group, so the watch can retry the other addresses when the one
            // that answered here stops answering there. Null for SMB/FTP, which carry no
            // imported alternates, and on the fallback path, where no group was resolved.
            endpoints = endpoints?.map { WearEndpointPayload(it.host, it.port) },
            // S2502: null when this resource was never edited since the stamp store existed.
            // Null, not zero: zero would rank it as the oldest record possible and lose to
            // anything the watch holds, which is the loss this ticket exists to remove.
            lastEditedAt = lastEditedAt
        )
    }
}
