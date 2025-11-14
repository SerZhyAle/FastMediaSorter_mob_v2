package com.sza.fastmediasorter_v2.ui.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.di.IoDispatcher
import com.sza.fastmediasorter_v2.core.ui.BaseViewModel
import com.sza.fastmediasorter_v2.domain.model.DisplayMode
import com.sza.fastmediasorter_v2.domain.model.FileFilter
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.UndoOperation
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.domain.repository.SettingsRepository
import com.sza.fastmediasorter_v2.domain.usecase.FileOperation
import com.sza.fastmediasorter_v2.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter_v2.data.observer.MediaFileObserver
import com.sza.fastmediasorter_v2.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter_v2.domain.usecase.SizeFilter
import com.sza.fastmediasorter_v2.domain.usecase.UpdateResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class BrowseState(
    val resource: MediaResource? = null,
    val mediaFiles: List<MediaFile> = emptyList(),
    val totalFileCount: Int? = null, // Total count (null if not yet calculated)
    val selectedFiles: Set<String> = emptySet(),
    val lastSelectedPath: String? = null,
    val sortMode: SortMode = SortMode.NAME_ASC,
    val displayMode: DisplayMode = DisplayMode.LIST,
    val filter: FileFilter? = null,
    val lastOperation: UndoOperation? = null,
    val undoOperationTimestamp: Long? = null // Timestamp when undo operation was saved (for expiry check)
)

