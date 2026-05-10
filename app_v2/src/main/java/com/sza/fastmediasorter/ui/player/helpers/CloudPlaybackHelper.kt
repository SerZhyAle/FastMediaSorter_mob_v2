package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.data.cloud.datasource.CloudDataSourceFactory
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormat
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * Cloud (Google Drive / OneDrive / Dropbox) network-stream playback.
 *
 * Extension function on [VideoPlayerManager] — extracted to a separate file to reduce
 * per-file CFG complexity for the Kotlin compiler (avoids GC overhead during parallel
 * flavor compilation of the original 1 700-line VideoPlayerManager.kt).
 *
 * Cloud buffer tuning: larger min/max buffers than local/SMB because cloud throughput
 * is variable and HTTP HEAD/redirect latency is higher on first request.
 */
internal suspend fun VideoPlayerManager.playCloudVideo(path: String, playWhenReady: Boolean) {
    val fileId = path.substringAfterLast("/")
    if (fileId.isEmpty() || fileId == path) {
        Timber.e("VideoPlayerManager: Invalid cloud path, no fileId")
        playerCallback.showError(context.getString(R.string.error_invalid_path))
        return
    }

    Timber.d("VideoPlayerManager: Playing cloud video - fileId=$fileId")
    releasePlayer()

    val clients = mapOf(
        "googledrive" to googleDriveClient,
        "onedrive" to oneDriveClient,
        "dropbox" to dropboxClient
    )
    val dataSourceFactory = CloudDataSourceFactory(clients)

    val loadControl = PrefetchLoadControlFactory.build(
        plan = activePrefetchPlan,
        useCloudDefaults = true,
        tag = "cloud"
    )

    val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    val cloudUri = PathUtils.safeParseUri(path)
    val routeHint = NetworkPlaybackContainerHint.fromPath(path)
    Timber.d("VideoPlayerManager: Cloud routeHint=$routeHint path=$path")
    val tsFormat: TsPacketFormat = if (routeHint == NetworkPlaybackContainerHint.M2TS_TS_CANDIDATE) {
        CloudDataSourceFactory(clients).detectTsFormatSuspend(cloudUri)
    } else {
        TsPacketFormat.STANDARD_188
    }
    Timber.d("S0054: CloudPlaybackHelper.playCloudVideo routeHint=$routeHint tsFormat=$tsFormat path=$path")

    exoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            (dataSourceFactory as DataSource.Factory).buildBdTsMediaSourceFactory(tsFormat)
        )
        .setLoadControl(loadControl)
        .setAudioAttributes(audioAttributes, true)
        .build()

    exoPlayer?.addListener(loadControl)
    exoPlayer?.addListener(playerListener)
    currentPlayerView?.player = exoPlayer

    val mediaItem = createMediaItem(cloudUri.toString(), path)
    exoPlayer?.setMediaItem(mediaItem)
    exoPlayer?.prepare()
    exoPlayer?.playWhenReady = playWhenReady

    Timber.i("VideoPlayerManager: Cloud video setup complete - fileId=$fileId")
}
