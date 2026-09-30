package com.sza.fastmediasorter.ui.player

import android.content.Context
import androidx.lifecycle.Lifecycle
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.memory.MemoryProbe
import com.sza.fastmediasorter.core.memory.MemoryProfileCoordinator
import com.sza.fastmediasorter.core.playback.RecentDecoderFailureTracker
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpEndpointResolver
import com.sza.fastmediasorter.domain.player.StreamProtocolSupport
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.usecase.streams.StreamTrackPreferenceUseCase
import com.sza.fastmediasorter.ui.player.helpers.PanelStereoSingleEyeNotifier
import dagger.Lazy

data class VideoPlayerHostDependencies(
    val context: Context,
    val lifecycle: Lifecycle,
    val playerCallback: VideoPlayerManager.PlayerCallback,
    val panelStereoSingleEyeNotifier: PanelStereoSingleEyeNotifier,
    val memoryProbe: MemoryProbe,
    val memoryProfileCoordinator: MemoryProfileCoordinator,
    val decoderFailureTracker: RecentDecoderFailureTracker,
    val remoteSourceGate: RemoteSourceAvailabilityGate,
    val statsSink: StatsSink,
    val streamProtocolSupport: StreamProtocolSupport,
)

// S3834: Lazy because VideoPlayerManager is built on the first player open, including for local
// media that never touches a network client or the credentials store.
data class VideoPlayerNetworkDependencies(
    val credentialsRepository: Lazy<NetworkCredentialsRepository>,
    val smbClient: Lazy<SmbClient>,
    val sftpClient: Lazy<SftpClient>,
    val endpointResolver: SftpEndpointResolver,
    val ftpClient: Lazy<FtpClient>,
    val googleDriveClient: Lazy<GoogleDriveRestClient>,
    val oneDriveClient: Lazy<OneDriveRestClient>,
    val dropboxClient: Lazy<DropboxClient>,
)

data class VideoPlayerStoreDependencies(
    val playbackPositionRepository: PlaybackPositionRepository,
    val settingsRepository: SettingsRepository,
    // S1144 (ADR-6): VideoPlayerManager is built by hand, outside the Hilt graph, so the per-channel
    // track preference travels the same bundle route as the two repositories above.
    val streamTrackPreferenceUseCase: StreamTrackPreferenceUseCase,
)
