package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.helpers.playSftpVideo
import com.sza.fastmediasorter.ui.player.helpers.playStreamVideo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Verifies that replacing a player cannot cancel the coroutine performing the replacement. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoPlayerManagerLoadCancellationTest {

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
                credentialsRepository = dagger.Lazy { mockk(relaxed = true) },
                smbClient = dagger.Lazy { mockk(relaxed = true) },
                sftpClient = dagger.Lazy { mockk(relaxed = true) },
                endpointResolver = mockk(relaxed = true),
                ftpClient = dagger.Lazy { mockk(relaxed = true) },
                googleDriveClient = dagger.Lazy { mockk(relaxed = true) },
                oneDriveClient = dagger.Lazy { mockk(relaxed = true) },
                dropboxClient = dagger.Lazy { mockk(relaxed = true) },
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
        if (::manager.isInitialized) manager.managerScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `stream setup survives release and a subsequent suspension`() = runTest {
        every { manager.streamProtocolSupport.createRtspMediaSource(any(), any()) } returns null
        var resumed = false
        val load = manager.managerScope.launch {
            manager.playStreamVideo("rtsp://example.invalid/live")
            yield()
            resumed = true
        }
        trackLoad(load)

        runCurrent()

        assertTrue("Stream setup must reach the next suspension without self-cancelling", resumed)
        assertFalse(load.isCancelled)
    }

    @Test
    fun `SFTP setup reaches endpoint suspension and external release cancels it`() = runTest {
        val credentials = mockk<NetworkCredentialsEntity>(relaxed = true)
        every { credentials.server } returns "example.invalid"
        coEvery { manager.credentialsRepository.getByCredentialId("credentials") } returns credentials
        val entered = CompletableDeferred<Unit>()
        coEvery { manager.endpointResolver.resolve(any(), any()) } coAnswers {
            yield()
            entered.complete(Unit)
            awaitCancellation()
        }
        val load = manager.managerScope.launch {
            manager.playSftpVideo("sftp://example.invalid/video.mp4", "credentials", true)
        }
        trackLoad(load)

        runCurrent()
        assertTrue("SFTP setup must survive cleanup and suspend during endpoint lookup", entered.isCompleted)
        assertTrue(load.isActive)

        manager.releasePlayer()
        runCurrent()
        assertTrue("External teardown must still cancel the suspended load", load.isCancelled)
    }

    @Test
    fun `default release cancels a load before it can resume`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var resumed = false
        val load = manager.managerScope.launch {
            gate.await()
            resumed = true
        }
        trackLoad(load)
        runCurrent()

        manager.releasePlayer()
        gate.complete(Unit)
        runCurrent()

        assertTrue(load.isCancelled)
        assertFalse(resumed)
    }

    private fun trackLoad(load: Job) {
        // Dispatch is bypassed to isolate teardown from preflight memory and network side effects.
        VideoPlayerManager::class.java.getDeclaredField("activeLoadJob").apply {
            isAccessible = true
            set(manager, load)
        }
    }
}
