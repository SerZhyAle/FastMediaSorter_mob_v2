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
    ): Result<Int> {
        if (directories.isEmpty()) return Result.success(0)

        var totalDeleted = 0
        val errors = mutableListOf<String>()

        for (dir in directories) {
            Timber.d("DeleteDirectoriesUseCase: deleting directory ${dir.path}")
            val result = fileOperationHandler.executeDeleteDirectory(dir.path, progressCallback)
            result
                .onSuccess { count ->
                    totalDeleted += count
                    Timber.d("DeleteDirectoriesUseCase: deleted $count entries from ${dir.name}")
                }
                .onFailure { e ->
                    val msg = "Failed to delete '${dir.name}': ${e.message}"
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
