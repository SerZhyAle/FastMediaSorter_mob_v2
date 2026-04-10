package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sza.fastmediasorter.data.remote.ITunesApiService
import com.sza.fastmediasorter.domain.model.AudioMetadata
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject

/**
 * UseCase for searching audio cover art and metadata online.
 * Sources (in priority order):
 *   1. iTunes Search API  — fast, structured; best for English/Western content
 *   2. Deezer Search API  — free, no key; excellent for Russian/CIS content
 *   3. MusicBrainz + Cover Art Archive — free, no key; exhaustive community DB
 */
class SearchAudioCoverUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val iTunesApiService: ITunesApiService,
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient
) {

    /**
     * Search for audio cover art and metadata.
     * @param filename Audio file name (used to build the search query as fallback)
     * @param filePath Full file path; used to infer artist from parent directory when ID3 is absent
     * @param metadataArtist Artist tag read directly from the file's ID3/Vorbis metadata (highest priority)
     * @param metadataTitle  Title tag read directly from the file's ID3/Vorbis metadata (highest priority)
     * @return AudioMetadata with track info and cover art URL, or null if nothing found or search disabled
     */
    suspend operator fun invoke(
        filename: String,
        filePath: String? = null,
        metadataArtist: String? = null,
        metadataTitle: String? = null
    ): AudioMetadata? {
        try {
            val settings = settingsRepository.getSettings().first()

            if (!settings.searchAudioCoversOnline) {
                Timber.d("Online cover search disabled (query would be: '${prepareSearchQuery(filename)}')")
                return null
            }
            if (settings.searchAudioCoversOnlyOnWifi && !isWiFiConnected()) {
                Timber.d("WiFi-only mode enabled, WiFi not connected")
                return null
            }

            // Priority 0: use internal ID3/Vorbis metadata when available — much more accurate
            // than guessing from the filename (e.g. "test_audio_flac.flac" vs "I'm So Tired / The Beatles")
            val cleanMetaArtist = metadataArtist?.trim()?.takeIf { it.isNotBlank() }
                ?.let { SearchQueryUtils.filterPlaceholder(it) }
                ?.let { SearchQueryUtils.cleanForSearch(it) }
                ?.takeIf { it.isNotBlank() }
            val cleanMetaTitle = metadataTitle?.trim()?.takeIf { it.isNotBlank() }
                ?.let { SearchQueryUtils.filterPlaceholder(it) }
                ?.let { SearchQueryUtils.cleanForSearch(it) }
                ?.takeIf { it.isNotBlank() }

            if (!cleanMetaArtist.isNullOrBlank() && !cleanMetaTitle.isNullOrBlank()) {
                val metaQuery = "$cleanMetaArtist $cleanMetaTitle"
                Timber.i("Cover search (ID3 metadata): artist='$cleanMetaArtist', title='$cleanMetaTitle' → '$metaQuery'")
                searchItunes(metaQuery)?.let { return it }
                searchDeezer(metaQuery)?.let { return it }
                searchMusicBrainz(metaQuery)?.let { return it }
                Timber.d("Cover search: ID3 metadata query '$metaQuery' exhausted all sources, falling back to filename")
            } else if (!cleanMetaTitle.isNullOrBlank()) {
                Timber.i("Cover search (ID3 title only): title='$cleanMetaTitle'")
                searchItunes(cleanMetaTitle)?.let { return it }
                searchDeezer(cleanMetaTitle)?.let { return it }
                searchMusicBrainz(cleanMetaTitle)?.let { return it }
                Timber.d("Cover search: ID3 title-only query exhausted all sources, falling back to filename")
            }

            // Fallback: build query from filename
            val searchQuery = prepareSearchQuery(filename)
            if (searchQuery.isBlank()) {
                Timber.w("Empty search query after processing filename: $filename")
                return null
            }
            Timber.i("Cover search (filename fallback): '$filename' → '$searchQuery'")

            // 1. iTunes
            searchItunes(searchQuery)?.let { return it }

            // Build enriched query: prepend artist extracted from parent directory.
            // Covers Cyrillic albums that iTunes doesn't index (e.g. "2017-Борис Гребенщиков-Золотой Букет").
            val dirArtist = filePath?.let { parseArtistFromPath(it) }
                ?.let { SearchQueryUtils.cleanForSearch(it) }
                ?.takeIf { it.isNotBlank() }
            val enrichedQuery = if (!dirArtist.isNullOrBlank() &&
                    !searchQuery.contains(dirArtist, ignoreCase = true)) {
                "$dirArtist $searchQuery"
            } else searchQuery
            if (enrichedQuery != searchQuery) {
                Timber.d("Cover enriched query: '$enrichedQuery' (dirArtist='$dirArtist')")
            }

            // 2. Deezer (free, no key; good for Russian/CIS content)
            searchDeezer(enrichedQuery)?.let { return it }
            if (enrichedQuery != searchQuery) searchDeezer(searchQuery)?.let { return it }

            // 3. MusicBrainz + Cover Art Archive (free, no key; community DB)
            searchMusicBrainz(enrichedQuery)?.let { return it }

        } catch (e: Exception) {
            Timber.e(e, "Error searching cover art for: $filename")
        }
        return null
    }

    // ── iTunes ────────────────────────────────────────────────────────────────

    private suspend fun searchItunes(query: String): AudioMetadata? {
        return try {
            Timber.d("iTunes: searching for '$query'")
            val response = iTunesApiService.searchTracks(term = query)
            if (!response.isSuccessful) {
                Timber.d("iTunes: HTTP ${response.code()} for '$query'")
                return null
            }
            val searchResponse = response.body() ?: return null
            if (searchResponse.resultCount == 0) {
                Timber.d("iTunes: no results for '$query'")
                return null
            }
            val track = searchResponse.results.firstOrNull() ?: return null
            val baseUrl = track.artworkUrl100 ?: return null
            val highResUrl = baseUrl.replace("100x100", "600x600")
            val releaseYear = track.releaseDate?.take(4)
            AudioMetadata(
                trackName = track.trackName,
                artistName = track.artistName,
                albumName = track.collectionName,
                releaseYear = releaseYear,
                coverArtUrl = highResUrl
            ).also {
                Timber.i("iTunes: found '${it.artistName} - ${it.trackName}' (${it.albumName}, ${it.releaseYear})")
            }
        } catch (e: Exception) {
            Timber.w("iTunes search failed for '$query': ${e.message}")
            null
        }
    }

    // ── Deezer ────────────────────────────────────────────────────────────────

    private suspend fun searchDeezer(query: String): AudioMetadata? = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.deezer.com/search?q=$encoded&limit=1"
            Timber.d("Deezer: searching '$query'")
            val response = okHttpClient.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", "FastMediaSorter/2.0 Android")
                    .build()
            ).execute()
            if (!response.isSuccessful) {
                Timber.d("Deezer: HTTP ${response.code} for '$query'")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            val json = org.json.JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext null
            if (data.length() == 0) {
                Timber.d("Deezer: no results for '$query'")
                return@withContext null
            }
            val track = data.getJSONObject(0)
            val title = track.optString("title").takeIf { it.isNotBlank() }
            val artist = track.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() }
            val album = track.optJSONObject("album")
            val albumName = album?.optString("title")?.takeIf { it.isNotBlank() }
            val coverUrl = (album?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                ?: album?.optString("cover_big")?.takeIf { it.isNotBlank() }
                ?: album?.optString("cover_medium")?.takeIf { it.isNotBlank() })
                ?: return@withContext null
            AudioMetadata(
                trackName = title,
                artistName = artist,
                albumName = albumName,
                releaseYear = null,
                coverArtUrl = coverUrl
            ).also {
                Timber.i("Deezer: found '${it.artistName} - ${it.trackName}' cover=$coverUrl")
            }
        } catch (e: Exception) {
            Timber.w("Deezer search failed for '$query': ${e.message}")
            null
        }
    }

    // ── MusicBrainz + Cover Art Archive ──────────────────────────────────────

    private suspend fun searchMusicBrainz(query: String): AudioMetadata? = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val mbUrl = "https://musicbrainz.org/ws/2/recording/?query=$encoded&fmt=json&limit=1"
            Timber.d("MusicBrainz: searching '$query'")
            val mbResponse = okHttpClient.newCall(
                Request.Builder().url(mbUrl)
                    .header("User-Agent", "FastMediaSorter/2.0 (android)")
                    .build()
            ).execute()
            if (!mbResponse.isSuccessful) {
                Timber.d("MusicBrainz: HTTP ${mbResponse.code} for '$query'")
                return@withContext null
            }
            val mbJson = org.json.JSONObject(mbResponse.body?.string() ?: return@withContext null)
            val recordings = mbJson.optJSONArray("recordings") ?: return@withContext null
            if (recordings.length() == 0) {
                Timber.d("MusicBrainz: no results for '$query'")
                return@withContext null
            }
            val recording = recordings.getJSONObject(0)
            val title = recording.optString("title").takeIf { it.isNotBlank() }
            val artistCredit = recording.optJSONArray("artist-credit")
            val artist = artistCredit?.optJSONObject(0)
                ?.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() }
            val releases = recording.optJSONArray("releases") ?: return@withContext null
            if (releases.length() == 0) return@withContext null
            val releaseMbid = releases.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
                ?: return@withContext null

            // Cover Art Archive URL — Glide will follow the 307 redirect to the actual image.
            // If the release has no cover in CAA, Glide shows the error placeholder.
            val coverUrl = "https://coverartarchive.org/release/$releaseMbid/front-500"
            AudioMetadata(
                trackName = title,
                artistName = artist,
                albumName = null,
                releaseYear = null,
                coverArtUrl = coverUrl
            ).also {
                Timber.i("MusicBrainz: found '${it.artistName} - ${it.trackName}' mbid=$releaseMbid")
            }
        } catch (e: Exception) {
            Timber.w("MusicBrainz search failed for '$query': ${e.message}")
            null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Infers artist name from the parent directory of the file path.
     * Handles patterns: "YYYY-Artist-Album", "Artist - Album", "Artist".
     */
    private fun parseArtistFromPath(path: String): String? {
        val withoutScheme = path.substringAfter("://").ifEmpty { path }
        val dirName = withoutScheme.split('/').dropLast(1).lastOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        val withoutYear = dirName.replace(Regex("^\\d{4}[-\\s]"), "").trim()
        if (withoutYear.contains(" - ")) {
            return withoutYear.substringBefore(" - ").trim().takeIf { it.isNotBlank() }
        }
        if (withoutYear.contains("-")) {
            val candidate = withoutYear.substringBefore("-").trim()
            if (candidate.isNotBlank() && !candidate.all { it.isDigit() }) return candidate
        }
        return withoutYear.takeIf { it.any { c -> c.isLetter() } }
    }

    private fun isWiFiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun prepareSearchQuery(filename: String): String =
        SearchQueryUtils.prepareSearchQuery(filename)
}
