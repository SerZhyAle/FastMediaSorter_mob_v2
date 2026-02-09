package com.sza.fastmediasorter.wear.domain.repository

/**
 * Repository interface for fetching album artwork.
 */
interface AlbumArtRepository {
    
    /**
     * Get album artwork URL for given artist and album.
     * 
     * @param artist Artist name
     * @param album Album name
     * @return Result with artwork URL or null if not found
     */
    suspend fun getAlbumArtUrl(artist: String, album: String): Result<String?>
}
