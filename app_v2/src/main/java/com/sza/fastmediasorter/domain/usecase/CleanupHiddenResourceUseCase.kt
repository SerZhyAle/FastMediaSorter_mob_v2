package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.ResourceRepository
import javax.inject.Inject

/**
 * S1009: remove an ad-hoc hidden local-folder resource once the scheduled operation that owned it is
 * deleted or re-pointed to a different resource. Strict 1:1 (no reference counting - owner decision);
 * the repository guard ensures only `is_hidden` rows are ever deleted, so a reused visible resource is
 * preserved. Null / unset (0) ids are a no-op.
 */
class CleanupHiddenResourceUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
) {
    suspend operator fun invoke(resourceId: Long?) {
        if (resourceId == null || resourceId == 0L) return
        resourceRepository.deleteResourceIfHidden(resourceId)
    }
}
