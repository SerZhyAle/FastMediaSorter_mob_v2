package com.sza.fastmediasorter.wear.domain.model

/**
 * One window into a folder-walk level.
 *
 * S2201 ADR-5 bounds the walk with a window inside a single level rather than a paging protocol, so
 * the caller is told explicitly whether more remains. Inferring exhaustion from a short list would be
 * wrong at the boundary, where a level whose size is an exact multiple of the window returns a full
 * page and nothing after it.
 */
data class WearFolderPage(
    val entries: List<WearFolderEntry>,
    val nextOffset: Int?
)
