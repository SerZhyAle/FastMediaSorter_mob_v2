package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.os.Environment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Creates the Downloads folder as the first destination on a fresh install.
 * Condition: no destination with the Downloads path exists yet.
 */
class ProvisionDownloadsDestinationUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val resolveResourceIconUseCase: ResolveResourceIconUseCase
) {
    /**
     * Returns true if the Downloads destination was created; false if skipped.
     */
    suspend operator fun invoke(): Boolean {
        val downloadsPath = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .absolutePath

        if (!java.io.File(downloadsPath).isDirectory) return false

        val existing = resourceRepository.getAllResourcesSync()
        if (existing.any { it.isDestination && it.path == downloadsPath }) return false

        val destinationOrder = 0
        val destinationColor = DestinationColors.getColorForDestination(destinationOrder)
        val iconId = resolveResourceIconUseCase(
            path = downloadsPath,
            profile = ResourceProfile.NONE,
            type = ResourceType.LOCAL
        )

        val resource = MediaResource(
            id = 0,
            name = context.getString(R.string.resource_name_downloads),
            path = downloadsPath,
            type = ResourceType.LOCAL,
            profile = ResourceProfile.NONE,
            supportedMediaTypes = setOf(
                MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO,
                MediaType.GIF, MediaType.TEXT, MediaType.PDF, MediaType.EPUB,
                MediaType.OFFICE_DOCUMENT
            ),
            isDestination = true,
            destinationOrder = destinationOrder,
            destinationColor = destinationColor,
            isWritable = true,
            isReadOnly = false,
            allFiles = true,
            scanSubdirectories = true,
            displayOrder = Int.MAX_VALUE,
            createdDate = System.currentTimeMillis(),
            fileCount = 0,
            iconId = iconId
        )

        resourceRepository.addResource(resource)
        Timber.i("Provisioned Downloads destination on first launch")
        return true
    }
}
