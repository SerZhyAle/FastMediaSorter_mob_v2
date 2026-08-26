package com.sza.fastmediasorter.wear.ui.browse

import com.sza.fastmediasorter.wear.domain.model.WearFileOperationResult
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

/**
 * A file operation while it runs and after it ends.
 *
 * Kept apart from [BrowseUiState] because a reload replaces that state entirely, and the outcome of
 * a batch has to outlive the reload it triggers - strategic 11 criterion 6 requires the user to be
 * able to read which item failed, which is impossible if the answer dies with the list that
 * produced it.
 */
data class WearFileOperationRunState(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val results: List<WearFileOperationResult> = emptyList()
) {
    /** Nothing to show: no run in flight and no outcome still waiting to be read. */
    val isIdle: Boolean get() = !running && results.isEmpty()
}
