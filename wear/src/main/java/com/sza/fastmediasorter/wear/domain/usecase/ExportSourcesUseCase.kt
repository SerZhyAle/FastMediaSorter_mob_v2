package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import android.os.Build
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
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
                sshPrivateKey = source.sshPrivateKey
            )
        }
        val payload = WearSourcesExportPayload(
            sources = payloads,
            watchName = Build.MODEL
        )
        val envelopeBytes = gson.toJson(
            WearEventEnvelope(
                eventType = WearDataLayerPaths.EVENT_SOURCES_EXPORT,
                sentAt = System.currentTimeMillis(),
                data = gson.toJson(payload).toByteArray()
            )
        ).toByteArray()

        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (nodes.isEmpty()) error("No phone connected")

        for (node in nodes) {
            Wearable.getMessageClient(context)
                .sendMessage(node.id, WearDataLayerPaths.SOURCES_EXPORT, envelopeBytes)
                .await()
        }

        payloads.size
    }
}
