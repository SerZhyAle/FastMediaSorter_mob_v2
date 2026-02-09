package com.sza.fastmediasorter.wear.ui.browse

import com.sza.fastmediasorter.wear.domain.model.WearMediaFile

/**
 * UI state for the browse screen.
 */
sealed class BrowseUiState {
    data object Loading : BrowseUiState()
    data class Success(val files: List<WearMediaFile>) : BrowseUiState()
    data class Empty(val message: String) : BrowseUiState()
    data class Error(val message: String) : BrowseUiState()
}
