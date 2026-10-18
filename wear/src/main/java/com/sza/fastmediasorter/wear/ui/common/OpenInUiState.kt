package com.sza.fastmediasorter.wear.ui.common

import com.sza.fastmediasorter.wear.domain.model.WearOpenInTarget

/** S4381: what the "Open in.." dialog draws while its list is being built, once built, and when it is empty. */
sealed interface OpenInUiState {
    data object Loading : OpenInUiState
    data object None : OpenInUiState
    data class Choose(val targets: List<WearOpenInTarget>) : OpenInUiState
}
