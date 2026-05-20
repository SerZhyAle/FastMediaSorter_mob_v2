package com.sza.fastmediasorter.domain.usecase.scan

import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * Computes the delta between two snapshots of a file list (A5-T2).
 *
 * Given a [current] scan result and a [previous] cached result, calculates
 * which files were [ScanDelta.added], [ScanDelta.modified], [ScanDelta.deleted],
 * and [ScanDelta.unchanged].
 *
 * ### Change detection criteria
 *
 * A file is considered **modified** if its [MediaFile.path] matches a previous
 * entry but either its [MediaFile.size] or [MediaFile.createdDate] differs.
 * This matches the TZ requirement: "mtime + size".
 *
 * A file is considered **added** when its path did not exist in the previous snapshot.
 *
 * A file is considered **deleted** when its path existed in the previous snapshot
 * but is absent from the current scan.
 *
 * ### Usage
 *
 * ```kotlin
 * val cached  = cachedFileListRepository.getCachedFiles(resource.id) ?: emptyList()
 * val current = scanner.scanFolder(...)
 * val delta   = ScanDeltaDetector.compute(current, cached)
 * Timber.d("delta: +${delta.added.size} ~${delta.modified.size} -${delta.deleted.size}")
 * ```
 *
 * @see ScanDelta
 */
object ScanDeltaDetector {

    /**
     * Computes the full delta between [current] and [previous] file lists.
     *
     * @param current  The freshly-scanned list of files.
     * @param previous The previously-cached list of files (may be empty if no cache exists).
     * @return [ScanDelta] partitioned into added / modified / deleted / unchanged.
     */
    fun compute(current: List<MediaFile>, previous: List<MediaFile>): ScanDelta {
        if (previous.isEmpty()) {
            // No previous data - everything is "added", nothing deleted/unchanged.
            return ScanDelta(
                added     = current,
                modified  = emptyList(),
                deleted   = emptyList(),
                unchanged = emptyList()
            )
        }

        val previousByPath = previous.associateBy { it.path }
        val currentByPath  = current.associateBy  { it.path }

        val added     = mutableListOf<MediaFile>()
        val modified  = mutableListOf<MediaFile>()
        val unchanged = mutableListOf<MediaFile>()

        for (file in current) {
            val prev = previousByPath[file.path]
            when {
                prev == null              -> added.add(file)
                isModified(file, prev)    -> modified.add(file)
                else                      -> unchanged.add(file)
            }
        }

        val deleted = previous.filter { prev -> currentByPath[prev.path] == null }

        return ScanDelta(
            added     = added,
            modified  = modified,
            deleted   = deleted,
            unchanged = unchanged
        )
    }

    /**
     * Returns `true` if the file content appears to have changed based on
     * [MediaFile.size] or [MediaFile.createdDate] (proxy for mtime).
     */
    private fun isModified(current: MediaFile, previous: MediaFile): Boolean =
        current.size != previous.size || current.createdDate != previous.createdDate
}

/**
 * The result of a delta comparison between two file-list snapshots.
 *
 * @property added     Files present in current scan but absent in the previous snapshot.
 * @property modified  Files whose path matches but whose size or mtime changed.
 * @property deleted   Files present in the previous snapshot but absent in the current scan.
 * @property unchanged Files identical in both snapshots (path + size + mtime all match).
 */
data class ScanDelta(
    val added: List<MediaFile>,
    val modified: List<MediaFile>,
    val deleted: List<MediaFile>,
    val unchanged: List<MediaFile>
) {
    /** True when no files were added, modified, or deleted. */
    val isEmpty: Boolean
        get() = added.isEmpty() && modified.isEmpty() && deleted.isEmpty()

    /** All files that require metadata re-processing (added + modified). */
    val changedFiles: List<MediaFile>
        get() = added + modified

    /** Total number of files in the current scan (all partitions except deleted). */
    val currentTotal: Int
        get() = added.size + modified.size + unchanged.size

    /** Total number of files that changed (added + modified + deleted). */
    val changedTotal: Int
        get() = added.size + modified.size + deleted.size

    override fun toString(): String =
        "ScanDelta(added=${added.size}, modified=${modified.size}, " +
            "deleted=${deleted.size}, unchanged=${unchanged.size})"
}
