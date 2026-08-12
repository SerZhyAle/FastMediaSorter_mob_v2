package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * UseCase for cleaning up trash folders created during soft-delete operations.
 *
 * Recognises both the canonical layout owned by [TrashFolderContract]
 * (`<parent>/.trash/<timestamp>/`) and the legacy single-level layout
 * (`<parent>/.trash_<timestamp>/`) for one-time best-effort migration of
 * directories left over by earlier versions of the app.
 *
 * Default behaviour deletes snapshots older than [DEFAULT_AGE_MS] (5 minutes).
 * Pass `maxAgeMs = 0L` to delete every recognised snapshot regardless of age.
 */
class CleanupTrashFoldersUseCase @Inject constructor() {

    companion object {
        const val DEFAULT_AGE_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Clean up trash folders under [rootDirectory] (recursive).
     *
     * @param rootDirectory directory to search.
     * @param maxAgeMs maximum age (ms) of snapshots to keep; `0` = delete all.
     * @return total number of snapshot directories deleted (canonical + legacy).
     */
    suspend fun cleanup(
        rootDirectory: File,
        maxAgeMs: Long = DEFAULT_AGE_MS
    ): Int = withContext(Dispatchers.IO) {
        if (!rootDirectory.exists() || !rootDirectory.isDirectory) {
            Timber.w("CleanupTrashFoldersUseCase: root does not exist or is not a directory: ${rootDirectory.absolutePath}")
            return@withContext 0
        }

        val currentTime = System.currentTimeMillis()
        val counters = Counters()

        try {
            cleanupRecursive(rootDirectory, currentTime, maxAgeMs, counters)
            if (maxAgeMs == 0L) {
                Timber.i(
                    "CleanupTrashFoldersUseCase: deleted ALL %d snapshots (canonical=%d, legacy=%d)",
                    counters.total,
                    counters.canonical,
                    counters.legacy,
                )
            } else {
                Timber.i(
                    "CleanupTrashFoldersUseCase: deleted %d snapshots older than %dms (canonical=%d, legacy=%d)",
                    counters.total,
                    maxAgeMs,
                    counters.canonical,
                    counters.legacy,
                )
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "CleanupTrashFoldersUseCase: error during cleanup")
        }

        counters.total
    }

    /**
     * Walk the directory tree:
     * - If a directory is the canonical container ([TrashFolderContract.isContainerDir]),
     *   process its snapshot children and, when the container becomes empty, remove it.
     * - If a directory matches the legacy layout ([TrashFolderContract.isLegacyContainerDir]),
     *   process it as a single legacy snapshot.
     * - Otherwise recurse into it.
     */
    private fun cleanupRecursive(
        directory: File,
        currentTime: Long,
        maxAgeMs: Long,
        counters: Counters,
    ) {
        try {
            val children = directory.listFiles() ?: return

            for (child in children) {
                if (!child.isDirectory) continue
                try {
                    when {
                        TrashFolderContract.isContainerDir(child.name) -> {
                            processCanonicalContainer(child, currentTime, maxAgeMs, counters)
                        }

                        TrashFolderContract.isLegacyContainerDir(child.name) -> {
                            processLegacyContainer(child, currentTime, maxAgeMs, counters)
                        }

                        else -> {
                            cleanupRecursive(child, currentTime, maxAgeMs, counters)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "CleanupTrashFoldersUseCase: error processing ${child.name}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "CleanupTrashFoldersUseCase: error listing ${directory.absolutePath}")
        }
    }

    /**
     * Iterate snapshot subdirectories inside a canonical `.trash/` container.
     * Each subdirectory name is a millisecond timestamp; apply [maxAgeMs] and delete.
     * After purging eligible snapshots, remove the container itself when empty.
     */
    private fun processCanonicalContainer(
        container: File,
        currentTime: Long,
        maxAgeMs: Long,
        counters: Counters,
    ) {
        val snapshots = container.listFiles() ?: emptyArray()
        for (snapshot in snapshots) {
            if (!snapshot.isDirectory) continue
            val timestamp = TrashFolderContract.parseSnapshotTimestamp(snapshot.name)
            if (timestamp == null) {
                if (maxAgeMs == 0L) {
                    Timber.w("CleanupTrashFoldersUseCase: unparseable snapshot name, deleting (maxAge=0): ${snapshot.absolutePath}")
                    if (deleteRecursively(snapshot)) counters.canonical++
                }
                continue
            }
            val age = currentTime - timestamp
            if (maxAgeMs == 0L || age > maxAgeMs) {
                if (deleteRecursively(snapshot)) {
                    counters.canonical++
                    Timber.d(
                        "CleanupTrashFoldersUseCase: deleted canonical snapshot ${snapshot.absolutePath} (age=%dms, maxAge=%dms)",
                        age,
                        maxAgeMs,
                    )
                } else {
                    Timber.w("CleanupTrashFoldersUseCase: failed to delete canonical snapshot ${snapshot.absolutePath}")
                }
            }
        }

        // Remove the container itself once it is empty - keeps the parent tidy.
        val remaining = container.listFiles()
        if (remaining != null && remaining.isEmpty()) {
            if (container.delete()) {
                Timber.d("CleanupTrashFoldersUseCase: removed empty canonical container ${container.absolutePath}")
            }
        }
    }

    /**
     * Handle a legacy `.trash_<timestamp>` directory as a single snapshot.
     * Parses the suffix; applies the same age policy as the canonical layout.
     */
    private fun processLegacyContainer(
        legacyDir: File,
        currentTime: Long,
        maxAgeMs: Long,
        counters: Counters,
    ) {
        val timestamp = TrashFolderContract.parseLegacyTimestamp(legacyDir.name)
        if (timestamp == null) {
            if (maxAgeMs == 0L) {
                Timber.w("CleanupTrashFoldersUseCase: unparseable legacy name, deleting (maxAge=0): ${legacyDir.absolutePath}")
                if (deleteRecursively(legacyDir)) counters.legacy++
            }
            return
        }
        val age = currentTime - timestamp
        if (maxAgeMs == 0L || age > maxAgeMs) {
            if (deleteRecursively(legacyDir)) {
                counters.legacy++
                Timber.d(
                    "CleanupTrashFoldersUseCase: deleted legacy snapshot ${legacyDir.absolutePath} (age=%dms, maxAge=%dms)",
                    age,
                    maxAgeMs,
                )
            } else {
                Timber.w("CleanupTrashFoldersUseCase: failed to delete legacy snapshot ${legacyDir.absolutePath}")
            }
        }
    }

    /** Delete a directory and its contents bottom-up. Returns true on success. */
    private fun deleteRecursively(directory: File): Boolean {
        return try {
            if (!directory.exists()) return true
            if (directory.isDirectory) {
                directory.listFiles()?.forEach { child -> deleteRecursively(child) }
            }
            directory.delete()
        } catch (e: Exception) {
            Timber.e(e, "CleanupTrashFoldersUseCase: error deleting ${directory.absolutePath}")
            false
        }
    }

    private class Counters(
        var canonical: Int = 0,
        var legacy: Int = 0,
    ) {
        val total: Int get() = canonical + legacy
    }
}
