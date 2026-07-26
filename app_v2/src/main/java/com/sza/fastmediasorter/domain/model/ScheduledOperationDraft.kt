package com.sza.fastmediasorter.domain.model

/**
 * S1009: UI -> save transport for a scheduled operation that may reference ad-hoc local folders not yet
 * persisted. A staged folder carries its tree-Uri string ([sourceFolderPath] / [targetFolderPath]) plus
 * a display name; the ViewModel resolves each to a (possibly newly created hidden) resource id before
 * upserting. A null folder field means the matching FK on [operation] is already a real resource id
 * (a visible pick, or an unchanged edit of an existing - possibly hidden - resource).
 */
data class ScheduledOperationDraft(
    val operation: ScheduledOperation,
    val sourceFolderPath: String? = null,
    val sourceFolderName: String? = null,
    val sourceFolderReadOnly: Boolean = false,
    val targetFolderPath: String? = null,
    val targetFolderName: String? = null,
)