sealed class BrowseEvent {
    data class ShowError(val message: String, val details: String? = null, val exception: Throwable? = null) : BrowseEvent()
    data class ShowMessage(val message: String) : BrowseEvent()
    data class ShowUndoToast(val operationType: String) : BrowseEvent()
    data class NavigateToPlayer(val filePath: String, val fileIndex: Int) : BrowseEvent()
}

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val mediaScannerFactory: MediaScannerFactory,
    private val settingsRepository: SettingsRepository,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val fileOperationUseCase: FileOperationUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<BrowseState, BrowseEvent>() {

    private val resourceId: Long = savedStateHandle.get<Long>("resourceId") 
        ?: savedStateHandle.get<String>("resourceId")?.toLongOrNull() 
        ?: 0L
    
    private val skipAvailabilityCheck: Boolean = savedStateHandle.get<Boolean>("skipAvailabilityCheck") ?: false
    
    private var fileObserver: MediaFileObserver? = null

    override fun getInitialState() = BrowseState()

    init {
        loadResource()
    }
    
    override fun onCleared() {
        super.onCleared()
        stopFileObserver()
    }

    fun reloadFiles() {
        loadResource()
    }

    private fun loadResource() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            val resource = getResourcesUseCase.getById(resourceId)
            if (resource == null) {
                sendEvent(BrowseEvent.ShowError("Resource not found"))
                setLoading(false)
                return@launch
            }
            
            // Check if resource is available (skip if already validated in MainActivity)
            // For network resources (SFTP/FTP/SMB), skip this check and try to load files directly
            // because fileCount might be 0 if initial scan failed, but connection might work
            val isNetworkResource = resource.type in setOf(
                com.sza.fastmediasorter_v2.domain.model.ResourceType.SMB,
                com.sza.fastmediasorter_v2.domain.model.ResourceType.SFTP,
                com.sza.fastmediasorter_v2.domain.model.ResourceType.FTP
            )
            
            if (!skipAvailabilityCheck && !isNetworkResource && resource.fileCount == 0 && !resource.isWritable) {
                sendEvent(BrowseEvent.ShowError(
                    message = "Resource '${resource.name}' is unavailable. Check network connection or resource settings.",
                    details = "Resource ID: ${resource.id}\nType: ${resource.type}\nPath: ${resource.path}"
                ))
                setLoading(false)
                return@launch
            }
            
            updateState { 
                it.copy(
                    resource = resource,
                    sortMode = resource.sortMode,
                    displayMode = resource.displayMode
                ) 
            }
            
            // Start background file count (for header display)
            startFileCountInBackground()
            
            loadMediaFiles()
        }
    }

    private fun loadMediaFiles() {
        val resource = state.value.resource ?: return
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            // Get current settings for size filters
            val settings = settingsRepository.getSettings().first()
            val sizeFilter = SizeFilter(
                imageSizeMin = settings.imageSizeMin,
                imageSizeMax = settings.imageSizeMax,
                videoSizeMin = settings.videoSizeMin,
                videoSizeMax = settings.videoSizeMax,
                audioSizeMin = settings.audioSizeMin,
                audioSizeMax = settings.audioSizeMax
            )
            
            // Use chunked loading for SMB resources (show first 100 files quickly)
            val useChunked = resource.type == com.sza.fastmediasorter_v2.domain.model.ResourceType.SMB
            
            getMediaFilesUseCase(
                resource = resource,
                sortMode = state.value.sortMode,
                sizeFilter = sizeFilter,
                useChunkedLoading = useChunked,
                maxFiles = 100
            )
                .catch { e ->
                    Timber.e(e, "Error loading media files")
                    setLoading(false)
                    
                    // Build detailed error information
                    val errorTitle = when {
                        e.message?.contains("Authentication failed", ignoreCase = true) == true ||
                        e.message?.contains("LOGON_FAILURE", ignoreCase = true) == true -> {
                            "Authentication Failed"
                        }
                        e.message?.contains("Connection error", ignoreCase = true) == true ||
                        e.message?.contains("Network", ignoreCase = true) == true -> {
                            "Network Connection Error"
                        }
                        e.message?.contains("Permission denied", ignoreCase = true) == true -> {
                            "Permission Denied"
                        }
                        else -> {
                            "Error Loading Files"
                        }
                    }
                    
                    val errorMessage = when {
                        e.message?.contains("Authentication failed", ignoreCase = true) == true ||
                        e.message?.contains("LOGON_FAILURE", ignoreCase = true) == true -> {
                            "Invalid username or password.\n\n" +
                            "Please edit this resource and update credentials, then click 'Save'."
                        }
                        e.message?.contains("Connection error", ignoreCase = true) == true ||
                        e.message?.contains("Network", ignoreCase = true) == true -> {
                            "Cannot connect to server.\n\n" +
                            "Check network connection and server availability."
                        }
                        e.message?.contains("Permission denied", ignoreCase = true) == true -> {
                            "No access to this folder.\n\n" +
                            "Check folder permissions or credentials."
                        }
                        else -> {
                            "Failed to load media files.\n\n" +
                            "See error details below."
                        }
                    }
                    
                    val errorDetails = buildString {
                        append("Resource: ${resource.name}\n")
                        append("Path: ${resource.path}\n")
                        append("Type: ${resource.type}\n\n")
                        append("Error: ${e.message ?: "Unknown error"}\n\n")
                        append("Stack trace:\n${e.stackTraceToString()}")
                    }
                    
                    sendEvent(BrowseEvent.ShowError(
                        message = "$errorTitle\n\n$errorMessage",
                        details = errorDetails,
                        exception = e
                    ))
                    handleError(e)
                }
                .collect { files ->
                    updateState { it.copy(mediaFiles = files) }
                    setLoading(false)
                    
                    if (useChunked && files.size >= 100) {
                        Timber.d("Loaded first ${files.size} files via chunked loading")
                    }
                    
                    // Start FileObserver for local resources
                    startFileObserver()
                }
        }
    }

    /**
     * Start background file counting for large folders.
     * Updates totalFileCount in state without blocking UI.
     */
    private fun startFileCountInBackground() {
        val resource = state.value.resource ?: return
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            try {
                val settings = settingsRepository.getSettings().first()
                val sizeFilter = SizeFilter(
                    imageSizeMin = settings.imageSizeMin,
                    imageSizeMax = settings.imageSizeMax,
                    videoSizeMin = settings.videoSizeMin,
                    videoSizeMax = settings.videoSizeMax,
                    audioSizeMin = settings.audioSizeMin,
                    audioSizeMax = settings.audioSizeMax
                )
                
                val scanner = mediaScannerFactory.getScanner(resource.type)
                val count = scanner.getFileCount(
                    path = resource.path,
                    supportedTypes = resource.supportedMediaTypes,
                    sizeFilter = sizeFilter
                )
                
                updateState { it.copy(totalFileCount = count) }
                Timber.d("Background file count completed: $count files")
            } catch (e: Exception) {
                Timber.e(e, "Error counting files in background")
                
                // Show error if it's authentication-related (critical)
                if (e.message?.contains("Authentication failed", ignoreCase = true) == true ||
                    e.message?.contains("LOGON_FAILURE", ignoreCase = true) == true) {
                    
                    val errorDetails = buildString {
                        append("Resource: ${resource.name}\n")
                        append("Path: ${resource.path}\n")
                        append("Type: ${resource.type}\n\n")
                        append("Error: ${e.message ?: "Unknown error"}\n\n")
                        append("Stack trace:\n${e.stackTraceToString()}")
                    }
                    
                    sendEvent(BrowseEvent.ShowError(
                        message = "Authentication Failed\n\n" +
                                 "Cannot count files: Invalid credentials.\n\n" +
                                 "Please edit this resource and update credentials.",
                        details = errorDetails,
                        exception = e
                    ))
                }
                // Don't show error for other cases, count is optional
            }
        }
    }

    fun setSortMode(sortMode: SortMode) {
        updateState { it.copy(sortMode = sortMode) }
        loadMediaFiles()
    }

    fun toggleDisplayMode() {
        val resource = state.value.resource ?: return
        
        val newMode = if (state.value.displayMode == DisplayMode.LIST) {
            DisplayMode.GRID
        } else {
            DisplayMode.LIST
        }
        
        // Update state immediately for UI responsiveness
        updateState { it.copy(displayMode = newMode) }
        
        // Save to database
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            updateResourceUseCase(resource.copy(displayMode = newMode))
            Timber.d("Saved displayMode=$newMode for resource: ${resource.name}")
        }
    }

    fun selectFile(filePath: String) {
        updateState { state ->
            val newSelected = state.selectedFiles.toMutableSet()
            if (filePath in newSelected) {
                newSelected.remove(filePath)
            } else {
                newSelected.add(filePath)
            }
            state.copy(
                selectedFiles = newSelected,
                lastSelectedPath = filePath
            )
        }
    }
    
    fun selectFileRange(filePath: String) {
        updateState { state ->
            val lastPath = state.lastSelectedPath
            
            // If no file was selected before, just select this file
            if (lastPath == null || state.selectedFiles.isEmpty()) {
                Timber.d("First selection: $filePath")
                return@updateState state.copy(
                    selectedFiles = setOf(filePath),
                    lastSelectedPath = filePath
                )
            }
            
            // Find indices of last selected and current file
            val currentIndex = state.mediaFiles.indexOfFirst { it.path == filePath }
            val lastIndex = state.mediaFiles.indexOfFirst { it.path == lastPath }
            
            if (currentIndex == -1 || lastIndex == -1) {
                Timber.w("File not found in list: current=$currentIndex, last=$lastIndex")
                return@updateState state
            }
            
            // Select all files between last and current (inclusive)
            val startIndex = minOf(currentIndex, lastIndex)
            val endIndex = maxOf(currentIndex, lastIndex)
            
            val newSelected = state.selectedFiles.toMutableSet()
            for (i in startIndex..endIndex) {
                newSelected.add(state.mediaFiles[i].path)
            }
            
            Timber.d("Range selection: from $lastIndex to $currentIndex, selected ${newSelected.size} files")
            
            state.copy(
                selectedFiles = newSelected,
                lastSelectedPath = filePath
            )
        }
    }

    fun clearSelection() {
        updateState { it.copy(selectedFiles = emptySet(), lastSelectedPath = null) }
    }
    
    fun selectAll() {
        val allPaths = state.value.mediaFiles.map { it.path }.toSet()
        updateState { it.copy(selectedFiles = allPaths, lastSelectedPath = allPaths.lastOrNull()) }
    }

    fun openFile(file: MediaFile) {
        val resource = state.value.resource ?: return
        val index = state.value.mediaFiles.indexOf(file)
        
        // Save last viewed file to resource
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            updateResourceUseCase(resource.copy(lastViewedFile = file.path))
            Timber.d("Saved lastViewedFile=${file.name} for resource: ${resource.name}")
        }
        
        sendEvent(BrowseEvent.NavigateToPlayer(file.path, index))
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val selectedPaths = state.value.selectedFiles.toList()
            if (selectedPaths.isEmpty()) {
                sendEvent(BrowseEvent.ShowMessage("No files selected"))
                return@launch
            }
            
            setLoading(true)
            
            val deletedFiles = mutableListOf<String>()
            val failedFiles = mutableListOf<String>()
            
            // Separate local and network files
            val localFiles = mutableListOf<java.io.File>()
            val networkFiles = mutableListOf<java.io.File>()
            
            selectedPaths.forEach { path ->
                val file = java.io.File(path)
                if (path.startsWith("smb://") || path.startsWith("sftp://")) {
                    // Network file - wrap in File object with original path
                    networkFiles.add(object : java.io.File(path) {
                        override fun getAbsolutePath(): String = path
                        override fun getPath(): String = path
                    })
                } else {
                    // Local file
                    localFiles.add(file)
                }
            }
            
            // Delete local files using java.io.File
            localFiles.forEach { file ->
                if (file.exists()) {
                    try {
                        if (file.delete()) {
                            deletedFiles.add(file.absolutePath)
                            Timber.d("Deleted local file: ${file.absolutePath}")
                        } else {
                            failedFiles.add(file.name)
                            Timber.w("Failed to delete local file: ${file.absolutePath}")
                        }
                    } catch (e: Exception) {
                        failedFiles.add(file.name)
                        Timber.e(e, "Error deleting local file: ${file.absolutePath}")
                    }
                } else {
                    failedFiles.add(file.name)
                    Timber.w("Local file not found: ${file.absolutePath}")
                }
            }
            
            // Delete network files using FileOperationUseCase
            if (networkFiles.isNotEmpty()) {
                Timber.d("Deleting ${networkFiles.size} network files via FileOperationUseCase")
                
                val deleteOperation = FileOperation.Delete(
                    files = networkFiles
                )
                
                when (val result = fileOperationUseCase.execute(deleteOperation)) {
                    is com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult.Success -> {
                        deletedFiles.addAll(networkFiles.map { it.path })
                        Timber.i("Successfully deleted ${networkFiles.size} network files")
                    }
                    is com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult.PartialSuccess -> {
                        // Some files deleted, some failed
                        deletedFiles.addAll(networkFiles.take(result.processedCount).map { it.path })
                        failedFiles.addAll(networkFiles.drop(result.processedCount).map { it.name })
                        Timber.w("Partially deleted network files: ${result.processedCount}/${networkFiles.size}, errors: ${result.errors}")
                    }
                    is com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult.Failure -> {
                        failedFiles.addAll(networkFiles.map { it.name })
                        Timber.e("Failed to delete network files: ${result.error}")
                    }
                }
            }
            
            // Save undo operation for deleted files
            if (deletedFiles.isNotEmpty()) {
                val undoOp = UndoOperation(
                    type = com.sza.fastmediasorter_v2.domain.model.FileOperationType.DELETE,
                    sourceFiles = deletedFiles,
                    destinationFolder = null,
                    copiedFiles = null,
                    oldNames = null
                )
                saveUndoOperation(undoOp)
            }
            
            // Clear selection and reload files
            clearSelection()
            loadResource()
            
            // Show result message
            val message = when {
                deletedFiles.isEmpty() -> "No files were deleted"
                failedFiles.isEmpty() -> "Deleted ${deletedFiles.size} file(s)"
                else -> "Deleted ${deletedFiles.size} file(s), failed: ${failedFiles.joinToString(", ")}"
            }
            sendEvent(BrowseEvent.ShowMessage(message))
            
            setLoading(false)
        }
    }
    
    fun saveUndoOperation(operation: UndoOperation) {
        updateState { 
            it.copy(
                lastOperation = operation,
                undoOperationTimestamp = System.currentTimeMillis()
            ) 
        }
        Timber.d("Saved undo operation: ${operation.type}, ${operation.sourceFiles.size} files")
        
        // Show toast notification with undo hint
        val operationType = when (operation.type) {
            com.sza.fastmediasorter_v2.domain.model.FileOperationType.COPY -> "copied"
            com.sza.fastmediasorter_v2.domain.model.FileOperationType.MOVE -> "moved"
            com.sza.fastmediasorter_v2.domain.model.FileOperationType.DELETE -> "deleted"
            com.sza.fastmediasorter_v2.domain.model.FileOperationType.RENAME -> "renamed"
        }
        sendEvent(BrowseEvent.ShowUndoToast(operationType))
    }
    
    fun undoLastOperation() {
        val operation = state.value.lastOperation
        if (operation == null) {
            sendEvent(BrowseEvent.ShowMessage("No operation to undo"))
            return
        }
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            try {
                when (operation.type) {
                    com.sza.fastmediasorter_v2.domain.model.FileOperationType.COPY -> {
                        // Delete copied files
                        operation.copiedFiles?.forEach { path ->
                            val file = java.io.File(path)
                            if (file.exists()) {
                                file.delete()
                                Timber.d("Undo copy: deleted $path")
                            }
                        }
                        sendEvent(BrowseEvent.ShowMessage("Undo: copy operation cancelled"))
                    }
                    
                    com.sza.fastmediasorter_v2.domain.model.FileOperationType.MOVE -> {
                        // Move files back to original location
                        operation.copiedFiles?.forEachIndexed { index, destPath ->
                            val sourcePath = operation.sourceFiles.getOrNull(index)
                            if (sourcePath != null) {
                                val destFile = java.io.File(destPath)
                                val sourceFile = java.io.File(sourcePath)
                                if (destFile.exists()) {
                                    destFile.renameTo(sourceFile)
                                    Timber.d("Undo move: $destPath -> $sourcePath")
                                }
                            }
                        }
                        sendEvent(BrowseEvent.ShowMessage("Undo: move operation cancelled"))
                    }
                    
                    com.sza.fastmediasorter_v2.domain.model.FileOperationType.RENAME -> {
                        // Rename files back to original names
                        operation.oldNames?.forEach { (oldPath, newPath) ->
                            val newFile = java.io.File(newPath)
                            val oldFile = java.io.File(oldPath)
                            if (newFile.exists()) {
                                newFile.renameTo(oldFile)
                                Timber.d("Undo rename: $newPath -> $oldPath")
                            }
                        }
                        sendEvent(BrowseEvent.ShowMessage("Undo: rename operation cancelled"))
                    }
                    
                    com.sza.fastmediasorter_v2.domain.model.FileOperationType.DELETE -> {
                        // Restore files from trash folder
                        operation.copiedFiles?.let { paths ->
                            if (paths.isNotEmpty()) {
                                val trashDirPath = paths[0] // First element is trash directory
                                val trashDir = java.io.File(trashDirPath)
                                
                                if (trashDir.exists() && trashDir.isDirectory) {
                                    var restoredCount = 0
                                    val originalPaths = paths.drop(1) // Rest are original file paths
                                    
                                    trashDir.listFiles()?.forEach { trashedFile ->
                                        // Find corresponding original path by filename
                                        val originalPath = originalPaths.find { it.endsWith(trashedFile.name) }
                                        if (originalPath != null) {
                                            val originalFile = java.io.File(originalPath)
                                            if (trashedFile.renameTo(originalFile)) {
                                                restoredCount++
                                                Timber.d("Undo delete: restored ${trashedFile.name}")
                                            }
                                        }
                                    }
                                    
                                    // Remove trash directory if empty
                                    if (trashDir.listFiles()?.isEmpty() == true) {
                                        trashDir.delete()
                                    }
                                    
                                    sendEvent(BrowseEvent.ShowMessage("Undo: restored $restoredCount file(s)"))
                                } else {
                                    sendEvent(BrowseEvent.ShowMessage("Undo: trash folder not found"))
                                }
                            } else {
                                sendEvent(BrowseEvent.ShowMessage("Undo: no files to restore"))
                            }
                        } ?: sendEvent(BrowseEvent.ShowMessage("Undo: delete operation cannot be undone"))
                    }
                }
                
                // Clear undo operation after execution
                updateState { it.copy(lastOperation = null, undoOperationTimestamp = null) }
                
                // Reload files to reflect changes
                loadResource()
                
            } catch (e: Exception) {
                Timber.e(e, "Undo operation failed")
                sendEvent(BrowseEvent.ShowError("Undo failed: ${e.message}"))
            } finally {
                setLoading(false)
            }
        }
    }
    
    /**
     * Clear undo operation if it has expired (older than 5 minutes).
     * Call this when activity resumes or when checking before showing undo button.
     */
    fun clearExpiredUndoOperation() {
        val currentState = state.value
        val timestamp = currentState.undoOperationTimestamp
        
        if (timestamp != null && currentState.lastOperation != null) {
            val elapsedMillis = System.currentTimeMillis() - timestamp
            val expiryMillis = 5 * 60 * 1000L // 5 minutes
            
            if (elapsedMillis > expiryMillis) {
                updateState { it.copy(lastOperation = null, undoOperationTimestamp = null) }
                Timber.d("Cleared expired undo operation (age: ${elapsedMillis / 1000}s)")
            }
        }
    }
    
    fun setFilter(filter: FileFilter?) {
        updateState { 
            it.copy(filter = filter)
        }
        applyFilter()
    }
    
    private fun applyFilter() {
        val resource = state.value.resource ?: return
        val filter = state.value.filter
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            // Get current settings for size filters
            val settings = settingsRepository.getSettings().first()
            val sizeFilter = SizeFilter(
                imageSizeMin = settings.imageSizeMin,
                imageSizeMax = settings.imageSizeMax,
                videoSizeMin = settings.videoSizeMin,
                videoSizeMax = settings.videoSizeMax,
                audioSizeMin = settings.audioSizeMin,
                audioSizeMax = settings.audioSizeMax
            )
            
            getMediaFilesUseCase(resource, state.value.sortMode, sizeFilter)
                .catch { e ->
                    Timber.e(e, "Error loading media files")
                    sendEvent(BrowseEvent.ShowError(
                        message = "Failed to load media files: ${e.message ?: "Unknown error"}",
                        details = e.stackTraceToString(),
                        exception = e
                    ))
                }
                .collect { files ->
                    var filteredFiles = files
                    
                    // Apply filter if exists
                    if (filter != null) {
                        filteredFiles = files.filter { file ->
                            val matchesName = filter.nameContains == null || 
                                file.name.contains(filter.nameContains, ignoreCase = true)
                            
                            val matchesMinDate = filter.minDate == null || 
                                file.createdDate >= filter.minDate
                            
                            val matchesMaxDate = filter.maxDate == null || 
                                file.createdDate <= filter.maxDate
                            
                            val fileSizeMb = file.size / (1024f * 1024f)
                            val matchesMinSize = filter.minSizeMb == null || 
                                fileSizeMb >= filter.minSizeMb
                            
                            val matchesMaxSize = filter.maxSizeMb == null || 
                                fileSizeMb <= filter.maxSizeMb
                            
                            matchesName && matchesMinDate && matchesMaxDate && 
                                matchesMinSize && matchesMaxSize
                        }
                    }
                    
                    updateState { 
                        it.copy(mediaFiles = filteredFiles) 
                    }
                    setLoading(false)
                }
        }
    }
    
    /**
     * Start FileObserver for local resources to detect external file changes
     */
    private fun startFileObserver() {
        val resource = state.value.resource ?: return
        
        // Only observe local folders
        if (resource.type != com.sza.fastmediasorter_v2.domain.model.ResourceType.LOCAL) {
            return
        }
        
        // Stop previous observer if exists
        stopFileObserver()
        
        try {
            fileObserver = MediaFileObserver(
                path = resource.path,
                listener = object : MediaFileObserver.FileChangeListener {
                    override fun onFileDeleted(fileName: String) {
                        Timber.i("External file deleted: $fileName")
                        // Reload files to reflect deletion
                        reloadFiles()
                    }

                    override fun onFileCreated(fileName: String) {
                        Timber.i("External file created: $fileName")
                        // Reload files to reflect new file
                        reloadFiles()
                    }

                    override fun onFileMoved(fromName: String?, toName: String?) {
                        Timber.i("External file moved: from=$fromName, to=$toName")
                        // Reload files to reflect move
                        reloadFiles()
                    }

                    override fun onFileModified(fileName: String) {
                        Timber.d("External file modified: $fileName")
                        // Optionally reload to update file metadata
                        // For now, skip reload for modifications to avoid too many refreshes
                    }
                }
            )
            fileObserver?.startWatching()
            Timber.d("Started FileObserver for: ${resource.path}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start FileObserver for: ${resource.path}")
        }
    }
    
    /**
     * Stop FileObserver
     */
    private fun stopFileObserver() {
        fileObserver?.stopWatching()
        fileObserver = null
        Timber.d("Stopped FileObserver")
    }
}
