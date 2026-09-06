package com.sza.fastmediasorter.domain.model

/**
 * S2533: action assignable to a horizontal swipe over a Browse file row.
 *
 * A subset of the row's own operation list - manual reordering and multi-select participation are
 * excluded, because neither is a one-shot action a swipe can stand for. The operation enum itself
 * lives in the UI layer, so the mapping between the two is a UI-layer concern and deliberately
 * absent here.
 *
 * [requiresWriteAccess] rides on the value rather than on each call site: the write check is one
 * link of a chain every direction walks, and a flag here is what stops each caller re-deriving it.
 */
enum class BrowseSwipeAction(val requiresWriteAccess: Boolean) {
    NONE(requiresWriteAccess = false),
    OPEN_IN_PLAYER(requiresWriteAccess = false),
    SEND_TO(requiresWriteAccess = false),
    INFO(requiresWriteAccess = false),
    FAVORITE(requiresWriteAccess = false),
    COPY(requiresWriteAccess = false),
    MOVE(requiresWriteAccess = true),
    RENAME(requiresWriteAccess = true),
    EXTRACT_ARCHIVE(requiresWriteAccess = true),
    DELETE(requiresWriteAccess = true),
    ;

    companion object {
        /**
         * Unknown or removed names degrade to [default] instead of throwing: the action list stays
         * open to change while installed settings keep whatever an earlier build wrote.
         */
        fun fromName(name: String?, default: BrowseSwipeAction): BrowseSwipeAction =
            entries.firstOrNull { it.name == name } ?: default
    }
}
