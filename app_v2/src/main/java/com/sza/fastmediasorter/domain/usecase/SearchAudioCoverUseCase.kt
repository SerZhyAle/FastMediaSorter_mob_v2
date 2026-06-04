package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sza.fastmediasorter.data.remote.ITunesApiService
import com.sza.fastmediasorter.domain.model.AudioMetadata
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
 *   1. iTunes Search API  - fast, structured; best for English/Western content
 *   2. Deezer Search API  - free, no key; excellent for Russian/CIS content
 *   3. MusicBrainz + Cover Art Archive - free, no key; exhaustive community DB
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

            // m4a files are voice/mic recordings - searching cover art makes no sense
            if (filename.endsWith(".m4a", ignoreCase = true)) {
                Timber.d("Cover search skipped: m4a is a microphone recording ($filename)")
                return null
            }

            // Priority 0: use internal ID3/Vorbis metadata when available - much more accurate
            // than guessing from the filename (e.g. "test_audio_flac.flac" vs "I'm So Tired / The Beatles")
            val cleanMetaArtist = metadataArtist?.trim()?.takeIf { it.isNotBlank() }
                ?.let { SearchQueryUtils.filterPlaceholder(it) }
                ?.let { SearchQueryUtils.cleanForSearch(it) }
                ?.takeIf { it.isNotBlank() }
            val cleanMetaTitle = metadataTitle?.trim()?.takeIf { it.isNotBlank() }
                ?.let { SearchQueryUtils.filterPlaceholder(it) }
                ?.let { SearchQueryUtils.cleanForSearch(it) }
                ?.takeIf { it.isNotBlank() }

            // ID3 tags are authoritative when present: use them as the verified query so the
            // provider result is still confidence-checked against the known artist/title.
            if (!cleanMetaArtist.isNullOrBlank() && !cleanMetaTitle.isNullOrBlank()) {
                val metaQuery = "$cleanMetaArtist $cleanMetaTitle"
                Timber.i("Cover search (ID3 metadata): artist='$cleanMetaArtist', title='$cleanMetaTitle' → '$metaQuery'")
                searchAcrossProviders(metaQuery, cleanMetaArtist, cleanMetaTitle, null)?.let { return it }
                Timber.d("Cover search: ID3 metadata query '$metaQuery' found no confident match, falling back to filename")
            } else if (!cleanMetaTitle.isNullOrBlank()) {
                Timber.i("Cover search (ID3 title only): title='$cleanMetaTitle'")
                searchAcrossProviders(cleanMetaTitle, null, cleanMetaTitle, null)?.let { return it }
                Timber.d("Cover search: ID3 title-only query found no confident match, falling back to filename")
            }

            // Fallback: decompose the filename into an explicit artist + title (artist-priority).
            val parsed = SearchQueryUtils.parseArtistTitle(filename)
            val fileArtist = parsed.artist
                ?.let { SearchQueryUtils.cleanForSearch(it) }
                ?.takeIf { it.isNotBlank() }
            val fileTitle = SearchQueryUtils.cleanForSearch(parsed.title)
                .takeIf { it.isNotBlank() }
                ?: prepareSearchQuery(filename)
            // Artist inferred from the album folder - extra artist signal for both query and scoring.
            val dirArtist = filePath?.let { parseArtistFromPath(it) }
                ?.let { SearchQueryUtils.cleanForSearch(it) }
                ?.takeIf { it.isNotBlank() }

            val baseQuery = listOfNotNull(fileArtist, fileTitle).joinToString(" ")
                .ifBlank { prepareSearchQuery(filename) }
            if (baseQuery.isBlank()) {
                Timber.w("Empty search query after processing filename: $filename")
                return null
            }
            val enrichedQuery = if (!dirArtist.isNullOrBlank() && !baseQuery.contains(dirArtist, ignoreCase = true)) {
                "$dirArtist $baseQuery"
            } else baseQuery
            Timber.i("Cover search (filename): '$filename' → artist='$fileArtist', title='$fileTitle', query='$enrichedQuery'")

            searchAcrossProviders(enrichedQuery, fileArtist, fileTitle, dirArtist)?.let { return it }
            if (enrichedQuery != baseQuery) {
                searchAcrossProviders(baseQuery, fileArtist, fileTitle, dirArtist)?.let { return it }
            }

        } catch (e: CancellationException) {
            Timber.d("Cover search cancelled for: $filename")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error searching cover art for: $filename")
        }
        return null
    }

    // ── Candidate selection ─────────────────────────────────────────────────────

    /**
     * Try each provider in turn; for each, fetch several candidates and keep the one whose artist
     * (primary) and title best match the request. Returns the first provider's best candidate that
     * clears the confidence threshold, or null when none does - so a likely-wrong cover is suppressed
     * (empty state) instead of shown. One candidate set per provider call; no per-track request loop.
     */
    private suspend fun searchAcrossProviders(
        query: String,
        queryArtist: String?,
        queryTitle: String?,
        dirArtist: String?,
    ): AudioMetadata? {
        pickConfident(queryArtist, queryTitle, dirArtist, searchItunes(query))?.let { return it }
        pickConfident(queryArtist, queryTitle, dirArtist, searchDeezer(query))?.let { return it }
        pickConfident(queryArtist, queryTitle, dirArtist, searchMusicBrainz(query))?.let { return it }
        return null
    }

    /** Pick the highest-confidence candidate, accepting it only if it clears the threshold. */
    private fun pickConfident(
        queryArtist: String?,
        queryTitle: String?,
        dirArtist: String?,
        candidates: List<AudioMetadata>,
    ): AudioMetadata? {
        if (candidates.isEmpty()) return null
        Timber.d("S0347: cover candidates scored by confidence (artist-priority)")
        val best = candidates
            .map { it to SearchQueryUtils.matchConfidence(queryArtist, queryTitle, it.artistName, it.trackName, dirArtist) }
            .maxByOrNull { it.second } ?: return null
        val (metadata, confidence) = best
        return if (confidence >= SearchQueryUtils.COVER_MATCH_CONFIDENCE_THRESHOLD) {
            Timber.i("Cover match accepted: '${metadata.artistName} - ${metadata.trackName}' confidence=${formatConfidence(confidence)}")
            metadata
        } else {
            Timber.i("Cover match rejected: best '${metadata.artistName} - ${metadata.trackName}' confidence=${formatConfidence(confidence)} < threshold")
            null
        }
    }

    private fun formatConfidence(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

    // ── iTunes ────────────────────────────────────────────────────────────────

    private suspend fun searchItunes(query: String): List<AudioMetadata> {
        return try {
            Timber.d("iTunes: searching for '$query'")
            val response = iTunesApiService.searchTracks(term = query, limit = SearchQueryUtils.COVER_CANDIDATE_LIMIT)
            if (!response.isSuccessful) {
                Timber.d("iTunes: HTTP ${response.code()} for '$query'")
                return emptyList()
            }
            val searchResponse = response.body() ?: return emptyList()
            searchResponse.results.mapNotNull { track ->
                val baseUrl = track.artworkUrl100 ?: return@mapNotNull null
                AudioMetadata(
                    trackName = track.trackName,
                    artistName = track.artistName,
                    albumName = track.collectionName,
                    releaseYear = track.releaseDate?.take(4),
                    coverArtUrl = baseUrl.replace("100x100", "600x600"),
                )
            }
        } catch (e: CancellationException) {
            Timber.d("iTunes search cancelled for '$query'")
            throw e
        } catch (e: Exception) {
            Timber.w("iTunes search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    // ── Deezer ────────────────────────────────────────────────────────────────

    private suspend fun searchDeezer(query: String): List<AudioMetadata> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.deezer.com/search?q=$encoded&limit=${SearchQueryUtils.COVER_CANDIDATE_LIMIT}"
            Timber.d("Deezer: searching '$query'")
            val response = okHttpClient.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", "FastMediaSorter/2.0 Android")
                    .build()
            ).execute()
            if (!response.isSuccessful) {
                Timber.d("Deezer: HTTP ${response.code} for '$query'")
                return@withContext emptyList()
            }
            val body = response.body?.string() ?: return@withContext emptyList()
            val data = org.json.JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            (0 until data.length()).mapNotNull { i ->
                val track = data.getJSONObject(i)
                val title = track.optString("title").takeIf { it.isNotBlank() }
                val artist = track.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() }
                val album = track.optJSONObject("album")
                val albumName = album?.optString("title")?.takeIf { it.isNotBlank() }
                val coverUrl = (album?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                    ?: album?.optString("cover_big")?.takeIf { it.isNotBlank() }
                    ?: album?.optString("cover_medium")?.takeIf { it.isNotBlank() })
                    ?: return@mapNotNull null
                AudioMetadata(
                    trackName = title,
                    artistName = artist,
                    albumName = albumName,
                    releaseYear = null,
                    coverArtUrl = coverUrl,
                )
            }
        } catch (e: CancellationException) {
            Timber.d("Deezer search cancelled for '$query'")
            throw e
        } catch (e: Exception) {
            Timber.w("Deezer search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    // ── MusicBrainz + Cover Art Archive ──────────────────────────────────────

    private suspend fun searchMusicBrainz(query: String): List<AudioMetadata> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val mbUrl = "https://musicbrainz.org/ws/2/recording/?query=$encoded&fmt=json&limit=${SearchQueryUtils.COVER_CANDIDATE_LIMIT}"
            Timber.d("MusicBrainz: searching '$query'")
            val mbResponse = okHttpClient.newCall(
                Request.Builder().url(mbUrl)
                    .header("User-Agent", "FastMediaSorter/2.0 (android)")
                    .build()
            ).execute()
            if (!mbResponse.isSuccessful) {
                Timber.d("MusicBrainz: HTTP ${mbResponse.code} for '$query'")
                return@withContext emptyList()
            }
            val mbJson = org.json.JSONObject(mbResponse.body?.string() ?: return@withContext emptyList())
            val recordings = mbJson.optJSONArray("recordings") ?: return@withContext emptyList()
            (0 until recordings.length()).mapNotNull { i ->
                val recording = recordings.getJSONObject(i)
                val title = recording.optString("title").takeIf { it.isNotBlank() }
                val artist = recording.optJSONArray("artist-credit")?.optJSONObject(0)
                    ?.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() }
                val releases = recording.optJSONArray("releases") ?: return@mapNotNull null
                if (releases.length() == 0) return@mapNotNull null
                val releaseMbid = releases.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                // Cover Art Archive URL - Glide follows the 307 redirect to the actual image.
                AudioMetadata(
                    trackName = title,
                    artistName = artist,
                    albumName = null,
                    releaseYear = null,
                    coverArtUrl = "https://coverartarchive.org/release/$releaseMbid/front-500",
                )
            }
        } catch (e: CancellationException) {
            Timber.d("MusicBrainz search cancelled for '$query'")
            throw e
        } catch (e: Exception) {
            Timber.w("MusicBrainz search failed for '$query': ${e.message}")
            emptyList()
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
