package com.sza.fastmediasorter.wear.domain.model

/**
 * S1836: the network source the watch opened last - [id] addresses it, [name] captions its home cell.
 */
data class LastUsedResource(
    val id: String,
    val name: String
) {
    companion object {
        /**
         * S1974: the width of the widest grid the home screen can draw, so a longer history would
         * store entries no view mode has a cell for.
         */
        const val HISTORY_LIMIT: Int = 3
    }
}
