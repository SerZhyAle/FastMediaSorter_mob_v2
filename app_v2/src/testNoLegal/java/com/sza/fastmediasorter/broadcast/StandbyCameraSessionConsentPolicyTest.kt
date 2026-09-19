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
    fun `a live session grants without starting anything`() = runTest {
        val policy =
            StandbyCameraSessionConsentPolicy(
                FakeController(BroadcastState.Live(descriptor(), startedAtElapsedRealtimeMs = 0L))
            )

        assertEquals(CameraConsentOutcome.Granted, policy.requestConsent("req-1"))
    }

    private fun descriptor(): BroadcastDescriptorDto =
        BroadcastDescriptorDto(url = "http://127.0.0.1:8080/live", mode = "AUDIO_ONLY")
}
