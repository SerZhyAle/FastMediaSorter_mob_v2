package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.media.MediaMetadataRetriever
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.jsoup.Jsoup

/** UseCase for searching song lyrics using file metadata; tries multiple providers with fallback. */
class SearchLyricsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val fileCache: UnifiedFileCache
) {
    private val metadataCache = mutableMapOf<String, Triple<String?, String?, String?>>()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Search for lyrics using ID3 metadata + filename; resolved iTunes metadata takes priority. */
    suspend fun execute(
        mediaFile: MediaFile,
        resolvedTitle: String? = null,
        resolvedArtist: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Searching lyrics for: ${mediaFile.name} (resolved: artist='$resolvedArtist', title='$resolvedTitle')")
            val (artist, title, album) = extractMetadataWithCache(mediaFile)
            Timber.d("Extracted metadata - Artist: $artist, Title: $title, Album: $album")
            val filteredResolvedTitle = SearchQueryUtils.filterPlaceholder(resolvedTitle)
            val filteredResolvedArtist = SearchQueryUtils.filterPlaceholder(resolvedArtist)
            // Extract artist from parent dir - last-resort fallback for "YYYY-Artist-Album/track.mp3" structures
            val dirArtist = parseArtistFromPath(mediaFile.path)
            if (!dirArtist.isNullOrBlank()) {
                Timber.d("AudioMetadataLoader: dirArtist extracted from path: '$dirArtist'")
            }
            val searchQueries = buildSearchQueries(artist, title, mediaFile.name, filteredResolvedTitle, filteredResolvedArtist, dirArtist)
            // Detect script; check filename too (covers Cyrillic tracks where ID3/iTunes both fail)
            val isCyrillic = hasCyrillic(artist) || hasCyrillic(title) ||
                hasCyrillic(filteredResolvedArtist) || hasCyrillic(filteredResolvedTitle) ||
                hasCyrillic(mediaFile.name) || hasCyrillic(dirArtist)
            Timber.d("Script detection: isCyrillic=$isCyrillic (artist='$artist', title='$title', dirArtist='$dirArtist')")

            // Source order depends on script:
            //   Cyrillic: Musixmatch first (international), then Genius; AZLyrics skipped (ASCII-only)
            //   Latin:    AZLyrics first (fastest), then Musixmatch, then Genius
            // Dead sources removed: Lyrics.ovh (502), Megalyrics (became WordPress blog, no lyrics)
            val sources: List<Pair<String, suspend (String) -> String?>> = if (isCyrillic) {
                listOf(
                    "Musixmatch" to { query -> searchMusixmatch(query) },
                    "Genius" to { query -> searchGeniusApi(query) }
                )
            } else {
                listOf(
                    "AZLyrics" to { query -> searchAZLyrics(query) },
                    "Musixmatch" to { query -> searchMusixmatch(query) },
                    "Genius" to { query -> searchGeniusApi(query) }
                )
            }
            
            var lastError: Throwable? = null
            
            for ((sourceName, source) in sources) {
                Timber.d("Trying source: $sourceName")
                queryLoop@ for (query in searchQueries) {
                    Timber.d("Trying source '$sourceName' with query: $query")
                    try {
                        val lyrics = source(query)
                        if (lyrics != null) {
                            Timber.d("Lyrics found for query: $query in $sourceName")
                            return@withContext Result.success(lyrics)
                        }
                    } catch (e: Exception) {
                        Timber.w("Search failed for query '$query' in $sourceName: ${e.message}")
                        lastError = e
                        
                        // If this is a network/connection error, the source is likely down/unreachable.
                        // Skip remaining queries for this source to save time.
                        if (e is java.net.SocketTimeoutException || 
                            e is java.net.ConnectException || 
                            e is java.net.UnknownHostException) {
                            Timber.w("$sourceName seems unreachable (${e.javaClass.simpleName}), skipping remaining queries for this source.")
                            break@queryLoop
                        }
                    }
                }
            }
            
            Timber.d("No lyrics found for: ${mediaFile.name}")
            if (lastError != null) {
                Result.failure(lastError)
            } else {
                Result.failure(Exception("Lyrics not found"))
            }
        } catch (e: Exception) {
            if (e is java.net.SocketTimeoutException) {
                Timber.w("Lyrics search network timeout for ${mediaFile.name}: ${e.message}")
            } else {
                Timber.e(e, "Error searching lyrics for: ${mediaFile.name}")
            }
            Result.failure(e)
        }
    }

    /** Extract artist, title and album from audio file metadata with caching. */
    private suspend fun extractMetadataWithCache(mediaFile: MediaFile): Triple<String?, String?, String?> {
        metadataCache[mediaFile.path]?.let { cached ->
            Timber.d("Using cached metadata for: ${mediaFile.name}")
            return cached
        }
        val metadata = extractMetadata(mediaFile)
        metadataCache[mediaFile.path] = metadata
        return metadata
    }
    
    /** Extract artist, title and album from audio file metadata. */
    private suspend fun extractMetadata(mediaFile: MediaFile): Triple<String?, String?, String?> {
        return try {
            val localFile = getLocalFile(mediaFile)
            
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(localFile.absolutePath)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                Triple(fixEncoding(artist), fixEncoding(title), fixEncoding(album))
            } finally {
                retriever.release()
            }
        } catch (e: IllegalArgumentException) {
            // Expected for deleted/inaccessible files
            Timber.d("SearchLyricsUseCase: file not accessible for metadata extraction: ${e.message}")
            Triple(null, null, null)
        } catch (e: Exception) {
            // Recoverable: MediaMetadataRetriever throws RuntimeException (EINVAL) for corrupt or
            // non-audio files, getLocalFile may raise IO errors for network sources. The search
            // then falls back to filename parsing, so this is expected - log without a stack trace.
            Timber.i("SearchLyricsUseCase: metadata extraction skipped (${e.javaClass.simpleName}: ${e.message})")
            Triple(null, null, null)
        }
    }
    
    /** Fix encoding issues with Cyrillic ID3 tags (Windows-1251 misread as ISO-8859-1). */
    private fun fixEncoding(text: String?): String? {
        if (text == null || text.isBlank()) return text
        return try {
            if (text.contains(Regex("[\u0080-\u00FF]{2,}"))) {
                // CP1251 misread as ISO-8859-1 \u2014 re-encode bytes through correct charset
                val bytes = text.toByteArray(Charsets.ISO_8859_1)
                String(bytes, java.nio.charset.Charset.forName("windows-1251"))
            } else {
                text
            }
        } catch (e: Exception) {
            Timber.w("Failed to fix encoding for: $text")
            text
        }
    }
    
    /** Get local file, downloading from network if required. */
    private suspend fun getLocalFile(mediaFile: MediaFile): File {
        return when {
            mediaFile.path.startsWith("smb://") -> {
                downloadFromSmb(mediaFile)
            }
            mediaFile.path.startsWith("sftp://") -> {
                downloadFromSftp(mediaFile)
            }
            mediaFile.path.startsWith("ftp://") -> {
                downloadFromFtp(mediaFile)
            }
            else -> File(mediaFile.path)
        }
    }
    
    private suspend fun downloadFromSmb(mediaFile: MediaFile): File {
        val cacheFile = fileCache.getCacheFile(mediaFile.path, mediaFile.size)
        val uri = android.net.Uri.parse(mediaFile.path)
        val server = uri.host ?: throw IllegalArgumentException("Invalid SMB path: ${mediaFile.path}")
        val port = if (uri.port > 0) uri.port else 445
        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) throw IllegalArgumentException("Invalid SMB path: ${mediaFile.path}")
        
        val shareName = pathSegments[0]
        val remotePath = "/" + pathSegments.drop(1).joinToString("/")
        
        // Get credentials: share-specific first, then host-level fallback (mirrors SmbOperationStrategy)
        val credentials = credentialsRepository.getByServerAndShare(server, shareName)
            ?: credentialsRepository.getCredentialsByHost(server)
                ?.takeIf { it.type.equals("SMB", ignoreCase = true) }
            ?: throw IllegalStateException("No credentials found for SMB: $server/$shareName")
        
        val connectionInfo = com.sza.fastmediasorter.data.network.model.SmbConnectionInfo(
            server = server,
            port = port,
            shareName = shareName,
            username = credentials.username,
            password = credentials.password,
            domain = credentials.domain
        )
        
        cacheFile.outputStream().use { outputStream ->
            val result = smbClient.downloadFile(
                connectionInfo = connectionInfo,
                remotePath = remotePath,
                localOutputStream = outputStream,
                fileSize = mediaFile.size
            )
            
            when (result) {
                is com.sza.fastmediasorter.data.network.model.SmbResult.Success -> cacheFile
                is com.sza.fastmediasorter.data.network.model.SmbResult.Error -> 
                    throw java.io.IOException("SMB download failed: ${result.exception?.message ?: "Unknown error"}")
            }
        }
        
        return cacheFile
    }
    
    private suspend fun downloadFromSftp(mediaFile: MediaFile): File {
        val cacheFile = fileCache.getCacheFile(mediaFile.path, mediaFile.size)
        val uri = android.net.Uri.parse(mediaFile.path)
        val server = uri.host ?: throw IllegalArgumentException("Invalid SFTP path: ${mediaFile.path}")
        val port = if (uri.port > 0) uri.port else 22
        val remotePath = uri.path ?: throw IllegalArgumentException("Invalid SFTP path: ${mediaFile.path}")
        val credentials = credentialsRepository.getByTypeServerAndPort("SFTP", server, port)
            ?: throw IllegalStateException("No credentials found for SFTP: $server:$port")
        
        val connectionInfo = com.sza.fastmediasorter.data.remote.sftp.SftpClient.SftpConnectionInfo(
            host = server,
            port = port,
            username = credentials.username,
            password = credentials.password.orEmpty()
        )
        
        cacheFile.outputStream().use { outputStream ->
            val result = sftpClient.downloadFile(
                connectionInfo = connectionInfo,
                remotePath = remotePath,
                outputStream = outputStream,
                fileSize = mediaFile.size
            )
            
            if (!result.isSuccess) {
                throw java.io.IOException("SFTP download failed: ${result.exceptionOrNull()?.message}")
            }
        }
        
        return cacheFile
    }
    
    private suspend fun downloadFromFtp(mediaFile: MediaFile): File {
        val cacheFile = fileCache.getCacheFile(mediaFile.path, mediaFile.size)
        val uri = android.net.Uri.parse(mediaFile.path)
        val server = uri.host ?: throw IllegalArgumentException("Invalid FTP path: ${mediaFile.path}")
        val port = if (uri.port > 0) uri.port else 21
        val remotePath = uri.path ?: throw IllegalArgumentException("Invalid FTP path: ${mediaFile.path}")
        val credentials = credentialsRepository.getByTypeServerAndPort("FTP", server, port)
            ?: throw IllegalStateException("No credentials found for FTP: $server:$port")
        val connectResult = ftpClient.connect(
            host = server,
            port = port,
            username = credentials.username,
            password = credentials.password.orEmpty()
        )
        
        if (connectResult.isFailure) {
            throw java.io.IOException("FTP connection failed: ${connectResult.exceptionOrNull()?.message}")
        }
        
        cacheFile.outputStream().use { outputStream ->
            val result = ftpClient.downloadFile(
                remotePath = remotePath,
                outputStream = outputStream,
                fileSize = mediaFile.size
            )
            
            if (!result.isSuccess) {
                ftpClient.disconnect()
                throw java.io.IOException("FTP download failed: ${result.exceptionOrNull()?.message}")
            }
        }
        
        ftpClient.disconnect()
        return cacheFile
    }

    /** Build search queries from resolved metadata, ID3 tags, filename parsing, and fuzzy variants. */
    private fun buildSearchQueries(
        artist: String?,
        title: String?,
        filename: String,
        resolvedTitle: String? = null,
        resolvedArtist: String? = null,
        dirArtist: String? = null
    ): List<String> {
        val queries = mutableListOf<String>()
        
        // Priority 0: Pre-resolved metadata from cover search (most accurate)
        // Clean brackets and special chars from resolved metadata before building queries.
        val cleanResolvedArtist = resolvedArtist?.let { SearchQueryUtils.cleanForSearch(it) }.orEmpty()
        val cleanResolvedTitle  = resolvedTitle?.let  { SearchQueryUtils.cleanForSearch(it) }.orEmpty()
        if (cleanResolvedArtist.isNotBlank() && cleanResolvedTitle.isNotBlank()) {
            queries.add("$cleanResolvedArtist $cleanResolvedTitle lyrics")
            queries.add("$cleanResolvedArtist - $cleanResolvedTitle")   // for AZLyrics/OVH multi-word artist parsing
            queries.add("$cleanResolvedArtist $cleanResolvedTitle")
            queries.add("$cleanResolvedTitle lyrics")
        } else if (cleanResolvedTitle.isNotBlank()) {
            queries.add("$cleanResolvedTitle lyrics")
            queries.add(cleanResolvedTitle)
        }
        
        val (filenameArtist, filenameTitle) = parseFilename(filename)
        
        // Determine best artist: prefer ID3 → filename parse → parent directory
        val bestArtist = normalizeText(artist ?: filenameArtist.ifBlank { dirArtist })
        val bestTitle = normalizeText(title ?: filenameTitle)
        
        // Priority 1: Artist + Title (from ID3 metadata or filename)
        if (bestArtist.isNotBlank() && bestTitle.isNotBlank()) {
            queries.add("$bestArtist $bestTitle lyrics")
            queries.add("$bestArtist - $bestTitle")   // for AZLyrics/OVH multi-word artist parsing
            queries.add("$bestArtist $bestTitle")
        }
        
        // Priority 2: Title only
        if (bestTitle.isNotBlank()) {
            queries.add("$bestTitle lyrics")
            queries.add(bestTitle)
        }
        
        // Priority 3: Artist only (for cases where title parsing failed)
        if (bestArtist.isNotBlank() && bestTitle.isBlank()) {
            queries.add("$bestArtist lyrics")
        }
        
        // Priority 4: Cleaned filename via shared SearchQueryUtils
        val cleanedFilename = SearchQueryUtils.prepareSearchQuery(filename)
        if (cleanedFilename.isNotBlank() && cleanedFilename != bestTitle && cleanedFilename != "$bestArtist $bestTitle") {
            queries.add("$cleanedFilename lyrics")
            queries.add(cleanedFilename)
        }
        
        // Priority 5: Fuzzy variants - try swapping if metadata seems wrong
        if (!artist.isNullOrBlank() && !title.isNullOrBlank() && 
            filenameArtist.isNotBlank() && filenameTitle.isNotBlank() &&
            normalizeText(artist) != normalizeText(filenameArtist)) {
            queries.add("${normalizeText(filenameArtist)} ${normalizeText(filenameTitle)} lyrics")
        }
        
        return queries.distinct()
    }
    
    /** Parse filename to extract artist and title ("Artist - Song", "001 - Artist - Song", etc.). */
    private fun parseFilename(filename: String): Pair<String, String> {
        var name = filename.substringBeforeLast('.')
        
        // Remove track numbers at start (e.g., "001 - ", "01. ", "1 - ")
        name = name.replace(Regex("^\\d+\\s*[-.]\\s*"), "")
        if (name.contains(" - ")) {
            val parts = name.split(" - ", limit = 2)
            if (parts.size == 2) return Pair(parts[0].trim(), parts[1].trim())
        }
        if (name.contains("-") && !name.contains(" ")) {
            val parts = name.split("-", limit = 2)
            if (parts.size == 2) return Pair(parts[0].trim(), parts[1].trim())
        }
        return Pair("", name.trim())
    }

    /**
     * Extracts artist from the parent directory ("YYYY-Artist-Album" → "Artist",
     * "Artist - Album" → "Artist"). Returns null when no meaningful artist can be determined.
     */
    private fun parseArtistFromPath(path: String): String? {
        val withoutScheme = path.substringAfter("://").ifEmpty { path }
        val parts = withoutScheme.split('/')
        val dirName = parts.dropLast(1).lastOrNull()?.takeIf { it.isNotBlank() } ?: return null
        // Strip leading year (e.g. "2017-" or "2017 ")
        val withoutYear = dirName.replace(Regex("^\\d{4}[-\\s]"), "").trim()
        if (withoutYear.contains(" - ")) {
            return withoutYear.substringBefore(" - ").trim().takeIf { it.isNotBlank() && !it.isGenericFolder() }
        }
        if (withoutYear.contains("-")) {
            val candidate = withoutYear.substringBefore("-").trim()
            // Reject single-word all-digit segments (track numbers etc.)
            if (candidate.isNotBlank() && !candidate.all { it.isDigit() }) {
                return candidate.takeIf { !it.isGenericFolder() }
            }
        }
        return withoutYear.trim().takeIf { it.any { c -> c.isLetter() } && !it.isGenericFolder() }
    }

    /**
     * Generic library/storage folder names that are never an artist. Using one of these as a
     * fallback artist (e.g. file in a folder literally named "document") poisons every search
     * query and yields false-positive matches.
     */
    private fun String.isGenericFolder(): Boolean = lowercase().trim() in GENERIC_FOLDER_NAMES

    /** Returns true if the text contains Cyrillic characters. */
    private fun hasCyrillic(text: String?): Boolean =
        text?.any { it in '\u0400'..'\u04FF' } == true

    /** Normalize text for search: remove brackets, stop-words, special chars, and extra spaces. */
    private fun normalizeText(text: String?): String {
        if (text == null || text.isBlank()) return ""
        var normalized: String = text
        // Remove content in parentheses/brackets (often contains "feat.", "remix", etc.)
        normalized = normalized.replace(Regex("\\([^)]*\\)"), " ")
        normalized = normalized.replace(Regex("\\[[^]]*\\]"), " ")
        normalized = normalized.replace(Regex("\\{[^}]*\\}"), " ")
        val wordsToRemove = listOf(
            "official", "video", "audio", "hd", "hq", "lyrics",
            "feat", "ft", "featuring", "remix", "cover", "live",
            "version", "remaster", "remastered", "edit", "extended"
        )
        wordsToRemove.forEach { word ->
            normalized = normalized.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), " ")
        }
        normalized = normalized.replace(Regex("[_]+"), " ")
        normalized = normalized.replace(Regex("-+"), " ")
        normalized = normalized.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        normalized = normalized.replace(Regex("\\s+"), " ").trim()
        
        return normalized
    }
    
    /** Search Musixmatch via direct /lyrics/Artist-Slug/Title-Slug URL (CloudFlare blocks /search/). */
    private suspend fun searchMusixmatch(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.replace(" lyrics", "", ignoreCase = true)
            val (artistRaw, titleRaw) = if (cleanQuery.contains(" - ")) {
                val p = cleanQuery.split(" - ", limit = 2)
                Pair(p[0].trim(), p[1].trim())
            } else {
                val p = cleanQuery.split(" ", limit = 2)
                if (p.size < 2) return@withContext null
                Pair(p[0].trim(), p[1].trim())
            }

            // Build hyphenated slug: "The Beatles" -> "The-Beatles", "Dig a Pony" -> "Dig-a-Pony"
            val artistSlug = artistRaw.trim().replace(Regex("\\s+"), "-")
            val titleSlug = titleRaw.trim().replace(Regex("\\s+"), "-")

            if (artistSlug.isBlank() || titleSlug.isBlank()) return@withContext null

            val url = "https://www.musixmatch.com/lyrics/$artistSlug/$titleSlug"
            Timber.d("Musixmatch: fetching direct URL $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                Timber.d("Musixmatch: response ${response.code} for $url")
                if (!response.isSuccessful) return@withContext null

                val html = response.body?.string() ?: return@withContext null

                // Musixmatch embeds lyrics in JSON payload: "body":"lyrics text with \n"
                val bodyRegex = """"body"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
                val bodyMatch = bodyRegex.find(html)
                if (bodyMatch == null) {
                    Timber.d("Musixmatch: no JSON body field found in page")
                    return@withContext null
                }

                val lyrics = bodyMatch.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\r", "")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .trim()

                Timber.d("Musixmatch extracted lyrics length: ${lyrics.length}, first 200 chars: ${lyrics.take(200)}")
                lyrics.takeIf { it.length > 50 }
            }
        } catch (e: Exception) {
            Timber.w("Musixmatch failed: ${e.message}")
            null
        }
    }
    
    // NOTE: searchLyricsOvhApi removed - api.lyrics.ovh returns 502 permanently (server dead since 2024).
    
    /** Search Genius API and scrape lyrics from the returned song page. */
    private suspend fun searchGeniusApi(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val searchQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://genius.com/api/search/multi?q=$searchQuery"
            
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                Timber.d("Genius API: response ${response.code} for query '$query'")
                if (!response.isSuccessful) return@withContext null
                
                val responseBody = response.body?.string() ?: return@withContext null

                // Pick a real song hit, not the first link: multi-search also returns artist pages
                // (/artists/..) and annotated articles, which previously matched and produced
                // garbage "lyrics". Genius song pages always end in "-lyrics".
                val urlRegex = """"url"\s*:\s*"(https://genius\.com/[^"]+)"""".toRegex()
                val songUrl = urlRegex.findAll(responseBody)
                    .map { it.groupValues[1] }
                    .firstOrNull { it.endsWith("-lyrics") }
                if (songUrl == null) {
                    Timber.d("Genius API: no song (-lyrics) URL found in response for '$query'")
                    return@withContext null
                }

                // Guard against an unrelated top hit: the song slug must share a token with the query.
                if (!isRelevantGeniusMatch(query, songUrl)) {
                    Timber.d("Genius API: '$songUrl' not relevant to query '$query', skipping")
                    return@withContext null
                }

                Timber.d("Genius API: found song URL: $songUrl")
                // Fetch lyrics from song page
                fetchGeniusLyrics(songUrl)
            }
        } catch (e: Exception) {
            Timber.w("Genius API failed: ${e.message}")
            null
        }
    }
    
    private suspend fun fetchGeniusLyrics(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                Timber.d("Genius lyrics page: response ${response.code} for $url")
                if (!response.isSuccessful) return@withContext null
                
                val html = response.body?.string() ?: return@withContext null
                val doc = Jsoup.parse(html)
                val containers = doc.select("[data-lyrics-container=true]")
                
                if (containers.isEmpty()) {
                    Timber.d("Genius: No lyrics containers found")
                    return@withContext null
                }
                
                val sb = StringBuilder()
                containers.forEach { container ->
                    // Preserve line breaks: <br> -> \n
                    // We append a specialized token which won't be eaten by text() normalization
                    container.select("br").append("\\n")
                    container.select("p").prepend("\\n\\n")
                    
                    if (sb.isNotEmpty()) sb.append("\n\n")
                    sb.append(container.text().replace("\\n", "\n"))
                }
                val rawLyrics = sb.toString().trim()
                val lyrics = rawLyrics
                    .replace(Regex("^\\d+\\s+Contributors.*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("^Translations.*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("^.*Lyrics\\s*$", RegexOption.MULTILINE), "") // Remove title lines like "Song Name Lyrics"
                    .replace(Regex("^[\\s\\S]*?Lyrics\\s*\\n", RegexOption.IGNORE_CASE), "") // Aggressive cleanup: remove everything up to "Lyrics" line if header persists
                    .trim()
                
                Timber.d("Genius extracted lyrics length: ${lyrics.length}, first 200 chars: ${lyrics.take(200)}")
                lyrics.takeIf { it.length > 50 }
            }
        } catch (e: Exception) {
            Timber.w("Genius lyrics fetch failed: ${e.message}")
            null
        }
    }
    
    /** Search AZLyrics via direct URL construction (ASCII-only; skips non-ASCII queries). */
    private suspend fun searchAZLyrics(query: String): String? = withContext(Dispatchers.IO) {
        try {
            // AZLyrics requires artist-title format
            val cleanQuery = query.replace(" lyrics", "", ignoreCase = true)
            // Prefer "Artist - Title" split to handle multi-word artists (e.g. "The Beatles")
            val (artistRaw, titleRaw) = if (cleanQuery.contains(" - ")) {
                val p = cleanQuery.split(" - ", limit = 2)
                Pair(p[0].trim(), p[1].trim())
            } else {
                val p = cleanQuery.split(" ", limit = 2)
                if (p.size < 2) return@withContext null
                Pair(p[0].trim(), p[1].trim())
            }

            // Strip all bracket types from both artist and title before building AZLyrics slug.
            // "Breathe (In the Air)" → "breathe", "Song [Live]" → "song"
            val artistCleaned = SearchQueryUtils.cleanForSearch(artistRaw)
            // AZLyrics strips leading articles: "The Beatles" → "beatles"
            val artistNoArticle = artistCleaned.replace(Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE), "")
            val artist = (artistNoArticle.ifBlank { artistCleaned }).lowercase().replace(Regex("[^a-z0-9]"), "")
            val titleCleaned = SearchQueryUtils.cleanForSearch(titleRaw)
            val title = (titleCleaned.ifBlank { titleRaw }).lowercase().replace(Regex("[^a-z0-9]"), "")

            // AZLyrics only indexes ASCII content - skip if artist or title is non-ASCII
            if (artist.isEmpty() || title.isEmpty()) {
                Timber.d("AZLyrics: skipping non-ASCII query '$artistRaw / $titleRaw'")
                return@withContext null
            }

            val url = "https://www.azlyrics.com/lyrics/$artist/$title.html"
            Timber.d("AZLyrics: fetching $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                Timber.d("AZLyrics: response ${response.code} for $url")
                if (!response.isSuccessful) return@withContext null
                
                val html = response.body?.string() ?: return@withContext null
                
                // Lyrics are in an attribute-less div after the usage comment:
                // <!-- Usage of azlyrics.com content... --><br><div>...lyrics...</div>
                val lyricsRegex = """<!-- Usage of azlyrics\.com content[^>]*-->.*?<div>(.*?)</div>""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val match = lyricsRegex.find(html)
                val rawLyrics = match?.groupValues?.get(1)
                
                val lyrics = rawLyrics
                    ?.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
                    ?.replace("""\<[^\>]+\>""".toRegex(), "\n") // Remove HTML tags
                    ?.replace("&quot;", "\"")
                    ?.replace("&amp;", "&")
                    ?.replace("&lt;", "<")
                    ?.replace("&gt;", ">")
                    ?.replace("&apos;", "'")
                    ?.replace("&#39;", "'")
                    ?.lines() // Split into lines
                    ?.filter { it.isNotBlank() } // Remove empty lines
                    ?.joinToString("\n") // Join back
                    ?.trim()
                
                Timber.d("AZLyrics extracted lyrics length: ${lyrics?.length}, first 200 chars: ${lyrics?.take(200)}")
                lyrics?.takeIf { it.length > 50 }
            }
        } catch (e: Exception) {
            Timber.w("AZLyrics failed: ${e.message}")
            null
        }
    }

    /**
     * True when the Genius song slug shares a meaningful token with the query, i.e. the top hit is
     * plausibly the requested song. Latin-only check: queries with no ASCII token (e.g. Cyrillic)
     * cannot be matched against the transliterated slug, so they pass through unvalidated.
     */
    private fun isRelevantGeniusMatch(query: String, songUrl: String): Boolean {
        val slug = songUrl.substringAfterLast('/').lowercase()
        val tokens = query.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 4 && it !in GENIUS_STOP_TOKENS }
        if (tokens.isEmpty()) return true
        return tokens.any { slug.contains(it) }
    }

    // NOTE: searchMegalyrics removed - megalyrics.ru migrated to WordPress blog, no longer serves lyrics (March 2026).

    private companion object {
        /** Generic library/storage folders that must never be treated as an artist. */
        val GENERIC_FOLDER_NAMES = setOf(
            "document", "documents", "download", "downloads", "music", "audio",
            "media", "sound", "sounds", "song", "songs", "track", "tracks", "mp3",
            "dcim", "movies", "movie", "video", "videos", "podcasts", "ringtones",
            "telegram", "whatsapp", "viber", "bluetooth", "recordings", "voice",
            "files", "file", "new", "temp", "tmp", "misc", "various", "unsorted"
        )

        /** Query words that carry no artist/title signal for the Genius relevance check. */
        val GENIUS_STOP_TOKENS = setOf("lyrics", "document", "official", "audio", "video", "track")
    }
}
