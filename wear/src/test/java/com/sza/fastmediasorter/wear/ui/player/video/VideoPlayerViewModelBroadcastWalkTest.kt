package com.sza.fastmediasorter.wear.ui.player.video

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.exchange.WearDirectoryBroadcast
import com.sza.fastmediasorter.wear.data.network.FakeWearNetworkChannelMonitor
import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_STREAM
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastRoute
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannel
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannelKind
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.WalkWearBroadcastEndpointsUseCase
import com.sza.fastmediasorter.wear.ui.player.common.PlayerFileOperationsManager
import com.sza.fastmediasorter.wear.ui.player.helpers.StreamPlaybackSessionFactory
import com.sza.fastmediasorter.wear.ui.player.helpers.StreamPlaybackSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * S4629: a video broadcast that stops answering on one endpoint plays on the next one, and a changed network
 * resolves it from the top (contract LIVE-BROADCAST 12, DEVICE-EXCHANGE 7.7) - the audio player's S4610 walk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelBroadcastWalkTest {

    private val dispatcher = StandardTestDispatcher()
    private val record = WearDirectoryBroadcast(
        broadcastId = "b1",
        deviceId = "",
        title = "Cam",
        mode = "VIDEO",
        descriptor = null,
        credentials = null,
    )
    private val lanRoute = WearBroadcastRoute(record, 0)
    private val relayRoute = WearBroadcastRoute(record, 1)
    private val selectedMediaManager = SelectedMediaManager()
    private val playbackSetManager = PlaybackSetManager()
    private val walk: WalkWearBroadcastEndpointsUseCase = mockk()
    private val network = FakeWearNetworkChannelMonitor(channel(WearNetworkChannelKind.WIFI))
    private val listener = slot<Player.Listener>()
    private val exoPlayer: ExoPlayer = mockk(relaxed = true) {
        every { addListener(capture(listener)) } just runs
    }
    private val session: StreamPlaybackSessionManager = mockk(relaxed = true) {
        every { prepare(any()) } returns true
    }
    private val sessionFactory: StreamPlaybackSessionFactory = mockk {
        every { create(any(), any()) } returns session
    }

    // A relaxed StateFlow never returns from collect, which the view model reads as a Nothing value.
    private val fileOperations: PlayerFileOperationsManager = mockk(relaxed = true) {
        every { operationResult } returns MutableStateFlow(null)
    }
    private val preferencesRepository: WearPreferencesRepository = mockk(relaxed = true) {
        every { videoScaleMode } returns flowOf(VideoScaleMode.FIT)
        every { isSlideshowEnabled } returns flowOf(false)
        every { isShuffleEnabled } returns flowOf(false)
        every { isAnimationsDisabled } returns flowOf(false)
    }
    private val context: Context = mockk(relaxed = true) {
        every { getString(R.string.wear_state_error) } returns ERROR_TEXT
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        val file = WearMediaFile(FILE_ID, "Cam", mockk(relaxed = true), "video/*", 0L, 0L)
        playbackSetManager.publish(listOf(file), startIndex = 0)
        selectedMediaManager.selectFile(
            file = file,
            isNetworkSource = true,
            streamUri = LAN_URL,
            sourceId = SOURCE_ID_STREAM,
            isDirectStream = true,
            broadcastRoute = lanRoute,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed LAN endpoint moves the player to the relay`() = runTest(dispatcher) {
        coEvery { walk(lanRoute, false) } returns WearBroadcastPlayback.Playable(RELAY_URL, "Cam", true, relayRoute)
        val viewModel = obtain(ViewModelStore())
        advanceUntilIdle()

        listener.captured.onPlayerError(networkError())
        advanceUntilIdle()

        coVerify(exactly = 1) { walk(lanRoute, false) }
        verify { Uri.parse(RELAY_URL) }
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `an exhausted walk shows the screen's error text`() = runTest(dispatcher) {
        coEvery { walk(lanRoute, false) } returns null
        val viewModel = obtain(ViewModelStore())
        advanceUntilIdle()

        listener.captured.onPlayerError(networkError())
        advanceUntilIdle()

        assertEquals(ERROR_TEXT, viewModel.uiState.value.error)
    }

    @Test
    fun `a screen in the background shows the error without walking`() = runTest(dispatcher) {
        val viewModel = obtain(ViewModelStore())
        advanceUntilIdle()

        viewModel.onHostStopped()
        listener.captured.onPlayerError(networkError())
        advanceUntilIdle()

        coVerify(exactly = 0) { walk(any(), any()) }
        assertTrue(viewModel.uiState.value.error.orEmpty().startsWith("Playback error"))
    }

    @Test
    fun `a screen that returned to the front walks again`() = runTest(dispatcher) {
        coEvery { walk(lanRoute, false) } returns WearBroadcastPlayback.Playable(RELAY_URL, "Cam", true, relayRoute)
        val viewModel = obtain(ViewModelStore())
        advanceUntilIdle()

        viewModel.onHostStopped()
        viewModel.onHostStarted()
        listener.captured.onPlayerError(networkError())
        advanceUntilIdle()

        coVerify(exactly = 1) { walk(lanRoute, false) }
    }

    @Test
    fun `a change from Wi-Fi to LTE resolves the playing broadcast from the top`() = runTest(dispatcher) {
        every { exoPlayer.playWhenReady } returns true
        coEvery { walk(lanRoute, true) } returns WearBroadcastPlayback.Playable(RELAY_URL, "Cam", true, relayRoute)
        obtain(ViewModelStore())
        advanceUntilIdle()

        network.set(WearNetworkChannel.NONE)
        advanceUntilIdle()
        network.set(channel(WearNetworkChannelKind.CELLULAR))
        advanceUntilIdle()

        coVerify(exactly = 1) { walk(lanRoute, true) }
    }

    @Test
    fun `a link lost and found again on the same kind reloads nothing`() = runTest(dispatcher) {
        every { exoPlayer.playWhenReady } returns true
        obtain(ViewModelStore())
        advanceUntilIdle()

        network.set(WearNetworkChannel.NONE)
        advanceUntilIdle()
        network.set(channel(WearNetworkChannelKind.WIFI))
        advanceUntilIdle()

        coVerify(exactly = 0) { walk(any(), any()) }
    }

    private fun networkError() = PlaybackException(
        "lost",
        IOException("timeout"),
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    )

    private fun channel(kind: WearNetworkChannelKind) = WearNetworkChannel(
        kind = kind,
        downstreamKbps = null,
        upstreamKbps = null,
        isMetered = false,
        isValidated = true,
    )

    private fun CoroutineScope.obtain(store: ViewModelStore): VideoPlayerViewModel {
        val applicationScope = this
        val factory = viewModelFactory {
            initializer {
                VideoPlayerViewModel(
                    mediaRepository = mockk(relaxed = true),
                    selectedMediaManager = selectedMediaManager,
                    playbackSetManager = playbackSetManager,
                    preferencesRepository = preferencesRepository,
                    downloadNetworkFile = mockk(relaxed = true),
                    endPhoneCameraSessionOnStreamError = mockk(relaxed = true),
                    exoPlayer = exoPlayer,
                    publishPlaybackStateUseCase = mockk(relaxed = true),
                    streamPlaybackSessionFactory = sessionFactory,
                    toggleFavoriteUseCase = mockk(relaxed = true),
                    toggleStreamPinUseCase = mockk(relaxed = true),
                    nowPlayingRepository = mockk(relaxed = true),
                    fileOperations = fileOperations,
                    castManager = mockk(relaxed = true),
                    batteryWarningStore = mockk(relaxed = true),
                    walkBroadcastEndpoints = walk,
                    networkChannelMonitor = network,
                    savedStateHandle = SavedStateHandle(mapOf("fileId" to FILE_ID)),
                    context = context,
                    applicationScope = applicationScope,
                )
            }
        }
        return ViewModelProvider(store, factory)[VideoPlayerViewModel::class.java]
    }

    private companion object {
        const val FILE_ID = 42L
        const val LAN_URL = "http://10.0.0.2:8080/"
        const val RELAY_URL = "http://127.0.0.1:4000/v2/b/b1/stream"
        const val ERROR_TEXT = "failed"
    }
}
