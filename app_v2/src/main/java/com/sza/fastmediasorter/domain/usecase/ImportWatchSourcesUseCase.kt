package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class ImportWatchResult(val added: Int, val skipped: Int)

class ImportWatchSourcesUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val gson: Gson
) {

    suspend operator fun invoke(payload: WearSourcesExportPayload): Result<ImportWatchResult> =
        runCatching {
            var added = 0
            var skipped = 0

            for (source in payload.sources) {
                val existing = credentialsRepository.getByTypeServerAndPort(
                    type = source.type,
                    server = source.server,
                    port = source.port
                )
                if (existing != null) {
                    skipped++
                    continue
                }

                val credentialId = UUID.randomUUID().toString()
                val entity = NetworkCredentialsEntity.create(
                    credentialId = credentialId,
                    type = source.type,
                    server = source.server,
                    port = source.port,
                    username = source.username,
                    plaintextPassword = source.password,
                    domain = source.domain,
                    shareName = source.shareName,
                    sshPrivateKey = source.sshPrivateKey
                )
                credentialsRepository.insert(entity)

                val resourceType = runCatching { ResourceType.valueOf(source.type) }
                    .getOrDefault(ResourceType.SMB)
                val resource = MediaResource(
                    name = source.name,
                    path = source.basePath,
                    type = resourceType,
                    credentialsId = credentialId
                )
                resourceRepository.addResource(resource)
                added++
            }

            ImportWatchResult(added = added, skipped = skipped)
        }
}
