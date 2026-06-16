package com.sza.fastmediasorter.domain.usecase

import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Outcome of resolving the target resource for "Open in FastMediaSorter".
 * [Resolved] carries the resource to open plus the absolute path of the file to land on.
 * [NotResolvable] means the file has no reachable filesystem location (network / SAF-only).
 */
sealed interface OpenInFmsTarget {
    data class Resolved(val resourceId: Long, val absoluteFilePath: String) : OpenInFmsTarget
    data object NotResolvable : OpenInFmsTarget
}

/**
 * Resolves which resource the in-app player should open when "Open in FastMediaSorter" is invoked
 * from a standalone player. Resolution chain (strict order, owner-confirmed):
 *
 * 1. Local-path gate - non-local files are [OpenInFmsTarget.NotResolvable].
 * 2. Reuse the predefined «All Files» resource if it already exists (never created here).
 * 3. Else reuse the type-aggregate resource matching the file's media family (All Photos / Video /
 *    Music / Documents), but only when the flavor supports that media slot.
 * 4. Else reuse a previously auto-created folder resource for that folder, or create a new persistent
 *    one named by the folder. Its supported types follow the file's family, or every supported type
 *    when the user's «all files» setting is enabled (profile ALL_FILES in that case).
 */
class ResolveOpenInFmsTargetUseCase @Inject constructor(
    private val resolveLocalPathFromUriUseCase: ResolveLocalPathFromUriUseCase,
    private val ensureAllFilesPredefinedResourceUseCase: EnsureAllFilesPredefinedResourceUseCase,
    private val resolveResourceIconUseCase: ResolveResourceIconUseCase,
    private val addResourceUseCase: AddResourceUseCase,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities
) {
    suspend operator fun invoke(uri: Uri, mediaType: MediaType): OpenInFmsTarget =
        withContext(Dispatchers.IO) {
            val resolution = resolveLocalPathFromUriUseCase(uri)
            if (resolution !is LocalPathResolution.Local) return@withContext OpenInFmsTarget.NotResolvable

            val absolutePath = resolution.absolutePath
            val parentFolderPath = resolution.parentFolderPath

            resolveExistingAllFiles(absolutePath)?.let { return@withContext it }
            resolveExistingAggregate(absolutePath, mediaType)?.let { return@withContext it }

            resolveOrCreateFolderResource(absolutePath, parentFolderPath, mediaType)
        }

    private suspend fun resolveExistingAllFiles(absolutePath: String): OpenInFmsTarget? {
        val allFiles = resourceRepository.getAllResourcesSync()
            .firstOrNull { ensureAllFilesPredefinedResourceUseCase.isPredefinedResource(it) }
            ?: return null
        return OpenInFmsTarget.Resolved(allFiles.id, absolutePath)
    }

    private suspend fun resolveExistingAggregate(absolutePath: String, mediaType: MediaType): OpenInFmsTarget? {
        val aggregatePath = aggregatePathForFamily(mediaType) ?: return null
        val aggregate = resourceRepository.getAllResourcesSync()
            .firstOrNull { it.type == ResourceType.LOCAL && it.path == aggregatePath }
            ?: return null
        return OpenInFmsTarget.Resolved(aggregate.id, absolutePath)
    }

    private suspend fun resolveOrCreateFolderResource(
        absolutePath: String,
        parentFolderPath: String,
        mediaType: MediaType
    ): OpenInFmsTarget {
        resourceRepository.getLocalResourceByPath(parentFolderPath)?.let {
            return OpenInFmsTarget.Resolved(it.id, absolutePath)
        }

        val allFilesEnabled = settingsRepository.getSettings().first().allFiles
        val supportedTypes = if (allFilesEnabled) MediaType.entries.toSet() else setOf(mediaType)
        val profile = if (allFilesEnabled) ResourceProfile.ALL_FILES else ResourceProfile.NONE
        val folder = File(parentFolderPath)

        val resource = MediaResource(
            id = 0,
            name = folder.name.ifBlank { parentFolderPath },
            path = parentFolderPath,
            type = ResourceType.LOCAL,
            supportedMediaTypes = supportedTypes,
            sortMode = SortMode.NAME_ASC,
            displayMode = DisplayMode.LIST,
            createdDate = System.currentTimeMillis(),
            fileCount = 0,
            isWritable = folder.exists() && folder.canWrite(),
            scanSubdirectories = false,
            allFiles = allFilesEnabled,
            profile = profile,
            iconId = resolveResourceIconUseCase(
                path = parentFolderPath,
                profile = profile,
                type = ResourceType.LOCAL
            )
        )

        val createdId = addResourceUseCase(resource, addToTop = true).getOrThrow()
        Timber.i("Open-in-FMS auto-created folder resource: id=%d path=%s", createdId, parentFolderPath)
        return OpenInFmsTarget.Resolved(createdId, absolutePath)
    }

    /**
     * Aggregate virtual path for the file's media family, or null when the flavor lacks that media
     * slot (so the chain skips to folder-resource creation instead of pointing at a missing aggregate).
     */
    private fun aggregatePathForFamily(mediaType: MediaType): String? = when (mediaType) {
        MediaType.IMAGE, MediaType.GIF ->
            if (mediaCapabilities.supportsImages) LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES else null
        MediaType.VIDEO ->
            if (mediaCapabilities.supportsVideo) LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO else null
        MediaType.AUDIO ->
            if (mediaCapabilities.supportsAudio) LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO else null
        MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT ->
            if (mediaCapabilities.supportsDocuments) LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS else null
        else -> null
    }
}
