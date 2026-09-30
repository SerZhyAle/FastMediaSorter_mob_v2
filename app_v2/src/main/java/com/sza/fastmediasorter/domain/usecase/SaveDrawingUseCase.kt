package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.domain.files.FileNameConflictResolver
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.stats.EditKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.util.InPlaceFileReplacer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * S0191: commits a new drawing session to its final destination.
 *
 * LOCAL_DEFERRED drawings are written directly to the final local folder and then leave
 * staging. NETWORK_STAGED drawings keep their local editable copy until the session closes,
 * so Save can stay in draw mode while Cancel still has something concrete to clean up.
 */
class SaveDrawingUseCase @Inject constructor(
    private val fileOperationUseCase: FileOperationUseCase,
    private val stagingRegistry: LocalStagingRegistry,
    private val resourceRepository: ResourceRepository,
    // S0473: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink,
) {

    data class SaveOutcome(
        val finalName: String,
        val renamedDueToConflict: Boolean,
        val isLocalSaveOnly: Boolean,
        val remoteFailureMessage: String? = null,
        val finalPath: String,
        val editableLocalPath: String? = null,
    )

    private val forbiddenChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    private companion object {
        const val DRAWING_TEMP_TAG = "drawing"
    }

    suspend operator fun invoke(
        currentLocalFile: File,
        intendedName: String,
        imageBytes: ByteArray,
        keepEditableCopy: Boolean,
    ): Result<SaveOutcome> {
        return try {
            val normalizedName = normalizeName(intendedName, currentLocalFile.name)
            validateName(normalizedName)

            // The only caller launches on the main dispatcher, and every branch writes a
            // multi-megabyte merged PNG and stats or deletes files.
            val outcome = withContext(Dispatchers.IO) {
                val stagedEntry = stagingRegistry.lookup(currentLocalFile)
                when {
                    stagedEntry != null && stagedEntry.location == LocalStagingRegistry.Location.LOCAL_DEFERRED -> {
                        saveDeferredLocalDrawing(currentLocalFile, normalizedName, imageBytes)
                    }

                    stagedEntry == null -> {
                        savePlainLocalDrawing(currentLocalFile, normalizedName, imageBytes)
                    }

                    else -> {
                        saveNetworkStagedDrawing(
                            currentLocalFile = currentLocalFile,
                            normalizedName = normalizedName,
                            imageBytes = imageBytes,
                            stagedEntry = stagedEntry,
                            keepEditableCopy = keepEditableCopy,
                        )
                    }
                }
            }
            // S0473: one drawing saved (a network save that fell back to local-only still produced a
            // saved artifact, so it counts).
            statsSink.record(StatsEvent.Edit(EditKind.DRAWING))
            Result.success(outcome)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SaveDrawingUseCase failed")
            Result.failure(e)
        }
    }

    private fun saveDeferredLocalDrawing(
        currentLocalFile: File,
        normalizedName: String,
        imageBytes: ByteArray,
    ): SaveOutcome {
        val (targetFile, renamedDueToConflict) = resolveLocalTarget(currentLocalFile, normalizedName)
        writeDrawing(targetFile, imageBytes)
        if (currentLocalFile.absolutePath != targetFile.absolutePath && currentLocalFile.exists()) {
            currentLocalFile.delete()
        }
        stagingRegistry.unregister(currentLocalFile)
        return SaveOutcome(
            finalName = targetFile.name,
            renamedDueToConflict = renamedDueToConflict,
            isLocalSaveOnly = true,
            finalPath = targetFile.absolutePath,
            editableLocalPath = targetFile.absolutePath,
        )
    }

    private fun savePlainLocalDrawing(
        currentLocalFile: File,
        normalizedName: String,
        imageBytes: ByteArray,
    ): SaveOutcome {
        val (targetFile, renamedDueToConflict) = resolveLocalTarget(currentLocalFile, normalizedName)
        writeDrawing(targetFile, imageBytes)
        if (currentLocalFile.absolutePath != targetFile.absolutePath && currentLocalFile.exists()) {
            currentLocalFile.delete()
        }
        return SaveOutcome(
            finalName = targetFile.name,
            renamedDueToConflict = renamedDueToConflict,
            isLocalSaveOnly = true,
            finalPath = targetFile.absolutePath,
            editableLocalPath = targetFile.absolutePath,
        )
    }

    // The target may be the previous drawing, which is the user's only copy: `writeBytes` would
    // truncate it before the first byte of the new one exists.
    private fun writeDrawing(targetFile: File, imageBytes: ByteArray) {
        InPlaceFileReplacer.replace(targetFile, DRAWING_TEMP_TAG) { out ->
            out.write(imageBytes)
            true
        }
    }

    private suspend fun saveNetworkStagedDrawing(
        currentLocalFile: File,
        normalizedName: String,
        imageBytes: ByteArray,
        stagedEntry: LocalStagingRegistry.StagedEntry,
        keepEditableCopy: Boolean,
    ): SaveOutcome {
        val workingFile = prepareWorkingFile(currentLocalFile, normalizedName)
        workingFile.writeBytes(imageBytes)

        val credentialsId = resourceRepository.getResourceById(stagedEntry.targetResourceId)?.credentialsId
        val copyOp = FileOperation.Copy(
            sources = listOf(workingFile),
            destination = File(stagedEntry.targetParentPath),
            overwrite = false,
            sourceCredentialsId = credentialsId,
        )

        val result = fileOperationUseCase.execute(copyOp)
        val oldEntry = stagingRegistry.unregister(currentLocalFile)
        if (result is FileOperationResult.Success) {
            if (keepEditableCopy) {
                stagingRegistry.register(
                    file = workingFile,
                    targetResourceId = stagedEntry.targetResourceId,
                    targetParentPath = stagedEntry.targetParentPath,
                    intendedName = normalizedName,
                    kind = stagedEntry.kind,
                    location = stagedEntry.location,
                )
            } else {
                cleanupWorkingFile(workingFile)
            }
            return SaveOutcome(
                finalName = normalizedName,
                renamedDueToConflict = false,
                isLocalSaveOnly = false,
                finalPath = "${stagedEntry.targetParentPath}/$normalizedName",
                editableLocalPath = if (keepEditableCopy) workingFile.absolutePath else null,
            )
        }

        stagingRegistry.register(
            file = workingFile,
            targetResourceId = stagedEntry.targetResourceId,
            targetParentPath = stagedEntry.targetParentPath,
            intendedName = normalizedName,
            kind = oldEntry?.kind ?: stagedEntry.kind,
            location = stagedEntry.location,
        )
        return SaveOutcome(
            finalName = normalizedName,
            renamedDueToConflict = false,
            isLocalSaveOnly = true,
            remoteFailureMessage = result.toString(),
            finalPath = workingFile.absolutePath,
            editableLocalPath = workingFile.absolutePath,
        )
    }

    private fun resolveLocalTarget(currentLocalFile: File, normalizedName: String): Pair<File, Boolean> {
        val parentDir = currentLocalFile.parentFile
            ?: throw IllegalStateException("Parent dir is null for ${currentLocalFile.absolutePath}")

        if (currentLocalFile.name == normalizedName) {
            return currentLocalFile to false
        }

        val requestedTarget = File(parentDir, normalizedName)
        if (!requestedTarget.exists() || requestedTarget.absolutePath == currentLocalFile.absolutePath) {
            return requestedTarget to false
        }

        val (resolvedName, renamedDueToConflict) = FileNameConflictResolver.resolveLocal(parentDir, normalizedName)
        return File(parentDir, resolvedName) to renamedDueToConflict
    }

    private fun prepareWorkingFile(currentLocalFile: File, normalizedName: String): File {
        val stagingRoot = currentLocalFile.parentFile
            ?: throw IllegalStateException("Staging dir is null for ${currentLocalFile.absolutePath}")

        val sessionDir = if (stagingRoot.name.startsWith("drawing_session_")) {
            stagingRoot
        } else {
            File(stagingRoot, "drawing_session_${System.currentTimeMillis()}").apply { mkdirs() }
        }

        val targetFile = File(sessionDir, normalizedName)
        if (currentLocalFile.absolutePath == targetFile.absolutePath) {
            return currentLocalFile
        }

        if (!currentLocalFile.renameTo(targetFile)) {
            if (currentLocalFile.exists()) {
                currentLocalFile.copyTo(targetFile, overwrite = true)
                currentLocalFile.delete()
            }
        }
        return targetFile
    }

    private fun cleanupWorkingFile(file: File) {
        if (file.exists()) {
            file.delete()
        }
        val parent = file.parentFile ?: return
        if (parent.name.startsWith("drawing_session_") && parent.list().isNullOrEmpty()) {
            parent.delete()
        }
    }

    private fun normalizeName(input: String, fallbackName: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return trimmed
        val fallbackExtension = fallbackName.substringAfterLast('.', "jpg").ifBlank { "jpg" }
        // Detect a real extension via the substring after the last dot so a bare trailing dot
        // ("foo.") is treated as MISSING, not as already-extensioned (mirrors ImageEditorFileNamer).
        val hasExtension = trimmed.substringAfterLast('.', "").isNotBlank()
        return if (hasExtension) trimmed else "${trimmed.trimEnd('.')}.$fallbackExtension"
    }

    private fun validateName(fileName: String) {
        require(fileName.isNotBlank()) { "File name cannot be empty" }
        require(fileName.none { it in forbiddenChars }) { "File name contains invalid characters" }
        require(fileName.length <= 255) { "File name is too long" }
    }
}