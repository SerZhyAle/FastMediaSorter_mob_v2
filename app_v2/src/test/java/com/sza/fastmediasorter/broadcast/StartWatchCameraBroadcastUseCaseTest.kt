package com.sza.fastmediasorter.broadcast

import android.content.Context
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.domain.model.WearCameraLensDto
import com.sza.fastmediasorter.domain.model.WearCameraRefusal
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3117: the answers this phone may give a watch that pressed Start.
 *
 * Asserted through the wire's own refusal constants rather than through the broadcast layer's
 * failures, because the refusal is what the watch turns into a sentence - a failure mapped to the
 * wrong constant shows the wearer a cause that is not theirs to act on.
 */
class StartWatchCameraBroadcastUseCaseTest {

    private val state = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
    private val controller = RecordingController(state)
    private val lenses = mockk<ListBroadcastCameraLensesUseCase>()

    private val useCase = StartWatchCameraBroadcastUseCase(
        controller = controller,
        listCameraLenses = lenses,
        context = mockk<Context>(relaxed = true)
    )

    @Test
    fun `refuses as unsupported when the build cannot broadcast`() = runTest {
        controller.available = false

        val outcome = useCase()

        assertEquals(WearCameraRefusal.NOT_SUPPORTED, (outcome as WatchCameraBroadcast.Refused).refusal)
        assertFalse(controller.started)
    }

    @Test
    fun `serves a broadcast that was already running without starting another`() = runTest {
        coEvery { lenses() } returns enumeration()
        state.value = live(url = "rtsp://10.0.0.2:8554/", activeLensId = "1")

        val outcome = useCase() as WatchCameraBroadcast.Serving

        assertEquals("rtsp://10.0.0.2:8554/", outcome.url)
        assertFalse(outcome.startedNow)
        assertFalse(controller.started)
    }

    @Test
    fun `serves the address of a session it started, naming the live lens`() = runTest {
        coEvery { lenses() } returns enumeration()
        controller.onStart = { state.value = live(url = "rtsp://10.0.0.2:8554/", activeLensId = "1") }

        val outcome = useCase() as WatchCameraBroadcast.Serving

        assertEquals("rtsp://10.0.0.2:8554/", outcome.url)
        assertEquals("1", outcome.lenses.activeLensId)
        assertEquals(listOf("0", "1"), outcome.lenses.lenses.map { it.id })
        assertTrue(outcome.startedNow)
    }

    @Test
    fun `reports an unreachable network as the wire's own no-network reason`() = runTest {
        controller.onStart = {
            state.value = BroadcastState.Failed(BroadcastFailure.NETWORK_UNAVAILABLE, "no route")
        }

        val outcome = useCase()

        assertEquals(WearCameraRefusal.NO_NETWORK, (outcome as WatchCameraBroadcast.Refused).refusal)
    }

    @Test
    fun `reports an encoder that would not open as a capture failure`() = runTest {
        controller.onStart = {
            state.value = BroadcastState.Failed(BroadcastFailure.ENCODER_UNAVAILABLE, "prepare failed")
        }

        val outcome = useCase()

        assertEquals(WearCameraRefusal.CAPTURE_FAILED, (outcome as WatchCameraBroadcast.Refused).refusal)
    }

    @Test
    fun `clears a failure left by an earlier session before waiting on this one`() = runTest {
        coEvery { lenses() } returns enumeration()
        state.value = BroadcastState.Failed(BroadcastFailure.CAPTURE_ERROR, "stale")
        controller.onStart = { state.value = live(url = "rtsp://10.0.0.2:8554/", activeLensId = null) }

        val outcome = useCase()

        assertTrue(outcome is WatchCameraBroadcast.Serving)
    }

    private fun enumeration() = BroadcastCameraLenses(
        lenses = listOf(lens("0", "lens_back", "back"), lens("1", "lens_front", "front")),
        activeLensId = "0"
    )

    private fun lens(id: String, labelKey: String, facing: String) =
        WearCameraLensDto(id = id, labelKey = labelKey, facing = facing)

    private fun live(url: String, activeLensId: String?) = BroadcastState.Live(
        descriptor = BroadcastDescriptorDto(url = url, mode = BroadcastMode.VIDEO_AUDIO.name),
        startedAtElapsedRealtimeMs = 0L,
        cameraEnabled = true,
        activeLensId = activeLensId
    )

    /**
     * A hand-written double rather than a mock: the use case suspends on [state] until the session
     * settles, so the test must be able to move that state from inside `start` - which a stubbed
     * method cannot do without becoming this class anyway.
     */
    private class RecordingController(
        private val stateFlow: MutableStateFlow<BroadcastState>
    ) : BroadcastSourceController {

        var available: Boolean = true
        var started: Boolean = false
        var onStart: (() -> Unit)? = null

        override val isAvailable: Boolean get() = available

        override val state: StateFlow<BroadcastState> get() = stateFlow

        override val listenerCount: StateFlow<Int> = MutableStateFlow(0)

        override fun start(mode: BroadcastMode, lensId: String?) {
            started = true
            onStart?.invoke()
        }

        override fun stop() = Unit

        override fun toggleCamera() = Unit

        override fun toggleMicrophone() = Unit

        override fun selectLens(lensId: String) = Unit

        override fun acknowledgeFailure() {
            if (stateFlow.value is BroadcastState.Failed) {
                stateFlow.value = BroadcastState.Idle
            }
        }
    }
}
