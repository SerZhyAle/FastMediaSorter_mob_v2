package com.sza.fastmediasorter.wear.ui.phonecamera

import android.net.Uri
import com.sza.fastmediasorter.wear.data.wear.CameraSessionCommandSender
import com.sza.fastmediasorter.wear.domain.model.CameraLensDto
import com.sza.fastmediasorter.wear.domain.model.PhoneCameraFailure
import com.sza.fastmediasorter.wear.domain.model.PhoneCameraSessionState
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannel
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannelKind
import com.sza.fastmediasorter.wear.domain.repository.PhoneCameraSessionHolder
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkChannelMonitor
import com.sza.fastmediasorter.wear.domain.usecase.EvaluateStreamStartUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareEphemeralStreamPlaybackUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * S2551: the watch must judge its own link before it asks the phone to open a camera.
 *
 * The failure this guards costs nothing on the watch and everything on the phone: a request sent
 * from a Bluetooth-carried link starts a camera and an encoder on the phone that nothing here can
 * ever reach, and the only symptom the owner sees is a spinner and a warm phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneCameraViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val holder = PhoneCameraSessionHolder()
    private val sender = mockk<CameraSessionCommandSender>(relaxed = true)
    private val prepare = PrepareEphemeralStreamPlaybackUseCase(SelectedMediaManager(), PlaybackSetManager())

    @Before
    fun install() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @After
    fun release() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
    }

    @Test
    fun `a bluetooth link is refused on the watch and nothing is sent`() = runTest(dispatcher) {
        val viewModel = viewModelOn(channelOf(WearNetworkChannelKind.BLUETOOTH, WIDE_KBPS))

        viewModel.start()

        assertEquals(
            PhoneCameraSessionState.Refused(PhoneCameraFailure.NOT_ON_WIFI),
            holder.state.value
        )
        verify(exactly = 0) { sender.start() }
    }

    @Test
    fun `a narrow wifi link says so rather than blaming wifi being off`() = runTest(dispatcher) {
        val viewModel = viewModelOn(channelOf(WearNetworkChannelKind.WIFI, NARROW_KBPS))

        viewModel.start()

        assertEquals(
            PhoneCameraSessionState.Refused(PhoneCameraFailure.NARROW_LINK),
            holder.state.value
        )
    }

    @Test
    fun `a wide wifi link sends the start command`() = runTest(dispatcher) {
        val viewModel = viewModelOn(channelOf(WearNetworkChannelKind.WIFI, WIDE_KBPS))

        viewModel.start()

        verify(exactly = 1) { sender.start() }
    }

    @Test
    fun `a live session exposes a playback target and a refusal does not`() = runTest(dispatcher) {
        val viewModel = viewModelOn(channelOf(WearNetworkChannelKind.WIFI, WIDE_KBPS))

        holder.markLive(
            PhoneCameraSessionState.Live(
                url = CAMERA_URL,
                lenses = listOf(CameraLensDto(id = "0", labelKey = "lens_back", facing = "BACK")),
                activeLensId = "0"
            )
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull("a live session did not reach the player", viewModel.state.value.playbackTarget)

        holder.markRefused(PhoneCameraFailure.STOPPED)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull("a stopped session still pointed at a player", viewModel.state.value.playbackTarget)
    }

    private fun viewModelOn(channel: WearNetworkChannel): PhoneCameraViewModel = PhoneCameraViewModel(
        holder = holder,
        sender = sender,
        evaluateStreamStart = EvaluateStreamStartUseCase(monitorOf(channel)),
        prepareEphemeralPlayback = prepare
    )

    private fun monitorOf(channelValue: WearNetworkChannel): WearNetworkChannelMonitor =
        object : WearNetworkChannelMonitor {
            override val channel: StateFlow<WearNetworkChannel> = MutableStateFlow(channelValue)
        }

    private fun channelOf(kind: WearNetworkChannelKind, downstreamKbps: Int): WearNetworkChannel =
        WearNetworkChannel(
            kind = kind,
            downstreamKbps = downstreamKbps,
            upstreamKbps = null,
            isMetered = false,
            isValidated = true
        )

    private companion object {
        const val WIDE_KBPS = 5000
        const val NARROW_KBPS = 300
        const val CAMERA_URL = "rtsp://192.168.1.42:8554/camera"
    }
}
