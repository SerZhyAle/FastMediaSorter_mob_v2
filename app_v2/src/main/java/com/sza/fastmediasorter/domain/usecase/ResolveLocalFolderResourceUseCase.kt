package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import javax.inject.Inject

/**
 * S1009: turn an ad-hoc local folder into a resource id for a scheduled-operation FK.
 *
 * Reuses a matching VISIBLE local resource (owner dedup decision) as the FK target. A match that is
 * itself HIDDEN is ignored - it belongs 1:1 to another operation, so reusing it would break orphan
 * cleanup - and a fresh hidden resource is created instead. Creation goes through the FTS-safe
 * [ResourceRepository.addResource] (transactional insert + FTS row), never the FTS-skipping delete path.
 */
class ResolveLocalFolderResourceUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
) {
    suspend operator fun invoke(folderPath: String, folderName: String, isWritable: Boolean): Long {
        val existing = resourceRepository.getLocalResourceByPath(folderPath)
        if (existing != null && !existing.isHidden) {
            return existing.id
        }
        val hidden = MediaResource(
            name = folderName,
            path = folderPath,
            type = ResourceType.LOCAL,
            isWritable = isWritable,
            isReadOnly = !isWritable,
            scanSubdirectories = true,
            allFiles = true,
            isHidden = true,
        )
        return resourceRepository.addResource(hidden)
    }
}
