package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

class RefreshResourceFileCountsUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaScannerFactory: MediaScannerFactory,
    private val resolveScanFilter: ResolveScanFilterUseCase,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(resourceIds: Collection<Long>) {
        val uniqueIds = resourceIds.distinct()
        if (uniqueIds.isEmpty()) return

        val settings = try {
            settingsRepository.getSettings().first()
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.w(e, "Resource count refresh skipped: settings unavailable")
            return
        }

        uniqueIds.forEach { resourceId -> refresh(resourceId, settings) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun refresh(resourceId: Long, settings: AppSettings) {
        try {
            val resource = resourceRepository.getResourceById(resourceId) ?: return
            val scanFilter = resolveScanFilter(resource, settings)
            val fileCount = mediaScannerFactory.getScanner(resource.type).getFileCount(
                path = resource.path,
                supportedTypes = scanFilter.mediaTypes,
                sizeFilter = scanFilter.sizeFilter,
                credentialsId = resource.credentialsId,
                scanSubdirectories = resource.scanSubdirectories,
            )
            if (fileCount != resource.fileCount) {
                resourceRepository.updateResource(resource.copy(fileCount = fileCount))
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.w(e, "Resource count refresh failed for id=%d", resourceId)
        }
    }
}
