package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.MediaStoreRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class ScanLocalFoldersUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaStoreRepository: MediaStoreRepository
) {

    companion object {
        private const val VIRTUAL_PATH_RECENT = "virtual://recent"
    }

    suspend operator fun invoke(): Result<List<MediaResource>> = withContext(Dispatchers.IO) {
        try {
            val existingResources = repository.getAllResources().first()
            val existingPaths = existingResources.map { it.path }.toSet()
            
            // Get current settings for default values
            val settings = settingsRepository.getSettings().first()
            
            // Determine supported media types from settings
            val supportedMediaTypes = mutableSetOf<MediaType>()
            if (settings.supportImages) supportedMediaTypes.add(MediaType.IMAGE)
            if (settings.supportVideos) supportedMediaTypes.add(MediaType.VIDEO)
            if (settings.supportAudio) supportedMediaTypes.add(MediaType.AUDIO)
            if (settings.supportGifs) supportedMediaTypes.add(MediaType.GIF)
            if (settings.supportText) supportedMediaTypes.add(MediaType.TEXT)
            if (settings.supportPdf) supportedMediaTypes.add(MediaType.PDF)
            
            val resources = mutableListOf<MediaResource>()

            if (VIRTUAL_PATH_RECENT !in existingPaths) {
                resources.add(
                    MediaResource(
                        id = 0,
                        name = context.getString(R.string.recent_media),
                        path = VIRTUAL_PATH_RECENT,
                        type = ResourceType.LOCAL,
                        createdDate = System.currentTimeMillis(),
                        fileCount = 0,
                        isDestination = false,
                        destinationOrder = null,
                        isWritable = false,
                        slideshowInterval = settings.slideshowInterval,
                        scanSubdirectories = false,
                        supportedMediaTypes = supportedMediaTypes,
                        allFiles = settings.allFiles
                    )
                )
            }
            
            // Fetch standard Android folders (always returned, even if empty)
            val standardFolders = mediaStoreRepository.getStandardFolders()
            
            // Fetch all other folders from MediaStore (efficient query)
            val mediaFolders = mediaStoreRepository.getFoldersWithMedia(supportedMediaTypes)
            
            // Merge both lists, prioritizing standard folders
            val allFolders = (standardFolders + mediaFolders)
                .distinctBy { it.path }
            
            allFolders.forEach { folderInfo ->
                if (folderInfo.path !in existingPaths) {
                    // Use folder's contained types, or fall back to settings if empty
                    val resourceSupportedTypes = if (folderInfo.containedTypes.isEmpty()) {
                        supportedMediaTypes
                    } else {
                        folderInfo.containedTypes
                    }
                    
                    resources.add(
                        MediaResource(
                            id = 0, // 0 indicates new/transient resource
                            name = folderInfo.name,
                            path = folderInfo.path,
                            type = ResourceType.LOCAL,
                            createdDate = System.currentTimeMillis(),
                            fileCount = folderInfo.fileCount,
                            isDestination = false,
                            destinationOrder = null,
                            isWritable = true, // LOCAL resources are writable via MediaStore/SAF
                            slideshowInterval = settings.slideshowInterval,
                            scanSubdirectories = false, // MediaStore query is flat, no recursion needed
                            supportedMediaTypes = resourceSupportedTypes,
                            allFiles = settings.allFiles // Inherit global "All Files" setting
                        )
                    )
                }
            }
            
            // Sort by name
            resources.sortBy { it.name }
            
            Result.success(resources)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning local folders")
            Result.failure(e)
        }
    }
}
