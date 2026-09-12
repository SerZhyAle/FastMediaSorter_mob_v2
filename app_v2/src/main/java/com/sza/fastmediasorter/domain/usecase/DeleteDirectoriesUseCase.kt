package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.model.MediaFile
import timber.log.Timber
import javax.inject.Inject

/**
 * Domain-layer façade for recursively deleting directory [MediaFile] items.
 *
 * Iterates directories sequentially, delegates each to
 * [UnifiedFileOperationHandler.executeDeleteDirectory], and aggregates totals.
 * On partial failure, returns [Result.failure] with an aggregated error message.
 */
class DeleteDirectoriesUseCase @Inject constructor(
    private val fileOperationHandler: UnifiedFileOperationHandler
) {

    /**
     * Delete each directory in [directories] recursively.
     *
     * @param directories List of [MediaFile] items with [MediaFile.isDirectory] == true.
     * @param progressCallback Optional callback (deletedCount, total, currentName).
     * @return Result<Int> - total number of entries deleted across all directories.
     */
    suspend operator fun invoke(
        directories: List<MediaFile>,
        progressCallback: ((Int, Int, String) -> Unit)? = null
    ): Result<Int> = deletePaths(directories.map { it.path }, progressCallback)

    /**
     * S1326: the path-only entry point. An undo record holds paths, and the copied trees it names exist
     * in no [MediaFile] list, so they cannot reach the overload above. Declared as a named method rather
     * than a second `invoke` overload - `List<MediaFile>` and `List<String>` erase to the same JVM
     * signature and the compiler rejects the clash.
     *
     * @return Result<Int> - total number of entries deleted across all directories.
     */
    suspend fun deletePaths(
        paths: List<String>,
        progressCallback: ((Int, Int, String) -> Unit)? = null
    ): Result<Int> {
        if (paths.isEmpty()) return Result.success(0)

        var totalDeleted = 0
        val errors = mutableListOf<String>()

        for (path in paths) {
            Timber.d("DeleteDirectoriesUseCase: deleting directory $path")
            val result = fileOperationHandler.executeDeleteDirectory(path, progressCallback)
            result
                .onSuccess { count ->
                    totalDeleted += count
                    Timber.d("DeleteDirectoriesUseCase: deleted $count entries from $path")
                }
                .onFailure { e ->
                    val msg = "Failed to delete '$path': ${e.message}"
                    Timber.e(e, "DeleteDirectoriesUseCase: $msg")
                    errors.add(msg)
                }
        }

        return if (errors.isEmpty()) {
            Result.success(totalDeleted)
        } else {
            Result.failure(Exception(errors.joinToString("\n")))
        }
    }
}
