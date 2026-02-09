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
     * Get a single media file by its ID.
     * 
     * @param id The MediaStore ID of the file
     * @param mediaType The type of media
     * @return The media file or null if not found
     */
    suspend fun getMediaFileById(id: Long, mediaType: MediaType): WearMediaFile?
}
