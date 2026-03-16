package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Creates the four predefined virtual resources on first app launch.
 * Condition: DB contains zero resources.
 */
class ProvisionDefaultResourcesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Returns true if provisioning was performed; false if skipped (not first launch).
     */
    suspend operator fun invoke(): Boolean {
        val existingResources = resourceRepository.getAllResources().first()
        if (existingResources.isNotEmpty()) return false

        val settings = settingsRepository.getSettings().first()
        var displayOrder = 0

        // 1. Recent
        createVirtualResource(
            name = context.getString(R.string.recent_media),
            path = LocalMediaScanner.VIRTUAL_PATH_RECENT,
            supportedMediaTypes = settings.getGloballyEnabledMediaTypes(),
            profile = ResourceProfile.NONE,
            displayOrder = displayOrder++
        )

        // 2. All Music
        if (BuildConfig.SUPPORT_AUDIO && settings.supportAudio) {
            createVirtualResource(
                name = context.getString(R.string.virtual_all_music),
                path = LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
                supportedMediaTypes = setOf(MediaType.AUDIO),
                profile = ResourceProfile.AUDIO_LIBRARY,
                displayOrder = displayOrder++
            )
        }

        // 3. All Videos
        if (settings.supportVideos) {
            createVirtualResource(
                name = context.getString(R.string.virtual_all_video),
                path = LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
                supportedMediaTypes = setOf(MediaType.VIDEO),
                profile = ResourceProfile.VIDEO_LIBRARY,
                displayOrder = displayOrder++
            )
        }

        // 4. All Images
        if (BuildConfig.SUPPORT_IMAGES) {
            val imageTypes = buildSet {
                if (settings.supportImages) add(MediaType.IMAGE)
                if (settings.supportGifs) add(MediaType.GIF)
            }
            if (imageTypes.isNotEmpty()) {
                createVirtualResource(
                    name = context.getString(R.string.virtual_all_images),
                    path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
                    supportedMediaTypes = imageTypes,
                    profile = ResourceProfile.PHOTO_STORAGE,
                    displayOrder = displayOrder++
                )
            }
        }

        // 5. All Documents
        if (BuildConfig.SUPPORT_DOCUMENTS) {
            val docTypes = buildSet {
                if (settings.supportText) add(MediaType.TEXT)
                if (settings.supportPdf) add(MediaType.PDF)
                if (settings.supportEpub) add(MediaType.EPUB)
            }
            if (docTypes.isNotEmpty()) {
                createVirtualResource(
                    name = context.getString(R.string.virtual_all_docs),
                    path = LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS,
                    supportedMediaTypes = docTypes,
                    profile = ResourceProfile.DOCUMENTS,
                    displayOrder = displayOrder++
                )
            }
        }

        Timber.i("Provisioned %d default virtual resources on first launch", displayOrder)
        return true
    }

    private suspend fun createVirtualResource(
        name: String,
        path: String,
        supportedMediaTypes: Set<MediaType>,
        profile: ResourceProfile,
        displayOrder: Int
    ) {
        val resource = MediaResource(
            id = 0,
            name = name,
            path = path,
            type = ResourceType.LOCAL,
            createdDate = System.currentTimeMillis(),
            fileCount = 0,
            isDestination = false,
            destinationOrder = null,
            isWritable = true,
            scanSubdirectories = false,
            supportedMediaTypes = supportedMediaTypes,
            sortMode = SortMode.NAME_ASC,
            profile = profile,
            allFiles = false,
            displayOrder = displayOrder
        )
        resourceRepository.addResource(resource)
    }
}
