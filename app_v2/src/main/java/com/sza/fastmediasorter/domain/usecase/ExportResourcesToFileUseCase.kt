package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.resourceshare.ResourceShareSerializer
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Writes the given resources (with their network credentials) into a share file at [target] (S0422).
 *
 * Key-auth SFTP resources are skipped: their credential carries only a bundled-asset SSH key, which
 * is not portable to another device, so exporting them would yield an unusable resource.
 */
class ExportResourcesToFileUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val serializer: ResourceShareSerializer,
) {
    sealed interface ExportResult {
        data class Success(val exported: Int, val skippedKeyAuth: Int) : ExportResult
        data class Failure(val error: Throwable) : ExportResult
    }

    suspend operator fun invoke(resourceIds: List<Long>, target: Uri): ExportResult =
        withContext(Dispatchers.IO) {
            try {
                val resources = resourceIds.mapNotNull { resourceRepository.getResourceById(it) }
                val credentialsById = mutableMapOf<String, NetworkCredentialsEntity>()
                val exportable = mutableListOf<MediaResource>()
                var skippedKeyAuth = 0

                for (resource in resources) {
                    val credId = resource.credentialsId
                    val credential = credId?.let { credentialsRepository.getByCredentialId(it) }
                    val keyOnly = credential != null &&
                        credential.encryptedPassword.isEmpty() &&
                        credential.sshPrivateKey != null
                    if (keyOnly) {
                        skippedKeyAuth++
                        continue
                    }
                    if (credId != null && credential != null) {
                        credentialsById[credId] = credential
                    }
                    exportable += resource
                }

                val xml = serializer.serialize(exportable, credentialsById)
                val stream = context.contentResolver.openOutputStream(target)
                    ?: return@withContext ExportResult.Failure(
                        IllegalStateException("Cannot open output stream for $target")
                    )
                stream.use { it.write(xml.toByteArray(Charsets.UTF_8)) }
                ExportResult.Success(exported = exportable.size, skippedKeyAuth = skippedKeyAuth)
            } catch (e: Exception) {
                Timber.e(e, "Resource export failed")
                ExportResult.Failure(e)
            }
        }
}
