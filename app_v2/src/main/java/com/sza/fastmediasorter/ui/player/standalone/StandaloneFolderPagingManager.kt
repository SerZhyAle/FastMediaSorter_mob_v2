package com.sza.fastmediasorter.ui.player.standalone

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * S0389 Phase 03 (pillar B): neighbour-file paging for a single externally-opened file.
 *
 * Why: the in-app player always has a resource-backed playlist, but a standalone host receives one
 * external URI with no resource context. When that URI resolves to a local folder (Phase 02), this
 * manager enumerates the folder's media of the host-supported types so next/previous/random/slideshow
 * can work without inventing a persistent resource. It is the single owner of the active list for the
 * standalone surface - the activities and the view-model never re-scan or hold their own copy.
 *
 * Enumeration reuses [MediaScanner.scanFolder] (the same scanner the in-app playlist builder uses),
 * so SAF/MediaStore/legacy folder handling is not duplicated here. [initialize] runs the scan on
 * [Dispatchers.IO]; the rest of the API is cheap in-memory list navigation safe to call on the main
 * thread.
 *
 * Paging is reported unavailable ([isPagingAvailable] == false) when the folder cannot be enumerated
 * or holds a single supported file - the host then keeps its existing single-file semantics.
 */
class StandaloneFolderPagingManager(
    private val mediaScanner: MediaScanner,
    private val supportedTypes: Set<MediaType>,
) {

    private var files: List<MediaFile> = emptyList()
    private var currentIndex: Int = -1

    /** True only after a successful scan that yielded at least two host-supported files. */
    val isPagingAvailable: Boolean
        get() = files.size > 1 && currentIndex in files.indices

    val total: Int
        get() = files.size

    val index: Int
        get() = currentIndex

    val currentFile: MediaFile?
        get() = files.getOrNull(currentIndex)

    /**
     * Enumerate [parentFolderPath] and position the cursor on the opened file ([currentAbsolutePath]).
     * Returns the resolved current file when paging is available, or null when the folder is not
     * enumerable / holds a single file. Runs the scan off the main thread; never blocks the caller's
     * thread on large folders.
     */
    suspend fun initialize(
        parentFolderPath: String,
        currentAbsolutePath: String,
    ): MediaFile? = withContext(Dispatchers.IO) {
        val scanned = runCatching {
            mediaScanner.scanFolder(
                path = parentFolderPath,
                supportedTypes = supportedTypes,
                sizeFilter = null,
                credentialsId = null,
                scanSubdirectories = false,
                showHiddenFiles = false,
                onProgress = null,
            )
        }.getOrElse { error ->
            // runCatching catches Throwable, so cancellation lands here too. Since S1890 made the
            // scanner propagate it, swallowing it here would just move the same defect up one floor.
            error.rethrowIfCancellation()
            // A failed enumeration is an expected degradation (permissions, removed folder): fall back
            // to single-file mode rather than surfacing an error to the user.
            Timber.i(error, "StandaloneFolderPaging: scan failed for %s, paging disabled", parentFolderPath)
            emptyList()
        }

        val ordered = scanned
            .filter { !it.isDirectory && it.type in supportedTypes }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        files = ordered
        currentIndex = ordered.indexOfFirst { samePath(it, currentAbsolutePath) }

        if (currentIndex < 0 && ordered.isNotEmpty()) {
            // The opened file was filtered out (unsupported here) or path-normalised differently; keep
            // the enumerated list but stay single-file so navigation does not jump to an unrelated file.
            Timber.i("StandaloneFolderPaging: opened file not found among %d neighbours", ordered.size)
            files = emptyList()
            currentIndex = -1
        }

        currentFile
    }

    fun next(): MediaFile? = move { (currentIndex + 1) % files.size }

    fun previous(): MediaFile? = move { (currentIndex - 1 + files.size) % files.size }

    fun random(): MediaFile? = move {
        if (files.size <= 1) currentIndex else generateDifferentIndex()
    }

    private inline fun move(nextIndex: () -> Int): MediaFile? {
        if (!isPagingAvailable) return null
        currentIndex = nextIndex()
        return currentFile
    }

    private fun generateDifferentIndex(): Int {
        var candidate = currentIndex
        while (candidate == currentIndex) {
            candidate = files.indices.random()
        }
        return candidate
    }

    private fun samePath(file: MediaFile, absolutePath: String): Boolean {
        if (file.path == absolutePath) return true
        // MediaStore and legacy scanners can report differently-cased / non-canonical paths for the
        // same file; compare canonical forms before giving up.
        return runCatching {
            java.io.File(file.path).canonicalPath == java.io.File(absolutePath).canonicalPath
        }.getOrDefault(false)
    }
}
