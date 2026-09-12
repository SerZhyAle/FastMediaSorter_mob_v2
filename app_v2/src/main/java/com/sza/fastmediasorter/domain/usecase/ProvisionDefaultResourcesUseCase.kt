package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.util.VirtualPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates predefined virtual resources on first app launch.
 *
 * Idempotent: checks each virtual path individually so that a partial provisioning
 * (e.g. interrupted by viewModelScope cancellation during WelcomeActivity) is
 * completed on the next call instead of being silently skipped.
 *
 * S2634: that per-path check reads ONE snapshot and then decides up to six inserts against it, so
 * two passes overlapping in time both see an empty table and both write all six records - measured
 * on a first run as six virtual resources rendered twice. Three callers can start a pass
 * (`MainViewModel.init`, [com.sza.fastmediasorter.domain.usecase.launcher.SeedLauncherDesktopUseCase],
 * [com.sza.fastmediasorter.domain.usecase.launcher.SyncEnabledResourceTilesUseCase]), so the class is
 * `@Singleton` and the whole pass runs under one mutex: a per-injection-point instance would each get
 * a lock of its own and serialise nothing.
 *
 * displayOrder follows the canonical slot positions (0-5) so that resources
 * created in a repair pass land in the same order as a fresh install.
 */
@Singleton
class ProvisionDefaultResourcesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val resolveResourceIconUseCase: ResolveResourceIconUseCase,
    private val mediaCapabilities: MediaCapabilities
) {
    private val provisionLock = Mutex()

    /**
     * Returns true if at least one predefined resource was provisioned; false if all already exist.
     */
    suspend operator fun invoke(): Boolean = provisionLock.withLock { provisionUnderLock() }

    // The two suppressions carry debt this ticket did not create: the detekt baseline held both
    // findings under the old name `invoke`, and moving the same body behind the lock renamed it.
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun provisionUnderLock(): Boolean {
        val existing = resourceRepository.getAllResources().first()
        collapseDuplicatePredefinedResources(existing)
        val existingPaths = existing.map { it.path }.toSet()
        val settings = settingsRepository.getSettings().first()

        // Slot counter - increments for every predefined resource slot (created or skipped),
        // keeping displayOrder stable whether this is a fresh install or a repair pass.
        var displayOrder = 0
        var provisionedCount = 0

        // 1. Recent - show all files by default (S0059: users expect full file history, not media-only)
        if (LocalMediaScanner.VIRTUAL_PATH_RECENT !in existingPaths) {
            createVirtualResource(
                name = context.getString(R.string.recent_media),
                comment = context.getString(R.string.virtual_comment_recent),
                path = LocalMediaScanner.VIRTUAL_PATH_RECENT,
                supportedMediaTypes = settings.getGloballyEnabledMediaTypes(),
                profile = ResourceProfile.NONE,
                displayOrder = displayOrder,
                allFiles = true
            )
            provisionedCount++
        }
        displayOrder++

        // 2. All Music
        if (mediaCapabilities.supportsAudio && settings.supportAudio) {
            if (LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO !in existingPaths) {
                createVirtualResource(
                    name = context.getString(R.string.virtual_all_music),
                    comment = context.getString(R.string.virtual_comment_all_music),
                    path = LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
                    supportedMediaTypes = setOf(MediaType.AUDIO),
                    profile = ResourceProfile.AUDIO_LIBRARY,
                    displayOrder = displayOrder
                )
                provisionedCount++
            }
            displayOrder++
        }

        // 3. All Videos
        if (settings.supportVideos) {
            if (LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO !in existingPaths) {
                createVirtualResource(
                    name = context.getString(R.string.virtual_all_video),
                    comment = context.getString(R.string.virtual_comment_all_video),
                    path = LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
                    supportedMediaTypes = setOf(MediaType.VIDEO),
                    profile = ResourceProfile.VIDEO_LIBRARY,
                    displayOrder = displayOrder
                )
                provisionedCount++
            }
            displayOrder++
        }

        // 4. Camera
        if (mediaCapabilities.supportsImages) {
            val cameraImageTypes = buildSet {
                if (settings.supportImages) add(MediaType.IMAGE)
                if (settings.supportGifs) add(MediaType.GIF)
                if (settings.supportVideos) add(MediaType.VIDEO)
            }
            if (cameraImageTypes.isNotEmpty()) {
                if (LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS !in existingPaths) {
                    createVirtualResource(
                        name = context.getString(R.string.virtual_camera_photos),
                        comment = context.getString(R.string.virtual_comment_camera_photos),
                        path = LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS,
                        supportedMediaTypes = cameraImageTypes,
                        profile = ResourceProfile.PHOTO_STORAGE,
                        displayMode = DisplayMode.GRID,
                        sortMode = SortMode.DATE_DESC,
                        displayOrder = displayOrder
                    )
                    provisionedCount++
                }
                displayOrder++
            }
        }

        // 5. All Images
        if (mediaCapabilities.supportsImages) {
            val imageTypes = buildSet {
                if (settings.supportImages) add(MediaType.IMAGE)
                if (settings.supportGifs) add(MediaType.GIF)
            }
            if (imageTypes.isNotEmpty()) {
                if (LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES !in existingPaths) {
                    createVirtualResource(
                        name = context.getString(R.string.virtual_all_images),
                        comment = context.getString(R.string.virtual_comment_all_images),
                        path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
                        supportedMediaTypes = imageTypes,
                        profile = ResourceProfile.PHOTO_STORAGE,
                        displayOrder = displayOrder
                    )
                    provisionedCount++
                }
                displayOrder++
            }
        }

        // 6. All Documents
        if (mediaCapabilities.supportsDocuments) {
            val docTypes = buildSet {
                if (settings.supportText) add(MediaType.TEXT)
                if (settings.supportPdf) add(MediaType.PDF)
                if (settings.supportEpub) add(MediaType.EPUB)
                if (settings.supportOfficeDocuments) add(MediaType.OFFICE_DOCUMENT)
            }
            if (docTypes.isNotEmpty()) {
                if (LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS !in existingPaths) {
                    createVirtualResource(
                        name = context.getString(R.string.virtual_all_docs),
                        comment = context.getString(R.string.virtual_comment_all_docs),
                        path = LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS,
                        supportedMediaTypes = docTypes,
                        profile = ResourceProfile.DOCUMENTS,
                        displayOrder = displayOrder
                    )
                    provisionedCount++
                }
                displayOrder++
            }
        }

        if (provisionedCount == 0) return false
        Timber.i("Provisioned %d default virtual resources on first launch", provisionedCount)
        return true
    }

    /**
     * Deletes the extra records an overlapping pass left behind, keeping the lowest id per path.
     *
     * S2634: the lock above closes the window from now on, but an install that already ran the race
     * carries the duplicates forever - nothing else ever removes them, and the user sees every
     * aggregate twice on the main screen. Two records under one `virtual://` path are always a
     * defect: the add-resource surface refuses that path by name (`virtual_resource_already_added`),
     * so no user can have created the second one. The lowest id is the one the launcher's own
     * `firstOrNull { it.path == .. }` already resolves to, so a seeded desktop keeps pointing at the
     * record that survives.
     */
    private suspend fun collapseDuplicatePredefinedResources(existing: List<MediaResource>) {
        val byPath = existing.filter { it.path in PREDEFINED_VIRTUAL_PATHS }.groupBy { it.path }
        for ((path, records) in byPath) {
            if (records.size > 1) {
                records.sortedBy { it.id }.drop(1).forEach { resourceRepository.deleteResource(it.id) }
                Timber.w("Removed %d duplicate record(s) of predefined resource %s", records.size - 1, path)
            }
        }
    }

    private suspend fun createVirtualResource(
        name: String,
        comment: String? = null,
        path: String,
        supportedMediaTypes: Set<MediaType>,
        profile: ResourceProfile,
        displayOrder: Int,
        displayMode: DisplayMode = DisplayMode.LIST,
        sortMode: SortMode = SortMode.NAME_ASC,
        allFiles: Boolean = false
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
            isWritable = VirtualPathUtils.isAggregateVirtualPath(path),
            scanSubdirectories = false,
            supportedMediaTypes = supportedMediaTypes,
            sortMode = sortMode,
            profile = profile,
            displayMode = displayMode,
            allFiles = allFiles,
            displayOrder = displayOrder,
            // Assign a fixed icon for predefined virtual resources on first provisioning
            iconId = resolveResourceIconUseCase(path = path, profile = profile, type = ResourceType.LOCAL)
        )
        resourceRepository.addResource(resource)
    }

    private companion object {
        /** The six slots this use case owns; the collapse pass must not judge any other path. */
        val PREDEFINED_VIRTUAL_PATHS = setOf(
            LocalMediaScanner.VIRTUAL_PATH_RECENT,
            LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
            LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
            LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS,
            LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
            LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS
        )
    }
}
