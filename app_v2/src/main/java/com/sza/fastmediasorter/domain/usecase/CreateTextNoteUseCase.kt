package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.model.MediaResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Creates a new text note file in the given Browse path.
 *
 * Mirrors [CreateDirectoryUseCase] in structure: validates inputs, then delegates to
 * [UnifiedFileOperationHandler.executeCreateTextFile] which dispatches to the matching
 * protocol strategy.
 *
 * LOCAL resources: file is created in-place.
 * Network/cloud resources: strategy returns a local staging path (Phase 03 impl) or an
 * [UnsupportedOperationException] stub (Phase 01 placeholders).
 */
class CreateTextNoteUseCase @Inject constructor(
    private val fileOperationHandler: UnifiedFileOperationHandler
) {
    /** Forbidden characters for filenames (same set as [CreateDirectoryUseCase]). */
    private val forbiddenChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    /**
     * @param resource    resource that owns the current Browse path
     * @param parentPath  protocol-specific path of the parent directory
     * @param fileName    desired name of the new file; `.txt` appended if extension absent
     * @param content     initial file content (empty string by default)
     * @return [Result] containing the protocol-specific or local absolute path of the created file
     */
    suspend operator fun invoke(
        resource: MediaResource,
        parentPath: String,
        fileName: String,
        content: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        Timber.d("S0189: CreateTextNoteUseCase.invoke parent=$parentPath name=$fileName resource=${resource.type}")

        if (resource.isReadOnly) {
            return@withContext Result.failure(Exception("Resource is read-only"))
        }

        val trimmedName = fileName.trim()
        if (trimmedName.isEmpty()) {
            return@withContext Result.failure(Exception("File name cannot be empty"))
        }

        if (trimmedName.any { it in forbiddenChars }) {
            return@withContext Result.failure(Exception("File name contains invalid characters"))
        }

        if (trimmedName.length > 255) {
            return@withContext Result.failure(Exception("File name is too long"))
        }

        // Append .txt if the name has no extension
        val finalName = if (trimmedName.contains('.')) trimmedName else "$trimmedName.txt"

        fileOperationHandler.executeCreateTextFile(
            parentPath = parentPath,
            fileName = finalName,
            resourceId = resource.id,
            content = content
        )
    }
}
