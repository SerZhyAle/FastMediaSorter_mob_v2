package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing media files on Wear OS.
 * Uses MediaStore to access local media content.
 */
interface WearMediaRepository {
    
    /**
     * Get all media files of the specified type.
     * 
     * @param mediaType The type of media to retrieve (MUSIC, VIDEO, PHOTO)
     * @return Flow of Result containing list of media files
     */
    fun getMediaFiles(mediaType: MediaType): Flow<Result<List<WearMediaFile>>>

    /**
     * Every document the watch holds, newest first.
     *
     * S2130 ADR-4: the watch's own store was refused a Documents category on the grounds that "a
     * document there has no address to reach it by". It has one - `MediaStore.Files` addresses
     * documents on the same volume as the three typed collections - so the category exists because
     * this query does.
     *
     * @return Flow of Result containing every text, pdf, epub or office file
     */
    fun getDocumentFiles(): Flow<Result<List<WearMediaFile>>>

    /**
     * Every file the watch holds, types mixed and no folders, newest first.
     *
     * S2130 §6 (carried from S2139): "all" means a flat list of files on any origin, because the list
     * shape is a property of the category rather than of the screen showing it. The same listing is
     * what "recents" reads - S2130 §6 (carried from S2134) settled that recency is the sort plus the
     * first page, with no `N days` cutoff - so the two categories differ in label and in how far the
     * wearer scrolls, not in the query behind them.
     *
     * @return Flow of Result containing every media file and document, ordered by date descending
     */
    fun getAllMediaFiles(): Flow<Result<List<WearMediaFile>>>

    /**
     * Get a single media file by its ID.
     * 
     * @param id The MediaStore ID of the file
     * @param mediaType The type of media
     * @return The media file or null if not found
     */
    suspend fun getMediaFileById(id: Long, mediaType: MediaType): WearMediaFile?
}
