package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.core.xr.StartVrPlaybackUseCase
import com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.SearchAudioCoverUseCase
import com.sza.fastmediasorter.domain.usecase.SearchLyricsUseCase
import com.sza.fastmediasorter.domain.usecase.streams.StreamTrackPreferenceUseCase
import dagger.Lazy
import javax.inject.Inject

/**
 * The repository half of [PlayerHostFactory], grouped so the factory's own constructor stays inside the
 * parameter-count budget - the same split `StandaloneHostDependencies` makes for the standalone family.
 *
 * [credentials] keeps its `Lazy` wrapping: the host had it lazy, and resolving it here would create the
 * repository earlier than the activity ever did (CLAUDE.md Rule 18).
 */
class PlayerHostRepositories @Inject constructor(
    val credentials: Lazy<NetworkCredentialsRepository>,
    val settings: SettingsRepository,
    val resource: ResourceRepository,
    val playbackPosition: PlaybackPositionRepository,
    val audioMetadataCache: AudioMetadataCacheRepository,
)

/**
 * S1637: owns the non-cluster domain dependencies `PlayerActivity` used to field-inject, so the host
 * declares none of them itself. Companion to [ImageEditFactory], which owns the image/GIF edit cluster.
 *
 * A supplier of objects rather than a builder of managers, because that is what the use sites are: every
 * one of these dependencies is read once or twice and handed straight to a constructor in
 * `PlayerManagerInitializer`, `PlayerViewerFactory` or a player helper. `StandaloneHostFactory` builds
 * managers instead, and that difference is deliberate - it serves four small hosts that build the same
 * few managers, while this one serves a single host whose construction already lives in a named
 * initializer that must not move here.
 *
 * The repositories are re-exposed flat rather than through [repositories], so a call site names one hop
 * (`playerHostFactory.settingsRepository`) instead of two - the grouping exists for the parameter budget,
 * not as a shape callers should have to know.
 *
 * Unscoped on purpose, mirroring `StandaloneHostFactory`: what it hands out must not outlive the host
 * that asked for it.
 */
class PlayerHostFactory @Inject constructor(
    private val repositories: PlayerHostRepositories,
    val startVrPlayback: StartVrPlaybackUseCase,
    val streamTrackPreference: StreamTrackPreferenceUseCase,
    val searchAudioCover: SearchAudioCoverUseCase,
    val downloadNetworkFile: DownloadNetworkFileUseCase,
    val searchLyrics: SearchLyricsUseCase,
    val fileOperation: FileOperationUseCase,
) {
    val credentialsRepository: Lazy<NetworkCredentialsRepository> get() = repositories.credentials
    val settingsRepository: SettingsRepository get() = repositories.settings
    val resourceRepository: ResourceRepository get() = repositories.resource
    val playbackPositionRepository: PlaybackPositionRepository get() = repositories.playbackPosition
    val audioMetadataCacheRepository: AudioMetadataCacheRepository get() = repositories.audioMetadataCache
}
