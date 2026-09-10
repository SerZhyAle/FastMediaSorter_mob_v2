package com.sza.fastmediasorter.domain.usecase

import android.os.Build
import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.repository.WearResourceSelectionRepositoryImpl
import com.sza.fastmediasorter.data.repository.wear.WearResourceStampStore
import com.sza.fastmediasorter.data.repository.wear.WearResourceTombstoneStore
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearEndpointPayload
import com.sza.fastmediasorter.domain.model.WearNetworkSourcePayload
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
 */
data class SendResult(
    val sent: Int,
    val skipped: Int,
    val deselected: Int = 0
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
    private val wearResourceTombstoneStore: WearResourceTombstoneStore
) {
    private val gson = Gson()
    suspend operator fun invoke(): Result<SendResult> = runCatching {
        val nodes = wearableRepository.getConnectedNodes()
        if (nodes.isEmpty()) error("No watch connected")

        val selectedIds = selectionRepository.getSelectedIds()
        // S2502: read once for the whole batch - the map is small and every record consults it.
        val editStamps = wearResourceStampStore.readStamps()
        Timber.d("S2502: phone push leg entered, ${editStamps.size} edit stamp(s) known")
        val allResources = resourceRepository.getAllResourcesSync()
        // S1009: never push hidden resources to the watch (defense-in-depth; hidden resources are LOCAL today).
        val networkResources = allResources.filter {
            it.id in selectedIds && !it.isHidden &&
                it.type in ResourceType.WATCH_TRANSFERABLE
        }
        val collected = collectPayloads(networkResources, editStamps)
        // S2882: the registered resources this phone declares unwanted on the watch when a selection exists.
        // S2909: when selectedIds is empty, there is no active selection to compute deselections against.
        val deselectedIds = if (selectedIds.isEmpty()) {
            emptyList()
        } else {
            allResources
                .filterNot { it.id in selectedIds }
                .map { it.id.toString() }
        }
        // S2507: every tombstone travels, whatever the selection holds. Selection says which
        // resources the watch should carry; it never says which deletions it may hear about, and
        // dropping a deselected resource's tombstone is what would let the watch resurrect it.
        val tombstones = wearResourceTombstoneStore.read()

        // S1781/S2909: an empty selection with no tombstones and no active deselections sends nothing.
        if (collected.payloads.isEmpty() && deselectedIds.isEmpty() && tombstones.orEmpty().isEmpty()) {
            Timber.d("S2909: empty selection push skipped, no DataItem sent to watch")
            return@runCatching SendResult(sent = 0, skipped = collected.skipped, deselected = 0)
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
        Timber.d("S2507: phone push leg carries ${syncPayload.tombstones.orEmpty().size} tombstone(s)")
        Timber.d("S2882: phone push leg declares ${deselectedIds.size} withdrawn resource(s)")
        val syncJson = gson.toJson(syncPayload)
        val bytes = syncJson.toByteArray(Charsets.UTF_8)
        wearableRepository.putDataItem(DATA_LAYER_PATH, bytes)
        Timber.i(
            "Sent ${collected.payloads.size} resources to watch " +
                "(${collected.skipped} skipped, ${deselectedIds.size} withdrawn)"
        )
        SendResult(collected.payloads.size, collected.skipped, deselectedIds.size)
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
