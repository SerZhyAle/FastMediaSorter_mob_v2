package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.datasource.SftpDataSourceFactory
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * SFTP network-stream playback.
 *
 * Extension function on [VideoPlayerManager] — extracted to a separate file to reduce
 * per-file CFG complexity for the Kotlin compiler (avoids GC overhead during parallel
 * flavor compilation of the original 1 700-line VideoPlayerManager.kt).
 */
internal suspend fun VideoPlayerManager.playSftpVideo(
    path: String,
    credentialsId: String?,
    playWhenReady: Boolean
) {
    if (credentialsId == null) {
        Timber.e("VideoPlayerManager: No credentials for SFTP")
        playerCallback.showError("No credentials found")
        return
    }

    val credentials = credentialsRepository.getByCredentialId(credentialsId)
    if (credentials == null) {
        Timber.e("VideoPlayerManager: Credentials not found in DB")
        playerCallback.showError("Credentials not found")
        return
    }

    Timber.d("VideoPlayerManager: Playing SFTP video - server=${credentials.server}")
    releasePlayer()

    // Activate video-player priority mode to suppress thumbnail pre-fetching while streaming
    val resourceKey = "sftp://${credentials.server}:${credentials.port}"
    activeResourceKey = resourceKey
    ConnectionThrottleManager.activateVideoPlayerMode(resourceKey)

    val dataSourceFactory = SftpDataSourceFactory(
        sftpClient,
        credentials.server,
        credentials.port,
        credentials.username,
        credentials.password
    )

    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            VideoPlayerManager.MIN_BUFFER_MS,
            VideoPlayerManager.MAX_BUFFER_MS,
            VideoPlayerManager.BUFFER_FOR_PLAYBACK_MS,
            VideoPlayerManager.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    exoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(dataSourceFactory as DataSource.Factory)
        )
        .setLoadControl(loadControl)
        .setAudioAttributes(audioAttributes, true)
        .build()

    exoPlayer?.addListener(playerListener)
    currentPlayerView?.player = exoPlayer

    // Construct SFTP URI with properly encoded path segments
    val rawUri = if (path.startsWith("sftp://")) {
        path
    } else {
        "sftp://${credentials.server}:${credentials.port}$path"
    }

    val parsedUri = PathUtils.safeParseUri(rawUri)
    val encodedPath = parsedUri.path?.split("/")
        ?.joinToString("/") { segment ->
            if (segment.isEmpty()) "" else Uri.encode(segment, "@")
        } ?: ""

    val sftpUri = Uri.Builder()
        .scheme("sftp")
        .authority(parsedUri.authority)
        .encodedPath(encodedPath)
        .build()

    Timber.d("VideoPlayerManager: SFTP URI=$sftpUri")

    val mediaItem = createMediaItem(sftpUri.toString(), path)
    exoPlayer?.setMediaItem(mediaItem)
    exoPlayer?.prepare()
    exoPlayer?.playWhenReady = playWhenReady

    Timber.i("VideoPlayerManager: SFTP video setup complete")
}
