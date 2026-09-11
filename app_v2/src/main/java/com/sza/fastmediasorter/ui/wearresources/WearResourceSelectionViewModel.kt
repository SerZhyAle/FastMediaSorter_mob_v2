package com.sza.fastmediasorter.ui.wearresources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.repository.WearResourceSelectionRepositoryImpl
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.isAllFilesPredefined
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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
    private val selectionRepository: WearResourceSelectionRepositoryImpl,
    // S2515 (ADR-4): the selection write outlives this ViewModel on purpose - see setSelected.
    @param:ApplicationScope private val applicationScope: CoroutineScope
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
                // S2910: the send path drops everything outside WATCH_TRANSFERABLE, so listing the
                // rest here would hand the user a tick that cannot travel.
                val watchTransferable = deduplicated.filter {
                    !it.isHidden && it.type in ResourceType.WATCH_TRANSFERABLE
                }
                Timber.d("S2482: Wear resource selection loaded deduplicated=${deduplicated.size}")
                Timber.d("S2910: Wear picker transferable=${watchTransferable.size} of ${deduplicated.size}")
                val hasSaved = selectionRepository.hasSavedSelection()
                val selectedIds = if (hasSaved) {
                    val saved = selectionRepository.getSelectedIds()
                    val sanitized = saved.intersect(watchTransferable.map { it.id }.toSet())
                    if (sanitized != saved) {
                        selectionRepository.setSelectedIds(sanitized)
                    }
                    sanitized
                } else {
                    emptySet()
                }
                _uiState.value = _uiState.value.copy(
                    resources = watchTransferable,
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

    /**
     * Each tick is persisted immediately, so leaving the screen any way keeps the choice.
     *
     * S2515 (ADR-4): the write runs in the application scope, not [viewModelScope]. It used to be
     * synchronous and so always completed before the screen could go; a viewModelScope write would be
     * cancelled by the very act of leaving, which is the one thing the line above promises never
     * happens. The UI state is assigned outside the launch so the checkbox still flips in this frame.
     */
    fun setSelected(resourceId: Long, selected: Boolean) {
        val updated = if (selected) {
            _uiState.value.selectedIds + resourceId
        } else {
            _uiState.value.selectedIds - resourceId
        }
        applicationScope.launch { selectionRepository.setSelectedIds(updated) }
        _uiState.value = _uiState.value.copy(selectedIds = updated)
    }

    fun selectAll() {
        val allIds = _uiState.value.resources.map { it.id }.toSet()
        applicationScope.launch { selectionRepository.selectAll(allIds) }
        _uiState.value = _uiState.value.copy(selectedIds = allIds)
    }
}
