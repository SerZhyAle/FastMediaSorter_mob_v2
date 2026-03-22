package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

class MigrateCameraResourceUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository
) {
    suspend operator fun invoke() {
        val oldCameraPath = "/storage/emulated/0/DCIM/Camera"
        val resource = resourceRepository.getAllResources().first()
            .firstOrNull { it.path == oldCameraPath }
            ?: return

        resourceRepository.updateResource(
            resource.copy(
                path = LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS,
                sortMode = SortMode.DATE_DESC
            )
        )
        Timber.i("Migrated Camera resource from physical path to virtual://camera_photos")
    }
}
