package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.domain.model.WearCameraRefusal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2551: the `standard` consent decision, which is the one place in this ticket where a wrong answer
 * would open the phone's camera without the owner having agreed.
 */
class NotificationCameraSessionConsentPolicyTest {

    private class FakePrompt(private val shown: Boolean) : CameraSessionConsentPrompt {
        override fun ask(requestId: String): Boolean = shown
    }

    @Test
    fun `a prompt that cannot be shown refuses with NOT_ASKED`() = runTest {
        val policy = NotificationCameraSessionConsentPolicy(FakePrompt(shown = false))

        assertEquals(
            CameraConsentOutcome.Refused(WearCameraRefusal.NOT_ASKED),
            policy.requestConsent("req-1")
        )
    }

    @Test
    fun `a posted prompt leaves the request waiting for the owner`() = runTest {
        val policy = NotificationCameraSessionConsentPolicy(FakePrompt(shown = true))

        assertEquals(CameraConsentOutcome.Asked, policy.requestConsent("req-1"))
    }
}
