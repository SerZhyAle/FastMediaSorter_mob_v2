package com.sza.fastmediasorter.wear.ui.player.audio

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
import com.sza.fastmediasorter.wear.data.exchange.WearDirectoryBroadcast
import com.sza.fastmediasorter.wear.data.network.FakeWearNetworkChannelMonitor
import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_STREAM
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastRoute
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannel
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannelKind
import com.sza.fastmediasorter.wear.domain.playback.WearAfterEndAnswer
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundSessionState
import com.sza.fastmediasorter.wear.domain.playback.WearStreamFailure
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
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * S4610: a broadcast that stops answering on one endpoint plays on the next one, and a changed network resolves it
 * from the top (contract LIVE-BROADCAST 12, DEVICE-EXCHANGE 7.7).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerViewModelBroadcastWalkTest {

    private val dispatcher = StandardTestDispatcher()
    private val record = WearDirectoryBroadcast(
        broadcastId = "b1",
        deviceId = "",
        title = "Mic",
        mode = "AUDIO_ONLY",
        descriptor = null,
        credentials = null,
    )
    private val lanRoute = WearBroadcastRoute(record, 0)
    private val relayRoute = WearBroadcastRoute(record, 1)
    private val selectedMediaManager = SelectedMediaManager()
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
    private val fileOperations: PlayerFileOperationsManager = mockk(relaxed = true) {
        every { operationResult } returns MutableStateFlow(null)
    }
    private val preferencesRepository: WearPreferencesRepository = mockk(relaxed = true) {
        every { backgroundPlaybackEnabled } returns flowOf(false)
        every { isShuffleEnabled } returns flowOf(false)
    }
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        selectedMediaManager.selectFile(
            file = WearMediaFile(FILE_ID, "Mic", mockk(relaxed = true), "audio/*", 0L, 0L),
            isNetworkSource = true,
            streamUri = LAN_URL,
            sourceId = SOURCE_ID_STREAM,
            isDirectStream = true,
            broadcastRoute = lanRoute,
        )
        coEvery { walk.afterEnd(any()) } returns WearAfterEndAnswer.NO_ANSWER
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed LAN endpoint moves the player to the relay`() = runTest(dispatcher) {
        coEvery { walk(lanRoute, false) } returns WearBroadcastPlayback.Playable(RELAY_URL, "Mic", false, relayRoute)
        val viewModel = obtain(ViewModelStore())
        advanceUntilIdle()

        listener.captured.onPlayerError(networkError())
        advanceUntilIdle()

        coVerify(exactly = 1) { walk(lanRoute, false) }
        verify { Uri.parse(RELAY_URL) }
        assertNull(viewModel.uiState.value.streamFailure)
    }

    @Test
    fun `an exhausted walk shows the failure screen`() = runTest(dispatcher) {
        coEvery { walk(lanRoute, false) } returns null
        val viewModel = obtain(ViewModelStore())
        advanceUntilIdle()

        listener.captured.onPlayerError(networkError())
        advanceUntilIdle()

        assertEquals(WearStreamFailure.FAILED, viewModel.uiState.value.streamFailure)
    }

    @Test
    fun `an exhausted walk whose one reconnect is refused shows the ended screen`() = runTest(dispatcher) {
        coEvery { walk(lanRoute, false) } returns null
        coEvery { walk.afterEnd(record) } returns WearAfterEndAnswer.ENDED
        val viewModel = obtain(ViewModelStore())
        advanceUntilIdle()

        listener.captured.onPlaybackStateChanged(Player.STATE_READY)
        listener.captured.onPlayerError(networkError())
        advanceUntilIdle()

        coVerify(exactly = 1) { walk.afterEnd(record) }
        assertEquals(WearStreamFailure.ENDED, viewModel.uiState.value.streamFailure)
    }

    @Test
    fun `an exhausted walk after play with no answer stays a failure`() = runTest(dispatcher) {
        coEvery { walk(lanRoute, false) } returns null
        val viewModel = obtain(ViewModelStore())
        advanceUntilIdle()

        listener.captured.onPlaybackStateChanged(Player.STATE_READY)
        listener.captured.onPlayerError(networkError())
        advanceUntilIdle()

        coVerify(exactly = 1) { walk.afterEnd(record) }
        assertEquals(WearStreamFailure.FAILED, viewModel.uiState.value.streamFailure)
    }

    @Test
    fun `a screen in the background shows the failure without walking`() = runTest(dispatcher) {
        val viewModel = obtain(ViewModelStore())
        advanceUntilIdle()

        viewModel.onHostStopped()
        listener.captured.onPlayerError(networkError())
        advanceUntilIdle()

        coVerify(exactly = 0) { walk(any(), any()) }
        assertEquals(WearStreamFailure.FAILED, viewModel.uiState.value.streamFailure)
    }

    @Test
    fun `a change from Wi-Fi to LTE resolves the playing broadcast from the top`() = runTest(dispatcher) {
        every { exoPlayer.playWhenReady } returns true
        coEvery { walk(lanRoute, true) } returns WearBroadcastPlayback.Playable(RELAY_URL, "Mic", false, relayRoute)
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

    private fun CoroutineScope.obtain(store: ViewModelStore): AudioPlayerViewModel {
        val applicationScope = this
        val factory = viewModelFactory {
            initializer {
                AudioPlayerViewModel(
                    mediaRepository = mockk(relaxed = true),
                    selectedMediaManager = selectedMediaManager,
                    playbackSetManager = mockk(relaxed = true),
                    downloadNetworkFile = mockk(relaxed = true),
                    exoPlayer = exoPlayer,
                    publishPlaybackStateUseCase = mockk(relaxed = true),
                    context = context,
                    toggleFavoriteUseCase = mockk(relaxed = true),
                    toggleStreamPinUseCase = mockk(relaxed = true),
                    resolveAlbumArt = mockk(relaxed = true),
                    preferencesRepository = preferencesRepository,
                    streamPlaybackSessionFactory = sessionFactory,
                    nowPlayingRepository = mockk(relaxed = true),
                    backgroundSessionState = WearBackgroundSessionState(),
                    fileOperations = fileOperations,
                    castManager = mockk(relaxed = true),
                    walkBroadcastEndpoints = walk,
                    networkChannelMonitor = network,
                    applicationScope = applicationScope,
                    savedStateHandle = SavedStateHandle(mapOf("fileId" to FILE_ID)),
                )
            }
        }
        return ViewModelProvider(store, factory)[AudioPlayerViewModel::class.java]
    }

    private companion object {
        const val FILE_ID = 42L
        const val LAN_URL = "http://10.0.0.2:8080/"
        const val RELAY_URL = "http://127.0.0.1:4000/v2/b/b1/stream"
    }
}
