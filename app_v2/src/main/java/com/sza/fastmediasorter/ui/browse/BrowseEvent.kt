package com.sza.fastmediasorter.ui.browse

import android.app.PendingIntent
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.MediaFile

sealed class BrowseEvent {
    data class ShowError(val message: String, val details: String? = null, val exception: Throwable? = null) : BrowseEvent()
    data class ShowMessage(val message: String) : BrowseEvent()
    data class ShowUndoToast(val operationType: String) : BrowseEvent()
    data class NavigateToPlayer(val filePath: String, val fileIndex: Int) : BrowseEvent()
    /** S0783: a favorited live channel was tapped in the favorites list - open it in the stream player. */
    data class OpenStreamPlayer(val url: String) : BrowseEvent()
    data class ShowCloudAuthenticationRequired(val provider: CloudProvider) : BrowseEvent()
    data class CloudAuthRequired(val provider: String, val message: String) : BrowseEvent()
    data class PermissionRequired(val pendingIntent: PendingIntent) : BrowseEvent()
    data class NoFilesFound(val message: String? = null, val messageResId: Int? = null) : BrowseEvent()
    /**
     * Fired after scanning files for delete-by-size so the UI can show a confirmation dialog.
     * [matchedFiles] holds the candidate list; the UI passes it back via [executeBySizeDeleteConfirmed].
     */
    data class ShowDeleteBySizePreview(
        val count: Int,
        val totalBytes: Long,
        val matchedFiles: List<MediaFile>
    ) : BrowseEvent()
    /** Archive operation progress - emitted during ZIP creation. */
    data class ArchiveProgress(val current: Int, val total: Int, val fileName: String) : BrowseEvent()
    /** Archive completed successfully. */
    data class ArchiveSuccess(val archivePath: String, val archivedCount: Int) : BrowseEvent()
    /** Archive failed or had a fatal error. */
    data class ArchiveError(val message: String, val exception: Throwable? = null) : BrowseEvent()
    data class ShowExtractConfirmDialog(val file: MediaFile, val targetDirName: String) : BrowseEvent()
    data class ShowArchivePasswordDialog(val file: MediaFile, val targetDirName: String) : BrowseEvent()
    data class ExtractionProgress(
        val entryName: String,
        val done: Int,
        val total: Int,
        val percent: Int
    ) : BrowseEvent()
    data class ExtractionSuccess(val targetPath: String, val extractedCount: Int) : BrowseEvent()
    data class ExtractionFailed(val message: String) : BrowseEvent()
    /** Fired after the current resource was successfully added as a Quick Sort destination. */
    data class ResourceAddedAsDestination(val resourceId: Long) : BrowseEvent()
    /** Scroll the Browse list to the file with this name (emitted after camera capture save). */
    data class ScrollToFile(val fileName: String) : BrowseEvent()
    /** S0189: open [filePath] in PlayerActivity with edit mode pre-activated. */
    data class NavigateToTextEditor(val filePath: String, val resourceId: Long) : BrowseEvent()
    /** S0191: open [filePath] in PlayerActivity and enter draw mode immediately. */
    data class NavigateToDrawingEditor(val filePath: String, val resourceId: Long) : BrowseEvent()
    /** Scan failed because ACCESS_LOCAL_NETWORK permission is not granted (API 37+). */
    data object ShowLocalNetworkPermissionRequired : BrowseEvent()
}
