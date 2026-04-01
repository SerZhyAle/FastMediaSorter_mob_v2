package com.sza.fastmediasorter.ui.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sza.fastmediasorter.domain.model.DuplicateDetectionResult
import com.sza.fastmediasorter.domain.model.DuplicateGroup
import com.sza.fastmediasorter.domain.model.DuplicateScanProgress
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScanPhase
import com.sza.fastmediasorter.domain.repository.DuplicateHashRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.DeleteFilesUseCase
import com.sza.fastmediasorter.domain.usecase.DetectDuplicatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class DuplicatesState(
    val availableResources: List<MediaResource> = emptyList(),
    val selectedResourceIds: Set<Long> = emptySet(),
    val scanState: ScanState = ScanState.Idle,
    val result: DuplicateDetectionResult? = null,
    val selectedFilePaths: Set<String> = emptySet()
)

sealed class ScanState {
    object Idle : ScanState()
    data class Running(val progress: DuplicateScanProgress) : ScanState()
    data class Error(val message: String) : ScanState()
}

sealed class DuplicatesEvent {
    data class ShowNetworkWarning(val networkResourceCount: Int) : DuplicatesEvent()
    data class ShowError(val message: String) : DuplicatesEvent()
    data class FileDeleted(val path: String) : DuplicatesEvent()
    object ScanComplete : DuplicatesEvent()
    object BatchDeletionComplete : DuplicatesEvent()
}

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val detectDuplicatesUseCase: DetectDuplicatesUseCase,
    private val deleteFilesUseCase: DeleteFilesUseCase,
    private val hashRepository: DuplicateHashRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DuplicatesState())
    val state: StateFlow<DuplicatesState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DuplicatesEvent>()
    val events: SharedFlow<DuplicatesEvent> = _events.asSharedFlow()

    init {
        loadResources()
    }

    private fun loadResources() {
        viewModelScope.launch {
            try {
                val resources = resourceRepository.getAllResourcesSync()
                _state.update { it.copy(availableResources = resources) }
            } catch (e: Exception) {
                Timber.e(e, "DuplicatesViewModel: failed to load resources")
            }
        }
    }

    fun toggleResourceSelection(resourceId: Long) {
        _state.update { current ->
            val selected = current.selectedResourceIds.toMutableSet()
            if (selected.contains(resourceId)) selected.remove(resourceId) else selected.add(resourceId)
            current.copy(selectedResourceIds = selected)
        }
    }

    fun startScan() {
        val selectedIds = _state.value.selectedResourceIds.toList()
        if (selectedIds.isEmpty()) return

        val networkCount = _state.value.availableResources
            .filter { it.id in selectedIds && it.type.isNetworkResource }
            .size

        if (networkCount > 0) {
            viewModelScope.launch {
                _events.emit(DuplicatesEvent.ShowNetworkWarning(networkCount))
            }
            return
        }
        doStartScan(selectedIds)
    }

    fun startScanConfirmed() {
        doStartScan(_state.value.selectedResourceIds.toList())
    }

    private var scanJob: kotlinx.coroutines.Job? = null

    private fun doStartScan(resourceIds: List<Long>) {
        val resources = _state.value.availableResources.filter { it.id in resourceIds }
        scanJob?.cancel()
        scanJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                detectDuplicatesUseCase.detect(resources).collect { emission ->
                    when (emission) {
                        is DuplicateScanProgress -> {
                            _state.update { it.copy(scanState = ScanState.Running(emission)) }
                        }
                        is DuplicateDetectionResult -> {
                            // Auto-select duplicates: preserve the oldest file in each group
                            val initialSelection = mutableSetOf<String>()
                            emission.groups.forEach { group ->
                                val sorted = group.files.sortedBy { it.lastModified }
                                if (sorted.size > 1) {
                                    initialSelection.addAll(sorted.drop(1).map { it.path })
                                }
                            }
                            _state.update {
                                it.copy(
                                    scanState = ScanState.Idle,
                                    result = emission,
                                    selectedFilePaths = initialSelection
                                )
                            }
                            _events.emit(DuplicatesEvent.ScanComplete)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "DuplicatesViewModel: scan failed")
                    _state.update { it.copy(scanState = ScanState.Error(e.message ?: "Scan failed")) }
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(scanState = ScanState.Idle) }
    }

    fun toggleFileSelection(path: String) {
        _state.update { current ->
            val set = current.selectedFilePaths.toMutableSet()
            if (!set.add(path)) {
                set.remove(path)
            }
            current.copy(selectedFilePaths = set)
        }
    }

    fun deleteSelectedFiles() {
        val selectedPaths = _state.value.selectedFilePaths
        if (selectedPaths.isEmpty()) return
        
        val result = _state.value.result ?: return
        val filesToDelete = result.groups.flatMap { it.files }.filter { it.path in selectedPaths }
        
        // Show blocking loader
        _state.update { it.copy(scanState = ScanState.Running(DuplicateScanProgress(ScanPhase.DONE, 0, filesToDelete.size))) }
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val ioFiles = filesToDelete.map { File(it.path) }
                deleteFilesUseCase(ioFiles)
                filesToDelete.forEach { hashRepository.deleteHashEntry(it) }
                
                _state.update { current ->
                    val updatedResult = current.result?.let { oldResult ->
                        val updatedGroups = oldResult.groups.mapNotNull { g ->
                            val remaining = g.files.filter { it.path !in selectedPaths }
                            if (remaining.size >= 2) g.copy(files = remaining) else null
                        }
                        oldResult.copy(
                            groups = updatedGroups,
                            totalWastedBytes = updatedGroups.sumOf { it.fileSize * (it.files.size - 1) }
                        )
                    }
                    current.copy(
                        scanState = ScanState.Idle,
                        result = updatedResult,
                        selectedFilePaths = emptySet()
                    )
                }
                _events.emit(DuplicatesEvent.BatchDeletionComplete)
            } catch (e: Exception) {
                Timber.e(e, "DuplicatesViewModel: deleteSelectedFiles failed")
                _state.update { it.copy(scanState = ScanState.Idle) }
                _events.emit(DuplicatesEvent.ShowError(e.message ?: "Delete failed"))
            }
        }
    }

    fun deleteFile(file: MediaFile, group: DuplicateGroup) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                deleteFilesUseCase(listOf(File(file.path)))
                hashRepository.deleteHashEntry(file)
                // Remove file from result in memory
                _state.update { current ->
                    val updatedResult = current.result?.let { result ->
                        val updatedGroups = result.groups.mapNotNull { g ->
                            if (g.fullHash == group.fullHash) {
                                val remaining = g.files.filter { it.path != file.path }
                                if (remaining.size >= 2) g.copy(files = remaining) else null
                            } else g
                        }
                        result.copy(
                            groups = updatedGroups,
                            totalWastedBytes = updatedGroups.sumOf { it.fileSize * (it.files.size - 1) }
                        )
                    }
                    current.copy(result = updatedResult, selectedFilePaths = current.selectedFilePaths - file.path)
                }
                _events.emit(DuplicatesEvent.FileDeleted(file.path))
            } catch (e: Exception) {
                Timber.e(e, "DuplicatesViewModel: deleteFile failed for ${file.path}")
                _events.emit(DuplicatesEvent.ShowError(e.message ?: "Delete failed"))
            }
        }
    }

    /** Called by Fragment when scan result arrives via DetectDuplicatesUseCase directly. */
    fun setResult(result: DuplicateDetectionResult) {
        val initialSelection = mutableSetOf<String>()
        result.groups.forEach { group ->
            val sorted = group.files.sortedBy { it.lastModified }
            if (sorted.size > 1) {
                initialSelection.addAll(sorted.drop(1).map { it.path })
            }
        }
        _state.update { it.copy(result = result, scanState = ScanState.Idle, selectedFilePaths = initialSelection) }
    }
}
