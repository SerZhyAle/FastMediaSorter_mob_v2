package com.sza.fastmediasorter.data.repository.streams

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0712: app-private, on-disk persistence of the last captured low-res stream frame, keyed by stream
 * URL. It is the cold-start backing layer below the in-memory [StreamFrameCache]: the snapshot engine
 * writes each freshly captured grid frame here, and on the next session the grid pre-warms its cache
 * from these files so a known channel shows its last frame immediately instead of waiting ~12 s for a
 * new live capture. Frames are stored as small JPEGs (alpha-free video frames) under a total-size disk
 * budget, so the disk footprint stays bounded. All I/O is off the main thread; every path is best-effort - a
 * failed read/write just falls back to the favicon/placeholder, never crashes the grid.
 */
@Singleton
class StreamFramePersistentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val directory: File by lazy { File(context.filesDir, DIR_NAME).apply { mkdirs() } }

    /** Persist [bitmap] as the last-frame thumbnail for [url], pruning the oldest files past the cap. */
    // Broad catch by design: a thumbnail write is best-effort - any failure logs and is swallowed so disk
    // or codec trouble never crashes the grid (the tile just falls back to favicon next time).
    @Suppress("TooGenericExceptionCaught")
    suspend fun save(url: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            val dir = directory
            val target = File(dir, fileName(url))
            val temp = File(dir, fileName(url) + TEMP_SUFFIX)
            FileOutputStream(temp).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out) }
            // Atomic swap so a half-written file is never decoded as a torn thumbnail.
            if (!temp.renameTo(target)) temp.delete()
            enforceCap(dir)
        } catch (t: Throwable) {
            Timber.w(t, "Stream frame persist failed: %s", url)
        }
    }

    /** Decode the persisted thumbnail for [url], or null when none exists / the file is unreadable. */
    // Broad catch by design: a corrupt/oversized thumbnail must degrade to null (favicon), not crash.
    @Suppress("TooGenericExceptionCaught")
    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(directory, fileName(url))
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (t: Throwable) {
            Timber.w(t, "Stream frame restore failed: %s", url)
            null
        }
    }

    /** Drop the persisted thumbnail for [url] (channel removed / its URL edited). */
    suspend fun remove(url: String) = withContext(Dispatchers.IO) {
        runCatching { File(directory, fileName(url)).delete() }
        Unit
    }

    private fun enforceCap(dir: File) = evictToBudget(dir, MAX_DISK_BYTES)

    /**
     * Evict oldest-first (by modification time) until the directory's total JPEG footprint is within
     * [maxBytes]. File count is intentionally unbounded: a captured catalog holds thousands of channels,
     * and the previous fixed 64-file cap silently dropped thumbnails for large catalogs regardless of the
     * real disk usage (S1130). `internal` for a size-controlled unit test; production uses [MAX_DISK_BYTES].
     */
    @VisibleForTesting
    internal fun evictToBudget(dir: File, maxBytes: Long) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun fileName(url: String): String = hash(url) + EXT

    // SHA-256 of the URL: a filesystem-safe, collision-resistant key that stays stable across restarts
    // and catalog re-imports as long as the channel URL is unchanged.
    private fun hash(url: String): String =
        MessageDigest.getInstance(HASH_ALGORITHM)
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIR_NAME = "stream_frames"
        const val EXT = ".jpg"
        const val TEMP_SUFFIX = ".tmp"
        const val HASH_ALGORITHM = "SHA-256"
        const val QUALITY = 75

        // Evict by total disk footprint, not file count: a large captured catalog would otherwise silently
        // lose its oldest thumbnails at a fixed 64-file cap regardless of real usage (S1130). 150 MB mirrors
        // the StreamsPlayer disk budget in stream-playback-recommendations.md (§6.3).
        const val MAX_DISK_BYTES = 150L * 1024 * 1024
    }
}
