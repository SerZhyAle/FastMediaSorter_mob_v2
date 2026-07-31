package com.sza.fastmediasorter.ui.browse.helpers

import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * Operations a browse row can offer. [SELECT] is the row's participation in multi-selection;
 * the rest map to the entries of the per-row menu and the row's direct action buttons.
 */
enum class BrowseItemOperation {
    SELECT,
    COPY,
    MOVE,
    RENAME,
    DELETE,
    FAVORITE,
    OPEN_IN_PLAYER,
    SEND_TO,
    INFO,
    EXTRACT_ARCHIVE,
    REORDER,
}

/**
 * S1325: the single answer to "does this row support this operation".
 *
 * Callers must not re-derive the answer from [MediaFile.isDirectory] inline. The reported defect
 * came from exactly that: the row binder hid the checkbox and the overflow button for a directory
 * while the selection store, the menu builder and the transfer layer all accepted directories, so
 * "select all" could reach a state the user could not reach by hand.
 *
 * A directory is refused only where the operation needs a single media file:
 * [BrowseItemOperation.FAVORITE] and [BrowseItemOperation.OPEN_IN_PLAYER] target one playable item,
 * [BrowseItemOperation.SEND_TO] stages file URIs for an outbound intent, [BrowseItemOperation.INFO]
 * reads single-file metadata, and [BrowseItemOperation.EXTRACT_ARCHIVE] needs an archive file.
 * Everything else - selection, copy, move, rename, delete, manual reordering - applies to a folder
 * exactly as it applies to a file. Resource-level gates (writability, settings toggles) stay with
 * the caller; this policy answers only the item-type question.
 */
object BrowseItemOperationPolicy {

    private val DIRECTORY_OPERATIONS = setOf(
        BrowseItemOperation.SELECT,
        BrowseItemOperation.COPY,
        BrowseItemOperation.MOVE,
        BrowseItemOperation.RENAME,
        BrowseItemOperation.DELETE,
        BrowseItemOperation.REORDER,
    )

    fun supports(operation: BrowseItemOperation, file: MediaFile): Boolean =
        if (file.isDirectory) operation in DIRECTORY_OPERATIONS else true

    fun isSelectable(file: MediaFile): Boolean = supports(BrowseItemOperation.SELECT, file)
}
