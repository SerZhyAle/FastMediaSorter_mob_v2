package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S3220: which broadcast transitions end a watch's camera session and which do not.
 *
 * The rule is asserted here rather than where the announcement is sent, because the sender lives in
 * `wearGms` - a source set mounted into the shipping variants by directory and into no unit-test
 * variant at all.
 *
 * The collector never completes, so it runs in `backgroundScope` and is advanced with [runCurrent]
 * after every state move.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnnounceWatchCameraSessionEndUseCaseTest {

    private val state = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
    private val controller = FakeController(state)
    private val announcer = CountingAnnouncer()

    private val useCase = AnnounceWatchCameraSessionEndUseCase(controller, announcer)

    @Test
    fun `announces the end when a live session goes idle`() = runTest {
        observeInBackground()
        state.value = live()
        runCurrent()

        state.value = BroadcastState.Idle
        runCurrent()

        assertEquals(1, announcer.announcements)
    }

    @Test
    fun `announces the end when the capture fails under a live session`() = runTest {
        observeInBackground()
        state.value = live()
        runCurrent()

        state.value = BroadcastState.Failed(BroadcastFailure.CAPTURE_ERROR, "camera closed")
        runCurrent()

        assertEquals(1, announcer.announcements)
    }

    @Test
    fun `says nothing when a session starts`() = runTest {
        observeInBackground()

        state.value = live()
        runCurrent()

        assertEquals(0, announcer.announcements)
    }

    @Test
    fun `says nothing when a live session only changes its lens`() = runTest {
        observeInBackground()
        state.value = live(activeLensId = "0")
        runCurrent()

        state.value = live(activeLensId = "1")
        runCurrent()

        assertEquals(0, announcer.announcements)
    }

    @Test
    fun `says nothing in a build that cannot broadcast`() = runTest {
        controller.available = false
        observeInBackground()

        state.value = live()
        runCurrent()
        state.value = BroadcastState.Idle
        runCurrent()

        assertEquals(0, announcer.announcements)
    }

    private fun TestScope.observeInBackground() {
        backgroundScope.launch { useCase.observe() }
        runCurrent()
    }

    private fun live(activeLensId: String? = null) = BroadcastState.Live(
        descriptor = BroadcastDescriptorDto(url = "rtsp://10.0.0.2:8554/", mode = BroadcastMode.VIDEO_AUDIO.name),
        startedAtElapsedRealtimeMs = 0L,
        cameraEnabled = true,
        activeLensId = activeLensId
    )

    private class CountingAnnouncer : WatchCameraSessionAnnouncer {

        var announcements: Int = 0

        override suspend fun announceSessionEnded() {
            announcements++
        }
    }

    /** A hand-written double: the test moves [state] itself, which a stubbed flow cannot express. */
    private class FakeController(
        private val stateFlow: MutableStateFlow<BroadcastState>
    ) : BroadcastSourceController {

        var available: Boolean = true

        override val isAvailable: Boolean get() = available

        override val state: StateFlow<BroadcastState> get() = stateFlow

        override val listenerCount: StateFlow<Int> = MutableStateFlow(0)

        override fun start(mode: BroadcastMode, lensId: String?) = Unit

        override fun stop() = Unit

        override fun toggleCamera() = Unit

        override fun toggleMicrophone() = Unit

        override fun selectLens(lensId: String) = Unit

        override fun acknowledgeFailure() = Unit
    }
}
