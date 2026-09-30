package com.sza.fastmediasorter.domain.model

/**
 * S4016: one pinned stream channel as a screen sees it - the row id every pin edit is addressed by,
 * and the title it is shown under. The companion window needs nothing more of the catalog row.
 */
data class PinnedStreamChannel(
    val id: String,
    val title: String,
)
