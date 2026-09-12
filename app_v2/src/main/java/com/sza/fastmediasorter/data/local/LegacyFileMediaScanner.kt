package com.sza.fastmediasorter.data.local

import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Direct `java.io.File` walk - the fallback [LocalMediaScanner] takes when MediaStore returns
 * nothing usable for a physical path. It reads only the file system, so it has no dependencies.
 */
internal class LegacyFileMediaScanner {

    suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        Timber.d("S2401: LegacyFileMediaScanner.scanFolder entered for '$path'")
        val folder = File(path)
        if (!folder.isDirectory) {
            Timber.w("LegacyFileMediaScanner: '$path' does not exist or is not a directory")
            onProgress?.onComplete(0, 0)
            return@withContext emptyList()
        }
        collect(visibleFiles(folder, scanSubdirectories, showHiddenFiles), supportedTypes, sizeFilter, onProgress)
    }

    suspend fun countFiles(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): Int = withContext(Dispatchers.IO) {
        val folder = File(path)
        if (folder.isDirectory) {
            visibleFiles(folder, scanSubdirectories, showHiddenFiles)
                .count { mediaTypeOf(it, supportedTypes, sizeFilter) != null }
        } else {
            0
        }
    }

    private fun visibleFiles(
        folder: File,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): List<File> {
        val files = if (scanSubdirectories) {
            collectFilesRecursively(folder)
        } else {
            folder.listFiles()?.filter { it.isFile }.orEmpty()
        }
        return if (showHiddenFiles) files else files.filter { !it.isHidden }
    }

    private suspend fun collect(
        files: List<File>,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> {
        val result = mutableListOf<MediaFile>()
        var processed = 0
        for (file in files) {
            processed++
            if (processed % PROGRESS_STEP == 0) {
                onProgress?.onProgress(processed, file.name)
                // The caller drives incremental loading and may stop the walk mid-folder.
                if (onProgress?.shouldStop() == true) {
                    Timber.d("LegacyFileMediaScanner: early stop at $processed of ${files.size}")
                    break
                }
            }
            mediaTypeOf(file, supportedTypes, sizeFilter)?.let { type ->
                result.add(
                    MediaFile(
                        name = file.name,
                        path = file.absolutePath,
                        size = file.length(),
                        createdDate = file.lastModified(),
                        type = type
                    )
                )
            }
        }
        onProgress?.onComplete(result.size, 0)
        Timber.d("LegacyFileMediaScanner: matched ${result.size} of $processed walked files")
        return result
    }

    private fun mediaTypeOf(
        file: File,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?
    ): MediaType? = MediaTypeUtils.getMediaType(file.name)
        ?.takeIf { it in supportedTypes }
        ?.takeIf { sizeFilter == null || MediaTypeUtils.isFileSizeInRange(file.length(), it, sizeFilter) }

    private fun collectFilesRecursively(folder: File): List<File> {
        val result = mutableListOf<File>()
        val queue = ArrayDeque<File>()
        queue.add(folder)
        while (queue.isNotEmpty()) {
            queue.removeFirst().listFiles()?.forEach { child ->
                if (child.isDirectory && !TrashFolderContract.matchesTrashSegment(child.name)) {
                    queue.add(child)
                } else if (child.isFile) {
                    result.add(child)
                }
            }
        }
        return result
    }

    private companion object {
        const val PROGRESS_STEP = 50
    }
}
