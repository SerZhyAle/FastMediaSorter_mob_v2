package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.datasource.FtpDataSourceFactory
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormat
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * FTP network-stream playback.
 *
 * Extension function on [VideoPlayerManager] — extracted to a separate file to reduce
 * per-file CFG complexity for the Kotlin compiler (avoids GC overhead during parallel
 * flavor compilation of the original 1 700-line VideoPlayerManager.kt).
 *
 * FTP-specific buffer tuning: lower initial/rebuffer thresholds than SMB/SFTP because
 * FTP connections tend to have higher latency on first read but moderate sustained throughput.
 */
internal suspend fun VideoPlayerManager.playFtpVideo(
    path: String,
    credentialsId: String?,
    playWhenReady: Boolean
) {
    if (credentialsId == null) {
        Timber.e("VideoPlayerManager: No credentials for FTP")
        playerCallback.showError(context.getString(R.string.player_credentials_missing))
        return
    }

    val credentials = credentialsRepository.getByCredentialId(credentialsId)
    if (credentials == null) {
        Timber.e("VideoPlayerManager: Credentials not found in DB")
        playerCallback.showError(context.getString(R.string.player_credentials_missing))
        return
    }

    Timber.d("VideoPlayerManager: Playing FTP video - server=${credentials.server}")
    releasePlayer()

    // Activate video-player priority mode to suppress thumbnail pre-fetching while streaming
    val resourceKey = "ftp://${credentials.server}:${credentials.port}"
    activeResourceKey = resourceKey
    ConnectionThrottleManager.activateVideoPlayerMode(resourceKey)

    val dataSourceFactory = FtpDataSourceFactory(
        ftpClient,
        credentials.server,
        credentials.port,
        credentials.username,
        credentials.password,
        context
    )

    val loadControl = PrefetchLoadControlFactory.build(
        plan = activePrefetchPlan,
        useCloudDefaults = false,
        tag = "ftp"
    )

    val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    // Construct FTP URI with properly encoded path segments
    val parsedUri = PathUtils.safeParseUri(path)
    val encodedPath = parsedUri.path?.split("/")
        ?.joinToString("/") { segment ->
            if (segment.isEmpty()) "" else Uri.encode(segment, "@")
        } ?: ""

    val ftpUri = Uri.Builder()
        .scheme("ftp")
        .authority(parsedUri.authority)
        .encodedPath(encodedPath)
        .build()

    Timber.d("VideoPlayerManager: FTP URI=$ftpUri")

    val routeHint = NetworkPlaybackContainerHint.fromPath(path)
    Timber.d("VideoPlayerManager: FTP routeHint=$routeHint path=$path")
    val format = if (routeHint == NetworkPlaybackContainerHint.M2TS_TS_CANDIDATE) {
        (dataSourceFactory as DataSource.Factory).detectTsFormatSuspend(ftpUri)
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

    val mediaItem = createMediaItem(ftpUri.toString(), path)
    exoPlayer?.setMediaItem(mediaItem)
    exoPlayer?.prepare()
    exoPlayer?.playWhenReady = playWhenReady

    Timber.i("VideoPlayerManager: FTP video setup complete")
}
