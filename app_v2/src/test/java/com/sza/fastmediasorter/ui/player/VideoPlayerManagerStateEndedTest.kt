package com.sza.fastmediasorter.ui.player

import androidx.media3.common.Player
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S0029: ExoPlayer.STATE_ENDED must clear the saved position via
 * [PlaybackPositionRepository.markPlaybackCompleted] exactly once per file.
 *
 * Strategic-criterion case: 30 s file with last-known position 28 s → STATE_ENDED →
 * `markPlaybackCompleted("..", "playback-completed")` is called once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoPlayerManagerStateEndedTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockRepo: PlaybackPositionRepository
    private lateinit var mockSettings: SettingsRepository
    private lateinit var manager: VideoPlayerManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockRepo = mockk(relaxed = true)
        mockSettings = mockk<SettingsRepository>(relaxed = true).also {
            every { it.getSettings() } returns flowOf(AppSettings())
        }
        manager = VideoPlayerManager(
            hostDependencies = VideoPlayerHostDependencies(
                context = RuntimeEnvironment.getApplication(),
                lifecycle = mockk(relaxed = true),
                playerCallback = mockk(relaxed = true),
                panelStereoSingleEyeNotifier = mockk(relaxed = true),
                memoryProbe = mockk(relaxed = true),
                memoryProfileCoordinator = mockk(relaxed = true),
                decoderFailureTracker = mockk(relaxed = true),
                remoteSourceGate = mockk(relaxed = true),
                statsSink = mockk(relaxed = true),
                streamProtocolSupport = mockk(relaxed = true),
            ),
            networkDependencies = VideoPlayerNetworkDependencies(
                credentialsRepository = mockk(relaxed = true),
                smbClient = mockk(relaxed = true),
                sftpClient = mockk(relaxed = true),
                endpointResolver = mockk(relaxed = true),
                ftpClient = mockk(relaxed = true),
                googleDriveClient = mockk(relaxed = true),
                oneDriveClient = mockk(relaxed = true),
                dropboxClient = mockk(relaxed = true),
            ),
            storeDependencies = VideoPlayerStoreDependencies(
                playbackPositionRepository = mockRepo,
                settingsRepository = mockSettings,
                streamTrackPreferenceUseCase = mockk(relaxed = true),
            ),
        )
    }

    @After
    fun tearDown() {
        if (::manager.isInitialized) {
            manager.releasePlayer()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `STATE_ENDED for 30s file at 28s triggers markPlaybackCompleted exactly once`() = runTest {
        manager.currentFilePath = "smb://server/short_30s.mp4"

        manager.playerListener.onPlaybackStateChanged(Player.STATE_ENDED)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockRepo.markPlaybackCompleted("smb://server/short_30s.mp4", "playback-completed")
        }
    }

    @Test
    fun `repeated STATE_ENDED for the same file calls markPlaybackCompleted only once`() = runTest {
        manager.currentFilePath = "smb://server/short_30s.mp4"

        manager.playerListener.onPlaybackStateChanged(Player.STATE_ENDED)
        manager.playerListener.onPlaybackStateChanged(Player.STATE_ENDED)
        manager.playerListener.onPlaybackStateChanged(Player.STATE_ENDED)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockRepo.markPlaybackCompleted(any(), "playback-completed")
        }
    }

    @Test
    fun `STATE_ENDED with null currentFilePath is a no-op`() = runTest {
        manager.currentFilePath = null

        manager.playerListener.onPlaybackStateChanged(Player.STATE_ENDED)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            mockRepo.markPlaybackCompleted(any(), any())
        }
    }
}
