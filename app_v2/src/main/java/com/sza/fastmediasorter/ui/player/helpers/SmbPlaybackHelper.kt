package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.datasource.SmbDataSourceFactory
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormat
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import com.sza.fastmediasorter.utils.SmbPathUtils
import timber.log.Timber

/**
 * SMB network-stream playback.
 *
 * Extension function on [VideoPlayerManager] - extracted to a separate file to reduce
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
        playerCallback.showError(context.getString(R.string.player_credentials_missing))
        return
    }

    val credentials = credentialsRepository.getByCredentialId(credentialsId)
    if (credentials == null) {
        Timber.e("VideoPlayerManager: Credentials not found in DB")
        playerCallback.showError(context.getString(R.string.player_credentials_missing))
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
        playerCallback.showError(context.getString(R.string.error_invalid_path))
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

    val dataSourceFactory = SmbDataSourceFactory(smbClient, connectionInfo, context)
    val isAudio = path.substringAfterLast('.', "").lowercase() in MediaTypeUtils.AUDIO_EXTENSIONS

    val loadControl = PrefetchLoadControlFactory.build(
        plan = activePrefetchPlan,
        useCloudDefaults = false,
        isAudio = isAudio,
        useNetworkAudioDefaults = isAudio,
        tag = "smb"
    )

    val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

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

    val routeHint = NetworkPlaybackContainerHint.fromPath(path)
    Timber.d("VideoPlayerManager: SMB routeHint=$routeHint path=$path")
    val format = when (routeHint) {
        NetworkPlaybackContainerHint.M2TS_TS_CANDIDATE ->
            (dataSourceFactory as DataSource.Factory).detectTsFormatSuspend(smbUri)
        NetworkPlaybackContainerHint.DVD_PS_VOB -> TsPacketFormat.UNKNOWN
        else -> TsPacketFormat.UNKNOWN
    }

    // S0865: detectTsFormatSuspend above may have let a concurrent playVideo() call race through
    // releasePlayer()+assignment while this coroutine was suspended - release any such orphan
    // before this call overwrites the field.
    releaseIfRacedPlayer()

    Timber.d("S1056: SMB renderers factory applied")
    exoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(createPlaybackRenderersFactory(context))
        .setMediaSourceFactory(
            (dataSourceFactory as DataSource.Factory).buildBdTsMediaSourceFactory(format)
        )
        .setLoadControl(loadControl)
        .setAudioAttributes(audioAttributes, true)
        .build()

    exoPlayer?.addListener(loadControl)
    activeExtraPlayerListener = loadControl
    exoPlayer?.addListener(playerListener)
    currentPlayerView?.player = exoPlayer

    val mediaItem = createMediaItem(smbUri.toString(), path)
    exoPlayer?.setMediaItem(mediaItem)
    exoPlayer?.prepare()
    exoPlayer?.playWhenReady = playWhenReady

    Timber.i("VideoPlayerManager: SMB video setup complete")
}
