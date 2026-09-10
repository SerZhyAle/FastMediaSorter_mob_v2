package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import android.os.Build
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearNetworkSourcePayload
import com.sza.fastmediasorter.wear.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

class ExportSourcesUseCase @Inject constructor(
    private val networkSourceRepository: NetworkSourceRepository,
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    private val envelopeCodec = WearEventEnvelopeCodec()

    suspend operator fun invoke(): Result<Int> = runCatching {
        val sources = networkSourceRepository.getAllSources()
        val payloads = sources.map { source ->
            WearNetworkSourcePayload(
                id = source.id,
                type = source.type.name,
                name = source.name,
                server = source.server,
                port = source.port,
                username = source.username,
                password = source.password,
                shareName = source.shareName,
                basePath = source.basePath,
                domain = source.domain,
                sshPrivateKey = source.sshPrivateKey,
                hostKeyFingerprint = source.hostKeyFingerprint,
                iconId = source.iconId,
                supportedMediaTypes = source.supportedMediaTypes,
                allFiles = source.allFiles,
                // S2502: without this the phone leg would carry no edit time and the exchange would
                // rank records in one direction only, which is the asymmetry the ticket removes.
                lastEditedAt = source.lastEditedAt
            )
        }
        // S2502: one reading for both, so the payload's own send time and the envelope's cannot drift
        // apart and describe two different moments for one exchange.
        val sentAt = System.currentTimeMillis()
        Timber.d("S2502: watch export leg built ${payloads.size} record(s) with sentAt=$sentAt")
        val payload = WearSourcesExportPayload(
            sources = payloads,
            watchName = localWatchDisplayName(),
            sentAt = sentAt,
            // S2507: the deletions this watch made. Without them the phone cannot tell a resource the
            // user removed here from one it has never seen, and hands the removed one straight back.
            tombstones = networkSourceRepository.getTombstones()
        )
        Timber.d("S2507: watch export leg carries ${payload.tombstones.orEmpty().size} tombstone(s)")
        val envelopeBytes = envelopeCodec.encode(
            WearEventEnvelope(
                eventType = WearDataLayerPaths.EVENT_SOURCES_EXPORT,
                sentAt = sentAt,
                data = gson.toJson(payload).toByteArray()
            )
        )

        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (nodes.isEmpty()) error("No phone connected")

        for (node in nodes) {
            Wearable.getMessageClient(context)
                .sendMessage(node.id, WearDataLayerPaths.SOURCES_EXPORT, envelopeBytes)
                .await()
        }

        payloads.size
    }

    /**
     * S2868: the phone shows this name in its sources-import card, so the card reads the same human
     * name the phone's settings row does - the node's display name with the model code in
     * parentheses stripped. [Build.MODEL] stays the fallback for a bridge that cannot name the node.
     */
    private suspend fun localWatchDisplayName(): String {
        val nodeName = runCatching { Wearable.getNodeClient(context).localNode.await().displayName }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(::withoutModelCode)
        val name = nodeName ?: Build.MODEL
        Timber.d("S2868: export watchName=%s", name)
        return name
    }

    private fun withoutModelCode(displayName: String): String {
        val withoutModelCode = displayName.substringBefore('(').trim()
        return withoutModelCode.ifBlank { displayName.trim() }
    }
}
