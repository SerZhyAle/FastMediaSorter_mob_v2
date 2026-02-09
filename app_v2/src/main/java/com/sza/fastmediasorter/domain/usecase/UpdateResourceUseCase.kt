package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import javax.inject.Inject
import timber.log.Timber

class UpdateResourceUseCase @Inject constructor(
    private val repository: ResourceRepository
) {
    suspend operator fun invoke(resource: MediaResource): Result<Unit> {
        return try {
            Timber.d("UpdateResourceUseCase: Updating resource '${resource.name}', scanSubdirectories=${resource.scanSubdirectories}, showSubfoldersAsItems=${resource.showSubfoldersAsItems}, allFiles=${resource.allFiles}, type=${resource.type}")
            Timber.i("╔═══ UPDATE RESOURCE usecase ═══")
            Timber.i("║ supportedMediaTypes: ${resource.supportedMediaTypes.map { it.name }}")
            Timber.i("║ Size: ${resource.supportedMediaTypes.size}/7")
            Timber.i("╚═══════════════════════════════")
            repository.updateResource(resource)
            Timber.d("UpdateResourceUseCase: Successfully updated resource '${resource.name}'")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "UpdateResourceUseCase: Failed to update resource '${resource.name}'")
            Result.failure(e)
        }
    }
}
