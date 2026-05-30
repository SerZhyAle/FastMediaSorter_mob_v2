package com.sza.fastmediasorter.ui.player.helpers

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormat
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormatDetector
import com.sza.fastmediasorter.domain.model.MidiPlaybackPolicy
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

/**
 * Local-file playback helpers and MIME-type / MediaItem utilities.
 *
 * Extension functions on [VideoPlayerManager] - extracted to reduce per-file CFG complexity
 * (Kotlin daemon ControlFlowGraph OOM during parallel flavor compilation).
 */

// ═══════════════════════════════════════════════════════════════════════════
// Public entry-point (non-suspend wrapper)
// ═══════════════════════════════════════════════════════════════════════════

fun VideoPlayerManager.playLocalVideo(path: String, playWhenReady: Boolean = true) {
    managerScope.launch {
        playLocalVideoInternal(path, playWhenReady)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Core local-file playback
// ═══════════════════════════════════════════════════════════════════════════

internal suspend fun VideoPlayerManager.playLocalVideoInternal(path: String, playWhenReady: Boolean = true) {
    val normalizedPath = normalizeLocalPath(path)
    val isAudio = normalizedPath.substringAfterLast('.', "").lowercase() in MediaTypeUtils.AUDIO_EXTENSIONS
    Timber.d("VideoPlayerManager: Playing local video - path=$path, normalizedPath=$normalizedPath")

    // Validate file exists for local files (IO thread to avoid StrictMode DiskReadViolation)
    if (!normalizedPath.startsWith("content://")) {
        val file = File(normalizedPath)
        val fileCheck = withContext(Dispatchers.IO) {
            Triple(file.exists(), file.canRead(), if (file.exists()) file.length() else 0L)
        }

        if (!fileCheck.first) {
            Timber.w("VideoPlayerManager: Local file does not exist: $normalizedPath (originalPath=$path)")
            playerCallback.showFileNotFound(file.name)
            return
        }
        if (!fileCheck.second) {
            // File exists but not readable - try content URI fallback (Android 11+ scoped storage)
            val contentUri = resolveContentUriForPath(normalizedPath)
            if (contentUri != null) {
                Timber.i("VideoPlayerManager: File not readable via path, using content URI fallback: $contentUri")
                playLocalVideoInternal(contentUri, playWhenReady)
                return
            }
            Timber.e("VideoPlayerManager: Cannot read local file: $normalizedPath (originalPath=$path)")
            playerCallback.showError(context.getString(R.string.cannot_read_file, file.name))
            return
        }
        Timber.d("VideoPlayerManager: File exists and readable: ${file.name}, size=${fileCheck.third} bytes")
    }

    if ((normalizedPath.endsWith(".m2ts", ignoreCase = true) || normalizedPath.endsWith(".m2t", ignoreCase = true))
        && !normalizedPath.startsWith("content://")) {
        val format = withContext(Dispatchers.IO) {
            try {
                FileInputStream(normalizedPath).use { fis ->
                    val probe = ByteArray(TsPacketFormatDetector.PROBE_BYTES)
                    val read = fis.read(probe)
                    TsPacketFormatDetector.detect(if (read > 0) probe.copyOf(read) else probe)
                }
            } catch (e: Exception) {
                Timber.w(e, "VideoPlayerManager: BD-TS probe failed for $normalizedPath")
                TsPacketFormat.UNKNOWN
            }
        }
        if (format != TsPacketFormat.STANDARD_188) {
            Timber.d("S0054: LocalPlaybackHelper recreating player with BD-TS strip factory for $normalizedPath format=$format")
            releasePlayer()
            val localFactory: DataSource.Factory = DefaultDataSourceFactory(context)
            val audioAttr = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
            exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(
                    localFactory.buildBdTsMediaSourceFactory(TsPacketFormat.BD_192)
                )
                .setAudioAttributes(audioAttr, true)
                .build()
            exoPlayer?.addListener(playerListener)
            currentPlayerView?.player = exoPlayer
        }
    }

    if (exoPlayer == null && currentPlayerView != null) {
        createPlayer(currentPlayerView!!, isAudio = isAudio)
    }

    exoPlayer?.apply {
        val uri = if (normalizedPath.startsWith("content://")) {
            normalizedPath
        } else {
            File(normalizedPath).toURI().toString()
        }

        Timber.d("VideoPlayerManager: Setting media item for URI: $uri")
        setMediaItem(createMediaItem(uri, normalizedPath))
        prepare()
        this.playWhenReady = playWhenReady
    }

    Timber.d("VideoPlayerManager: Local video setup complete")
}

// ═══════════════════════════════════════════════════════════════════════════
// Content URI fallback (Android 11+ scoped storage)
// ═══════════════════════════════════════════════════════════════════════════

internal suspend fun VideoPlayerManager.resolveContentUriForPath(filePath: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
            val selectionArgs = arrayOf(filePath)
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    ContentUris.withAppendedId(uri, id).toString()
                } else null
            }
        } catch (e: Exception) {
            Timber.w(e, "VideoPlayerManager: Failed to resolve content URI for $filePath")
            null
        }
    }

