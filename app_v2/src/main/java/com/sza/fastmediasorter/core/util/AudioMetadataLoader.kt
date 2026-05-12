package com.sza.fastmediasorter.core.util

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import com.sza.fastmediasorter.data.local.db.FileMetadataCacheDao
import com.sza.fastmediasorter.data.local.db.FileMetadataCacheEntity
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.utils.FtpPathUtils
import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SmbPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Viewport-based audio metadata loader for network files (SMB/SFTP/FTP).
 *
 * Reads the first 64KB of audio files over the network, parses metadata
 * (artist, album, title, duration) via Media3 [MetadataRetriever] through a
 * temp-file bridge (no native SIGSEGV risk), and caches results in both an
 * in-memory map and
 * the Room DB ([FileMetadataCacheDao]).
 *
 * Safety: If [KILL_SWITCH_THRESHOLD] consecutive extraction failures occur,
 * the feature auto-disables for the session to avoid wasting network/resources.
 *
 * Designed to be triggered from the scroll-idle listener in BrowseActivity,
 * loading metadata only for visible items — analogous to the existing
 * thumbnail viewport-loading pattern.
 */
@Singleton
@UnstableApi
class AudioMetadataLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fileMetadataCacheDao: FileMetadataCacheDao,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val audioMetadataCacheRepository: AudioMetadataCacheRepository
) {

    companion object {
        private const val MAX_PARTIAL_READ_BYTES = 65536 // 64 KB — enough for ID3v2 + Vorbis headers
        private const val FAILED_CACHE_MAX_SIZE = 5000
        /** After this many consecutive failures, disable the feature for the session. */
        private const val KILL_SWITCH_THRESHOLD = 15
        private const val MAX_COVER_BYTES = 4 * 1024 * 1024 // 4 MB cover art size limit
    }

    /** Background scope for network + parsing work. Cancelled items won't crash. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Limits concurrent network fetches to avoid connection pool exhaustion. */
    private val semaphore = Semaphore(3)

    /** In-memory cache: path → parsed metadata. Survives across scroll events. */
    private val memoryCache = ConcurrentHashMap<String, AudioMetadata>()

    /** Paths currently being loaded — prevents duplicate parallel requests. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** Consecutive extraction failure counter for kill-switch. Reset on any success. */
    private val consecutiveFailures = AtomicInteger(0)

    /** When true, all new loadIfNeeded calls are silently skipped. */
    @Volatile
    private var disabled = false

    /** Paths that failed extraction — FIFO eviction at [FAILED_CACHE_MAX_SIZE]. */
    private val failedCache: MutableMap<String, Boolean> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(128, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean {
                return size > FAILED_CACHE_MAX_SIZE
            }
        }
    )

    /**
     * Returns cached metadata for [path] if available in memory, or null.
     * Used by the adapter during partial bind to apply enriched data.
     */
    fun getCachedMetadata(path: String): AudioMetadata? = memoryCache[path]

    /**
     * Initiates metadata loading for a network audio file if not already cached/failed.
     *
     * @param file      The MediaFile to enrich (must be AUDIO + network path).
     * @param onLoaded  Callback invoked on the **main thread** with enriched [MediaFile].
     */
    fun loadIfNeeded(file: MediaFile, onLoaded: (MediaFile) -> Unit) {
        if (disabled) return
        if (file.type != MediaType.AUDIO || file.isDirectory) return
        if (PathUtils.isLocalPath(file.path) || file.path.startsWith("content://")) return
        if (file.artist != null && file.title != null) return // already enriched
        if (failedCache.containsKey(file.path)) return
        if (!inFlight.add(file.path)) return // already loading

        // Fast path: in-memory cache hit
        val cached = memoryCache[file.path]
        if (cached != null) {
            inFlight.remove(file.path)
            val enriched = applyMetadata(file, cached)
            onLoaded(enriched)
            return
        }

        val resourceId = file.resourceId
        scope.launch {
            try {
                // 1. DB cache check
                if (resourceId != null) {
                    val dbEntry = fileMetadataCacheDao.getEntry(resourceId, file.path)
                    if (dbEntry != null && dbEntry.hasAudioMetadata()) {
                        val metadata = AudioMetadata(
                            artist = dbEntry.artist,
                            album = dbEntry.album,
                            title = dbEntry.title,
                            duration = dbEntry.durationMs
                        )
                        memoryCache[file.path] = metadata
                        inFlight.remove(file.path)
                        val enriched = applyMetadata(file, metadata)
                        withContext(Dispatchers.Main) { onLoaded(enriched) }
                        return@launch
                    }
                }

                // Re-check kill-switch before network I/O
                if (disabled) {
                    inFlight.remove(file.path)
                    return@launch
                }

                // 2. Network fetch + parse with concurrency limit
                semaphore.withPermit {
                    val bytes = readPartialBytes(file.path)
                    if (bytes == null || bytes.isEmpty()) {
                        Timber.d("AudioMetadataLoader: Empty bytes for ${file.name}")
                        recordFailure()
                        failedCache[file.path] = true
                        inFlight.remove(file.path)
                        return@withPermit
                    }

                    val metadata = extractMetadataFromBytes(bytes, file.path)
                    if (metadata == null || !metadata.hasAnyData()) {
                        // No embedded tags is a per-file outcome, not a system failure — skip kill-switch counter
                        Timber.d("AudioMetadataLoader: No metadata extracted for ${file.name}")
                        failedCache[file.path] = true
                        inFlight.remove(file.path)
                        return@withPermit
                    }

                    // Success — reset consecutive failure counter
                    recordSuccess()

                    // 3. Cache in memory
                    memoryCache[file.path] = metadata

                    // 4. Persist to DB
                    if (resourceId != null) {
                        saveToDatabaseCache(resourceId, file, metadata)
                    }

                    inFlight.remove(file.path)

                    // 5. Callback on main thread
                    val enriched = applyMetadata(file, metadata)
                    withContext(Dispatchers.Main) { onLoaded(enriched) }
                }
            } catch (e: Exception) {
                Timber.w(e, "AudioMetadataLoader: Failed for ${file.name}")
                recordFailure()
                failedCache[file.path] = true
                inFlight.remove(file.path)
            }
        }
    }

    /**
     * Pre-populates [memoryCache] from Room DB for all audio files in [resourceId].
     *
     * Call on the IO thread **before** the first RecyclerView bind so that
     * [getCachedMetadata] returns data immediately and [resolveAudioMetadata] in the
     * adapter can enrich items without waiting for an async DB round-trip.
     *
     * Safe to call multiple times — already-cached paths are skipped.
     */
    suspend fun warmMemoryCacheForResource(resourceId: Long) {
        if (disabled) return
        try {
            val entries = fileMetadataCacheDao.getAllForResource(resourceId)
            var count = 0
            for (entry in entries) {
                if (entry.hasAudioMetadata() && !memoryCache.containsKey(entry.filePath)) {
                    memoryCache[entry.filePath] = AudioMetadata(
                        artist = entry.artist,
                        album = entry.album,
                        title = entry.title,
                        duration = entry.durationMs
                    )
                    count++
                }
            }
            if (count > 0) {
                Timber.d("AudioMetadataLoader: Warmed memoryCache with $count audio entries for resource $resourceId")
            }
        } catch (e: Exception) {
            Timber.w(e, "AudioMetadataLoader: warmMemoryCacheForResource failed for resource $resourceId")
        }
    }

    /**
     * Synchronously loads extended audio metadata for [file] within the calling coroutine.
     * Does not touch the in-flight set, kill-switch, or callbacks.
     * Returns the parsed [AudioMetadata] or null on any failure.
     */
    suspend fun loadDetailed(file: MediaFile): AudioMetadata? {
        return try {
            val bytes: ByteArray? = when {
                PathUtils.isLocalPath(file.path) -> {
                    RandomAccessFile(file.path, "r").use { raf ->
                        val toRead = minOf(raf.length(), MAX_PARTIAL_READ_BYTES.toLong()).toInt()
                        val buf = ByteArray(toRead)
                        raf.readFully(buf)
                        buf
                    }
                }
                file.path.startsWith("content://") -> {
                    context.contentResolver.openInputStream(file.path.toUri())?.use { stream ->
                        stream.readNBytes(MAX_PARTIAL_READ_BYTES)
                    }
                }
                else -> readPartialBytes(file.path)
            }
            if (bytes == null || bytes.isEmpty()) return null
            extractMetadataFromBytes(bytes, file.path)
        } catch (e: Exception) {
            Timber.w(e, "AudioMetadataLoader: loadDetailed failed for ${file.path}")
            null
        }
    }

    private fun recordSuccess() {
        consecutiveFailures.set(0)
    }

    private fun recordFailure() {
        val count = consecutiveFailures.incrementAndGet()
        if (count == KILL_SWITCH_THRESHOLD) {
            disabled = true
            Timber.e(
                "AudioMetadataLoader: KILL-SWITCH activated after $count consecutive failures. " +
                    "Network audio metadata loading disabled for this session."
            )
        }
    }

    private fun shouldLogMetadataRetrieverFailureAsDebug(filePath: String, throwable: Throwable): Boolean {
        if (!isPartialNetworkMetadataPath(filePath)) return false
        return throwableCauseChain(throwable).any {
            it.javaClass.simpleName == "UnrecognizedInputFormatException"
        }
    }

    private fun metadataRetrieverFailureName(throwable: Throwable): String {
        return throwableCauseChain(throwable)
            .map { it.javaClass.simpleName }
            .lastOrNull { it.isNotBlank() }
            ?: throwable.javaClass.simpleName
    }

    private fun throwableCauseChain(throwable: Throwable): Sequence<Throwable> =
        generateSequence(throwable) { it.cause }

    private fun isPartialNetworkMetadataPath(path: String): Boolean {
        return path.startsWith("smb://", ignoreCase = true) ||
            path.startsWith("sftp://", ignoreCase = true) ||
            path.startsWith("ftp://", ignoreCase = true)
    }

    // ── Protocol routing ──────────────────────────────────────────────────────

    private suspend fun readPartialBytes(path: String): ByteArray? {
        return try {
            when {
                path.startsWith("smb://", ignoreCase = true) -> readSmbPartial(path)
                path.startsWith("sftp://", ignoreCase = true) -> readSftpPartial(path)
                path.startsWith("ftp://", ignoreCase = true) -> readFtpPartial(path)
                else -> null
            }
        } catch (e: Exception) {
            Timber.w(e, "AudioMetadataLoader: readPartialBytes failed for $path")
            null
        }
    }

    private suspend fun readSmbPartial(path: String): ByteArray? {
        return try {
            val pathInfo = SmbPathUtils.parseSmbPath(path) ?: return null
            val server = pathInfo.connectionInfo.server
            val shareName = pathInfo.connectionInfo.shareName
            val remotePath = pathInfo.remotePath

            val credentials = resolveSmbCredentials(server, shareName) ?: return null
            val connectionInfo = pathInfo.connectionInfo.copy(
                username = credentials.username,
                password = credentials.password,
                domain = credentials.domain
            )

            ConnectionThrottleManager.withThrottle(
                ConnectionThrottleManager.ProtocolLimits.SMB,
                "$server/$shareName"
            ) {
                when (val result = smbClient.readFileBytes(connectionInfo, remotePath, MAX_PARTIAL_READ_BYTES.toLong())) {
                    is SmbResult.Success -> result.data
                    is SmbResult.Error -> {
                        Timber.w("AudioMetadataLoader SMB read failed: ${result.message}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "AudioMetadataLoader: SMB readPartial exception")
            null
        }
    }

    private suspend fun readSftpPartial(path: String): ByteArray? {
        return try {
            val pathInfo = SftpPathUtils.parseSftpPath(path) ?: return null
            val credentials = credentialsRepository.getByTypeServerAndPort("SFTP", pathInfo.host, pathInfo.port)
                ?: credentialsRepository.getCredentialsByHost(pathInfo.host)
                ?: return null

            val connectionInfo = SftpClient.SftpConnectionInfo(
                host = pathInfo.host,
                port = pathInfo.port,
                username = credentials.username,
                password = credentials.password,
                privateKey = credentials.decryptedSshPrivateKey,
                passphrase = credentials.password.ifEmpty { null }
            )

            ConnectionThrottleManager.withThrottle(
                ConnectionThrottleManager.ProtocolLimits.SFTP,
                pathInfo.host
            ) {
                sftpClient.readFileBytes(connectionInfo, pathInfo.remotePath, MAX_PARTIAL_READ_BYTES.toLong())
                    .getOrNull()
            }
        } catch (e: Exception) {
            Timber.w(e, "AudioMetadataLoader: SFTP readPartial exception")
            null
        }
    }

    private suspend fun readFtpPartial(path: String): ByteArray? {
        return try {
            val pathInfo = FtpPathUtils.parseFtpPath(path) ?: return null
            val credentials = credentialsRepository.getByTypeServerAndPort("FTP", pathInfo.host, pathInfo.port)
                ?: credentialsRepository.getCredentialsByHost(pathInfo.host)
                ?: return null

            val usernameToUse = credentials.username

            // FTP client is stateful — ensure connected
            val connectResult = ftpClient.connect(
                host = pathInfo.host,
                port = pathInfo.port,
                username = usernameToUse,
                password = credentials.password
            )
            if (connectResult.isFailure) {
                Timber.w("AudioMetadataLoader: FTP connect failed for ${pathInfo.host}")
                return null
            }

            ConnectionThrottleManager.withThrottle(
                ConnectionThrottleManager.ProtocolLimits.FTP,
                pathInfo.host
            ) {
                ftpClient.readFileBytes(pathInfo.remotePath, MAX_PARTIAL_READ_BYTES.toLong())
                    .getOrNull()
            }
        } catch (e: Exception) {
            Timber.w(e, "AudioMetadataLoader: FTP readPartial exception")
            null
        }
    }

    // ── Metadata extraction (Media3 — safe Java-based parsing, no native SIGSEGV) ──

    /**
     * Parses metadata from a byte array using Media3's [MetadataRetriever].
     * Writes to a temp file and uses Media3's Java-based extractor pipeline,
     * which unlike native [android.media.MediaMetadataRetriever] cannot SIGSEGV
     * on truncated files. Returns null on any failure.
     */
    private suspend fun extractMetadataFromBytes(bytes: ByteArray, filePath: String): AudioMetadata? {
        val tempFile = File(context.cacheDir, "audio_meta_${System.nanoTime()}.tmp")
        return try {
            tempFile.writeBytes(bytes)
            val mediaItem = MediaItem.fromUri(tempFile.toUri())
            val trackGroupsFuture = MetadataRetriever.retrieveMetadata(context, mediaItem)
            val trackGroups = trackGroupsFuture.get(5, TimeUnit.SECONDS)

            var artist: String? = null
            var album: String? = null
            var title: String? = null
            var sampleRateHz: Int? = null
            var bitDepth: Int? = null
            var channels: Int? = null
            var bitrateBps: Int? = null
            var lossless: Boolean? = null
            var replayGainTrackDb: Float? = null
            var replayGainAlbumDb: Float? = null
            var coverFileName: String? = null
            var coverExtension: String? = null

            val coverCacheKey = coverCacheKey(filePath)

            for (groupIndex in 0 until trackGroups.length) {
                val trackGroup = trackGroups[groupIndex]
                for (trackIndex in 0 until trackGroup.length) {
                    val format = trackGroup.getFormat(trackIndex)

                    val metadata: Metadata? = format.metadata
                    if (metadata != null) {
                        for (i in 0 until metadata.length()) {
                            val entry = metadata.get(i)
                            extractFromMetadataEntry(entry)?.let { (key, value) ->
                                when (key) {
                                    "artist" -> if (artist == null) artist = value
                                    "album" -> if (album == null) album = value
                                    "title" -> if (title == null) title = value
                                    "replayGainTrack" -> if (replayGainTrackDb == null) {
                                        replayGainTrackDb = value.replace(" dB", "", ignoreCase = true).toFloatOrNull()
                                    }
                                    "replayGainAlbum" -> if (replayGainAlbumDb == null) {
                                        replayGainAlbumDb = value.replace(" dB", "", ignoreCase = true).toFloatOrNull()
                                    }
                                }
                            }
                            if (coverFileName == null) {
                                extractCoverArtEntry(entry)?.let { (imgBytes, imgMimeType) ->
                                    if (imgBytes.size <= MAX_COVER_BYTES) {
                                        val ext = when {
                                            imgMimeType.contains("png", ignoreCase = true) -> "png"
                                            else -> "jpg"
                                        }
                                        audioMetadataCacheRepository.saveCover(coverCacheKey, imgBytes, ext)
                                        coverFileName = coverCacheKey
                                        coverExtension = ext
                                    }
                                }
                            }
                        }
                    }

                    // Format technical fields (sample rate, channels, bitrate, lossless, FLAC bit depth)
                    val mimeType = format.sampleMimeType ?: format.containerMimeType
                    if (mimeType != null && mimeType.startsWith("audio/")) {
                        if (sampleRateHz == null && format.sampleRate > 0) sampleRateHz = format.sampleRate
                        if (channels == null && format.channelCount > 0) channels = format.channelCount
                        if (bitrateBps == null && format.bitrate > 0) bitrateBps = format.bitrate
                        if (lossless == null) {
                            lossless = when (mimeType.lowercase()) {
                                "audio/flac", "audio/raw", "audio/x-wav", "audio/x-alac" -> true
                                "audio/mpeg", "audio/mp4a-latm", "audio/vorbis", "audio/opus" -> false
                                else -> null
                            }
                        }
                        // FLAC: bit depth from STREAMINFO block (not exposed by Media3 via Format)
                        if (bitDepth == null && mimeType.equals("audio/flac", ignoreCase = true)) {
                            bitDepth = parseFlacBitDepth(bytes)
                        }
                    }
                }
            }

            AudioMetadata(
                artist = artist?.takeIf { it.isNotBlank() },
                album = album?.takeIf { it.isNotBlank() },
                title = title?.takeIf { it.isNotBlank() },
                duration = null, // Duration from partial 64KB read is unreliable
                sampleRateHz = sampleRateHz,
                bitDepth = bitDepth,
                channels = channels,
                bitrateBps = bitrateBps,
                lossless = lossless,
                replayGainTrackDb = replayGainTrackDb,
                replayGainAlbumDb = replayGainAlbumDb,
                coverFileName = coverFileName,
                coverExtension = coverExtension
            )
        } catch (e: Exception) {
            val failureName = metadataRetrieverFailureName(e)
            if (shouldLogMetadataRetrieverFailureAsDebug(filePath, e)) {
                // Partial SMB/SFTP/FTP headers are best-effort inputs here, so format misses are expected noise.
                Timber.d("AudioMetadataLoader: Media3 MetadataRetriever expected miss on ${bytes.size} bytes: $failureName")
            } else {
                Timber.w("AudioMetadataLoader: Media3 MetadataRetriever failed on ${bytes.size} bytes: $failureName")
            }
            null
        } finally {
            try {
                tempFile.delete()
            } catch (_: Exception) { /* best-effort cleanup */ }
        }
    }

    /**
     * Extracts a key-value pair from a Media3 metadata entry.
     * Supports ID3v2 (TextInformationFrame), Vorbis (VorbisComment), and generic entries.
     */
    private fun extractFromMetadataEntry(entry: Metadata.Entry): Pair<String, String>? {
        return try {
            when (entry) {
                is androidx.media3.extractor.metadata.id3.TextInformationFrame -> {
                    val id = entry.id.uppercase()
                    if (id == "TXXX") {
                        val description = entry.description?.uppercase() ?: return null
                        val raw = entry.values.firstOrNull() ?: return null
                        return when (description) {
                            "REPLAYGAIN_TRACK_GAIN" -> "replayGainTrack" to raw
                            "REPLAYGAIN_ALBUM_GAIN" -> "replayGainAlbum" to raw
                            else -> null
                        }
                    }
                    val raw = entry.values.firstOrNull() ?: return null
                    val value = fixCp1251Encoding(raw)
                    when (id) {
                        "TPE1", "TPE2" -> "artist" to value
                        "TALB" -> "album" to value
                        "TIT2" -> "title" to value
                        else -> null
                    }
                }
                is androidx.media3.extractor.metadata.vorbis.VorbisComment -> {
                    val key = entry.key.uppercase()
                    val value = entry.value
                    when (key) {
                        "ARTIST", "ALBUMARTIST" -> "artist" to value
                        "ALBUM" -> "album" to value
                        "TITLE" -> "title" to value
                        "REPLAYGAIN_TRACK_GAIN" -> "replayGainTrack" to value
                        "REPLAYGAIN_ALBUM_GAIN" -> "replayGainAlbum" to value
                        else -> null
                    }
                }
                else -> {
                    Timber.v("AudioMetadataLoader: Unhandled metadata entry: ${entry.javaClass.simpleName}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.v(e, "AudioMetadataLoader: Failed to parse metadata entry")
            null
        }
    }

    // ── Credentials resolution ────────────────────────────────────────────────

    private suspend fun resolveSmbCredentials(
        server: String,
        shareName: String
    ): com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity? {
        val normalizedShare = shareName.trim().trim('/', '\\')
        val firstSegment = normalizedShare.substringBefore('/', normalizedShare)

        val candidates = linkedSetOf<String>().apply {
            add(shareName)
            if (normalizedShare.isNotEmpty()) add(normalizedShare)
            if (firstSegment.isNotEmpty()) add(firstSegment)
        }

        for (candidate in candidates) {
            val cred = credentialsRepository.getByServerAndShare(server, candidate)
            if (cred != null) return cred
        }

        // Fallback: host-level SMB credentials
        val hostCredentials = credentialsRepository.getCredentialsByHost(server)
        if (hostCredentials != null && hostCredentials.type.equals("SMB", ignoreCase = true)) {
            return hostCredentials
        }

        return null
    }

    // ── DB persistence ────────────────────────────────────────────────────────

    private suspend fun saveToDatabaseCache(
        resourceId: Long,
        file: MediaFile,
        metadata: AudioMetadata
    ) {
        try {
            val entity = FileMetadataCacheEntity(
                resourceId = resourceId,
                filePath = file.path,
                provider = "NETWORK",
                credentialsId = null,
                lastModified = file.createdDate,
                fileSize = file.size,
                cachedAt = System.currentTimeMillis(),
                thumbnailPath = null,
                durationMs = metadata.duration,
                width = null,
                height = null,
                videoRotation = null,
                exifDateTime = null,
                exifJson = null,
                artist = metadata.artist,
                album = metadata.album,
                title = metadata.title
            )
            fileMetadataCacheDao.upsert(entity)
        } catch (e: Exception) {
            Timber.w(e, "AudioMetadataLoader: DB save failed for ${file.path}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Detects ID3 tags encoded as CP1251 (Windows Russian) but declared as ISO-8859-1.
     * Media3 reads them as Latin-1, producing garbled text for Cyrillic content.
     * Re-encodes the string as ISO-8859-1 bytes and decodes as windows-1251;
     * accepts the result only when ≥50% of letters are Cyrillic.
     */
    private fun fixCp1251Encoding(value: String): String {
        // Already valid Unicode Cyrillic — nothing to fix
        if (value.any { it.code in 0x0400..0x04FF }) return value
        // No Latin-1 extended range → plain ASCII, no re-encoding needed
        if (value.none { it.code in 0x00C0..0x00FF }) return value
        return try {
            val bytes = value.toByteArray(Charsets.ISO_8859_1)
            val reDec = String(bytes, charset("windows-1251"))
            val letterCount = reDec.count { it.isLetter() }
            val cyrillicCount = reDec.count { it.code in 0x0400..0x04FF }
            // Accept only when clearly Cyrillic content (majority of letters)
            if (letterCount > 0 && cyrillicCount * 2 >= letterCount) {
                Timber.v("AudioMetadataLoader: CP1251 re-decode applied: '$value' → '$reDec'")
                reDec
            } else {
                value
            }
        } catch (e: Exception) {
            value
        }
    }

    /**
     * Parses bits-per-sample from the FLAC STREAMINFO block.
     * Layout: fLaC(4) + block-header(4) + min/max-blocksize(4) + min/max-framesize(6) = 18 bytes;
     * then bits 23-27 straddle bytes 20-21 (big-endian bit order).
     */
    private fun parseFlacBitDepth(bytes: ByteArray): Int? {
        if (bytes.size < 27) return null
        // FLAC signature "fLaC" (0x66 0x4C 0x61 0x43)
        if (bytes[0] != 0x66.toByte() || bytes[1] != 0x4C.toByte() ||
            bytes[2] != 0x61.toByte() || bytes[3] != 0x43.toByte()) return null
        // Byte 4: last-metadata-block(bit7) + block-type(bits6-0); STREAMINFO type = 0
        if ((bytes[4].toInt() and 0x7F) != 0) return null
        // Within STREAMINFO data (starting at byte 8), bits-per-sample-1 occupies 5 bits:
        //   byte[20] bit0 = MSB, byte[21] bits7-4 = remaining 4 bits
        val b20 = bytes[20].toInt() and 0xFF
        val b21 = bytes[21].toInt() and 0xFF
        val bpsMinusOne = ((b20 and 0x01) shl 4) or (b21 ushr 4)
        val bps = bpsMinusOne + 1
        return if (bps in 4..32) bps else null
    }

    /**
     * Extracts embedded cover art bytes from a Media3 metadata entry.
     * Handles FLAC PictureFrame and ID3v2 ApicFrame.
     * @return Pair(imageBytes, mimeType) or null if entry is not cover art.
     */
    private fun extractCoverArtEntry(entry: Metadata.Entry): Pair<ByteArray, String>? {
        return try {
            when (entry) {
                is androidx.media3.extractor.metadata.flac.PictureFrame -> {
                    val bytes = entry.pictureData ?: return null
                    if (bytes.isEmpty()) null else bytes to entry.mimeType
                }
                is androidx.media3.extractor.metadata.id3.ApicFrame -> {
                    val bytes = entry.pictureData ?: return null
                    if (bytes.isEmpty()) null else bytes to entry.mimeType
                }
                else -> null
            }
        } catch (e: Exception) {
            Timber.v(e, "AudioMetadataLoader: Failed to extract cover art entry")
            null
        }
    }

    /** First 16 bytes of SHA-256(path) as a hex string — stable, unique cache key. */
    private fun coverCacheKey(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(path.toByteArray(Charsets.UTF_8))
        return hash.take(16).joinToString("") { "%02x".format(it) }
    }

    private fun applyMetadata(file: MediaFile, metadata: AudioMetadata): MediaFile {
        return file.copy(
            artist = metadata.artist ?: file.artist,
            album = metadata.album ?: file.album,
            title = metadata.title ?: file.title,
            duration = metadata.duration ?: file.duration
        )
    }

    private fun FileMetadataCacheEntity.hasAudioMetadata(): Boolean {
        return artist != null || album != null || title != null || durationMs != null
    }

    /**
     * Parsed audio metadata from a network file header.
     */
    data class AudioMetadata(
        val artist: String?,
        val album: String?,
        val title: String?,
        val duration: Long?,
        val sampleRateHz: Int? = null,
        val bitDepth: Int? = null,
        val channels: Int? = null,
        val bitrateBps: Int? = null,
        val lossless: Boolean? = null,
        val replayGainTrackDb: Float? = null,
        val replayGainAlbumDb: Float? = null,
        val coverFileName: String? = null,
        val coverExtension: String? = null
    ) {
        fun hasAnyData(): Boolean = artist != null || album != null || title != null
            || duration != null || sampleRateHz != null || bitDepth != null
            || channels != null || bitrateBps != null || lossless != null
            || replayGainTrackDb != null || replayGainAlbumDb != null || coverFileName != null
    }
}
