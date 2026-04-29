package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.DisplayMode
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
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val resolveResourceIconUseCase: ResolveResourceIconUseCase
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
            comment = context.getString(R.string.virtual_comment_recent),
            path = LocalMediaScanner.VIRTUAL_PATH_RECENT,
            supportedMediaTypes = settings.getGloballyEnabledMediaTypes(),
            profile = ResourceProfile.NONE,
            displayOrder = displayOrder++
        )

        // 2. All Music
        if (BuildConfig.SUPPORT_AUDIO && settings.supportAudio) {
            createVirtualResource(
                name = context.getString(R.string.virtual_all_music),
                comment = context.getString(R.string.virtual_comment_all_music),
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
                comment = context.getString(R.string.virtual_comment_all_video),
                path = LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
                supportedMediaTypes = setOf(MediaType.VIDEO),
                profile = ResourceProfile.VIDEO_LIBRARY,
                displayOrder = displayOrder++
            )
        }

        // 4. Camera
        if (BuildConfig.SUPPORT_IMAGES) {
            val cameraImageTypes = buildSet {
                if (settings.supportImages) add(MediaType.IMAGE)
                if (settings.supportGifs) add(MediaType.GIF)
                if (settings.supportVideos) add(MediaType.VIDEO)
            }
            if (cameraImageTypes.isNotEmpty()) {
                createVirtualResource(
                    name = context.getString(R.string.virtual_camera_photos),
                    comment = context.getString(R.string.virtual_comment_camera_photos),
                    path = LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS,
                    supportedMediaTypes = cameraImageTypes,
                    profile = ResourceProfile.PHOTO_STORAGE,
                    displayMode = DisplayMode.GRID,
                    sortMode = SortMode.DATE_DESC,
                    displayOrder = displayOrder++
                )
            }
        }

        // 5. All Images
        if (BuildConfig.SUPPORT_IMAGES) {
            val imageTypes = buildSet {
                if (settings.supportImages) add(MediaType.IMAGE)
                if (settings.supportGifs) add(MediaType.GIF)
            }
            if (imageTypes.isNotEmpty()) {
                createVirtualResource(
                    name = context.getString(R.string.virtual_all_images),
                    comment = context.getString(R.string.virtual_comment_all_images),
                    path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
                    supportedMediaTypes = imageTypes,
                    profile = ResourceProfile.PHOTO_STORAGE,
                    displayOrder = displayOrder++
                )
            }
        }

        // 6. All Documents
        if (BuildConfig.SUPPORT_DOCUMENTS) {
            val docTypes = buildSet {
                if (settings.supportText) add(MediaType.TEXT)
                if (settings.supportPdf) add(MediaType.PDF)
                if (settings.supportEpub) add(MediaType.EPUB)
            }
            if (docTypes.isNotEmpty()) {
                createVirtualResource(
                    name = context.getString(R.string.virtual_all_docs),
                    comment = context.getString(R.string.virtual_comment_all_docs),
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
        comment: String? = null,
        path: String,
        supportedMediaTypes: Set<MediaType>,
        profile: ResourceProfile,
        displayOrder: Int,
        displayMode: DisplayMode = DisplayMode.LIST,
        sortMode: SortMode = SortMode.NAME_ASC
    ) {
        val resource = MediaResource(
            id = 0,
            name = name,
            comment = comment,
            path = path,
            type = ResourceType.LOCAL,
            createdDate = System.currentTimeMillis(),
            fileCount = 0,
            isDestination = false,
            destinationOrder = null,
            isWritable = false,
            scanSubdirectories = false,
            supportedMediaTypes = supportedMediaTypes,
            sortMode = sortMode,
            profile = profile,
            displayMode = displayMode,
            allFiles = false,
            displayOrder = displayOrder,
            // Assign a fixed icon for predefined virtual resources on first provisioning
            iconId = resolveResourceIconUseCase(path = path, profile = profile, type = ResourceType.LOCAL)
        )
        resourceRepository.addResource(resource)
    }
}
