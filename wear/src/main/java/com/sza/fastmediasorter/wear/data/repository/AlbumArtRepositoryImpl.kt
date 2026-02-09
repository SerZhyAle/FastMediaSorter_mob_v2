package com.sza.fastmediasorter.wear.data.repository

import com.sza.fastmediasorter.wear.data.network.itunes.ITunesApiService
import com.sza.fastmediasorter.wear.domain.repository.AlbumArtRepository
import timber.log.Timber

/**
 * Implementation of AlbumArtRepository using iTunes Search API.
 * 
 * Note: This class is provided via WearAppModule.provideAlbumArtRepository
 * Do not add @Inject constructor as it would create duplicate bindings.
 */
class AlbumArtRepositoryImpl(
    private val iTunesApiService: ITunesApiService
) : AlbumArtRepository {
    
    override suspend fun getAlbumArtUrl(artist: String, album: String): Result<String?> {
        return try {
            val searchTerm = "$artist $album"
            Timber.d("Searching album art for: $searchTerm")
            
            val response = iTunesApiService.searchAlbumArt(searchTerm)
            
            val artworkUrl = response.results.firstOrNull()?.getHighResArtworkUrl()
            
            if (artworkUrl != null) {
                Timber.d("Found album art: $artworkUrl")
                Result.success(artworkUrl)
            } else {
                Timber.d("No album art found for: $searchTerm")
                Result.success(null)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching album art")
            Result.failure(e)
        }
    }
}
