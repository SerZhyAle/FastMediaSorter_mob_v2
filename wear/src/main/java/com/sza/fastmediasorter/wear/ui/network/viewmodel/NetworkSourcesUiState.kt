package com.sza.fastmediasorter.wear.ui.network.viewmodel

import com.sza.fastmediasorter.wear.ui.network.SourceItem

/**
 * UI state for the Network Sources screen.
 */
sealed class NetworkSourcesUiState {
    data object Loading : NetworkSourcesUiState()
    data class Success(val sources: List<SourceItem>) : NetworkSourcesUiState()
    data object Empty : NetworkSourcesUiState()
    data class Error(val message: String) : NetworkSourcesUiState()
}
