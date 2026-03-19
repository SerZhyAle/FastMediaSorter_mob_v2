package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_AUDIO
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_DOCS
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_IMAGES
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_VIDEO
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
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
    @param:ApplicationContext private val context: Context,
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
            val supportedMediaTypes = settings.getGloballyEnabledMediaTypes().toMutableSet()
            
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
                        isWritable = true,
                        slideshowInterval = settings.slideshowInterval,
                        scanSubdirectories = false,
                        supportedMediaTypes = supportedMediaTypes,
                        allFiles = settings.allFiles
                    )
                )
            }

            // Virtual aggregate: All Music
            if (BuildConfig.SUPPORT_AUDIO && VIRTUAL_PATH_ALL_AUDIO !in existingPaths && settings.supportAudio) {
                resources.add(
                    MediaResource(
                        id = 0,
                        name = context.getString(R.string.virtual_all_music),
                        path = VIRTUAL_PATH_ALL_AUDIO,
                        type = ResourceType.LOCAL,
                        createdDate = System.currentTimeMillis(),
                        fileCount = 0,
                        isDestination = false,
                        destinationOrder = null,
                        isWritable = true,
                        slideshowInterval = settings.slideshowInterval,
                        scanSubdirectories = false,
                        supportedMediaTypes = setOf(MediaType.AUDIO),
                        sortMode = SortMode.NAME_ASC,
                        profile = ResourceProfile.AUDIO_LIBRARY,
                        allFiles = false
                    )
                )
            }

            // Virtual aggregate: All Videos
            if (VIRTUAL_PATH_ALL_VIDEO !in existingPaths && settings.supportVideos) {
                resources.add(
                    MediaResource(
                        id = 0,
                        name = context.getString(R.string.virtual_all_video),
                        path = VIRTUAL_PATH_ALL_VIDEO,
                        type = ResourceType.LOCAL,
                        createdDate = System.currentTimeMillis(),
                        fileCount = 0,
                        isDestination = false,
                        destinationOrder = null,
                        isWritable = true,
                        slideshowInterval = settings.slideshowInterval,
                        scanSubdirectories = false,
                        supportedMediaTypes = setOf(MediaType.VIDEO),
                        sortMode = SortMode.NAME_ASC,
                        profile = ResourceProfile.VIDEO_LIBRARY,
                        allFiles = false
                    )
                )
            }

            // Virtual aggregate: All Images
            if (BuildConfig.SUPPORT_IMAGES && VIRTUAL_PATH_ALL_IMAGES !in existingPaths) {
                val imageTypes = buildSet {
                    if (settings.supportImages) add(MediaType.IMAGE)
                    if (settings.supportGifs) add(MediaType.GIF)
                }
                if (imageTypes.isNotEmpty()) {
                    resources.add(
                        MediaResource(
                            id = 0,
                            name = context.getString(R.string.virtual_all_images),
                            path = VIRTUAL_PATH_ALL_IMAGES,
                            type = ResourceType.LOCAL,
                            createdDate = System.currentTimeMillis(),
                            fileCount = 0,
                            isDestination = false,
                            destinationOrder = null,
                            isWritable = true,
                            slideshowInterval = settings.slideshowInterval,
                            scanSubdirectories = false,
                            supportedMediaTypes = imageTypes,
                            sortMode = SortMode.NAME_ASC,
                            profile = ResourceProfile.PHOTO_STORAGE,
                            allFiles = false
                        )
                    )
                }
            }

            // Virtual aggregate: All Documents
            if (BuildConfig.SUPPORT_DOCUMENTS) {
                val docTypes = buildSet {
                    if (settings.supportText) add(MediaType.TEXT)
                    if (settings.supportPdf) add(MediaType.PDF)
                    if (settings.supportEpub) add(MediaType.EPUB)
                }
                if (docTypes.isNotEmpty() && VIRTUAL_PATH_ALL_DOCS !in existingPaths) {
                    resources.add(
                        MediaResource(
                            id = 0,
                            name = context.getString(R.string.virtual_all_docs),
                            path = VIRTUAL_PATH_ALL_DOCS,
                            type = ResourceType.LOCAL,
                            createdDate = System.currentTimeMillis(),
                            fileCount = 0,
                            isDestination = false,
                            destinationOrder = null,
                            isWritable = true,
                            slideshowInterval = settings.slideshowInterval,
                            scanSubdirectories = false,
                            supportedMediaTypes = docTypes,
                            sortMode = SortMode.NAME_ASC,
                            profile = ResourceProfile.DOCUMENTS,
                            allFiles = false
                        )
                    )
                }
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
