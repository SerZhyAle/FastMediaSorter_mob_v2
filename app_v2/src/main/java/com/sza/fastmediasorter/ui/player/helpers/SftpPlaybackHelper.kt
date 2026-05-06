package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.datasource.SftpDataSourceFactory
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormat
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

    // The path may embed a different port than credentials (e.g. non-standard SFTP port in URI).
    // Always prefer the port from the path URI over the credential default.
    val pathUri = PathUtils.safeParseUri(path)
    val serverHost = pathUri.host?.takeIf { it.isNotEmpty() } ?: credentials.server
    val serverPort = pathUri.port.takeIf { it > 0 } ?: credentials.port

    // Activate video-player priority mode to suppress thumbnail pre-fetching while streaming
    val resourceKey = "sftp://$serverHost:$serverPort"
    activeResourceKey = resourceKey
    ConnectionThrottleManager.activateVideoPlayerMode(resourceKey)

    val dataSourceFactory = SftpDataSourceFactory(
        sftpClient,
        serverHost,
        serverPort,
        credentials.username,
        credentials.password,
        context
    )

    val loadControl = PrefetchLoadControlFactory.build(
        plan = activePrefetchPlan,
        useCloudDefaults = false,
        tag = "sftp"
    )

    val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

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

    val routeHint = NetworkPlaybackContainerHint.fromPath(path)
    Timber.d("VideoPlayerManager: SFTP routeHint=$routeHint path=$path")
    val format = if (routeHint == NetworkPlaybackContainerHint.M2TS_TS_CANDIDATE) {
        (dataSourceFactory as DataSource.Factory).detectTsFormatSuspend(sftpUri)
    } else {
        TsPacketFormat.UNKNOWN
    }

    exoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            (dataSourceFactory as DataSource.Factory).buildBdTsMediaSourceFactory(format)
        )
        .setLoadControl(loadControl)
        .setAudioAttributes(audioAttributes, true)
        .build()

    exoPlayer?.addListener(loadControl)
    exoPlayer?.addListener(playerListener)
    currentPlayerView?.player = exoPlayer

    val mediaItem = createMediaItem(sftpUri.toString(), path)
    exoPlayer?.setMediaItem(mediaItem)
    exoPlayer?.prepare()
    exoPlayer?.playWhenReady = playWhenReady

    Timber.i("VideoPlayerManager: SFTP video setup complete")
}
