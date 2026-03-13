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
import timber.log.Timber
import java.io.File
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
 * (artist, album, title, duration) via Media3 [MetadataRetriever] (no temp files,
 * no native SIGSEGV risk), and caches results in both an in-memory map and
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
    @ApplicationContext private val context: Context,
    private val fileMetadataCacheDao: FileMetadataCacheDao,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) {

    companion object {
        private const val MAX_PARTIAL_READ_BYTES = 65536 // 64 KB — enough for ID3v2 + Vorbis headers
        private const val FAILED_CACHE_MAX_SIZE = 5000
        /** After this many consecutive failures, disable the feature for the session. */
        private const val KILL_SWITCH_THRESHOLD = 15
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

                    val metadata = extractMetadataFromBytes(bytes)
                    if (metadata == null || !metadata.hasAnyData()) {
                        Timber.d("AudioMetadataLoader: No metadata extracted for ${file.name}")
                        recordFailure()
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

    private fun recordSuccess() {
        consecutiveFailures.set(0)
    }

    private fun recordFailure() {
        val count = consecutiveFailures.incrementAndGet()
        if (count >= KILL_SWITCH_THRESHOLD) {
            disabled = true
            Timber.e(
                "AudioMetadataLoader: KILL-SWITCH activated after $count consecutive failures. " +
                    "Network audio metadata loading disabled for this session."
            )
        }
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
    private suspend fun extractMetadataFromBytes(bytes: ByteArray): AudioMetadata? {
        val tempFile = File(context.cacheDir, "audio_meta_${System.nanoTime()}.tmp")
        return try {
            tempFile.writeBytes(bytes)
            val mediaItem = MediaItem.fromUri(tempFile.toUri())
            val trackGroupsFuture = MetadataRetriever.retrieveMetadata(context, mediaItem)
            val trackGroups = trackGroupsFuture.get(5, TimeUnit.SECONDS)

            var artist: String? = null
            var album: String? = null
            var title: String? = null

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
                                }
                            }
                        }
                    }
                }
            }

            AudioMetadata(
                artist = artist?.takeIf { it.isNotBlank() },
                album = album?.takeIf { it.isNotBlank() },
                title = title?.takeIf { it.isNotBlank() },
                duration = null // Duration from partial 64KB read is unreliable
            )
        } catch (e: Exception) {
            Timber.w(e, "AudioMetadataLoader: Media3 MetadataRetriever failed on ${bytes.size} bytes")
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
                    val value = entry.values.firstOrNull() ?: return null
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
                        else -> null
                    }
                }
                is androidx.media3.extractor.metadata.flac.PictureFrame -> null
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
        val duration: Long?
    ) {
        fun hasAnyData(): Boolean = artist != null || album != null || title != null || duration != null
    }
}
