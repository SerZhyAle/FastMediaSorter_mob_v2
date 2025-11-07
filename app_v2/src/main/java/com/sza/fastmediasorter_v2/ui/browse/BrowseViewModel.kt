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
import com.sza.fastmediasorter_v2.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetResourcesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class BrowseState(
    val resource: MediaResource? = null,
    val mediaFiles: List<MediaFile> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val lastSelectedPath: String? = null,
    val sortMode: SortMode = SortMode.NAME_ASC,
    val displayMode: DisplayMode = DisplayMode.LIST,
    val filter: FileFilter? = null,
    val lastOperation: UndoOperation? = null
)

sealed class BrowseEvent {
    data class ShowError(val message: String) : BrowseEvent()
    data class ShowMessage(val message: String) : BrowseEvent()
    data class NavigateToPlayer(val filePath: String, val fileIndex: Int) : BrowseEvent()
}

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<BrowseState, BrowseEvent>() {

    private val resourceId: Long = savedStateHandle.get<Long>("resourceId") 
        ?: savedStateHandle.get<String>("resourceId")?.toLongOrNull() 
        ?: 0L

    override fun getInitialState() = BrowseState()

    init {
        loadResource()
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
            
            updateState { 
                it.copy(
                    resource = resource,
                    sortMode = resource.sortMode,
                    displayMode = resource.displayMode
                ) 
            }
            
            loadMediaFiles()
        }
    }

    private fun loadMediaFiles() {
        val resource = state.value.resource ?: return
        
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            setLoading(true)
            
            getMediaFilesUseCase(resource, state.value.sortMode)
                .catch { e ->
                    Timber.e(e, "Error loading media files")
                    handleError(e)
                }
                .collect { files ->
                    updateState { it.copy(mediaFiles = files) }
                    setLoading(false)
                }
        }
    }

    fun setSortMode(sortMode: SortMode) {
        updateState { it.copy(sortMode = sortMode) }
        loadMediaFiles()
    }

    fun toggleDisplayMode() {
        val newMode = if (state.value.displayMode == DisplayMode.LIST) {
            DisplayMode.GRID
        } else {
            DisplayMode.LIST
        }
        updateState { it.copy(displayMode = newMode) }
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

    fun openFile(file: MediaFile) {
        val index = state.value.mediaFiles.indexOf(file)
        sendEvent(BrowseEvent.NavigateToPlayer(file.path, index))
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val selectedPaths = state.value.selectedFiles
            if (selectedPaths.isEmpty()) {
                sendEvent(BrowseEvent.ShowMessage("No files selected"))
                return@launch
            }
            
            // TODO: Реализовать удаление файлов
            Timber.d("Delete files: $selectedPaths")
            sendEvent(BrowseEvent.ShowMessage("Delete: ${selectedPaths.size} files"))
        }
    }
    
    fun saveUndoOperation(operation: UndoOperation) {
        updateState { it.copy(lastOperation = operation) }
        Timber.d("Saved undo operation: ${operation.type}, ${operation.sourceFiles.size} files")
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
                        // Restore deleted files (if they were only marked for deletion)
                        sendEvent(BrowseEvent.ShowMessage("Undo: delete operation cancelled"))
                    }
                }
                
                // Clear undo operation after execution
                updateState { it.copy(lastOperation = null) }
                
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
            
            getMediaFilesUseCase(resource, state.value.sortMode)
                .catch { e ->
                    Timber.e(e, "Error loading media files")
                    sendEvent(BrowseEvent.ShowError("Failed to load media files: ${e.message}"))
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
}
