package com.sza.fastmediasorter.wear.ui.network.viewmodel

/**
 * UI state for SMB connection screen.
 */
data class SmbConnectionUiState(
    val server: String = "",
    val shareName: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val statusMessage: String = "",
    val isError: Boolean = false
)
