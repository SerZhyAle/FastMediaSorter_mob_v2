package com.sza.fastmediasorter.ui.wearresources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.repository.WearResourceSelectionRepositoryImpl
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the picker draws: every registered resource, plus the ids currently marked for the watch. */
data class WearResourceSelectionUiState(
    val resources: List<MediaResource> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isLoaded: Boolean = false
)

@HiltViewModel
class WearResourceSelectionViewModel @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val selectionRepository: WearResourceSelectionRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearResourceSelectionUiState())
    val uiState: StateFlow<WearResourceSelectionUiState> = _uiState.asStateFlow()

    init {
        observeResources()
    }

    private fun observeResources() {
        viewModelScope.launch {
            resourceRepository.getAllResources().collect { resources ->
                _uiState.value = _uiState.value.copy(
                    resources = resources,
                    selectedIds = selectionRepository.getSelectedIds(),
                    isLoaded = true
                )
            }
        }
    }

    /** Each tick is persisted immediately, so leaving the screen any way keeps the choice. */
    fun setSelected(resourceId: Long, selected: Boolean) {
        val updated = if (selected) {
            _uiState.value.selectedIds + resourceId
        } else {
            _uiState.value.selectedIds - resourceId
        }
        selectionRepository.setSelectedIds(updated)
        _uiState.value = _uiState.value.copy(selectedIds = updated)
    }

    fun selectAll() {
        val allIds = _uiState.value.resources.map { it.id }.toSet()
        selectionRepository.selectAll(allIds)
        _uiState.value = _uiState.value.copy(selectedIds = allIds)
    }
}
