package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_AUDIO
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_DOCS
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_IMAGES
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_VIDEO
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_CAMERA_PHOTOS
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_RECENT

object VirtualPathUtils {

    /** True for any virtual:// path (recent + aggregates). */
    fun isVirtualPath(path: String): Boolean = path.startsWith("virtual://")

    /** True only for aggregate virtual resources (not "recent"). */
    fun isAggregateVirtualPath(path: String): Boolean =
        path == VIRTUAL_PATH_ALL_AUDIO ||
        path == VIRTUAL_PATH_ALL_VIDEO ||
        path == VIRTUAL_PATH_ALL_IMAGES ||
        path == VIRTUAL_PATH_ALL_DOCS

    /** All five predefined virtual paths. */
    val ALL_VIRTUAL_PATHS = setOf(
        VIRTUAL_PATH_RECENT,
        VIRTUAL_PATH_ALL_AUDIO,
        VIRTUAL_PATH_ALL_VIDEO,
        VIRTUAL_PATH_ALL_IMAGES,
        VIRTUAL_PATH_ALL_DOCS,
        VIRTUAL_PATH_CAMERA_PHOTOS
    )
}
