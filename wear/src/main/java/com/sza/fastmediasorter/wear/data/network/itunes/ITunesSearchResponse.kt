package com.sza.fastmediasorter.wear.data.network.itunes

import com.google.gson.annotations.SerializedName

/**
 * Response from iTunes Search API.
 */
data class ITunesSearchResponse(
    @SerializedName("resultCount")
    val resultCount: Int,
    
    @SerializedName("results")
    val results: List<ITunesTrack>
)

/**
 * Individual track/album result from iTunes API.
 */
data class ITunesTrack(
    @SerializedName("artistName")
    val artistName: String?,
    
    @SerializedName("collectionName")
    val collectionName: String?,
    
    @SerializedName("artworkUrl100")
    val artworkUrl100: String?,
    
    @SerializedName("artworkUrl60")
    val artworkUrl60: String?
) {
    /**
     * Get high-resolution artwork URL by replacing 100x100 with 600x600.
     */
    fun getHighResArtworkUrl(): String? {
        return artworkUrl100?.replace("100x100", "600x600")
    }
}
