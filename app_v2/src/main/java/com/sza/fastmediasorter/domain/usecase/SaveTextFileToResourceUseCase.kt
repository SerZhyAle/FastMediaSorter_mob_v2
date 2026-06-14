package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.model.MediaResource
import java.io.File
import javax.inject.Inject

/**
 * Saves arbitrary UTF-8 text into the root of a chosen writable resource.
 *
 * Reuses the text-note staging pipeline so local, network, and cloud destinations all follow
 * the same protocol-specific file-write path.
 */
class SaveTextFileToResourceUseCase @Inject constructor(
    private val fileOperationHandler: UnifiedFileOperationHandler,
    private val saveTextNoteUseCase: SaveTextNoteUseCase,
) {
    private val forbiddenChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    suspend operator fun invoke(
        resource: MediaResource,
        fileName: String,
        content: String,
    ): Result<String> {
        if (resource.isReadOnly || !resource.isWritable) {
            return Result.failure(IllegalStateException("Destination is not writable"))
        }

        val trimmedName = fileName.trim()
        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("File name cannot be empty"))
        }
        if (trimmedName.any { it in forbiddenChars }) {
            return Result.failure(IllegalArgumentException("File name contains invalid characters"))
        }
        if (trimmedName.length > 255) {
            return Result.failure(IllegalArgumentException("File name is too long"))
        }

        val finalName = if (trimmedName.contains('.')) trimmedName else "$trimmedName.txt"
        val createdPath = fileOperationHandler.executeCreateTextFile(
            parentPath = resource.path,
            fileName = finalName,
            resourceId = resource.id,
            content = "",
        ).getOrElse { return Result.failure(it) }

        val outcome = saveTextNoteUseCase(
            currentLocalFile = File(createdPath),
            intendedName = finalName,
            content = content,
        ).getOrElse { return Result.failure(it) }

        if (outcome.remoteFailureMessage != null) {
            return Result.failure(IllegalStateException(outcome.remoteFailureMessage))
        }

        return Result.success(outcome.finalPath)
    }
}
