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

/**
 * UseCase for searching song lyrics online using file name and/or audio metadata.
 * Searches multiple lyrics providers and returns the first successful result.
 */
class SearchLyricsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val fileCache: UnifiedFileCache
) {
    
    // Metadata cache: path -> (artist, title)
    private val metadataCache = mutableMapOf<String, Pair<String?, String?>>()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Search for lyrics using audio file metadata and filename.
     * @param mediaFile The audio file to search lyrics for
     * @param resolvedTitle Pre-resolved track title (e.g. from iTunes cover search). Takes priority over file metadata.
     * @param resolvedArtist Pre-resolved artist name (e.g. from iTunes cover search). Takes priority over file metadata.
     * @return Result containing lyrics text or error
     */
    suspend fun execute(
        mediaFile: MediaFile,
        resolvedTitle: String? = null,
        resolvedArtist: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Searching lyrics for: ${mediaFile.name} (resolved: artist='$resolvedArtist', title='$resolvedTitle')")
            
            // Extract metadata (with caching)
            val (artist, title) = extractMetadataWithCache(mediaFile)
            Timber.d("Extracted metadata - Artist: $artist, Title: $title")
            
            // Filter placeholder values from resolved metadata
            val filteredResolvedTitle = SearchQueryUtils.filterPlaceholder(resolvedTitle)
            val filteredResolvedArtist = SearchQueryUtils.filterPlaceholder(resolvedArtist)
            
            // Build search queries (resolved metadata gets priority)
            val searchQueries = buildSearchQueries(artist, title, mediaFile.name, filteredResolvedTitle, filteredResolvedArtist)
            
            // Try each source with all queries before moving to next source
            // Sources ordered by speed and reliability: AZLyrics is first as it's the fastest for anonymous requests
            val sources = listOf<Pair<String, suspend (String) -> String?>>(
                "AZLyrics" to { query -> searchAZLyrics(query) },
                "Musixmatch" to { query -> searchMusixmatch(query) },
                "Genius" to { query -> searchGeniusApi(query) },
                "Lyrics.ovh" to { query -> searchLyricsOvhApi(query) }
            )
            
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
    
    // ... extractMetadata ... getLocalFile ... downloadFromSmb ... (unchanged)

    /**
     * Extract artist and title from audio file metadata with caching.
     */
    private suspend fun extractMetadataWithCache(mediaFile: MediaFile): Pair<String?, String?> {
        // Check cache first
        metadataCache[mediaFile.path]?.let { cached ->
            Timber.d("Using cached metadata for: ${mediaFile.name}")
            return cached
        }
        
        // Extract and cache
        val metadata = extractMetadata(mediaFile)
        metadataCache[mediaFile.path] = metadata
        return metadata
    }
    
    /**
     * Extract artist and title from audio file metadata.
     */
    private suspend fun extractMetadata(mediaFile: MediaFile): Pair<String?, String?> {
        return try {
            val localFile = getLocalFile(mediaFile)
            
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(localFile.absolutePath)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                Pair(fixEncoding(artist), fixEncoding(title))
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract metadata")
            Pair(null, null)
        }
    }
    
    /**
     * Fix encoding issues with Cyrillic text from ID3 tags.
     * Some MP3 files have tags encoded in Windows-1251 or ISO-8859-1 instead of UTF-8.
     */
    private fun fixEncoding(text: String?): String? {
        if (text == null || text.isBlank()) return text
        
        return try {
            // Check if text contains garbled characters (common sign of encoding issues)
            if (text.contains(Regex("[\u0080-\u00FF]{2,}"))) {
                // Try to fix: interpret as ISO-8859-1 bytes and re-encode as Windows-1251
                // This handles the common case where CP1251 was misinterpreted as ISO-8859-1
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
    
    /**
     * Get local file (download if network resource).
     */
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
        
        // Parse smb://server:port/share/path
        val uri = android.net.Uri.parse(mediaFile.path)
        val server = uri.host ?: throw IllegalArgumentException("Invalid SMB path: ${mediaFile.path}")
        val port = if (uri.port > 0) uri.port else 445
        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) throw IllegalArgumentException("Invalid SMB path: ${mediaFile.path}")
        
        val shareName = pathSegments[0]
        val remotePath = "/" + pathSegments.drop(1).joinToString("/")
        
        // Get credentials
        val credentials = credentialsRepository.getByServerAndShare(server, shareName)
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
        
        // Parse sftp://server:port/path
        val uri = android.net.Uri.parse(mediaFile.path)
        val server = uri.host ?: throw IllegalArgumentException("Invalid SFTP path: ${mediaFile.path}")
        val port = if (uri.port > 0) uri.port else 22
        val remotePath = uri.path ?: throw IllegalArgumentException("Invalid SFTP path: ${mediaFile.path}")
        
        // Get credentials
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
        
        // Parse ftp://server:port/path
        val uri = android.net.Uri.parse(mediaFile.path)
        val server = uri.host ?: throw IllegalArgumentException("Invalid FTP path: ${mediaFile.path}")
        val port = if (uri.port > 0) uri.port else 21
        val remotePath = uri.path ?: throw IllegalArgumentException("Invalid FTP path: ${mediaFile.path}")
        
        // Get credentials
        val credentials = credentialsRepository.getByTypeServerAndPort("FTP", server, port)
            ?: throw IllegalStateException("No credentials found for FTP: $server:$port")
        
        // Connect to FTP
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

    /**
     * Build multiple search queries using different combinations of metadata.
     * Priority order:
     * 1. Resolved metadata from online cover search (iTunes) — most reliable
     * 2. ID3/file metadata (artist + title tags)
     * 3. Filename parsing with [SearchQueryUtils.prepareSearchQuery]
     * 4. Fuzzy variants (swapping metadata/filename sources)
     */
    private fun buildSearchQueries(
        artist: String?,
        title: String?,
        filename: String,
        resolvedTitle: String? = null,
        resolvedArtist: String? = null
    ): List<String> {
        val queries = mutableListOf<String>()
        
        // Priority 0: Pre-resolved metadata from cover search (most accurate)
        if (!resolvedArtist.isNullOrBlank() && !resolvedTitle.isNullOrBlank()) {
            queries.add("$resolvedArtist $resolvedTitle lyrics")
            queries.add("$resolvedArtist $resolvedTitle")
            queries.add("$resolvedTitle lyrics")
        } else if (!resolvedTitle.isNullOrBlank()) {
            queries.add("$resolvedTitle lyrics")
            queries.add(resolvedTitle)
        }
        
        // Parse filename to extract potential artist and title
        val (filenameArtist, filenameTitle) = parseFilename(filename)
        
        // Determine best artist and title (prefer ID3 metadata, fallback to filename)
        val bestArtist = normalizeText(artist ?: filenameArtist)
        val bestTitle = normalizeText(title ?: filenameTitle)
        
        // Priority 1: Artist + Title (from ID3 metadata or filename)
        if (bestArtist.isNotBlank() && bestTitle.isNotBlank()) {
            queries.add("$bestArtist $bestTitle lyrics")
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
    
    /**
     * Parse filename to extract artist and title.
     * Handles patterns like: "Artist - Song", "001 - Artist - Song", "Song (Artist)", etc.
     */
    private fun parseFilename(filename: String): Pair<String, String> {
        // Remove extension
        var name = filename.substringBeforeLast('.')
        
        // Remove track numbers at start (e.g., "001 - ", "01. ", "1 - ")
        name = name.replace(Regex("^\\d+\\s*[-.]\\s*"), "")
        
        // Try pattern: "Artist - Song"
        if (name.contains(" - ")) {
            val parts = name.split(" - ", limit = 2)
            if (parts.size == 2) {
                return Pair(parts[0].trim(), parts[1].trim())
            }
        }
        
        // Try pattern: "Artist-Song" (no spaces)
        if (name.contains("-") && !name.contains(" ")) {
            val parts = name.split("-", limit = 2)
            if (parts.size == 2) {
                return Pair(parts[0].trim(), parts[1].trim())
            }
        }
        
        // No clear pattern found, treat entire name as title
        return Pair("", name.trim())
    }
    
    /**
     * Normalize text for search: remove special characters, common words, extra spaces.
     */
    private fun normalizeText(text: String?): String {
        if (text == null || text.isBlank()) return ""
        
        var normalized: String = text
        
        // Remove content in parentheses/brackets (often contains "feat.", "remix", etc.)
        normalized = normalized.replace(Regex("\\([^)]*\\)"), " ")
        normalized = normalized.replace(Regex("\\[[^]]*\\]"), " ")
        normalized = normalized.replace(Regex("\\{[^}]*\\}"), " ")
        
        // Remove common words that don't help search
        val wordsToRemove = listOf(
            "official", "video", "audio", "hd", "hq", "lyrics",
            "feat", "ft", "featuring", "remix", "cover", "live",
            "version", "remaster", "remastered", "edit", "extended"
        )
        wordsToRemove.forEach { word ->
            normalized = normalized.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), " ")
        }
        
        // Replace underscores and multiple dashes with spaces
        normalized = normalized.replace(Regex("[_]+"), " ")
        normalized = normalized.replace(Regex("-+"), " ")
        
        // Remove special characters (keep letters, numbers, spaces)
        normalized = normalized.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        
        // Remove extra whitespace
        normalized = normalized.replace(Regex("\\s+"), " ").trim()
        
        return normalized
    }
    
    /**
     * Search lyrics online using multiple providers with fallback.
     */
    private suspend fun searchLyricsOnline(query: String): String? {
        // Try multiple sources in order
        val sources = listOf<suspend () -> String?>(
            { searchLyricsOvhApi(query) },
            { searchGeniusApi(query) },
            { searchAZLyrics(query) }
        )
        
        for (source in sources) {
            try {
                val lyrics = source()
                if (lyrics != null) {
                    return lyrics
                }
            } catch (e: Exception) {
                Timber.w("Source failed: ${e.message}")
                // Continue to next source
            }
        }
        
        return null
    }
    
    
    /**
     * Search Musixmatch (web scraping - one of the best lyrics databases).
     */
    private suspend fun searchMusixmatch(query: String): String? = withContext(Dispatchers.IO) {
        try {
            // Search Musixmatch
            val searchQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.musixmatch.com/search/$searchQuery"
            
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val html = response.body?.string() ?: return@withContext null
                
                // Extract first song URL from search results
                val urlRegex = """href="(/lyrics/[^"]+)"""".toRegex()
                val songPath = urlRegex.find(html)?.groupValues?.get(1)
                    ?: return@withContext null
                
                // Fetch lyrics from song page
                fetchMusixmatchLyrics("https://www.musixmatch.com$songPath")
            }
        } catch (e: Exception) {
            Timber.w("Musixmatch search failed: ${e.message}")
            null
        }
    }
    
    private suspend fun fetchMusixmatchLyrics(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val html = response.body?.string() ?: return@withContext null
                
                // Musixmatch has lyrics in <span> tags with class "lyrics__content__ok"
                val lyricsRegex = """<span[^>]*class="[^"]*lyrics__content__ok[^"]*"[^>]*>(.*?)</span>""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val matches = lyricsRegex.findAll(html)
                
                val lyrics = matches.joinToString("\n\n") { match ->
                    match.groupValues[1]
                        .replace("""<[^>]+>""".toRegex(), "\n")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&#x27;", "'")
                        .trim()
                }
                
                Timber.d("Musixmatch extracted lyrics length: ${lyrics.length}, first 200 chars: ${lyrics.take(200)}")
                lyrics.takeIf { it.length > 50 }
            }
        } catch (e: Exception) {
            Timber.w("Musixmatch lyrics fetch failed: ${e.message}")
            null
        }
    }
    
    /**
     * Search Lyrics.ovh API (free, simple).
     */
    private suspend fun searchLyricsOvhApi(query: String): String? = withContext(Dispatchers.IO) {
        // Extract artist and title from query
        val parts = query.replace(" lyrics", "", ignoreCase = true).split(" ", limit = 2)
        if (parts.size < 2) return@withContext null
        
        val artist = URLEncoder.encode(parts[0].trim(), "UTF-8")
        val title = URLEncoder.encode(parts[1].trim(), "UTF-8")
        
        val apiUrl = "https://api.lyrics.ovh/v1/$artist/$title"
        
        val request = Request.Builder()
            .url(apiUrl)
            .get()
            .build()
        
        // Use 'use' to ensure response is closed
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            
            val responseBody = response.body?.string() ?: return@withContext null
            
            // Parse JSON response (simple manual parsing)
            val lyricsRegex = """"lyrics"\s*:\s*"([^"]+)"""".toRegex()
            val lyrics = lyricsRegex.find(responseBody)?.groupValues?.get(1)
                ?.replace("\\n", "\n")
                ?.replace("\\r", "")
                ?.replace("\\t", "\t")
                ?.trim()
            
            Timber.d("Lyrics.ovh extracted lyrics length: ${lyrics?.length}, first 200 chars: ${lyrics?.take(200)}")
            lyrics?.takeIf { it.length > 50 }
        }
    }
    
    /**
     * Search Genius API (requires parsing HTML from search results).
     */
    private suspend fun searchGeniusApi(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val searchQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://genius.com/api/search/multi?q=$searchQuery"
            
            val request = Request.Builder()
                .url(searchUrl)
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val responseBody = response.body?.string() ?: return@withContext null
                
                // Extract first song URL from JSON
                val urlRegex = """"url"\s*:\s*"(https://genius\.com/[^"]+)"""".toRegex()
                val songUrl = urlRegex.find(responseBody)?.groupValues?.get(1)
                    ?: return@withContext null
                
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
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val html = response.body?.string() ?: return@withContext null
                
                // Parse with Jsoup
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
                    // text() normalizes whitespace but keeps our \\n tokens
                    sb.append(container.text().replace("\\n", "\n"))
                }
                
                val rawLyrics = sb.toString().trim()
                
                // Cleanup Genius metadata headers
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
    
    /**
     * Search AZLyrics (web scraping).
     */
    private suspend fun searchAZLyrics(query: String): String? = withContext(Dispatchers.IO) {
        try {
            // AZLyrics requires artist-title format
            val parts = query.replace(" lyrics", "", ignoreCase = true).split(" ", limit = 2)
            if (parts.size < 2) return@withContext null
            
            val artist = parts[0].trim().lowercase().replace(Regex("[^a-z0-9]"), "")
            val title = parts[1].trim().lowercase().replace(Regex("[^a-z0-9]"), "")
            
            val url = "https://www.azlyrics.com/lyrics/$artist/$title.html"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val html = response.body?.string() ?: return@withContext null
                
                // AZLyrics has lyrics in a div without class/id after "Sorry about that" comment
                // The lyrics div is the one WITHOUT any attributes (no class, no id)
                // We need to skip script tags and find the actual lyrics div
                // AZLyrics has lyrics in a div without class/id after "Sorry about that" comment
                // The structure is: <!-- Usage of azlyrics.com content... -->\n<br>\n<div>\n...lyrics...\n</div>
                val lyricsRegex = """<!-- Usage of azlyrics\.com content[^>]*-->.*?<div>(.*?)</div>""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val match = lyricsRegex.find(html)
                val rawLyrics = match?.groupValues?.get(1)
                
                val lyrics = rawLyrics
                    ?.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "") // Explictly remove scripts
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
}
