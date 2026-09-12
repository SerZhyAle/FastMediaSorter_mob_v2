package com.sza.fastmediasorter.ui.browse.helpers

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BrowseSwipeAction
import com.sza.fastmediasorter.domain.model.BrowseSwipeDirection
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.allowsWriteOperations

/**
 * S2533: decides whether a horizontal swipe may start on a row, and which action it would run.
 *
 * A refusal returns null and the row does not move at all, so the user sees the refusal instead of a
 * gesture that appears to miss. The chain answers cheapest question first; every link must say yes.
 *
 * Takes no `RecyclerView`, no `View` and no framework type beyond the models - every criterion the
 * strategic spec numbers 3 to 7 and 10 is decided here, and none of them is checked by a compile.
 */
class BrowseSwipeActionResolver {

    @Suppress("ReturnCount") // A refusal chain: each link returns null at its own question.
    fun resolve(
        file: MediaFile,
        direction: BrowseSwipeDirection,
        settings: AppSettings,
        resource: MediaResource?,
        hasDestinations: Boolean,
        isHorizontalAxisFree: Boolean,
        isSelectionActive: Boolean,
    ): BrowseSwipeAction? {
        // The layout question is "does drag already own this axis", not "how many columns are
        // there": drag takes all four directions under any GridLayoutManager, a one-column grid
        // included. Band-select owns the horizontal axis while a selection runs.
        if (!isHorizontalAxisFree) return null
        if (isSelectionActive) return null

        val action = direction.actionOf(settings)
        if (action == BrowseSwipeAction.NONE) return null
        if (!BrowseItemOperationPolicy.supports(operationFor(action), file)) return null
        if (action.requiresWriteAccess && resource?.allowsWriteOperations() != true) return null
        if (!isEnabledBySettings(action, file, settings, hasDestinations)) return null
        return action
    }

    /**
     * The per-action conditions the overflow menu applies, so the swipe offers exactly what the menu
     * offers and cannot re-enable an operation the user switched off.
     */
    private fun isEnabledBySettings(
        action: BrowseSwipeAction,
        file: MediaFile,
        settings: AppSettings,
        hasDestinations: Boolean,
    ): Boolean = when (action) {
        BrowseSwipeAction.COPY -> hasDestinations && settings.enableCopying
        BrowseSwipeAction.MOVE -> settings.enableMoving
        BrowseSwipeAction.RENAME -> settings.allowRename
        BrowseSwipeAction.DELETE -> settings.allowDelete
        BrowseSwipeAction.OPEN_IN_PLAYER -> !file.type.isBinaryFile()
        BrowseSwipeAction.EXTRACT_ARCHIVE -> BrowseItemOperationPolicy.isZipArchive(file)
        BrowseSwipeAction.SEND_TO,
        BrowseSwipeAction.INFO,
        BrowseSwipeAction.FAVORITE,
        BrowseSwipeAction.NONE,
        -> true
    }

    /**
     * The action-to-operation mapping Phase 01 deliberately kept out of the domain layer: the
     * applicability policy speaks in [BrowseItemOperation], which lives here in the UI layer.
     */
    private fun operationFor(action: BrowseSwipeAction): BrowseItemOperation = when (action) {
        BrowseSwipeAction.OPEN_IN_PLAYER -> BrowseItemOperation.OPEN_IN_PLAYER
        BrowseSwipeAction.SEND_TO -> BrowseItemOperation.SEND_TO
        BrowseSwipeAction.INFO -> BrowseItemOperation.INFO
        BrowseSwipeAction.FAVORITE -> BrowseItemOperation.FAVORITE
        BrowseSwipeAction.COPY -> BrowseItemOperation.COPY
        BrowseSwipeAction.MOVE -> BrowseItemOperation.MOVE
        BrowseSwipeAction.RENAME -> BrowseItemOperation.RENAME
        BrowseSwipeAction.EXTRACT_ARCHIVE -> BrowseItemOperation.EXTRACT_ARCHIVE
        BrowseSwipeAction.DELETE -> BrowseItemOperation.DELETE
        // NONE is refused before this is reached; SELECT is the nearest harmless operation.
        BrowseSwipeAction.NONE -> BrowseItemOperation.SELECT
    }
}
