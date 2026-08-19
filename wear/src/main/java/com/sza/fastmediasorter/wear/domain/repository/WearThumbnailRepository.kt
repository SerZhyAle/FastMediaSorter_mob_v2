package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail

/**
 * The single door to a file's picture on the watch.
 *
 * A screen never opens a data source itself: the two file lists draw the same cell but sit on top
 * of different origins, so which one is being shown is this repository's business and not theirs.
 */
interface WearThumbnailRepository {

    /**
     * @param sourceId id of the registered network resource the file came from, or null when the
     *   file lives on the watch itself. The origin decides how the picture is obtained.
     */
    suspend fun thumbnailFor(file: WearMediaFile, sourceId: String?): WearThumbnail
}
