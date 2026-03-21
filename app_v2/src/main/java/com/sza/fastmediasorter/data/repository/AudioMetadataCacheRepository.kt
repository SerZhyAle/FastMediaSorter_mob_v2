package com.sza.fastmediasorter.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioMetadataCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CACHE_DIR_NAME = "audio_metadata_cache"
        const val MAX_CACHE_BYTES = 10L * 1024 * 1024 * 1024 // 10 GB
        private const val CLEANUP_FRACTION = 0.5
    }

    private val cacheDir: File get() = File(context.filesDir, CACHE_DIR_NAME)

    fun readMetadata(audioFileName: String): CachedAudioData? {
        val metaFile = File(cacheDir, "$audioFileName.metadata")
        if (!metaFile.exists()) return null
        return try {
            val json = JSONObject(metaFile.readText())
            val ext = json.optString("coverExtension", "").ifEmpty { null }
            val coverFile = ext?.let {
                File(cacheDir, "$audioFileName.$it").takeIf { f -> f.exists() }
            }
            CachedAudioData(
                trackName   = json.optString("trackName").ifEmpty { null },
                artistName  = json.optString("artistName").ifEmpty { null },
                albumName   = json.optString("albumName").ifEmpty { null },
                releaseYear = json.optString("releaseYear").ifEmpty { null },
                coverArtUrl = json.optString("coverArtUrl").ifEmpty { null },
                coverFile   = coverFile
            )
        } catch (e: Exception) {
            Timber.d(e, "AudioMetadataCache: failed to read %s", metaFile)
            null
        }
    }

    fun saveMetadata(audioFileName: String, data: AudioMetadataSaveData) {
        try {
            ensureCacheDirExists()
            val json = JSONObject().apply {
                put("trackName",      data.trackName ?: "")
                put("artistName",     data.artistName ?: "")
                put("albumName",      data.albumName ?: "")
                put("releaseYear",    data.releaseYear ?: "")
                put("coverArtUrl",    data.coverArtUrl ?: "")
                put("coverExtension", data.coverExtension ?: "")
            }
            atomicWrite(File(cacheDir, "$audioFileName.metadata"), json.toString())
        } catch (e: Exception) {
            Timber.d(e, "AudioMetadataCache: failed to save metadata for %s", audioFileName)
        }
    }

    fun saveCover(audioFileName: String, imageBytes: ByteArray, extension: String) {
        try {
            ensureCacheDirExists()
            atomicWriteBytes(File(cacheDir, "$audioFileName.$extension"), imageBytes)
        } catch (e: Exception) {
            Timber.d(e, "AudioMetadataCache: failed to save cover for %s", audioFileName)
        }
    }

    fun getCacheSize(): Long =
        cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Timber.d("AudioMetadataCache: cleared")
        } catch (e: Exception) {
            Timber.d(e, "AudioMetadataCache: clearCache failed")
        }
    }

    /** @return true если были удалены файлы */
    fun trimIfNeeded(): Boolean {
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return false
        val totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_CACHE_BYTES) return false
        val toDelete = (files.size * CLEANUP_FRACTION).toInt().coerceAtLeast(1)
        files.take(toDelete).forEach { it.delete() }
        Timber.d("AudioMetadataCache: trimmed %d files (was %d MB)", toDelete, totalSize / 1024 / 1024)
        return true
    }

    private fun ensureCacheDirExists() {
        if (!cacheDir.exists()) cacheDir.mkdirs()
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parent, target.name + ".tmp")
        tmp.writeText(content)
        tmp.renameTo(target)
    }

    private fun atomicWriteBytes(target: File, content: ByteArray) {
        val tmp = File(target.parent, target.name + ".tmp")
        tmp.writeBytes(content)
        tmp.renameTo(target)
    }
}

data class CachedAudioData(
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val releaseYear: String?,
    val coverArtUrl: String?,
    val coverFile: File?      // null = нет сохранённой обложки
)

data class AudioMetadataSaveData(
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val releaseYear: String?,
    val coverArtUrl: String?,
    val coverExtension: String?  // "jpg" или "png"
)
