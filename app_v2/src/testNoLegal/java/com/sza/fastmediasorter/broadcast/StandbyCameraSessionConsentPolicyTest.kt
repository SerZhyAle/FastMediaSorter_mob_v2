package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.domain.model.WearCameraRefusal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2551: standby grants only while the broadcast layer is already live, because that state is the
 * whole evidence that consent was given ahead of time and that nothing has to be started from a
 * background listener to honour it.
 */
class StandbyCameraSessionConsentPolicyTest {

    private class FakeController(initial: BroadcastState) : BroadcastSourceController {
        override val isAvailable: Boolean = true
        private val mutableState = MutableStateFlow(initial)
        override val state: StateFlow<BroadcastState> = mutableState
        override val listenerCount: StateFlow<Int> = MutableStateFlow(0)
        override fun start(mode: BroadcastMode, lensId: String?) = Unit
        override fun stop() = Unit
        override fun toggleCamera() = Unit
        override fun toggleMicrophone() = Unit
        override fun selectLens(lensId: String) = Unit
        override fun acknowledgeFailure() = Unit
    }

    @Test
    fun `nothing armed refuses with NOT_ARMED`() = runTest {
        val policy = StandbyCameraSessionConsentPolicy(FakeController(BroadcastState.Idle))

        assertEquals(
            CameraConsentOutcome.Refused(WearCameraRefusal.NOT_ARMED),
            policy.requestConsent("req-1")
        )
    }

    @Test
    fun `a failed session is not a grant either`() = runTest {
        val policy = StandbyCameraSessionConsentPolicy(
            FakeController(BroadcastState.Failed(BroadcastFailure.CAPTURE_ERROR, "boom"))
        )

        assertEquals(
            CameraConsentOutcome.Refused(WearCameraRefusal.NOT_ARMED),
            policy.requestConsent("req-1")
        )
    }

    @Test
    fun `a live camera session grants without starting anything`() = runTest {
        val policy =
            StandbyCameraSessionConsentPolicy(
                FakeController(
                    BroadcastState.Live(
                        descriptor(BroadcastMode.VIDEO_AUDIO),
                        startedAtElapsedRealtimeMs = 0L
                    )
                )
            )

        assertEquals(CameraConsentOutcome.Granted, policy.requestConsent("req-1"))
    }

    /** S2551 step 06.3: broadcasting sound to the room is not consent to be seen through the camera. */
    @Test
    fun `an audio broadcast is not a standing camera arrangement`() = runTest {
        val policy =
            StandbyCameraSessionConsentPolicy(
                FakeController(
                    BroadcastState.Live(
                        descriptor(BroadcastMode.AUDIO_ONLY),
                        startedAtElapsedRealtimeMs = 0L
                    )
                )
            )

        assertEquals(
            CameraConsentOutcome.Refused(WearCameraRefusal.NOT_ARMED),
            policy.requestConsent("req-1")
        )
    }

    private fun descriptor(mode: BroadcastMode): BroadcastDescriptorDto =
        BroadcastDescriptorDto(url = "rtsp://127.0.0.1:8554/live", mode = mode.name)
}
