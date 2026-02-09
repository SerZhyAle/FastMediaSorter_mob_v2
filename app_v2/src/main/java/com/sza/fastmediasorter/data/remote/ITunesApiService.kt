package com.sza.fastmediasorter.data.remote

import androidx.annotation.Keep
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * iTunes Search API service for searching audio cover art
 * API Documentation: https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/index.html
 */
interface ITunesApiService {
    
    /**
     * Search for audio tracks by query
     * @param term Search query (typically filename or artist + track name)
     * @param entity Search entity type (default: "song")
     * @param limit Maximum number of results (default: 1)
     * @param country Country code for search (default: "US")
     */
    @GET("search")
    suspend fun searchTracks(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 1,
        @Query("country") country: String = "US"
    ): Response<ITunesSearchResponse>
}

/**
 * iTunes Search API response
 */
@Keep
data class ITunesSearchResponse(
    val resultCount: Int,
    val results: List<ITunesTrack>
)

/**
 * iTunes track information
 */
@Keep
data class ITunesTrack(
    val trackId: Long,
    val trackName: String?,
    val artistName: String?,
    val collectionName: String?,
    val releaseDate: String?, // Release date in ISO format (e.g., "2020-01-15T08:00:00Z")
    val artworkUrl30: String?,
    val artworkUrl60: String?,
    val artworkUrl100: String?,
    val artworkUrl600: String?, // High quality cover art (600x600)
    val artworkUrl1000: String? // Ultra high quality cover art (1000x1000) - if available
)
