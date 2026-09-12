package com.sza.fastmediasorter.data.local

import android.content.Context
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.MediaStoreRepository
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.util.VirtualPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over the three local access mechanisms: the MediaStore queries it owns, plus the SAF and
 * legacy-File walks it delegates to. The two collaborators are built here rather than injected so
 * the constructor signature (and `LocalMediaScannerTest`, which calls it with two arguments) is
 * unchanged; neither needs anything beyond the context this class already holds.
 */
@Singleton
class LocalMediaScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaStoreRepository: MediaStoreRepository
) : MediaScanner {

    private val safScanner = SafMediaScanner(context)
    private val legacyScanner = LegacyFileMediaScanner()

    companion object {
        const val VIRTUAL_PATH_RECENT = "virtual://recent"
        const val VIRTUAL_PATH_ALL_AUDIO = "virtual://all_audio"
        const val VIRTUAL_PATH_ALL_VIDEO = "virtual://all_video"
        const val VIRTUAL_PATH_ALL_IMAGES = "virtual://all_images"
        const val VIRTUAL_PATH_ALL_DOCS = "virtual://all_docs"
        const val VIRTUAL_PATH_CAMERA_PHOTOS = "virtual://camera_photos"
        private const val RECENT_FILES_LIMIT = 1000
        const val VIRTUAL_ALL_FILES_LIMIT = 10_000

        private const val SAF_URI_PREFIX = "content://"

        private val DIRECTORY_FIRST: Comparator<MediaFile> =
            compareBy<MediaFile> { !it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }

    override suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        Timber.d("LocalMediaScanner.scanFolder: START - path='$path'")
        Timber.d("S2401: scanFolder dispatch entered for '$path'")
        scanVirtualPath(path, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles, onProgress)
            ?: if (path.startsWith(SAF_URI_PREFIX)) {
                safScanner.scanFolderFast(
                    path, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles, onProgress
                )
            } else {
                scanPhysicalFolder(
                    path, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles, onProgress
                )
            }
    }

    override suspend fun scanFolderPaged(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        offset: Int,
        limit: Int,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): MediaFilePage = withContext(Dispatchers.IO) {
        // Reuse scanFolder logic (handles virtual paths, SAF, MediaStore, and legacy)
        val allFiles = scanFolder(
            path = path,
            supportedTypes = supportedTypes,
            sizeFilter = sizeFilter,
            credentialsId = credentialsId,
            scanSubdirectories = scanSubdirectories,
            showHiddenFiles = showHiddenFiles,
            onProgress = null
        )
        val page = allFiles.drop(offset).take(limit)
        MediaFilePage(page, offset + limit < allFiles.size)
    }

    override suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): Int = withContext(Dispatchers.IO) {
        Timber.d("S2401: getFileCount dispatch entered for '$path'")
        countVirtualPath(path, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles)
            ?: if (path.startsWith(SAF_URI_PREFIX)) {
                safScanner.getFileCount(path, supportedTypes, sizeFilter, scanSubdirectories)
            } else {
                countPhysicalFolder(path, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles)
            }
    }

    override suspend fun isWritable(path: String, credentialsId: String?): Boolean =
        withContext(Dispatchers.IO) {
            when {
                VirtualPathUtils.isVirtualPath(path) -> false
                path.startsWith(SAF_URI_PREFIX) -> safScanner.isWritable(path)
                else -> File(path).let { it.exists() && it.canWrite() }
            }
        }

    /** List directory contents; returns files and folders (isDirectory=true for folders). */
    override suspend fun listDirectoryContents(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        showHiddenFiles: Boolean
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        Timber.d("LocalMediaScanner.listDirectoryContents: path='$path'")
        when {
            // Virtual resources delegate to scanFolder to produce their file list
            VirtualPathUtils.isVirtualPath(path) -> scanFolder(
                path = path,
                supportedTypes = supportedTypes,
                sizeFilter = sizeFilter,
                credentialsId = credentialsId,
                scanSubdirectories = false,
                showHiddenFiles = showHiddenFiles,
                onProgress = null
            )
            path.startsWith(SAF_URI_PREFIX) ->
                safScanner.listDirectoryContents(path, supportedTypes, sizeFilter, showHiddenFiles)
            else -> listLocalDirectory(path, supportedTypes, sizeFilter, showHiddenFiles)
        }
    }

    /** Returns null when [path] is not one of the virtual resources, so the caller can dispatch on. */
    private suspend fun scanVirtualPath(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile>? = when (path) {
        VIRTUAL_PATH_RECENT ->
            scanRecentFiles(supportedTypes, sizeFilter, showHiddenFiles, onProgress)
        VIRTUAL_PATH_ALL_AUDIO ->
            scanAllByTypes(setOf(MediaType.AUDIO), sizeFilter, showHiddenFiles, onProgress)
        VIRTUAL_PATH_ALL_VIDEO ->
            scanAllByTypes(setOf(MediaType.VIDEO), sizeFilter, showHiddenFiles, onProgress)
        VIRTUAL_PATH_ALL_IMAGES ->
            scanTypeSubset(imageTypesFromSettings(supportedTypes), sizeFilter, showHiddenFiles, onProgress)
        VIRTUAL_PATH_ALL_DOCS ->
            scanTypeSubset(docTypesFromSettings(supportedTypes), sizeFilter, showHiddenFiles, onProgress)
        VIRTUAL_PATH_CAMERA_PHOTOS ->
            scanCameraPhotos(supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles, onProgress)
        else -> null
    }

    private suspend fun countVirtualPath(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): Int? = when (path) {
        VIRTUAL_PATH_RECENT ->
            scanRecentFiles(supportedTypes, sizeFilter, showHiddenFiles, null).size
        VIRTUAL_PATH_ALL_AUDIO ->
            countAllByTypes(setOf(MediaType.AUDIO), sizeFilter, showHiddenFiles)
        VIRTUAL_PATH_ALL_VIDEO ->
            countAllByTypes(setOf(MediaType.VIDEO), sizeFilter, showHiddenFiles)
        VIRTUAL_PATH_ALL_IMAGES ->
            countTypeSubset(imageTypesFromSettings(supportedTypes), sizeFilter, showHiddenFiles)
        VIRTUAL_PATH_ALL_DOCS ->
            countTypeSubset(docTypesFromSettings(supportedTypes), sizeFilter, showHiddenFiles)
        VIRTUAL_PATH_CAMERA_PHOTOS ->
            countCameraPhotos(supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles)
        else -> null
    }

    private suspend fun scanTypeSubset(
        types: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> = if (types.isEmpty()) {
        onProgress?.onComplete(0, 0)
        emptyList()
    } else {
        scanAllByTypes(types, sizeFilter, showHiddenFiles, onProgress)
    }

    private suspend fun countTypeSubset(
        types: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean
    ): Int = if (types.isEmpty()) 0 else countAllByTypes(types, sizeFilter, showHiddenFiles)

    private suspend fun scanCameraPhotos(
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> {
        val cameraPath = mediaStoreRepository.findCameraFolderPath()
        if (cameraPath == null) {
            Timber.w("LocalMediaScanner: Camera folder not found on this device")
            return emptyList()
        }
        Timber.d("LocalMediaScanner: Scanning camera path='$cameraPath'")
        return scanPhysicalFolder(
            path = cameraPath,
            supportedTypes = supportedTypes,
            sizeFilter = sizeFilter,
            scanSubdirectories = scanSubdirectories,
            showHiddenFiles = showHiddenFiles,
            onProgress = onProgress,
            requireExisting = false
        )
    }

    private suspend fun countCameraPhotos(
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): Int {
        val cameraPath = mediaStoreRepository.findCameraFolderPath() ?: return 0
        return countPhysicalFolder(cameraPath, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles)
    }

    /**
     * MediaStore first (Android 10+ compliant), legacy File walk when it returns nothing usable.
     * [requireExisting] drops MediaStore phantom rows whose file is gone; the camera branch keeps
     * them, because its own fallback re-walks the same folder anyway.
     */
    private suspend fun scanPhysicalFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?,
        requireExisting: Boolean = true
    ): List<MediaFile> {
        val fromStore = mediaStoreFiles(
            path = path,
            supportedTypes = supportedTypes,
            sizeFilter = sizeFilter,
            scanSubdirectories = scanSubdirectories,
            showHiddenFiles = showHiddenFiles,
            requireExisting = requireExisting
        )?.also {
            onProgress?.onComplete(it.size, 0)
            Timber.d("LocalMediaScanner: MediaStore returned ${it.size} files for '$path'")
        }
        return fromStore ?: legacyScanner.scanFolder(
            path, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles, onProgress
        )
    }

    private suspend fun countPhysicalFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): Int {
        val fromStore = queryMediaStore(path, supportedTypes, scanSubdirectories, showHiddenFiles)
        return if (fromStore.isEmpty()) {
            legacyScanner.countFiles(path, supportedTypes, sizeFilter, scanSubdirectories, showHiddenFiles)
        } else {
            // The count path deliberately omits scanFolder's `size <= 0L` escape: a row with an
            // unknown size is counted there but not here, and changing either shifts a user-visible
            // total against the list it labels.
            fromStore.count { sizeFilter == null || inSizeRange(it, sizeFilter) }
        }
    }

    /** Null when MediaStore has nothing usable for [path] and the legacy walk should take over. */
    private suspend fun mediaStoreFiles(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        requireExisting: Boolean
    ): List<MediaFile>? {
        val files = queryMediaStore(path, supportedTypes, scanSubdirectories, showHiddenFiles)
        val filtered = files.filter { file ->
            (!requireExisting || existsOnDisk(file)) &&
                (sizeFilter == null || file.size <= 0L || inSizeRange(file, sizeFilter))
        }
        return filtered.takeIf { files.isNotEmpty() && (it.isNotEmpty() || sizeFilter == null) }
    }

    private suspend fun queryMediaStore(
        path: String,
        supportedTypes: Set<MediaType>,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): List<MediaFile> = try {
        mediaStoreRepository.getFilesInFolder(path, supportedTypes, scanSubdirectories, showHiddenFiles)
    } catch (e: Exception) {
        // S1890: a cancelled query is not "MediaStore unavailable" - without the rethrow inside
        // warnUnlessCancellation the legacy walk below starts for a result already cancelled.
        e.warnUnlessCancellation("MediaStore scan failed for '$path', falling back to legacy File API")
        emptyList()
    }

    private fun existsOnDisk(file: MediaFile): Boolean {
        val exists = !file.path.startsWith("/") || File(file.path).exists()
        if (!exists) {
            Timber.w("LocalMediaScanner: Filtered out non-existent MediaStore phantom file: ${file.path}")
        }
        return exists
    }

    private fun inSizeRange(file: MediaFile, sizeFilter: SizeFilter): Boolean =
        MediaTypeUtils.isFileSizeInRange(file.size, file.type, sizeFilter)

    private suspend fun scanRecentFiles(
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> {
        return try {
            val files = mediaStoreRepository.getRecentFiles(RECENT_FILES_LIMIT, supportedTypes)
            val visibleFiles = if (showHiddenFiles) {
                files
            } else {
                files.filter { !File(it.path).name.startsWith(".") }
            }

            val filteredFiles = visibleFiles.filter { file ->
                sizeFilter == null || file.size <= 0L || inSizeRange(file, sizeFilter)
            }

            onProgress?.onComplete(filteredFiles.size, 0)
            filteredFiles
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Coroutine cancellation is not a scan failure - it must propagate, never be logged as error.
            throw e
        } catch (e: Exception) {
            Timber.e(e, "LocalMediaScanner: failed to scan recent files")
            onProgress?.onComplete(0, 1)
            emptyList()
        }
    }

    private suspend fun scanAllByTypes(
        allowedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> {
        return try {
            val files = mediaStoreRepository.getAllFilesByTypes(
                allowedTypes = allowedTypes,
                limit = VIRTUAL_ALL_FILES_LIMIT,
                showHiddenFiles = showHiddenFiles
            )

            val filteredFiles = files.filter { file ->
                sizeFilter == null || file.size <= 0L || inSizeRange(file, sizeFilter)
            }

            onProgress?.onComplete(filteredFiles.size, 0)
            filteredFiles
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Coroutine cancellation is not a scan failure - it must propagate, never be logged as error.
            throw e
        } catch (e: Exception) {
            Timber.e(e, "LocalMediaScanner: failed to scan all files by types: $allowedTypes")
            onProgress?.onComplete(0, 1)
            emptyList()
        }
    }

    private suspend fun countAllByTypes(
        allowedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean
    ): Int {
        return try {
            mediaStoreRepository.countAllFilesByTypes(
                allowedTypes = allowedTypes,
                limit = VIRTUAL_ALL_FILES_LIMIT,
                showHiddenFiles = showHiddenFiles,
                sizeFilter = sizeFilter
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Coroutine cancellation is not a scan failure - it must propagate, never be logged as error.
            throw e
        } catch (e: Exception) {
            Timber.e(e, "LocalMediaScanner: failed to count all files by types: $allowedTypes")
            0
        }
    }

    private fun imageTypesFromSettings(supportedTypes: Set<MediaType>): Set<MediaType> {
        return supportedTypes.filter { it in setOf(MediaType.IMAGE, MediaType.GIF) }.toSet()
    }

    private fun docTypesFromSettings(supportedTypes: Set<MediaType>): Set<MediaType> {
        return supportedTypes.filter {
            it in setOf(MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT)
        }.toSet()
    }

    private fun listLocalDirectory(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean
    ): List<MediaFile> {
        val children = File(path).takeIf { it.isDirectory }?.listFiles()
        if (children == null) {
            Timber.w("LocalMediaScanner.listDirectoryContents: Invalid path '$path'")
            return emptyList()
        }
        val visible = if (showHiddenFiles) children.toList() else children.filter { !it.isHidden }
        return visible
            .mapNotNull { child -> localEntry(child, supportedTypes, sizeFilter) }
            .sortedWith(DIRECTORY_FIRST)
    }

    private fun localEntry(
        file: File,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?
    ): MediaFile? = if (file.isDirectory) {
        localFolderEntry(file)
    } else {
        localFileEntry(file, supportedTypes, sizeFilter)
    }

    private fun localFolderEntry(file: File): MediaFile? =
        // Keep recognised trash containers out of normal folder listings.
        if (TrashFolderContract.matchesTrashSegment(file.name)) {
            null
        } else {
            MediaFile(
                name = file.name,
                path = file.absolutePath,
                size = 0L,
                createdDate = file.lastModified(),
                type = MediaType.IMAGE, // Placeholder type for folders
                isDirectory = true,
                childCount = file.listFiles()?.size ?: 0
            )
        }

    private fun localFileEntry(
        file: File,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?
    ): MediaFile? = MediaTypeUtils.getMediaType(file.name)
        ?.takeIf { it in supportedTypes }
        ?.takeIf { sizeFilter == null || MediaTypeUtils.isFileSizeInRange(file.length(), it, sizeFilter) }
        ?.let { type ->
            MediaFile(
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                createdDate = file.lastModified(),
                type = type,
                isDirectory = false
            )
        }
}