// ═══════════════════════════════════════════════════════════════════════════
// Path normalisation
// ═══════════════════════════════════════════════════════════════════════════

internal fun VideoPlayerManager.normalizeLocalPath(path: String): String {
    if (path.startsWith("content://")) return path

    if (path.startsWith("file:", ignoreCase = true)) {
        return try {
            Uri.parse(path).path?.takeIf { it.isNotBlank() } ?: path
        } catch (e: Exception) {
            Timber.w(e, "VideoPlayerManager: Failed to normalize file URI, using original path")
            path
        }
    }

    return path
}

// ═══════════════════════════════════════════════════════════════════════════
// MIME-type detection
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Infer MIME type from the file extension.
 * Returns null for cloud:// URIs and unknown extensions so ExoPlayer can auto-detect.
 */
internal fun VideoPlayerManager.getMimeTypeFromPath(path: String): String? {
    // Cloud URIs (cloud://provider/fileId) have no extension - skip detection
    if (path.startsWith("cloud://")) return null
    // content:// URIs have no file extension - use ContentResolver for accurate MIME type
    if (path.startsWith("content://")) {
        return try {
            context.contentResolver.getType(android.net.Uri.parse(path))
                .also { Timber.d("VideoPlayerManager.getMimeTypeFromPath: content:// → ContentResolver type=$it") }
        } catch (e: Exception) {
            Timber.d("VideoPlayerManager.getMimeTypeFromPath: ContentResolver failed for $path: ${e.message}")
            null
        }
    }
    // Extract filename from URI path (remove query parameters and fragments)
    val cleanPath = path.substringBefore('?').substringBefore('#')
    val extension = cleanPath.substringAfterLast('.', "").lowercase()

    Timber.d("VideoPlayerManager.getMimeTypeFromPath: path=$path, extension=$extension")

    if (MidiPlaybackPolicy.isMidiExtension(extension)) {
        return MimeTypes.AUDIO_MIDI
    }

    return when (extension) {
        "mp3" -> MimeTypes.AUDIO_MPEG
        "m4a", "aac", "adts" -> MimeTypes.AUDIO_AAC
        "flac" -> MimeTypes.AUDIO_FLAC
        "ogg", "oga" -> MimeTypes.AUDIO_OGG
        "opus" -> MimeTypes.AUDIO_OPUS
        "amr" -> MimeTypes.AUDIO_AMR_NB
        "awb" -> MimeTypes.AUDIO_AMR_WB
        "ac3" -> MimeTypes.AUDIO_AC3
        "ec3" -> MimeTypes.AUDIO_E_AC3
        "ac4" -> MimeTypes.AUDIO_AC4
        "mka" -> MimeTypes.AUDIO_MATROSKA
        "alac", "caf" -> MimeTypes.AUDIO_ALAC
        "mp4", "m4v" -> MimeTypes.VIDEO_MP4
        "webm" -> MimeTypes.VIDEO_WEBM
        "mkv" -> MimeTypes.VIDEO_MATROSKA
        "ts", "m2ts", "mts" -> MimeTypes.VIDEO_MP2T
        "ogv" -> MimeTypes.VIDEO_OGG
        "vob", "mpg", "mpeg", "m2v" -> MimeTypes.VIDEO_MPEG2
        "divx" -> MimeTypes.VIDEO_DIVX
        "flv" -> "video/x-flv"  // FLV not natively supported by ExoPlayer
        else -> {
            Timber.w("VideoPlayerManager.getMimeTypeFromPath: Unknown extension '$extension' for path: $path")
            null
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MediaItem factory
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Create a [MediaItem] with an explicit MIME type hint when detectable,
 * so ExoPlayer can skip sniffing overhead for well-known formats.
 */
internal fun VideoPlayerManager.createMediaItem(uri: String, path: String): MediaItem {
    val mimeType = getMimeTypeFromPath(path)

    Timber.d("VideoPlayerManager.createMediaItem: uri=$uri, path=$path, mimeType=$mimeType")

    return if (mimeType != null) {
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .build()
        Timber.i("VideoPlayerManager.createMediaItem: Created with explicit MIME type: $mimeType")
        item
    } else {
        Timber.w("VideoPlayerManager.createMediaItem: No MIME type detected, using auto-detection")
        MediaItem.fromUri(uri)
    }
}
