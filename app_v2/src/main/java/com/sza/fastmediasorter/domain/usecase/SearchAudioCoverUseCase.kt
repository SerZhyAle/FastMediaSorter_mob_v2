package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sza.fastmediasorter.data.remote.ITunesApiService
import com.sza.fastmediasorter.domain.model.AudioMetadata
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * UseCase for searching audio cover art and metadata online using iTunes Search API
 */
class SearchAudioCoverUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val iTunesApiService: ITunesApiService,
    private val settingsRepository: SettingsRepository
) {
    
    /**
     * Search for audio cover art and metadata by filename
     * @param filename Audio file name (with or without extension)
     * @return AudioMetadata with track info and cover art URL, or null if not found or search disabled
     */
    suspend operator fun invoke(filename: String): AudioMetadata? {
        try {
            val settings = settingsRepository.getSettings().first()
            
            // Check if online search is enabled
            if (!settings.searchAudioCoversOnline) {
                val wouldBeQuery = prepareSearchQuery(filename)
                Timber.d("Online cover search disabled in settings (query would be: '$wouldBeQuery')")
                return null
            }
            
            // Check WiFi requirement
            if (settings.searchAudioCoversOnlyOnWifi && !isWiFiConnected()) {
                Timber.d("WiFi-only mode enabled, but WiFi not connected")
                return null
            }
            
            // Prepare search query from filename
            val searchQuery = prepareSearchQuery(filename)
            Timber.i("Cover search: '$filename' → '$searchQuery'")
            if (searchQuery.isBlank()) {
                Timber.w("Empty search query after processing filename: $filename")
                return null
            }
            
            Timber.d("Searching iTunes for: $searchQuery")
            
            // Execute API request
            val response = iTunesApiService.searchTracks(term = searchQuery)
            
            if (response.isSuccessful) {
                val searchResponse = response.body()
                if (searchResponse != null && searchResponse.resultCount > 0) {
                    val track = searchResponse.results.firstOrNull()
                    // iTunes API returns artworkUrl100 by default, we need to transform it to higher resolution
                    val baseUrl = track?.artworkUrl100
                    
                    if (baseUrl != null) {
                        // Transform URL to get higher resolution (replace 100x100 with 600x600)
                        val highResUrl = baseUrl.replace("100x100", "600x600")
                        Timber.d("Found cover art, transformed to high-res: $highResUrl")
                        
                        // Extract year from releaseDate (format: "2020-01-15T08:00:00Z")
                        val releaseYear = track.releaseDate?.take(4) // First 4 chars = year
                        
                        val metadata = AudioMetadata(
                            trackName = track.trackName,
                            artistName = track.artistName,
                            albumName = track.collectionName,
                            releaseYear = releaseYear,
                            coverArtUrl = highResUrl
                        )
                        
                        Timber.i("Found metadata: ${metadata.artistName} - ${metadata.trackName} (${metadata.albumName}, ${metadata.releaseYear})")
                        return metadata
                    } else {
                        Timber.d("No cover art URL in response")
                    }
                } else {
                    Timber.d("No results found for: $searchQuery")
                }
            } else {
                Timber.w("iTunes API request failed: ${response.code()} ${response.message()}")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error searching cover art for: $filename")
        }
        
        return null
    }
    
    /**
     * Check if device is connected to WiFi
     */
    private fun isWiFiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * Prepare search query from filename
     * Removes file extension, replaces separators with spaces, removes common patterns
     */
    private fun prepareSearchQuery(filename: String): String {
        // Remove file extension
        val nameWithoutExtension = filename.substringBeforeLast('.')
        
        // Replace common separators with spaces
        val cleaned = nameWithoutExtension
            .replace(Regex("[_\\-.]"), " ")
            // Remove common audio file patterns (track numbers, brackets, etc.)
            .replace(Regex("^\\d+\\s*[-.]?\\s*"), "") // Leading track numbers like "01 - " or "01."
            .replace(Regex("\\[.*?\\]"), " ") // Content in square brackets
            .replace(Regex("\\(.*?\\)"), " ") // Content in parentheses
            // Remove extra spaces
            .replace(Regex("\\s+"), " ")
            .trim()
        
        return cleaned
    }
}
