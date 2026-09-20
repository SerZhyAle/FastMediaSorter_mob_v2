package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
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
 * S2551 step 06.3: what the owner's "let my watch see my camera" switch may and may not promise.
 */
class ArmWatchCameraStandbyUseCaseTest {

    private class FakeController(
        initial: BroadcastState,
        override val cameraSurvivesBackground: Boolean = true,
    ) : BroadcastSourceController {
        override val isAvailable: Boolean = true
        private val mutableState = MutableStateFlow(initial)
        override val state: StateFlow<BroadcastState> = mutableState
        override val listenerCount: StateFlow<Int> = MutableStateFlow(0)
        override val feedbackSuppressed: StateFlow<Boolean> = MutableStateFlow(false)
        var stopCount: Int = 0
            private set

        override fun start(mode: BroadcastMode, lensId: String?) = Unit
        override fun stop() { stopCount++ }
        override fun toggleCamera() = Unit
        override fun toggleMicrophone() = Unit
        override fun selectLens(lensId: String) = Unit
        override fun acknowledgeFailure() = Unit
    }

    @Test
    fun `a build whose camera dies off screen arms nothing`() = runTest {
        val start = mockk<StartWatchCameraBroadcastUseCase>()
        val useCase = ArmWatchCameraStandbyUseCase(
            FakeController(BroadcastState.Idle, cameraSurvivesBackground = false),
            start,
        )

        assertEquals(
            WatchCameraStandby.Refused(WearCameraRefusal.NOT_SUPPORTED),
            useCase.arm()
        )
    }

    @Test
    fun `a served session is armed`() = runTest {
        val start = mockk<StartWatchCameraBroadcastUseCase>()
        coEvery { start.invoke() } returns WatchCameraBroadcast.Serving(
            url = "rtsp://192.168.1.2:8554/live",
            lenses = BroadcastCameraLenses.EMPTY,
            startedNow = true,
        )

        assertEquals(
            WatchCameraStandby.Armed,
            ArmWatchCameraStandbyUseCase(FakeController(BroadcastState.Idle), start).arm()
        )
    }

    @Test
    fun `a refusal carries its own reason to the row`() = runTest {
        val start = mockk<StartWatchCameraBroadcastUseCase>()
        coEvery { start.invoke() } returns
            WatchCameraBroadcast.Refused(WearCameraRefusal.CAPTURE_FAILED)

        assertEquals(
            WatchCameraStandby.Refused(WearCameraRefusal.CAPTURE_FAILED),
            ArmWatchCameraStandbyUseCase(FakeController(BroadcastState.Idle), start).arm()
        )
    }

    @Test
    fun `disarming ends a camera session`() {
        val controller = FakeController(live(BroadcastMode.VIDEO_AUDIO))
        val useCase = ArmWatchCameraStandbyUseCase(controller, mockk())

        assertTrue(useCase.isArmed)
        useCase.disarm()

        assertEquals(1, controller.stopCount)
    }

    @Test
    fun `disarming leaves an audio broadcast to its own listeners`() {
        val controller = FakeController(live(BroadcastMode.AUDIO_ONLY))
        val useCase = ArmWatchCameraStandbyUseCase(controller, mockk())

        assertFalse(useCase.isArmed)
        useCase.disarm()

        assertEquals(0, controller.stopCount)
    }

    private fun live(mode: BroadcastMode): BroadcastState.Live = BroadcastState.Live(
        descriptor = BroadcastDescriptorDto(url = "rtsp://127.0.0.1:8554/live", mode = mode.name),
        startedAtElapsedRealtimeMs = 0L,
    )
}
