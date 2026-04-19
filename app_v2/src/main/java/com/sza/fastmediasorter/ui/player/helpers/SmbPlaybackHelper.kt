package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.datasource.SmbDataSourceFactory
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import com.sza.fastmediasorter.utils.SmbPathUtils
import timber.log.Timber

/**
 * SMB network-stream playback.
 *
 * Extension function on [VideoPlayerManager] — extracted to a separate file to reduce
 * per-file CFG complexity for the Kotlin compiler (avoids GC overhead during parallel
 * flavor compilation of the original 1 700-line VideoPlayerManager.kt).
 */
internal suspend fun VideoPlayerManager.playSmbVideo(
    path: String,
    credentialsId: String?,
    playWhenReady: Boolean
) {
    if (credentialsId == null) {
        Timber.e("VideoPlayerManager: No credentials for SMB")
        playerCallback.showError("No credentials found")
        return
    }

    val credentials = credentialsRepository.getByCredentialId(credentialsId)
    if (credentials == null) {
        Timber.e("VideoPlayerManager: Credentials not found in DB")
        playerCallback.showError("Credentials not found")
        return
    }

    Timber.d("VideoPlayerManager: Playing SMB video - server=${credentials.server}, path=$path")
    releasePlayer()

    // Parse path to extract the correct share name (may differ from credentials default share)
    val pathInfo = SmbPathUtils.parseSmbPath(path)

    // Use share from path if available, otherwise fall back to credentials share
    val targetShare = pathInfo?.connectionInfo?.shareName?.ifEmpty { null } ?: credentials.shareName ?: ""
    val targetRemotePath = pathInfo?.remotePath ?: ""

    if (targetShare.isEmpty()) {
        Timber.e("VideoPlayerManager: Could not determine share name for SMB playback")
        playerCallback.showError("Invalid SMB path: missing share name")
        return
    }

    Timber.d("VideoPlayerManager: Resolved SMB target - share='$targetShare', remotePath='$targetRemotePath'")

    val connectionInfo = SmbConnectionInfo(
        server = credentials.server,
        shareName = targetShare,
        username = credentials.username,
        password = credentials.password,
        domain = credentials.domain,
        port = credentials.port
    )

    // Activate video-player priority mode to suppress thumbnail pre-fetching while streaming
    val resourceKey = "smb://${credentials.server}:${credentials.port}"
    activeResourceKey = resourceKey
    ConnectionThrottleManager.activateVideoPlayerMode(resourceKey)

    val dataSourceFactory = SmbDataSourceFactory(smbClient, connectionInfo)

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

    // Encode path segments (preserve @ and other special chars that are valid in share paths)
    val encodedPath = targetRemotePath.split("/")
        .joinToString("/") { segment -> Uri.encode(segment, "@") }

    // URI path: /shareName/path/to/file
    val finalUriPath = "/$targetShare/$encodedPath"

    val smbUri = Uri.Builder()
        .scheme("smb")
        .authority(credentials.server)
        .encodedPath(finalUriPath)
        .build()

    Timber.d("VideoPlayerManager: Created SMB URI=$smbUri")

    val mediaItem = createMediaItem(smbUri.toString(), path)
    exoPlayer?.setMediaItem(mediaItem)
    exoPlayer?.prepare()
    exoPlayer?.playWhenReady = playWhenReady

    Timber.i("VideoPlayerManager: SMB video setup complete")
}
