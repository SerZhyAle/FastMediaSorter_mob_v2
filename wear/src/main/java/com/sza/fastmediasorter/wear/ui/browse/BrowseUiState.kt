package com.sza.fastmediasorter.wear.ui.browse

import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle

/**
 * UI state for the browse screen.
 */
sealed class BrowseUiState {
    data object Loading : BrowseUiState()
    data class Success(val files: List<WearMediaFile>) : BrowseUiState()
    data class Empty(val message: ScreenTitle) : BrowseUiState()
    data class Error(val message: ScreenTitle) : BrowseUiState()
}
