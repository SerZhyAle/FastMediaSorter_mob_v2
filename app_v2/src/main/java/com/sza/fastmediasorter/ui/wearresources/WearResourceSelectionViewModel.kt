package com.sza.fastmediasorter.ui.wearresources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.repository.WearResourceSelectionRepositoryImpl
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.isAllFilesPredefined
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class ResourceCategory {
    VIRTUAL,
    INTERNAL,
    EXTERNAL
}

fun MediaResource.getResourceCategory(): ResourceCategory {
    return when {
        type.isNetworkResource || type == ResourceType.WEAR_WATCH -> ResourceCategory.EXTERNAL
        isAllFilesPredefined || profile != ResourceProfile.NONE || id < 0 ||
            type == ResourceType.HTTP_STREAM || type == ResourceType.RTSP_STREAM -> ResourceCategory.VIRTUAL
        else -> ResourceCategory.INTERNAL
    }
}

/** What the picker draws: registered resources split into categories, selected ids, and expansion state. */
data class WearResourceSelectionUiState(
    val resources: List<MediaResource> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val expandedCategories: Set<ResourceCategory> = setOf(
        ResourceCategory.VIRTUAL,
        ResourceCategory.INTERNAL,
        ResourceCategory.EXTERNAL
    ),
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
                val deduplicated = resources.distinctBy { Pair(it.id, it.path) }
                Timber.d("S2482: Wear resource selection loaded deduplicated=${deduplicated.size}")
                val hasSaved = selectionRepository.hasSavedSelection()
                val selectedIds = if (hasSaved) {
                    selectionRepository.getSelectedIds()
                } else {
                    // Default selection: auto-select local & virtual resources, force external network resources to be unselected
                    val defaultSelected = deduplicated
                        .filter { it.getResourceCategory() != ResourceCategory.EXTERNAL }
                        .map { it.id }
                        .toSet()
                    selectionRepository.setSelectedIds(defaultSelected)
                    defaultSelected
                }
                _uiState.value = _uiState.value.copy(
                    resources = deduplicated,
                    selectedIds = selectedIds,
                    isLoaded = true
                )
            }
        }
    }

    /** Toggle expansion of a resource group in the UI list. */
    fun toggleCategoryExpanded(category: ResourceCategory) {
        val current = _uiState.value.expandedCategories
        val updated = if (category in current) current - category else current + category
        _uiState.value = _uiState.value.copy(expandedCategories = updated)
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
