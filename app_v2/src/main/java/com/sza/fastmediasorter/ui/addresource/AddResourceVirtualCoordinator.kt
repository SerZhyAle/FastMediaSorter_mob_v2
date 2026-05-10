package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.ScanLocalFoldersUseCase
import com.sza.fastmediasorter.util.VirtualPathUtils
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Local-media flows: virtual resource construction (Recent / All Music / etc.),
 * local-folder SAF picker handoff, and MediaStore scan.
 */
internal class AddResourceVirtualCoordinator(
    private val context: Context,
    private val scanLocalFoldersUseCase: ScanLocalFoldersUseCase,
    private val addResourceUseCase: AddResourceUseCase,
    private val mediaScannerFactory: MediaScannerFactory,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val bridge: AddResourceBridge
) {

    fun scanLocalFolders() {
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.mutate { it.copy(isScanning = true) }
            bridge.markLoading(true)

            scanLocalFoldersUseCase().onSuccess { resources ->
                bridge.mutate {
                    it.copy(
                        resourcesToAdd = resources,
                        isScanning = false
                    )
                }
                bridge.emit(AddResourceEvent.ShowMessage(context.getString(R.string.addresource_local_folders_found)))
            }.onFailure { e ->
                Timber.e(e, "Error scanning local folders")
                bridge.reportError(e)
                bridge.mutate { it.copy(isScanning = false) }
            }

            bridge.markLoading(false)
        }
    }

    fun addVirtualResource(virtualPath: String) {
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)
            try {
                val existingResources = resourceRepository.getAllResources().first()
                if (existingResources.any { it.path == virtualPath }) {
                    bridge.emit(AddResourceEvent.ShowMessage(
                        context.getString(R.string.virtual_resource_already_added)
                    ))
                    return@launch
                }

                val settings = settingsRepository.getSettings().first()
                val resource = buildVirtualResource(virtualPath, settings)
                if (resource != null) {
                    addResourceUseCase(resource)
                    bridge.emit(AddResourceEvent.ShowMessage(
                        context.getString(R.string.virtual_resource_added, resource.name)
                    ))
                    bridge.emit(AddResourceEvent.ResourcesAdded)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to add virtual resource: $virtualPath")
                bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.friendly_copy_error_generic)))
            } finally {
                bridge.markLoading(false)
            }
        }
    }

    suspend fun getExistingVirtualPaths(): Set<String> {
        return resourceRepository.getAllResources().first()
            .map { it.path }
            .filter(VirtualPathUtils::isVirtualPath)
            .toSet()
    }

    fun addManualFolder(uri: Uri, accessPin: String?) {
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)
            try {
                // Preserve full content:// URIs (SAF tokens) so permission grants survive
                // a process restart. file:// URIs fall back to the raw path for parity with
                // legacy code that predates SAF.
                val path = if (uri.scheme == "content") uri.toString() else uri.path ?: ""
                val name = suggestLocalResourceName(uri)

                val supportedTypes = bridge.supportedMediaTypes()
                val scanner = mediaScannerFactory.getScanner(ResourceType.LOCAL)

                val fileCount = try {
                    scanner.getFileCount(path, supportedTypes, sizeFilter = null, credentialsId = null, scanSubdirectories = true)
                } catch (e: Exception) {
                    Timber.e(e, "Error counting files in $path")
                    0
                }

                val isWritable = try {
                    withTimeout(5000) { scanner.isWritable(path, credentialsId = null) }
                } catch (e: TimeoutCancellationException) {
                    Timber.w("Write permission check timed out for $path")
                    false
                } catch (e: Exception) {
                    Timber.e(e, "Error checking write access for $path")
                    false
                }

                val settings = settingsRepository.getSettings().first()
                val displayMode = if (settings.defaultGridMode) DisplayMode.GRID else DisplayMode.LIST

                // Downloads folder should show all files regardless of global setting (S0059)
                val downloadsPath = Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .absolutePath
                val isDownloadsFolder = path == downloadsPath ||
                    path.endsWith("/$downloadsPath") ||
                    path == "file://$downloadsPath" ||
                    path.removePrefix("file://") == downloadsPath
                val effectiveAllFiles = if (isDownloadsFolder) true else settings.allFiles

                val resource = MediaResource(
                    id = 1,
                    name = name,
                    path = path,
                    type = ResourceType.LOCAL,
                    supportedMediaTypes = supportedTypes,
                    createdDate = System.currentTimeMillis(),
                    fileCount = fileCount,
                    isDestination = false,
                    destinationOrder = null,
                    isWritable = isWritable,
                    slideshowInterval = settings.slideshowInterval,
                    displayMode = displayMode,
                    sortMode = settings.defaultSortMode,
                    // manually added local folders default to recursive scanning
                    scanSubdirectories = true,
                    isReadOnly = false,
                    // Downloads always shows all files; others inherit global setting (S0059)
                    allFiles = effectiveAllFiles,
                    accessPin = accessPin?.ifBlank { null }
                )

                bridge.mutate { state ->
                    state.copy(
                        resourcesToAdd = state.resourcesToAdd + resource,
                        // auto-select so the user doesn't have to tap twice
                        selectedPaths = state.selectedPaths + resource.path
                    )
                }

                bridge.emit(AddResourceEvent.ShowMessage(context.getString(R.string.addresource_folder_added_to_list)))
            } catch (e: Exception) {
                Timber.e(e, "Error adding manual folder")
                bridge.reportError(e)
            }

            bridge.markLoading(false)
        }
    }

    private fun buildVirtualResource(
        virtualPath: String,
        settings: AppSettings
    ): MediaResource? {
        Timber.d("S0130: building user-added virtual resource path=$virtualPath isWritable=${VirtualPathUtils.isAggregateVirtualPath(virtualPath)}")
        val (name, types, profile) = when (virtualPath) {
            LocalMediaScanner.VIRTUAL_PATH_RECENT -> Triple(
                context.getString(R.string.recent_media),
                settings.getGloballyEnabledMediaTypes(),
                ResourceProfile.NONE
            )
            LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO -> Triple(
                context.getString(R.string.virtual_all_music),
                setOf(MediaType.AUDIO),
                ResourceProfile.AUDIO_LIBRARY
            )
            LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO -> Triple(
                context.getString(R.string.virtual_all_video),
                setOf(MediaType.VIDEO),
                ResourceProfile.VIDEO_LIBRARY
            )
            LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS -> {
                val cameraTypes = buildSet {
                    if (settings.supportImages) add(MediaType.IMAGE)
                    if (settings.supportGifs) add(MediaType.GIF)
                    if (settings.supportVideos) add(MediaType.VIDEO)
                }
                if (cameraTypes.isEmpty()) return null
                Triple(
                    context.getString(R.string.virtual_camera_photos),
                    cameraTypes,
                    ResourceProfile.PHOTO_STORAGE
                )
            }
            LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES -> {
                val imageTypes = buildSet {
                    if (settings.supportImages) add(MediaType.IMAGE)
                    if (settings.supportGifs) add(MediaType.GIF)
                }
                if (imageTypes.isEmpty()) return null
                Triple(
                    context.getString(R.string.virtual_all_images),
                    imageTypes,
                    ResourceProfile.PHOTO_STORAGE
                )
            }
            LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS -> {
                val docTypes = buildSet {
                    if (settings.supportText) add(MediaType.TEXT)
                    if (settings.supportPdf) add(MediaType.PDF)
                    if (settings.supportEpub) add(MediaType.EPUB)
                }
                if (docTypes.isEmpty()) return null
                Triple(
                    context.getString(R.string.virtual_all_docs),
                    docTypes,
                    ResourceProfile.DOCUMENTS
                )
            }
            else -> return null
        }

        val comment = when (virtualPath) {
            LocalMediaScanner.VIRTUAL_PATH_RECENT -> context.getString(R.string.virtual_comment_recent)
            LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO -> context.getString(R.string.virtual_comment_all_music)
            LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO -> context.getString(R.string.virtual_comment_all_video)
            LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS -> context.getString(R.string.virtual_comment_camera_photos)
            LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES -> context.getString(R.string.virtual_comment_all_images)
            LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS -> context.getString(R.string.virtual_comment_all_docs)
            else -> null
        }

        val defaultSortMode = if (virtualPath == LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS) {
            SortMode.DATE_DESC
        } else {
            SortMode.NAME_ASC
        }

        return MediaResource(
            id = 0,
            name = name,
            comment = comment,
            path = virtualPath,
            type = ResourceType.LOCAL,
            createdDate = System.currentTimeMillis(),
            fileCount = 0,
            isDestination = false,
            destinationOrder = null,
            isWritable = VirtualPathUtils.isAggregateVirtualPath(virtualPath),
            scanSubdirectories = false,
            supportedMediaTypes = types,
            sortMode = defaultSortMode,
            profile = profile,
            // Recent shows all files by default so nothing is hidden from history (S0059)
            allFiles = virtualPath == LocalMediaScanner.VIRTUAL_PATH_RECENT
        )
    }

    /**
     * Derives a display name for a SAF folder URI.
     *
     * Uses the last two path segments when the parent segment is short (< 15 chars) —
     * prevents collisions like "Pictures/Telegram" vs "Movies/Telegram" without
     * producing unwieldy names for deeply nested paths.
     *
     * SAF content URIs encode the path as "primary:Pictures/Telegram" — strip the
     * volume prefix before splitting.
     */
    private fun suggestLocalResourceName(uri: Uri): String {
        val rawSegment = uri.lastPathSegment ?: return "Folder"
        val pathPart = if (rawSegment.contains(':')) rawSegment.substringAfter(':') else rawSegment
        val segments = pathPart.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return rawSegment

        val folderName = segments.last()
        if (segments.size < 2) return folderName

        val parentName = segments[segments.size - 2]
        return if (parentName.length < 15) "$parentName/$folderName" else folderName
    }
}
