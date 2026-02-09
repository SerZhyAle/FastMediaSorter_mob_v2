package com.sza.fastmediasorter.core.ui

/**
 * Base sealed class for common UI events across ViewModels.
 * Screen-specific ViewModels can extend this class with their own events.
 * 
 * Common patterns:
 * - Error/Message display
 * - Cloud authentication
 * - Navigation
 * 
 * Usage:
 * ```
 * sealed class BrowseEvent : UiEvent() {
 *     data class NavigateToPlayer(val filePath: String) : BrowseEvent()
 * }
 * ```
 */
sealed class UiEvent {
    /**
     * Display an error message to the user.
     * @param message Main error message
     * @param details Optional detailed error information
     * @param exception Optional exception for logging/debugging
     */
    data class ShowError(
        val message: String,
        val details: String? = null,
        val exception: Throwable? = null
    ) : UiEvent()
    
    /**
     * Display an informational message to the user.
     * @param message Message to display
     */
    data class ShowMessage(val message: String) : UiEvent()
    
    /**
     * Display detailed information (typically in a dialog).
     * @param message Main message
     * @param details Optional detailed information
     */
    data class ShowInfo(val message: String, val details: String? = null) : UiEvent()
    
    /**
     * Cloud authentication is required for the operation.
     * @param provider Cloud provider name (e.g., "Google Drive", "OneDrive")
     * @param message Reason why authentication is needed
     */
    data class CloudAuthRequired(val provider: String, val message: String) : UiEvent()
    
    /**
     * Navigate back in the navigation stack.
     */
    object NavigateBack : UiEvent()
}
