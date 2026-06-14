package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.domain.files.FileNameConflictResolver
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * S0189: commits an edited text note to its final destination.
 *
 * - LOCAL note (not in staging registry): write content directly to [currentLocalFile] parent,
 *   applying conflict resolution if needed.
 * - NETWORK-STAGED note (found in staging registry): rewrite the local staging file, then
 *   issue a [FileOperation.Copy] to upload it via the [FileOperationUseCase] pipeline.
 *   On remote failure, the local staging file is preserved so the user can retry.
 */
class SaveTextNoteUseCase @Inject constructor(
    private val fileOperationUseCase: FileOperationUseCase,
    private val stagingRegistry: LocalStagingRegistry,
    private val resourceRepository: ResourceRepository
) {

    data class SaveOutcome(
        val finalName: String,
        val renamedDueToConflict: Boolean,
        val isLocalSaveOnly: Boolean,
        val remoteFailureMessage: String? = null,
        val finalPath: String
    )

    suspend operator fun invoke(
        currentLocalFile: File,
        intendedName: String,
        content: String
    ): Result<SaveOutcome> {
        return try {
            val stagedNote = stagingRegistry.lookup(currentLocalFile)

            // S0189: LOCAL_DEFERRED = file was never created on disk; write directly to the
            // intended target inside the LOCAL resource directory, then drop the registry entry.
            if (stagedNote != null && stagedNote.location == LocalStagingRegistry.Location.LOCAL_DEFERRED) {
                val parentDir = File(stagedNote.targetParentPath)
                val (finalName, renamed) = FileNameConflictResolver.resolveLocal(parentDir, intendedName)
                val targetFile = File(parentDir, finalName)
                targetFile.writeText(content, Charsets.UTF_8)
                stagingRegistry.unregister(currentLocalFile)

                val outcome = SaveOutcome(
                    finalName = finalName,
                    renamedDueToConflict = renamed,
                    isLocalSaveOnly = true,
                    finalPath = targetFile.absolutePath
                )
                return Result.success(outcome)
            }

            if (stagedNote == null) {
                // Pure-local save: overwrite in place or resolve conflict in parent dir
                val parentDir = currentLocalFile.parentFile
                    ?: return Result.failure(IllegalStateException("Parent dir is null for ${currentLocalFile.absolutePath}"))

                val (finalName, renamed) = FileNameConflictResolver.resolveLocal(parentDir, intendedName)
                val targetFile = File(parentDir, finalName)

                targetFile.writeText(content, Charsets.UTF_8)
                // Delete old file if the name changed
                if (currentLocalFile.name != finalName && currentLocalFile.exists()) {
                    currentLocalFile.delete()
                }

                val outcome = SaveOutcome(
                    finalName = finalName,
                    renamedDueToConflict = renamed,
                    isLocalSaveOnly = true,
                    finalPath = targetFile.absolutePath
                )
                Result.success(outcome)

            } else {
                // Network-staged save: rewrite local staging file, then upload via Copy.
                // The staging file may not exist yet (deferred creation) - writeText below
                // creates it before the upload step.
                val stagingDir = currentLocalFile.parentFile
                    ?: return Result.failure(IllegalStateException("Staging dir is null"))

                // Conflict check is omitted for network destination (see spec §6.2 note)
                val finalName = intendedName
                val renamedDueToConflict = false

                // Rewrite staging file (rename if needed)
                val finalStagingFile = if (currentLocalFile.name != finalName) {
                    val renamed = File(stagingDir, finalName)
                    currentLocalFile.renameTo(renamed)
                    renamed
                } else {
                    currentLocalFile
                }
                finalStagingFile.writeText(content, Charsets.UTF_8)

                val credentialsId = resourceRepository.getResourceById(stagedNote.targetResourceId)?.credentialsId

                val copyOp = FileOperation.Copy(
                    sources = listOf(finalStagingFile),
                    destination = File(stagedNote.targetParentPath),
                    overwrite = false,
                    sourceCredentialsId = credentialsId
                )

                val result = fileOperationUseCase.execute(copyOp)
                val isSuccess = result is FileOperationResult.Success

                if (isSuccess) {
                    finalStagingFile.delete()
                    stagingRegistry.unregister(currentLocalFile)
                    val outcome = SaveOutcome(
                        finalName = finalName,
                        renamedDueToConflict = renamedDueToConflict,
                        isLocalSaveOnly = false,
                        finalPath = "${stagedNote.targetParentPath}/$finalName"
                    )
                    Result.success(outcome)
                } else {
                    val errMsg = result.toString()
                    val outcome = SaveOutcome(
                        finalName = finalName,
                        renamedDueToConflict = renamedDueToConflict,
                        isLocalSaveOnly = true,
                        remoteFailureMessage = errMsg,
                        finalPath = finalStagingFile.absolutePath
                    )
                    Result.success(outcome)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SaveTextNoteUseCase failed")
            Result.failure(e)
        }
    }
}
