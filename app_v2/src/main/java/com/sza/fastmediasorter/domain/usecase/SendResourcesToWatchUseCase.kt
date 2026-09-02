package com.sza.fastmediasorter.domain.usecase

import android.os.Build
import com.google.gson.Gson
import com.sza.fastmediasorter.data.repository.WearResourceSelectionRepositoryImpl
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearNetworkSourcePayload
import com.sza.fastmediasorter.domain.model.WearSyncPayload
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
    private val selectionRepository: WearResourceSelectionRepositoryImpl
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
            payloads.add(
                WearNetworkSourcePayload(
                    id = resource.id.toString(),
                    type = resource.type.name,
                    name = resource.name,
                    server = creds.server,
                    port = creds.port,
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
                    iconId = resource.iconId
                )
            )
            sent++
        }

        val syncPayload = WearSyncPayload(
            sentAt = System.currentTimeMillis(),
            phoneName = Build.MODEL.orEmpty(),
            sources = payloads
        )
        val syncJson = gson.toJson(syncPayload)
        val bytes = syncJson.toByteArray(Charsets.UTF_8)
        wearableRepository.putDataItem(DATA_LAYER_PATH, bytes)
        Timber.i("Sent $sent resources to watch ($skipped skipped)")
        SendResult(sent, skipped)
    }
}
