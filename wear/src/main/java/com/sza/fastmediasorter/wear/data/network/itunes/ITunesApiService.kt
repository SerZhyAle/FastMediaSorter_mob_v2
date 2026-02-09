package com.sza.fastmediasorter.wear.data.network.itunes

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service interface for iTunes Search API.
 * Used to fetch album artwork based on artist and album name.
 */
interface ITunesApiService {
    
    /**
     * Search for album artwork.
     * 
     * @param searchTerm Artist and album name (e.g., "Pink Floyd The Wall")
     * @param entity Type of entity to search (default: "album")
     * @param limit Maximum number of results (default: 1)
     * @return Search response with artwork URLs
     */
    @GET("search")
    suspend fun searchAlbumArt(
        @Query("term") searchTerm: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 1
    ): ITunesSearchResponse
}
