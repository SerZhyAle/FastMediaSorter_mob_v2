package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.stats.FileOpAction
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsMediaType
import com.sza.fastmediasorter.domain.stats.StatsSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for creating a new directory within a resource.
 * Handles validation and delegates to UnifiedFileOperationHandler.
 */
class CreateDirectoryUseCase @Inject constructor(
    private val fileOperationHandler: UnifiedFileOperationHandler,
    // S0473: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink
) {
    /**
     * Create a new directory.
     * 
     * @param resource The resource where the directory should be created
     * @param parentPath The protocol-specific path of the parent directory
     * @param folderName The name of the new folder to create
     * @return Result with the path of the created directory
     */
    suspend operator fun invoke(
        resource: MediaResource,
        parentPath: String,
        folderName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        // 1. Validation: Basic read-only check
        if (resource.isReadOnly) {
            return@withContext Result.failure(Exception("Resource is read-only"))
        }

        // 2. Validation: Name constraints
        val trimmedName = folderName.trim()
        if (trimmedName.isEmpty()) {
            return@withContext Result.failure(Exception("Folder name cannot be empty"))
        }

        // Forbidden characters: / \ : * ? " < > |
        val forbiddenChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        if (trimmedName.any { it in forbiddenChars }) {
            return@withContext Result.failure(Exception("Folder name contains invalid characters"))
        }

        if (trimmedName.length > 255) {
            return@withContext Result.failure(Exception("Folder name is too long"))
        }

        // 3. Construct full path
        val fullPath = if (parentPath.endsWith("/")) {
            "$parentPath$trimmedName"
        } else {
            "$parentPath/$trimmedName"
        }

        // 4. Execute operation
        val result = fileOperationHandler.executeCreateDirectory(fullPath)
        if (result.isSuccess) {
            // S0473: one folder created.
            statsSink.record(StatsEvent.FileOp(FileOpAction.CREATE_FOLDER, StatsMediaType.OTHER, 1L, 0L))
        }
        result
    }
}
