package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.ArchiveFilesUseCase
import com.sza.fastmediasorter.domain.usecase.ArchiveProgress
import com.sza.fastmediasorter.domain.usecase.ArchiveAccessResult
import com.sza.fastmediasorter.domain.usecase.CreateDirectoryUseCase
import com.sza.fastmediasorter.domain.usecase.ExtractArchiveUseCase
import com.sza.fastmediasorter.domain.usecase.ExtractProgress
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.ExtractionState
import com.sza.fastmediasorter.utils.SafHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Manages archive (ZIP) creation and extraction operations in the Browse screen.
 *
 * Responsibilities:
 * - Archive selected files into a ZIP file ([archiveSelectedFiles]).
 * - Prepare and confirm extraction of a ZIP archive ([prepareExtraction], [extractArchive]).
 * - Cancel in-progress archive/extraction jobs ([cancelArchive], [cancelExtraction]).
 * - Report progress and results via [sendEvent] callbacks.
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition - IV.1).
 */
class BrowseArchiveManager(
    private val context: Context,
    private val archiveFilesUseCase: ArchiveFilesUseCase,
    private val extractArchiveUseCase: ExtractArchiveUseCase,
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val clearSelection: () -> Unit,
    private val reloadFiles: (clearList: Boolean) -> Unit
) {
    /** Active archive job - allows cancellation from UI. */
    private var archiveJob: Job? = null

    /** Active extraction job - allows cancellation from UI. */
    private var extractionJob: Job? = null

    @Volatile
    private var extractionCancelRequested: Boolean = false

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Archives the currently selected files into a single ZIP file.
     *
     * @param archiveName     Desired archive base name (e.g. "vacation_2026"; ".zip" appended if absent).
     * @param destinationPath Absolute path to the directory where the archive will be saved.
     */
    fun archiveSelectedFiles(archiveName: String, destinationPath: String) {
        val selectedPaths = stateFlow.value.selectedFiles.toList()
        if (selectedPaths.isEmpty()) {
            Timber.w("archiveSelectedFiles: no files selected")
            return
        }

        // Reject non-local resources up-front
        val resource = stateFlow.value.resource
        if (resource != null && resource.type.isNetworkResource) {
            sendEvent(BrowseEvent.ArchiveError(
                context.getString(R.string.archive_error_network_not_supported)
            ))
            return
        }

        archiveJob?.cancel()
        archiveJob = scope.launch(ioDispatcher) {
            Timber.i("archiveSelectedFiles: starting for ${selectedPaths.size} files → $destinationPath/$archiveName")
            try {
                archiveFilesUseCase(selectedPaths, archiveName, destinationPath).collect { progress ->
                    when (progress) {
                        is ArchiveProgress.Started -> {
                            Timber.d("archiveSelectedFiles: started, total=${progress.totalFiles}")
                        }
                        is ArchiveProgress.FileDone -> {
                            sendEvent(BrowseEvent.ArchiveProgress(progress.current, progress.total, progress.fileName))
                        }
                        is ArchiveProgress.FileWarning -> {
                            Timber.w("archiveSelectedFiles: warning for ${progress.fileName}: ${progress.reason}")
                        }
                        is ArchiveProgress.Success -> {
                            Timber.i("archiveSelectedFiles: success - ${progress.archivePath}")
                            clearSelection()
                            // Refresh file list if archive landed in the current directory
                            val currentDir = stateFlow.value.currentPath ?: stateFlow.value.resource?.path
                            if (currentDir != null && File(progress.archivePath).parent == currentDir) {
                                reloadFiles(false)
                            }
                            sendEvent(BrowseEvent.ArchiveSuccess(progress.archivePath, progress.archivedCount))
                        }
                        is ArchiveProgress.Error -> {
                            Timber.e(progress.exception, "archiveSelectedFiles: error - ${progress.message}")
                            sendEvent(BrowseEvent.ArchiveError(progress.message, progress.exception))
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Timber.i("archiveSelectedFiles: cancelled")
                // No event - UI dismisses the dialog itself
            }
        }
    }

    /** Cancels an in-progress archive operation. Partial archive file is deleted by the UseCase. */
    fun cancelArchive() {
        archiveJob?.cancel()
        archiveJob = null
    }

    /**
     * Phase 1: validate the file and resolve a unique target directory name, then show confirm dialog.
     */
    fun prepareExtraction(file: MediaFile) {
        val resource = stateFlow.value.resource ?: return
        if (resource.type != ResourceType.LOCAL) {
            sendEvent(BrowseEvent.ExtractionFailed(context.getString(R.string.unarchive_network_not_supported)))
            return
        }
        if (!isZipArchive(file)) {
            sendEvent(BrowseEvent.ExtractionFailed(context.getString(R.string.unarchive_only_zip_supported)))
            return
        }

        scope.launch(ioDispatcher) {
            val parentPath = stateFlow.value.currentPath ?: resource.path
            val targetDirName = resolveUniqueExtractionDirName(file.name, parentPath)
            val isProtected = runCatching { extractArchiveUseCase.isPasswordRequired(file.path) }
                .getOrElse { error ->
                    Timber.e(error, "prepareExtraction: failed to inspect archive protection")
                    sendEvent(
                        BrowseEvent.ExtractionFailed(
                            context.getString(
                                R.string.unarchive_error_generic,
                                context.getString(R.string.error_reason_unknown)
                            )
                        )
                    )
                    return@launch
                }
            withContext(Dispatchers.Main) {
                if (isProtected) {
                    sendEvent(BrowseEvent.ShowArchivePasswordDialog(file, targetDirName))
                } else {
                    sendEvent(BrowseEvent.ShowExtractConfirmDialog(file, targetDirName))
                }
            }
        }
    }

    /**
     * Phase 2: create target directory and stream-extract the ZIP archive.
     */
    fun extractArchive(file: MediaFile) = extractArchiveInternal(file, password = null)

    fun extractArchiveWithPassword(file: MediaFile, password: CharArray) = extractArchiveInternal(file, password)

    private fun extractArchiveInternal(file: MediaFile, password: CharArray?) {
        val resource = stateFlow.value.resource ?: return
        if (resource.type != ResourceType.LOCAL) {
            sendEvent(BrowseEvent.ExtractionFailed(context.getString(R.string.unarchive_network_not_supported)))
            return
        }
        if (!isZipArchive(file)) {
            sendEvent(BrowseEvent.ExtractionFailed(context.getString(R.string.unarchive_only_zip_supported)))
            return
        }

        extractionJob?.cancel()
        extractionCancelRequested = false

        extractionJob = scope.launch(ioDispatcher) {
            try {
                val parentPath = stateFlow.value.currentPath ?: resource.path
                val targetDirName = resolveUniqueExtractionDirName(file.name, parentPath)

                val accessResult = validateArchiveAccess(file, password, targetDirName)
                if (accessResult != ArchiveAccessResult.Accessible) {
                    return@launch
                }

                val createdDirPath = createDirectoryUseCase(resource, parentPath, targetDirName).getOrElse {
                    sendEvent(BrowseEvent.ExtractionFailed(
                        context.getString(R.string.unarchive_error_create_dir)
                    ))
                    return@launch
                }

                updateState {
                    it.copy(
                        extractionState = ExtractionState(
                            isExtracting = true,
                            currentEntry = "",
                            progressPercent = 0,
                            doneEntries = 0,
                            totalEntries = 0,
                            targetPath = createdDirPath
                        )
                    )
                }

                extractArchiveUseCase.invoke(
                    archivePath = file.path,
                    targetDirPath = createdDirPath,
                    password = password,
                    onCancel = { extractionCancelRequested }
                ).collect { progress ->
                    when (progress) {
                        is ExtractProgress.Started -> {
                            updateState {
                                it.copy(
                                    extractionState = it.extractionState.copy(
                                        isExtracting = true,
                                        totalEntries = progress.totalEntries,
                                        doneEntries = 0,
                                        progressPercent = 0
                                    )
                                )
                            }
                        }

                        is ExtractProgress.EntryDone -> {
                            updateState {
                                it.copy(
                                    extractionState = it.extractionState.copy(
                                        isExtracting = true,
                                        currentEntry = progress.entryName,
                                        doneEntries = progress.done,
                                        totalEntries = progress.total,
                                        progressPercent = progress.percent
                                    )
                                )
                            }
                            sendEvent(
                                BrowseEvent.ExtractionProgress(
                                    entryName = progress.entryName,
                                    done = progress.done,
                                    total = progress.total,
                                    percent = progress.percent
                                )
                            )
                        }

                        is ExtractProgress.Success -> {
                            updateState {
                                it.copy(
                                    extractionState = it.extractionState.copy(
                                        isExtracting = false,
                                        progressPercent = 100,
                                        targetPath = progress.targetPath
                                    )
                                )
                            }
                            reloadFiles(false)
                            sendEvent(BrowseEvent.ExtractionSuccess(progress.targetPath, progress.extractedCount))
                        }

                        is ExtractProgress.Failure -> {
                            updateState {
                                it.copy(
                                    extractionState = it.extractionState.copy(isExtracting = false)
                                )
                            }
                            if (progress.error != "cancelled") {
                                val message = when (progress.error) {
                                    "zip_bomb" -> context.getString(R.string.unarchive_error_zip_bomb)
                                    "no_space"  -> context.getString(R.string.unarchive_error_no_space)
                                    "password_required" -> context.getString(R.string.protected_archive_password_wrong)
                                    "password_invalid" -> context.getString(R.string.protected_archive_password_wrong)
                                    else        -> context.getString(
                                        R.string.unarchive_error_generic,
                                        context.getString(R.string.error_reason_unknown)
                                    )
                                }
                                sendEvent(BrowseEvent.ExtractionFailed(message))
                            }
                        }
                    }
                }
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    /** Cancels an in-progress extraction. */
    fun cancelExtraction() {
        extractionCancelRequested = true
        extractionJob?.cancel()
        extractionJob = null
        updateState {
            it.copy(extractionState = it.extractionState.copy(isExtracting = false))
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun resolveUniqueExtractionDirName(archiveFileName: String, parentPath: String): String {
        val base = archiveFileName.replaceFirst(Regex("(?i)\\.zip$"), "").ifBlank { "extracted" }
        for (index in 0..99) {
            val candidate = if (index == 0) base else "${base}_$index"
            if (!folderExists(parentPath, candidate)) {
                return candidate
            }
        }
        return "${base}_99"
    }

    private suspend fun folderExists(parentPath: String, folderName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (parentPath.startsWith("content:/")) {
                val dirUri = SafHelper.parseUri(parentPath)
                val parentDoc = SafHelper.getDocumentFileFromUri(context, dirUri)
                    ?: return@withContext false
                parentDoc.findFile(folderName)?.isDirectory == true
            } else {
                File(parentPath, folderName).exists()
            }
        }

    private fun validateArchiveAccess(
        file: MediaFile,
        password: CharArray?,
        targetDirName: String
    ): ArchiveAccessResult {
        val accessResult = extractArchiveUseCase.validateArchiveAccess(file.path, password)
        when (accessResult) {
            ArchiveAccessResult.Accessible -> Unit
            ArchiveAccessResult.PasswordRequired -> sendEvent(BrowseEvent.ShowArchivePasswordDialog(file, targetDirName))
            ArchiveAccessResult.InvalidPassword -> sendEvent(
                BrowseEvent.ExtractionFailed(context.getString(R.string.protected_archive_password_wrong))
            )
            ArchiveAccessResult.Unreadable -> sendEvent(
                BrowseEvent.ExtractionFailed(
                    context.getString(
                        R.string.unarchive_error_generic,
                        context.getString(R.string.error_reason_unknown)
                    )
                )
            )
        }
        return accessResult
    }

    private fun isZipArchive(file: MediaFile): Boolean {
        if (file.type != MediaType.BINARY_ARCHIVE) return false
        val extension = file.name.substringAfterLast('.', "").lowercase()
        return extension == "zip"
    }
}
