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

data class SendResult(
    val sent: Int,
    val skipped: Int
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

        // S1781: an empty selection means "nothing to send", never "send the whole registry".
        val selectedIds = selectionRepository.getSelectedIds()
        if (selectedIds.isEmpty()) {
            Timber.i("No resources are marked for the watch - nothing sent")
            return@runCatching SendResult(0, 0)
        }
        // S2502: read once for the whole batch - the map is small and every record consults it.
        val editStamps = wearResourceStampStore.readStamps()
        Timber.d("S2502: phone push leg entered, ${editStamps.size} edit stamp(s) known")
        val allResources = resourceRepository.getAllResourcesSync()
        // S1009: never push hidden resources to the watch (defense-in-depth; hidden resources are LOCAL today).
        val networkResources = allResources.filter {
            it.id in selectedIds && !it.isHidden &&
                it.type in listOf(ResourceType.SMB, ResourceType.FTP, ResourceType.SFTP)
        }

        var sent = 0
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
            // S2488: for SFTP, send the address that answers right now instead of the one recorded at
            // import - the companion's listening endpoint moves, and the watch has no way to notice.
            val endpoints = if (resource.type == ResourceType.SFTP) {
                runCatching { reachableEndpointProvider.orderedEndpoints(creds.server, creds.port) }
                    .onFailure {
                        Timber.w(it, "Endpoint resolution failed for ${resource.name} - sending stored address")
                    }
                    .getOrNull()
            } else {
                null
            }
            payloads.add(
                toPayload(resource, creds, password, endpoints, editStamps[resource.id.toString()])
            )
            sent++
        }

        val syncPayload = WearSyncPayload(
            sentAt = System.currentTimeMillis(),
            phoneName = Build.MODEL.orEmpty(),
            sources = payloads,
            // S2507: every tombstone travels, whatever the selection holds. Selection says which
            // resources the watch should carry; it never says which deletions it may hear about, and
            // dropping a deselected resource's tombstone is what would let the watch resurrect it.
            tombstones = wearResourceTombstoneStore.read()
        )
        Timber.d("S2507: phone push leg carries ${syncPayload.tombstones.size} tombstone(s)")
        val syncJson = gson.toJson(syncPayload)
        val bytes = syncJson.toByteArray(Charsets.UTF_8)
        wearableRepository.putDataItem(DATA_LAYER_PATH, bytes)
        Timber.i("Sent $sent resources to watch ($skipped skipped)")
        SendResult(sent, skipped)
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
