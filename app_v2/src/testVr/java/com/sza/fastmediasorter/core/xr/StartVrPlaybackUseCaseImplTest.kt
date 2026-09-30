package com.sza.fastmediasorter.core.xr

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StartVrPlaybackUseCaseImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val payloadHolder = spyk(VrLaunchPayloadHolder())
    private val detectionFacade = mockk<XrDetectionFacade> {
        every { state() } returns flowOf(XrDetectionState.AVAILABLE_ENABLED)
    }

    // Mirrors XrEntryGatewayImpl: every intent it builds stores the launch input in the holder.
    private val entryGateway = mockk<XrEntryGateway> {
        every { createImmersiveIntent(any()) } answers {
            payloadHolder.put(firstArg<VrLaunchInput>())
            mockk<Intent>(relaxed = true)
        }
    }

    private val useCase = StartVrPlaybackUseCaseImpl(
        appContext = context,
        detectionFacade = detectionFacade,
        entryGateway = entryGateway,
        payloadHolder = payloadHolder,
    )

    private val request = StartVrPlaybackRequest(
        launchMode = VrLaunchMode.FILE_URI,
        fileUriString = "file:///sdcard/pano.jpg",
        mediaType = VrMediaType.IMAGE,
        source = VrLaunchPoint.PLAYER_BADGE,
    )

    @Test
    fun `dispatch stores exactly one launch input token`() = runTest {
        val result = useCase(request, returnTarget = null)

        assertEquals(StartVrPlaybackUseCase.DispatchResult.Started, result)
        verify(exactly = 1) { entryGateway.createImmersiveIntent(any()) }
        verify(exactly = 1) { payloadHolder.put(any<VrLaunchInput>()) }
    }

    @Test
    fun `dispatch reports NoRuntime when the gateway builds no intent`() = runTest {
        every { entryGateway.createImmersiveIntent(any()) } returns null

        val result = useCase(request, returnTarget = null)

        assertEquals(
            StartVrPlaybackUseCase.DispatchResult.Unavailable(VrLaunchUnavailableReason.NoRuntime),
            result,
        )
        verify(exactly = 1) { entryGateway.createImmersiveIntent(any()) }
    }
}
